package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BluetoothMultiplayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DenizCraft", appName)
  }

  @Test
  fun `bluetooth manager initializes safely on virtual device`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val btManager = BluetoothMultiplayerManager(context)
    btManager.registerReceiver()
    val state = btManager.state.value
    assertNotNull(state.statusLog)
    btManager.release()
  }
}
