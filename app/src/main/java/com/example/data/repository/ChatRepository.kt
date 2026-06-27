package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessage
import com.example.data.model.Content
import com.example.data.model.GenerateContentRequest
import com.example.data.model.GenerationConfig
import com.example.data.model.Part
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(private val chatDao: ChatDao) {

    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    suspend fun insertMessage(text: String, isUser: Boolean): ChatMessage {
        val message = ChatMessage(text = text, isUser = isUser)
        val id = chatDao.insertMessage(message)
        return message.copy(id = id)
    }

    suspend fun clearHistory() {
        chatDao.clearHistory()
    }

    suspend fun getGeminiResponse(userPrompt: String, apiKey: String): String {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "ERROR_API_KEY_MISSING"
        }

        // Fetch recent message history to build conversational memory
        val historyList = chatDao.getAllMessages().first()
        // Format history as context
        val contextBuilder = StringBuilder()
        contextBuilder.append("أنت أحمد، مساعد ذكي، ودود، وسريع الاستجابة ومحترم جداً. تتحدث باللغة العربية الفصحى أو اللهجة العامية المريحة للمستخدم بشكل طبيعي للغاية ومختصر ومباشر.\n\n")
        contextBuilder.append("سياق المحادثة السابقة:\n")
        
        // Take last 15 messages for history context
        val recentHistory = historyList.takeLast(15)
        for (msg in recentHistory) {
            val sender = if (msg.isUser) "المستخدم" else "أحمد"
            contextBuilder.append("$sender: ${msg.text}\n")
        }
        
        contextBuilder.append("\nالرسالة الجديدة الحالية من المستخدم: $userPrompt\n")
        contextBuilder.append("أجب الآن كمساعد ذكي مباشرة، وبشكل مختصر جداً وواضح (بين جملة إلى ثلاث جمل كحد أقصى) لتسهيل تجربة السماع الصوتي المستمر:\n")

        val systemPrompt = "أنت أحمد، مساعد ذكي، سريع البديهة والرد، تجيب بلغة عربية سلسة ومختصرة جداً (لا تزيد عن 3 جمل)."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = contextBuilder.toString())))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.7f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            replyText?.trim() ?: "معذرةً، لم أستطع فهم ذلك بشكل صحيح."
        } catch (e: Exception) {
            "عذراً، حدث خطأ أثناء الاتصال بالخادم: ${e.localizedMessage}"
        }
    }
}
