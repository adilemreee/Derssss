package xyz.adilemree.dersdefteri.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.adilemree.dersdefteri.MainActivity
import xyz.adilemree.dersdefteri.R
import xyz.adilemree.dersdefteri.app
import xyz.adilemree.dersdefteri.data.AppSettings
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.NotebookRepository
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.atTime
import xyz.adilemree.dersdefteri.data.startOfDay
import xyz.adilemree.dersdefteri.widget.TodayWidget

/// Yerel bildirimleri planlar. Her veri kaydından sonra baştan kurulur;
/// böylece eklenen, taşınan, iptal edilen ya da silinen dersler güncel kalır.
class AppNotifications(
    private val context: Context,
    private val repository: NotebookRepository,
    private val settings: AppSettings,
    private val isPro: () -> Boolean,
    private val scope: CoroutineScope,
) {
    companion object {
        const val CHANNEL_LESSONS = "lessons"
        const val CHANNEL_DONE = "lesson_done"
        const val CHANNEL_HOMEWORK = "homework"
        const val CHANNEL_DIGEST = "digest"

        const val ACTION_SHOW = "xyz.adilemree.dersdefteri.SHOW_NOTIFICATION"
        const val ACTION_MARK_DONE = "xyz.adilemree.dersdefteri.MARK_DONE"
        const val ACTION_REFRESH_WIDGET = "xyz.adilemree.dersdefteri.REFRESH_WIDGET"

        const val EXTRA_ID = "id"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_LESSON = "lessonID"

        private const val BASE_CODE = 4000
        private const val WIDGET_CODE = 3999
    }

    private data class Planned(val time: Long, val channel: String, val title: String, val body: String, val lessonId: String? = null)

    private val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private var pending: Job? = null

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_LESSONS, "Ders hatırlatmaları", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Ders saatinden önce gelen hatırlatma" },
                NotificationChannel(CHANNEL_DONE, "Ders bitti mi?", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Ders bitince İşlendi olarak işaretlemek için" },
                NotificationChannel(CHANNEL_HOMEWORK, "Ödev hatırlatmaları", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_DIGEST, "Günlük program özeti", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /// Art arda kayıtlarda bildirimleri tek seferde yeniden kurmak için kısa bir bekleme
    fun resyncSoon() {
        pending?.cancel()
        pending = scope.launch {
            delay(1000)
            resync()
        }
    }

    suspend fun resync() {
        val notebook = repository.load()
        val now = System.currentTimeMillis()
        val planned = buildList {
            // Ders hatırlatması kapalıyken ödev ve özet bildirimleri yine kurulur.
            if (settings.remindersEnabled.value) addAll(lessonReminders(notebook, now))
            if (settings.lessonDonePromptEnabled.value) addAll(donePrompts(notebook, now))
            if (settings.homeworkRemindersEnabled.value) addAll(homeworkReminders(notebook, now))
            if (settings.dailyDigestEnabled.value && isPro()) addAll(dailyDigest(notebook, now))
        }

        // Öncekiler silinip yenileri kurulur.
        prefs.getStringSet("codes", emptySet()).orEmpty().forEach { code ->
            pendingIntent(code.toInt(), Intent(context, NotificationReceiver::class.java).setAction(ACTION_SHOW), PendingIntent.FLAG_NO_CREATE)
                ?.let { alarms.cancel(it); it.cancel() }
        }
        val codes = mutableSetOf<String>()
        planned.forEachIndexed { index, item ->
            val code = BASE_CODE + index
            val intent = Intent(context, NotificationReceiver::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_ID, code)
                .putExtra(EXTRA_CHANNEL, item.channel)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_BODY, item.body)
                .putExtra(EXTRA_LESSON, item.lessonId)
            val pi = pendingIntent(code, intent, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEachIndexed
            schedule(item.time, pi)
            codes += code.toString()
        }
        prefs.edit().putStringSet("codes", codes).apply()

        scheduleWidgetRefresh(notebook, now)
        TodayWidget.refresh(context)
    }

    /// Tam zamanlı alarm izni varsa bildirim dakikasında gelir. İzin yoksa
    /// (Android 14+ varsayılanı) Android'in esnek alarmı bir saate kadar
    /// kayabildiği için en fazla 10 dakikalık bir aralık istenir.
    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun schedule(time: Long, pi: PendingIntent) {
        if (canScheduleExact) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                return
            } catch (_: SecurityException) {
            }
        }
        alarms.setWindow(AlarmManager.RTC_WAKEUP, time, 10 * 60_000L, pi)
    }

    private fun pendingIntent(code: Int, intent: Intent, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, code, intent, flag or PendingIntent.FLAG_IMMUTABLE)

    private fun lessonLine(notebook: Notebook, lesson: Lesson): String {
        val student = notebook.studentOf(lesson)
        val name = student?.name ?: "Öğrenci"
        val subject = student?.subject.orEmpty()
        val time = Fmt.time(lesson.date)
        return if (subject.isEmpty()) "$time — $name" else "$time — $name • $subject"
    }

    private fun lessonReminders(notebook: Notebook, now: Long): List<Planned> {
        val lead = settings.reminderMinutes.value * 60_000L
        return notebook.lessons.filter { it.isPlanned && it.date > now }.take(20).mapNotNull { lesson ->
            val fire = lesson.date - lead
            if (fire <= now) null else Planned(fire, CHANNEL_LESSONS, "Yaklaşan ders 📚", lessonLine(notebook, lesson))
        }
    }

    /// Ders bitince "İşlendi mi?" diye sorar. İşlendi denmeyen ders bakiyeye yansımaz.
    private fun donePrompts(notebook: Notebook, now: Long): List<Planned> {
        // Şu an süren ders de dahil: bitişi henüz gelmedi.
        val earliest = now - 6 * 3_600_000L
        return notebook.lessons.filter { it.isPlanned && it.date > earliest }.take(16)
            .filter { it.endDate > now }.take(10)
            .map { lesson ->
                // Ders bitiminden birkaç dakika sonra; öğrenci kapıdan çıkarken değil.
                Planned(
                    lesson.endDate + 5 * 60_000L, CHANNEL_DONE, "Ders bitti mi? ✅",
                    "${lessonLine(notebook, lesson)} — yapıldıysa İşlendi olarak işaretle.", lesson.id,
                )
            }
    }

    private fun homeworkReminders(notebook: Notebook, now: Long): List<Planned> {
        val hour = settings.homeworkReminderHour.value
        return notebook.homeworks.filter { !it.isDone }.take(8).flatMap { hw ->
            val name = notebook.student(hw.studentId)?.name ?: "Öğrenci"
            val dueMorning = atTime(hw.dueDate, hour, 0)
            listOf(
                Triple(addDays(dueMorning, -1), "Ödev teslimi yaklaşıyor", "Yarın teslim"),
                Triple(dueMorning, "Bugün ödev teslim günü", "Bugün teslim"),
                Triple(addDays(dueMorning, 1), "Geciken ödev var", "Gecikti"),
            ).filter { it.first > now }.map { (time, title, prefix) ->
                Planned(time, CHANNEL_HOMEWORK, title, "$prefix — $name • ${hw.title}")
            }
        }
    }

    /// Günün derslerini sabah tek bildirimde özetler (Pro). Bildirim metni o
    /// günün ders listesini içerdiği için önümüzdeki hafta için ayrı ayrı kurulur.
    private fun dailyDigest(notebook: Notebook, now: Long): List<Planned> {
        val hour = settings.dailyDigestHour.value
        val today = startOfDay(now)
        val horizon = addDays(today, 8)
        val byDay = notebook.lessons.filter { it.isPlanned && it.date > now && it.date < horizon }.groupBy { startOfDay(it.date) }
        return (0 until 7).mapNotNull { offset ->
            val day = addDays(today, offset)
            val lessons = byDay[day].orEmpty()
            if (lessons.isEmpty()) return@mapNotNull null
            val fire = atTime(day, hour, 0)
            if (fire <= now) return@mapNotNull null
            var body = lessons.take(4).joinToString(" • ") { "${Fmt.time(it.date)} ${notebook.studentOf(it)?.name ?: "Öğrenci"}" }
            if (lessons.size > 4) body += " +${lessons.size - 4}"
            val title = if (lessons.size == 1) "Bugün 1 dersin var 📖" else "Bugün ${lessons.size} dersin var 📖"
            Planned(fire, CHANNEL_DIGEST, title, body)
        }
    }

    /// Widget ders başlarken ve biterken (tik düğmesi o an çıkar) tazelenir.
    private fun scheduleWidgetRefresh(notebook: Notebook, now: Long) {
        val next = notebook.lessons.asSequence()
            .filter { !it.isCancelled }
            .flatMap { sequenceOf(it.date, it.endDate) }
            .filter { it > now }
            .minOrNull()
        val midnight = addDays(startOfDay(now), 1) + 60_000L
        val time = minOf(next ?: midnight, midnight)
        val intent = Intent(context, NotificationReceiver::class.java).setAction(ACTION_REFRESH_WIDGET)
        pendingIntent(WIDGET_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)?.let {
            alarms.setWindow(AlarmManager.RTC, time + 1000, 5 * 60_000L, it)
        }
    }
}

