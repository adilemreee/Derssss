package xyz.adilemree.dersdefteri.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.CancellationReason
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonStatus
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.ui.LessonFormRoute
import xyz.adilemree.dersdefteri.ui.components.ActionSheet
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.SheetAction
import xyz.adilemree.dersdefteri.ui.components.statusIcon
import xyz.adilemree.dersdefteri.ui.nav

/// Ders satırlarına uzun basınca açılan menü ve onayların durumu.
class LessonMenuState {
    var menuFor by mutableStateOf<Lesson?>(null)
    var cancelFor by mutableStateOf<Lesson?>(null)
    var stopRepeatFor by mutableStateOf<Lesson?>(null)
    var deleteFor by mutableStateOf<Lesson?>(null)
}

@Composable
fun rememberLessonMenuState() = remember { LessonMenuState() }

/// iOS'taki ders bağlam menüsü: durum, kopyalama, haftalık tekrar ve silme.
@Composable
fun LessonMenus(
    state: LessonMenuState,
    notebook: Notebook,
    showEdit: Boolean = false,
    showDelete: Boolean = true,
    onDeleted: () -> Unit = {},
) {
    val app = LocalApp.current
    val nav = nav()

    state.menuFor?.let { lesson ->
        val actions = mutableListOf<SheetAction>()
        LessonStatus.entries.filter { it != lesson.lessonStatus }.forEach { status ->
            actions += SheetAction(status.title, statusIcon(status)) {
                if (status == LessonStatus.CANCELLED) state.cancelFor = lesson
                else app.edit { setStatus(lesson.id, status) }
            }
        }
        if (showEdit) {
            actions += SheetAction("Düzenle", Icons.Rounded.Edit) { nav.navigate(LessonFormRoute(lessonId = lesson.id)) }
        }
        if (lesson.templateId == null) {
            actions += SheetAction("Haftaya Aynı Ders", Icons.Rounded.EventRepeat) { app.edit { copyNextWeek(lesson.id) } }
        }
        actions += SheetAction("Aynı Öğrenciye Yeni Ders", Icons.Rounded.PersonAddAlt) {
            nav.navigate(LessonFormRoute(studentId = lesson.studentId, date = addDays(lesson.date, 7)))
        }
        if (lesson.templateId == null) {
            actions += SheetAction("Her Hafta Tekrarla", Icons.Rounded.Repeat) { app.edit { startSeries(lesson.id) } }
        } else {
            actions += SheetAction("Tekrarı Durdur", Icons.Rounded.RepeatOne, destructive = true) { state.stopRepeatFor = lesson }
        }
        if (showDelete) {
            actions += SheetAction("Sil", Icons.Rounded.Delete, destructive = true) { state.deleteFor = lesson }
        }
        val name = notebook.studentOf(lesson)?.name ?: "Ders"
        ActionSheet(
            title = name,
            message = "${Fmt.dayMonth(lesson.date)} ${Fmt.weekday(lesson.date)} · ${Fmt.time(lesson.date)}",
            actions = actions,
            onDismiss = { state.menuFor = null },
        )
    }

    state.cancelFor?.let { lesson ->
        CancelReasonSheet(onPick = { reason -> app.edit { setStatus(lesson.id, LessonStatus.CANCELLED, reason) } }, onDismiss = { state.cancelFor = null })
    }

    state.stopRepeatFor?.let { lesson ->
        ConfirmDialog(
            title = "Haftalık tekrar durdurulsun mu?",
            message = "Bu dersten sonraki planlı dersler silinir; bu ders ve öncekiler kalır.",
            confirmTitle = "Tekrarı Durdur",
            onConfirm = { app.edit { stopRepeating(lesson.id) } },
            onDismiss = { state.stopRepeatFor = null },
        )
    }

    state.deleteFor?.let { lesson ->
        LessonDeleteDialog(lesson, onDismiss = { state.deleteFor = null }, onDeleted = onDeleted)
    }
}

/// İptal sebebi sorulur; sebep raporda ve telafi takibinde kullanılır.
@Composable
fun CancelReasonSheet(onPick: (CancellationReason) -> Unit, onDismiss: () -> Unit) {
    ActionSheet(
        title = "İptal sebebi seç",
        actions = CancellationReason.choices.map { reason -> SheetAction(reason.title) { onPick(reason) } },
        onDismiss = onDismiss,
    )
}

/// Ders silme onayı. Haftalık serideki bir derste "yalnızca bu" ve "bu ve
/// sonrakiler" seçenekleri çıkar.
@Composable
fun LessonDeleteDialog(lesson: Lesson, onDismiss: () -> Unit, onDeleted: () -> Unit = {}) {
    val app = LocalApp.current
    if (lesson.templateId != null) {
        ActionSheet(
            title = "Bu ders her hafta tekrarlanıyor",
            message = "Yalnızca bu dersi silersen sonraki haftalar devam eder.",
            actions = listOf(
                SheetAction("Yalnızca Bu Dersi Sil", Icons.Rounded.Delete, destructive = true) {
                    app.edit { deleteLesson(lesson.id) }
                    onDeleted()
                },
                SheetAction("Bu ve Sonraki Dersleri Sil", Icons.Rounded.Delete, destructive = true) {
                    app.edit { deleteThisAndFollowing(lesson.id) }
                    onDeleted()
                },
            ),
            onDismiss = onDismiss,
        )
    } else {
        ConfirmDialog(
            title = "Ders silinsin mi?",
            confirmTitle = "Dersi Sil",
            onConfirm = {
                app.edit { deleteLesson(lesson.id) }
                onDeleted()
            },
            onDismiss = onDismiss,
        )
    }
}

/// Toplu işaretleme bakiyeleri birden değiştirdiği için tutarı söyler.
@Composable
fun MarkAllCompletedDialog(lessons: List<Lesson>, notebook: Notebook, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val total = lessons.sumOf { notebook.fee(it) }
    ConfirmDialog(
        title = "${lessons.size} ders İşlendi olarak işaretlensin mi?",
        message = "Toplam ${Fmt.money(total)} öğrencilerin bakiyesine eklenir. Yapılmayan ders varsa önce onu iptal et.",
        confirmTitle = "Tümünü İşlendi Yap",
        destructive = false,
        onConfirm = {
            val ids = lessons.map { it.id }
            app.edit { ids.forEach { setStatus(it, LessonStatus.COMPLETED) } }
        },
        onDismiss = onDismiss,
    )
}

/// Dünden önceki, hâlâ planlı dersler; en yenisi önce.
fun unmarkedLessons(notebook: Notebook, now: Long = System.currentTimeMillis()): List<Lesson> {
    val today = xyz.adilemree.dersdefteri.data.startOfDay(now)
    return notebook.lessons.filter { it.isPlanned && it.date < today }.sortedByDescending { it.date }
}
