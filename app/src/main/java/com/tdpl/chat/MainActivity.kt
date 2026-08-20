package com.tdpl.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.tdpl.chat.data.ModelState
import com.tdpl.chat.ui.chat.ChatScreen
import com.tdpl.chat.ui.download.DownloadScreen
import com.tdpl.chat.ui.theme.TDPLChatTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.bootstrap(BuildConfig.HF_MANIFEST_URL)

        setContent {
            TDPLChatTheme {
                val modelState by viewModel.modelState.collectAsState()
                val loadedInMemory by viewModel.isModelLoadedInMemory.collectAsState()
                val messages by viewModel.messages.collectAsState()
                val isGenerating by viewModel.isGenerating.collectAsState()

                if (modelState is ModelState.Ready && loadedInMemory) {
                    ChatScreen(
                        messages = messages,
                        isGenerating = isGenerating,
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopGenerating
                    )
                } else {
                    DownloadScreen(state = modelState)
                }
            }
        }
    }
}
