package com.superdriver.app.ocr

import java.text.Normalizer
import java.util.Locale

data class TripOfferPresenceResult(
    val isLikelyOffer: Boolean,
    val score: Double,
    val reasons: List<String>,
    val hasActionMarker: Boolean
)

class TripOfferPresenceValidator {
    fun hasActiveOffer(rawText: String?): Boolean {
        return evaluate(rawText).isLikelyOffer
    }

    fun evaluate(rawText: String?): TripOfferPresenceResult {
        if (rawText.isNullOrBlank()) {
            return TripOfferPresenceResult(
                isLikelyOffer = false,
                score = 0.0,
                reasons = listOf("blank text"),
                hasActionMarker = false
            )
        }

        val lines = rawText.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .toList()
        if (lines.isEmpty()) {
            return TripOfferPresenceResult(
                isLikelyOffer = false,
                score = 0.0,
                reasons = listOf("no text lines"),
                hasActionMarker = false
            )
        }

        val bottomStartIndex = (lines.size * BOTTOM_SECTION_RATIO).toInt()
            .coerceIn(0, lines.lastIndex)
        val normalizedText = rawText.normalizeForEvidence()
        if (normalizedText.looksLikeOwnOverlay()) {
            return TripOfferPresenceResult(
                isLikelyOffer = false,
                score = 0.0,
                reasons = listOf("own overlay text"),
                hasActionMarker = false
            )
        }

        val hasActionMarker = lines.withIndex().any { (index, line) ->
            index >= bottomStartIndex && line.hasOfferActionMarker()
        }
        if (hasActionMarker) {
            return TripOfferPresenceResult(
                isLikelyOffer = true,
                score = 1.0,
                reasons = listOf("action marker"),
                hasActionMarker = true
            )
        }

        val reasons = mutableListOf<String>()
        var score = 0.0
        val hasFare = fareRegex.containsMatchIn(normalizedText)
        val hasKm = kmRegex.containsMatchIn(normalizedText)
        val hasMinutes = minuteRegex.containsMatchIn(normalizedText)
        val hasTripStructure = pickupStructureRegex.containsMatchIn(normalizedText) ||
            tripStructureRegex.containsMatchIn(normalizedText)
        val hasTripWord = tripWordRegex.containsMatchIn(normalizedText)
        val hasPlatform = platformRegex.containsMatchIn(normalizedText)

        if (hasFare) {
            score += 0.35
            reasons += "fare"
        }
        if (hasKm) {
            score += 0.15
            reasons += "km"
        }
        if (hasMinutes) {
            score += 0.15
            reasons += "minutes"
        }
        if (hasTripStructure) {
            score += 0.25
            reasons += "trip structure"
        } else if (hasTripWord) {
            score += 0.15
            reasons += "trip word"
        }
        if (hasPlatform) {
            score += 0.10
            reasons += "platform"
        }

        val hasStrongEvidence = hasFare &&
            hasKm &&
            hasMinutes &&
            (hasTripStructure || hasTripWord) &&
            score >= CONSERVATIVE_SCORE_THRESHOLD

        return TripOfferPresenceResult(
            isLikelyOffer = hasStrongEvidence,
            score = score.coerceAtMost(1.0),
            reasons = if (reasons.isEmpty()) listOf("insufficient evidence") else reasons,
            hasActionMarker = false
        )
    }

    private fun String.hasOfferActionMarker(): Boolean {
        val normalized = normalizeForMarker()
        if (contains("قبول")) return true
        val words = normalized.split(" ").filter { it.isNotBlank() }
        val compact = normalized.filter { it in 'a'..'z' }

        return "aceptar" in words ||
            "قبول" in words ||
            compact in EMPAREJAR_MARKER_VARIANTS
    }

    private fun String.normalizeForEvidence(): String {
        val withoutAccents = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return withoutAccents
            .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
            .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
            .replace('٨', '8').replace('٩', '9').replace('٫', '.').replace('٬', ',')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.looksLikeOwnOverlay(): Boolean {
        val hasOwnDecisionTitle = contains("بيانات غير مكتملة") ||
            contains("datos incompletos") ||
            contains("قبول") ||
            contains("رفض") ||
            contains("مراجعة") ||
            contains("aceptar") ||
            contains("rechazar") ||
            contains("revisar")
        val hasOwnMetricLine = contains("جنيه/km") && contains("جنيه/h")
        return hasOwnDecisionTitle && hasOwnMetricLine
    }

    private fun String.normalizeForMarker(): String {
        val withoutAccents = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return withoutAccents
            .replace('0', 'o')
            .replace('1', 'l')
            .replace(Regex("[^a-z]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private companion object {
        private const val BOTTOM_SECTION_RATIO = 0.55f
        private const val CONSERVATIVE_SCORE_THRESHOLD = 0.75
        val fareRegex = Regex(
            pattern = """(?i)(?:EGP\s*|\$\s*|ج\.?\s*م\.?\s*|جنيه(?:\s+مصري)?\s*)\d{1,6}(?:[.,]\d{1,3})*|\b\d{1,6}(?:[.,]\d{1,3})*\s*(?:EGP|pesos|جنيه(?:\s+مصري)?|ج\.?\s*م\.?)"""
        )
        val kmRegex = Regex(pattern = """(?i)\b\d+(?:[.,]\d+)?\s*(?:km|كم)\b""")
        val minuteRegex = Regex(pattern = """(?i)\b\d+(?:[.,]\d+)?\s*(?:min(?:utos)?|دقائق?|دق(?:يقة|ائق)?|د)\b""")
        val pickupStructureRegex = Regex(
            pattern = """(?i)(?:^|\s)(?:a|استلام|للراكب|إلى\s+الراكب)\s*\d+(?:[.,]\d+)?\s*(?:min(?:utos)?|دقائق?|دق(?:يقة|ائق)?|د)\s*(?:\(\s*)?\d+(?:[.,]\d+)?\s*(?:km|كم)"""
        )
        val tripStructureRegex = Regex(
            pattern = """(?i)(?:viaje|رحلة|الرحلة|مشوار)(?:\s*de)?\s*\d+(?:[.,]\d+)?\s*(?:min(?:utos)?|دقائق?|دق(?:يقة|ائق)?|د)\s*(?:\(\s*)?\d+(?:[.,]\d+)?\s*(?:km|كم)"""
        )
        val tripWordRegex = Regex(pattern = """(?i)(?:viaje|رحلة|الرحلة|مشوار)""")
        val platformRegex = Regex(pattern = """(?i)(?:\b(?:uber|didi|cabify)\b|أوبر)""")
        val EMPAREJAR_MARKER_VARIANTS = setOf(
            "emparejar",
            "empajar",
            "empareiar",
            "enparejar",
            "ejar"
        )
    }
}
