package com.superdriver.app.zones

import java.text.Normalizer
import java.time.LocalTime

class AvoidZoneMatcher {
    fun findMatch(
        rawText: String?,
        rules: List<AvoidZoneRule>,
        currentTime: LocalTime = LocalTime.now()
    ): ZoneMatchResult? {
        if (rawText.isNullOrBlank()) return null

        val normalizedText = rawText.normalizeForMatch()
        return rules
            .asSequence()
            .filter { it.enabled && it.isActiveAt(currentTime) }
            .flatMap { rule ->
                rule.keywords
                    .asSequence()
                    .filter { it.isNotBlank() }
                    .map { keyword -> rule to keyword }
            }
            .firstOrNull { (_, keyword) ->
                normalizedText.contains(keyword.normalizeForMatch())
            }
            ?.let { (rule, keyword) ->
                ZoneMatchResult(rule = rule, matchedKeyword = keyword)
            }
    }

    private fun AvoidZoneRule.isActiveAt(currentTime: LocalTime): Boolean {
        val from = activeFrom?.toLocalTimeOrNull()
        val to = activeTo?.toLocalTimeOrNull()

        return when {
            from == null && to == null -> true
            from != null && to == null -> !currentTime.isBefore(from)
            from == null && to != null -> !currentTime.isAfter(to)
            from == to -> true
            from != null && to != null && from.isBefore(to) -> {
                !currentTime.isBefore(from) && !currentTime.isAfter(to)
            }
            from != null && to != null -> {
                !currentTime.isBefore(from) || !currentTime.isAfter(to)
            }
            else -> true
        }
    }

    private fun String.toLocalTimeOrNull(): LocalTime? {
        return runCatching { LocalTime.parse(this) }.getOrNull()
    }

    private fun String.normalizeForMatch(): String {
        val withoutAccents = Normalizer
            .normalize(this, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

        return withoutAccents.lowercase()
    }
}

