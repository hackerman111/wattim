package io.ronesec.android.data

import io.ronesec.android.data.entity.TargetAppEntity
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.StatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PolicyCompilerAnimationTest {

    private fun createEntity(animation: String) = TargetAppEntity(
        packageName = "com.test.app",
        displayName = "Test App",
        enabled = true,
        phrase = "Take a breath",
        animation = animation,
        durationMs = 5000L,
        reinterventionMs = 300000L,
        quickReturnGraceMs = 5000L,
        growthEnabled = false,
        growthPercent = 20,
        growthWindowMs = 3600000L,
        rowVersion = 1L
    )

    @Test
    fun legacyFill2MapsToFill() {
        val entity = createEntity("FILL_2")
        val config = PolicyCompiler.toTargetConfig(entity)
        assertEquals(AnimationMode.FILL, config.animation)
    }

    @Test
    fun legacyCircleMapsToOrbit() {
        val entity = createEntity("CIRCLE")
        val config = PolicyCompiler.toTargetConfig(entity)
        assertEquals(AnimationMode.ORBIT, config.animation)
    }

    @Test
    fun standardAndNewModesMapDirectly() {
        assertEquals(AnimationMode.FILL, PolicyCompiler.toTargetConfig(createEntity("FILL")).animation)
        assertEquals(AnimationMode.PULSE, PolicyCompiler.toTargetConfig(createEntity("PULSE")).animation)
        assertEquals(AnimationMode.WAVE, PolicyCompiler.toTargetConfig(createEntity("WAVE")).animation)
        assertEquals(AnimationMode.ORBIT, PolicyCompiler.toTargetConfig(createEntity("ORBIT")).animation)
        assertEquals(AnimationMode.RIPPLE, PolicyCompiler.toTargetConfig(createEntity("RIPPLE")).animation)
    }

    @Test
    fun allModesHaveUntimedFlag() {
        for (mode in AnimationMode.entries) {
            assertFalse("Animation $mode must not reveal remaining time", mode.revealsRemainingTime)
        }
    }

    @Test
    fun statsPeriodEnumHasAllExpectedValues() {
        val periods = StatsPeriod.entries.map { it.name }
        assertEquals(listOf("TODAY", "WEEK", "MONTH", "ALL_TIME"), periods)
    }
}
