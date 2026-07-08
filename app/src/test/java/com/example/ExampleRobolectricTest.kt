package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Omni Button", appName)
  }

  @Test
  fun `test default app settings schema`() {
    val settings = AppSettings()
    assertEquals("#FF6200EE", settings.iconColorHex)
    assertEquals("#FFFFFFFF", settings.iconTintHex)
    assertEquals(1.0f, settings.bgOpacity)
    assertEquals(1.0f, settings.symbolOpacity)
    assertFalse(settings.isButtonFixed)
    assertEquals(100, settings.lastX)
    assertEquals(300, settings.lastY)
  }

  @Test
  fun `test database inserts and updates`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.appSettingsDao()

    val initialSettings = AppSettings(
      id = 1,
      iconColorHex = "#FF03DAC5",
      iconTintHex = "#FF000000",
      bgOpacity = 0.5f,
      symbolOpacity = 0.75f,
      isButtonFixed = true,
      lastX = 250,
      lastY = 450
    )

    dao.insertSettings(initialSettings)
    val fetched = dao.getSettings()
    assertNotNull(fetched)
    assertEquals("#FF03DAC5", fetched?.iconColorHex)
    assertEquals("#FF000000", fetched?.iconTintHex)
    assertEquals(0.5f, fetched?.bgOpacity)
    assertEquals(0.75f, fetched?.symbolOpacity)
    assertTrue(fetched?.isButtonFixed == true)
    assertEquals(250, fetched?.lastX)
    assertEquals(450, fetched?.lastY)
  }
}
