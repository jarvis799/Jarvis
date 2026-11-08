package com.jarvis.assistant.ui.chat

import android.app.Application
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.jarvis.assistant.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChatComposerLayoutTest {
    @Test fun sendStaysInsideComposerOnNarrowScreensAndWithKeyboard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (widthDp in listOf(320, 360, 411)) for (font in listOf(1f, 1.3f)) {
            val config = Configuration(app.resources.configuration).apply {
                screenWidthDp = widthDp; fontScale = font
            }
            val context = ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_JarvisAssistant)
            val root = LayoutInflater.from(context).inflate(R.layout.fragment_chat, null) as ViewGroup
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (430 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val send = root.findViewById<View>(R.id.btnSendMessage)
            val input = root.findViewById<EditText>(R.id.etMessageInput)
            val rect = Rect(); send.getDrawingRect(rect)
            root.offsetDescendantRectToMyCoords(send, rect)
            assertEquals(View.VISIBLE, send.visibility)
            assertTrue("Send outside width=$widthDp font=$font: $rect", rect.left >= 0 && rect.right <= width)
            assertTrue("Send outside resized height: $rect", rect.top >= 0 && rect.bottom <= height)
            assertTrue("Message input collapsed", input.measuredWidth > (80 * density).toInt())
            assertTrue("Send touch target collapsed", send.measuredWidth >= (48 * density).toInt())
        }
    }
}
