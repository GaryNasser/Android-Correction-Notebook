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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.StudyPreferencesManager
import com.github.garynasser.correction_notebook.data.model.home.DailyStats
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val subjectDistribution: Map<String, Int> = emptyMap()
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val studyPreferencesManager: StudyPreferencesManager,
    private val studySessionRepository: StudySessionRepository
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

    private fun loadStats() {
        viewModelScope.launch {
            val totalMinutes = studyPreferencesManager.getTotalStudyMinutes()
            val pomodoros = studyPreferencesManager.pomodorosCompleted.first()

            val (dailyMinutes, avgMinutes) = when (_uiState.value.period) {
                StatsPeriod.DAY -> Pair(List(1) { totalMinutes }, totalMinutes)
                StatsPeriod.WEEK -> {
                    val days = 7
                    val avg = if (days > 0) totalMinutes / days else 0
                    Pair(List(days) { totalMinutes / days }, avg)
                }
                StatsPeriod.MONTH -> {
                    val days = 30
                    val avg = if (days > 0) totalMinutes / days else 0
                    Pair(List(days) { totalMinutes / days }, avg)
                }
            }

            _uiState.value = _uiState.value.copy(
                totalStudyMinutes = totalMinutes,
                averageDailyMinutes = avgMinutes,
                completedPomodoros = pomodoros,
                dailyMinutes = dailyMinutes
            )
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                        Spacer(modifier = Modifier.height(16.dp))
                        BarChart(
                            data = uiState.dailyMinutes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
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
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOrNull()?.coerceAtLeast(1) ?: 1
    val barColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, value ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(0.6f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val heightFraction = value.toFloat() / maxValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
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
