package com.clipvault.manager.data.security

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricManager @Inject constructor() {

    /**
     * Tiered authenticator check. Some devices/OEMs refuse the combined
     * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` mask (e.g. returning
     * `BIOMETRIC_STATUS_UNKNOWN` or `BIOMETRIC_ERROR_UNSUPPORTED`) even when
     * either half of the union is available. Falling back to the weaker
     * "any of these alone" checks keeps the prompt usable on every device
     * that has any form of biometric or device credential configured.
     */
    fun canAuthenticate(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        val preferred = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return when (mgr.canAuthenticate(preferred)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> {
                mgr.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                    BiometricManager.BIOMETRIC_SUCCESS ||
                    mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                    BiometricManager.BIOMETRIC_SUCCESS ||
                    mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            }
        }
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancellation / lockout / no-hardware — surface the
                    // message so the caller can give feedback (previously a
                    // swallowed "stay locked" branch made the prompt look like
                    // it never fired).
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel?.invoke()
                    } else {
                        Log.w(TAG, "Biometric error $errorCode: $errString")
                        onFailure(errString.toString())
                    }
                }
                override fun onAuthenticationFailed() {
                    // Single bad attempt — the prompt stays up; do nothing.
                    // Without this override the prompt silently no-ops on
                    // a wrong fingerprint, which read as "biometric broken".
                }
            }
        )
        val preferred = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        // Same fallback ladder as canAuthenticate(): some devices reject the
        // combined authenticator set when constructing PromptInfo.
        val allowedAuthenticators = run {
            val mgr = BiometricManager.from(activity)
            when (mgr.canAuthenticate(preferred)) {
                BiometricManager.BIOMETRIC_SUCCESS -> preferred
                else -> when {
                    mgr.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                        BiometricManager.BIOMETRIC_SUCCESS ->
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                        BiometricManager.BIOMETRIC_SUCCESS ->
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                    else -> BiometricManager.Authenticators.BIOMETRIC_STRONG
                }
            }
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
        // androidx.biometric throws IllegalArgumentException("Negative text must
        // not be set if device credential authentication is allowed") whenever
        // the authenticator set includes the credential bit — the system then
        // renders its own PIN/pattern entry. Add our explicit Cancel label only
        // for pure-biometric sets (bitwise test, not equality — the combined
        // WEAK|CREDENTIAL mask also contains the credential bit).
        val includesCredential =
            (allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0
        if (!includesCredential) {
            builder.setNegativeButtonText("Cancel")
        }
        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            // Several OEM implementations (and androidx.biometric itself)
            // throw from authenticate() when the host activity isn't RESUMED
            // or when another authentication is still in flight. Convert that
            // into the normal failure path — an uncaught exception here took
            // the whole process down right after a save/unlock interaction.
            Log.w(TAG, "authenticate() failed to start", e)
            onFailure("Biometric prompt unavailable: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "BiometricManager"
    }
}