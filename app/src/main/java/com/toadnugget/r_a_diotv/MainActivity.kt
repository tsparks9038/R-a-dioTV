package com.toadnugget.r_a_diotv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.toadnugget.r_a_diotv.ui.theme.RadioTVTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engineBuilder: CronetEngine.Builder = CronetEngine.Builder(applicationContext)
        val engine = engineBuilder.build()
        val executor: Executor = Executors.newSingleThreadExecutor()
        val callback = MyCallback()
        val requestBuilder = engine.newUrlRequestBuilder(
            "https://r-a-d.io/api",
            callback,
            executor
        )
        val request = requestBuilder.build()

        request.start()

        setContent {
            RadioTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    var responseText by remember { mutableStateOf("") }

                    // Update responseText on the main thread
                    LaunchedEffect(callback) {
                        callback.responseBodyFlow.collect { newResponseText ->
                            responseText = newResponseText
                            Log.d("MainActivity", "responseText updated: $responseText")
                        }
                    }

                    Text(text = responseText)
                }
            }
        }
    }
}

class MyCallback : UrlRequest.Callback() {
    private val _responseBodyFlow = MutableStateFlow("")
    val responseBodyFlow: StateFlow<String> = _responseBodyFlow.asStateFlow()

    private val responseBody = StringBuilder()

    override fun onRedirectReceived(
        request: UrlRequest,
        info: UrlResponseInfo,
        newLocationUrl: String
    ) {
        request.followRedirect()
    }

    override fun onResponseStarted(
        request: UrlRequest,
        info: UrlResponseInfo
    ) {
        request.read(ByteBuffer.allocateDirect(1024))
    }

    override fun onReadCompleted(
        request: UrlRequest,
        info: UrlResponseInfo,
        byteBuffer: ByteBuffer
    ) {
        byteBuffer.flip()
        val data = Charset.forName("UTF-8").decode(byteBuffer).toString()
        responseBody.append(data)
        byteBuffer.clear()
        request.read(byteBuffer)
    }

    override fun onSucceeded(
        request: UrlRequest,
        info: UrlResponseInfo
    ) {
        val responseText = responseBody.toString()
        CoroutineScope(Dispatchers.Main).launch {
            _responseBodyFlow.value = responseText
            Log.d("MyCallback", "Response: $responseText")
        }
    }

    override fun onFailed(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        error: CronetException?
    ) {
        TODO("Not yet implemented")
    }
}