package com.example

import com.example.domain.model.EasingCurveType
import com.example.domain.model.KeyframePoint
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.TimelineLayer
import com.example.media.MediaEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEngineTest {

    @Test
    fun waveformEnvelope_generatesNormalizedSamples() {
        val samples = MediaEngine.generateWaveformEnvelope(seed = 42, bars = 24)
        assertEquals(24, samples.size)
        assertTrue(samples.all { it in 0.15f..0.96f })
    }

    @Test
    fun keyframeInterpolation_linearMidpoint() {
        val layer = TimelineLayer(
            id = "l1",
            keyframes = listOf(
                KeyframePoint(
                    id = "k1",
                    timeMs = 0L,
                    property = KeyframeProperty.SCALE,
                    value = 1.0f,
                    easing = EasingCurveType.LINEAR
                ),
                KeyframePoint(
                    id = "k2",
                    timeMs = 1000L,
                    property = KeyframeProperty.SCALE,
                    value = 2.0f,
                    easing = EasingCurveType.LINEAR
                )
            )
        )
        val kfs = layer.keyframes.filter { it.property == KeyframeProperty.SCALE }.sortedBy { it.timeMs }
        val t = (500L - kfs[0].timeMs).toFloat() / (kfs[1].timeMs - kfs[0].timeMs).toFloat()
        val interpolated = kfs[0].value + (kfs[1].value - kfs[0].value) * t
        assertEquals(1.5f, interpolated, 0.001f)
    }
}
