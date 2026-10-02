package xyz.adilemree.dersdefteri.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import xyz.adilemree.dersdefteri.data.Homework
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonTemplate
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.NotebookRepository
import xyz.adilemree.dersdefteri.data.Payment
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.net.ApiClient
import xyz.adilemree.dersdefteri.net.ApiException
import java.io.File
import java.security.MessageDigest

/// Yerel defteri sunucuyla eşitler.
///
/// Tasarım (iOS ile aynı): uygulamanın hiçbir yerinde "bu kayıt değişti"
/// işareti tutulmaz. Her eşitlemede yerel kayıtların içerik özeti çıkarılır ve
/// bir önceki başarılı itmedeki özetlerle karşılaştırılır. Böylece silinen ve
/// zincirleme silinen kayıtlar da kendiliğinden yakalanır.
class SyncEngine(context: Context, private val repository: NotebookRepository, private val api: ApiClient) {
    sealed interface Status {
        data object Idle : Status
        /// Abonelik yok — eşitleme kapalı, defter yalnızca cihazda.
        data object Disabled : Status
        /// Abonelik var ama henüz giriş yapılmamış.
        data object NeedsAccount : Status
        data object Syncing : Status
        data class Failed(val message: String) : Status
        data class Synced(val at: Long) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val stateFile = File(context.filesDir, "sync-state.json")
    private var state = load()
    private val inFlight = Mutex()

    /// Oturum sunucu tarafında kapandığında (yenileme jetonu reddedildi) çağrılır.
    var onSessionExpired: () -> Unit = {}

    private fun load(): SyncState = runCatching {
        api.json.decodeFromString<SyncState>(stateFile.readText())
    }.getOrElse { SyncState() }

    private fun save() {
        runCatching {
            val tmp = File(stateFile.parentFile, "sync-state.json.tmp")
            tmp.writeText(api.json.encodeToString(SyncState.serializer(), state))
            tmp.renameTo(stateFile)
        }
    }

    /// Oturum kapanınca yerel eşitleme defteri sıfırlanır; başka bir hesapla
    /// girildiğinde önceki hesabın kayıtları sunucuya itilmemeli.
    fun reset() {
        state = SyncState()
        save()
        _status.value = Status.Idle
    }

    /// Eşitleme açıkken haftalık dersler açılışta değil, sunucudaki güncel hâl
    /// çekildikten sonra üretilir.
    fun handlesRecurringLessons(isPro: Boolean): Boolean = isPro && api.isSignedIn

    /// Eşitleme Pro'ya aittir ve hesap gerektirir. Abonelik sona erdiğinde
    /// eşitleme durur ama hiçbir şey silinmez.
    suspend fun sync(isPro: Boolean) {
        if (!isPro) {
            _status.value = Status.Disabled
            return
        }
        if (!api.isSignedIn) {
            _status.value = Status.NeedsAccount
            return
        }
        if (!inFlight.tryLock()) return
        _status.value = Status.Syncing
        try {
            withContext(Dispatchers.IO) {
                push()
                pull()
                // Güncel hâl alındı; eksik haftalık dersler artık güvenle üretilir ve hemen gönderilir.
                repository.edit { topUp() }
                push()
                save()
            }
            _status.value = Status.Synced(System.currentTimeMillis())
        } catch (e: Exception) {
            _status.value = Status.Failed(e.message ?: "Eşitlenemedi.")
            if (e is ApiException.Unauthorized && !api.isSignedIn) onSessionExpired()
            // Bağlantı yoksa beklemek güvenli (dersler 4 hafta ileriye kadar
            // üretilmiş durumda). Sunucu isteği reddettiyse cihaz kendi başına
            // ders üretmeye devam etmeli.
            if (e !is ApiException.Offline) runCatching { repository.edit { topUp() } }
        } finally {
            inFlight.unlock()
        }
    }

    // MARK: Anlık görüntü

    private class Snapshot(
        val students: List<StudentDTO>,
        val lessons: List<LessonDTO>,
        val payments: List<PaymentDTO>,
        val homeworks: List<HomeworkDTO>,
        val templates: List<TemplateDTO>,
    )

    private fun snapshot(n: Notebook) = Snapshot(
        students = n.students.map(::dto),
        lessons = n.lessons.map(::dto),
        payments = n.payments.map(::dto),
        homeworks = n.homeworks.map(::dto),
        templates = n.templates.map(::dto),
    )

