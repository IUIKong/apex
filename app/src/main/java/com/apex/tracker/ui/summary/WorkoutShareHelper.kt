package com.apex.tracker.ui.summary

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.apex.tracker.ui.components.MapProjectionMath
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.state.WorkoutSummaryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for rendering high-resolution athletic workout summary share cards (1080x1350)
 * and triggering Android system share intents (WhatsApp, Instagram, etc.) via FileProvider.
 */
object WorkoutShareHelper {

    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1350
    const val SHARED_WORKOUTS_DIR = "shared_workouts"

    /**
     * Renders a 1080x1350 (4:5 social/Instagram/WhatsApp aspect ratio) athletic summary card
     * directly to an Android [Bitmap].
     */
    fun generateWorkoutShareCardBitmap(summary: WorkoutSummaryUiState): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Pure OLED Obsidian Canvas Background with subtle dark vertical gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                0xFF06080C.toInt(), 0xFF0C1017.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // 2. Subtle Precision Architectural Grid Accent
        val gridPaint = Paint().apply {
            color = 0x261B2433.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        var gx = 45f
        while (gx < CARD_WIDTH) {
            canvas.drawLine(gx, 0f, gx, CARD_HEIGHT.toFloat(), gridPaint)
            gx += 45f
        }
        var gy = 45f
        while (gy < CARD_HEIGHT) {
            canvas.drawLine(0f, gy, CARD_WIDTH.toFloat(), gy, gridPaint)
            gy += 45f
        }

        // 3. Top-Right Subtle Electric Cyan Ambient Aura Glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                CARD_WIDTH - 80f, 120f, 440f,
                0x2200F5D4.toInt(), 0x00000000,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), 550f, glowPaint)

        // Margins & Card Inset: 48px padding
        val margin = 48f
        val contentWidth = CARD_WIDTH - (margin * 2)

        // 4. Header: Logo Badge, Wordmark, Activity Badge, and Date
        drawHeader(canvas, margin, summary)

        // 5. Dedicated Route Circuit Map Panel (y = 156f to 690f)
        val mapRect = RectF(margin, 156f, margin + contentWidth, 690f)
        drawRouteMapPanel(canvas, mapRect, summary)

        // 6. Main Telemetry Metrics Grid (2x2) (y = 712f to 1180f)
        drawMetricsGrid(canvas, margin, contentWidth, summary)

        // 7. Footer: Minimal Apex Branding & Sub-meter Telemetry Badge
        drawFooter(canvas, margin, contentWidth, summary)

        return bitmap
    }

    private fun drawHeader(canvas: Canvas, margin: Float, summary: WorkoutSummaryUiState) {
        val badgeSize = 68f
        val badgeTop = 56f
        val badgeRect = RectF(margin, badgeTop, margin + badgeSize, badgeTop + badgeSize)

        // Official Obsidian Black Logo Badge matching ApexLogoMark
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0C1017.toInt() // Deep Cockpit Obsidian
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBgPaint)

        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B2433.toInt() // Hairline Titanium Border
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBorderPaint)

        // Official Dynamic Chevron Emblem
        val chevronScale = (badgeSize * 0.68f) / 48f
        val chevronOffsetX = badgeRect.left + (badgeSize - 48f * chevronScale) / 2f
        val chevronOffsetY = badgeRect.top + (badgeSize - 48f * chevronScale) / 2f

        val outerPeakPath = Path().apply {
            moveTo(chevronOffsetX + 24f * chevronScale, chevronOffsetY + 8f * chevronScale)
            lineTo(chevronOffsetX + 40f * chevronScale, chevronOffsetY + 34f * chevronScale)
            lineTo(chevronOffsetX + 34f * chevronScale, chevronOffsetY + 34f * chevronScale)
            lineTo(chevronOffsetX + 24f * chevronScale, chevronOffsetY + 18f * chevronScale)
            lineTo(chevronOffsetX + 14f * chevronScale, chevronOffsetY + 34f * chevronScale)
            lineTo(chevronOffsetX + 8f * chevronScale, chevronOffsetY + 34f * chevronScale)
            close()
        }
        val outerPeakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt() // Crisp Pure Titanium White
            style = Paint.Style.FILL
        }
        canvas.drawPath(outerPeakPath, outerPeakPaint)

        val innerPeakPath = Path().apply {
            moveTo(chevronOffsetX + 24f * chevronScale, chevronOffsetY + 22f * chevronScale)
            lineTo(chevronOffsetX + 34f * chevronScale, chevronOffsetY + 38f * chevronScale)
            lineTo(chevronOffsetX + 29.5f * chevronScale, chevronOffsetY + 38f * chevronScale)
            lineTo(chevronOffsetX + 24f * chevronScale, chevronOffsetY + 29f * chevronScale)
            lineTo(chevronOffsetX + 18.5f * chevronScale, chevronOffsetY + 38f * chevronScale)
            lineTo(chevronOffsetX + 14f * chevronScale, chevronOffsetY + 38f * chevronScale)
            close()
        }
        val innerPeakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00F5D4.toInt() // Athletic Electric Cyan
            style = Paint.Style.FILL
        }
        canvas.drawPath(innerPeakPath, innerPeakPaint)

        // Brand Wordmark "APEX"
        val wordmarkX = badgeRect.right + 20f
        val wordmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF8FAFC.toInt() // Titanium White
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.drawText("APEX", wordmarkX, badgeTop + 36f, wordmarkPaint)

        // Activity Type Pill Badge
        val wordmarkWidth = wordmarkPaint.measureText("APEX")
        val pillX = wordmarkX + wordmarkWidth + 18f
        val activityName = summary.activityType.uppercase(Locale.US)
        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00F5D4.toInt() // Electric Cyan
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val pillTextWidth = pillTextPaint.measureText(activityName)
        val pillRect = RectF(pillX, badgeTop + 10f, pillX + pillTextWidth + 24f, badgeTop + 40f)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF131923.toInt() // Elevated Dark Surface
            style = Paint.Style.FILL
        }
        val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B2433.toInt() // Subtle Border
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(pillRect, 15f, 15f, pillBgPaint)
        canvas.drawRoundRect(pillRect, 15f, 15f, pillBorderPaint)
        canvas.drawText(activityName, pillX + 12f, badgeTop + 30f, pillTextPaint)

        // Date / Time Subtitle
        val dateString = if (summary.startTime > 0L) {
            SimpleDateFormat("EEEE, MMM d, yyyy • h:mm a", Locale.US).format(Date(summary.startTime))
        } else {
            SimpleDateFormat("EEEE, MMM d, yyyy • h:mm a", Locale.US).format(Date())
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt() // Steel Slate
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(dateString, wordmarkX, badgeTop + 65f, datePaint)
    }

    private fun drawRouteMapPanel(canvas: Canvas, mapRect: RectF, summary: WorkoutSummaryUiState) {
        // Map Panel Container (Cockpit Obsidian Sheet)
        val mapBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0C1017.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(mapRect, 24f, 24f, mapBgPaint)

        val mapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B2433.toInt()
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(mapRect, 24f, 24f, mapBorderPaint)

        // Subtle Map Internal Grid
        canvas.save()
        canvas.clipRect(mapRect)

        val mapGridPaint = Paint().apply {
            color = 0x261B2433.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        var mgx = mapRect.left + 36f
        while (mgx < mapRect.right) {
            canvas.drawLine(mgx, mapRect.top, mgx, mapRect.bottom, mapGridPaint)
            mgx += 36f
        }
        var mgy = mapRect.top + 36f
        while (mgy < mapRect.bottom) {
            canvas.drawLine(mapRect.left, mgy, mapRect.right, mgy, mapGridPaint)
            mgy += 36f
        }

        // Header Label inside Map
        val mapTitleDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00F5D4.toInt() // Electric Cyan
            style = Paint.Style.FILL
        }
        canvas.drawCircle(mapRect.left + 28f, mapRect.top + 32f, 4f, mapTitleDotPaint)

        val mapTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00F5D4.toInt() // Electric Cyan
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("ROUTE CIRCUIT TRAJECTORY", mapRect.left + 40f, mapRect.top + 37f, mapTitlePaint)

        // Legend: START & FINISH Markers
        val legendPaintStart = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00FF87.toInt() // Electric Lime
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val legendPaintFinish = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF3B56.toInt() // Electric Coral/Crimson
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("● START", mapRect.right - 170f, mapRect.bottom - 20f, legendPaintStart)
        canvas.drawText("● FINISH", mapRect.right - 85f, mapRect.bottom - 20f, legendPaintFinish)

        // Render Route Vector Trace
        val points = summary.trackPoints.filter {
            it.latitude != 0.0 && it.longitude != 0.0 &&
                !it.latitude.isNaN() && !it.longitude.isNaN() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
                !it.isOutlier
        }
        if (points.size >= 2) {
            val fit = MapProjectionMath.computeAutoFitBoundsForPoints(
                trackPoints = points,
                canvasWidth = mapRect.width(),
                canvasHeight = mapRect.height(),
                padding = 58f
            )

            val routePath = Path()
            var startX = 0f
            var startY = 0f
            var finishX = 0f
            var finishY = 0f

            points.forEachIndexed { index, pt ->
                val offset = MapProjectionMath.projectToCanvas(
                    lat = pt.latitude,
                    lon = pt.longitude,
                    centerLat = fit.centerLat,
                    centerLon = fit.centerLon,
                    scale = fit.scale,
                    canvasWidth = mapRect.width(),
                    canvasHeight = mapRect.height()
                )
                val px = mapRect.left + offset.x
                val py = mapRect.top + offset.y

                if (index == 0) {
                    routePath.moveTo(px, py)
                    startX = px
                    startY = py
                } else {
                    routePath.lineTo(px, py)
                }

                if (index == points.size - 1) {
                    finishX = px
                    finishY = py
                }
            }

            // Layer 1: Ambient Electric Cyan Glow Halo
            val glowRoutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x2600F5D4.toInt()
                strokeWidth = 14f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(routePath, glowRoutePaint)

            // Layer 2: Medium Cyan Underlay Halo
            val underlayRoutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x5000F5D4.toInt()
                strokeWidth = 8f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(routePath, underlayRoutePaint)

            // Layer 3: Vibrant Glowing Electric Cyan Core Line
            val coreRoutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00F5D4.toInt()
                strokeWidth = 4.5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(routePath, coreRoutePaint)

            // Start Pin (Electric Lime Beacon)
            val startHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x3800FF87.toInt()
                style = Paint.Style.FILL
            }
            val startDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00FF87.toInt()
                style = Paint.Style.FILL
            }
            val whiteCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(startX, startY, 14f, startHaloPaint)
            canvas.drawCircle(startX, startY, 8f, startDotPaint)
            canvas.drawCircle(startX, startY, 3.5f, whiteCenterPaint)

            // Finish Pin (Electric Crimson Beacon)
            val finishHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x38FF3B56.toInt()
                style = Paint.Style.FILL
            }
            val finishDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFF3B56.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(finishX, finishY, 14f, finishHaloPaint)
            canvas.drawCircle(finishX, finishY, 8f, finishDotPaint)
            canvas.drawCircle(finishX, finishY, 3.5f, whiteCenterPaint)
        } else if (points.size == 1) {
            // Single location point
            val centerX = mapRect.centerX()
            val centerY = mapRect.centerY()
            val singleBeaconHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x3800FF87.toInt()
                style = Paint.Style.FILL
            }
            val singleBeaconDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00FF87.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, 18f, singleBeaconHalo)
            canvas.drawCircle(centerX, centerY, 9f, singleBeaconDot)
        } else {
            // Indoor / Zero GPS Points Empty State
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.08f
            }
            canvas.drawText("SENSOR FUSION TELEMETRY • INDOOR SESSION", mapRect.centerX(), mapRect.centerY(), emptyPaint)
        }

        canvas.restore()
    }

    private fun drawMetricsGrid(canvas: Canvas, margin: Float, contentWidth: Float, summary: WorkoutSummaryUiState) {
        val gridTop = 712f
        val cardGap = 20f
        val cardWidth = (contentWidth - cardGap) / 2f
        val cardHeight = 224f

        val distKm = UiFormatters.formatDistanceKm(summary.totalDistanceMeters)
        val movingTime = UiFormatters.formatDuration(summary.movingTimeSeconds)
        val elapsedTime = UiFormatters.formatDuration(summary.elapsedTimeSeconds)
        val avgPace = UiFormatters.formatPace(summary.avgPaceSecPerKm)
        val avgSpeedKmh = if (summary.movingTimeSeconds > 0) {
            (summary.totalDistanceMeters / summary.movingTimeSeconds) * 3.6
        } else 0.0
        val avgSpeedStr = String.format(Locale.US, "%.1f", avgSpeedKmh)

        // Row 1, Col 1: Total Distance (Electric Cyan accent)
        val card1Rect = RectF(margin, gridTop, margin + cardWidth, gridTop + cardHeight)
        drawMetricCard(
            canvas = canvas,
            rect = card1Rect,
            label = "TOTAL DISTANCE",
            value = distKm,
            unit = "KM",
            subtext = "Accepted trajectory",
            accentColor = 0xFF00F5D4.toInt()
        )

        // Row 1, Col 2: Moving Time (Electric Lime accent)
        val card2Rect = RectF(margin + cardWidth + cardGap, gridTop, margin + contentWidth, gridTop + cardHeight)
        drawMetricCard(
            canvas = canvas,
            rect = card2Rect,
            label = "MOVING TIME",
            value = movingTime,
            unit = "",
            subtext = "Elapsed: $elapsedTime",
            accentColor = 0xFF00FF87.toInt()
        )

        // Row 2, Col 1: Average Pace (Electric Cyan accent)
        val row2Top = gridTop + cardHeight + cardGap
        val card3Rect = RectF(margin, row2Top, margin + cardWidth, row2Top + cardHeight)
        drawMetricCard(
            canvas = canvas,
            rect = card3Rect,
            label = "AVERAGE PACE",
            value = avgPace,
            unit = "/KM",
            subtext = "Moving pace",
            accentColor = 0xFF00F5D4.toInt()
        )

        // Row 2, Col 2: Average Speed (Electric Lime accent)
        val card4Rect = RectF(margin + cardWidth + cardGap, row2Top, margin + contentWidth, row2Top + cardHeight)
        drawMetricCard(
            canvas = canvas,
            rect = card4Rect,
            label = "AVG SPEED",
            value = avgSpeedStr,
            unit = "KM/H",
            subtext = "GPS telemetry",
            accentColor = 0xFF00FF87.toInt()
        )
    }

    private fun drawMetricCard(
        canvas: Canvas,
        rect: RectF,
        label: String,
        value: String,
        unit: String,
        subtext: String,
        accentColor: Int
    ) {
        // Card Background (Cockpit Obsidian Sheet)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0C1017.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 22f, 22f, cardBgPaint)

        // Card Border (Hairline Titanium Border)
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B2433.toInt()
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(rect, 22f, 22f, cardBorderPaint)

        // Top Subtle Accent Pill
        val accentPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(rect.left + 24f, rect.top + 2f, rect.left + 72f, rect.top + 5f),
            2f, 2f, accentPillPaint
        )

        // Metric Label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt() // Steel Slate
            textSize = 17f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(label, rect.left + 24f, rect.top + 46f, labelPaint)

        // Main Numerical Value (Titanium White)
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF8FAFC.toInt()
            textSize = 66f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valueBaseline = rect.top + 138f
        canvas.drawText(value, rect.left + 24f, valueBaseline, valuePaint)

        // Optional Unit
        if (unit.isNotEmpty()) {
            val valueWidth = valuePaint.measureText(value)
            val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                textSize = 24f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.04f
            }
            canvas.drawText(unit, rect.left + 24f + valueWidth + 10f, valueBaseline, unitPaint)
        }

        // Subtext / Context
        val subtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF64748B.toInt()
            textSize = 15.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(subtext, rect.left + 24f, rect.top + 188f, subtextPaint)
    }

    private fun drawFooter(canvas: Canvas, margin: Float, contentWidth: Float, summary: WorkoutSummaryUiState) {
        val dividerY = 1216f
        val dividerPaint = Paint().apply {
            color = 0xFF1B2433.toInt()
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(margin, dividerY, margin + contentWidth, dividerY, dividerPaint)

        // Footer Branding: Minimal, clean, and elegant
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.16f
        }
        canvas.drawText(
            "APEX • ATHLETIC PERFORMANCE TRACKER",
            CARD_WIDTH / 2f,
            1280f,
            footerPaint
        )
    }

    /**
     * Computes the clean, sanitized filename for the cached workout share image.
     */
    fun getShareFileName(activityId: String): String {
        val safeId = if (activityId.isNotBlank()) {
            activityId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        } else {
            System.currentTimeMillis().toString()
        }
        return "apex_$safeId.png"
    }

    /**
     * Generates the athletic share message caption.
     */
    fun getShareMessage(summary: WorkoutSummaryUiState): String {
        val formattedDistance = "${UiFormatters.formatDistanceKm(summary.totalDistanceMeters)} km"
        val sport = when (summary.activityType.uppercase(Locale.US)) {
            "CYCLING" -> "ride 🚴‍♂️💨"
            "WALKING" -> "walk 🚶‍♂️💨"
            "HIKING" -> "hike 🥾💨"
            else -> "run 🏃‍♂️💨"
        }
        return "Just finished a $formattedDistance $sport with Apex!"
    }

    /**
     * Saves the rendered bitmap to application cache: `context.cacheDir.resolve("shared_workouts/apex_${activityId}.png")`.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, activityId: String): File {
        val shareDir = context.cacheDir.resolve(SHARED_WORKOUTS_DIR)
        if (!shareDir.exists()) {
            shareDir.mkdirs()
        } else {
            // Prune older cached workout share images to avoid disk cache accumulation
            val oldFiles = shareDir.listFiles()
            if (oldFiles != null && oldFiles.size > 5) {
                oldFiles.sortedBy { it.lastModified() }
                    .take(oldFiles.size - 5)
                    .forEach { runCatching { it.delete() } }
            }
        }

        val file = shareDir.resolve(getShareFileName(activityId))
        FileOutputStream(file).use { outStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            outStream.flush()
        }
        return file
    }

    /**
     * Renders and saves the workout share card to cache, guaranteeing that the generated native
     * bitmap is safely recycled immediately to prevent memory leaks.
     */
    fun renderAndSaveWorkoutShareCard(context: Context, summary: WorkoutSummaryUiState): File {
        val bitmap = generateWorkoutShareCardBitmap(summary)
        return try {
            saveBitmapToCache(context, bitmap, summary.activityId)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * Obtains the FileProvider content URI for the given file.
     */
    fun getShareUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Constructs the ACTION_SEND Intent with EXTRA_STREAM pointing to the share card URI,
     * wrapped in a chooser intent.
     */
    fun createShareIntent(context: Context, uri: Uri, summary: WorkoutSummaryUiState): Intent {
        val shareMessage = getShareMessage(summary)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, "Share Workout").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * High-level entry point: renders the workout share card, writes to cache with automatic
     * bitmap recycling to avoid memory leaks, resolves the content URI via FileProvider,
     * and launches the Android share chooser.
     */
    suspend fun shareWorkoutSummary(context: Context, summary: WorkoutSummaryUiState) {
        withContext(Dispatchers.IO) {
            val file = renderAndSaveWorkoutShareCard(context, summary)
            val uri = getShareUri(context, file)
            val chooserIntent = createShareIntent(context, uri, summary)

            withContext(Dispatchers.Main) {
                context.startActivity(chooserIntent)
            }
        }
    }
}
