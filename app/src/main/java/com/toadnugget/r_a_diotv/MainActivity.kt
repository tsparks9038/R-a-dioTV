package com.toadnugget.r_a_diotv

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
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
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var engine: CronetEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engineBuilder: CronetEngine.Builder = CronetEngine.Builder(applicationContext)
        engine = engineBuilder.build()
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
                    var main by remember { mutableStateOf<JSONObject?>(null) }
                    var dj by remember { mutableStateOf<JSONObject?>(null) }
                    var queue by remember { mutableStateOf<JSONArray?>(null) }
                    var lp by remember { mutableStateOf<JSONArray?>(null) }

                    // Update responseText on the main thread
                    LaunchedEffect(callback) {
                        callback.responseBodyFlow.collect { newResponseText ->
                            responseText = newResponseText
                            Log.d("MainActivity", "responseText updated: $responseText")

                            if (responseText.isNotEmpty()) {
                                try {
                                    val jObject = JSONObject(responseText)
                                    main = jObject.getJSONObject("main")
                                    dj = main?.getJSONObject("dj")
                                    queue = main?.getJSONArray("queue")
                                    lp = main?.getJSONArray("lp")
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error parsing JSON: ${e.message}")
                                }
                            }
                        }
                    }

                    if (main != null && dj != null && queue != null && lp != null) {
                        Main(
                            main!!.getString("np"),
                            main!!.getInt("listeners"),
                            main!!.getLong("current"),
                            main!!.getLong("start_time"),
                            main!!.getLong("end_time")
                        )
                        Dj(
                            dj!!.getString("djname"),
                            "https://r-a-d.io/api/dj-image/" + dj!!.getString("djimage")
                        )

                        Lp(lp!!, main!!.getLong("current"))
                        Queue(queue!!, main!!.getLong("current"))
                    } else {
                        Text(text = "Loading...")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.shutdown()
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
        CoroutineScope(Dispatchers.Main).launch {
            _responseBodyFlow.value = ""
            Log.e("MyCallback", "Request failed: ${error?.message}")
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun Main(np: String, listeners: Int, current: Long, start_time: Long, end_time: Long) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(np) // Current song

        Row {
            val time = current - start_time
            val end = end_time - start_time
            val timeStr = String.format("%02d:%02d", time / 60, time % 60)
            val endStr = String.format("%02d:%02d", end / 60, end % 60)

            Text("Listeners: $listeners") // Listeners
            Spacer(modifier = Modifier.size(50.dp))
            Text("$timeStr / $endStr")
        }
    }
}

@Composable
fun Dj(djname: String, djimage: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.End
    ) {
        AsyncImage(model = djimage, contentDescription = null, modifier = Modifier.size(180.dp))
        Text(djname) // DJ
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun Lp(lp: JSONArray, current: Long) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart // Align content to the bottom start
    ) {
        Column (
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .wrapContentWidth(align = Alignment.Start),
            verticalArrangement = Arrangement.Center
        ) {
            for (i in 0 until lp.length()) {
                val song = lp.getJSONObject(i)
                val meta = song.getString("meta")
                val time = current - song.getLong("timestamp")
                val timeStr = String.format("%02d:%02d", time / 60, time % 60)

                Text("$meta - $timeStr ago")
                Spacer(modifier = Modifier.size(25.dp))
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun Queue(queue: JSONArray, current: Long) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd // Align content to the bottom start
    ) {
        Column (
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .wrapContentWidth(align = Alignment.End),
            verticalArrangement = Arrangement.Center
        ) {
            for (i in 0 until queue.length()) {
                val song = queue.getJSONObject(i)
                val meta = song.getString("meta")
                val time = song.getLong("timestamp") - current
                val timeStr = String.format("%02d:%02d", time / 60, time % 60)

                Text("$meta - in $timeStr")
                Spacer(modifier = Modifier.size(25.dp))
            }
        }
    }
}