    private fun dto(s: Student) = StudentDTO(
        clientId = s.id, name = s.name, subject = s.subject, grade = s.grade, phone = s.phone,
        parentName = s.parentName, parentPhone = s.parentPhone, hourlyRate = s.hourlyRate,
        startDate = isoOf(s.startDate), colorIndex = s.colorIndex, notes = s.notes, isArchived = s.isArchived,
    )

    private fun dto(l: Lesson) = LessonDTO(
        clientId = l.id, studentClientId = l.studentId, templateClientId = l.templateId, paymentClientId = l.paymentId,
        date = isoOf(l.date), duration = l.duration, status = l.status, cancellationReason = l.cancellationReason,
        topic = l.topic, note = l.note, feeOverride = l.feeOverride, usesCustomFee = l.usesCustomFee,
    )

    private fun dto(p: Payment) = PaymentDTO(
        clientId = p.id, studentClientId = p.studentId, date = isoOf(p.date), amount = p.amount, method = p.method, note = p.note,
    )

    private fun dto(h: Homework) = HomeworkDTO(
        clientId = h.id, studentClientId = h.studentId, title = h.title, detail = h.detail,
        assignedDate = isoOf(h.assignedDate), dueDate = isoOf(h.dueDate), isDone = h.isDone, doneDate = h.doneDate?.let(::isoOf),
    )

    private fun dto(t: LessonTemplate) = TemplateDTO(
        clientId = t.id, studentClientId = t.studentId, weekday = t.weekday, hour = t.hour, minute = t.minute,
        duration = t.duration, feeOverride = t.feeOverride, usesCustomFee = t.usesCustomFee, isPaused = t.isPaused,
        generatedUntil = t.generatedUntil?.let(::isoOf),
    )

