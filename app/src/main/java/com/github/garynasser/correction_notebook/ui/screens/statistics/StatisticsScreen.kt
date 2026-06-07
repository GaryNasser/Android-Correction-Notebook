package com.github.garynasser.correction_notebook.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class StatsPeriod {
    DAY, WEEK, MONTH
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val totalStudyMinutes: Int = 0,
    val averageDailyMinutes: Int = 0,
    val completedPomodoros: Int = 0,
    val dailyMinutes: List<Int> = emptyList(),
    val chartLabels: List<String> = emptyList(),
    val subjectDistribution: Map<String, Int> = emptyMap(),
    val aiInsight: String? = null,
    val isAiInsightLoading: Boolean = false,
    val aiInsightError: String? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val studySessionRepository: StudySessionRepository,
    private val courseLearningRepository: CourseLearningRepository,
    private val aiStudyUseCase: AiStudyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun setPeriod(period: StatsPeriod) {
        _uiState.value = _uiState.value.copy(period = period)
        loadStats()
    }

    fun generateAiInsight() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiInsightLoading = true, aiInsightError = null)
            aiStudyUseCase.generateStatsInsight()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        aiInsight = it,
                        isAiInsightLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isAiInsightLoading = false,
                        aiInsightError = it.message ?: "AI 解读失败"
                    )
                }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val (startDate, endDate) = when (_uiState.value.period) {
                StatsPeriod.DAY -> today to today
                StatsPeriod.WEEK -> today.minusDays(6) to today
                StatsPeriod.MONTH -> today.withDayOfMonth(1) to today
            }

            val sessions = studySessionRepository.getSessionsBetween(startDate, endDate)
            val dateRange = generateSequence(startDate) { current ->
                current.plusDays(1).takeIf { !it.isAfter(endDate) }
            }.toList()

            val sessionsByDate = sessions.groupBy { it.startTime.toLocalDate() }
            val dailyStats = dateRange.map { date ->
                studySessionRepository.buildDailyStats(date, sessionsByDate[date].orEmpty())
            }
            val totalMinutes = dailyStats.sumOf { it.totalStudyMinutes }
            val pomodoros = dailyStats.sumOf { it.completedPomodoros }
            val avgMinutes = if (dailyStats.isNotEmpty()) totalMinutes / dailyStats.size else 0
            val dailyMinutes = dailyStats.map { it.totalStudyMinutes }
            val chartLabels = buildChartLabels(dateRange, _uiState.value.period)
            val subjectDistribution = sessions
                .groupBy { it.subject }
                .mapValues { (_, items) -> items.sumOf { it.durationMinutes } }
                .filterValues { it > 0 }
                .toMutableMap()

            courseLearningRepository.progressItems.first()
                .filter { progress -> progress.completedCount > 0 || progress.lastAccessedAt > 0L }
                .forEach { progress ->
                    val name = progress.courseName.ifBlank { "课程学习" }
                    val minutes = (progress.watchedMinutes.takeIf { it > 0 } ?: progress.completedCount * 45).coerceAtLeast(0)
                    if (minutes > 0) {
                        subjectDistribution[name] = (subjectDistribution[name] ?: 0) + minutes
                    }
                }

            _uiState.value = _uiState.value.copy(
                totalStudyMinutes = totalMinutes,
                averageDailyMinutes = avgMinutes,
                completedPomodoros = pomodoros,
                dailyMinutes = dailyMinutes,
                chartLabels = chartLabels,
                subjectDistribution = subjectDistribution
            )
        }
    }

    private fun buildChartLabels(
        dates: List<LocalDate>,
        period: StatsPeriod
    ): List<String> {
        return when (period) {
            StatsPeriod.DAY -> listOf("今天")
            StatsPeriod.WEEK -> dates.map { date ->
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
            }
            StatsPeriod.MONTH -> dates.mapIndexed { index, date ->
                val shouldShow = index == 0 || index == dates.lastIndex || index % 5 == 4
                if (shouldShow) "${date.dayOfMonth}" else ""
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习统计") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::generateAiInsight,
                        enabled = !uiState.isAiInsightLoading
                    ) {
                        if (uiState.isAiInsightLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Psychology, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI 解读")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Period selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = uiState.period == period,
                            onClick = { viewModel.setPeriod(period) },
                            label = {
                                Text(
                                    when (period) {
                                        StatsPeriod.DAY -> "今日"
                                        StatsPeriod.WEEK -> "本周"
                                        StatsPeriod.MONTH -> "本月"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Summary cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "总学习时长",
                        value = formatMinutesToHours(uiState.totalStudyMinutes),
                        icon = Icons.Default.Timer
                    )
                    StatsSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "日均学习",
                        value = formatMinutesToHours(uiState.averageDailyMinutes),
                        icon = Icons.Default.TrendingUp
                    )
                }
            }

            item {
                StatsSummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "完成番茄钟",
                    value = "${uiState.completedPomodoros} 个",
                    icon = Icons.Default.EmojiEvents
                )
            }

            if (uiState.aiInsight != null || uiState.aiInsightError != null || uiState.isAiInsightLoading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI 学习反馈",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            when {
                                uiState.isAiInsightLoading -> Text("AI 正在解读本周学习情况...")
                                uiState.aiInsightError != null -> Text(
                                    uiState.aiInsightError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error
                                )
                                else -> Text(uiState.aiInsight.orEmpty())
                            }
                        }
                    }
                }
            }

            // Bar chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "学习时长趋势",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (uiState.period) {
                                StatsPeriod.DAY -> "查看今天的学习时长"
                                StatsPeriod.WEEK -> "查看最近 7 天的学习分布"
                                StatsPeriod.MONTH -> "查看本月每日学习趋势"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BarChart(
                            data = uiState.dailyMinutes,
                            labels = uiState.chartLabels,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                }
            }

            // Pie chart for subject distribution
            if (uiState.subjectDistribution.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "科目分布",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PieChart(
                                    data = uiState.subjectDistribution.values.toList(),
                                    modifier = Modifier.size(120.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.subjectDistribution.forEach { (subject, minutes) ->
                                        LegendItem(
                                            color = getColorForSubject(subject),
                                            label = subject,
                                            value = formatMinutesToHours(minutes)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StatsSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BarChart(
    data: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOrNull() ?: 0
    val hasData = maxValue > 0
    val chartColor = MaterialTheme.colorScheme.primary
    val mutedText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)

    if (data.isEmpty() || labels.isEmpty()) {
        EmptyChartState(modifier = modifier, message = "暂无趋势数据")
        return
    }

    if (!hasData) {
        EmptyChartState(modifier = modifier, message = "最近还没有学习记录")
        return
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizontalLines = 3
                val chartHeight = size.height
                val chartWidth = size.width
                val baseLine = chartHeight
                val stepY = chartHeight / horizontalLines

                repeat(horizontalLines + 1) { index ->
                    val y = baseLine - (index * stepY)
                    drawLine(
                        color = chartColor.copy(alpha = if (index == 0) 0.22f else 0.08f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.forEachIndexed { index, value ->
                        val heightFraction = (value.toFloat() / maxValue).coerceAtLeast(0.08f)
                        val isPeak = value == maxValue
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(heightFraction),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                            ) {
                                drawRoundRect(
                                    color = if (isPeak) chartColor else chartColor.copy(alpha = 0.78f),
                                    topLeft = Offset(0f, 0f),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                )
                            }
                            if (value > 0 && data.size <= 7) {
                                Text(
                                    text = value.toString(),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedText,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyChartState(
    modifier: Modifier = Modifier,
    message: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun PieChart(
    data: List<Int>,
    modifier: Modifier = Modifier
) {
    val total = data.sum().coerceAtLeast(1)
    val colors = listOf(
        Color(0xFF006781),
        Color(0xFF006A40),
        Color(0xFF43A047),
        Color(0xFFFFA000),
        Color(0xFFE53935)
    )

    Canvas(modifier = modifier) {
        var startAngle = -90f
        data.forEachIndexed { index, value ->
            val sweepAngle = (value.toFloat() / total) * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt),
                size = Size(size.width, size.height)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

private fun formatMinutesToHours(minutes: Int): String {
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins > 0) "${hours}小时${mins}分钟" else "${hours}小时"
    } else {
        "${minutes}分钟"
    }
}

private fun getColorForSubject(subject: String): Color {
    return when (subject.hashCode() % 5) {
        0 -> Color(0xFF006781)
        1 -> Color(0xFF006A40)
        2 -> Color(0xFF43A047)
        3 -> Color(0xFFFFA000)
        else -> Color(0xFFE53935)
    }
}
