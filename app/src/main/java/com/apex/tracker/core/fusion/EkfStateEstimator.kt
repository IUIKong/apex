package com.apex.tracker.core.fusion

import com.apex.tracker.core.math.EnuProjection
import com.apex.tracker.core.math.Matrix
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.FilterDiagnostics
import com.apex.tracker.core.model.FusedState
import com.apex.tracker.core.model.GpsUpdateResult
import com.apex.tracker.core.model.MotionState
import com.apex.tracker.core.model.SessionSnapshot
import com.apex.tracker.service.DeadReckoningPhase
import com.apex.tracker.service.DeadReckoningStep
import kotlin.math.*

/**
 * 2D Horizontal Extended Kalman Filter (EKF) State Estimator & Sensor Fusion Engine.
 *
 * Implements a 6-state [pe, pn, ve, vn, ae, an] constant acceleration kinematic model
 * with continuous Wiener jerk process noise, dynamic activity noise tuning,
 * turn-adaptive Mahalanobis innovation gating, vertical 1.5m deadband hysteresis fusion,
 * 5-state auto-pause classification, dual-window pace smoothing, and triple distance accumulators.
 */
class EkfStateEstimator : StateEstimator {

    // Origin geodesic coordinates
    var originLat: Double = 0.0
        private set
    var originLon: Double = 0.0
        private set
    var originAlt: Double = 0.0
        private set
    var lastRawLat: Double = 0.0
        private set
    var lastRawLon: Double = 0.0
        private set

    // 6-state vector [pe, pn, ve, vn, ae, an]^T
    var state: DoubleArray = DoubleArray(6)
        private set

    // 6x6 covariance matrix P
    var covariance: Matrix = Matrix.identity(6)
        private set

    // Pipeline components
    val mahalanobisGating = MahalanobisGating()
    val verticalFilter = VerticalHysteresisFilter()
    val autoPauseClassifier = AutoPauseClassifier()
    val paceEngine = PaceEngine()
    val distanceAccumulators = DistanceAccumulators()
    val splitTicker = SplitTicker()

    // Tracking metadata
    var elapsedTimeMs: Long = 0L
        private set
    var movingTimeMs: Long = 0L
        private set

    private var isInitialized = false
    private var lastGyroYawRateRadPerSec: Double = 0.0
    private var lastMahalanobisD2: Double = 0.0
    private var lastDtSeconds: Double = 1.0
    private var timeSinceLastGpsFixMs: Long = 0L

    var lastGpsSpeedMps: Float? = null
        private set
    var lastGpsSpeedAccuracyMps: Float? = null
        private set

    private var isDeadReckoningManagedExternally = false

    override fun initialize(
        originLat: Double,
        originLon: Double,
        originAlt: Double,
        initialAccuracyMeters: Float
    ) {
        val safeLat = if (originLat.isNaN() || originLat.isInfinite()) 0.0 else originLat.coerceIn(-90.0, 90.0)
        val safeLon = if (originLon.isNaN() || originLon.isInfinite()) 0.0 else originLon
        val safeAlt = if (originAlt.isNaN() || originAlt.isInfinite()) 0.0 else originAlt
        val safeAcc = if (initialAccuracyMeters.isNaN() || initialAccuracyMeters.isInfinite() || initialAccuracyMeters <= 0f) 5.0f else initialAccuracyMeters

        this.originLat = safeLat
        this.originLon = safeLon
        this.originAlt = safeAlt
        this.lastRawLat = safeLat
        this.lastRawLon = safeLon

        // State vector starts at origin with zero velocity and acceleration
        state = DoubleArray(6)

        // Initial covariance: position uncertainty from accuracy, initial velocity (3.0 m/s), accel (1.0 m/s^2)
        val posSigma = max(safeAcc.toDouble(), 1.5)
        val posVar = posSigma * posSigma
        val velVar = 9.0 // (3.0 m/s)^2
        val accVar = 1.0 // (1.0 m/s^2)^2

        covariance = Matrix.diag(posVar, posVar, velVar, velVar, accVar, accVar)

        // Reset and initialize subcomponents
        mahalanobisGating.reset()
        verticalFilter.initialize(safeAlt)
        autoPauseClassifier.setState(MotionState.INITIALIZING)
        paceEngine.reset()
        distanceAccumulators.reset()
        distanceAccumulators.addRawLocation(safeLat, safeLon)
        distanceAccumulators.addFilteredPosition(0.0, 0.0)
        splitTicker.reset()

        elapsedTimeMs = 0L
        movingTimeMs = 0L
        lastGyroYawRateRadPerSec = 0.0
        lastMahalanobisD2 = 0.0
        lastDtSeconds = 1.0
        timeSinceLastGpsFixMs = 0L
        lastGpsSpeedMps = null
        lastGpsSpeedAccuracyMps = null
        isInitialized = true
    }

