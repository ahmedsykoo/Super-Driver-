package com.superdriver.app.capture

import java.util.concurrent.atomic.AtomicLong

internal class OcrTraceIdGenerator(
    private val prefix: String = DEFAULT_PREFIX
) {
    private val sequence = AtomicLong(0L)

    fun next(nowMillis: Long = System.currentTimeMillis()): String {
        return "$prefix-$nowMillis-${sequence.incrementAndGet()}"
    }

    private companion object {
        const val DEFAULT_PREFIX = "ocr"
    }
}