    /// Kaydın içerik özeti. clientUpdatedAt bilerek dışarıda bırakılır; yoksa
    /// her eşitlemede değişmemiş kayıtlar yeniden gönderilirdi.
    private fun digest(element: JsonObject): String {
        val canonical = JsonObject(element.filterKeys { it != "clientUpdatedAt" }.toSortedMap()).toString()
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun digest(s: StudentDTO) = digest(api.json.encodeToJsonElement(s).jsonObject)
    private fun digest(l: LessonDTO) = digest(api.json.encodeToJsonElement(l).jsonObject)
    private fun digest(p: PaymentDTO) = digest(api.json.encodeToJsonElement(p).jsonObject)
    private fun digest(h: HomeworkDTO) = digest(api.json.encodeToJsonElement(h).jsonObject)
    private fun digest(t: TemplateDTO) = digest(api.json.encodeToJsonElement(t).jsonObject)

    // MARK: İtme

    /// Değişen ve silinen kayıtları parça parça gönderir. Her parça sunucu
    /// kabul edince deftere işlenir; bağlantı koparsa yalnız kalanlar gider.
    private suspend fun push() {
        val snap = snapshot(repository.load())
        val payload = SyncPayload()
        val seen = HashSet<String>()
        val now = isoOf(System.currentTimeMillis())

        snap.students.forEach { seen += it.clientId; if (state.hashes[it.clientId] != digest(it)) payload.students += it.copy(clientUpdatedAt = now) }
        snap.lessons.forEach { seen += it.clientId; if (state.hashes[it.clientId] != digest(it)) payload.lessons += it.copy(clientUpdatedAt = now) }
        snap.payments.forEach { seen += it.clientId; if (state.hashes[it.clientId] != digest(it)) payload.payments += it.copy(clientUpdatedAt = now) }
        snap.homeworks.forEach { seen += it.clientId; if (state.hashes[it.clientId] != digest(it)) payload.homeworks += it.copy(clientUpdatedAt = now) }
        snap.templates.forEach { seen += it.clientId; if (state.hashes[it.clientId] != digest(it)) payload.templates += it.copy(clientUpdatedAt = now) }

        // Defterde olup artık cihazda olmayan her kayıt silinmiş demektir.
        for (key in state.hashes.keys - seen) {
            when (state.kinds[key]) {
                RecordKind.student -> payload.students += StudentDTO(clientId = key, clientUpdatedAt = now, deletedAt = now, startDate = now)
                RecordKind.lesson -> payload.lessons += LessonDTO(clientId = key, clientUpdatedAt = now, deletedAt = now, date = now)
                RecordKind.payment -> payload.payments += PaymentDTO(clientId = key, clientUpdatedAt = now, deletedAt = now, date = now)
                RecordKind.homework -> payload.homeworks += HomeworkDTO(clientId = key, clientUpdatedAt = now, deletedAt = now, assignedDate = now, dueDate = now)
                RecordKind.template -> payload.templates += TemplateDTO(clientId = key, clientUpdatedAt = now, deletedAt = now)
                RecordKind.`package`, null -> state.forget(key)
            }
        }

        if (payload.isEmpty) return

        for (chunk in payload.chunked()) {
            api.request("/v1/sync", method = "POST", body = encode(chunk))
            // Yalnız sunucu kabul ettikten sonra defter güncellenir.
            record(chunk)
            save()
        }
    }

    private fun encode(chunk: SyncPayload): JsonObject = buildJsonObject {
        put("students", JsonArray(chunk.students.map { api.json.encodeToJsonElement(it) }))
        put("templates", JsonArray(chunk.templates.map { api.json.encodeToJsonElement(it) }))
        put("payments", JsonArray(chunk.payments.map { api.json.encodeToJsonElement(it) }))
        put("lessons", JsonArray(chunk.lessons.map { lesson ->
            val obj = api.json.encodeToJsonElement(lesson).jsonObject
            // Ödeme bağı boşsa açıkça null: sunucudaki bağ da silinsin.
            if (lesson.paymentClientId == null) JsonObject(obj + ("paymentClientId" to JsonNull)) else obj
        }))
        put("homeworks", JsonArray(chunk.homeworks.map { api.json.encodeToJsonElement(it) }))
    }

    /// Gönderilen parçayı deftere işler: yazılanların özeti hatırlanır, silinenler unutulur.
    private fun record(chunk: SyncPayload) {
        chunk.students.forEach { if (it.deletedAt == null) state.remember(it.clientId, digest(it), RecordKind.student) else state.forget(it.clientId) }
        chunk.templates.forEach { if (it.deletedAt == null) state.remember(it.clientId, digest(it), RecordKind.template) else state.forget(it.clientId) }
        chunk.payments.forEach { if (it.deletedAt == null) state.remember(it.clientId, digest(it), RecordKind.payment) else state.forget(it.clientId) }
        chunk.lessons.forEach { if (it.deletedAt == null) state.remember(it.clientId, digest(it), RecordKind.lesson) else state.forget(it.clientId) }
        chunk.homeworks.forEach { if (it.deletedAt == null) state.remember(it.clientId, digest(it), RecordKind.homework) else state.forget(it.clientId) }
    }

    // MARK: Çekme

    private suspend fun pull() {
        val query = state.cursor?.let { mapOf("since" to it) } ?: emptyMap()
        val response = api.decode<PullResponse>(api.request("/v1/sync", query = query))
        val forgotten = mutableListOf<String>()

        repository.edit {
            // Öğrenciler önce uygulanır; ders, ödeme ve ödevler onlara bağlanır.
            for (dto in response.students) {
                if (dto.deletedAt != null) { deleteStudent(dto.clientId); forgotten += dto.clientId; continue }
                val base = students[dto.clientId] ?: Student(id = dto.clientId)
                students[dto.clientId] = base.copy(
                    name = dto.name, subject = dto.subject, grade = dto.grade, phone = dto.phone,
                    parentName = dto.parentName, parentPhone = dto.parentPhone, hourlyRate = dto.hourlyRate,
                    startDate = runCatching { msOf(dto.startDate) }.getOrDefault(base.startDate),
                    colorIndex = dto.colorIndex, notes = dto.notes, isArchived = dto.isArchived,
                )
            }
            fun studentRef(id: String?) = id?.lowercase()?.takeIf { it in students }

            for (dto in response.templates) {
                if (dto.deletedAt != null) { deleteTemplate(dto.clientId); forgotten += dto.clientId; continue }
                val base = templates[dto.clientId] ?: LessonTemplate(id = dto.clientId)
                templates[dto.clientId] = base.copy(
                    studentId = studentRef(dto.studentClientId), weekday = dto.weekday, hour = dto.hour, minute = dto.minute,
                    duration = dto.duration, feeOverride = dto.feeOverride, usesCustomFee = dto.usesCustomFee,
                    isPaused = dto.isPaused, generatedUntil = dto.generatedUntil?.let { runCatching { msOf(it) }.getOrNull() },
                )
            }
            // Ödemeler derslerden önce: dersler toplu ödemelerine bağlanır.
            for (dto in response.payments) {
                if (dto.deletedAt != null) { deletePayment(dto.clientId); forgotten += dto.clientId; continue }
                val base = payments[dto.clientId] ?: Payment(id = dto.clientId)
                payments[dto.clientId] = base.copy(
                    studentId = studentRef(dto.studentClientId),
                    date = runCatching { msOf(dto.date) }.getOrDefault(base.date),
                    amount = dto.amount, method = dto.method, note = dto.note,
                )
            }
            val serverKnowsPayments = response.lessonPayments == true
            for (dto in response.lessons) {
                if (dto.deletedAt != null) { deleteLesson(dto.clientId); forgotten += dto.clientId; continue }
                val existing = lessons[dto.clientId]
                val date = runCatching { msOf(dto.date) }.getOrDefault(existing?.date ?: System.currentTimeMillis())
                val base = existing ?: Lesson(id = dto.clientId, date = date)
                lessons[dto.clientId] = base.copy(
                    date = date, duration = dto.duration, status = dto.status, cancellationReason = dto.cancellationReason,
                    topic = dto.topic, note = dto.note, feeOverride = dto.feeOverride, usesCustomFee = dto.usesCustomFee,
                    studentId = studentRef(dto.studentClientId),
                    templateId = dto.templateClientId?.lowercase()?.takeIf { it in templates },
                    // Ödeme bağını bilmeyen sunucu bu alanı hiç göndermez; o zaman cihazdaki bağ silinmemeli.
                    paymentId = if (serverKnowsPayments) dto.paymentClientId?.lowercase()?.takeIf { it in payments } else base.paymentId,
                )
            }
            for (dto in response.homeworks) {
                if (dto.deletedAt != null) { homeworks.remove(dto.clientId); forgotten += dto.clientId; continue }
                val base = homeworks[dto.clientId] ?: Homework(id = dto.clientId)
                homeworks[dto.clientId] = base.copy(
                    studentId = studentRef(dto.studentClientId), title = dto.title, detail = dto.detail,
                    assignedDate = runCatching { msOf(dto.assignedDate) }.getOrDefault(base.assignedDate),
                    dueDate = runCatching { msOf(dto.dueDate) }.getOrDefault(base.dueDate),
                    isDone = dto.isDone, doneDate = dto.doneDate?.let { runCatching { msOf(it) }.getOrNull() },
                )
            }
        }

        forgotten.forEach { state.forget(it) }
        state.cursor = response.cursor

        // Sunucudan gelen hâli deftere yaz; yoksa bir sonraki itmede aynı
        // kayıtlar değişmiş sanılıp geri gönderilir. Yalnız sunucudan gelen
        // kayıtlar hatırlanır: eşitleme sürerken cihazda eklenen bir kayıt
        // gönderilmiş sanılıp atlanmasın.
        val pulled = HashSet<String>().apply {
            response.students.forEach { add(it.clientId) }
            response.lessons.forEach { add(it.clientId) }
            response.payments.forEach { add(it.clientId) }
            response.homeworks.forEach { add(it.clientId) }
            response.templates.forEach { add(it.clientId) }
        }
        val snap = snapshot(repository.load())
        snap.students.filter { it.clientId in pulled }.forEach { state.remember(it.clientId, digest(it), RecordKind.student) }
        snap.lessons.filter { it.clientId in pulled }.forEach { state.remember(it.clientId, digest(it), RecordKind.lesson) }
        snap.payments.filter { it.clientId in pulled }.forEach { state.remember(it.clientId, digest(it), RecordKind.payment) }
        snap.homeworks.filter { it.clientId in pulled }.forEach { state.remember(it.clientId, digest(it), RecordKind.homework) }
        snap.templates.filter { it.clientId in pulled }.forEach { state.remember(it.clientId, digest(it), RecordKind.template) }
    }
}
