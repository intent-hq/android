package dev.intent.app.connection

import android.Manifest
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalNetworkPermissionTest {
    @Test fun olderAndroidDoesNotRequireLocalNetworkPermission() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
        assertTrue(hasLocalNetworkAccess(context))
    }

    @Test fun android17BlocksUntilPermissionGrantedAndBlocksAgainAfterRevocation() {
        val context = RuntimeEnvironment.getApplication()
        val original = Build.VERSION.SDK_INT
        try {
            ReflectionHelpers.setStaticField(
                Build.VERSION::class.java,
                "SDK_INT",
                Build.VERSION_CODES.CINNAMON_BUN
            )
            shadowOf(context).denyPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
            assertFalse(hasLocalNetworkAccess(context))
            shadowOf(context).grantPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
            assertTrue(hasLocalNetworkAccess(context))
            shadowOf(context).denyPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
            assertFalse(hasLocalNetworkAccess(context))
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", original)
        }
    }
}
