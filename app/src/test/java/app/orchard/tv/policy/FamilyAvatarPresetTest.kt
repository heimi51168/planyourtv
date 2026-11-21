package app.orchard.tv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyAvatarPresetTest {
    @Test
    fun canonicalPresetAcceptsBuiltInPortraits() {
        for (preset in FamilyAvatarStore.PRESETS) {
            assertEquals(preset, FamilyAvatarStore.canonicalPreset(preset))
        }
    }

    @Test
    fun legacyAnimalPresetsMapToPortraits() {
        assertEquals("boy", FamilyAvatarStore.canonicalPreset("rabbit"))
        assertEquals("girl", FamilyAvatarStore.canonicalPreset("fox"))
        assertEquals("dad", FamilyAvatarStore.canonicalPreset("bear"))
        assertEquals("mom", FamilyAvatarStore.canonicalPreset("panda"))
    }

    @Test
    fun unknownPresetFallsBackToDefaultButCanStillBeRejected() {
        assertEquals(FamilyAvatarStore.DEFAULT_PRESET, FamilyAvatarStore.canonicalPreset("unknown"))
        assertNull(FamilyAvatarStore.resolvePreset("unknown"))
    }
}