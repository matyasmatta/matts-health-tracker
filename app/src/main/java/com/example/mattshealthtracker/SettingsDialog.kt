package com.example.mattshealthtracker

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.DateTimeFormatter // For UserProfile summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit,
    currentSignedInAccount: GoogleSignInAccount?,
    onSignedInAccountChange: (GoogleSignInAccount?) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appPackageName = "com.example\n.mattshealthtracker"
    val appVersion = BuildConfig.VERSION_NAME
    val githubLink = "https://github.com/matyasmatta/matts-health-tracker"

    var googleDriveSyncEnabled by remember { mutableStateOf(currentSignedInAccount != null) }
    var showHealthConnectDialog by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) } // State for UserProfileDialog
    val scope = rememberCoroutineScope()

    // Observe AppGlobals states that can change
    val currentDeviceRole by rememberUpdatedState(AppGlobals.deviceRole)
    val currentEnergyUnit by rememberUpdatedState(AppGlobals.energyUnitPreference)
    val currentUserProfile by rememberUpdatedState(AppGlobals.userProfile)
    val currentAppDeviceID by rememberUpdatedState(AppGlobals.appDeviceID)


    var showDeviceRoleInfoDialog by remember { mutableStateOf(false) }

    val signInLauncher = GoogleDriveUtils.rememberGoogleSignInLauncher { account ->
        onSignedInAccountChange(account)
        if (account != null) {
            googleDriveSyncEnabled = true
            Toast.makeText(
                context,
                "Google Sign-in successful: ${account.email}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            googleDriveSyncEnabled = false
            Toast.makeText(context, "Google Sign-in failed or cancelled.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    val exportToDeviceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            // Placeholder - requires local zip utility
            Toast.makeText(
                context,
                "Local export (SAF) needs a dedicated local zip function in Utils.",
                Toast.LENGTH_LONG
            ).show()
            Log.w("SettingsDialog", "SAF Export: Needs util for local zipping only.")
        }
    }

    LaunchedEffect(currentSignedInAccount) {
        googleDriveSyncEnabled = currentSignedInAccount != null
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // User Profile Section
                UserProfileSettingsSection(
                    currentUserProfile = currentUserProfile, // Use the observed state
                    onEditProfileClick = { showUserProfileDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                PreferencesSection(
                    context = context,
                    currentEnergyUnit = currentEnergyUnit, // Use observed state
                    currentDeviceRole = currentDeviceRole, // Use observed state
                    onDeviceRoleInfoClick = { showDeviceRoleInfoDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                DataManagementSection(
                    context = context,
                    googleDriveSyncEnabled = googleDriveSyncEnabled,
                    onGoogleDriveSyncEnabledChange = { enabled ->
                        googleDriveSyncEnabled = enabled
                        if (enabled) {
                            if (currentSignedInAccount == null) {
                                scope.launch {
                                    GoogleDriveUtils.signInToGoogleDrive(
                                        context,
                                        signInLauncher
                                    )
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Drive backup features enabled.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Drive backup features disabled.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    currentSignedInAccount = currentSignedInAccount,
                    onExportToDriveClick = {
                        scope.launch {
                            currentSignedInAccount?.let {
                                GoogleDriveUtils.exportDataToCSVZip(
                                    context,
                                    Uri.EMPTY,
                                    it
                                )
                            }
                        }
                    },
                    onExportToDeviceClick = {
                        val timestamp =
                            AppGlobals.getUtcTimestampForFileName() // Use correct timestamp
                        exportToDeviceLauncher.launch("mht-backup-$timestamp.zip")
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Button( // Health Connect
                    onClick = { showHealthConnectDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Link,
                        "Health Connect Icon",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Health Connect Demo")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                AppInfoSection(
                    context = context, // For regenerating device ID
                    appPackageName = appPackageName,
                    appVersion = appVersion,
                    currentAppDeviceID = currentAppDeviceID, // Use observed state
                    onGitHubClick = { uriHandler.openUri(githubLink) }
                )

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    "Made with 💖 in 🇪🇺",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    if (showUserProfileDialog) {
        UserProfileDialog(
            currentUserProfile = currentUserProfile, // Pass the observed state
            onDismissRequest = { showUserProfileDialog = false },
            onUserProfileUpdate = { updatedProfile ->
                AppGlobals.updateUserProfile(context, updatedProfile)
                showUserProfileDialog = false // Dismiss after update
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDeviceRoleInfoDialog) {
        DeviceRoleInfoDialog(currentDeviceRole = currentDeviceRole) {
            showDeviceRoleInfoDialog = false
        }
    }

    if (showHealthConnectDialog) {
        HealthConnectDialog(onDismissRequest = { showHealthConnectDialog = false })
    }
}

@Composable
private fun UserProfileSettingsSection(
    currentUserProfile: UserProfile,
    onEditProfileClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "User Profile",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        // Display a summary of the profile
        val dobFormatted =
            currentUserProfile.dateOfBirth?.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                ?: "Not set"
        val heightFormatted = currentUserProfile.heightCm?.let { "$it cm" } ?: "Not set"
        val genderFormatted = currentUserProfile.gender.name.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        InfoRow(icon = Icons.Filled.Person, label = "Gender", value = genderFormatted)
        InfoRow(icon = Icons.Filled.Cake, label = "Date of Birth", value = dobFormatted)
        InfoRow(icon = Icons.Filled.Height, label = "Height", value = heightFormatted)

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onEditProfileClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit Profile",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Edit Profile")
        }
    }
}


@Composable
private fun PreferencesSection(
    context: Context,
    currentEnergyUnit: EnergyUnit,
    currentDeviceRole: DeviceRole,
    onDeviceRoleInfoClick: () -> Unit
) {
    Text(
        "Preferences",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Energy Unit (kcal/kJ)", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = currentEnergyUnit == EnergyUnit.KJ,
            onCheckedChange = { isChecked ->
                val newUnit = if (isChecked) EnergyUnit.KJ else EnergyUnit.KCAL
                AppGlobals.updateEnergyUnit(context, newUnit)
                Toast.makeText(context, "Energy unit set to ${newUnit.name}", Toast.LENGTH_SHORT)
                    .show()
            }
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = "Device Role: ${
                    currentDeviceRole.name.lowercase().replaceFirstChar { it.titlecase() }
                }",
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onDeviceRoleInfoClick) {
                Icon(
                    Icons.Filled.Info,
                    "Device Role Information",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Switch(
            checked = currentDeviceRole == DeviceRole.PRIMARY,
            onCheckedChange = { isChecked ->
                val newRole = if (isChecked) DeviceRole.PRIMARY else DeviceRole.SECONDARY
                AppGlobals.updateDeviceRole(context, newRole)
                Toast.makeText(
                    context,
                    "Device role set to ${
                        newRole.name.lowercase().replaceFirstChar { it.titlecase() }
                    }",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun DataManagementSection(
    context: Context,
    googleDriveSyncEnabled: Boolean,
    onGoogleDriveSyncEnabledChange: (Boolean) -> Unit,
    currentSignedInAccount: GoogleSignInAccount?,
    onExportToDriveClick: () -> Unit,
    onExportToDeviceClick: () -> Unit
) {
    Text(
        "Data Management",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Backup data to Drive", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = googleDriveSyncEnabled, onCheckedChange = onGoogleDriveSyncEnabledChange)
    }

    if (googleDriveSyncEnabled && currentSignedInAccount != null) {
        Button(
            onClick = onExportToDriveClick, modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Icon(
                Icons.Filled.UploadFile,
                "Export to Drive Icon",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Export Data to Drive Now")
        }
    }

    Button(
        onClick = onExportToDeviceClick, modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            Icons.Filled.Save,
            "Export to Device Icon",
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text("Export Data to Device Storage")
    }
}

@Composable
private fun AppInfoSection(
    context: Context, // Added context for regenerating ID
    appPackageName: String,
    appVersion: String,
    currentAppDeviceID: String?,
    onGitHubClick: () -> Unit
) {
    var showRegenerateIdConfirmDialog by remember { mutableStateOf(false) }

    Text(
        "About & Advanced",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
    InfoRow(
        icon = Icons.Filled.Article,
        label = "Package Name",
        value = appPackageName,
        isMonospace = true
    )
    InfoRow(icon = Icons.Filled.VerifiedUser, label = "Version", value = appVersion)

    // Device ID with Regenerate Button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Filled.Fingerprint,
                contentDescription = "Device ID",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Device ID", style = MaterialTheme.typography.bodyLarge)
                if (currentAppDeviceID != null) {
                    Text(
                        currentAppDeviceID,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        "Not set",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }
            }
        }
        IconButton(
            onClick = { showRegenerateIdConfirmDialog = true },
            enabled = currentAppDeviceID != null // Enable only if an ID exists
        ) {
            Icon(
                Icons.Filled.Autorenew,
                contentDescription = "Regenerate Device ID",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }


    InfoRow(
        icon = Icons.Filled.Code,
        label = "View on GitHub",
        value = "",
        isLink = true,
        onClick = onGitHubClick
    )

    if (showRegenerateIdConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateIdConfirmDialog = false },
            title = { Text("Confirm Regenerate ID") },
            text = { Text("Regenerating the Device ID might affect data synchronization if the old ID was used for identifying this device's backups. Are you sure you want to continue?") },
            confirmButton = {
                TextButton(onClick = {
                    AppGlobals.regenerateAppDeviceID(context)
                    Toast.makeText(context, "Device ID regenerated.", Toast.LENGTH_SHORT).show()
                    showRegenerateIdConfirmDialog = false
                }) { Text("Regenerate") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateIdConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}


@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isMonospace: Boolean = false,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                label,
                style = if (isLink) MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                else MaterialTheme.typography.bodyLarge,
                color = if (isLink) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
            if (value.isNotEmpty()) {
                Text(
                    value,
                    style = if (isMonospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DeviceRoleInfoDialog(
    currentDeviceRole: DeviceRole,
    onDismissRequest: () -> Unit
) { // Pass current role
    val infoText = when (currentDeviceRole) { // Use passed role
        DeviceRole.PRIMARY -> "Primary Device:\nHandles all data operations and can sync to Google Drive if enabled. Health Connect data from this device can be used as a source of truth."
        DeviceRole.SECONDARY -> "Secondary Device:\nPrimarily for viewing data. It can receive data from a Primary device via Google Drive sync but typically does not initiate uploads or write to Health Connect."
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Device Role Information") },
        text = { Text(infoText) },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("OK") } }
    )
}

// Ensure HealthConnectDialog is defined in your project
// @Composable
// fun HealthConnectDialog(onDismissRequest: () -> Unit) { ... }
