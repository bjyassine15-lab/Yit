package com.example.data.remote

import android.util.Base64
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object GeminiLiveApi {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val clientAdapter = moshi.adapter(LiveClientMessage::class.java)
    private val serverAdapter = moshi.adapter(LiveServerMessage::class.java)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Important for WebSockets
        .build()

    fun connect(
        apiKey: String,
        onMessage: (LiveServerMessage) -> Unit,
        onClose: () -> Unit,
        onError: (Throwable) -> Unit
    ): WebSocket {
        // Use the exact model requested by the user
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setup = LiveClientMessage(
                    setup = SetupMessage(
                        model = "models/gemini-3.1-flash-live-preview", // Latest Live API Model requested
                        systemInstruction = Content(
                            role = "system",
                            parts = listOf(Part(text = "أنت أحمد، مساعد ذكي، سريع البديهة والرد، وودود للغاية. تتحدث باللغة العربية بشكل طبيعي ومباشر. لا تطل في الردود لتكون المحادثة سلسة ومستمرة."))
                        )
                    )
                )
                webSocket.send(clientAdapter.toJson(setup))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = serverAdapter.fromJson(text)
                    if (msg != null) onMessage(msg)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClose()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError(t)
            }
        })
    }
    
    fun sendAudio(webSocket: WebSocket, pcmData: ByteArray) {
        val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val msg = LiveClientMessage(
            realtimeInput = RealtimeInputMessage(
                mediaChunks = listOf(
                    MediaChunk(mimeType = "audio/pcm;rate=16000", data = base64)
                )
            )
        )
        webSocket.send(clientAdapter.toJson(msg))
    }

    fun sendText(webSocket: WebSocket, text: String) {
        val msg = LiveClientMessage(
            clientContent = ClientContentMessage(
                turns = listOf(
                    Content(role = "user", parts = listOf(Part(text = text)))
                ),
                turnComplete = true
            )
        )
        webSocket.send(clientAdapter.toJson(msg))
    }
}
