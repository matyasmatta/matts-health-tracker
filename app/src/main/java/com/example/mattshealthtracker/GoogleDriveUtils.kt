package com.example.mattshealthtracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File as JavaFile
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.text.SimpleDateFormat

object GoogleDriveUtils {

    private fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail() // Ensure the email is returned.
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    suspend fun signInToGoogleDrive(context: Context, launcher: ActivityResultLauncher<Intent>): GoogleSignInAccount? =
        suspendCancellableCoroutine { continuation ->
            val signInIntent = getGoogleSignInClient(context).signInIntent
            launcher.launch(signInIntent)

            // The launcher callback handles the result.
            val activityResultCallback = object : ActivityResultCallback<ActivityResult> {
                override fun onActivityResult(result: ActivityResult) {
                    if (result.resultCode == Activity.RESULT_OK) {
                        try {
                            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            val account = task.getResult(ApiException::class.java)
                            continuation.resume(account)
                        } catch (e: ApiException) {
                            Log.e("GoogleDriveSignIn", "Google Sign-in failed", e)
                            continuation.resumeWithException(e)
                        }
                    } else {
                        Log.d("GoogleDriveSignIn", "Sign-in failed or cancelled")
                        continuation.resumeWithException(Exception("Sign-in failed or cancelled"))
                    }
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

    // Make exportDataToCSVZip a suspend function so it can run its blocking calls on an IO dispatcher.
    suspend fun exportDataToCSVZip(context: Context, destinationUri: Uri, account: GoogleSignInAccount?) {
        Log.d("Export CSV", "exportDataToCSVZip called. Destination URI: $destinationUri")
        val filesDir = context.getExternalFilesDir(null)
        val filesToZip = filesDir?.listFiles()

        // Get the current UTC timestamp
        val deviceId = AppGlobals.appDeviceID
        val timestamp = AppGlobals.getUtcTimestamp()
        val zipFileName = "mht-backup-$timestamp-$deviceId.zip"

        if (filesToZip.isNullOrEmpty()) {
            Log.d("Export CSV", "No CSV files found in: ${filesDir?.absolutePath}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No data to export.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d("Export CSV", "Found ${filesToZip.size} files in: ${filesDir.absolutePath}")
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
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Data exported to CSV zip successfully!", Toast.LENGTH_LONG).show()
            }
            Log.d("Export CSV", "Zip file created: $zipFile")
            // Now upload the zipped file on a background thread.
            uploadDataToDrive(context, account, zipFile)
        } catch (e: Exception) {
            Log.e("Export CSV", "Error creating zip file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Make uploadDataToDrive a suspend function to run network calls off the main thread.
    suspend fun uploadDataToDrive(context: Context, account: GoogleSignInAccount?, zipFile: JavaFile) {
        if (account != null) {
            withContext(Dispatchers.IO) {
                try {
                    val driveService = getDriveService(context, account)
                    val metadata = DriveFile().apply {
                        name = zipFile.name
                        mimeType = "application/zip"
                    }
                    val folderId = "1fEoXBPvHT84LviBNJuhoNxI5Duk9V7sh" // Replace with actual folder ID or leave empty for root
                    if (folderId.isNotEmpty()) {
                        metadata.parents = listOf(folderId)
                    }
                    val fileContent = FileContent("application/zip", zipFile)
                    val file = driveService.files().create(metadata, fileContent)
                        .setFields("id")
                        .execute()
                    Log.d("GoogleDriveUpload", "Upload successful: ${file.id}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup completed to Google Drive", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("GoogleDriveUpload", "Upload error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                Log.w("GoogleDriveUpload", "Not signed in, cannot upload.")
                Toast.makeText(context, "Sign-in required to backup data to Drive.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Creates and returns a Drive service object using the signed-in account's OAuth token.
    fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val httpTransport: HttpTransport = NetHttpTransport()
        val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
        val email = "suplstory@gmail.com"
        // val email = account.email
        //    ?: throw IllegalArgumentException("GoogleSignInAccount email is null. Make sure email is requested during sign-in.")
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(Scopes.DRIVE_FILE)
        ).apply {
            selectedAccountName = email
        }
        return Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("YourAppName")
            .build()
    }
}
