package com.github.garynasser.correction_notebook.utils

fun isRemoteVersionNewer(remoteVersionName: String, currentVersionName: String): Boolean {
    return compareVersionNames(remoteVersionName, currentVersionName) > 0
}

fun compareVersionNames(first: String, second: String): Int {
    val firstParts = first.toComparableVersionParts()
    val secondParts = second.toComparableVersionParts()
    val maxSize = maxOf(firstParts.size, secondParts.size)

    for (index in 0 until maxSize) {
        val firstValue = firstParts.getOrNull(index) ?: 0
        val secondValue = secondParts.getOrNull(index) ?: 0
        if (firstValue != secondValue) {
            return firstValue.compareTo(secondValue)
        }
    }

    return 0
}

fun versionNameToCode(versionName: String): Long {
    val parts = versionName.toComparableVersionParts().take(3)

    return parts.foldIndexed(0L) { index, acc, value ->
        val multiplier = when (index) {
            0 -> 1_000_000L
            1 -> 1_000L
            else -> 1L
        }
        acc + value * multiplier
    }
}

private fun String.toComparableVersionParts(): List<Int> {
    val versionText = trim()
        .dropWhile { char -> !char.isDigit() }
        .takeWhile { char -> char.isDigit() || char == '.' || char == '-' || char == '_' }

    return versionText
        .split('.', '-', '_')
        .map { part -> part.takeWhile { char -> char.isDigit() }.toIntOrNull() ?: 0 }
        .ifEmpty { listOf(0) }
}
