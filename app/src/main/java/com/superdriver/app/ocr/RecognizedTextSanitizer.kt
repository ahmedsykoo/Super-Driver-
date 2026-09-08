package com.superdriver.app.ocr

class RecognizedTextSanitizer {
    fun sanitize(rawText: String): String {
        val normalizedRawText = rawText
            .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
            .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
            .replace('٨', '8').replace('٩', '9')
            .replace('٫', '.').replace('٬', ',')

        val lines = normalizedRawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return ""

        val platformLineIndex = lines.indexOfFirst { line ->
            platformLineRegex.containsMatchIn(line)
        }
        val candidateLines = if (platformLineIndex > 0) {
            lines.drop(platformLineIndex)
        } else {
            lines
        }

        return candidateLines
            .filterIndexed { index, line -> !line.isOwnOverlayLine(candidateLines, index) }
            .joinToString("\n")
    }

    private fun String.isOwnOverlayLine(lines: List<String>, index: Int): Boolean {
        return ownDecisionLabelRegex.matches(this) ||
            ownProfitabilityMetricRegex.containsMatchIn(this) ||
            ownOverlayReasonRegex.containsMatchIn(this) ||
            (ownOverlayFareRegex.matches(this) && hasAdjacentOwnOverlayLine(lines, index))
    }

    private fun hasAdjacentOwnOverlayLine(lines: List<String>, index: Int): Boolean {
        val previous = lines.getOrNull(index - 1)
        val next = lines.getOrNull(index + 1)
        return listOfNotNull(previous, next).any { line ->
            ownDecisionLabelRegex.matches(line) ||
                ownProfitabilityMetricRegex.containsMatchIn(line) ||
                ownOverlayReasonRegex.containsMatchIn(line)
        }
    }

    private companion object {
        val platformLineRegex = Regex("""(?i)(?:\b(?:uberx?|didi|cabify)\b|أوبر)""")
        val ownDecisionLabelRegex = Regex("""(?i)^(?:aceptar|rechazar|revisar|قبول|رفض|مراجعة)$""")
        val ownOverlayFareRegex = Regex("""(?i)^(?:\$\s*|جنيه\s*|ج\.?\s*م\.?\s*)\d+(?:[.,]\d+)?$""")
        val ownProfitabilityMetricRegex = Regex("""(?i)(?:\$/h|\$/km|\$\s*\d+(?:[.,]\d+)?\s*/\s*(?:h|km)|جنيه\s*/\s*(?:ساعة|كم)|\d+(?:[.,]\d+)?\s*(?:جنيه/ساعة|جنيه/كم))""")
        val ownOverlayReasonRegex = Regex(
            """(?i)(?:por debajo del minimo|dentro de tolerancia|ganancia neta|plataforma no|distancia incompleta|tiempo incompleto|أقل من الحد الأدنى|داخل هامش المراجعة|صافي الربح|المسافة غير مكتملة|الوقت غير مكتمل)"""
        )
    }
}
