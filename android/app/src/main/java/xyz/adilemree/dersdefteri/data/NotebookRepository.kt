package xyz.adilemree.dersdefteri.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@Dao
interface NotebookDao {
    @Query("SELECT * FROM students") fun observeStudents(): Flow<List<Student>>
    @Query("SELECT * FROM lessons") fun observeLessons(): Flow<List<Lesson>>
    @Query("SELECT * FROM payments") fun observePayments(): Flow<List<Payment>>
    @Query("SELECT * FROM homeworks") fun observeHomeworks(): Flow<List<Homework>>
    @Query("SELECT * FROM templates") fun observeTemplates(): Flow<List<LessonTemplate>>

    @Query("SELECT * FROM students") suspend fun students(): List<Student>
    @Query("SELECT * FROM lessons") suspend fun lessons(): List<Lesson>
    @Query("SELECT * FROM payments") suspend fun payments(): List<Payment>
    @Query("SELECT * FROM homeworks") suspend fun homeworks(): List<Homework>
    @Query("SELECT * FROM templates") suspend fun templates(): List<LessonTemplate>

    @Upsert suspend fun upsertStudents(items: List<Student>)
    @Upsert suspend fun upsertLessons(items: List<Lesson>)
    @Upsert suspend fun upsertPayments(items: List<Payment>)
    @Upsert suspend fun upsertHomeworks(items: List<Homework>)
    @Upsert suspend fun upsertTemplates(items: List<LessonTemplate>)

    @Query("DELETE FROM students WHERE id IN (:ids)") suspend fun deleteStudents(ids: List<String>)
    @Query("DELETE FROM lessons WHERE id IN (:ids)") suspend fun deleteLessons(ids: List<String>)
    @Query("DELETE FROM payments WHERE id IN (:ids)") suspend fun deletePayments(ids: List<String>)
    @Query("DELETE FROM homeworks WHERE id IN (:ids)") suspend fun deleteHomeworks(ids: List<String>)
    @Query("DELETE FROM templates WHERE id IN (:ids)") suspend fun deleteTemplates(ids: List<String>)
}

@Database(
    entities = [Student::class, Lesson::class, Payment::class, Homework::class, LessonTemplate::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): NotebookDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ders-defteri.db").build()
    }
}

/// Defterin tek giriş noktası. Ekranlar `notebook` akışını okur; her
/// değişiklik `edit` ile yapılır ve tek bir işlemde (transaction) yazılır.
class NotebookRepository(private val db: AppDatabase, scope: CoroutineScope) {
    private val dao = db.dao()

    /// Veritabanından ilk okuma bitene kadar null.
    val notebook: StateFlow<Notebook?> = combine(
        dao.observeStudents(),
        dao.observeLessons(),
        dao.observePayments(),
        dao.observeHomeworks(),
        dao.observeTemplates(),
    ) { s, l, p, h, t -> Notebook(s, l, p, h, t) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /// Her kayıttan sonra yayınlanır: bildirimler, widget ve eşitleme bunu dinler.
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    suspend fun load(): Notebook = Notebook(dao.students(), dao.lessons(), dao.payments(), dao.homeworks(), dao.templates())

    /// Defteri değiştirir. Blok defterin güncel kopyası üzerinde çalışır;
    /// yalnızca değişen kayıtlar yazılır.
    suspend fun <T> edit(block: NotebookEditor.() -> T): T {
        val (result, changed) = withContext(Dispatchers.IO) {
            db.withTransaction {
                val editor = NotebookEditor(load())
                val result = editor.block()
                val changes = editor.changes()
                apply(changes)
                result to !changes.isEmpty
            }
        }
        if (changed) _saved.tryEmit(Unit)
        return result
    }

    private suspend fun apply(c: NotebookEditor.Changes) {
        // Önce silinenler, sonra yazılanlar: kimliği değişen şablonlar böyle tutarlı kalır.
        c.deletedLessons.chunked(500).forEach { dao.deleteLessons(it) }
        c.deletedPayments.chunked(500).forEach { dao.deletePayments(it) }
        c.deletedHomeworks.chunked(500).forEach { dao.deleteHomeworks(it) }
        c.deletedTemplates.chunked(500).forEach { dao.deleteTemplates(it) }
        c.deletedStudents.chunked(500).forEach { dao.deleteStudents(it) }
        if (c.students.isNotEmpty()) dao.upsertStudents(c.students)
        if (c.templates.isNotEmpty()) dao.upsertTemplates(c.templates)
        if (c.payments.isNotEmpty()) dao.upsertPayments(c.payments)
        if (c.lessons.isNotEmpty()) dao.upsertLessons(c.lessons)
        if (c.homeworks.isNotEmpty()) dao.upsertHomeworks(c.homeworks)
    }
}
