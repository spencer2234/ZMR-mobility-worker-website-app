package com.zmr.rent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zmr.rent.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Shows a date ("Sat, 10 Oct 2026") in a large tappable box; tapping it opens a calendar.
 * The date is a plain calendar date with no time zone. When [noDateLabel] is given the date is
 * optional: an empty date shows that text, and the calendar has a button to clear the date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    noDateLabel: String? = null,
) {
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale) }
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = { showCalendar = true },
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(date?.format(formatter) ?: noDateLabel.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(
                painter = painterResource(R.drawable.ic_today),
                contentDescription = stringResource(R.string.action_choose_date),
                tint = colors.primary,
                modifier = Modifier.size(32.dp),
            )
        }
    }

    if (showCalendar) {
        // The Material date picker works in UTC midnight milliseconds.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showCalendar = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                if (noDateLabel != null) {
                    TextButton(onClick = { onDateChange(null); showCalendar = false }) { Text(noDateLabel) }
                }
                TextButton(onClick = { showCalendar = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
