//
//  StudentReportPDF.swift
//  One — Ders Defteri
//
//  Veliye gönderilebilir PDF ders raporu.
//

import SwiftUI
import UIKit

// MARK: - Rapor verisi

/// Rapor içeriği, çizimden ayrı hesaplanır: hem görünüm hem PDF aynı veriyi
/// kullanır, ikisi arasında sapma olamaz.
struct StudentReportData {
    let studentName: String
    let subject: String
    let grade: String
    let teacherName: String
    let periodTitle: String
    let rangeText: String

    let completedCount: Int
    let cancelledCount: Int
    let totalMinutes: Int
    let earned: Double
    let balance: Double

    let lessons: [Row]
    let homeworks: [Row]

    struct Row: Identifiable {
        let id = UUID()
        let leading: String
        let title: String
        let trailing: String
    }

    init(student: Student, period: StudentSummaryPeriod, teacherName: String) {
        let interval = period.dateInterval
        let inRange = student.allLessons
            .filter { $0.date >= interval.start && $0.date < interval.end }
            .sorted { $0.date < $1.date }
        let completed = inRange.filter { $0.status == .completed }

        studentName = student.name
        subject = student.subject
        grade = student.grade
        self.teacherName = teacherName
        periodTitle = period.title
        rangeText = "\(Fmt.long.string(from: interval.start)) – \(Fmt.long.string(from: interval.end.adding(days: -1)))"

        completedCount = completed.count
        cancelledCount = inRange.filter { $0.status == .cancelled }.count
        totalMinutes = completed.reduce(0) { $0 + $1.duration }
        earned = completed.reduce(0.0) { $0 + $1.fee }
        balance = student.balance

        // Rapor bir sayfada okunabilir kalsın diye satır sayısı sınırlanır.
        lessons = inRange.prefix(18).map { lesson in
            let topic = lesson.topic.isEmpty
                ? (student.subject.isEmpty ? "—" : student.subject)
                : lesson.topic
            return Row(
                leading: "\(Fmt.dayMonthShort.string(from: lesson.date)) \(Fmt.time.string(from: lesson.date))",
                title: topic,
                trailing: lesson.status.title
            )
        }

        homeworks = student.allHomeworks
            .filter { $0.assignedDate < interval.end && ($0.dueDate >= interval.start || !$0.isDone) }
            .sorted { $0.dueDate < $1.dueDate }
            .prefix(12)
            .map { homework in
                Row(
                    leading: Fmt.dayMonthShort.string(from: homework.dueDate),
                    title: homework.title,
                    trailing: homework.isDone ? "Tamamlandı" : (homework.isLate ? "Gecikti" : "Bekliyor")
                )
            }
    }

    var balanceText: String {
        if balance > 0.5 { return "\(Fmt.money(balance)) borç" }
        if balance < -0.5 { return "\(Fmt.money(-balance)) avans" }
        return "Bakiye kapalı"
    }

    var fileName: String {
        let safeName = studentName
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "-")
        let stamp = Fmt.fileStamp.string(from: Date())
        return "\(safeName.isEmpty ? "ogrenci" : safeName)-rapor-\(stamp).pdf"
    }
}

// MARK: - Rapor sayfası

/// Belge her zaman açık zeminli çizilir; koyu moddaki bir cihazdan üretilen
/// PDF'in siyah sayfa olarak yazdırılmaması için tema renkleri kullanılmaz.
private enum Paper {
    static let ink = Color(hex: 0x26303E)
    static let soft = Color(hex: 0x77808D)
    static let board = Color(hex: 0x1E4B39)
    static let line = Color(hex: 0x26303E).opacity(0.12)
    static let tint = Color(hex: 0x1E4B39).opacity(0.06)
    static let background = Color.white
}

struct StudentReportPage: View {
    let data: StudentReportData

