package com.github.garynasser.correction_notebook.data.model.home

enum class WhiteNoise(val displayName: String) {
    RAIN("雨声"),
    OCEAN("海浪"),
    FOREST("森林"),
    CAFE("咖啡馆")
}

data class WhiteNoiseState(
    val selectedNoise: WhiteNoise? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null
)
