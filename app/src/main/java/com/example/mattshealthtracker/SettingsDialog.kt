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
import androidx.compose.ui.semantics.disabled
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

    var googleDriveSyncEnabled by remember { mutableStateOf(currentSignedInAccount != null) }
    var showHealthConnectDialog by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    val signInLauncher = GoogleDriveUtils.rememberGoogleSignInLauncher { account ->
        onSignedInAccountChange(account)
        googleDriveSyncEnabled = account != null
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
            "Local export (SAF) needs a dedicated local zip function in Utils.",
            Toast.LENGTH_LONG
        ).show()
        Log.w("SettingsDialog", "SAF Export: Needs util for local zipping only for URI: $uri")
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
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                UserProfileSettingsSection(
                    currentUserProfile = currentUserProfile,
                    onEditProfileClick = { showUserProfileDialog = true },
                    onIconClick = { title, message ->
                        displayInfoDialog(
                            title,
                            message
                        )
                    } // Pass callback
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                PreferencesSection(
                    context = context,
                    currentEnergyUnit = currentEnergyUnit,
                    currentDeviceRole = currentDeviceRole,
                    onIconClick = { title, message -> displayInfoDialog(title, message) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

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
                            // Optionally, confirm sign out if they disable it while signed in.
                            // For now, just a toast.
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
                                GoogleDriveUtils.exportDataToCSVZip(context, Uri.EMPTY, it)
                            } ?: Toast.makeText(
                                context,
                                "Not signed into Google Drive.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onExportToDeviceClick = {
                        val timestamp = AppGlobals.getUtcTimestampForFileName()
                        exportToDeviceLauncher.launch("mht-backup-$timestamp.zip")
                    },
                    onIconClick = { title, message -> displayInfoDialog(title, message) }
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
                        Icons.Filled.Link, // Or HealthAndSafety
                        contentDescription = "Health Connect Icon",
                        modifier = Modifier
                            .size(ButtonDefaults.IconSize)
                            .clickable {
                                displayInfoDialog(
                                    "Health Connect Demo",
                                    "This section demonstrates integration with Health Connect by Google, allowing the app to read and write health and fitness data securely, with user consent."
                                )
                            }
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Health Connect Demo")
                }


                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                AppInfoSection(
                    context = context,
                    appPackageName = appPackageName,
                    appVersion = appVersion,
                    currentAppDeviceID = currentAppDeviceID,
                    onGitHubClick = { uriHandler.openUri(githubLink) },
                    onIconClick = { title, message -> displayInfoDialog(title, message) }
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
                showUserProfileDialog = false
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Show the generic information dialog when needed
    if (showInformationDialog && currentDialogInfo != null) {
        InformationDialog(
            title = currentDialogInfo!!.title,
            message = currentDialogInfo!!.message,
            onDismissRequest = { showInformationDialog = false }
        )
    }

    if (showHealthConnectDialog) {
        HealthConnectDialog(onDismissRequest = { showHealthConnectDialog = false })
        // You might want to move the displayInfoDialog call for Health Connect here
        // if clicking the button should also show the info dialog immediately.
    }
}

// --- Updated Section Composables ---

@Composable
private fun UserProfileSettingsSection(
    currentUserProfile: UserProfile,
    onEditProfileClick: () -> Unit,
    onIconClick: (title: String, message: String) -> Unit // Callback for icon clicks
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
                java.util.Locale.UK
            )
        ) ?: "Not set"
        val heightFormatted = currentUserProfile.heightCm?.let { "$it cm" } ?: "Not set"
        val genderFormatted = currentUserProfile.gender.name.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

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
    onIconClick: (title: String, message: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Preferences",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        // Helper to format the energy unit name correctly
        val formattedEnergyUnitName = when (currentEnergyUnit) {
            EnergyUnit.KCAL -> "kcal"
            EnergyUnit.KJ -> "kJ"
        }

        // Energy Unit Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.LocalFireDepartment, // Or Thermostat, Bolt etc.
                    contentDescription = "Energy Unit Information",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // No visual indication for the icon itself
                            onClick = {
                                onIconClick(
                                    "Energy Unit",
                                    "Choose your preferred unit for displaying energy values throughout the application.\n\n" +
                                            "kcal: Kilocalories (often referred to as Calories) are a common unit for food energy.\n\n" +
                                            "kJ: Kilojoules are the standard international (SI) unit of energy. 1 kcal is approximately 4.184 kJ."
                                )
                            }
                        ),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                // Updated Text display for Energy Unit with correct "kJ" formatting
                Text(
                    text = "Energy Unit: $formattedEnergyUnitName", // Displays "Energy Unit: kcal" or "Energy Unit: kJ"
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Switch(
                checked = currentEnergyUnit == EnergyUnit.KJ,
                onCheckedChange = { isChecked ->
                    val newUnit = if (isChecked) EnergyUnit.KJ else EnergyUnit.KCAL
                    AppGlobals.updateEnergyUnit(context, newUnit)
                    // Update Toast message for consistency
                    val toastUnitName = if (newUnit == EnergyUnit.KJ) "kJ" else "kcal"
                    Toast.makeText(context, "Energy unit set to $toastUnitName", Toast.LENGTH_SHORT)
                        .show()
                }
            )
        }
        // Device Role Row (remains the same)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.Devices,
                    contentDescription = "Device Role Information",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val primaryInfo =
                                    "Primary Device:\nHandles all data operations and can sync to Google Drive if enabled. Health Connect data from this device can be used as a source of truth."
                                val secondaryInfo =
                                    "Secondary Device:\nPrimarily for viewing data. It can receive data from a Primary device via Google Drive sync but typically does not initiate uploads or write to Health Connect."
                                onIconClick(
                                    "Device Role",
                                    if (currentDeviceRole == DeviceRole.PRIMARY) primaryInfo else secondaryInfo
                                )
                            }
                        ),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Device Role: ${
                        currentDeviceRole.name.lowercase().replaceFirstChar { it.titlecase() }
                    }",
                    style = MaterialTheme.typography.bodyLarge
                )
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
}


