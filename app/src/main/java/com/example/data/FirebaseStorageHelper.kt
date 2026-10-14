package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

object FirebaseStorageHelper {
    private const val TAG = "FirebaseStorageHelper"

    /**
     * Uploads a local content:// or file:// URI to Firebase Storage
     * and returns the public https download URL.
     */
    suspend fun uploadOfferMedia(
        context: Context,
        mediaUriString: String,
        isVideo: Boolean
    ): String {
        // If it's already an HTTP URL or local bundled resource name, return as is
        if (mediaUriString.startsWith("http://") || mediaUriString.startsWith("https://") || !mediaUriString.contains("://")) {
            return mediaUriString
        }

        return try {
            val uri = Uri.parse(mediaUriString)
            val storage = FirebaseStorage.getInstance()
            val extension = if (isVideo) "mp4" else "jpg"
            val filename = "offers/${UUID.randomUUID()}.$extension"
            val storageRef = storage.reference.child(filename)

            val uploadTask = storageRef.putFile(uri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "Media successfully uploaded to Firebase Storage: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload media to Firebase Storage, falling back to original URI: ${e.message}", e)
            mediaUriString
        }
    }
}
