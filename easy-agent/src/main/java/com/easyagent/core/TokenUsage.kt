package com.easyagent.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedTokens: Int = 0
) {
    val hasData: Boolean
        get() = promptTokens > 0 || completionTokens > 0 || totalTokens > 0

    operator fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        totalTokens = totalTokens + other.totalTokens,
        cachedTokens = cachedTokens + other.cachedTokens
    )

    companion object {
        val ZERO = TokenUsage()

        fun fromUsageJson(usageObj: JsonObject?): TokenUsage? {
            if (usageObj == null) return null

            val prompt = usageObj.firstInt(
                "input_tokens",
                "prompt_tokens",
                "prompt_token_count"
            ) ?: 0
            val completion = usageObj.firstInt(
                "output_tokens",
                "completion_tokens",
                "candidates_token_count"
            ) ?: 0
            val total = usageObj.firstInt("total_tokens", "total_token_count")
                ?: (prompt + completion).takeIf { it > 0 }
                ?: 0
            val cached = usageObj.cachedTokensFromDetails()

            if (prompt == 0 && completion == 0 && total == 0) return null
            return TokenUsage(
                promptTokens = prompt,
                completionTokens = completion,
                totalTokens = total,
                cachedTokens = cached
            )
        }

        private fun JsonObject.firstInt(vararg keys: String): Int? {
            for (key in keys) {
                this[key]?.jsonPrimitive?.content?.toIntOrNull()?.let { return it }
            }
            return null
        }

        private fun JsonObject.cachedTokensFromDetails(): Int {
            for (detailsKey in listOf("input_tokens_details", "prompt_tokens_details")) {
                this[detailsKey]?.jsonObject
                    ?.get("cached_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                    ?.let { return it }
            }
            return 0
        }
    }
}

data class TokenUsageReport(
    val total: TokenUsage,
    val stepUsages: List<TokenUsage> = emptyList()
) {
    val apiCallCount: Int get() = stepUsages.size
}
