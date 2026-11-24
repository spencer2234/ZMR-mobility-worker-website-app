package com.zmr.rent.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.entity.TeamMember
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.theme.TileToday
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val WEEK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/**
 * Who has and has not submitted a report in a week (Monday to Sunday, India time). A report
 * counts once its file has been added under Send manager data, because there is no server.
 */
@Composable
fun WeeklyStatusScreen(container: AppContainer, onBack: () -> Unit) {
    val reports = container.reportRepository
    var weekStartDay by rememberSaveable { mutableLongStateOf(reports.currentWeekStart().toEpochDay()) }
    val weekStart = LocalDate.ofEpochDay(weekStartDay)
    val team by remember { container.authRepository.observeTeam() }.collectAsState(emptyList())
    val submitted by remember(weekStartDay) { reports.observeSubmitted(weekStart) }.collectAsState(emptyList())
    val (done, pending) = team.partition { it.employeeCode in submitted }

    Scaffold(topBar = { ZmrTopBar(stringResource(R.string.action_weekly_status), onBack) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.week_range, weekStart.format(WEEK_DATE), weekStart.plusDays(6).format(WEEK_DATE)),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigOutlinedButton(
                    label = stringResource(R.string.action_previous_week),
                    icon = painterResource(R.drawable.ic_arrow_back),
                    onClick = { weekStartDay -= 7 },
                    modifier = Modifier.weight(1f),
                )
                if (weekStart < reports.currentWeekStart()) {
                    BigOutlinedButton(
                        label = stringResource(R.string.action_next_week),
                        icon = painterResource(R.drawable.ic_today),
                        onClick = { weekStartDay += 7 },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            PeopleList(R.string.week_submitted, done, TileToday)
            PeopleList(R.string.week_pending, pending, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PeopleList(titleRes: Int, people: List<TeamMember>, color: Color) {
    Text("${stringResource(titleRes)} (${people.size})", style = MaterialTheme.typography.titleMedium, color = color)
    people.forEach { Text("${it.name} (${it.employeeCode})", style = MaterialTheme.typography.bodyLarge) }
}
