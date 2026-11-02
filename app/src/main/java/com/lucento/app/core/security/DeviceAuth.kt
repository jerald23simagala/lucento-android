package com.lucento.app.core.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The phone's own unlock (fingerprint, face, PIN, pattern or password) through the system prompt. Nothing is stored. */
object DeviceAuth {

    enum class Outcome { SUCCESS, CANCELLED, UNAVAILABLE }

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True when the phone has a screen lock or an enrolled biometric, so the prompt can succeed. */
    fun isAvailable(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure == true) return true
        return BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onResult: (Outcome) -> Unit) {
        if (!isAvailable(activity)) {
            onResult(Outcome.UNAVAILABLE)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(Outcome.SUCCESS)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val gone = errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS
                    onResult(if (gone && !isAvailable(activity)) Outcome.UNAVAILABLE else Outcome.CANCELLED)
                }
                // onAuthenticationFailed (one unrecognised finger) keeps the prompt open; nothing to do.
            },
        )
        // With DEVICE_CREDENTIAL allowed the prompt offers the PIN/pattern itself, so no negative button is set.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
