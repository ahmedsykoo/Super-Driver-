package com.superdriver.app.debug

import android.util.Log

internal object SuperDriverDebugLogger {
    private const val TAG = "SuperDriverDebug"
    private const val CHUNK_SIZE = 3_500

    fun log(label: String, value: Any?) {
        val message = "$label:\n${value ?: "null"}"
        if (message.length <= CHUNK_SIZE) {
            write(message)
            return
        }

        message.chunked(CHUNK_SIZE).forEachIndexed { index, chunk ->
            write("$label [${index + 1}]:\n$chunk")
        }
    }

    private fun write(message: String) {
        // TODO: remover estos logs de diagnostico antes de preparar una build release.
        runCatching { Log.d(TAG, message) }
            .onFailure { println("$TAG: $message") }
    }
}
