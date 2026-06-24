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

private fun String.toComparableVersionParts(): List<Int> {
    return trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '_')
        .map { part -> part.takeWhile { char -> char.isDigit() }.toIntOrNull() ?: 0 }
}
