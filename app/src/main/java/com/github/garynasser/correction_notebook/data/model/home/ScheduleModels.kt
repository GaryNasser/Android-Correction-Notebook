package com.github.garynasser.correction_notebook.data.model.home

import java.time.LocalDate
import java.time.LocalDateTime

enum class PlannerTab {
    SCHEDULE,
    TODO
}

enum class ScheduleRange {
    TODAY,
    TOMORROW,
    WEEK
}

enum class ScheduleSourceType {
    MANUAL,
    ICS_IMPORT,
    SCHOOL_IMPORT
}

enum class ImportDecision {
    MERGE,
    OVERWRITE
}

enum class IcsDiffType {
    ADDED,
    UPDATED,
    CONFLICT,
    DELETED
}

data class ScheduleEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val location: String = "",
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val allDay: Boolean = false,
    val timezoneId: String? = null,
    val sourceType: ScheduleSourceType = ScheduleSourceType.MANUAL,
    val sourceCalendarId: String? = null,
    val sourceEventUid: String? = null,
    val recurrenceRule: String? = null,
    val recurrenceId: LocalDateTime? = null,
    val exDateList: List<LocalDateTime> = emptyList(),
    val lastImportedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ScheduleOccurrence(
    val occurrenceId: String,
    val eventId: String,
    val title: String,
    val description: String,
    val location: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val allDay: Boolean,
    val sourceType: ScheduleSourceType
)

data class IcsDiffItem(
    val type: IcsDiffType,
    val title: String,
    val startsAt: LocalDateTime,
    val detail: String
)

data class IcsImportPreview(
    val fileName: String,
    val sourceCalendarId: String,
    val incomingEvents: List<ScheduleEvent>,
    val added: List<IcsDiffItem>,
    val updated: List<IcsDiffItem>,
    val conflicts: List<IcsDiffItem>,
    val deleted: List<IcsDiffItem>
)

data class ScheduleSection(
    val title: String,
    val date: LocalDate,
    val items: List<ScheduleOccurrence>
)
