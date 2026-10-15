package com.sameerasw.airsync.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.health.HealthStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * OpenRouter AI integration for personalized Health & Fitness recommendations.
 * Strictly uses free-tier models (suffix ':free' or 'openrouter/free') to guarantee zero-cost usage for users.
 */
object OpenRouterHealthCoach {

    private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"

    val DEFAULT_FREE_MODEL = "openrouter/free"

    data class FreeModel(
        val id: String,
        val displayName: String,
        val provider: String,
        val description: String
    )

    // Curated high-performance free models verified on OpenRouter
    val CURATED_FREE_MODELS: List<FreeModel> = listOf(
        // OpenRouter Auto-Router
        FreeModel(
            id = "openrouter/free",
            displayName = "OpenRouter Free Router",
            provider = "OpenRouter",
            description = "Auto-routes to the best active, available free model on OpenRouter."
        ),

        // NVIDIA
        FreeModel(
            id = "nvidia/nemotron-3-ultra:free",
            displayName = "Nemotron 3 Ultra",
            provider = "NVIDIA",
            description = "Complex reasoning and long-context analysis."
        ),
        FreeModel(
            id = "nvidia/nemotron-3.5-lightning:free",
            displayName = "Nemotron 3.5 Lightning",
            provider = "NVIDIA",
            description = "Fast responses and high-throughput workloads."
        ),
        FreeModel(
            id = "nvidia/nemotron-3-super:free",
            displayName = "Nemotron 3 Super",
            provider = "NVIDIA",
            description = "Multi-step reasoning and agent workflows."
        ),
        FreeModel(
            id = "nvidia/nemotron-3-nano-omni:free",
            displayName = "Nemotron 3 Nano Omni",
            provider = "NVIDIA",
            description = "Multimodal analysis of text, images, audio and video."
        ),
        FreeModel(
            id = "nvidia/nemotron-3.5-super-vl:free",
            displayName = "Nemotron 3.5 Super VL",
            provider = "NVIDIA",
            description = "Visual understanding and document analysis."
        ),

        // Google
        FreeModel(
            id = "google/gemma-4-26b-a4b-it:free",
            displayName = "Gemma 4 26B A4B",
            provider = "Google",
            description = "Efficient reasoning and multimodal assistance."
        ),
        FreeModel(
            id = "google/gemma-4-31b-it:free",
            displayName = "Gemma 4 31B",
            provider = "Google",
            description = "Detailed explanations and general-purpose reasoning."
        ),

        // Poolside
        FreeModel(
            id = "poolside/laguna-s-2.1:free",
            displayName = "Laguna S 2.1",
            provider = "Poolside",
            description = "Coding, structured tasks and agentic workflows."
        ),
        FreeModel(
            id = "poolside/laguna-xs-2.1:free",
            displayName = "Laguna XS 2.1",
            provider = "Poolside",
            description = "Lightweight coding and tool-assisted tasks."
        ),

        // Dots Studio
        FreeModel(
            id = "dots-studio/dots3-note-preview:free",
            displayName = "Dots3-Note Preview",
            provider = "Dots Studio",
            description = "Reasoning, coding and long-context tasks."
        ),

        // Thinking Machines Lab
        FreeModel(
            id = "thinking-machines/inkling:free",
            displayName = "Inkling",
            provider = "Thinking Machines",
            description = "Multimodal reasoning and general assistance."
        ),
        FreeModel(
            id = "thinking-machines/inkling-small:free",
            displayName = "Inkling Small",
            provider = "Thinking Machines",
            description = "Efficient reasoning and multilingual chat."
        ),

        // Cohere
        FreeModel(
            id = "cohere/north-mini-code:free",
            displayName = "North Mini Code",
            provider = "Cohere",
            description = "Code generation and structured tool use."
        ),

        // Liquid AI
        FreeModel(
            id = "liquid/lfm2.5-2.6b:free",
            displayName = "LFM2.5 2.6B",
            provider = "Liquid AI",
            description = "Lightweight data extraction and summarization."
        ),

        // Fallbacks
        FreeModel(
            id = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "Llama 3.3 70B",
            provider = "Meta",
            description = "General-purpose instruction following."
        ),
        FreeModel(
            id = "deepseek/deepseek-r1:free",
            displayName = "DeepSeek R1",
            provider = "DeepSeek",
            description = "Analytical reasoning and problem solving."
        ),
        FreeModel(
            id = "qwen/qwen-2.5-72b-instruct:free",
            displayName = "Qwen 2.5 72B",
            provider = "Alibaba",
            description = "Instruction following and general assistance."
        ),
        FreeModel(
            id = "mistralai/mistral-small-24b-instruct-2501:free",
            displayName = "Mistral Small 24B",
            provider = "Mistral",
            description = "Concise responses and structured suggestions."
        ),
        FreeModel(
            id = "meta-llama/llama-3.1-8b-instruct:free",
            displayName = "Llama 3.1 8B",
            provider = "Meta",
            description = "Lightweight conversational responses."
        )
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Enforces free models only. Replaces paid or non-free models with the default free model.
     */
    fun sanitizeModelId(modelId: String): String {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return DEFAULT_FREE_MODEL
        val lower = trimmed.lowercase()
        return if (lower == "openrouter/free" || lower.endsWith("/free") || lower.endsWith(":free")) {
            trimmed
        } else {
            DEFAULT_FREE_MODEL
        }
    }

    /**
     * Fetches live free models dynamically from https://openrouter.ai/api/v1/models
     */
    suspend fun fetchLiveFreeModels(): List<FreeModel> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(OPENROUTER_MODELS_URL)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext CURATED_FREE_MODELS

            val body = response.body?.string() ?: return@withContext CURATED_FREE_MODELS
            val root = gson.fromJson(body, JsonObject::class.java)
            val dataArray = root.getAsJsonArray("data") ?: return@withContext CURATED_FREE_MODELS

            val models = CURATED_FREE_MODELS.toMutableList()
            val existingIds = models.map { it.id.lowercase() }.toSet()

            for (element in dataArray) {
                val obj = element.asJsonObject ?: continue
                val id = obj.get("id")?.asString ?: continue
                val lowerId = id.lowercase()
                val isFree = lowerId == "openrouter/free" || lowerId.endsWith("/free") || lowerId.endsWith(":free")
                if (isFree && !existingIds.contains(lowerId)) {
                    val name = obj.get("name")?.asString ?: id
                    val desc = obj.get("description")?.asString ?: "Free model on OpenRouter."
                    val provider = id.substringBefore("/").replaceFirstChar { it.uppercase() }
                    models.add(FreeModel(id = id, displayName = name, provider = provider, description = desc))
                }
            }
            models
        } catch (e: Exception) {
            CURATED_FREE_MODELS
        }
    }

    /**
     * Tests the user's OpenRouter API key and selected free model.
     */
    suspend fun testConnection(apiKey: String, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be empty."))
        }

        val verifiedModel = sanitizeModelId(modelId)

        val jsonBody = JsonObject().apply {
            addProperty("model", verifiedModel)
            val messagesArray = com.google.gson.JsonArray()
            val userMsg = JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", "Hello! Confirm you are active and free to use.")
            }
            messagesArray.add(userMsg)
            add("messages", messagesArray)
            addProperty("max_tokens", 20)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(OPENROUTER_API_URL)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("HTTP-Referer", "https://github.com/sameerasw/airsync")
            .addHeader("X-Title", "AirSync Health")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val parsed = gson.fromJson(bodyStr, JsonObject::class.java)
                        parsed.getAsJsonObject("error")?.get("message")?.asString ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $bodyStr"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success("Connection successful with $verifiedModel!")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates personalized health and fitness recommendations based on HealthStats.
     */
    suspend fun generateHealthSuggestions(
        context: Context,
        stats: HealthStats,
        date: LocalDate
    ): Result<String> = withContext(Dispatchers.IO) {
        val dataStore = DataStoreManager(context)
        val apiKey = dataStore.getOpenRouterApiKey().first()
        val enabled = dataStore.isOpenRouterEnabled().first()

        if (!enabled) {
            return@withContext Result.failure(IllegalStateException("OpenRouter AI integration is disabled in Settings."))
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenRouter API key is not configured. Please set your key in Settings."))
        }

        val rawModel = dataStore.getOpenRouterModel().first()
        val model = sanitizeModelId(rawModel)

        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
        val dateString = date.format(dateFormatter)

        val metricsSummary = buildString {
            appendLine("Health Data for $dateString:")
            appendLine("- Steps: ${if (stats.steps > 0) "${stats.steps} / 10,000 goal" else "No step records"}")
            appendLine("- Active Calories Burned: ${if (stats.calories > 0) String.format("%.0f kcal / 500 kcal goal", stats.calories) else "No calorie records"}")
            appendLine("- Active Minutes: ${if (stats.activeMinutes > 0) "${stats.activeMinutes} min / 60 min goal" else "No active minutes recorded"}")
            appendLine("- Distance: ${if (stats.distance > 0) String.format("%.2f km", stats.distance) else "No distance recorded"}")
            appendLine("- Sleep Duration: ${if (stats.sleepHours > 0) String.format("%.1f hours", stats.sleepHours) else "No sleep records"}")
            appendLine("- Heart Rate: ${if (stats.heartRate > 0) "Avg ${stats.heartRate} bpm (Range: ${stats.heartRateMin}-${stats.heartRateMax} bpm)" else "No heart rate records"}")
            if (stats.restingHeartRate > 0) {
                appendLine("- Resting Heart Rate: ${stats.restingHeartRate} bpm")
            }
            if (stats.floorsClimbed > 0) {
                appendLine("- Floors Climbed: ${stats.floorsClimbed}")
            }
            if (stats.hydration > 0) {
                appendLine("- Hydration: ${String.format("%.1f L", stats.hydration)}")
            }
            if (stats.weight > 0) {
                appendLine("- Weight: ${String.format("%.1f kg", stats.weight)}")
            }
        }

        val systemPrompt = """
            You are an expert personal health, fitness, and recovery coach.
            Your role is to analyze health metrics synced from Health Connect and provide encouraging, practical, and scientifically sound advice.
            Format your response cleanly using bullet points:
            1. 📊 Daily Performance Summary (1-2 sentences highlighting key achievements or gaps)
            2. 💡 Recovery & Body Signals (insights on sleep, heart rate, or strain)
            3. 🎯 3 Actionable Suggestions (targeted recommendations for movement, hydration, or recovery for tomorrow)
            Keep the tone motivating, friendly, and concise. Avoid disclaimers.
        """.trimIndent()

        val jsonBody = JsonObject().apply {
            addProperty("model", model)
            val messagesArray = com.google.gson.JsonArray()

            val systemMsg = JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            }
            val userMsg = JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", metricsSummary)
            }
            messagesArray.add(systemMsg)
            messagesArray.add(userMsg)

            add("messages", messagesArray)
            addProperty("temperature", 0.7)
            addProperty("max_tokens", 700)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(OPENROUTER_API_URL)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("HTTP-Referer", "https://github.com/sameerasw/airsync")
            .addHeader("X-Title", "AirSync Health Coach")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val parsed = gson.fromJson(bodyStr, JsonObject::class.java)
                        parsed.getAsJsonObject("error")?.get("message")?.asString ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $bodyStr"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val parsed = gson.fromJson(bodyStr, JsonObject::class.java)
                val choices = parsed.getAsJsonArray("choices")
                if (choices == null || choices.size() == 0) {
                    return@withContext Result.failure(Exception("No suggestion returned from AI model."))
                }
                val suggestion = choices[0].asJsonObject
                    .getAsJsonObject("message")
                    ?.get("content")?.asString.orEmpty().trim()

                // Cache the suggestion in DataStore
                dataStore.saveLastAiHealthSuggestion(suggestion, date.toString())

                Result.success(suggestion)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