    /// A4 genişliği (72 dpi punto).
    static let pageWidth: CGFloat = 595

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            header
            statsRow
            if !data.lessons.isEmpty {
                section(title: "Dersler", rows: data.lessons)
            }
            if !data.homeworks.isEmpty {
                section(title: "Ödevler", rows: data.homeworks)
            }
            footer
        }
        .padding(36)
        .frame(width: Self.pageWidth, alignment: .topLeading)
        .background(Paper.background)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(data.studentName)
                        .font(.system(size: 26, weight: .bold, design: .serif))
                        .foregroundStyle(Paper.ink)
                    Text([data.subject, data.grade].filter { !$0.isEmpty }.joined(separator: " • "))
                        .font(.system(size: 12))
                        .foregroundStyle(Paper.soft)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text("\(data.periodTitle) Ders Raporu")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Paper.board)
                    Text(data.rangeText)
                        .font(.system(size: 11))
                        .foregroundStyle(Paper.soft)
                }
            }
            Rectangle()
                .fill(Paper.board)
                .frame(height: 2)
        }
    }

    private var statsRow: some View {
        HStack(spacing: 10) {
            stat("İşlenen ders", "\(data.completedCount)")
            stat("Toplam süre", Fmt.hours(data.totalMinutes))
            stat("Ders tutarı", Fmt.money(data.earned))
            stat("Bakiye", data.balanceText)
        }
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(.system(size: 10))
                .foregroundStyle(Paper.soft)
            Text(value)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Paper.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(RoundedRectangle(cornerRadius: 8).fill(Paper.tint))
    }

    private func section(title: String, rows: [StudentReportData.Row]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: 14, weight: .bold, design: .serif))
                .foregroundStyle(Paper.ink)
                .padding(.bottom, 8)

            ForEach(rows) { row in
                HStack(alignment: .top, spacing: 12) {
                    Text(row.leading)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Paper.soft)
                        .frame(width: 92, alignment: .leading)
                    Text(row.title)
                        .font(.system(size: 12))
                        .foregroundStyle(Paper.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(row.trailing)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Paper.soft)
                        .frame(width: 78, alignment: .trailing)
                }
                .padding(.vertical, 6)
                Rectangle().fill(Paper.line).frame(height: 0.5)
            }
        }
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 3) {
            if data.cancelledCount > 0 {
                Text("Dönem içinde \(data.cancelledCount) ders iptal edildi.")
                    .font(.system(size: 10))
                    .foregroundStyle(Paper.soft)
            }
            Text(data.teacherName.isEmpty
                 ? "Ders Defteri ile hazırlandı."
                 : "\(data.teacherName) • Ders Defteri ile hazırlandı.")
                .font(.system(size: 10))
                .foregroundStyle(Paper.soft)
        }
        .padding(.top, 4)
    }
}

// MARK: - PDF üretimi

enum StudentReportPDF {
    /// Raporu PDF olarak geçici dizine yazar ve dosya adresini döndürür.
    ///
    /// Sayfa yüksekliği içeriğe göre belirlenir. Sabit A4 yüksekliği
    /// kullanılsaydı uzun bir rapor sessizce kırpılırdı; içerik kadar uzun tek
    /// sayfa, hiçbir satırın kaybolmamasını garanti eder.
    @MainActor
    static func make(data: StudentReportData) -> URL? {
        let renderer = ImageRenderer(content: StudentReportPage(data: data))
        renderer.scale = 2

        let url = FileManager.default.temporaryDirectory.appending(path: data.fileName)
        var result: URL?

        renderer.render { size, renderInContext in
            var box = CGRect(origin: .zero, size: size)
            guard let consumer = CGDataConsumer(url: url as CFURL),
                  let context = CGContext(consumer: consumer, mediaBox: &box, nil) else { return }

            context.beginPDFPage(nil)
            renderInContext(context)
            context.endPDFPage()
            context.closePDF()
            result = url
        }

        return result
    }
}
