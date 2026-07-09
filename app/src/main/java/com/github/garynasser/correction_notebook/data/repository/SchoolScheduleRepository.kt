package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.model.school.SchoolScheduleException
import com.github.garynasser.correction_notebook.data.model.school.SchoolScheduleSyncResult
import com.github.garynasser.correction_notebook.data.model.school.SchoolTerm
import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleRemoteDataSource
import com.github.garynasser.correction_notebook.data.repository.school.SchoolScheduleMapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolScheduleRepository @Inject constructor(
    private val credentialManager: CredentialManager,
    private val remoteDataSource: SchoolScheduleRemoteDataSource,
    private val scheduleRepository: ScheduleRepository,
    private val mapper: SchoolScheduleMapper
) {
    suspend fun getCurrentTerm(): SchoolTerm {
        val credential = credentialManager.getCredentials()
            ?: throw SchoolScheduleException("请先登录 BITStudy，再同步教务课表")
        return remoteDataSource.getCurrentTerm(credential.studentId, credential.password)
    }

    suspend fun getTerms(): List<SchoolTerm> {
        val credential = credentialManager.getCredentials()
            ?: throw SchoolScheduleException("请先登录 BITStudy，再同步教务课表")
        return remoteDataSource.getTerms(credential.studentId, credential.password)
    }

    suspend fun syncCurrentTerm(): SchoolScheduleSyncResult {
        val credential = credentialManager.getCredentials()
            ?: throw SchoolScheduleException("请先登录 BITStudy，再同步教务课表")
        val startedAt = System.currentTimeMillis()
        val term = remoteDataSource.getCurrentTerm(credential.studentId, credential.password)
        return syncTermInternal(credential.studentId, credential.password, term, startedAt)
    }

    suspend fun syncTerm(term: SchoolTerm): SchoolScheduleSyncResult {
        val credential = credentialManager.getCredentials()
            ?: throw SchoolScheduleException("请先登录 BITStudy，再同步教务课表")
        return syncTermInternal(credential.studentId, credential.password, term, System.currentTimeMillis())
    }

    private suspend fun syncTermInternal(
        studentId: String,
        password: String,
        term: SchoolTerm,
        startedAt: Long
    ): SchoolScheduleSyncResult {
        val rawCourses = remoteDataSource.getSchedule(studentId, password, term.id)
        if (rawCourses.isEmpty()) {
            throw SchoolScheduleException("这个学期暂时没有可导入课程")
        }
        val importedAt = System.currentTimeMillis()
        val events = mapper.mapCourses(term, rawCourses, importedAt)
        if (events.isEmpty()) {
            throw SchoolScheduleException("学校课表格式暂不支持，已保留本地日程")
        }
        scheduleRepository.applySchoolSchedule(term.id, events)
        val finishedAt = System.currentTimeMillis()
        return SchoolScheduleSyncResult(
            termId = term.id,
            termName = term.name,
            importedCount = events.size,
            startedAt = startedAt,
            finishedAt = finishedAt,
            message = "已同步 ${term.name} ${events.size} 节课程"
        )
    }
}
