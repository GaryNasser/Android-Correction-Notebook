package com.github.garynasser.correction_notebook.data.model.yanhe

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class CourseSection(
    @SerializedName("chapter")
    val chapter: String = "",

    @SerializedName("course_id")
    val courseId: Int = 0,

    @SerializedName("section_big_end")
    val sectionBigEnd: Int = 0,

    @SerializedName("week_type")
    val weekType: Int = 0,

    @SerializedName("section_end")
    val sectionEnd: Int = 0,

    @SerializedName("is_record")
    val isRecord: Int = 0,

    @SerializedName("videos")
    val videos: List<Video> = emptyList(),

    @SerializedName("title")
    val title: String = "",

    @SerializedName("number")
    val number: String = "",

    @SerializedName("badge")
    val badge: String = "",

    @SerializedName("university_id")
    val universityId: Int = 0,

    @SerializedName("professor_id")
    val professorId: Int = 0,

    @SerializedName("is_live")
    val isLive: Int = 0,

    @SerializedName("started_at")
    val startedAt: String = "",

    @SerializedName("location")
    val location: String = "",

    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("week_number")
    val weekNumber: Int = 0,

    @SerializedName("state")
    val state: Int = 0,

    @SerializedName("section_start")
    val sectionStart: Int = 0,

    @SerializedName("section_big_start")
    val sectionBigStart: Int = 0,

    @SerializedName("day")
    val day: String = "",

    @SerializedName("ended_at")
    val endedAt: String = "",

    @SerializedName("video_ids")
    val videoIds: List<Int> = emptyList()
) : Serializable

data class Video(
    @SerializedName("course_id")
    val courseId: Int = 0,

    @SerializedName("start_room")
    val startRoom: String = "",

    @SerializedName("room_origin")
    val roomOrigin: String = "",

    @SerializedName("format")
    val format: String = "",

    @SerializedName("start_vga")
    val startVga: String = "",

    @SerializedName("created_at")
    val createdAt: String = "",

    @SerializedName("main")
    val mainUrl: String = "",

    @SerializedName("type")
    val type: Int = 0,

    @SerializedName("room")
    val room: String = "",

    @SerializedName("start_main")
    val startMain: String = "",

    @SerializedName("duration")
    val duration: String = "",

    @SerializedName("path")
    val path: String = "",

    @SerializedName("vga")
    val vgaUrl: String = "",

    @SerializedName("vga_origin")
    val vgaOrigin: String = "",

    @SerializedName("main_origin")
    val mainOrigin: String = "",

    @SerializedName("updated_at")
    val updatedAt: String = "",

    @SerializedName("id")
    val id: Int = 0
) : Serializable