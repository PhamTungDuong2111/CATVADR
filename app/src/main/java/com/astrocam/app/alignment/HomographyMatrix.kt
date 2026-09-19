package com.astrocam.app.alignment

/**
 * 3x3 Homography Matrix stored in Column-Major order for direct OpenGL ES shader binding.
 *
 * | values[0]  values[3]  values[6] |
 * | values[1]  values[4]  values[7] |
 * | values[2]  values[5]  values[8] |
 */
data class HomographyMatrix(
    val values: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )
) {
    val isIdentity: Boolean
        get() = values[0] == 1f && values[4] == 1f && values[8] == 1f &&
                values[1] == 0f && values[2] == 0f && values[3] == 0f &&
                values[5] == 0f && values[6] == 0f && values[7] == 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HomographyMatrix
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        val Identity = HomographyMatrix()
    }
}