    override fun predict(
        dtSeconds: Double,
        activityType: ActivityType,
        gyroYawRateRadPerSec: Double
    ) {
        if (!isInitialized) return

        val dt = if (dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds < 1e-4) 1e-4 else dtSeconds.coerceIn(1e-4, 60.0)
        val safeGyroYawRate = if (gyroYawRateRadPerSec.isNaN() || gyroYawRateRadPerSec.isInfinite()) 0.0 else gyroYawRateRadPerSec

        lastDtSeconds = dt
        lastGyroYawRateRadPerSec = safeGyroYawRate
        autoPauseClassifier.activityType = activityType

        elapsedTimeMs += (dt * 1000.0).toLong()

        // 1. Kinematic state propagation: x_k = F(dt) * x_{k-1}
        val dt2 = 0.5 * dt * dt
        val pe = state[0] + dt * state[2] + dt2 * state[4]
        val pn = state[1] + dt * state[3] + dt2 * state[5]
        val ve = state[2] + dt * state[4]
        val vn = state[3] + dt * state[5]

        // Dampen acceleration to prevent divergence during dropout
        val dampAccel = exp(-dt / 2.0)
        val ae = state[4] * dampAccel
        val an = state[5] * dampAccel

        state[0] = pe
        state[1] = pn
        state[2] = ve
        state[3] = vn
        state[4] = ae
        state[5] = an

        // 2. State transition matrix F
        val F = Matrix.identity(6).apply {
            this[0, 2] = dt; this[0, 4] = dt2
            this[1, 3] = dt; this[1, 5] = dt2
            this[2, 4] = dt
            this[3, 5] = dt
            this[4, 4] = dampAccel
            this[5, 5] = dampAccel
        }

        // 3. Process noise covariance Q(dt, q_j, q_a) with turn-adaptive boost
        val baseQj = activityType.jerkSpectralDensity
        val boost = mahalanobisGating.computeProcessNoiseBoost(safeGyroYawRate)
        val qj = baseQj * boost
        // Athletic continuous acceleration spectral density
        val qa = qj * 0.5

        val dt3 = dt * dt * dt
        val dt4 = dt3 * dt
        val dt5 = dt4 * dt

        val q00 = qj * (dt5 / 20.0) + qa * (dt3 / 3.0)
        val q01 = qj * (dt4 / 8.0) + qa * (dt2 / 2.0)
        val q02 = qj * (dt3 / 6.0)
        val q11 = qj * (dt3 / 3.0) + qa * dt
        val q12 = qj * (dt2 / 2.0)
        val q22 = qj * dt

        val Q = Matrix.zeros(6, 6).apply {
            // East axis
            this[0, 0] = q00; this[0, 2] = q01; this[0, 4] = q02
            this[2, 0] = q01; this[2, 2] = q11; this[2, 4] = q12
            this[4, 0] = q02; this[4, 2] = q12; this[4, 4] = q22

            // North axis
            this[1, 1] = q00; this[1, 3] = q01; this[1, 5] = q02
            this[3, 1] = q01; this[3, 3] = q11; this[3, 5] = q12
            this[5, 1] = q02; this[5, 3] = q12; this[5, 5] = q22
        }

        // 4. Covariance propagation: P^- = F * P * F^T + Q
        val pPred = (F * covariance * F.transpose()) + Q

        // Bound positional covariance to prevent exploding during outlier rejection
        // Congruent row/column scaling preserves Cauchy-Schwarz inequality & positive semi-definiteness
        if (pPred[0, 0] > 100.0 && !pPred[0, 0].isNaN() && !pPred[0, 0].isInfinite()) {
            val scale = sqrt(100.0 / pPred[0, 0])
            for (i in 0 until 6) {
                pPred[0, i] *= scale
                pPred[i, 0] *= scale
            }
        }
        if (pPred[1, 1] > 100.0 && !pPred[1, 1].isNaN() && !pPred[1, 1].isInfinite()) {
            val scale = sqrt(100.0 / pPred[1, 1])
            for (i in 0 until 6) {
                pPred[1, i] *= scale
                pPred[i, 1] *= scale
            }
        }

        covariance = symmetrize(pPred)

        timeSinceLastGpsFixMs += (dt * 1000.0).toLong()

        // 5. GPS blackout handling (> 30s cutoff when no motion detected)
        val hasAccel = autoPauseClassifier.hasAccelerationSamples()
        val eAcc = autoPauseClassifier.computeAccelerometerEnergy()
        val isImuActive = hasAccel && eAcc >= autoPauseClassifier.activityType.eMoveMps2

        if (timeSinceLastGpsFixMs > 30000L && !isImuActive) {
            // Cutoff after 30s of GPS dropout only if no footsteps/cadence detected
            if (autoPauseClassifier.currentState != MotionState.STOPPED) {
                autoPauseClassifier.update(vHorizMps = 0.0, dtSeconds = dt, incrementalDistanceMeters = 0.0)
            }
            state[2] = 0.0
            state[3] = 0.0
            state[4] = 0.0
            state[5] = 0.0
        } else if (!isDeadReckoningManagedExternally && isImuActive && timeSinceLastGpsFixMs > 1000L && (autoPauseClassifier.currentState == MotionState.MOVING || autoPauseClassifier.currentState == MotionState.POSSIBLY_MOVING)) {
            val dStep = distanceAccumulators.addFilteredPosition(state[0], state[1])
            val vHoriz = sqrt(state[2] * state[2] + state[3] * state[3])
            val fsmResult = autoPauseClassifier.update(vHoriz, dt, dStep)
            if (fsmResult.committedDistanceMeters > 0.0) {
                distanceAccumulators.addAcceptedDistance(fsmResult.committedDistanceMeters)
            }
            if (fsmResult.committedDurationMs > 0L) {
                movingTimeMs += fsmResult.committedDurationMs
            } else if (fsmResult.isMoving) {
                movingTimeMs += (dt * 1000.0).toLong()
            }
        }

        if (!isDeadReckoningManagedExternally && isImuActive && timeSinceLastGpsFixMs > 1000L) {
            val isAutoPaused = autoPauseClassifier.currentState != MotionState.MOVING
            paceEngine.update(
                elapsedMovingTimeSec = movingTimeMs / 1000.0,
                acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                dtSeconds = dt,
                isAutoPaused = isAutoPaused
            )

            splitTicker.checkSplit(
                acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                elapsedTimeMs = elapsedTimeMs,
                movingTimeMs = movingTimeMs,
                elevationGainMeters = verticalFilter.elevationGain,
                elevationLossMeters = verticalFilter.elevationLoss
            )
        }
    }

