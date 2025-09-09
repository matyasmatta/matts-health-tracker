package com.example.mattshealthtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun MedicationScreen(openedDay: String) {
    val context = LocalContext.current
    val dbHelper = NewMedicationDatabaseHelper(context)

    val medications = remember { mutableStateListOf<MedicationItem>() }
    var sideEffects by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }

    // Load initial data when openedDay changes.
    LaunchedEffect(openedDay) {
        val fetchedMedications = dbHelper.fetchMedicationItemsForDateWithDefaults(openedDay)
        medications.clear()
        medications.addAll(fetchedMedications)
        dbHelper.insertOrUpdateMedicationList(openedDay, fetchedMedications)
        sideEffects = dbHelper.fetchSideEffectsForDate(openedDay) ?: ""
    }

    fun saveMedicationData() {
        dbHelper.insertOrUpdateMedicationList(openedDay, medications.toList())
        dbHelper.insertOrUpdateSideEffects(openedDay, sideEffects)
        dbHelper.exportToCSV(context)
    }

    fun updateMedication(medication: MedicationItem, newMedication: MedicationItem) {
        val index = medications.indexOf(medication)
        if (index != -1) {
            medications[index] = newMedication
            saveMedicationData()
        }
    }

    fun refreshMedications() {
        val fetchedMedications = dbHelper.fetchMedicationItemsForDateWithDefaults(openedDay)
        medications.clear()
        medications.addAll(fetchedMedications)
        saveMedicationData()
    }

    val starredMedications = medications.filter { it.isStarred }
    val nonStarredMedications = medications.filter { !it.isStarred }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with manage button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Medications",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            IconButton(onClick = { showManageDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Manage Medications")
            }
        }

        // Starred medications (always visible)
        starredMedications.forEach { medication ->
            MedicationItemRow(
                medication = medication,
                onIncrement = {
                    updateMedication(
                        medication,
                        medication.copy(dosage = medication.dosage + medication.step)
                    )
                },
                onDecrement = {
                    if (medication.dosage - medication.step >= 0)
                        updateMedication(
                            medication,
                            medication.copy(dosage = medication.dosage - medication.step)
                        )
                },
                onToggleStar = {
                    updateMedication(medication, medication.copy(isStarred = !medication.isStarred))
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Expandable section for non-starred medications
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = if (expanded) "Close medication tab" else "Add medications",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }

        if (expanded) {
            LazyColumn {
                items(nonStarredMedications) { medication ->
                    MedicationItemRow(
                        medication = medication,
                        onIncrement = {
                            updateMedication(
                                medication,
                                medication.copy(dosage = medication.dosage + medication.step)
                            )
                        },
                        onDecrement = {
                            if (medication.dosage - medication.step >= 0)
                                updateMedication(
                                    medication,
                                    medication.copy(dosage = medication.dosage - medication.step)
                                )
                        },
                        onToggleStar = {
                            updateMedication(
                                medication,
                                medication.copy(isStarred = !medication.isStarred)
                            )
                        }
                    )
                    Divider()
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Side effects section
        Text(
            text = "Side effects",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        TextField(
            value = sideEffects,
            onValueChange = {
                sideEffects = it
                saveMedicationData()
            },
            placeholder = { Text("Enter any side effects here...") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Manage medications dialog
    if (showManageDialog) {
        MedicationManagementDialog(
            dbHelper = dbHelper,
            onDismiss = { showManageDialog = false },
            onMedicationsUpdated = { refreshMedications() }
        )
    }
}

@Composable
fun MedicationItemRow(
    medication: MedicationItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleStar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Star icon
        Icon(
            imageVector = if (medication.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = "Toggle Star",
            tint = if (medication.isStarred)
                MaterialTheme.colorScheme.secondary
            else LocalContentColor.current,
            modifier = Modifier
                .size(24.dp)
                .clickable { onToggleStar() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = medication.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        // Dosage counter
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDecrement() }) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease dosage")
            }
            Text(
                text = "${medication.dosage} ${medication.unit}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onIncrement() }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase dosage")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // For Scaffold, TopAppBar, FloatingActionButton
@Composable
fun MedicationManagementDialog(
    dbHelper: NewMedicationDatabaseHelper,
    onDismiss: () -> Unit,
    onMedicationsUpdated: () -> Unit
) {
    var medicationTemplates by remember { mutableStateOf(dbHelper.loadMedicationTemplates()) }
    var showAddOrEditDialog by remember { mutableStateOf<DefaultMedicationTemplate?>(null) } // null for add, template for edit
    var isAddMode by remember { mutableStateOf(false) }


    fun refreshTemplates() {
        medicationTemplates = dbHelper.loadMedicationTemplates()
        onMedicationsUpdated() // Callback to update the main screen if needed
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Allow dialog to be wider
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f) // Use most of the screen width
                .fillMaxHeight(0.85f), // Use most of the screen height
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Manage Medications") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Dialog")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant, // Or surface
                            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {
                            isAddMode = true
                            showAddOrEditDialog =
                                DefaultMedicationTemplate("", 0f, "") // Dummy template for add mode
                        },
                        icon = { Icon(Icons.Filled.Add, "Add new medication template") },
                        text = { Text("Add New") }
                    )
                },
                floatingActionButtonPosition = FabPosition.Center,
                content = { paddingValues ->
                    if (medicationTemplates.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(paddingValues)
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No medication templates found. Tap 'Add New' to create one.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .padding(paddingValues)
                                .fillMaxSize(), // Allow LazyColumn to take available space
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(medicationTemplates, key = { it.name }) { template ->
                                MedicationTemplateRow(
                                    template = template,
                                    onEdit = {
                                        isAddMode = false
                                        showAddOrEditDialog = template
                                    },
                                    onDelete = {
                                        dbHelper.removeMedicationTemplate(template.name)
                                        refreshTemplates()
                                    }
                                )
                                if (medicationTemplates.last() != template) {
                                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            // Add Spacer at the bottom to ensure FAB doesn't overlap last item too much
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            )
        }
    }

    // Unified Add/Edit Dialog
    showAddOrEditDialog?.let { currentTemplate ->
        AddEditMedicationDialog(
            template = if (isAddMode) null else currentTemplate, // Pass null if in add mode
            onDismiss = { showAddOrEditDialog = null },
            onSave = { name, step, unit ->
                if (isAddMode) {
                    dbHelper.addMedicationTemplate(name, step, unit)
                } else {
                    // When editing, currentTemplate.name is the old name
                    dbHelper.updateMedicationTemplate(currentTemplate.name, name, step, unit)
                }
                refreshTemplates()
                showAddOrEditDialog = null
            }
        )
    }
}

@Composable
fun MedicationTemplateRow(
    template: DefaultMedicationTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium, // Larger name
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Step: ${template.step} ${template.unit}",
                style = MaterialTheme.typography.bodyMedium, // Consistent body style
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row {
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit ${template.name}")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete ${template.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


@Composable
fun AddEditMedicationDialog(
    template: DefaultMedicationTemplate?,
    onDismiss: () -> Unit,
    onSave: (name: String, step: Float, unit: String) -> Unit
) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var step by remember { mutableStateOf(template?.step?.toString() ?: "") }
    var unit by remember { mutableStateOf(template?.unit ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (template == null) "Add Medication" else "Edit Medication",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medication Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    label = { Text("Step Size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (mg, IU, pack, etc.)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val stepFloat = step.toFloatOrNull()
                            if (name.isNotBlank() && stepFloat != null && stepFloat > 0 && unit.isNotBlank()) {
                                onSave(name, stepFloat, unit)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && step.toFloatOrNull() != null && step.toFloatOrNull()!! > 0 && unit.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}