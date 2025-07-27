package com.example.mattshealthtracker

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

// Data class to hold information for the dialog
data class DialogInfo(val title: String, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit,
    currentSignedInAccount: GoogleSignInAccount?,
    onSignedInAccountChange: (GoogleSignInAccount?) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appPackageName = context.packageName
    val appVersion = BuildConfig.VERSION_NAME
    val githubLink = "https://github.com/matyasmatta/matts-health-tracker"

    // --- State Variables ---
    var googleDriveSignInEnabled by remember { mutableStateOf(currentSignedInAccount != null) } // For the main Drive toggle
    var performAutoSync by remember { mutableStateOf(AppGlobals.performAutoSync) }

    var showHealthConnectDialog by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val syncManager = remember { Sync(context) } // Instance of your Sync class

    val currentDeviceRole by rememberUpdatedState(AppGlobals.deviceRole)
    val currentEnergyUnit by rememberUpdatedState(AppGlobals.energyUnitPreference)
    val currentUserProfile by rememberUpdatedState(AppGlobals.userProfile)
    val currentAppDeviceID by rememberUpdatedState(AppGlobals.appDeviceID)

    // State for the generic information dialog
    var showInformationDialog by remember { mutableStateOf(false) }
    var currentDialogInfo by remember { mutableStateOf<DialogInfo?>(null) }

    val displayInfoDialog = { title: String, message: String ->
        currentDialogInfo = DialogInfo(title, message)
        showInformationDialog = true
    }

    // --- ActivityResultLaunchers ---
    val signInLauncher = GoogleDriveUtils.rememberGoogleSignInLauncher { account ->
        onSignedInAccountChange(account)
        googleDriveSignInEnabled = account != null
        Toast.makeText(
            context,
            if (account != null) "Google Sign-in successful: ${account.email}" else "Google Sign-in failed or cancelled.",
            Toast.LENGTH_SHORT
        ).show()
    }

    val exportToDeviceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(context, "Export cancelled.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        // TODO: Implement actual local export using the 'uri' with a utility function
        // For example, by packaging databases and writing to the uri's OutputStream
        Toast.makeText(
            context,
            "Local export (SAF) needs a dedicated local zip function in Utils for URI.",
            Toast.LENGTH_LONG
        ).show()
        Log.w("SettingsDialog", "SAF Export: Needs util for local zipping to URI: $uri")
    }

    val restoreFromFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = syncManager.manualRestoreFromLocalMhtFile(uri)
                // Toast messages are handled within manualRestoreFromLocalMhtFile
                if (success) {
                    Log.i("SettingsDialog", "Manual restore initiated successfully via settings.")
                    // Consider if app restart or data refresh is needed and how to signal it
                }
            }
        } else {
            Toast.makeText(context, "File selection cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Effects ---
    LaunchedEffect(currentSignedInAccount) {
        googleDriveSignInEnabled = currentSignedInAccount != null
    }

    LaunchedEffect(AppGlobals.performAutoSync) {
        performAutoSync = AppGlobals.performAutoSync
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
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                UserProfileSettingsSection(
                    currentUserProfile = currentUserProfile,
                    onEditProfileClick = { showUserProfileDialog = true },
                    onIconClick = displayInfoDialog
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                PreferencesSection(
                    context = context,
                    currentEnergyUnit = currentEnergyUnit,
                    currentDeviceRole = currentDeviceRole,
                    onIconClick = displayInfoDialog
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Updated DataManagementSection
                DataManagementSection(
                    context = context,
                    syncManager = syncManager, // Pass SyncManager
                    googleDriveSignInEnabled = googleDriveSignInEnabled,
                    onGoogleDriveSignInEnabledChange = { enabled ->
                        googleDriveSignInEnabled = enabled
                        if (enabled) {
                            if (currentSignedInAccount == null) {
                                scope.launch {
                                    GoogleDriveUtils.signInToGoogleDrive(context, signInLauncher)
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Google Drive features enabled.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            // Optionally confirm sign out, for now just a toast
                            // Also, if they disable Google Drive sign-in, should auto-sync be disabled too?
                            AppGlobals.updatePerformAutoSync(
                                context,
                                false
                            ) // Disable auto-sync if Drive is disabled
                            performAutoSync = false
                            Toast.makeText(
                                context,
                                "Google Drive features disabled. Auto-sync also disabled.",
                                Toast.LENGTH_LONG
                            ).show()
                            // Note: Signing out explicitly would be: GoogleDriveUtils.signOut(context) { onSignedInAccountChange(null) }
                        }
                    },
                    performAutoSync = performAutoSync,
                    onPerformAutoSyncChange = { enabled ->
                        if (enabled && currentSignedInAccount == null) {
                            Toast.makeText(
                                context,
                                "Please sign in to Google Drive to enable automatic sync.",
                                Toast.LENGTH_LONG
                            ).show()
                            // Optionally trigger sign-in:
                            // scope.launch { GoogleDriveUtils.signInToGoogleDrive(context, signInLauncher) }
                        } else {
                            AppGlobals.updatePerformAutoSync(context, enabled)
                            performAutoSync =
                                enabled // Update local state for immediate UI reflection
                            Toast.makeText(
                                context,
                                if (enabled) "Automatic sync enabled." else "Automatic sync disabled.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    currentSignedInAccount = currentSignedInAccount,
                    onManualBackupToDriveClick = {
                        scope.launch {
                            syncManager.manualBackupToDrive()
                            // Toasts are handled within manualBackupToDrive
                        }
                    },
                    onRestoreFromFileClick = {
                        // Ensure MIME types cover .mht (which are zip files)
                        restoreFromFileLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onExportToDeviceClick = {
                        val timestamp = AppGlobals.getUtcTimestampForFileName().replace(":", "")
                            .replace("-", "")
                        exportToDeviceLauncher.launch("mht-backup-$timestamp.zip")
                    },
                    onIconClick = displayInfoDialog
                )


                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Health Connect Button
                Button(
                    onClick = { showHealthConnectDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = null, // Content description for Button text itself is enough
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Health Connect Demo")
                    Spacer(Modifier.weight(1f)) // Push info icon to the end
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Health Connect Information",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    displayInfoDialog(
                                        "Health Connect Demo",
                                        "This section demonstrates integration with Health Connect by Google, allowing the app to read and write health and fitness data securely, with user consent."
                                    )
                                }
                            ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }


                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                AppInfoSection(
                    context = context,
                    appPackageName = appPackageName,
                    appVersion = appVersion,
                    currentAppDeviceID = currentAppDeviceID,
                    onGitHubClick = { uriHandler.openUri(githubLink) },
                    onIconClick = displayInfoDialog
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
                        Text("Close", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showUserProfileDialog) {
        UserProfileDialog(
            currentUserProfile = currentUserProfile,
            onDismissRequest = { showUserProfileDialog = false },
            onUserProfileUpdate = { updatedProfile ->
                AppGlobals.updateUserProfile(context, updatedProfile)
                showUserProfileDialog = false // Handled in UserProfileDialog's onDismiss
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showInformationDialog && currentDialogInfo != null) {
        InformationDialog(
            title = currentDialogInfo!!.title,
            message = currentDialogInfo!!.message,
            onDismissRequest = { showInformationDialog = false }
        )
    }

    if (showHealthConnectDialog) {
        HealthConnectDialog(onDismissRequest = { showHealthConnectDialog = false })
    }
}

// --- Section Composables ---
// UserProfileSettingsSection and PreferencesSection remain the same from your previous code.

@Composable
private fun UserProfileSettingsSection(
    currentUserProfile: UserProfile,
    onEditProfileClick: () -> Unit,
    onIconClick: (title: String, message: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "User Profile",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        val dobFormatted = currentUserProfile.dateOfBirth?.format(
            DateTimeFormatter.ofPattern(
                "dd/MM/yyyy",
                java.util.Locale.getDefault() // Use default locale or specify as needed
            )
        ) ?: "Not set"
        val heightFormatted = currentUserProfile.heightCm?.let { "%.0f cm".format(it) }
            ?: "Not set" // Format to no decimal places
        val genderFormatted = currentUserProfile.gender.name.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }


        InfoRow(
            icon = Icons.Filled.Person,
            label = "Gender",
            value = genderFormatted,
            onIconClick = {
                onIconClick(
                    "Gender Information",
                    "Your biological gender, used for more accurate estimations of Basal Metabolic Rate (BMR) and Total Daily Energy Expenditure (TDEE)."
                )
            }
        )
        InfoRow(
            icon = Icons.Filled.Cake,
            label = "Date of Birth",
            value = dobFormatted,
            onIconClick = {
                onIconClick(
                    "Date of Birth Information",
                    "Your date of birth, used to calculate your age for BMR and TDEE estimations."
                )
            }
        )
        InfoRow(
            icon = Icons.Filled.Height,
            label = "Height",
            value = heightFormatted,
            onIconClick = {
                onIconClick(
                    "Height Information",
                    "Your height in centimeters, used for BMR and TDEE estimations."
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onEditProfileClick, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null, // Button text is descriptive
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
    onIconClick: (title: String, message: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Preferences",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        val formattedEnergyUnitName = when (currentEnergyUnit) {
            EnergyUnit.KCAL -> "kcal"
            EnergyUnit.KJ -> "kJ"
        }

        // Energy Unit Row
        SettingSwitchRow(
            icon = Icons.Filled.LocalFireDepartment,
            title = "Energy Unit: $formattedEnergyUnitName",
            infoContentDescription = "Energy Unit Information",
            checked = currentEnergyUnit == EnergyUnit.KJ, // True if KJ is selected
            onCheckedChange = { isChecked ->
                val newUnit = if (isChecked) EnergyUnit.KJ else EnergyUnit.KCAL
                AppGlobals.updateEnergyUnit(context, newUnit)
                val toastUnitName = if (newUnit == EnergyUnit.KJ) "kJ" else "kcal"
                Toast.makeText(context, "Energy unit set to $toastUnitName", Toast.LENGTH_SHORT)
                    .show()
            },
            onIconClick = {
                onIconClick(
                    "Energy Unit",
                    "Choose your preferred unit for displaying energy values throughout the application.\n\n" +
                            "kcal: Kilocalories (often referred to as Calories) are a common unit for food energy.\n\n" +
                            "kJ: Kilojoules are the standard international (SI) unit of energy. 1 kcal is approximately 4.184 kJ.\n\n" +
                            "The switch toggles between kcal (off) and kJ (on)."
                )
            }
        )

        // Device Role Row
        SettingSwitchRow(
            icon = Icons.Filled.Devices,
            title = "Device Role: ${
                currentDeviceRole.name.lowercase()
                    .replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
            }",
            infoContentDescription = "Device Role Information",
            checked = currentDeviceRole == DeviceRole.PRIMARY, // True if Primary is selected
            onCheckedChange = { isChecked ->
                val newRole = if (isChecked) DeviceRole.PRIMARY else DeviceRole.SECONDARY
                AppGlobals.updateDeviceRole(context, newRole)
                Toast.makeText(
                    context,
                    "Device role set to ${
                        newRole.name.lowercase()
                            .replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
                    }",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onIconClick = {
                onIconClick(
                    "Device Role",
                    "Primary Device (Switch ON):\nHandles all data operations and can sync to Google Drive. Health Connect data from this device can be used as a source of truth.\n\n" +
                            "Secondary Device (Switch OFF):\nPrimarily for viewing data. It receives data from a Primary device via Google Drive sync but typically does not initiate uploads or write to Health Connect."
                )
            }
        )
    }
}


@Composable
private fun DataManagementSection(
    context: Context,
    syncManager: Sync, // Pass the Sync instance
    googleDriveSignInEnabled: Boolean,
    onGoogleDriveSignInEnabledChange: (Boolean) -> Unit,
    performAutoSync: Boolean,
    onPerformAutoSyncChange: (Boolean) -> Unit,
    currentSignedInAccount: GoogleSignInAccount?,
    onManualBackupToDriveClick: () -> Unit,
    onRestoreFromFileClick: () -> Unit,
    onExportToDeviceClick: () -> Unit,
    onIconClick: (title: String, message: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Data & Synchronization",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        // Google Drive Sign-In Toggle
        SettingSwitchRow(
            icon = Icons.Filled.CloudSync, // Using CloudSync for the main toggle
            title = if (currentSignedInAccount != null) "Drive Sync: ${currentSignedInAccount.email}" else "Sign in to Google Drive",
            infoContentDescription = "Google Drive Sign-In Information",
            checked = googleDriveSignInEnabled,
            onCheckedChange = onGoogleDriveSignInEnabledChange,
            onIconClick = {
                onIconClick(
                    "Google Drive Synchronization",
                    "Sign in with your Google Account to enable cloud backup and synchronization features.\n\n" +
                            "When enabled, you can:\n" +
                            "- Automatically sync data changes between devices (if 'Automatic Sync' is on).\n" +
                            "- Manually upload your current data to Drive.\n" +
                            "- Manually restore data from a file on your device (which could be a Drive download)."
                )
            }
        )

        // Automatic Sync Toggle (only enabled if Google Drive is signed in)
        SettingSwitchRow(
            icon = Icons.Filled.Autorenew,
            title = "Automatic Sync (on app launch)",
            infoContentDescription = "Automatic Sync Information",
            checked = performAutoSync,
            onCheckedChange = onPerformAutoSyncChange,
            enabled = currentSignedInAccount != null, // Only enable if signed in
            onIconClick = {
                onIconClick(
                    "Automatic Sync (on app launch)",
                    "If enabled and you are signed into Google Drive, the app will attempt to download and restore the latest data from another device when you open the app.\n\n" +
                            "This helps keep your data consistent across multiple devices without manual intervention.\n\n" +
                            "Requires Google Drive sign-in to be active."
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp)) // Some spacing before buttons

        // Manual Backup to Google Drive Button
        SettingsButton(
            icon = Icons.Filled.UploadFile,
            text = "Upload Data to Drive",
            onClick = onManualBackupToDriveClick,
            enabled = currentSignedInAccount != null,
            onInfoClick = {
                onIconClick(
                    "Upload Data to Drive",
                    "Manually creates a new backup of your current app data and uploads it as an '.mht' file to your Google Drive in the app's dedicated folder.\n\n" +
                            "Useful for ensuring your latest data is in the cloud, or for creating a specific restore point.\n\n" +
                            "Requires Google Drive sign-in."
                )
            }
        )

        // Manual Restore from Device File Button
        SettingsButton(
            icon = Icons.Filled.Download, // Or Restore
            text = "Restore Data from File",
            onClick = onRestoreFromFileClick,
            // Enabled even if not signed into Drive, as file could be local
            // but it's more common to restore Drive backups this way too.
            enabled = true,
            onInfoClick = {
                onIconClick(
                    "Restore Data from File",
                    "Allows you to select an '.mht' backup file from your device's storage (e.g., Downloads, or a file you previously saved from Drive) and restore your app data from it.\n\n" +
                            "WARNING: This will overwrite your current app data. A redundancy backup of your current data will be attempted before restoring."
                )
            }
        )

        // Export to Device (Local Backup) Button
        SettingsButton(
            icon = Icons.Filled.SaveAlt,
            text = "Export Data to Device Storage",
            onClick = onExportToDeviceClick,
            enabled = true, // Always enabled
            onInfoClick = {
                onIconClick(
                    "Export Data to Device Storage",
                    "Manually creates a ZIP backup of your app data and saves it to your device's local storage (you'll choose the location via the system file picker).\n\n" +
                            "This backup is NOT automatically synced to Google Drive and is for local archival or manual transfer."
                )
            }
        )
    }
}

@Composable
private fun AppInfoSection(
    context: Context,
    appPackageName: String,
    appVersion: String,
    currentAppDeviceID: String?,
    onGitHubClick: () -> Unit,
    onIconClick: (title: String, message: String) -> Unit
) {
    var showRegenerateIdConfirmDialog by remember { mutableStateOf(false) }

    Text(
        "About & Advanced",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
    InfoRow(
        icon = Icons.Filled.Article, // Changed for package name
        label = "Package Name",
        value = appPackageName, // Use the variable
        isMonospace = true,
        onIconClick = {
            onIconClick(
                "Package Name",
                "The unique identifier for this application on your device and in the Google Play Store (if applicable). It's typically in a reverse domain name format."
            )
        }
    )
    InfoRow(
        icon = Icons.Filled.Info,
        label = "Version",
        value = appVersion,
        onIconClick = {
            onIconClick(
                "App Version",
                "The current version of the Matt's Health Tracker application installed on your device. Useful for troubleshooting and checking for updates."
            )
        }
    )

    // Device ID with Clickable Icon and Regenerate Button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp), // Reduced padding slightly
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Icon(
                Icons.Filled.Fingerprint,
                contentDescription = null, // Label text is sufficient
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onIconClick(
                                "Device ID",
                                "A unique identifier generated for this app instance on this device. " +
                                        "It helps distinguish data from this device if you use the app on multiple devices and sync via Google Drive. " +
                                        "Regenerating it creates a new ID; old cloud backups might not be recognized as originating from this 'new' device identity unless the sync system handles ID changes."
                            )
                        }
                    ),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Device ID", style = MaterialTheme.typography.bodyLarge)
                Text(
                    currentAppDeviceID ?: "Not available",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, // Ensure it doesn't wrap excessively if long
                    // overflow = TextOverflow.Ellipsis // Add if needed
                )
            }
        }
        // IconButton for Regenerate
        if (currentAppDeviceID != null) { // Only show if ID exists
            IconButton(
                onClick = { showRegenerateIdConfirmDialog = true }
            ) {
                Icon(
                    Icons.Filled.Cached,
                    contentDescription = "Regenerate Device ID",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }


    InfoRow(
        icon = Icons.Filled.Code,
        label = "View on GitHub",
        value = "", // Value not needed as label is the link text
        isLink = true,
        onClick = onGitHubClick,
        onIconClick = {
            onIconClick(
                "View on GitHub",
                "Opens the source code repository for this application on GitHub. You can view the code, report issues, or contribute."
            )
        }
    )

    if (showRegenerateIdConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateIdConfirmDialog = false },
            title = { Text("Confirm Regenerate ID") },
            text = { Text("Regenerating the Device ID might affect how this device is identified in cloud backups. Existing cloud data from the old ID will remain, but new backups will use the new ID. Are you sure?") },
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
    onClick: (() -> Unit)? = null,
    onIconClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowModifier =
        if (onClick != null && isLink) { // Special handling for links to make whole row clickable
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
        } else if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
        } else {
            Modifier
        }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = 10.dp), // Adjusted padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null, // Label should describe it
            modifier = Modifier
                .size(24.dp)
                .then(
                    if (onIconClick != null && !isLink) { // Don't make icon separately clickable if row is a link
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onIconClick
                        )
                    } else Modifier
                ),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = if (isLink) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodyLarge,
                color = if (isLink) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
            if (value.isNotEmpty()) {
                Text(
                    value,
                    style = if (isMonospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


/**
 * A reusable row composable for settings that include a title, an icon,
 * an optional info icon, and a switch.
 */
@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    infoContentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onIconClick: () -> Unit,
    enabled: Boolean = true // Added enabled state for the switch and row
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp) // Reduced padding
            .semantics { if (!enabled) this.disabled() } // Semantics for accessibility
            .clickable(
                enabled = enabled,
                onClick = { if (enabled) onCheckedChange(!checked) }), // Make row clickable to toggle switch
        verticalAlignment = Alignment.CenterVertically,

        ) {
        Icon(
            icon,
            contentDescription = null, // Title is descriptive
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    enabled = enabled, // Info icon clickable even if switch is disabled for context
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onIconClick
                ),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            )
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            )
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * A reusable button for settings screens, with an icon, text, and an optional info icon.
 */
@Composable
private fun SettingsButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), // Reduced padding
        enabled = enabled
    ) {
        Icon(
            icon,
            contentDescription = null, // Button text is descriptive
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text, modifier = Modifier.weight(1f)) // Text takes available space

        if (onInfoClick != null) {
            Spacer(Modifier.size(ButtonDefaults.IconSpacing)) // Spacer before info icon
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp), // Ensure info icon is a decent tap target
                enabled = enabled // Info available even if button action is disabled
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "$text Information",
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary // Or primary if on surface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}


@Composable
fun InformationDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("OK")
            }
        }
    )
}