    override fun updateGpsPosition(
        lat: Double,
        lon: Double,
        accuracyMeters: Float
    ): GpsUpdateResult {
        if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite() ||
            accuracyMeters.isNaN() || accuracyMeters.isInfinite() || accuracyMeters <= 0f
        ) {
            return GpsUpdateResult.REJECTED_OUTLIER
        }

        if (!isInitialized) {
            initialize(lat, lon, 0.0, accuracyMeters)
            return GpsUpdateResult.ACCEPTED
        }

        lastRawLat = lat
        lastRawLon = lon

        // Accumulate raw distance
        val dRaw = distanceAccumulators.addRawLocation(lat, lon)

        // Forward ENU projection
        val (ze, zn) = EnuProjection.forward(lat, lon, originLat, originLon)
        if (ze.isNaN() || zn.isNaN() || ze.isInfinite() || zn.isInfinite()) {
            return GpsUpdateResult.REJECTED_OUTLIER
        }

        // Innovation y = z - H * x^-
        val ye = ze - state[0]
        val yn = zn - state[1]
        val innovation = doubleArrayOf(ye, yn)

        // Measurement noise R_pos
        val sigmaPos = max(accuracyMeters.toDouble(), 1.5)
        val rVar = sigmaPos * sigmaPos

        // Innovation covariance S = H * P^- * H^T + R
        val s00 = covariance[0, 0] + rVar
        val s01 = covariance[0, 1]
        val s10 = covariance[1, 0]
        val s11 = covariance[1, 1] + rVar
        val S = Matrix(2, 2, doubleArrayOf(s00, s01, s10, s11))

