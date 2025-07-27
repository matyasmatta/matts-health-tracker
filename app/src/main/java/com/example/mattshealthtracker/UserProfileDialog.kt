package com.example.mattshealthtracker

// REMOVE: import androidx.compose.foundation.Indication // Not strictly needed for this correction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
// REMOVE: import androidx.compose.material.ripple.rememberRipple // DEPRECATED
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// REMOVE: import androidx.compose.runtime.getValue // Not used directly
// REMOVE: import androidx.compose.runtime.setValue // Not used directly
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDialog(
    currentUserProfile: UserProfile,
    onDismissRequest: () -> Unit,
    onUserProfileUpdate: (UserProfile) -> Unit
) {
    var gender by remember { mutableStateOf(currentUserProfile.gender) }
    var dateOfBirth by remember { mutableStateOf(currentUserProfile.dateOfBirth) }
    var heightCmString by remember { mutableStateOf(currentUserProfile.heightCm?.toString() ?: "") }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateOfBirth?.toInstant()?.toEpochMilli()
            ?: ZonedDateTime.now().minusYears(18).toInstant().toEpochMilli(),
        yearRange = IntRange(1900, LocalDate.now().year)
    )

    var genderDropdownExpanded by remember { mutableStateOf(false) }
    val genderOptions = Gender.values()

    fun formatGenderName(genderEnum: Gender): String {
        return genderEnum.name.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.large,
            // tonalElevation = androidx.wear.compose.material.dialog.DialogDefaults.TonalElevation, // REMOVED - Incorrect import
            // Material 3 Surface handles its own elevation based on context (e.g., within a Dialog)
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Edit User Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Gender Selector
                Text(
                    "Gender",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = genderDropdownExpanded,
                    onExpandedChange = { genderDropdownExpanded = !genderDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = formatGenderName(gender),
                        onValueChange = { /* Read Only */ },
                        label = { Text("Select Gender") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = genderDropdownExpanded,
                        onDismissRequest = { genderDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        genderOptions.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(formatGenderName(selectionOption)) },
                                onClick = {
                                    gender = selectionOption
                                    genderDropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Date of Birth Selector
                Text(
                    "Date of Birth",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                val interactionSourceForDate = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSourceForDate,
                            indication = null, // Passing null for indication; M3 often provides a default ripple.
                            // If you truly want NO visual feedback, this is correct.
                            // For a default ripple on the Box, this should also work.
                            onClick = { showDatePickerDialog = true }
                        )
                ) {
                    OutlinedTextField(
                        value = dateOfBirth?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            ?: "Not Set",
                        onValueChange = { /* Read Only */ },
                        label = { Text("Select Date of Birth") },
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Filled.CalendarToday, "Select Date") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        interactionSource = interactionSourceForDate
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Height Input
                Text(
                    "Height (cm)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = heightCmString,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,1}$"))) {
                            heightCmString = newValue
                        }
                    },
                    label = { Text("Enter Height in cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val updatedHeight = heightCmString.toDoubleOrNull()
                            val updatedProfile = UserProfile(
                                gender = gender,
                                dateOfBirth = dateOfBirth,
                                heightCm = updatedHeight
                            )
                            onUserProfileUpdate(updatedProfile)
                            onDismissRequest()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePickerDialog = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedLocalDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            dateOfBirth = ZonedDateTime.of(
                                selectedLocalDate.atStartOfDay(),
                                ZoneId.systemDefault()
                            )
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(
                state = datePickerState,
            )
        }
    }
}
