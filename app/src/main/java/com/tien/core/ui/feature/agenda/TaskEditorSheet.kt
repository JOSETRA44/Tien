package com.tien.core.ui.feature.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.core.time.TienClock
import com.tien.core.domain.model.Priority
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Create/edit sheet for a task.
 *
 * The deadline is split into a date control and a time control rather than one
 * combined picker: people think "Friday" first and "at 6" second, and splitting
 * them lets the common case — today, later — be a single tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    sheetState: SheetState,
    clock: TienClock,
    labels: DateTimeLabels,
    initialTitle: String,
    initialDetails: String,
    initialDueAt: Long,
    initialPriority: Priority,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, details: String, dueAt: Long, priority: Priority) -> Unit
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var details by rememberSaveable(initialDetails) { mutableStateOf(initialDetails) }
    var titleTouched by rememberSaveable { mutableStateOf(false) }

    // Defaults to 18:00 today when creating: a deadline with no time is not
    // actionable, and end-of-working-day is the most likely intent.
    val defaultDue = rememberSaveable {
        clock.today().atTime(DEFAULT_HOUR, 0).atZone(clock.zone()).toEpochSecond()
    }
    var dueAt by rememberSaveable(initialDueAt) {
        mutableLongStateOf(if (initialDueAt > 0) initialDueAt else defaultDue)
    }
    var priorityOrdinal by rememberSaveable(initialPriority) {
        mutableIntStateOf(initialPriority.ordinal)
    }
    val priority = Priority.entries[priorityOrdinal]

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val dueDateTime = clock.toLocalDateTime(dueAt)
    val titleIsBlank = title.isBlank()
    val showTitleError = titleTouched && titleIsBlank

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TienTheme.spacing.gutter)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = TienTheme.spacing.loose)
        ) {
            Text(
                text = if (isEditing) "Editar tarea" else "Nueva tarea",
                style = TienTextStyles.eyebrow,
                color = TienTheme.extendedColors.muted
            )

            Spacer(Modifier.height(TienTheme.spacing.snug))

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleTouched = true
                },
                label = { Text("¿Qué hay que hacer?") },
                singleLine = true,
                isError = showTitleError,
                supportingText = if (showTitleError) {
                    { Text("Escribe un título") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(TienTheme.spacing.base))

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Detalles (opcional)") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 180.dp)
            )

            Spacer(Modifier.height(TienTheme.spacing.comfy))

            Text(
                text = "Vence",
                style = TienTextStyles.eyebrow,
                color = TienTheme.extendedColors.muted
            )
            Spacer(Modifier.height(TienTheme.spacing.snug))

            Row(horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(labels.dayLabel(dueDateTime.toLocalDate())) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )
                AssistChip(
                    onClick = { showTimePicker = true },
                    label = { Text(labels.time(dueAt)) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )
            }

            Spacer(Modifier.height(TienTheme.spacing.comfy))

            Text(
                text = "Prioridad",
                style = TienTextStyles.eyebrow,
                color = TienTheme.extendedColors.muted
            )
            Spacer(Modifier.height(TienTheme.spacing.snug))

            Row(horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)) {
                Priority.entries.forEach { option ->
                    FilterChip(
                        selected = option == priority,
                        onClick = { priorityOrdinal = option.ordinal },
                        label = { Text(option.label()) }
                    )
                }
            }

            Spacer(Modifier.height(TienTheme.spacing.loose))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    TienTheme.spacing.snug,
                    Alignment.End
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(
                    onClick = {
                        titleTouched = true
                        if (!titleIsBlank) {
                            onSave(title, details, dueAt, priority)
                            onDismiss()
                        }
                    }
                ) {
                    Text(if (isEditing) "Guardar cambios" else "Crear tarea")
                }
            }
        }
    }

    if (showDatePicker) {
        // The picker works in UTC millis; the conversion below keeps the user's
        // chosen calendar day intact regardless of their offset.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            dueAt = combine(picked, dueDateTime.toLocalTime(), clock)
                        }
                        showDatePicker = false
                    }
                ) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = dueDateTime.hour,
            initialMinute = dueDateTime.minute,
            is24Hour = true
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dueAt = combine(
                            dueDateTime.toLocalDate(),
                            LocalTime.of(timeState.hour, timeState.minute),
                            clock
                        )
                        showTimePicker = false
                    }
                ) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TienTheme.spacing.loose),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timeState)
            }
        }
    }
}

/** Rebuilds an epoch-second deadline from a calendar date and a wall-clock time. */
private fun combine(date: LocalDate, time: LocalTime, clock: TienClock): Long =
    LocalDateTime.of(date, time).atZone(clock.zone()).toEpochSecond()

private fun Priority.label(): String = when (this) {
    Priority.LOW -> "Baja"
    Priority.MEDIUM -> "Media"
    Priority.HIGH -> "Alta"
}

private const val DEFAULT_HOUR = 18
