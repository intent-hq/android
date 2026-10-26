package dev.intent.app.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Keeps test content across emulator configuration changes, without retaining an Activity. */
class ComposeTestActivity : ComponentActivity() {
    private val contentModel: TestContent
        get() = ViewModelProvider(this)[TestContent::class.java]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { contentModel.content.value?.invoke() }
    }

    fun setTestContent(content: @Composable () -> Unit) {
        check(contentModel.content.value == null) { "Test content is already installed" }
        contentModel.content.value = content
    }

    class TestContent : ViewModel() {
        val content = mutableStateOf<(@Composable () -> Unit)?>(null)
    }
}
