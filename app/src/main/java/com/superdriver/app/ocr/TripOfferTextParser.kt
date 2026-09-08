package com.superdriver.app.ocr

import com.superdriver.app.debug.SuperDriverDebugLogger

class TripOfferTextParser(
    private val textSanitizer: RecognizedTextSanitizer = RecognizedTextSanitizer()
) {
    fun parse(rawText: String, traceId: String? = null): TripOfferCandidate? {
        SuperDriverDebugLogger.log(
            "parser raw text",
            "traceId=${traceId.orNone()}, textLength=${rawText.length}"
        )
        if (rawText.isBlank()) {
            SuperDriverDebugLogger.log("parser result", "traceId=${traceId.orNone()}, result=blank raw text")
            return null
        }

        val sanitizedText = textSanitizer.sanitize(rawText)
            .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
            .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
            .replace('٨', '8').replace('٩', '9').replace('٫', '.').replace('٬', ',')
        SuperDriverDebugLogger.log(
            "parser sanitized text",
            "traceId=${traceId.orNone()}, sanitizedTextLength=${sanitizedText.length}"
        )
        if (sanitizedText.isBlank()) {
            SuperDriverDebugLogger.log("parser result", "traceId=${traceId.orNone()}, result=blank sanitized text")
            return null
        }

        val platform = parsePlatform(sanitizedText)
        val structuredTripFields = parseStructuredTripFields(sanitizedText)
        val hasStructuredTripFields = structuredTripFields.hasAnyField()
        val genericDistances = if (hasStructuredTripFields) {
            emptyList()
        } else {
            distanceRegex.findAll(sanitizedText)
                .mapNotNull { it.groupValues[1].toDecimalOrNull() }
                .toList()
        }
        val genericTimes = if (hasStructuredTripFields) {
            emptyList()
        } else {
            parseTimes(sanitizedText)
        }
        val fare = if (hasStructuredTripFields) {
            parseStructuredFare(sanitizedText, traceId)
        } else {
            parseFare(sanitizedText, traceId)
        }
        val pickupKm = if (hasStructuredTripFields) {
            structuredTripFields.pickupKm
        } else {
            genericDistances.getOrNull(1)?.let { genericDistances.firstOrNull() }
        }
        val tripKm = if (hasStructuredTripFields) {
            structuredTripFields.tripKm
        } else {
            genericDistances.lastOrNull()
        }
        val pickupMinutes = if (hasStructuredTripFields) {
            structuredTripFields.pickupMinutes
        } else {
            genericTimes.getOrNull(1)?.let { genericTimes.firstOrNull() }
        }
        val tripMinutes = if (hasStructuredTripFields) {
            structuredTripFields.tripMinutes
        } else {
            genericTimes.lastOrNull()
        }

        SuperDriverDebugLogger.log(
            "parser extracted fields",
            "traceId=${traceId.orNone()}, fare=$fare, pickupKm=$pickupKm, tripKm=$tripKm, " +
                "pickupMinutes=$pickupMinutes, tripMinutes=$tripMinutes, platform=$platform, " +
                "hasStructuredTripFields=$hasStructuredTripFields, genericDistanceCount=${genericDistances.size}, " +
                "genericTimeCount=${genericTimes.size}"
        )

        if (fare == null && pickupKm == null && tripKm == null && pickupMinutes == null && tripMinutes == null && platform == null) {
            SuperDriverDebugLogger.log("parser result", "traceId=${traceId.orNone()}, result=no trip fields detected")
            return null
        }

        val parsedFields = buildSet {
            if (fare != null) add(ParsedTripField.FARE)
            if (pickupKm != null || tripKm != null) add(ParsedTripField.DISTANCE)
            if (pickupMinutes != null || tripMinutes != null) add(ParsedTripField.TIME)
            if (platform != null) add(ParsedTripField.PLATFORM)
        }

        val candidate = TripOfferCandidate(
            fareAmount = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            platform = platform,
            rawText = sanitizedText,
            confidence = parsedFields.size / ParsedTripField.entries.size.toDouble()
        )
        SuperDriverDebugLogger.log(
            "parser candidate summary",
            "traceId=${traceId.orNone()}, fare=${candidate.fareAmount}, pickupKm=${candidate.pickupKm}, " +
                "tripKm=${candidate.tripKm}, pickupMinutes=${candidate.pickupMinutes}, " +
                "tripMinutes=${candidate.tripMinutes}, platform=${candidate.platform}, confidence=${candidate.confidence}"
        )
        return candidate
    }

    private fun String?.orNone(): String = this ?: "none"

    private fun parseFare(rawText: String, traceId: String?): Double? {
        val baseFare = parseBaseFare(rawText, traceId)
        return baseFare?.plus(parseAdditionalFares(rawText))
    }

    private fun parseBaseFare(rawText: String, traceId: String?): Double? {
        return selectBaseFareCandidate(rawText.baseFareLines(), traceId)?.amount
    }

    private fun parseStructuredFare(rawText: String, traceId: String?): Double? {
        val lines = rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val firstStructuredLineIndex = lines.indexOfFirst { line ->
            structuredFieldStartRegex.containsMatchIn(line) ||
            structuredPickupRegexes.any { regex -> regex.containsMatchIn(line) } ||
                structuredTripRegexes.any { regex -> regex.containsMatchIn(line) }
        }
        if (firstStructuredLineIndex <= 0) {
            return parseFare(rawText, traceId)
        }

        val linesBeforeStructuredFields = lines.take(firstStructuredLineIndex).asReversed()
        val baseFare = selectBaseFareCandidate(linesBeforeStructuredFields, traceId)?.amount

        val additionalFares = linesBeforeStructuredFields
            .asReversed()
            .joinToString("\n")
            .let { parseAdditionalFares(it) }

        return baseFare?.plus(additionalFares)
    }

    private fun selectBaseFareCandidate(lines: List<String>, traceId: String?): FareCandidate? {
        val evaluatedCandidates = lines
            .flatMapIndexed { index, line -> findFareCandidates(line, index) }
            .map { candidate -> candidate.withDiscardReason() }
            .toList()
        val acceptedCandidates = evaluatedCandidates.filter { candidate -> candidate.discardReason == null }
        val selectedCandidate = acceptedCandidates
            .maxWithOrNull(compareBy<FareCandidate> { it.confidence }.thenBy { -it.lineIndex })

        SuperDriverDebugLogger.log(
            "parser fare candidates",
            "traceId=${traceId.orNone()}, candidates=" +
                evaluatedCandidates.joinToString(separator = " | ") { candidate ->
                    "amount=${candidate.amount}, raw='${candidate.rawAmount}', reason=${candidate.reason}, " +
                        "line=${candidate.lineIndex}, discarded=${candidate.discardReason ?: "false"}"
                }.ifBlank { "none" }
        )
        SuperDriverDebugLogger.log(
            "parser fare selected",
            if (selectedCandidate == null) {
                "traceId=${traceId.orNone()}, selected=none, reason=no accepted base fare candidate"
            } else {
                "traceId=${traceId.orNone()}, selected=${selectedCandidate.amount}, " +
                    "raw='${selectedCandidate.rawAmount}', reason=${selectedCandidate.reason}, " +
                    "line=${selectedCandidate.lineIndex}"
            }
        )

        return selectedCandidate
    }

    private fun parseAdditionalFares(rawText: String): Double {
        return rawText.lineSequence()
            .map { it.trim() }
            .filter { line -> line.hasAdditionalFareContext() }
            .flatMap { line ->
                additionalFareRegex.findAll(line)
                    .map { match -> match.toAdditionalFareAmountOrNull(line) }
            }
            .filterNotNull()
            .distinct()
            .sum()
    }

    private fun String.baseFareLines(): List<String> {
        return lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .filterNot { line -> line.hasAdditionalFareContext() }
            .toList()
    }

    private fun findFareCandidates(line: String, lineIndex: Int): List<FareCandidate> {
        return buildList {
            trailingCurrencyFareRegex.findAll(line).forEach { match ->
                add(match.toFareCandidate(line, lineIndex, reason = "explicit trailing currency", confidence = 100))
            }
            leadingCurrencyFareRegex.findAll(line).forEach { match ->
                add(match.toFareCandidate(line, lineIndex, reason = "explicit leading currency", confidence = 100))
            }
            standaloneLargeFareRegex.findAll(line).forEach { match ->
                add(match.toFareCandidate(line, lineIndex, reason = "standalone large amount", confidence = 60))
            }
        }.distinctBy { candidate -> candidate.lineIndex to candidate.range }
    }

    private fun MatchResult.toFareCandidate(
        line: String,
        lineIndex: Int,
        reason: String,
        confidence: Int
    ): FareCandidate {
        val amountText = groupValues[1]
        return FareCandidate(
            amount = amountText.toMoneyOrNull(),
            rawAmount = amountText,
            sourceLine = line,
            range = groups[1]?.range ?: range,
            reason = reason,
            confidence = confidence,
            lineIndex = lineIndex
        )
    }

    private fun MatchResult.toAdditionalFareAmountOrNull(line: String): Double? {
        if (line.hasMetricUnitAfter(range.last)) return null

        return groupValues
            .drop(1)
            .firstOrNull { value -> value.isNotBlank() }
            ?.toMoneyOrNull()
    }

    private fun FareCandidate.withDiscardReason(): FareCandidate {
        val discardReason = when {
            amount == null -> "invalid amount"
            amount < MIN_STANDALONE_FARE -> "below minimum base fare"
            sourceLine.hasAdditionalFareContext() -> "additional fare context"
            sourceLine.hasMetricUnitAfter(range.last) -> "metric unit after amount"
            sourceLine.hasPartialThousandsPrefixBefore(range.first) -> "partial thousands match"
            else -> null
        }
        return copy(discardReason = discardReason)
    }

    private fun parseStructuredTripFields(rawText: String): StructuredTripFields {
        val pickupMatch = parseFirstTimeDistanceMatch(rawText, structuredPickupRegexes)
        val tripMatch = parseFirstTimeDistanceMatch(rawText, structuredTripRegexes)

        return StructuredTripFields(
            pickupMinutes = pickupMatch?.minutes,
            pickupKm = pickupMatch?.km,
            tripMinutes = tripMatch?.minutes,
            tripKm = tripMatch?.km
        )
    }

    private fun parseFirstTimeDistanceMatch(
        rawText: String,
        regexes: List<Regex>
    ): TimeDistanceMatch? {
        return regexes.firstNotNullOfOrNull { regex ->
            regex.find(rawText)?.let { match ->
                val minutes = match.groupValues.getOrNull(1)?.toOcrMetricDecimalOrNull()
                val km = match.groupValues.getOrNull(2)?.toOcrMetricDecimalOrNull()
                if (minutes != null && km != null) {
                    TimeDistanceMatch(minutes = minutes, km = km)
                } else {
                    null
                }
            }
        }
    }

    private fun parseTimes(rawText: String): List<Double> {
        val rangeMatches = minuteRangeRegex.findAll(rawText).toList()
        val rangeSpans = rangeMatches.map { it.range }
        val rangeValues = rangeMatches.mapNotNull { match ->
            val from = match.groupValues[1].toDoubleOrNull()
            val to = match.groupValues[2].toDoubleOrNull()
            if (from != null && to != null) (from + to) / 2.0 else null
        }

        val singleValues = minuteRegex.findAll(rawText)
            .filterNot { single -> rangeSpans.any { single.range.first >= it.first && single.range.last <= it.last } }
            .mapNotNull { it.groupValues[1].toDecimalOrNull() }
            .toList()

        return rangeValues + singleValues
    }

    private fun parsePlatform(rawText: String): String? {
        val normalized = rawText.lowercase()
        return when {
            "uber" in normalized || "أوبر" in normalized -> "uber"
            "didi" in normalized -> "didi"
            "cabify" in normalized -> "cabify"
            else -> null
        }
    }

    private fun String.toMoneyOrNull(): Double? {
        val normalized = replace(" ", "")
        val lastSeparatorIndex = maxOf(normalized.lastIndexOf('.'), normalized.lastIndexOf(','))
        val hasMixedSeparators = normalized.contains('.') && normalized.contains(',')
        val integerLike = when {
            lastSeparatorIndex < 0 -> normalized
            hasMixedSeparators -> {
                val integerPart = normalized.take(lastSeparatorIndex).replace(".", "").replace(",", "")
                val decimalPart = normalized.drop(lastSeparatorIndex + 1)
                "$integerPart.$decimalPart"
            }
            normalized.length - lastSeparatorIndex - 1 == 3 -> {
                normalized.replace(".", "").replace(",", "")
            }
            else -> normalized.replace(",", ".")
        }

        return integerLike.toDoubleOrNull()
    }

    private fun String.hasMetricUnitAfter(amountEndIndex: Int): Boolean {
        val suffix = drop(amountEndIndex + 1).take(12)
        return metricUnitAfterAmountRegex.containsMatchIn(suffix)
    }

    private fun String.hasPartialThousandsPrefixBefore(amountStartIndex: Int): Boolean {
        val prefix = take(amountStartIndex).takeLast(8)
        return partialThousandsPrefixRegex.containsMatchIn(prefix)
    }

    private fun String.toDecimalOrNull(): Double? {
        return replace(",", ".").toDoubleOrNull()
    }

    private fun String.toOcrMetricDecimalOrNull(): Double? {
        return replace('l', '1')
            .replace('I', '1')
            .replace('|', '1')
            .toDecimalOrNull()
    }

    private fun String.containsMetricOrNoise(): Boolean {
        return metricOrNoiseRegex.containsMatchIn(this)
    }

    private fun String.hasAdditionalFareContext(): Boolean {
        return additionalFareContextRegex.containsMatchIn(normalizeAdditionalFareContext())
    }

    private fun String.normalizeAdditionalFareContext(): String {
        return lowercase()
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('1', 'l')
            .replace('I', 'l')
            .replace('|', 'l')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private data class StructuredTripFields(
        val pickupKm: Double? = null,
        val tripKm: Double? = null,
        val pickupMinutes: Double? = null,
        val tripMinutes: Double? = null
    ) {
        fun hasAnyField(): Boolean {
            return pickupKm != null ||
                tripKm != null ||
                pickupMinutes != null ||
                tripMinutes != null
        }
    }

    private data class TimeDistanceMatch(
        val minutes: Double,
        val km: Double
    )

    private data class FareCandidate(
        val amount: Double?,
        val rawAmount: String,
        val sourceLine: String,
        val range: IntRange,
        val reason: String,
        val confidence: Int,
        val lineIndex: Int,
        val discardReason: String? = null
    )

    private companion object {
        private const val MIN_STANDALONE_FARE = 20.0
        private const val MONEY_AMOUNT_PATTERN =
            """\d{1,3}(?:\s*[.,\s]\s*\d{3})+(?:[.,]\d{1,2})?|\d{4,7}|\d{1,3}(?:[.,]\d{1,2})?"""
        private const val TIME_DISTANCE_PATTERN =
            """([0-9lI|]+(?:[.,][0-9lI|]+)?)\s*(?:m\s*(?:in|n|inutos)?|rnin|minutos?|دقائق?|دق(?:يقة|ائق)?|د)\s*(?:\(\s*)?([0-9lI|]+(?:[.,][0-9lI|]+)?)\s*(?:k\s*(?:m|rn|in)|كم)(?:\s*\))?"""

        val trailingCurrencyFareRegex = Regex(
            pattern = """(?i)(?<![\d.,])($MONEY_AMOUNT_PATTERN)\s*(?:EGP|pesos|جنيه(?:\s+مصري)?|ج\.?\s*م\.?)\b?"""
        )
        val leadingCurrencyFareRegex = Regex(
            pattern = """(?i)(?:\bEGP\b|\$|ج\.?\s*م\.?|جنيه(?:\s+مصري)?)\s*($MONEY_AMOUNT_PATTERN)"""
        )
        val additionalFareRegex = Regex(
            pattern = """(?i)(?:(?:\+\s*)?(?:\$\s*)?($MONEY_AMOUNT_PATTERN)\s*(?:EGP|pesos|جنيه(?:\s+مصري)?|ج\.?\s*م\.?)\b?|\b(?:EGP|pesos|جنيه(?:\s+مصري)?|ج\.?\s*م\.?)\b?\s*(?:\+\s*)?(?:\$\s*)?($MONEY_AMOUNT_PATTERN)|\+\s*\$?\s*($MONEY_AMOUNT_PATTERN)|\$\s*(?:\+\s*)?($MONEY_AMOUNT_PATTERN))"""
        )
        val additionalFareContextRegex = Regex(
            pattern = """(?i)(?:\b(?:adicional(?:es)?|incluye(?:n)?|incluido(?:s|as)?|suman|agrega(?:n)?)\b|إضاف(?:ة|ات)|يشمل|شامل|إضافي)"""
        )
        val standaloneLargeFareRegex = Regex(
            pattern = """(?<![\d.,])($MONEY_AMOUNT_PATTERN)(?![\d.,])"""
        )
        val metricUnitAfterAmountRegex = Regex(pattern = """^\s*/?\s*(?:km|h|min)\b""", option = RegexOption.IGNORE_CASE)
        val partialThousandsPrefixRegex = Regex(pattern = """\d{1,3}\s*[.,]\s*$""")
        val metricOrNoiseRegex = Regex(
            pattern = """(?i)(?:\b(?:min|km|ars|egp|pesos|dni|viaje|distancia|رحلة|الرحلة|دقيقة|دقائق|كم|جنيه)\b|أوبر|\$/|/\s*(?:h|km)|%|\d+\s*:\s*\d+|\d+[.,]\d+\s*\()"""
        )
        val distanceRegex = Regex(
            pattern = """(?i)\b(\d+(?:[.,]\d+)?)\s*(?:km|كم)\b"""
        )
        val minuteRangeRegex = Regex(
            pattern = """(?i)\b(\d+)\s*-\s*(\d+)\s*(?:min(?:utos)?|دقائق?|د)\b?"""
        )
        val minuteRegex = Regex(
            pattern = """(?i)\b(\d+(?:[.,]\d+)?)\s*(?:min(?:utos)?|دقائق?|دق(?:يقة|ائق)?|د)\b?"""
        )
        val structuredFieldStartRegex = Regex(
            pattern = """(?i)^(?:A\s*[0-9lI|]+|Viaje\b|رحلة\b|الرحلة\b|استلام\b|للراكب\b|إلى\s+الراكب\b)"""
        )
        val structuredPickupRegexes = listOf(
            Regex(
                pattern = """(?i)(?:\bA|استلام|للراكب|إلى\s+الراكب)\s*$TIME_DISTANCE_PATTERN(?:\s*(?:de\s+distancia|للراكب|حتى\s+الراكب))?"""
            ),
            Regex(
                pattern = """(?i)(?:^|[^\p{Alnum}])$TIME_DISTANCE_PATTERN\s*(?:de\s+distancia|للراكب|حتى\s+الراكب)"""
            )
        )
        val structuredTripRegexes = listOf(
            Regex(
                pattern = """(?i)(?:Viaje|رحلة|الرحلة|مشوار)(?:\s*(?:de|الرحلة))?\s*$TIME_DISTANCE_PATTERN"""
            ),
            Regex(
                pattern = """(?is)(?:Viaje|رحلة|الرحلة|مشوار).{0,50}?$TIME_DISTANCE_PATTERN"""
            )
        )
    }
}