        // Mahalanobis distance gating
        val decision = mahalanobisGating.evaluate(innovation, S, lastGyroYawRateRadPerSec)
        lastMahalanobisD2 = decision.squaredMahalanobisDistance

        if (!decision.accepted) {
            if (decision.failsafeReanchored) {
                // Soft re-anchoring failsafe after consecutive rejected fixes
                state[0] = ze
                state[1] = zn
                state[2] = 0.0
                state[3] = 0.0
                state[4] = 0.0
                state[5] = 0.0

                covariance = Matrix.diag(rVar, rVar, 9.0, 9.0, 1.0, 1.0)
                timeSinceLastGpsFixMs = 0L

                // Re-anchor accumulator reference without double-counting discontinuous jump
                distanceAccumulators.reanchorFilteredPosition(ze, zn)
                val dt = if (lastDtSeconds > 0.0 && !lastDtSeconds.isNaN() && !lastDtSeconds.isInfinite()) lastDtSeconds else 1.0

                // Discontinuous re-anchor has zero incremental step displacement
                val fsmResult = autoPauseClassifier.update(0.0, dt, 0.0, accuracyMeters)
                if (fsmResult.committedDistanceMeters > 0.0) {
                    distanceAccumulators.addAcceptedDistance(fsmResult.committedDistanceMeters)
                }
                if (fsmResult.committedDurationMs > 0L) {
                    movingTimeMs += fsmResult.committedDurationMs
                } else if (fsmResult.isMoving) {
                    movingTimeMs += (dt * 1000.0).toLong()
                }

                paceEngine.update(
                    elapsedMovingTimeSec = movingTimeMs / 1000.0,
                    acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                    dtSeconds = dt,
                    isAutoPaused = !fsmResult.isMoving
                )

                splitTicker.checkSplit(
                    acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                    elapsedTimeMs = elapsedTimeMs,
                    movingTimeMs = movingTimeMs,
                    elevationGainMeters = verticalFilter.elevationGain,
                    elevationLossMeters = verticalFilter.elevationLoss
                )

                return GpsUpdateResult.REANCHORED
            } else {
                return GpsUpdateResult.REJECTED_OUTLIER
            }
        }

        // Kalman gain K = P^- * H^T * S^(-1)
        val sInv = S.invert2x2() ?: return GpsUpdateResult.REJECTED_OUTLIER

        // P^- * H^T (6x2: columns 0 and 1 of P^-)
        val pHT = Matrix(6, 2)
        for (i in 0 until 6) {
            pHT[i, 0] = covariance[i, 0]
            pHT[i, 1] = covariance[i, 1]
        }
        val K = pHT * sInv // (6x2)

        // State correction: x = x^- + K * y
        for (i in 0 until 6) {
            val delta = K[i, 0] * ye + K[i, 1] * yn
            if (!delta.isNaN() && !delta.isInfinite()) {
                state[i] += delta
            }
        }

        // Covariance correction using Joseph stabilized form: P = (I - K*H) * P^- * (I - K*H)^T + K * R * K^T
        val I6 = Matrix.identity(6)
        val KH = Matrix.zeros(6, 6)
        for (i in 0 until 6) {
            KH[i, 0] = K[i, 0]
            KH[i, 1] = K[i, 1]
        }
        val A = I6 - KH
        val Rpos = Matrix.diag(rVar, rVar)
        val pUpdated = (A * covariance * A.transpose()) + (K * Rpos * K.transpose())
        covariance = symmetrize(pUpdated)

        timeSinceLastGpsFixMs = 0L
        isDeadReckoningManagedExternally = false

        // Update distance accumulator position reference
        val dFiltered = distanceAccumulators.addFilteredPosition(state[0], state[1])

        // Re-evaluate motion state via AutoPauseClassifier with kinematic speed
        val dt = if (lastDtSeconds > 0.0 && !lastDtSeconds.isNaN() && !lastDtSeconds.isInfinite()) lastDtSeconds else 1.0
        val dispSpeed = dFiltered / dt
        val rawSpeed = dRaw / dt
        val baseSpeed = min(dispSpeed, if (dRaw > 0.0) rawSpeed else dispSpeed)
        val isGpsDopplerStationary = (lastGpsSpeedMps != null && lastGpsSpeedMps!! < 0.25f && (lastGpsSpeedAccuracyMps ?: 1f) <= 1.2f)
        val effectiveSpeed = if (isGpsDopplerStationary) {
            min(baseSpeed, lastGpsSpeedMps!!.toDouble())
        } else {
            baseSpeed
        }

