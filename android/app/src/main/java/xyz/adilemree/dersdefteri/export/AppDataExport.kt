package xyz.adilemree.dersdefteri.export

import android.content.Context
import xyz.adilemree.dersdefteri.data.CancellationReason
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.TrCollator
import java.io.File

/// Defteri CSV dosyalarına aktarır (iOS ile aynı sütunlar). Excel'in Türkçe
/// karakterleri doğru okuması için dosyalar UTF-8 imzasıyla (BOM) yazılır.
object AppDataExport {
    fun makeCsvFiles(context: Context, notebook: Notebook): List<File> {
        val folder = File(File(context.cacheDir, "exports"), "DersDefteri-CSV-${Fmt.fileStamp()}").apply { mkdirs() }
        val files = listOf(
            "ogrenciler.csv" to students(notebook),
            "dersler.csv" to lessons(notebook),
            "odemeler.csv" to payments(notebook),
            "odevler.csv" to homeworks(notebook),
        )
        return files.mapNotNull { (name, content) ->
            runCatching {
                File(folder, name).apply { writeText("﻿" + content, Charsets.UTF_8) }
            }.getOrNull()
        }
    }

    private fun students(n: Notebook): String = rows(
        listOf(listOf("Ad Soyad", "Ders", "Sınıf", "Öğrenci Telefonu", "Veli", "Veli Telefonu", "Saatlik Ücret", "Ders Tutarı", "Ödenen", "Bakiye", "Arşiv")) +
            n.students.sortedWith(compareBy(TrCollator) { it.name }).map { s ->
                val stats = n.stats(s.id)
                listOf(
                    s.name, s.subject, s.grade, s.phone, s.parentName, s.parentPhone,
                    money(s.hourlyRate), money(stats.totalEarned), money(stats.totalPaid), money(stats.balance),
                    if (s.isArchived) "Evet" else "Hayır",
                )
            },
    )

    private fun lessons(n: Notebook): String = rows(
        listOf(listOf("Tarih", "Saat", "Öğrenci", "Ders", "Süre", "Durum", "İptal Sebebi", "Konu", "Not", "Ders Tutarı", "Toplu Ödeme")) +
            n.lessons.sortedBy { it.date }.map { l ->
                val student = n.studentOf(l)
                listOf(
                    Fmt.long(l.date), Fmt.time(l.date), student?.name.orEmpty(), student?.subject.orEmpty(),
                    "${l.duration}", l.lessonStatus.title,
                    if (l.reason == CancellationReason.NONE) "" else l.reason.title,
                    l.topic, l.note, money(n.fee(l)),
                    n.paymentOf(l)?.let { Fmt.long(it.date) }.orEmpty(),
                )
            },
    )

    private fun payments(n: Notebook): String = rows(
        listOf(listOf("Tarih", "Öğrenci", "Tutar", "Yöntem", "Not", "Kapsadığı Dersler")) +
            n.payments.sortedBy { it.date }.map { p ->
                listOf(
                    Fmt.long(p.date), n.student(p.studentId)?.name.orEmpty(), money(p.amount),
                    p.paymentMethod.title, p.note,
                    n.coveredLessons(p.id).joinToString(", ") { Fmt.dayMonthShort(it.date) },
                )
            },
    )

    private fun homeworks(n: Notebook): String = rows(
        listOf(listOf("Öğrenci", "Başlık", "Açıklama", "Veriliş", "Teslim", "Durum", "Tamamlanma")) +
            n.homeworks.sortedBy { it.dueDate }.map { h ->
                listOf(
                    n.student(h.studentId)?.name.orEmpty(), h.title, h.detail,
                    Fmt.long(h.assignedDate), Fmt.long(h.dueDate),
                    when {
                        h.isDone -> "Tamamlandı"
                        h.isLate() -> "Gecikti"
                        else -> "Bekliyor"
                    },
                    h.doneDate?.let { Fmt.long(it) }.orEmpty(),
                )
            },
    )

    private fun rows(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } } + "\n"

    private fun money(value: Double): String = String.format(java.util.Locale.US, "%.2f", value).replace(".", ",")
}
