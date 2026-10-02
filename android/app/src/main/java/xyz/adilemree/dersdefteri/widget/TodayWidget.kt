package xyz.adilemree.dersdefteri.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as FixedColor
import xyz.adilemree.dersdefteri.MainActivity
import xyz.adilemree.dersdefteri.R
import xyz.adilemree.dersdefteri.app
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.isSameDay
import xyz.adilemree.dersdefteri.data.startOfDay
import xyz.adilemree.dersdefteri.ui.theme.studentColor

// Ana ekran widget'ı: bugünün dersleri, sıradaki ders ve biten derslerde
// uygulamayı açmadan "İşlendi" düğmesi. iOS widget'ıyla aynı kurallar.

private object W {
    val background = ColorProvider(day = Color(0xFFF7F2E7), night = Color(0xFF1A1915))
    val ink = ColorProvider(day = Color(0xFF26303E), night = Color(0xFFEAE6DB))
    val inkSoft = ColorProvider(day = Color(0xFF77808D), night = Color(0xFF9C988D))
    val accent = ColorProvider(day = Color(0xFF1E4B39), night = Color(0xFF7FC79F))
    val green = ColorProvider(day = Color(0xFF3E8E5F), night = Color(0xFF5CB283))
    val amber = ColorProvider(day = Color(0xFFDF9E3B), night = Color(0xFFE6AF58))
    val card = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF262420))
}

private data class WLesson(
    val id: String,
    val date: Long,
    val duration: Int,
    val studentName: String,
    val subject: String,
    val colorIndex: Int,
    val completed: Boolean,
) {
    val endDate: Long get() = date + duration * 60_000L
}

private class Entry(val now: Long, val lessons: List<WLesson>, val olderUnmarked: Int) {
    val today = lessons.filter { isSameDay(it.date, now) }
    /// Şu an süren ya da sıradaki planlı ders
    val next = lessons.firstOrNull { !it.completed && it.endDate > now }
    /// Saati geçmiş, işaretlenmemiş dersler (dün ve bugün) + daha eskiler
    val unmarkedCount = lessons.count { !it.completed && it.endDate <= now } + olderUnmarked
    fun ongoing(l: WLesson) = l.date <= now && l.endDate > now
    fun needsMarking(l: WLesson) = !l.completed && l.endDate <= now

    companion object {
        fun build(notebook: Notebook, now: Long): Entry {
            val from = addDays(startOfDay(now), -1)
            val to = addDays(startOfDay(now), 8)
            val lessons = notebook.lessons
                .filter { it.date >= from && it.date < to && !it.isCancelled && notebook.studentOf(it)?.isArchived != true }
                .map {
                    val s = notebook.studentOf(it)
                    WLesson(it.id, it.date, it.duration, s?.name ?: "Öğrenci", s?.subject.orEmpty(), s?.colorIndex ?: 0, it.isCompleted)
                }
            val older = notebook.lessons.count { it.date < from && it.isPlanned }
            return Entry(now, lessons, older)
        }
    }
}

class TodayWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 260.dp)

        suspend fun refresh(context: Context) {
            runCatching { TodayWidget().updateAll(context) }
        }
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.app.repository
        // Oturum açıkken gelen güncellemelerde veri canlı akıştan okunur; ilk
        // okuma bitmeden boş widget görünmesin diye başlangıçta bir kez yüklenir.
        val initial = repository.notebook.value ?: repository.load()
        provideContent {
            val notebook by repository.notebook.collectAsState()
            val entry = Entry.build(notebook ?: initial, System.currentTimeMillis())
            val size = LocalSize.current
            Box(
                GlanceModifier
                    .fillMaxSize()
                    .background(W.background)
                    .cornerRadius(22.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(14.dp),
            ) {
                when {
                    size.width < 200.dp -> Small(entry)
                    size.height < 200.dp -> ListView(entry, limit = 3)
                    else -> ListView(entry, limit = 7)
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/// Widget'taki "İşlendi" düğmesi: ders doğrudan işlenir (iptal ya da silinmişse dokunulmaz).
class MarkLessonDoneAction : ActionCallback {
    companion object {
        val lessonKey = ActionParameters.Key<String>("lessonID")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[lessonKey] ?: return
        context.app.repository.edit { markCompletedIfPlanned(listOf(id)) }
        TodayWidget.refresh(context)
    }
}

private fun text(size: Int, color: androidx.glance.unit.ColorProvider, bold: Boolean = false, serif: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = if (serif) FontFamily.Serif else null,
)

private fun doneAction(id: String) = actionRunCallback<MarkLessonDoneAction>(actionParametersOf(MarkLessonDoneAction.lessonKey to id))

// MARK: - Küçük

@Composable
private fun Small(entry: Entry) {
    val toMark = entry.today.lastOrNull { entry.needsMarking(it) }
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Bugün", style = text(12, W.accent, bold = true), modifier = GlanceModifier.defaultWeight())
            Text("${entry.today.size} ders", style = text(11, W.inkSoft))
        }
        Spacer(GlanceModifier.defaultWeight())
        when {
            toMark != null -> {
                // Biten ders öncelikli: işaretlenmeden bakiyeye yansımaz.
                Text("Ders bitti mi?", style = text(11, W.amber, bold = true))
                Headline(toMark)
                Spacer(GlanceModifier.height(6.dp))
                Box(
                    GlanceModifier.fillMaxWidth().background(W.green).cornerRadius(14.dp).padding(vertical = 5.dp)
                        .clickable(doneAction(toMark.id)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓ İşlendi", style = TextStyle(color = FixedColor(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
            entry.next != null -> {
                val next = entry.next
                Text(
                    if (entry.ongoing(next)) "Şu an" else Fmt.dayLabel(next.date, entry.now),
                    style = text(11, if (entry.ongoing(next)) W.green else W.inkSoft, bold = true),
                )
                Headline(next)
            }
            else -> {
                Image(ImageProvider(R.drawable.ic_cafe), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(W.amber))
                Text("Planlı ders yok", style = text(14, W.ink, bold = true))
            }
        }
    }
}

@Composable
private fun Headline(lesson: WLesson) {
    Column {
        Text(Fmt.time(lesson.date), style = text(26, W.ink, bold = true, serif = true))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(7.dp).background(FixedColor(studentColor(lesson.colorIndex))).cornerRadius(4.dp)) {}
            Spacer(GlanceModifier.width(5.dp))
            Text(lesson.studentName, style = text(12, W.ink, bold = true), maxLines = 1)
        }
        if (lesson.subject.isNotEmpty()) {
            Text("${lesson.subject} · ${lesson.duration} dk", style = text(11, W.inkSoft), maxLines = 1)
        }
    }
}

// MARK: - Orta ve büyük: liste

@Composable
private fun ListView(entry: Entry, limit: Int) {
    // Bugünün dersleri; bugün bittiyse sıradaki günün dersleri.
    val (title, lessons) = run {
        val today = entry.today
        if (today.any { !it.completed } || today.any { entry.needsMarking(it) }) "Bugün" to today
        else {
            val next = entry.next
            if (next != null) Fmt.dayLabel(next.date, entry.now) to entry.lessons.filter { isSameDay(it.date, next.date) }
            else "Bugün" to today
        }
    }
    // Sığmayanlar varsa bitmiş ve işaretlenmiş dersler önce düşer.
    val visible = if (lessons.size <= limit) lessons else {
        val open = lessons.filter { !(it.completed && it.endDate <= entry.now) }
        if (open.size >= limit) open.take(limit) else lessons.takeLast(limit)
    }

    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = text(16, W.ink, bold = true, serif = true))
            Spacer(GlanceModifier.width(6.dp))
            Text(Fmt.dayMonth(lessons.firstOrNull()?.date ?: entry.now) + " " + Fmt.weekday(lessons.firstOrNull()?.date ?: entry.now),
                style = text(11, W.inkSoft), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (entry.unmarkedCount > 0) Text("! ${entry.unmarkedCount}", style = text(11, W.amber, bold = true))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (lessons.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_cafe), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(W.amber))
                Spacer(GlanceModifier.width(8.dp))
                Text("Önümüzdeki günlerde planlı ders yok", style = text(13, W.inkSoft))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            visible.forEach { lesson ->
                LessonRow(lesson, entry)
                Spacer(GlanceModifier.height(5.dp))
            }
            if (lessons.size > limit) {
                Text("+${lessons.size - limit} ders daha", style = text(11, W.inkSoft, bold = true))
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: WLesson, entry: Entry) {
    val ongoing = entry.ongoing(lesson)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.time(lesson.date), style = text(14, if (lesson.completed) W.inkSoft else W.ink, bold = true, serif = true), modifier = GlanceModifier.width(46.dp))
        Box(GlanceModifier.width(3.dp).height(24.dp).background(FixedColor(studentColor(lesson.colorIndex))).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(lesson.studentName, style = text(12, if (lesson.completed) W.inkSoft else W.ink, bold = true), maxLines = 1)
            Text(
                when {
                    ongoing -> "Şu an · ${lesson.duration} dk"
                    lesson.subject.isEmpty() -> "${lesson.duration} dk"
                    else -> "${lesson.subject} · ${lesson.duration} dk"
                },
                style = text(11, if (ongoing) W.green else W.inkSoft),
                maxLines = 1,
            )
        }
        when {
            lesson.completed -> Image(ImageProvider(R.drawable.ic_check_circle), "İşlendi", GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(W.green))
            entry.needsMarking(lesson) -> Image(
                ImageProvider(R.drawable.ic_check_circle_outline),
                "${lesson.studentName} dersini işlendi yap",
                GlanceModifier.size(26.dp).clickable(doneAction(lesson.id)),
                colorFilter = ColorFilter.tint(W.green),
            )
            else -> {}
        }
    }
}
