package xyz.adilemree.dersdefteri.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// Veritabanı kayıtları. Alanlar iOS uygulamasındaki modellerle ve sunucu
// şemasıyla birebir aynıdır; kimlikler küçük harfli UUID metnidir.
// Tarihler epoch milisaniye olarak tutulur.

fun newId(): String = UUID.randomUUID().toString()

// MARK: - Ders durumu

enum class LessonStatus(val raw: String, val title: String) {
    PLANNED("planned", "Planlandı"),
    COMPLETED("completed", "İşlendi"),
    CANCELLED("cancelled", "İptal");

    companion object {
        fun from(raw: String): LessonStatus = entries.firstOrNull { it.raw == raw } ?: PLANNED
    }
}

enum class CancellationReason(val raw: String, val title: String, val shortTitle: String) {
    NONE("none", "Sebep yok", "İptal"),
    STUDENT("student", "Öğrenci iptal etti", "Öğrenci iptal"),
    TEACHER("teacher", "Öğretmen iptal etti", "Öğretmen iptal"),
    MAKEUP("makeup", "Telafi edilecek", "Telafi");

    companion object {
        fun from(raw: String): CancellationReason = entries.firstOrNull { it.raw == raw } ?: NONE

        /// İptal sebebi sorulurken sunulanlar
        val choices = listOf(STUDENT, TEACHER, MAKEUP)
    }
}

enum class PaymentMethod(val raw: String, val title: String) {
    CASH("cash", "Nakit"),
    TRANSFER("transfer", "Havale/EFT"),
    OTHER("other", "Diğer");

    companion object {
        fun from(raw: String): PaymentMethod = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

// MARK: - Öğrenci

@Entity(tableName = "students")
data class Student(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val subject: String = "",
    val grade: String = "",
    val phone: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val hourlyRate: Double = 0.0,
    val startDate: Long = System.currentTimeMillis(),
    val colorIndex: Int = 0,
    val notes: String = "",
    val isArchived: Boolean = false,
) {
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("")
            .uppercase(TR)
}

// MARK: - Ders

@Entity(
    tableName = "lessons",
    indices = [Index("studentId"), Index("templateId"), Index("paymentId"), Index("date")],
)
data class Lesson(
    @PrimaryKey val id: String = newId(),
    val studentId: String? = null,
    /// Ders bir haftalık dersten (şablondan) üretildiyse kaynağı
    val templateId: String? = null,
    /// Ders toplu ödemeyle ödendiyse o ödeme. Dersin ücreti değişmez; bağ
    /// yalnızca "bu ders şu ödemeyle ödendi" bilgisini taşır.
    val paymentId: String? = null,
    val date: Long,
    /// Dakika cinsinden süre
    val duration: Int = 60,
    val status: String = LessonStatus.PLANNED.raw,
    val cancellationReason: String = CancellationReason.NONE.raw,
    val topic: String = "",
    val note: String = "",
    /// Saatlik ücret yerine derse özel sabit ücret
    val feeOverride: Double? = null,
    /// Ücret elle girildiyse true; false ise kayıt anındaki saatlik ücretten kilitlenmiştir
    val usesCustomFee: Boolean = false,
) {
    val lessonStatus: LessonStatus get() = LessonStatus.from(status)
    val reason: CancellationReason get() = CancellationReason.from(cancellationReason)
    val endDate: Long get() = date + duration * 60_000L

    val isPlanned: Boolean get() = status == LessonStatus.PLANNED.raw
    val isCompleted: Boolean get() = status == LessonStatus.COMPLETED.raw
    val isCancelled: Boolean get() = status == LessonStatus.CANCELLED.raw

    /// İptal dışındaki her durumda iptal sebebi temizlenir (iOS ile aynı kural).
    fun withStatus(newStatus: LessonStatus, newReason: CancellationReason = CancellationReason.NONE): Lesson =
        copy(
            status = newStatus.raw,
            cancellationReason = if (newStatus == LessonStatus.CANCELLED) newReason.raw else CancellationReason.NONE.raw,
        )

    companion object {
        fun standardFee(student: Student?, duration: Int): Double =
            duration / 60.0 * (student?.hourlyRate ?: 0.0)
    }
}

// MARK: - Ödeme

@Entity(tableName = "payments", indices = [Index("studentId"), Index("date")])
data class Payment(
    @PrimaryKey val id: String = newId(),
    val studentId: String? = null,
    val date: Long = System.currentTimeMillis(),
    val amount: Double = 0.0,
    val method: String = PaymentMethod.CASH.raw,
    val note: String = "",
) {
    val paymentMethod: PaymentMethod get() = PaymentMethod.from(method)
}

// MARK: - Ödev

@Entity(tableName = "homeworks", indices = [Index("studentId")])
data class Homework(
    @PrimaryKey val id: String = newId(),
    val studentId: String? = null,
    val title: String = "",
    val detail: String = "",
    val assignedDate: Long = System.currentTimeMillis(),
    val dueDate: Long = System.currentTimeMillis(),
    val isDone: Boolean = false,
    val doneDate: Long? = null,
) {
    /// Teslim tarihi geçti ve hâlâ yapılmadı
    fun isLate(now: Long = System.currentTimeMillis()): Boolean = !isDone && dueDate < startOfDay(now)
}

// MARK: - Haftalık ders (tekrarlayan ders şablonu)

@Entity(tableName = "templates", indices = [Index("studentId")])
data class LessonTemplate(
    @PrimaryKey val id: String = newId(),
    val studentId: String? = null,
    /// Calendar.weekday değeri (1 = Pazar ... 7 = Cumartesi)
    val weekday: Int = 3,
    val hour: Int = 17,
    val minute: Int = 0,
    /// Dakika cinsinden süre
    val duration: Int = 60,
    val feeOverride: Double? = null,
    /// Ücret elle girildiyse true; false ise üretilen derslerde o günkü saatlik ücret kilitlenir
    val usesCustomFee: Boolean = false,
    /// Duraklatılan şablon yeni ders üretmez
    val isPaused: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /// Bu tarihe kadar dersler üretildi; ileri tarihli üretim buradan devam eder
    val generatedUntil: Long? = null,
) {
    val weekdayName: String get() = weekdayName(weekday)
    val timeText: String get() = "%02d:%02d".format(hour, minute)

    companion object {
        /// UI'daki hafta sırası (Pazartesi başlangıçlı) → Calendar.weekday değerleri
        val weekdayOrder = listOf(2, 3, 4, 5, 6, 7, 1)

        private val names = mapOf(
            1 to "Pazar", 2 to "Pazartesi", 3 to "Salı", 4 to "Çarşamba",
            5 to "Perşembe", 6 to "Cuma", 7 to "Cumartesi",
        )

        fun weekdayName(weekday: Int): String = names[weekday] ?: "—"

        val shortNames = mapOf(2 to "Pzt", 3 to "Sal", 4 to "Çar", 5 to "Per", 6 to "Cum", 7 to "Cmt", 1 to "Paz")
    }
}
