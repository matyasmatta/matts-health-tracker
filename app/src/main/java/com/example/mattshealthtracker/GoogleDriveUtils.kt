package com.example.mattshealthtracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile // Alias to avoid collision
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File as JavaFile // Alias for java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object GoogleDriveUtils {

    private const val DEFAULT_APP_FOLDER_ID =
        "1fEoXBPvHT84LviBNJuhoNxI5Duk9V7sh" // Replace with your actual folder ID
    private const val DOWNLOAD_BUFFER_SIZE = 8192 // 8KB buffer for downloading

    private fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(Scopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    suspend fun signInToGoogleDrive(context: Context, launcher: ActivityResultLauncher<Intent>): GoogleSignInAccount? =
        suspendCancellableCoroutine { continuation ->
            val signInIntent = getGoogleSignInClient(context).signInIntent
            launcher.launch(signInIntent)
            val activityResultCallback = object : ActivityResultCallback<ActivityResult> {
                override fun onActivityResult(result: ActivityResult) {
                    if (result.resultCode == Activity.RESULT_OK) {
                        try {
                            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            val account = task.getResult(ApiException::class.java)
                            continuation.resume(account)
                        } catch (e: ApiException) {
                            Log.e("GoogleDriveSignIn", "Google Sign-in failed (within callback)", e)
                            continuation.resumeWithException(e)
                        }
                    } else {
                        Log.d("GoogleDriveSignIn", "Sign-in failed or cancelled (within callback)")
                        continuation.resumeWithException(Exception("Sign-in failed or cancelled (within callback)"))
                    }
                }
            }
        }

    // Inside @Composable fun SettingsDialog(...)

    // Add a function to handle sign out
    fun signOut(
        context: Context,
        onSignOutComplete: (Boolean) -> Unit
    ) { // Changed to Boolean for clarity
        getGoogleSignInClient(context).signOut()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("GoogleDriveUtils", "Google Sign-out successful.")
                    onSignOutComplete(true)
                } else {
                    Log.e("GoogleDriveUtils", "Google Sign-out failed.", task.exception)
                    onSignOutComplete(false)
                }
            }
    }


    @Composable
    fun rememberGoogleSignInLauncher(onSignInComplete: (GoogleSignInAccount?) -> Unit): ActivityResultLauncher<Intent> {
        val context = LocalContext.current
        return rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    val account = task.getResult(ApiException::class.java)
                    onSignInComplete(account)
                    Log.d("GoogleDriveSignIn", "Google Sign-in successful: ${account?.email}")
                } catch (e: ApiException) {
                    Log.e("GoogleDriveSignIn", "Google Sign-in failed", e)
                    onSignInComplete(null)
                }
            } else {
                Log.d("GoogleDriveSignIn", "Google Sign-in cancelled or failed, resultCode: ${result.resultCode}")
                onSignInComplete(null)
            }
        }
    }

    fun getExistingAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun exportDataToCSVZip(context: Context, destinationUri: Uri, account: GoogleSignInAccount?) {
        Log.d("Export CSV", "exportDataToCSVZip called. Destination URI: $destinationUri")
        val filesDir = context.getExternalFilesDir(null)
        val filesToZip = filesDir?.listFiles()

        val deviceId = AppGlobals.appDeviceID ?: "unknown_device"
        val timestamp = AppGlobals.getUtcTimestamp()
        val zipFileName = "mht-backup-$timestamp-$deviceId.zip"

        if (filesToZip.isNullOrEmpty()) {
            Log.d("Export CSV", "No CSV files found in: ${filesDir?.absolutePath}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No data to export.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d("Export CSV", "Found ${filesToZip.size} files in: ${filesDir?.absolutePath}")
        val zipFile = JavaFile(context.cacheDir, zipFileName)
        try {
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    filesToZip.forEach { file ->
                        Log.d("Export CSV", "Processing file: ${file.name}")
                        if (file.isFile && file.name.endsWith(".csv")) {
                            val entry = ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            try {
                                file.inputStream().use { input ->
                                    input.copyTo(zos)
                                }
                                zos.closeEntry()
                                Log.d("Export CSV", "Zipped: ${file.name}")
                            } catch (e: Exception) {
                                Log.e("Export CSV", "Error zipping ${file.name}", e)
                            }
                        } else {
                            Log.d("Export CSV", "Skipping non-CSV file: ${file.name}")
                        }
                    }
                }
            }
            /*withContext(Dispatchers.Main) {
                Toast.makeText(context, "Data exported to CSV zip successfully!", Toast.LENGTH_LONG).show()
            }*/
            Log.d("Export CSV", "Zip file created: $zipFile")
            uploadDataToDrive(context, account, zipFile)
        } catch (e: Exception) {
            Log.e("Export CSV", "Error creating zip file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun uploadDataToDrive(context: Context, account: GoogleSignInAccount?, zipFile: JavaFile) {
        if (account == null) {
            withContext(Dispatchers.Main) {
                Log.w("GoogleDriveUpload", "Not signed in, cannot upload (uploadDataToDrive).")
                Toast.makeText(
                    context,
                    "Sign-in required to backup data to Drive.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(context, account)
                val uploadedFileId = uploadFile(
                    driveService = driveService,
                    fileToUpload = zipFile,
                    folderId = DEFAULT_APP_FOLDER_ID
                )

                if (uploadedFileId != null) {
                    Log.d(
                        "GoogleDriveUpload",
                        "Upload successful (via uploadFile): $uploadedFileId"
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Full cloud backup completed (Device ID: ${AppGlobals.appDeviceID})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e("GoogleDriveUpload", "Upload failed (via uploadFile)")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload failed.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleDriveUpload", "Upload error (uploadDataToDrive)", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Upload failed: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    suspend fun uploadFileToDrive(
        context: Context,
        account: GoogleSignInAccount?,
        fileToUpload: JavaFile,
        targetFolderId: String? = DEFAULT_APP_FOLDER_ID
    ): String? {
        val currentAccount = account ?: getExistingAccount(context)
        if (currentAccount == null) {
            Log.w("GoogleDriveUtils", "Not signed in, cannot upload file: ${fileToUpload.name}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Sign-in required to upload to Drive.", Toast.LENGTH_SHORT)
                    .show()
            }
            return null
        }
        if (!fileToUpload.exists() || !fileToUpload.isFile) {
            Log.e(
                "GoogleDriveUtils",
                "File to upload does not exist or is not a file: ${fileToUpload.path}"
            )
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(context, currentAccount)
                uploadFile(driveService, fileToUpload, targetFolderId)
            } catch (e: IOException) {
                Log.e("GoogleDriveUtils", "IOException during file upload: ${fileToUpload.name}", e)
                null
            } catch (e: Exception) {
                Log.e(
                    "GoogleDriveUtils",
                    "Generic exception during file upload: ${fileToUpload.name}",
                    e
                )
                null
            }
        }
    }

    private suspend fun uploadFile(
        driveService: Drive,
        fileToUpload: JavaFile,
        folderId: String?
    ): String? {
        return try {
            val mimeType = getMimeType(fileToUpload) ?: "application/octet-stream"
            val metadata = DriveFile().apply {
                name = fileToUpload.name
                this.mimeType = mimeType
                if (!folderId.isNullOrEmpty()) {
                    parents = listOf(folderId)
                }
            }
            val fileContent = FileContent(mimeType, fileToUpload)
            Log.d(
                "GoogleDriveUtils",
                "Uploading '${fileToUpload.name}' to folder '$folderId' with MIME '$mimeType'"
            )
            val uploadedDriveFile = driveService.files().create(metadata, fileContent)
                .setFields("id, name, webViewLink")
                .execute()
            Log.i(
                "GoogleDriveUtils",
                "File uploaded successfully: ${uploadedDriveFile.name}, ID: ${uploadedDriveFile.id}, Link: ${uploadedDriveFile.webViewLink}"
            )
            uploadedDriveFile.id
        } catch (e: Exception) {
            Log.e("GoogleDriveUtils", "Failed to upload file '${fileToUpload.name}'", e)
            null
        }
    }

    suspend fun listFilesFromDrive(
        context: Context,
        account: GoogleSignInAccount?,
        folderIdToListFrom: String? = DEFAULT_APP_FOLDER_ID,
        pageSize: Int = 20,
        queryParams: String? = null
    ): List<DriveFile>? {
        val currentAccount = account ?: getExistingAccount(context)
        if (currentAccount == null) {
            Log.w("GoogleDriveUtils", "Not signed in, cannot list files.")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Sign-in required to list files from Drive.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(context, currentAccount)
                val files = mutableListOf<DriveFile>()
                var pageToken: String? = null
                var queryString = ""

                if (!folderIdToListFrom.isNullOrEmpty()) {
                    queryString = "'$folderIdToListFrom' in parents"
                }
                if (!queryParams.isNullOrBlank()) {
                    queryString =
                        if (queryString.isNotEmpty()) "$queryString and $queryParams" else queryParams
                }
                queryString =
                    if (queryString.isNotEmpty()) "$queryString and trashed = false" else "trashed = false"

                Log.d("GoogleDriveUtils", "Listing files with query: \"$queryString\"")
                do {
                    val result: FileList = driveService.files().list()
                        .setQ(queryString.ifEmpty { null })
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, mimeType, createdTime, modifiedTime, size, parents, webViewLink, iconLink, shortcutDetails)")
                        .setPageSize(pageSize)
                        .setPageToken(pageToken)
                        .execute()
                    files.addAll(result.files)
                    pageToken = result.nextPageToken
                } while (pageToken != null)
                Log.i(
                    "GoogleDriveUtils",
                    "Found ${files.size} files in folder '$folderIdToListFrom'."
                )
                files
            } catch (e: IOException) {
                Log.e(
                    "GoogleDriveUtils",
                    "IOException during listing files from folder '$folderIdToListFrom'",
                    e
                )
                null
            } catch (e: Exception) {
                Log.e(
                    "GoogleDriveUtils",
                    "Generic exception during listing files from folder '$folderIdToListFrom'",
                    e
                )
                null
            }
        }
    }

    /**
     * Downloads a file from Google Drive to a local destination.
     *
     * @param context The application context.
     * @param account The signed-in GoogleSignInAccount. If null, attempts to get an existing one.
     * @param fileId The ID of the file to download from Google Drive.
     * @param destinationFile The local JavaFile where the downloaded content will be saved.
     *                        The parent directory of this file must exist.
     * @return True if the download was successful, false otherwise.
     */
    suspend fun downloadFileFromDrive(
        context: Context,
        account: GoogleSignInAccount?,
        fileId: String,
        destinationFile: JavaFile
    ): Boolean {
        val currentAccount = account ?: getExistingAccount(context)
        if (currentAccount == null) {
            Log.w("GoogleDriveUtils", "Not signed in, cannot download file ID: $fileId")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Sign-in required to download from Drive.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }

        if (fileId.isBlank()) {
            Log.e("GoogleDriveUtils", "File ID for download cannot be blank.")
            return false
        }

        // Ensure destination directory exists, create if not (though usually the caller should handle this)
        destinationFile.parentFile?.mkdirs()

        return withContext(Dispatchers.IO) {
            try {
                Log.d(
                    "GoogleDriveUtils",
                    "Attempting to download Drive file ID '$fileId' to '${destinationFile.absolutePath}'"
                )
                val driveService = getDriveService(context, currentAccount)
                val outputStream: OutputStream = FileOutputStream(destinationFile)

                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                // The executeMediaAndDownloadTo method handles closing the outputStream.

                Log.i(
                    "GoogleDriveUtils",
                    "File ID '$fileId' downloaded successfully to '${destinationFile.absolutePath}'"
                )
                true
            } catch (e: IOException) {
                Log.e("GoogleDriveUtils", "IOException during download of file ID '$fileId'", e)
                destinationFile.delete() // Clean up partially downloaded file
                false
            } catch (e: Exception) {
                Log.e(
                    "GoogleDriveUtils",
                    "Generic exception during download of file ID '$fileId'",
                    e
                )
                destinationFile.delete() // Clean up partially downloaded file
                false
            }
        }
    }

    private fun getMimeType(file: JavaFile): String? {
        val extension = file.extension
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            null
        }
    }

    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val httpTransport: HttpTransport = NetHttpTransport()
        val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
        val email = account.email
            ?: throw IllegalArgumentException("GoogleSignInAccount email is null. Ensure email is requested during sign-in and account is valid.")
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(Scopes.DRIVE_FILE)
        ).apply {
            selectedAccountName = email
        }
        return Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

}
