package com.apex.tracker.core.math

import kotlin.math.abs

/**
 * Lightweight, allocation-conscious Matrix class optimized for EKF state estimation.
 * Matrix data is stored in row-major 1D DoubleArray.
 */
class Matrix(val rows: Int, val cols: Int, val data: DoubleArray = DoubleArray(rows * cols)) {

    operator fun get(r: Int, c: Int): Double = data[r * cols + c]

    operator fun set(r: Int, c: Int, value: Double) {
        data[r * cols + c] = value
    }

    operator fun plus(other: Matrix): Matrix {
        require(rows == other.rows && cols == other.cols) { "Matrix dimensions must match for addition" }
        val result = DoubleArray(data.size)
        for (i in data.indices) {
            result[i] = data[i] + other.data[i]
        }
        return Matrix(rows, cols, result)
    }

    operator fun minus(other: Matrix): Matrix {
        require(rows == other.rows && cols == other.cols) { "Matrix dimensions must match for subtraction" }
        val result = DoubleArray(data.size)
        for (i in data.indices) {
            result[i] = data[i] - other.data[i]
        }
        return Matrix(rows, cols, result)
    }

    operator fun times(scalar: Double): Matrix {
        val result = DoubleArray(data.size)
        for (i in data.indices) {
            result[i] = data[i] * scalar
        }
        return Matrix(rows, cols, result)
    }

    operator fun times(other: Matrix): Matrix {
        require(cols == other.rows) { "Matrix multiplication dimensions mismatch: ($rows x $cols) * (${other.rows} x ${other.cols})" }
        val result = DoubleArray(rows * other.cols)
        for (i in 0 until rows) {
            val iOffset = i * cols
            val resOffset = i * other.cols
            for (k in 0 until cols) {
                val aVal = data[iOffset + k]
                val kOffset = k * other.cols
                for (j in 0 until other.cols) {
                    result[resOffset + j] += aVal * other.data[kOffset + j]
                }
            }
        }
        return Matrix(rows, other.cols, result)
    }

    fun transpose(): Matrix {
        val result = DoubleArray(rows * cols)
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[j * rows + i] = data[i * cols + j]
            }
        }
        return Matrix(cols, rows, result)
    }

    /**
     * Inverts a 2x2 matrix using closed-form analytical formula.
     * Returns null if matrix is singular (abs(det) < 1e-15 or non-finite).
     */
    fun invert2x2(): Matrix? {
        require(rows == 2 && cols == 2) { "Matrix must be 2x2 for invert2x2" }
        val a = data[0]
        val b = data[1]
        val c = data[2]
        val d = data[3]
        val det = a * d - b * c
        if (det.isNaN() || det.isInfinite() || abs(det) < 1e-15) return null
        val invDet = 1.0 / det
        if (invDet.isNaN() || invDet.isInfinite()) return null
        val m00 = d * invDet
        val m01 = -b * invDet
        val m10 = -c * invDet
        val m11 = a * invDet
        if (m00.isNaN() || m00.isInfinite() ||
            m01.isNaN() || m01.isInfinite() ||
            m10.isNaN() || m10.isInfinite() ||
            m11.isNaN() || m11.isInfinite()
        ) {
            return null
        }
        return Matrix(2, 2, doubleArrayOf(m00, m01, m10, m11))
    }

    /**
     * Determinant of a 2x2 matrix.
     */
    fun det2x2(): Double {
        require(rows == 2 && cols == 2) { "Matrix must be 2x2 for det2x2" }
        return data[0] * data[3] - data[1] * data[2]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix) return false
        if (rows != other.rows || cols != other.cols) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = rows
        result = 31 * result + cols
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String {
        return buildString {
            append("Matrix(${rows}x${cols})[\n")
            for (i in 0 until rows) {
                append("  [")
                for (j in 0 until cols) {
                    append(this@Matrix[i, j])
                    if (j < cols - 1) append(", ")
                }
                append("]\n")
            }
            append("]")
        }
    }

    companion object {
        fun identity(size: Int): Matrix {
            require(size > 0) { "Matrix size must be positive" }
            val m = Matrix(size, size)
            for (i in 0 until size) {
                m[i, i] = 1.0
            }
            return m
        }

        fun diag(vararg values: Double): Matrix {
            val size = values.size
            require(size > 0) { "Diagonal values must not be empty" }
            val m = Matrix(size, size)
            for (i in 0 until size) {
                m[i, i] = values[i]
            }
            return m
        }

        fun zeros(rows: Int, cols: Int): Matrix {
            require(rows > 0 && cols > 0) { "Matrix dimensions must be positive" }
            return Matrix(rows, cols)
        }
    }
}
