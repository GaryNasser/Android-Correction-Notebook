package com.github.garynasser.correction_notebook.data.model.appupdate

data class AppVersionInfo(
    val latestVersionName: String,
    val latestVersionCode: Long,
    val minSupportedVersionCode: Long,
    val downloadUrl: String,
    val updateTitle: String,
    val updateContent: String,
    val forceUpdate: Boolean,
    val publishTime: Long
)
