package com.github.garynasser.correction_notebook.data.remote.school

import kotlin.math.max
import kotlin.math.min

internal object SchoolScheduleParsers {
    private val rangeRegex = Regex("(\\d+)\\s*(?:-|~|～|—|–|至|到)\\s*(\\d+)")
    private val segmentSeparator = Regex("[,，;；、]+")

    fun parseWeekday(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        return when {
            text.contains("一") -> 1
            text.contains("二") -> 2
            text.contains("三") -> 3
            text.contains("四") -> 4
            text.contains("五") -> 5
            text.contains("六") -> 6
            text.contains("日") || text.contains("天") -> 7
            else -> text.filter(Char::isDigit).toIntOrNull()
        }
    }

    fun parseSectionRange(raw: String?): Pair<Int, Int>? {
        val text = raw.orEmpty()
        val compactSections = parseCompactSectionCodes(text)
        if (compactSections.isNotEmpty()) {
            return compactSections.first() to compactSections.last()
        }

        val numbers = text.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
        return when {
            numbers.size >= 2 -> numbers.first() to numbers.last()
            numbers.size == 1 -> numbers.first() to numbers.first()
            else -> null
        }
    }

    fun parseWeeks(raw: String?): List<Int> {
        val text = raw.orEmpty()
        if (text.isBlank()) return emptyList()

        val results = mutableSetOf<Int>()
        text.split(segmentSeparator)
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { segment ->
                val rangeMatches = rangeRegex.findAll(segment).toList()
                rangeMatches.forEachIndexed { index, match ->
                    val first = match.groupValues[1].toInt()
                    val second = match.groupValues[2].toInt()
                    val nextRangeStart = rangeMatches.getOrNull(index + 1)?.range?.first ?: segment.length
                    val qualifierText = segment.substring(match.range.last + 1, nextRangeStart)
                    val oddOnly = qualifierText.contains("单") || segment.hasGlobalOddQualifier()
                    val evenOnly = qualifierText.contains("双") || segment.hasGlobalEvenQualifier()
                    addWeeks(results, min(first, second)..max(first, second), oddOnly, evenOnly)
                }

                val withoutRanges = rangeRegex.replace(segment, " ")
                val globalOddOnly = segment.hasGlobalOddQualifier()
                val globalEvenOnly = segment.hasGlobalEvenQualifier()
                Regex("\\d+").findAll(withoutRanges)
                    .map { it.value.toInt() }
                    .forEach { week ->
                        if (matchesWeekParity(week, globalOddOnly, globalEvenOnly)) {
                            results.add(week)
                        }
                    }
            }

        return results.sorted()
    }

    private fun addWeeks(
        results: MutableSet<Int>,
        range: IntRange,
        oddOnly: Boolean,
        evenOnly: Boolean
    ) {
        range
            .filter { matchesWeekParity(it, oddOnly, evenOnly) }
            .forEach(results::add)
    }

    private fun String.hasGlobalOddQualifier(): Boolean = contains("单") && !contains("双")

    private fun String.hasGlobalEvenQualifier(): Boolean = contains("双") && !contains("单")

    private fun matchesWeekParity(week: Int, oddOnly: Boolean, evenOnly: Boolean): Boolean {
        return (!oddOnly || week % 2 == 1) && (!evenOnly || week % 2 == 0)
    }

    private fun parseCompactSectionCodes(text: String): List<Int> {
        val digitRuns = Regex("\\d+").findAll(text).map { it.value }.toList()
        if (digitRuns.isEmpty()) return emptyList()
        if (digitRuns.any { it.length < 4 || it.length % 2 != 0 }) return emptyList()

        val expectedCount = digitRuns.sumOf { it.length / 2 }
        val sections = digitRuns.flatMap { digits -> digits.chunked(2) }.mapNotNull { code ->
            code.toIntOrNull()?.takeIf { it in 1..20 }
        }
        return sections.takeIf { it.size == expectedCount }.orEmpty()
    }
}
