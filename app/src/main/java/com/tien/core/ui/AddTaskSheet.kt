package com.tien.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (title: String, details: String, dueAt: Long, priority: Int) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    val now = Calendar.getInstance()
    val defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
    var dueDate by rememberSaveable { mutableStateOf(defaultDate) }
    var dueHour by rememberSaveable { mutableStateOf("09") }
    var dueMinute by rememberSaveable { mutableStateOf("00") }
    var priority by rememberSaveable { mutableStateOf(1) }
    var dateError by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = "Nueva tarea",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                placeholder = { Text("Ej. Entregar informe") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Detalles") },
                placeholder = { Text("Contexto, checklist, enlaces...") },
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it },
                label = { Text("Fecha (yyyy-MM-dd)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = dateError != null,
                supportingText = {
                    dateError?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dueHour,
                    onValueChange = { dueHour = it.take(2) },
                    label = { Text("Hora") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = dueMinute,
                    onValueChange = { dueMinute = it.take(2) },
                    label = { Text("Min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = priority == 0, onClick = { priority = 0 }, label = { Text("Baja") })
                FilterChip(selected = priority == 1, onClick = { priority = 1 }, label = { Text("Media") })
                FilterChip(selected = priority == 2, onClick = { priority = 2 }, label = { Text("Alta") })
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Button(onClick = {
                    val dueEpoch = parseDueEpoch(dueDate, dueHour, dueMinute)
                    if (dueEpoch == null) {
                        dateError = "Fecha/hora inválida"
                    } else {
                        dateError = null
                        onSave(title.trim(), details.trim(), dueEpoch, priority)
                        onDismiss()
                    }
                }, enabled = title.isNotBlank()) {
                    Text("Crear")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun parseDueEpoch(date: String, hour: String, minute: String): Long? {
    return try {
        val h = hour.toInt()
        val m = minute.toInt()
        if (h !in 0..23 || m !in 0..59) return null
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val parsed = format.parse("$date ${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}") ?: return null
        parsed.time / 1_000L
    } catch (_: Exception) {
        null
    }
}