/// Planlanan bildirimi gösterir, widget'ı tazeler ve bildirimdeki "İşlendi"
/// düğmesini işler (uygulama kapalıyken de).
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppNotifications.ACTION_SHOW -> show(context, intent)
            AppNotifications.ACTION_MARK_DONE -> markDone(context, intent)
            AppNotifications.ACTION_REFRESH_WIDGET -> {
                val result = goAsync()
                context.app.scope.launch {
                    try {
                        TodayWidget.refresh(context)
                        context.app.notifications.resync()
                    } finally {
                        result.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                // Yeniden başlatmada alarmlar silinir; baştan kurulur.
                val result = goAsync()
                context.app.scope.launch {
                    try {
                        context.app.notifications.resync()
                        TodayWidget.refresh(context)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }

    private fun show(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppNotifications.EXTRA_ID, 0)
        val channel = intent.getStringExtra(AppNotifications.EXTRA_CHANNEL) ?: AppNotifications.CHANNEL_LESSONS
        val title = intent.getStringExtra(AppNotifications.EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(AppNotifications.EXTRA_BODY).orEmpty()
        val lessonId = intent.getStringExtra(AppNotifications.EXTRA_LESSON)

        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1E4B39.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
        if (lessonId != null) {
            val done = PendingIntent.getBroadcast(
                context, id,
                Intent(context, NotificationReceiver::class.java)
                    .setAction(AppNotifications.ACTION_MARK_DONE)
                    .putExtra(AppNotifications.EXTRA_LESSON, lessonId)
                    .putExtra(AppNotifications.EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "İşlendi", done)
        }
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(id, builder.build())
            } catch (_: SecurityException) {
            }
        }
    }

    /// Bu arada iptal edilmiş ya da silinmiş derse dokunulmaz.
    private fun markDone(context: Context, intent: Intent) {
        val lessonId = intent.getStringExtra(AppNotifications.EXTRA_LESSON) ?: return
        val id = intent.getIntExtra(AppNotifications.EXTRA_ID, 0)
        NotificationManagerCompat.from(context).cancel(id)
        val result = goAsync()
        context.app.scope.launch {
            try {
                context.app.repository.edit { markCompletedIfPlanned(listOf(lessonId)) }
            } finally {
                result.finish()
            }
        }
    }
}
