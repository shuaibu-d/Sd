package com.sdtech.studio.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.PlannerItem
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.PlannerStatus
import com.sdtech.studio.planner.PlannerReminders
import com.sdtech.studio.ui.components.ScreenScaffold
import com.sdtech.studio.ui.components.SectionHeader
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val platforms = listOf("Instagram", "Facebook", "TikTok", "LinkedIn", "YouTube", "WhatsApp Status", "X")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as SDStudioApplication
    val repo = app.appContainer.plannerRepository
    val scope = rememberCoroutineScope()
    val plannerItems by repo.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val projects by app.appContainer.projectRepository.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var listMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlannerItem?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var pendingReminder by remember { mutableStateOf<Pair<Long, Pair<String, Long>>?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingReminder?.let { (id, data) -> PlannerReminders.schedule(context, id, data.first, data.second) }
        pendingReminder = null
    }

    ScreenScaffold(title = "Planner", onBack = onBack) {
        Text("Plan content locally. Nothing is uploaded or synced.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                SegmentedButton(selected = !listMode, onClick = { listMode = false }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("Month") }
                SegmentedButton(selected = listMode, onClick = { listMode = true }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("List") }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editing = null; showForm = true }) { Text("Add") }
        }
        if (!listMode) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1); selectedDay = null }) { Text("‹") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { month = month.plusMonths(1); selectedDay = null }) { Text("›") }
            }
            Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) } }
            val firstOffset = month.atDay(1).dayOfWeek.value - 1
            val cells: List<LocalDate?> = List(firstOffset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), userScrollEnabled = false) {
                items(cells) { day ->
                    Box(Modifier.padding(2.dp).aspectRatio(1f).clickable(enabled = day != null) { selectedDay = day }.then(if (day == selectedDay) Modifier else Modifier), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val count = plannerItems.count { Instant.ofEpochMilli(it.scheduledDate).atZone(ZoneId.systemDefault()).toLocalDate() == day }
                            Surface(shape = RoundedCornerShape(10.dp), color = if (day == selectedDay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (count > 0) .8f else .25f)) {
                                Column(Modifier.fillMaxSize().padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
                                    if (count > 0) Text("•".repeat(count.coerceAtMost(3)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Text(selectedDay?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: "All dates this month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        val visible = plannerItems.filter { item ->
            val date = Instant.ofEpochMilli(item.scheduledDate).atZone(ZoneId.systemDefault()).toLocalDate()
            if (listMode) true else if (selectedDay != null) date == selectedDay else YearMonth.from(date) == month
        }
        if (visible.isEmpty()) Text("No planned posts here yet. Add an item to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 520.dp)) {
            items(visible, key = { it.id }) { item ->
                PlannerItemCard(item, onEdit = { editing = item; showForm = true }, onDelete = { scope.launch { PlannerReminders.cancel(context, item.id); repo.delete(item.id) } }, onChecklist = { updated -> scope.launch { repo.save(updated) } })
            }
        }
    }

    if (showForm) {
        PlannerItemDialog(item = editing, projects = projects, onDismiss = { showForm = false }, onSave = { item ->
            scope.launch {
                val id = repo.save(item)
                val reminder = item.reminderAtMillis
                if (reminder != null && reminder > System.currentTimeMillis()) {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        pendingReminder = id to (item.title to reminder)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else PlannerReminders.schedule(context, id, item.title, reminder)
                } else PlannerReminders.cancel(context, id)
                showForm = false
            }
        })
    }
}

@Composable
private fun PlannerItemCard(item: PlannerItem, onEdit: () -> Unit, onDelete: () -> Unit, onChecklist: (PlannerItem) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${item.platform} · ${Instant.ofEpochMilli(item.scheduledDate).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))}", style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = onEdit, label = { Text(item.status.name.lowercase().replaceFirstChar(Char::uppercase)) })
            }
            Text("Posting checklist", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            ChecklistRow("Logo visible", item.logoVisible) { onChecklist(item.copy(logoVisible = it)) }
            ChecklistRow("Text readable", item.textReadable) { onChecklist(item.copy(textReadable = it)) }
            ChecklistRow("Caption and hashtags added", item.captionHashtagsAdded) { onChecklist(item.copy(captionHashtagsAdded = it)) }
            ChecklistRow("Call-to-action added", item.callToActionAdded) { onChecklist(item.copy(callToActionAdded = it)) }
            ChecklistRow("Safe zone checked", item.safeZoneChecked) { onChecklist(item.copy(safeZoneChecked = it)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDelete) { Text("Delete") }; TextButton(onClick = onEdit) { Text("Edit") } }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerItemDialog(item: PlannerItem?, projects: List<Project>, onDismiss: () -> Unit, onSave: (PlannerItem) -> Unit) {
    val context = LocalContext.current
    var title by remember(item) { mutableStateOf(item?.title.orEmpty()) }
    var platform by remember(item) { mutableStateOf(item?.platform ?: platforms.first()) }
    var status by remember(item) { mutableStateOf(item?.status ?: PlannerStatus.IDEA) }
    var projectId by remember(item) { mutableStateOf(item?.projectId) }
    var dateMillis by remember(item) { mutableLongStateOf(item?.scheduledDate ?: System.currentTimeMillis()) }
    var reminderMillis by remember(item) { mutableStateOf(item?.reminderAtMillis) }
    var platformMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
    val time = reminderMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item == null) "New planner item" else "Edit planner item") }, text = {
        Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Post title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(expanded = platformMenu, onExpandedChange = { platformMenu = it }) {
                OutlinedTextField(platform, {}, readOnly = true, label = { Text("Platform") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(platformMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = platformMenu, onDismissRequest = { platformMenu = false }) { platforms.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { platform = it; platformMenu = false }) } }
            }
            ExposedDropdownMenuBox(expanded = statusMenu, onExpandedChange = { statusMenu = it }) {
                OutlinedTextField(status.name, {}, readOnly = true, label = { Text("Status") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) { PlannerStatus.entries.forEach { DropdownMenuItem(text = { Text(it.name.lowercase().replaceFirstChar(Char::uppercase)) }, onClick = { status = it; statusMenu = false }) } }
            }
            ExposedDropdownMenuBox(expanded = projectMenu, onExpandedChange = { projectMenu = it }) {
                OutlinedTextField(projects.firstOrNull { it.id == projectId }?.title ?: "No linked project", {}, readOnly = true, label = { Text("Link project (optional)") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                    DropdownMenuItem(text = { Text("No linked project") }, onClick = { projectId = null; projectMenu = false })
                    projects.forEach { project -> DropdownMenuItem(text = { Text(project.title) }, onClick = { projectId = project.id; projectMenu = false }) }
                }
            }
            OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d ->
                    val chosen = LocalDate.of(y, m + 1, d)
                    val previousTime = reminderMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                    dateMillis = chosen.atStartOfDay(zone).toInstant().toEpochMilli()
                    if (previousTime != null) reminderMillis = chosen.atTime(previousTime).atZone(zone).toInstant().toEpochMilli()
                }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}") }
            OutlinedButton(onClick = {
                val initial = time ?: java.time.LocalTime.of(9, 0)
                TimePickerDialog(context, { _, h, min -> reminderMillis = date.atTime(h, min).atZone(zone).toInstant().toEpochMilli() }, initial.hour, initial.minute, false).show()
            }, modifier = Modifier.fillMaxWidth()) { Text(if (time == null) "Set reminder time (optional)" else "Reminder: ${time.format(DateTimeFormatter.ofPattern("h:mm a"))}") }
            if (reminderMillis != null) TextButton(onClick = { reminderMillis = null }) { Text("Remove reminder") }
            Text("Reminders are stored and scheduled on this device only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { Button(enabled = title.isNotBlank(), onClick = {
        onSave(PlannerItem(id = item?.id ?: 0, projectId = projectId, title = title.trim(), caption = item?.caption.orEmpty(), platform = platform, scheduledDate = dateMillis, status = status, reminderAtMillis = reminderMillis, logoVisible = item?.logoVisible ?: false, textReadable = item?.textReadable ?: false, captionHashtagsAdded = item?.captionHashtagsAdded ?: false, callToActionAdded = item?.callToActionAdded ?: false, safeZoneChecked = item?.safeZoneChecked ?: false))
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
