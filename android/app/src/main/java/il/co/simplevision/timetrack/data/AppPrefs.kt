package il.co.simplevision.timetrack.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppPrefs(appContext: Context) {
    private val prefs = appContext.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val _hasSeenOnboarding = MutableStateFlow(prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false))
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding

    private val _hasSeenWalkthrough = MutableStateFlow(prefs.getBoolean(KEY_HAS_SEEN_WALKTHROUGH, false))
    val hasSeenWalkthrough: StateFlow<Boolean> = _hasSeenWalkthrough

    private val _debugRunOnboarding = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_RUN_ONBOARDING, false))
    val debugRunOnboarding: StateFlow<Boolean> = _debugRunOnboarding

    private val _debugRunWalkthrough = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_RUN_WALKTHROUGH, false))
    val debugRunWalkthrough: StateFlow<Boolean> = _debugRunWalkthrough

    fun setHasSeenOnboarding(value: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply()
        _hasSeenOnboarding.value = value
    }

    fun setHasSeenWalkthrough(value: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_WALKTHROUGH, value).apply()
        _hasSeenWalkthrough.value = value
    }

    fun setDebugRunOnboarding(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_RUN_ONBOARDING, value).apply()
        _debugRunOnboarding.value = value
    }

    fun setDebugRunWalkthrough(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_RUN_WALKTHROUGH, value).apply()
        _debugRunWalkthrough.value = value
    }

    fun resetIntroFlags() {
        setHasSeenOnboarding(false)
        setHasSeenWalkthrough(false)
    }

    companion object {
        private const val PREFS_FILE = "timetrack_prefs"

        private const val KEY_HAS_SEEN_ONBOARDING = "hasSeenOnboarding"
        private const val KEY_HAS_SEEN_WALKTHROUGH = "hasSeenWalkthrough"
        private const val KEY_DEBUG_RUN_ONBOARDING = "debugRunOnboarding"
        private const val KEY_DEBUG_RUN_WALKTHROUGH = "debugRunWalkthrough"
    }
}