        val hasAccel = autoPauseClassifier.hasAccelerationSamples()
        val eAcc = autoPauseClassifier.computeAccelerometerEnergy()
        val isStationaryImu = hasAccel && eAcc < autoPauseClassifier.activityType.eStopMps2 && effectiveSpeed < autoPauseClassifier.activityType.vMoveMps

        val isStationary = isStationaryImu || isGpsDopplerStationary

        if (isStationary) {
            state[2] = 0.0
            state[3] = 0.0
            state[4] = 0.0
            state[5] = 0.0
        }

        val classifiedSpeed = if (isStationary) 0.0 else effectiveSpeed
        val stepDistance = if (isStationary) 0.0 else dFiltered

        val fsmResult = autoPauseClassifier.update(classifiedSpeed, dt, stepDistance, accuracyMeters)
        if (fsmResult.committedDistanceMeters > 0.0) {
            distanceAccumulators.addAcceptedDistance(fsmResult.committedDistanceMeters)
        }
        if (fsmResult.committedDurationMs > 0L) {
            movingTimeMs += fsmResult.committedDurationMs
        } else if (fsmResult.isMoving) {
            movingTimeMs += (dt * 1000.0).toLong()
        }
        if (fsmResult.currentState == MotionState.STOPPED) {
            state[2] = 0.0
            state[3] = 0.0
            state[4] = 0.0
            state[5] = 0.0
        }

        paceEngine.update(
            elapsedMovingTimeSec = movingTimeMs / 1000.0,
            acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
            dtSeconds = dt,
            isAutoPaused = !fsmResult.isMoving
        )

        splitTicker.checkSplit(
            acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
            elapsedTimeMs = elapsedTimeMs,
            movingTimeMs = movingTimeMs,
            elevationGainMeters = verticalFilter.elevationGain,
            elevationLossMeters = verticalFilter.elevationLoss
        )

