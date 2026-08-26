package com.agentos.media

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CameraGeometryTest {
    @Test
    fun centeredCropPreservesSensorCenter() {
        assertArrayEquals(intArrayOf(1000, 750, 3000, 2250), centeredCrop(0, 0, 4000, 3000, 2f))
    }
}
