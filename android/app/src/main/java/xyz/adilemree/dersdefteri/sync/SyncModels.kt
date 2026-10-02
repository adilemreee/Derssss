package xyz.adilemree.dersdefteri.sync

import kotlinx.serialization.Serializable
import java.time.Instant

// Sunucuyla taşınan kayıt biçimleri. Alan adları sunucu şemasıyla ve iOS
// uygulamasıyla birebir aynıdır. Tarihler ISO-8601 metin olarak taşınır.

fun isoOf(ms: Long): String = Instant.ofEpochMilli(ms).toString()
fun msOf(iso: String): Long = Instant.parse(iso).toEpochMilli()

@Serializable
data class StudentDTO(
    val clientId: String,
    val clientUpdatedAt: String = "",
    val deletedAt: String? = null,
    val name: String = "",
    val subject: String = "",
    val grade: String = "",
    val phone: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val hourlyRate: Double = 0.0,
    val startDate: String = "",
    val colorIndex: Int = 0,
    val notes: String = "",
    val isArchived: Boolean = false,
)

@Serializable
data class LessonDTO(
    val clientId: String,
    val clientUpdatedAt: String = "",
    val deletedAt: String? = null,
    val studentClientId: String? = null,
    val templateClientId: String? = null,
    /// Toplu ödeme bağı. Boşken sunucuya giderken açıkça null yazılır:
    /// dersi ödemeden çıkarmak sunucudaki bağı da silmeli.
    val paymentClientId: String? = null,
    val date: String = "",
    val duration: Int = 60,
    val status: String = "planned",
    val cancellationReason: String = "none",
    val topic: String = "",
    val note: String = "",
    val feeOverride: Double? = null,
    val usesCustomFee: Boolean = false,
)

@Serializable
data class PaymentDTO(
    val clientId: String,
    val clientUpdatedAt: String = "",
    val deletedAt: String? = null,
    val studentClientId: String? = null,
    val date: String = "",
    val amount: Double = 0.0,
    val method: String = "cash",
    val note: String = "",
)

@Serializable
data class HomeworkDTO(
    val clientId: String,
    val clientUpdatedAt: String = "",
    val deletedAt: String? = null,
    val studentClientId: String? = null,
    val title: String = "",
    val detail: String = "",
    val assignedDate: String = "",
    val dueDate: String = "",
    val isDone: Boolean = false,
    val doneDate: String? = null,
)

@Serializable
data class TemplateDTO(
    val clientId: String,
    val clientUpdatedAt: String = "",
    val deletedAt: String? = null,
    val studentClientId: String? = null,
    val weekday: Int = 3,
    val hour: Int = 17,
    val minute: Int = 0,
    val duration: Int = 60,
    val feeOverride: Double? = null,
    val usesCustomFee: Boolean = false,
    val isPaused: Boolean = false,
    val generatedUntil: String? = null,
)

/// İtme gövdesi
data class SyncPayload(
    val students: MutableList<StudentDTO> = mutableListOf(),
    val lessons: MutableList<LessonDTO> = mutableListOf(),
    val payments: MutableList<PaymentDTO> = mutableListOf(),
    val homeworks: MutableList<HomeworkDTO> = mutableListOf(),
    val templates: MutableList<TemplateDTO> = mutableListOf(),
) {
    val isEmpty: Boolean
        get() = students.isEmpty() && lessons.isEmpty() && payments.isEmpty() && homeworks.isEmpty() && templates.isEmpty()

    val count: Int get() = students.size + lessons.size + payments.size + homeworks.size + templates.size

    /// Değişiklikleri sunucunun kabul edeceği büyüklükte parçalara böler.
    /// Sıra: öğrenciler, haftalık dersler, ödemeler, dersler, ödevler. Dersler
    /// öğrencilerine, haftalık derslerine ve toplu ödemelerine bağlanır.
    fun chunked(maxRecords: Int = CHUNK_SIZE): List<SyncPayload> {
        val chunks = mutableListOf<SyncPayload>()
        var current = SyncPayload()
        fun <T> add(items: List<T>, target: (SyncPayload) -> MutableList<T>) {
            for (item in items) {
                if (current.count >= maxRecords) {
                    chunks += current
                    current = SyncPayload()
                }
                target(current) += item
            }
        }
        add(students) { it.students }
        add(templates) { it.templates }
        add(payments) { it.payments }
        add(lessons) { it.lessons }
        add(homeworks) { it.homeworks }
        if (!current.isEmpty) chunks += current
        return chunks
    }

    companion object {
        /// Sunucu tür başına 500 kabul eder; tek istekte toplam 200 gövdeyi de
        /// küçük tutar (yavaş bağlantıda zaman aşımına düşmesin).
        const val CHUNK_SIZE = 200
    }
}

@Serializable
data class PullResponse(
    val cursor: String,
    val students: List<StudentDTO> = emptyList(),
    val lessons: List<LessonDTO> = emptyList(),
    val payments: List<PaymentDTO> = emptyList(),
    val homeworks: List<HomeworkDTO> = emptyList(),
    val templates: List<TemplateDTO> = emptyList(),
    /// Derslerin ödeme bağını bilen sunucu true gönderir.
    val lessonPayments: Boolean? = null,
)

@Serializable
data class PushResponse(val ok: Boolean = false, val applied: Int = 0)

@Serializable
enum class RecordKind {
    student, lesson, payment, homework, template,
    /// Kaldırılan ders paketleri; eski defterler okunabilsin diye durur.
    `package`,
}

/// Son başarılı eşitlemenin hafızası. Veritabanına değil dosyaya yazılır:
/// bu veri kullanıcının değil senkronizasyonun defteri, sunucuya gitmez.
@Serializable
data class SyncState(
    var cursor: String? = null,
    val hashes: MutableMap<String, String> = mutableMapOf(),
    val kinds: MutableMap<String, RecordKind> = mutableMapOf(),
) {
    fun remember(id: String, digest: String, kind: RecordKind) {
        hashes[id] = digest
        kinds[id] = kind
    }

    fun forget(id: String) {
        hashes.remove(id)
        kinds.remove(id)
    }
}
