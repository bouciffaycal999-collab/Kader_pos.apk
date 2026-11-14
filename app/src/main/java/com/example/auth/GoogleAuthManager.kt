package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthAttemptResult {
    data object Success : AuthAttemptResult
    data object Cancelled : AuthAttemptResult
    data object NoSavedCredential : AuthAttemptResult
    data class Error(val message: String) : AuthAttemptResult
}

object GoogleAuthManager {
    private const val TAG = "Auth"

    fun isFirebaseAvailable(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context.applicationContext).isNotEmpty() }.getOrDefault(false)

    private fun safeAuth(context: Context, explicitAuth: FirebaseAuth?): FirebaseAuth? {
        if (explicitAuth != null) return explicitAuth
        if (!isFirebaseAvailable(context)) return null
        return runCatching { Firebase.auth }.getOrNull()
    }

    private fun resolveServerClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return null
        return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun authStateFlow(auth: FirebaseAuth?): Flow<FirebaseUser?> = callbackFlow {
        if (auth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun attemptSilentSignIn(
        context: Context,
        credentialManager: CredentialManager = CredentialManager.create(context),
        auth: FirebaseAuth? = null
    ): AuthAttemptResult {
        val firebaseAuth = safeAuth(context, auth) ?: return AuthAttemptResult.NoSavedCredential
        if (firebaseAuth.currentUser != null) return AuthAttemptResult.Success
        val serverClientId = resolveServerClientId(context) ?: return AuthAttemptResult.NoSavedCredential
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context = context, request = request)
            signInWithGoogleCredential(result.credential, firebaseAuth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            AuthAttemptResult.NoSavedCredential
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled: ${e.message}", e)
            AuthAttemptResult.Cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In skipped: ${e.message}", e)
            AuthAttemptResult.NoSavedCredential
        }
    }

    suspend fun signInInteractive(
        context: Context,
        credentialManager: CredentialManager = CredentialManager.create(context),
        auth: FirebaseAuth? = null
    ): AuthAttemptResult {
        val firebaseAuth = safeAuth(context, auth)
            ?: return AuthAttemptResult.Error("Cloud Google non configuré dans cette version APK hors-ligne.")
        val serverClientId = resolveServerClientId(context)
            ?: return AuthAttemptResult.Error("Cloud Google non configuré dans cette version APK hors-ligne.")
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context = context, request = request)
            signInWithGoogleCredential(result.credential, firebaseAuth)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available for sign-in: ${e.message}", e)
            AuthAttemptResult.NoSavedCredential
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            AuthAttemptResult.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            AuthAttemptResult.Error(e.localizedMessage ?: "Échec de la connexion Google.")
        }
    }

    private suspend fun signInWithGoogleCredential(
        credential: Credential,
        auth: FirebaseAuth
    ): AuthAttemptResult {
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return AuthAttemptResult.Error("Type d'identifiant Google non reconnu.")
        }
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        auth.signInWithCredential(firebaseCredential).await()
        return AuthAttemptResult.Success
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        auth: FirebaseAuth?
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    auth?.signOut()
                }
            }
        }
    }
}
