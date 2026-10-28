package dev.intent.app.testing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedEmulatorGuardTest {
    @Test
    fun acceptsManagedEmulatorOnlyWhenKernelConfirmsEmulation() {
        val fingerprint = "google/sdk_gphone64_arm64/emu64a:14/UE1A/12077443:userdebug/dev-keys"
        assertTrue(isManagedEmulator("1", fingerprint))
        assertFalse(isManagedEmulator("0", fingerprint))
        assertFalse(isManagedEmulator("", fingerprint))
    }

    @Test
    fun rejectsPhysicalPixelAndGenericFingerprints() {
        val pixel = "google/husky/husky:14/AP2A/123456:user/release-keys"
        for (qemu in listOf("", "0", "1")) {
            assertFalse(isManagedEmulator(qemu, pixel))
            assertFalse(isManagedEmulator(qemu, "generic"))
            assertFalse(isManagedEmulator(qemu, ""))
        }
    }
}
