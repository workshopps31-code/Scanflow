package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CropQuad
import com.example.data.model.FilterType
import com.example.data.model.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context matches DocuLens`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DocuLens", appName)
  }

  @Test
  fun `verify default crop quad and filter models`() {
    val quad = CropQuad()
    assertNotNull(quad.topLeft)
    assertNotNull(quad.bottomRight)
    assertEquals(FilterType.MAGIC_COLOR.displayName, "Magic Color")
    assertEquals(ScanMode.BATCH.title, "Batch Mode")
  }
}
