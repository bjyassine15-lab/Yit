package com.example.data.model

data class LiveClientMessage(
    val setup: SetupMessage? = null,
    val clientContent: ClientContentMessage? = null,
    val realtimeInput: RealtimeInputMessage? = null
)

data class SetupMessage(
    val model: String,
    val systemInstruction: Content? = null
)

data class ClientContentMessage(
    val turns: List<Content>,
    val turnComplete: Boolean
)

data class RealtimeInputMessage(
    val mediaChunks: List<MediaChunk>
)

data class MediaChunk(
    val mimeType: String,
    val data: String
)

data class LiveServerMessage(
    val setupComplete: SetupCompleteMessage? = null,
    val serverContent: ServerContentMessage? = null
)

data class SetupCompleteMessage(val name: String? = null)

data class ServerContentMessage(
    val modelTurn: Content? = null,
    val interrupted: Boolean? = null,
    val turnComplete: Boolean? = null
)

data class Content(
    val role: String? = null,
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

// For standard REST API fallback
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

data class Candidate(
    val content: Content? = null
)
