package com.easyagent.core

import android.util.Log

object AgentLogger {
    private const val TAG = "EasyAgent"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    fun apiError(provider: String, url: String, statusCode: Int, responseBody: String) {
        Log.e(
            TAG,
            buildString {
                append("API error [$provider]\n")
                append("URL: $url\n")
                append("Status: $statusCode\n")
                append("Body: $responseBody")
            }
        )
    }

    fun apiRequest(provider: String, url: String, model: String) {
        Log.d(TAG, "API request [$provider] url=$url model=$model")
    }

    fun stepTokenUsage(step: Int, usage: TokenUsage) {
        Log.d(
            TAG,
            buildString {
                append("Step $step token usage: ")
                append("prompt=${usage.promptTokens}, ")
                append("completion=${usage.completionTokens}, ")
                append("total=${usage.totalTokens}")
                if (usage.cachedTokens > 0) {
                    append(", cached=${usage.cachedTokens}")
                }
            }
        )
    }

    fun turnTokenUsage(report: TokenUsageReport) {
        val usage = report.total
        Log.i(
            TAG,
            buildString {
                append("Turn token usage (${report.apiCallCount} API call(s)): ")
                append("prompt=${usage.promptTokens}, ")
                append("completion=${usage.completionTokens}, ")
                append("total=${usage.totalTokens}")
                if (usage.cachedTokens > 0) {
                    append(", cached=${usage.cachedTokens}")
                }
            }
        )
    }
}
