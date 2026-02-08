package il.co.simplevision.timetrack.data

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppLock {
    var isUnlocked by mutableStateOf(false)
        private set

    private var isAuthenticating = false
    private var hasPendingRequest = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun requestUnlock(activity: FragmentActivity) {
        if (isUnlocked || isAuthenticating || hasPendingRequest) return
        hasPendingRequest = true
        mainHandler.postDelayed({
            hasPendingRequest = false
            unlock(activity)
        }, 200)
    }

    fun unlock(activity: FragmentActivity) {
        if (isUnlocked || isAuthenticating) return
        if (!activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            requestUnlock(activity)
            return
        }

        isAuthenticating = true

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuth = BiometricManager.from(activity).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // If biometrics/device credential are not available, do not block the user.
            isAuthenticating = false
            isUnlocked = true
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticating = false
                    isUnlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isAuthenticating = false
                    isUnlocked = false
                    // Mirror iOS behavior: retry unlock on system/app cancel.
                    if (errorCode == BiometricPrompt.ERROR_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        requestUnlock(activity)
                    }
                }

                override fun onAuthenticationFailed() {
                    // Keep prompt open; no state change needed.
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MyTimetrack")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    fun lock() {
        isUnlocked = false
    }
}

