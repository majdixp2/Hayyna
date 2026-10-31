package com.example.data

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleSignInHelper {
    private const val TAG = "GoogleSignInHelper"

    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser?> {
        val credentialManager = CredentialManager.create(activity)
        val serverClientId = activity.getString(R.string.default_web_client_id)

        val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                Result.success(authResult.user)
            } else {
                Result.failure(Exception("نوع بيانات الاعتماد غير مدعوم"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "تم إلغاء تسجيل الدخول بواسطة المستخدم: ${e.message}")
            Result.success(FirebaseAuth.getInstance().currentUser)
        } catch (e: NoCredentialException) {
            // ما فيه حساب Google على الجهاز
            Log.w(TAG, "no credential: ${e.message}")
            Result.failure(Exception("ما فيه حساب Google على جوالك. أضف حساب من إعدادات الجوال وجرّب مرة ثانية."))
        } catch (e: Exception) {
            Log.e(TAG, "فشل تسجيل الدخول: ${e.message}", e)
            Result.failure(e)
        }
    }
}