@Composable
private fun DataManagementSection(
    context: Context,
    googleDriveSyncEnabled: Boolean,
    onGoogleDriveSyncEnabledChange: (Boolean) -> Unit,
    currentSignedInAccount: GoogleSignInAccount?,
    onExportToDriveClick: () -> Unit,
    onExportToDeviceClick: () -> Unit,
    onIconClick: (title: String, message: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Data Management",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        // Backup to Drive Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.CloudSync,
                    contentDescription = "Backup data to Drive Information",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onIconClick(
                                    "Backup Data to Drive",
                                    "Enable this to automatically (or manually via export) back up your health data to your Google Drive account. " +
                                            "This allows for data recovery and synchronization across devices if this app is installed elsewhere with the same Google account."
                                )
                            }
                        ),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Text("Backup data to Drive", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = googleDriveSyncEnabled,
                onCheckedChange = onGoogleDriveSyncEnabledChange
            )
        }

        if (googleDriveSyncEnabled && currentSignedInAccount != null) {
            Button(
                onClick = onExportToDriveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Icon(
                    Icons.Filled.UploadFile,
                    contentDescription = "Export to Drive Now Information",
                    modifier = Modifier
                        .size(ButtonDefaults.IconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onIconClick(
                                    "Export Data to Drive Now",
                                    "Manually triggers an immediate backup of your current app data to a ZIP file in your Google Drive. " +
                                            "Useful for creating a specific restore point."
                                )
                            }
                        )
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Export Data to Drive Now")
            }
        }

        Button(
            onClick = onExportToDeviceClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Icon(
                Icons.Filled.SaveAlt,
                contentDescription = "Export to Device Storage Information",
                modifier = Modifier
                    .size(ButtonDefaults.IconSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onIconClick(
                                "Export Data to Device Storage",
                                "Manually creates a ZIP backup of your app data and saves it to your device's local storage (e.g., Downloads folder, specific location you choose via System File Picker). " +
                                        "This backup is not synced automatically."
                            )
                        }
                    )
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Export Data to Device Storage")
        }
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
        value = "com.example\n.mattshealthtracker",
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Filled.Fingerprint,
                contentDescription = "Device ID Information",
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onIconClick(
                                "Device ID",
                                "A unique identifier generated for this app instance on this device. " +
                                        "It can be used to distinguish data from this device if you use the app on multiple devices and sync via Google Drive. " +
                                        "Regenerating it creates a new ID; old backups might not be associated with the new ID unless handled by the backup system."
                            )
                        }
                    ),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Device ID", style = MaterialTheme.typography.bodyLarge)
                Text(
                    currentAppDeviceID ?: "Not set",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { showRegenerateIdConfirmDialog = true },
            enabled = currentAppDeviceID != null
        ) {
            Icon(
                Icons.Filled.Cached,
                contentDescription = "Regenerate Device ID", // This icon itself is not for info, but action
                tint = if (currentAppDeviceID != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }


    InfoRow(
        icon = Icons.Filled.Code,
        label = "View on GitHub",
        value = "", // Value not needed as label is the link text
        isLink = true,
        onClick = onGitHubClick, // This makes the whole row clickable
        onIconClick = { // Make the icon itself also show info, or make it redundant if row click is clear
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
    onClick: (() -> Unit)? = null, // For whole row click (like GitHub link)
    onIconClick: (() -> Unit)? = null // For icon-specific click to show info
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowModifier =
        if (onClick != null && !isLink) { // Make row clickable if onClick provided and not a link (link handles its own style)
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current, // Use default ripple for the row
                onClick = onClick
            )
        } else {
            Modifier
        }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = "$label Information", // More descriptive for accessibility
            modifier = Modifier
                .size(24.dp)
                .then(
                    if (onIconClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, // Separate for icon
                            indication = null, // Usually no separate indication for just an icon if row is clickable
                            onClick = onIconClick
                        )
                    } else Modifier
                ),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    // If it's a link, make the text area clickable to trigger onGitHubClick
                    if (isLink && onClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // Text click usually doesn't need its own ripple if icon/row has one
                        onClick = onClick
                    ) else Modifier
                )
        ) {
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

// InformationDialog Composable (defined earlier or in its own file)
// @Composable
// fun InformationDialog(title: String, message: String, onDismissRequest: () -> Unit) { ... }


// DeviceRoleInfoDialog is now effectively replaced by the generic InformationDialog
// triggered by the clickable icon in PreferencesSection. So, this can be removed.
// @Composable
// private fun DeviceRoleInfoDialog(currentDeviceRole: DeviceRole, onDismissRequest: () -> Unit) { ... }
