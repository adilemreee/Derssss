package xyz.adilemree.dersdefteri.data

// Defterin o anki hâli. Ekranlar bunu okur; hesaplar (ücret, bakiye, toplu
// ödeme) iOS uygulamasındakiyle aynı kurallarla burada yapılır. Değişmez bir
// nesne olduğu için hesaplanan değerler önbelleğe alınabilir.

class Notebook(
    students: List<Student>,
    lessons: List<Lesson>,
    payments: List<Payment>,
    homeworks: List<Homework>,
    templates: List<LessonTemplate>,
) {
    /// Ada göre sıralı (Türkçe)
    val students: List<Student> = students.sortedWith(compareBy(TrCollator) { it.name })
    /// Tarihe göre sıralı
    val lessons: List<Lesson> = lessons.sortedBy { it.date }
    /// Tarihe göre sıralı
    val payments: List<Payment> = payments.sortedBy { it.date }
    /// Teslim tarihine göre sıralı
    val homeworks: List<Homework> = homeworks.sortedBy { it.dueDate }
    val templates: List<LessonTemplate> = templates.sortedBy { it.createdAt }

    val studentById: Map<String, Student> = this.students.associateBy { it.id }
    val lessonById: Map<String, Lesson> = this.lessons.associateBy { it.id }
    val paymentById: Map<String, Payment> = this.payments.associateBy { it.id }
    val homeworkById: Map<String, Homework> = this.homeworks.associateBy { it.id }
    val templateById: Map<String, LessonTemplate> = this.templates.associateBy { it.id }

    private val lessonsByStudent = this.lessons.groupBy { it.studentId }
    private val paymentsByStudent = this.payments.groupBy { it.studentId }
    private val homeworksByStudent = this.homeworks.groupBy { it.studentId }
    private val templatesByStudent = this.templates.groupBy { it.studentId }
    private val lessonsByPayment = this.lessons.filter { it.paymentId != null }.groupBy { it.paymentId }

    val isEmpty: Boolean get() = students.isEmpty()

    fun student(id: String?): Student? = id?.let { studentById[it] }
    fun studentOf(lesson: Lesson): Student? = student(lesson.studentId)

    fun fee(lesson: Lesson): Double =
        lesson.feeOverride ?: Lesson.standardFee(studentOf(lesson), lesson.duration)

    fun lessonsOf(studentId: String): List<Lesson> = lessonsByStudent[studentId].orEmpty()
    fun paymentsOf(studentId: String): List<Payment> = paymentsByStudent[studentId].orEmpty()
    fun homeworksOf(studentId: String): List<Homework> = homeworksByStudent[studentId].orEmpty()
    fun templatesOf(studentId: String): List<LessonTemplate> = templatesByStudent[studentId].orEmpty()

    fun templateOf(lesson: Lesson): LessonTemplate? = lesson.templateId?.let { templateById[it] }
    fun paymentOf(lesson: Lesson): Payment? = lesson.paymentId?.let { paymentById[it] }

    // MARK: Öğrenci hesapları

    data class Stats(
        val completedCount: Int,
        /// İşlenen derslerin toplam süresi (dakika)
        val totalMinutes: Int,
        /// İşlenen derslerin toplam tutarı
        val totalEarned: Double,
        val totalPaid: Double,
        val plannedCount: Int,
    ) {
        /// Pozitif = öğrencinin borcu var, negatif = avans ödemiş
        val balance: Double get() = totalEarned - totalPaid
    }

    private val statsCache = HashMap<String, Stats>()

    fun stats(studentId: String): Stats = synchronized(statsCache) {
        statsCache.getOrPut(studentId) {
            val lessons = lessonsOf(studentId)
            val completed = lessons.filter { it.isCompleted }
            Stats(
                completedCount = completed.size,
                totalMinutes = completed.sumOf { it.duration },
                totalEarned = completed.sumOf { fee(it) },
                totalPaid = paymentsOf(studentId).sumOf { it.amount },
                plannedCount = lessons.count { it.isPlanned },
            )
        }
    }

    fun balance(studentId: String): Double = stats(studentId).balance

    // MARK: Toplu ödeme

    /// Toplu ödemenin dersleri, tarihe göre.
    fun coveredLessons(paymentId: String): List<Lesson> = lessonsByPayment[paymentId].orEmpty()

    fun isBulk(paymentId: String): Boolean = coveredLessons(paymentId).isNotEmpty()

    /// "4 ders" gibi kısa açıklama
    fun bulkSummary(paymentId: String): String =
        "${coveredLessons(paymentId).count { !it.isCancelled }} ders"

    companion object {
        val EMPTY = Notebook(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/// Türkçe alfabe sırası (Ç, Ğ, İ, Ö, Ş, Ü doğru yerde).
object TrCollator : Comparator<String> {
    private val collator = java.text.Collator.getInstance(TR).apply { strength = java.text.Collator.SECONDARY }
    override fun compare(a: String, b: String): Int = synchronized(collator) { collator.compare(a, b) }
}

// MARK: - Dersin ödeme durumu

sealed interface LessonPayState {
    /// Toplu ödemeyle ödendi
    data class Bulk(val paymentId: String) : LessonPayState
    /// Derse bağlı olmayan ödemelerle (en eski dersten başlayarak) kapandı
    data object Paid : LessonPayState
    /// İşlendi, ödenmedi
    data object Unpaid : LessonPayState
    /// Planlı, henüz ödenmedi (borç değil)
    data object Open : LessonPayState
}

/// Öğrencinin derslerinden hangisinin ödendiğini hesaplar.
///
/// Toplu ödemeye bağlı dersler o ödemeyle ödenmiştir. Kalan para (derse
/// bağlı olmayan ödemeler ve toplu ödemenin artan kısmı) en eski işlenmiş
/// dersten başlayarak dersleri kapatır, sonra sıradaki planlı dersleri (avans).
class PaymentLedger(notebook: Notebook, studentId: String, excludingPaymentId: String? = null) {
    private val states = HashMap<String, LessonPayState>()

    /// Toplu ödemeye eklenebilecek dersler, tarihe göre
    val candidates: List<Lesson>

    init {
        val active = notebook.lessonsOf(studentId).filter { !it.isCancelled }
        var pool = notebook.paymentsOf(studentId)
            .filter { it.id != excludingPaymentId }
            .sumOf { it.amount }

        val unlinked = mutableListOf<Lesson>()
        for (lesson in active) {
            val payment = notebook.paymentOf(lesson)
            if (payment != null && payment.id != excludingPaymentId && payment.studentId == studentId) {
                states[lesson.id] = LessonPayState.Bulk(payment.id)
                pool -= notebook.fee(lesson)
            } else {
                unlinked += lesson
            }
        }

        val ordered = unlinked.filter { it.isCompleted }.sortedBy { it.date } +
            unlinked.filter { it.isPlanned }.sortedBy { it.date }
        val found = mutableListOf<Lesson>()
        var covering = true
        for (lesson in ordered) {
            val fee = notebook.fee(lesson)
            if (covering && pool + 0.5 >= fee) {
                pool -= fee
                states[lesson.id] = LessonPayState.Paid
            } else {
                covering = false
                states[lesson.id] = if (lesson.isCompleted) LessonPayState.Unpaid else LessonPayState.Open
                if (fee > 0.005) found += lesson
            }
        }
        candidates = found.sortedBy { it.date }
    }

    fun state(lesson: Lesson): LessonPayState? = states[lesson.id]
}
