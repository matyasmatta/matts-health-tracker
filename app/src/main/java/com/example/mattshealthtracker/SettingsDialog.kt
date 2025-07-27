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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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
    var googleDriveSignInEnabled by remember { mutableStateOf(currentSignedInAccount != null) }
    var performAutoSync by remember { mutableStateOf(AppGlobals.performAutoSync) }

    var showHealthConnectDialog by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val syncManager = remember { Sync(context) }

    val currentDeviceRole by rememberUpdatedState(AppGlobals.deviceRole)
    val currentEnergyUnit by rememberUpdatedState(AppGlobals.energyUnitPreference)
    val currentUserProfile by rememberUpdatedState(AppGlobals.userProfile)
    val currentAppDeviceID by rememberUpdatedState(AppGlobals.appDeviceID)

    var showInformationDialog by remember { mutableStateOf(false) }
    var currentDialogInfo by remember { mutableStateOf<DialogInfo?>(null) }

    val displayInfoDialog = { title: String, message: String ->
        currentDialogInfo = DialogInfo(title, message)
        showInformationDialog = true
    }

    // --- ActivityResultLaunchers ---
    val signInLauncher = GoogleDriveUtils.rememberGoogleSignInLauncher { account ->
        onSignedInAccountChange(account) // This will update currentSignedInAccount
        // googleDriveSignInEnabled will be updated by the LaunchedEffect below
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
                if (success) {
                    Log.i("SettingsDialog", "Manual restore initiated successfully via settings.")
                }
            }
        } else {
            Toast.makeText(context, "File selection cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Sign Out Handler ---
    val handleSignOut: () -> Unit = {
        GoogleDriveUtils.signOut(context) { success ->
            if (success) {
                onSignedInAccountChange(null)
                AppGlobals.updatePerformAutoSync(context, false)
                Toast.makeText(context, "Signed out successfully.", Toast.LENGTH_SHORT).show()
                Log.d("SettingsDialog", "Sign out successful, UI will update via LaunchedEffects.")
            } else {
                Toast.makeText(context, "Sign out failed. Please try again.", Toast.LENGTH_SHORT)
                    .show()
                Log.e("SettingsDialog", "Sign out failed from GoogleDriveUtils callback.")
            }
        }
    }

    // --- Effects ---
    LaunchedEffect(currentSignedInAccount) {
        googleDriveSignInEnabled = currentSignedInAccount != null
        if (currentSignedInAccount == null) {
            AppGlobals.updatePerformAutoSync(context, false) // Also turn off auto-sync
        }
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
                // ... (Title, UserProfileSettingsSection, PreferencesSection) ...
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

                DataManagementSection(
                    context = context,
                    syncManager = syncManager,
                    googleDriveSignInEnabled = googleDriveSignInEnabled,
                    onGoogleDriveSignInEnabledChange = { enabled ->
                        // No need to set googleDriveSignInEnabled here,
                        // it's primarily driven by currentSignedInAccount via LaunchedEffect
                        if (enabled) {
                            if (currentSignedInAccount == null) {
                                // Get the sign-in intent and launch it with the ActivityResultLauncher
                                val signInClient = GoogleSignIn.getClient(
                                    context,
                                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .requestScopes(com.google.android.gms.common.api.Scope(com.google.android.gms.common.Scopes.DRIVE_FILE)) // Ensure you have the correct scope
                                        .build()
                                )
                                signInLauncher.launch(signInClient.signInIntent) // Use the launcher here
                            } else {
                                Toast.makeText(context, "Already signed in.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } else {
                            // User is toggling it OFF, so sign out
                            handleSignOut()
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
                        } else {
                            AppGlobals.updatePerformAutoSync(context, enabled)
                            // performAutoSync will be updated by its LaunchedEffect listening to AppGlobals
                            Toast.makeText(
                                context,
                                if (enabled) "Automatic sync enabled." else "Automatic sync disabled.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    currentSignedInAccount = currentSignedInAccount,
                    onSignOutClick = handleSignOut,
                    onManualBackupToDriveClick = {
                        scope.launch {
                            syncManager.manualBackupToDrive()
                        }
                    },
                    onRestoreFromFileClick = {
                        restoreFromFileLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*")
                        )
                    },
                    onExportToDeviceClick = {
                        val timestamp = AppGlobals.getUtcTimestampForFileName().replace(":", "")
                            .replace("-", "")
                        exportToDeviceLauncher.launch("mht-backup-$timestamp.zip")
                    },
                    onIconClick = displayInfoDialog
                )

                // ... (Rest of your Dialog content: Health Connect, AppInfoSection, Close button, etc.) ...
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Button(
                    onClick = { showHealthConnectDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Health Connect Demo")
                    Spacer(Modifier.weight(1f))
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

    // ... (UserProfileDialog, InformationDialog, HealthConnectDialog) ...
    if (showUserProfileDialog) {
        UserProfileDialog(
            currentUserProfile = currentUserProfile,
            onDismissRequest = { showUserProfileDialog = false },
            onUserProfileUpdate = { updatedProfile ->
                AppGlobals.updateUserProfile(context, updatedProfile)
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
            DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.getDefault())
        ) ?: "Not set"
        val heightFormatted = currentUserProfile.heightCm?.let { "%.0f cm".format(it) } ?: "Not set"
        val genderFormatted = currentUserProfile.gender.name.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

        InfoRow(
            icon = Icons.Filled.Person, label = "Gender", value = genderFormatted,
            onIconClick = {
                onIconClick(
                    "Gender Information",
                    "Your biological gender, used for more accurate estimations of Basal Metabolic Rate (BMR) and Total Daily Energy Expenditure (TDEE)."
                )
            }
        )
        InfoRow(
            icon = Icons.Filled.Cake, label = "Date of Birth", value = dobFormatted,
            onIconClick = {
                onIconClick(
                    "Date of Birth Information",
                    "Your date of birth, used to calculate your age for BMR and TDEE estimations."
                )
            }
        )
        InfoRow(
            icon = Icons.Filled.Height, label = "Height", value = heightFormatted,
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
                contentDescription = null,
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
            EnergyUnit.KCAL -> "kcal"; EnergyUnit.KJ -> "kJ"
        }

        SettingSwitchRow(
            icon = Icons.Filled.LocalFireDepartment,
            title = "Energy Unit: $formattedEnergyUnitName",
            infoContentDescription = "Energy Unit Information",
            checked = currentEnergyUnit == EnergyUnit.KJ,
            onCheckedChange = { isChecked ->
                val newUnit = if (isChecked) EnergyUnit.KJ else EnergyUnit.KCAL
                AppGlobals.updateEnergyUnit(context, newUnit)
                Toast.makeText(
                    context,
                    "Energy unit set to ${if (newUnit == EnergyUnit.KJ) "kJ" else "kcal"}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onIconClick = {
                onIconClick(
                    "Energy Unit",
                    "Choose your preferred unit for displaying energy values (kcal or kJ).\nThe switch toggles between kcal (off) and kJ (on)."
                )
            }
        )

        SettingSwitchRow(
            icon = Icons.Filled.Devices,
            title = "Device Role: ${
                currentDeviceRole.name.lowercase()
                    .replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
            }",
            infoContentDescription = "Device Role Information",
            checked = currentDeviceRole == DeviceRole.PRIMARY,
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
                    "Primary Device (Switch ON): Handles all data operations and syncs to Drive.\nSecondary Device (Switch OFF): Primarily for viewing data from a Primary device."
                )
            }
        )
    }
}

@Composable
private fun DataManagementSection(
    context: Context,
    syncManager: Sync,
    googleDriveSignInEnabled: Boolean,
    onGoogleDriveSignInEnabledChange: (Boolean) -> Unit,
    performAutoSync: Boolean,
    onPerformAutoSyncChange: (Boolean) -> Unit,
    currentSignedInAccount: GoogleSignInAccount?,
    onSignOutClick: () -> Unit, // Added for the sign out button
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

        SettingSwitchRow(
            icon = Icons.Filled.CloudSync,
            title = if (googleDriveSignInEnabled) "Drive Sync: ON" else "Drive Sync: OFF",
            infoContentDescription = "Google Drive Synchronization Information",
            checked = googleDriveSignInEnabled,
            onCheckedChange = onGoogleDriveSignInEnabledChange, // This will trigger sign-in or sign-out
            onIconClick = {
                val accountStatus = if (currentSignedInAccount != null) {
                    "Currently signed in as: ${currentSignedInAccount.email}.\n\n"
                } else {
                    "Not currently signed in.\n\n"
                }
                onIconClick(
                    "Google Drive Synchronization",
                    accountStatus +
                            "Enable to sign in with your Google Account for cloud backup and synchronization.\n\n" +
                            "Toggling this off will sign you out."
                )
            }
        )

        if (currentSignedInAccount != null) {
            InfoRow(
                icon = Icons.Filled.AccountCircle,
                label = "Signed in as",
                value = currentSignedInAccount.email ?: "Unknown Email"
            )
            Spacer(modifier = Modifier.height(4.dp))
            SettingsButton(
                icon = Icons.Filled.Logout,
                text = "Sign Out & Switch Account",
                onClick = onSignOutClick, // Use the passed lambda
                enabled = true,
                onInfoClick = {
                    onIconClick(
                        "Sign Out / Switch Account",
                        "Signs you out of the current Google Account (${currentSignedInAccount.email}).\n\n" +
                                "To sign in with a different account, toggle 'Drive Sync' back on after signing out. You will then be prompted to choose an account."
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        SettingSwitchRow(
            icon = Icons.Filled.Autorenew,
            title = "Automatic Sync (on app launch)",
            infoContentDescription = "Automatic Sync Information",
            checked = performAutoSync,
            onCheckedChange = onPerformAutoSyncChange,
            enabled = currentSignedInAccount != null,
            onIconClick = {
                onIconClick(
                    "Automatic Sync (on app launch)",
                    "If enabled and signed into Google Drive, the app will sync data on launch.\nRequires Google Drive sign-in."
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsButton(
            icon = Icons.Filled.UploadFile, text = "Upload Data to Drive",
            onClick = onManualBackupToDriveClick, enabled = currentSignedInAccount != null,
            onInfoClick = {
                onIconClick(
                    "Upload Data to Drive",
                    "Manually uploads current app data to Google Drive.\nRequires Google Drive sign-in."
                )
            }
        )

        SettingsButton(
            icon = Icons.Filled.Download, text = "Restore Data from File",
            onClick = onRestoreFromFileClick, enabled = true,
            onInfoClick = {
                onIconClick(
                    "Restore Data from File",
                    "Restores app data from a selected '.mht' backup file.\nWARNING: Overwrites current data."
                )
            }
        )

        SettingsButton(
            icon = Icons.Filled.SaveAlt, text = "Export Data to Device Storage",
            onClick = onExportToDeviceClick, enabled = true,
            onInfoClick = {
                onIconClick(
                    "Export Data to Device Storage",
                    "Saves a local ZIP backup of app data to your device."
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
        icon = Icons.Filled.Article,
        label = "Package Name",
        value = appPackageName,
        isMonospace = true,
        onIconClick = { onIconClick("Package Name", "The unique identifier for this application.") }
    )
    InfoRow(
        icon = Icons.Filled.Info, label = "Version", value = appVersion,
        onIconClick = { onIconClick("App Version", "Current version of the application.") }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Icon(
                Icons.Filled.Fingerprint, contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onIconClick(
                                "Device ID",
                                "Unique identifier for this app instance on this device. Helps distinguish data for cloud sync."
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
                    maxLines = 1
                )
            }
        }
        if (currentAppDeviceID != null) {
            IconButton(onClick = { showRegenerateIdConfirmDialog = true }) {
                Icon(
                    Icons.Filled.Cached,
                    contentDescription = "Regenerate Device ID",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    InfoRow(
        icon = Icons.Filled.Code, label = "View on GitHub", value = "", isLink = true,
        onClick = onGitHubClick,
        onIconClick = {
            onIconClick(
                "View on GitHub",
                "Opens the source code repository on GitHub."
            )
        }
    )

    if (showRegenerateIdConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateIdConfirmDialog = false },
            title = { Text("Confirm Regenerate ID") },
            text = { Text("Regenerating the Device ID might affect cloud sync identification. Are you sure?") },
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
    val rowModifier = when {
        onClick != null && isLink -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )

        onClick != null -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )

        else -> Modifier
    }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .then(
                    if (onIconClick != null && !isLink) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onIconClick
                    ) else Modifier
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

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    infoContentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onIconClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { if (!enabled) this.disabled() }
            .clickable(enabled = enabled, onClick = { if (enabled) onCheckedChange(!checked) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    enabled = true, // Info icon always clickable for context
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
            text = title, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            )
        )
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

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
            .padding(vertical = 4.dp),
        enabled = enabled
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text, modifier = Modifier.weight(1f))

        if (onInfoClick != null) {
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp),
                enabled = true // Info always available
            ) {
                Icon(
                    Icons.Filled.Info, contentDescription = "$text Information",
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary // Or current if button is disabled
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
            TextButton(onClick = onDismissRequest) { Text("OK") }
        }
    )
}
