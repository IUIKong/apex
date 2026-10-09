package com.apex.tracker.core.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MatrixTest {

    @Test
    fun testIdentityAndDiagonal() {
        val eye = Matrix.identity(3)
        assertThat(eye[0, 0]).isEqualTo(1.0)
        assertThat(eye[1, 1]).isEqualTo(1.0)
        assertThat(eye[2, 2]).isEqualTo(1.0)
        assertThat(eye[0, 1]).isEqualTo(0.0)

        val diag = Matrix.diag(2.0, 5.0, 7.0)
        assertThat(diag[0, 0]).isEqualTo(2.0)
        assertThat(diag[1, 1]).isEqualTo(5.0)
        assertThat(diag[2, 2]).isEqualTo(7.0)
        assertThat(diag[1, 2]).isEqualTo(0.0)
    }

    @Test
    fun testMatrixAdditionAndSubtraction() {
        val a = Matrix(2, 2, doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        val b = Matrix(2, 2, doubleArrayOf(5.0, 6.0, 7.0, 8.0))
        val sum = a + b
        assertThat(sum[0, 0]).isEqualTo(6.0)
        assertThat(sum[1, 1]).isEqualTo(12.0)

        val diff = b - a
        assertThat(diff[0, 0]).isEqualTo(4.0)
        assertThat(diff[1, 1]).isEqualTo(4.0)

        val scaled = a * 2.0
        assertThat(scaled[0, 0]).isEqualTo(2.0)
        assertThat(scaled[1, 1]).isEqualTo(8.0)
    }

    @Test
    fun testMatrixMultiplication() {
        // [1 2] * [5 6] = [1*5 + 2*7, 1*6 + 2*8] = [19, 22]
        // [3 4]   [7 8]   [3*5 + 4*7, 3*6 + 4*8]   [43, 50]
        val a = Matrix(2, 2, doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        val b = Matrix(2, 2, doubleArrayOf(5.0, 6.0, 7.0, 8.0))
        val c = a * b

        assertThat(c[0, 0]).isEqualTo(19.0)
        assertThat(c[0, 1]).isEqualTo(22.0)
        assertThat(c[1, 0]).isEqualTo(43.0)
        assertThat(c[1, 1]).isEqualTo(50.0)
    }

    @Test
    fun testMatrixTranspose() {
        val a = Matrix(2, 3, doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
        val at = a.transpose()

        assertThat(at.rows).isEqualTo(3)
        assertThat(at.cols).isEqualTo(2)
        assertThat(at[0, 0]).isEqualTo(1.0)
        assertThat(at[1, 0]).isEqualTo(2.0)
        assertThat(at[2, 0]).isEqualTo(3.0)
        assertThat(at[0, 1]).isEqualTo(4.0)
        assertThat(at[1, 1]).isEqualTo(5.0)
        assertThat(at[2, 1]).isEqualTo(6.0)
    }

    @Test
    fun test2x2InversionAndDeterminant() {
        // [4 7] det = 4*6 - 7*2 = 24 - 14 = 10
        // [2 6] inv = [0.6 -0.7; -0.2 0.4]
        val m = Matrix(2, 2, doubleArrayOf(4.0, 7.0, 2.0, 6.0))
        assertThat(m.det2x2()).isEqualTo(10.0)

        val inv = m.invert2x2()
        assertThat(inv).isNotNull()
        val prod = m * inv!!

        assertThat(prod[0, 0]).isWithin(1e-9).of(1.0)
        assertThat(prod[0, 1]).isWithin(1e-9).of(0.0)
        assertThat(prod[1, 0]).isWithin(1e-9).of(0.0)
        assertThat(prod[1, 1]).isWithin(1e-9).of(1.0)
    }

    @Test
    fun testSingularMatrixReturnsNullInverse() {
        // [1 2; 2 4] det = 0
        val m = Matrix(2, 2, doubleArrayOf(1.0, 2.0, 2.0, 4.0))
        assertThat(m.invert2x2()).isNull()
    }

    @Test
    fun testNaNAndInfiniteInversionReturnsNull() {
        val mNan = Matrix(2, 2, doubleArrayOf(Double.NaN, 2.0, 3.0, 4.0))
        assertThat(mNan.invert2x2()).isNull()

        val mInf = Matrix(2, 2, doubleArrayOf(Double.POSITIVE_INFINITY, 2.0, 3.0, 4.0))
        assertThat(mInf.invert2x2()).isNull()
    }

    @Test
    fun testEqualsHashCodeAndToString() {
        val m1 = Matrix(2, 2, doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        val m2 = Matrix(2, 2, doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        val m3 = Matrix(2, 2, doubleArrayOf(1.0, 2.0, 3.0, 5.0))

        assertThat(m1).isEqualTo(m2)
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode())
        assertThat(m1).isNotEqualTo(m3)
        assertThat(m1.toString()).contains("Matrix(2x2)")
    }
}
