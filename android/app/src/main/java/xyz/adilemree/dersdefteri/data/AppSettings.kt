package xyz.adilemree.dersdefteri.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/// Kullanıcı tercihleri. Anahtar adları iOS'taki @AppStorage adlarıyla aynıdır.
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    inner class Pref<T>(private val key: String, private val default: T) {
        private val state = MutableStateFlow(read())

        val flow: StateFlow<T> = state.asStateFlow()

        var value: T
            get() = state.value
            set(newValue) {
                prefs.edit().apply {
                    @Suppress("UNCHECKED_CAST")
                    when (newValue) {
                        is Boolean -> putBoolean(key, newValue)
                        is Int -> putInt(key, newValue)
                        is Long -> putLong(key, newValue)
                        is String -> putString(key, newValue)
                        else -> error("Desteklenmeyen tür")
                    }
                }.apply()
                state.value = newValue
            }

        @Suppress("UNCHECKED_CAST")
        private fun read(): T = when (default) {
            is Boolean -> prefs.getBoolean(key, default) as T
            is Int -> prefs.getInt(key, default) as T
            is Long -> prefs.getLong(key, default) as T
            is String -> (prefs.getString(key, default) ?: default) as T
            else -> default
        }
    }

    val hasCompletedOnboarding = Pref("hasCompletedOnboarding", false)
    val teacherName = Pref("teacherName", "")

    val remindersEnabled = Pref("remindersEnabled", true)
    val reminderMinutes = Pref("reminderMinutes", 60)
    val lessonDonePromptEnabled = Pref("lessonDonePromptEnabled", true)
    val homeworkRemindersEnabled = Pref("homeworkRemindersEnabled", true)
    val homeworkReminderHour = Pref("homeworkReminderHour", 9)
    val dailyDigestEnabled = Pref("dailyDigestEnabled", false)
    val dailyDigestHour = Pref("dailyDigestHour", 8)

    /// Bildirim izni bir kez soruldu mu (Android 13+)
    val askedNotificationPermission = Pref("askedNotificationPermission", false)

    /// "Ali öğretmenim" ya da boşsa "Öğretmenim"
    fun teacherDisplayName(name: String = teacherName.value): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Öğretmenim"
        return "${trimmed.capitalizedTr()} öğretmenim"
    }
}
