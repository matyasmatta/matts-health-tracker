package com.example.mattshealthtracker // Use your actual package name

// Remove: import android.app.DatePickerDialog
// Remove: import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
// Remove: import androidx.compose.material.icons.filled.ArrowDropDown // ExposedDropdownMenuBox has its own
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// No longer need LocalContext directly for the old DatePickerDialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Remove: import java.util.Calendar // Not needed for M3 DatePicker

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

    // --- State for Material 3 DatePickerDialog ---
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateOfBirth?.toInstant()?.toEpochMilli()
            ?: ZonedDateTime.now().minusYears(18)
                .toInstant().toEpochMilli(), // Default to 18 years ago or today
        // You can set yearRange if needed: yearRange = IntRange(1900, LocalDate.now().year)
    )

    // --- State for Gender ExposedDropdownMenu ---
    var genderDropdownExpanded by remember { mutableStateOf(false) }
    val genderOptions = Gender.values()

    // Function to format gender names for display
    fun formatGenderName(genderEnum: Gender): String {
        return genderEnum.name.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(vertical = 32.dp) // Give some vertical padding for scrollable content
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()), // Allow scrolling if content overflows
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Edit User Profile", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(24.dp))

                // Gender Selector using ExposedDropdownMenuBox
                Text(
                    "Gender",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                            .menuAnchor() // Important for ExposedDropdownMenuBox
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
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Date of Birth Selector using Material 3 DatePicker
                Text(
                    "Date of Birth",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = dateOfBirth?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "Not Set",
                    onValueChange = { /* Read Only - Value changes via DatePicker */ },
                    label = { Text("Select Date of Birth") },
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Filled.CalendarToday, "Select Date") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePickerDialog = true }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Height Input
                Text(
                    "Height (cm)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = heightCmString,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,1}\$"))) { // Allow max 1 decimal place
                            heightCmString = newValue
                        }
                    },
                    label = { Text("Enter Height in cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val updatedHeight = heightCmString.toDoubleOrNull()
                        // Date is selected from M3 DatePicker, convert millis to ZonedDateTime
                        val selectedMillis = datePickerState.selectedDateMillis
                        val updatedDateOfBirth = selectedMillis?.let {
                            ZonedDateTime.ofInstant(
                                Instant.ofEpochMilli(it),
                                ZoneId.systemDefault()
                            )
                        } ?: dateOfBirth // Fallback to original if nothing selected/changed

                        val updatedProfile = UserProfile(
                            gender = gender,
                            dateOfBirth = updatedDateOfBirth,
                            heightCm = updatedHeight
                        )
                        onUserProfileUpdate(updatedProfile)
                        onDismissRequest()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }

    // Material 3 DatePickerDialog
    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePickerDialog = false
                        // Convert selectedDateMillis to ZonedDateTime and update the state
                        datePickerState.selectedDateMillis?.let { millis ->
                            // When confirming, convert the selected UTC millis to the system's default ZoneId for LocalDate part
                            // and then create a ZonedDateTime at the start of that day.
                            val selectedLocalDate =
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            dateOfBirth = ZonedDateTime.of(
                                selectedLocalDate.atStartOfDay(),
                                ZoneId.systemDefault()
                            )
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

