package com.github.garynasser.correction_notebook.data.model.yanhe

import com.google.gson.annotations.SerializedName

// Pagination container
data class PaginatedData(
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("data") val courses: List<Course>,
    @SerializedName("first_page_url") val firstPageUrl: String,
    @SerializedName("from") val from: Int,
    @SerializedName("last_page") val lastPage: Int,
    @SerializedName("last_page_url") val lastPageUrl: String,
    @SerializedName("links") val links: List<PageLink>,
    @SerializedName("next_page_url") val nextPageUrl: String?,
    @SerializedName("path") val path: String,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("prev_page_url") val prevPageUrl: String?,
    @SerializedName("to") val to: Int,
    @SerializedName("total") val total: Int
)

// Course information
data class Course(
    @SerializedName("name_zh") val nameZh: String = "",
    @SerializedName("orientation") val orientation: Int = 0,
    @SerializedName("code") val code: String = "",
    @SerializedName("college_name") val collegeName: String = "",
    @SerializedName("image_url") val imageUrl: String = "",
    @SerializedName("college_code") val collegeCode: String = "",
    @SerializedName("school_year") val schoolYear: String = "",
    @SerializedName("university_code") val universityCode: String = "",
    @SerializedName("lx_url") val lxUrl: String = "",
    @SerializedName("number") val number: String = "",
    @SerializedName("university_id") val universityId: Int = 0,
    @SerializedName("semester") val semester: String = "",
    @SerializedName("id") val id: Int = 0,
    @SerializedName("state") val state: Int = 0,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("name_en") val nameEn: String = "",
    @SerializedName("participant_count") val participantCount: Int = 0,
    @SerializedName("professors") val professors: List<String> = emptyList(),
    @SerializedName("classrooms") val classrooms: List<Classroom> = emptyList()
)

// Classroom information
data class Classroom(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("number") val number: String = ""
)

// Pagination link
data class PageLink(
    @SerializedName("url") val url: String?,
    @SerializedName("label") val label: String,
    @SerializedName("active") val active: Boolean
)