        return GpsUpdateResult.ACCEPTED
    }

    override fun updateGpsVelocity(
        speedMps: Float,
        bearingDegrees: Float,
        speedAccuracyMps: Float
    ) {
        if (!isInitialized) return
        if (speedMps.isNaN() || speedMps.isInfinite() || speedMps < 0f ||
            bearingDegrees.isNaN() || bearingDegrees.isInfinite() ||
            speedAccuracyMps.isNaN() || speedAccuracyMps.isInfinite()
        ) {
            return
        }

        lastGpsSpeedMps = speedMps
        lastGpsSpeedAccuracyMps = speedAccuracyMps

        val (zve, zvn) = if (speedMps < 0.25f) {
            Pair(0.0, 0.0)
        } else if (speedMps > 0.5f) {
            val bearingRad = Math.toRadians(bearingDegrees.toDouble())
            Pair(speedMps * sin(bearingRad), speedMps * cos(bearingRad))
        } else {
            return
        }

        val yve = zve - state[2]
        val yvn = zvn - state[3]

        val sigmaVel = if (speedAccuracyMps > 0.05f) {
            speedAccuracyMps.toDouble()
        } else if (speedMps < 0.25f) {
            0.25
        } else {
            max(0.4, 0.1 * speedMps)
        }
        val rVar = sigmaVel * sigmaVel

        val s22 = covariance[2, 2] + rVar
        val s23 = covariance[2, 3]
        val s32 = covariance[3, 2]
        val s33 = covariance[3, 3] + rVar
        val S = Matrix(2, 2, doubleArrayOf(s22, s23, s32, s33))
        val sInv = S.invert2x2() ?: return

        // P * H_vel^T (6x2: columns 2 and 3 of P)
        val pHT = Matrix(6, 2)
        for (i in 0 until 6) {
            pHT[i, 0] = covariance[i, 2]
            pHT[i, 1] = covariance[i, 3]
        }
        val K = pHT * sInv

        for (i in 0 until 6) {
            val delta = K[i, 0] * yve + K[i, 1] * yvn
            if (!delta.isNaN() && !delta.isInfinite()) {
                state[i] += delta
            }
        }

        val I6 = Matrix.identity(6)
        val KH = Matrix.zeros(6, 6)
        for (i in 0 until 6) {
            KH[i, 2] = K[i, 0]
            KH[i, 3] = K[i, 1]
        }
        val A = I6 - KH
        val Rvel = Matrix.diag(rVar, rVar)
        val pUpdated = (A * covariance * A.transpose()) + (K * Rvel * K.transpose())
        covariance = symmetrize(pUpdated)
    }

    override fun updateBarometerPressure(pressureHpa: Float, timestampEpochMs: Long) {
        verticalFilter.updateBarometer(pressureHpa, timestampEpochMs)
    }

    override fun updateGnssAltitude(altitudeMeters: Double, timestampEpochMs: Long) {
        verticalFilter.updateGnssAltitude(altitudeMeters, timestampEpochMs)
    }

    override fun updateImuAcceleration(ax: Float, ay: Float, az: Float, timestampEpochMs: Long) {
        autoPauseClassifier.addAccelerationSample(ax, ay, az, timestampEpochMs)
    }

    override fun getFusedState(): FusedState {
        val (lat, lon) = if (isInitialized) {
            EnuProjection.inverse(state[0], state[1], originLat, originLon)
        } else {
            Pair(originLat, originLon)
        }

        val speed = if (autoPauseClassifier.currentState == MotionState.STOPPED) {
            0.0
        } else {
            sqrt(state[2] * state[2] + state[3] * state[3])
        }
        val safeSpeed = if (speed.isNaN() || speed.isInfinite() || speed < 0.0) 0.0 else speed

        var bearing = Math.toDegrees(atan2(state[2], state[3])).toFloat()
        if (bearing.isNaN() || bearing.isInfinite()) bearing = 0f
        if (bearing < 0f) bearing += 360f

        val currentPace = paceEngine.currentPaceSecPerKm(autoPauseClassifier.currentState == MotionState.STOPPED)
        val accDist = distanceAccumulators.acceptedDistanceMeters
        val avgPace = if (accDist > 5.0 && movingTimeMs > 1000) {
            (movingTimeMs / 1000.0) / (accDist / 1000.0)
        } else {
            Double.NaN
        }

        return FusedState(
            lat = lat,
            lon = lon,
            altitude = verticalFilter.fusedAltitude,
            speedMps = safeSpeed,
            bearingDegrees = bearing,
            rawDistance = distanceAccumulators.rawDistanceMeters,
            filteredDistance = distanceAccumulators.filteredDistanceMeters,
            acceptedDistance = distanceAccumulators.acceptedDistanceMeters,
            fsmState = autoPauseClassifier.currentState,
            currentPaceSecPerKm = currentPace,
            avgPaceSecPerKm = avgPace,
            elevationGain = verticalFilter.elevationGain,
            elevationLoss = verticalFilter.elevationLoss,
            timestampEpochMs = elapsedTimeMs,
            rawLat = if (lastRawLat != 0.0) lastRawLat else lat,
            rawLon = if (lastRawLon != 0.0) lastRawLon else lon,
            movingTimeMs = movingTimeMs
        )
    }

    override fun applyDeadReckoningStep(step: DeadReckoningStep, dtSeconds: Double) {
        if (!isInitialized) return
        isDeadReckoningManagedExternally = true

        val dt = if (dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds < 1e-4) 1e-4 else dtSeconds

        if (step.phase == DeadReckoningPhase.CUTOFF) {
            state[2] = 0.0
            state[3] = 0.0
            state[4] = 0.0
            state[5] = 0.0
            autoPauseClassifier.update(vHorizMps = 0.0, dtSeconds = dt, incrementalDistanceMeters = 0.0)
            return
        }

        if (step.phase == DeadReckoningPhase.STEP_FREQUENCY_HEADING) {
            // Apply incremental step displacement to EKF state position
            state[0] += step.deltaEastMeters
            state[1] += step.deltaNorthMeters
            state[2] = step.speedMps * sin(step.headingRad)
            state[3] = step.speedMps * cos(step.headingRad)

            if (step.stepDistanceMeters > 0.0) {
                distanceAccumulators.addFilteredPosition(state[0], state[1])
                distanceAccumulators.addAcceptedDistance(step.stepDistanceMeters)
                movingTimeMs += (dt * 1000.0).toLong()

                paceEngine.update(
                    elapsedMovingTimeSec = movingTimeMs / 1000.0,
                    acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                    dtSeconds = dt,
                    isAutoPaused = false
                )
                splitTicker.checkSplit(
                    acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                    elapsedTimeMs = elapsedTimeMs,
                    movingTimeMs = movingTimeMs,
                    elevationGainMeters = verticalFilter.elevationGain,
                    elevationLossMeters = verticalFilter.elevationLoss
                )
            }
        } else if (step.phase == DeadReckoningPhase.KINEMATIC_EXTRAPOLATION) {
            if (step.stepDistanceMeters > 0.0 && autoPauseClassifier.currentState != MotionState.STOPPED) {
                distanceAccumulators.addFilteredPosition(state[0], state[1])
                distanceAccumulators.addAcceptedDistance(step.stepDistanceMeters)
                movingTimeMs += (dt * 1000.0).toLong()

                paceEngine.update(
                    elapsedMovingTimeSec = movingTimeMs / 1000.0,
                    acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                    dtSeconds = dt,
                    isAutoPaused = false
                )
                splitTicker.checkSplit(
                    acceptedDistanceMeters = distanceAccumulators.acceptedDistanceMeters,
                    elapsedTimeMs = elapsedTimeMs,
                    movingTimeMs = movingTimeMs,
                    elevationGainMeters = verticalFilter.elevationGain,
                    elevationLossMeters = verticalFilter.elevationLoss
                )
            }
        }
    }

    override fun getDiagnostics(): FilterDiagnostics {
        return FilterDiagnostics(
            posVarEast = covariance[0, 0],
            posVarNorth = covariance[1, 1],
            velVarEast = covariance[2, 2],
            velVarNorth = covariance[3, 3],
            mahalanobisD2 = lastMahalanobisD2,
            outliersRejectedCount = mahalanobisGating.consecutiveOutliersCount,
            isDeadbandActive = (verticalFilter.currentTrend == VerticalHysteresisFilter.ElevationTrend.NEUTRAL)
        )
    }

    override fun restore(snapshot: SessionSnapshot) {
        originLat = snapshot.originLat
        originLon = snapshot.originLon
        originAlt = snapshot.originAlt
        lastRawLat = snapshot.originLat
        lastRawLon = snapshot.originLon

        val sv = snapshot.stateVector
        if (sv.size >= 6) {
            for (i in 0 until 6) {
                val v = sv[i]
                state[i] = if (v.isNaN() || v.isInfinite()) 0.0 else v
            }
        }
        val cd = snapshot.covarianceDiagonals
        if (cd.size >= 6) {
            for (i in 0 until 6) {
                val v = cd[i]
                covariance[i, i] = if (v.isNaN() || v.isInfinite() || v <= 0.0) 1.0 else v
            }
        }

        distanceAccumulators.restore(
            raw = snapshot.rawDistanceMeters,
            filtered = snapshot.filteredDistanceMeters,
            accepted = snapshot.acceptedDistanceMeters
        )

        elapsedTimeMs = snapshot.elapsedTimeMs
        movingTimeMs = snapshot.movingTimeMs

        autoPauseClassifier.restore(
            fsmState = snapshot.fsmState,
            activityType = snapshot.activityType
        )

        verticalFilter.restoreState(
            fusedAlt = snapshot.elevationAnchorMeters,
            gain = snapshot.elevationGainMeters,
            loss = snapshot.elevationLossMeters,
            anchor = snapshot.elevationAnchorMeters,
            trend = runCatching { VerticalHysteresisFilter.ElevationTrend.valueOf(snapshot.elevationTrend) }.getOrDefault(VerticalHysteresisFilter.ElevationTrend.NEUTRAL)
        )

        isInitialized = true
    }

    private fun symmetrize(m: Matrix): Matrix {
        val result = Matrix(m.rows, m.cols)
        for (i in 0 until m.rows) {
            for (j in 0 until m.cols) {
                val v = 0.5 * (m[i, j] + m[j, i])
                result[i, j] = if (v.isNaN() || v.isInfinite()) 0.0 else v
            }
            if (result[i, i] <= 1e-9) {
                result[i, i] = 1e-9
            }
        }
        return result
    }
}
