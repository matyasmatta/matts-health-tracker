package com.example.mattshealthtracker // Or your actual package name

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.google.api.services.drive.model.File as DriveFile // Assuming this import from GoogleDriveUtils context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Sync(private val context: Context) {

    // Assuming AppGlobals is accessible like this
    // If not, you might need to pass deviceId or AppGlobals instance
    private val currentAppDeviceId: String by lazy {
        AppGlobals.appDeviceID ?: "unknown_device_id" // Fallback if null
    }

    private val isoDateTimeFormat =
        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ROOT) // Use Locale.ROOT for consistency
    private val databaseSuffix = ".db"
    private val mhtExtension = ".mht"
    private val syncFilePrefix = "sync-"
    private val redundancyFileSuffix = "-redundancy_before_restore"

    // This should be your specific Google Drive Folder ID for sync files
    private val driveSyncFolderId = "1L3IjKPYOZXTBp5Y8FDxOPU3hmyFz7kSw" // From your class

    companion object {
        private const val TAG = "AppSync"
        private const val BUFFER_SIZE = 8192
        private const val TEMP_MHT_PREFIX = "manual_restore_temp_"
    }

    // --- Existing packageDatabases and restoreDatabasesAndCreateRedundancyPackage methods ---
    // (Keep them as they are)
    fun packageDatabases(
        deviceId: String,
        targetDirectory: File,
        specificSuffix: String = ""
    ): File? {
        val databasesDir = context.getDatabasePath("any_name_here").parentFile ?: run {
            Log.e(TAG, "Could not get databases directory.")
            return null
        }

        if (!databasesDir.exists() || !databasesDir.isDirectory) {
            Log.w(
                TAG,
                "Databases directory does not exist or is not a directory. Nothing to package."
            )
            return null
        }

        val dbFiles = databasesDir.listFiles { _, name -> name.endsWith(databaseSuffix) }
        if (dbFiles == null || dbFiles.isEmpty()) {
            Log.w(TAG, "No database files found to package.")
            return null
        }

        // Use AppGlobals for consistent timestamp formatting for filenames if desired,
        // or keep using isoDateTimeFormat if that's specifically for sync files.
        // val timestamp = AppGlobals.getUtcTimestampForFileName().replace(":", "").replace("-", "") // Example
        val timestamp = isoDateTimeFormat.format(Date())
        val mhtFileName = "$syncFilePrefix$timestamp-$deviceId$specificSuffix$mhtExtension"
        val mhtFile = File(targetDirectory, mhtFileName)

        Log.i(TAG, "Starting database packaging. Target file: ${mhtFile.absolutePath}")

        try {
            FileOutputStream(mhtFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    for (dbFile in dbFiles) {
                        if (!dbFile.exists() || !dbFile.isFile) {
                            Log.w(
                                TAG,
                                "Database file ${dbFile.name} does not exist or is not a file. Skipping."
                            )
                            continue
                        }
                        val entry = ZipEntry(dbFile.name)
                        zos.putNextEntry(entry)
                        FileInputStream(dbFile).use { fis ->
                            var len: Int
                            while (fis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                        }
                        zos.closeEntry()
                    }
                    zos.finish()
                    Log.i(TAG, "Database packaging successful: ${mhtFile.absolutePath}")
                    return mhtFile
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error packaging databases: ${e.message}", e)
            mhtFile.delete()
            return null
        }
    }

    fun restoreDatabasesAndCreateRedundancyPackage(
        mhtFileToRestore: File,
        deviceId: String,
        redundancyPackageTargetDirectory: File
    ): File? {
        if (!mhtFileToRestore.exists() || !mhtFileToRestore.isFile) {
            Log.e(
                TAG,
                "Provided .mht file for restore does not exist or is not a file: ${mhtFileToRestore.absolutePath}"
            )
            return null
        }
        Log.i(TAG, "Creating redundancy package before restore...")
        val redundancyPackageFile = packageDatabases(
            deviceId = deviceId,
            targetDirectory = redundancyPackageTargetDirectory,
            specificSuffix = redundancyFileSuffix
        )
        if (redundancyPackageFile == null) {
            Log.e(TAG, "Failed to create redundancy package. Aborting restore.")
            return null // Critical failure
        }
        Log.i(TAG, "Redundancy package created: ${redundancyPackageFile.absolutePath}")

        val databasesDir = context.getDatabasePath("any_name_here").parentFile ?: run {
            Log.e(TAG, "Could not get databases directory for restore.")
            return redundancyPackageFile // Return redundancy, but restore setup failed
        }
        if (!databasesDir.exists() && !databasesDir.mkdirs()) {
            Log.e(TAG, "Failed to create database directory: ${databasesDir.absolutePath}")
            return redundancyPackageFile // Return redundancy, but restore setup failed
        }

        Log.i(TAG, "Starting database restoration from: ${mhtFileToRestore.absolutePath}")
        val existingDbFiles = databasesDir.listFiles { _, name ->
            name.endsWith(databaseSuffix) || name.endsWith("$databaseSuffix-shm") || name.endsWith("$databaseSuffix-wal")
        }
        existingDbFiles?.forEach {
            Log.d(TAG, "Deleting existing file for restore: ${it.name}")
            if (!it.delete()) {
                Log.w(TAG, "Failed to delete existing database file: ${it.name}")
                // Consider this a significant issue that might affect the restore
            }
        }

        var restoreSuccessful = false
        try {
            FileInputStream(mhtFileToRestore).use { fis ->
                ZipInputStream(fis).use { zis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var zipEntry: ZipEntry? = zis.nextEntry
                    while (zipEntry != null) {
                        val entryName = zipEntry.name
                        if (!entryName.endsWith(databaseSuffix) || entryName.contains("..") || entryName.contains(
                                "/"
                            )
                        ) {
                            Log.w(TAG, "Skipping invalid or non-DB file in restore zip: $entryName")
                            zipEntry = zis.nextEntry
                            continue
                        }
                        val newFile = File(databasesDir, entryName)
                        Log.d(TAG, "Extracting ${zipEntry.name} to ${newFile.absolutePath}")
                        FileOutputStream(newFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                        zis.closeEntry()
                        zipEntry = zis.nextEntry
                    }
                    restoreSuccessful = true
                    Log.i(TAG, "Database restoration from ${mhtFileToRestore.name} completed.")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error restoring databases from ${mhtFileToRestore.name}: ${e.message}", e)
            // Restore failed. The redundancy package is still valuable.
        }

        if (!restoreSuccessful) {
            Log.e(TAG, "Database restoration process failed. Database state might be inconsistent.")
            // You might want to consider automatically trying to restore the redundancy package here,
            // but that adds complexity. For now, we rely on the user to manage this.
        }
        return redundancyPackageFile
    }


    // --- Automatic Sync Logic (syncOnAppLaunch, getLatestForeignSyncFile, etc.) ---
    // (Keep them as they are from your provided code)

    private data class ParsedSyncFile(
        val driveFileId: String,
        val fileName: String,
        val timestamp: Date,
        val deviceId: String
    )

    private fun parseSyncFileName(driveFile: DriveFile): ParsedSyncFile? {
        val fileName = driveFile.name ?: return null
        if (!fileName.startsWith(syncFilePrefix) || !fileName.endsWith(mhtExtension)) {
            return null
        }
        val nameWithoutPrefixSuffix =
            fileName.removePrefix(syncFilePrefix).removeSuffix(mhtExtension)
        val parts = nameWithoutPrefixSuffix.split('-', limit = 2)
        if (parts.size < 2) {
            Log.w(TAG, "Invalid sync filename format (not enough parts): $fileName")
            return null
        }
        val timestampStr = parts[0]
        val remoteDeviceId = parts[1]
        return try {
            val date = isoDateTimeFormat.parse(timestampStr)
            if (date != null) {
                ParsedSyncFile(driveFile.id, fileName, date, remoteDeviceId)
            } else {
                Log.w(TAG, "Could not parse timestamp from filename: $fileName")
                null
            }
        } catch (e: ParseException) {
            Log.w(TAG, "ParseException for filename: $fileName", e)
            null
        }
    }

    private suspend fun getLatestForeignSyncFile(): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Checking for latest foreign sync file in Drive folder: $driveSyncFolderId")
            val currentAccount = GoogleDriveUtils.getExistingAccount(context)
            if (currentAccount == null) {
                Log.w(TAG, "No Google account signed in. Cannot check for remote sync files.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Not signed in to Google Drive.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext null
            }

            val driveFiles = GoogleDriveUtils.listFilesFromDrive(
                context = context,
                account = currentAccount,
                folderIdToListFrom = driveSyncFolderId,
                queryParams = "name contains '$syncFilePrefix' and mimeType != 'application/vnd.google-apps.folder' and trashed = false"
            )

            if (driveFiles == null) {
                Log.e(TAG, "Failed to list files from Google Drive folder $driveSyncFolderId.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error listing Drive files.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext null
            }
            if (driveFiles.isEmpty()) {
                Log.i(TAG, "No sync files found in Drive folder $driveSyncFolderId.")
                return@withContext null
            }
            val parsedSyncFiles = driveFiles.mapNotNull { parseSyncFileName(it) }
            if (parsedSyncFiles.isEmpty()) {
                Log.i(TAG, "No validly named sync files found after parsing.")
                return@withContext null
            }
            val latestSyncFile =
                parsedSyncFiles.maxByOrNull { it.timestamp } ?: return@withContext null
            Log.i(
                TAG,
                "Latest remote sync file found: ${latestSyncFile.fileName} from device ${latestSyncFile.deviceId} with timestamp ${latestSyncFile.timestamp}"
            )

            if (latestSyncFile.deviceId != currentAppDeviceId) {
                Log.i(
                    TAG,
                    "Latest sync file (${latestSyncFile.fileName}) is from a FOREIGN device (${latestSyncFile.deviceId}). Current device is $currentAppDeviceId."
                )
                Pair(latestSyncFile.driveFileId, latestSyncFile.fileName)
            } else {
                Log.i(
                    TAG,
                    "Latest sync file (${latestSyncFile.fileName}) is from THIS device ($currentAppDeviceId). No action needed from remote."
                )
                null
            }
        }

    suspend fun syncOnAppLaunch() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting sync on app launch process...")
        if (!AppGlobals.performAutoSync) {
            Log.i(TAG, "Auto-sync is disabled in AppGlobals. Skipping.")
            return@withContext
        }
        val localDeviceId = currentAppDeviceId
        Log.d(TAG, "Current device ID: $localDeviceId")

        val foreignFileSyncInfo = getLatestForeignSyncFile()
        if (foreignFileSyncInfo == null) {
            Log.i(
                TAG,
                "No newer foreign sync file found or not applicable. Sync process complete without restore."
            )
            return@withContext
        }

        val (foreignFileId, foreignFileName) = foreignFileSyncInfo
        Log.i(
            TAG,
            "Found newer foreign file: ID '$foreignFileId', Name '$foreignFileName'. Proceeding with download and restore."
        )

        val tempDownloadDir = File(context.cacheDir, "sync_downloads").apply { mkdirs() }
        val localDestinationFile = File(tempDownloadDir, foreignFileName)

        val downloadSuccess = GoogleDriveUtils.downloadFileFromDrive(
            context = context,
            account = GoogleDriveUtils.getExistingAccount(context),
            fileId = foreignFileId,
            destinationFile = localDestinationFile
        )

        if (!downloadSuccess) {
            Log.e(
                TAG,
                "Failed to download foreign sync file: $foreignFileName (ID: $foreignFileId). Aborting restore."
            )
            localDestinationFile.delete()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Download of sync file failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext
        }
        Log.i(
            TAG,
            "Successfully downloaded foreign sync file to: ${localDestinationFile.absolutePath}"
        )

        val redundancyPackageTargetDir = context.cacheDir // Or another suitable directory
        val redundancyPackage = restoreDatabasesAndCreateRedundancyPackage(
            mhtFileToRestore = localDestinationFile,
            deviceId = localDeviceId,
            redundancyPackageTargetDirectory = redundancyPackageTargetDir
        )

        if (redundancyPackage != null) {
            Log.i(
                TAG,
                "Restore process initiated. Redundancy package created: ${redundancyPackage.absolutePath}"
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Data restored. Redundancy backup created.",
                    Toast.LENGTH_LONG
                ).show()
            }
            // Optionally, upload the redundancyPackage to Google Drive
            // GoogleDriveUtils.uploadFileToDrive(context, GoogleDriveUtils.getExistingAccount(context), redundancyPackage, driveSyncFolderId)
            // Log.i(TAG, "Uploaded redundancy package to Drive.")
        } else {
            Log.e(
                TAG,
                "Failed to create redundancy package during restore process OR restore input was invalid."
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Restore failed or redundancy package creation failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        localDestinationFile.delete()
        Log.d(TAG, "Cleaned up downloaded temporary file: ${localDestinationFile.name}")
        Log.i(TAG, "Sync on app launch process completed.")
    }


    // --- Manual Sync Triggers ---

    /**
     * Creates a temporary local copy of a file selected by the user via its URI.
     * This is necessary because SAF URIs don't always provide direct file paths.
     * @param uri The URI of the file selected by the user.
     * @return A File object pointing to the temporary copy in the app's cache, or null on error.
     */
    private suspend fun createTempFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            // Try to get original file name to use for the temp file
            var fileName = TEMP_MHT_PREFIX + System.currentTimeMillis()
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { fileName = it }
                    }
                }
            }
            // Ensure it has .mht extension for consistency, or handle it based on actual type
            if (!fileName.endsWith(mhtExtension)) {
                // fileName += mhtExtension // or reject if not mht
                Log.w(
                    TAG,
                    "Selected file '$fileName' does not have .mht extension. Proceeding, but ensure it's a valid sync file."
                )
            }


            val tempDir = File(context.cacheDir, "manual_restore_temp").apply { mkdirs() }
            val tempFile = File(tempDir, fileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    copyStream(inputStream, outputStream)
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for URI: $uri")
                return@withContext null
            }
            Log.i(TAG, "Created temporary copy for restore: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temporary file from URI: $uri", e)
            null
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
    }

    /**
     * Manually restores databases from a user-selected .mht file URI.
     * This involves copying the URI content to a temporary local file first.
     *
     * @param mhtFileUri The URI of the .mht file selected by the user.
     * @return True if the restore process (including redundancy package creation) was initiated successfully,
     *         false otherwise (e.g., file selection error, copy error, initial restore error).
     *         The actual success of database replacement needs to be inferred from logs or UI feedback.
     */
    suspend fun manualRestoreFromLocalMhtFile(mhtFileUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Starting manual restore from URI: $mhtFileUri")

            val tempMhtFile = createTempFileFromUri(mhtFileUri)
            if (tempMhtFile == null) {
                Log.e(TAG, "Failed to create temporary file from URI for restore. Aborting.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error processing selected file.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@withContext false
            }

            if (!tempMhtFile.name.endsWith(mhtExtension)) {
                Log.w(
                    TAG,
                    "The selected file '${tempMhtFile.name}' might not be a valid .mht sync file."
                )
                // Optionally add a Toast message here
                // withContext(Dispatchers.Main) { Toast.makeText(context, "Warning: Selected file may not be a sync file.", Toast.LENGTH_LONG).show()}
            }


            val redundancyPackageTargetDir = context.cacheDir // Or another suitable directory
            val redundancyPackage = restoreDatabasesAndCreateRedundancyPackage(
                mhtFileToRestore = tempMhtFile,
                deviceId = currentAppDeviceId, // Use current device ID for redundancy package name
                redundancyPackageTargetDirectory = redundancyPackageTargetDir
            )

            val success = redundancyPackage != null
            if (success) {
                Log.i(
                    TAG,
                    "Manual restore process initiated. Redundancy package: ${redundancyPackage?.absolutePath}"
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Data restore initiated. Check logs for details.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // You might want to upload the redundancyPackage here if that's a desired flow
                // GoogleDriveUtils.uploadFileToDrive(context, GoogleDriveUtils.getExistingAccount(context), redundancyPackage, driveSyncFolderId)
            } else {
                Log.e(
                    TAG,
                    "Manual restore failed or redundancy package creation failed for ${tempMhtFile.name}."
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Data restore failed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Clean up the temporary file
            tempMhtFile.delete()
            Log.d(TAG, "Cleaned up temporary restore file: ${tempMhtFile.name}")
            return@withContext success
        }

    /**
     * Manually packages current databases and uploads the resulting .mht file to Google Drive.
     *
     * @return True if the backup file was created and upload was successfully initiated, false otherwise.
     */
    suspend fun manualBackupToDrive(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting manual backup to Google Drive...")

        if (GoogleDriveUtils.getExistingAccount(context) == null) {
            Log.w(TAG, "Cannot perform manual backup: User not signed in to Google Drive.")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Please sign in to Google Drive first.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext false
        }

        val tempBackupDir = File(context.cacheDir, "manual_backups").apply { mkdirs() }
        val localMhtFile = packageDatabases(
            deviceId = currentAppDeviceId,
            targetDirectory = tempBackupDir,
            specificSuffix = "-manual_backup" // Add a suffix to distinguish
        )

        if (localMhtFile == null) {
            Log.e(TAG, "Failed to package databases for manual backup.")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Database backup creation failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext false
        }

        Log.i(TAG, "Databases packaged for manual backup: ${localMhtFile.absolutePath}")

        val uploadedFileId = GoogleDriveUtils.uploadFileToDrive(
            context = context,
            account = GoogleDriveUtils.getExistingAccount(context), // Re-fetch or pass
            fileToUpload = localMhtFile,
            targetFolderId = driveSyncFolderId
        )

        if (uploadedFileId != null) {
            Log.i(
                TAG,
                "Manual backup file uploaded to Drive successfully. File ID: $uploadedFileId"
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Backup uploaded to Google Drive.",
                    Toast.LENGTH_LONG
                ).show()
            }
            localMhtFile.delete() // Clean up local temporary backup file
            Log.d(TAG, "Cleaned up temporary manual backup file: ${localMhtFile.name}")
            return@withContext true
        } else {
            Log.e(TAG, "Failed to upload manual backup file to Drive.")
            // Don't delete localMhtFile here, user might want to retry or save it manually
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Google Drive upload failed. Backup saved locally.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext false
        }
    }
}
