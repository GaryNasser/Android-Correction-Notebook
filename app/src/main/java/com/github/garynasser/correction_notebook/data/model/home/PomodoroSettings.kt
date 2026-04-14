package com.github.garynasser.correction_notebook.data.model.home

data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val pomodorosBeforeLongBreak: Int = 4
)
