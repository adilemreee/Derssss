//
//  AppDataExport.swift
//  One — Ders Defteri
//
//  Uygulama verilerini CSV dosyalarına aktarır.
//

import Foundation

enum AppDataExport {
    static func makeCSVFiles(students: [Student],
                             lessons: [Lesson],
                             payments: [Payment],
                             homeworks: [Homework]) -> [URL] {
        let folderName = "DersDefteri-CSV-\(exportStamp())"
        let folder = FileManager.default.temporaryDirectory.appending(path: folderName, directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)

        let files: [(String, String)] = [
            ("ogrenciler.csv", studentsCSV(students)),
            ("dersler.csv", lessonsCSV(lessons)),
            ("odemeler.csv", paymentsCSV(payments)),
            ("odevler.csv", homeworksCSV(homeworks))
        ]

        return files.compactMap { name, content in
            let url = folder.appending(path: name)
            do {
                try content.write(to: url, atomically: true, encoding: .utf8)
                return url
            } catch {
                return nil
            }
        }
    }

    private static func studentsCSV(_ students: [Student]) -> String {
        rows([
            ["Ad Soyad", "Ders", "Sınıf", "Öğrenci Telefonu", "Veli", "Veli Telefonu", "Saatlik Ücret", "Ders Tutarı", "Ödenen", "Bakiye", "Arşiv"]
        ] + students.sorted { $0.name < $1.name }.map { student in
            [
                student.name,
                student.subject,
                student.grade,
                student.phone,
                student.parentName,
                student.parentPhone,
                moneyValue(student.hourlyRate),
                moneyValue(student.totalEarned),
                moneyValue(student.totalPaid),
                moneyValue(student.balance),
                student.isArchived ? "Evet" : "Hayır"
            ]
        })
    }

    private static func lessonsCSV(_ lessons: [Lesson]) -> String {
        rows([
            ["Tarih", "Saat", "Öğrenci", "Ders", "Süre", "Durum", "İptal Sebebi", "Konu", "Not", "Ders Tutarı"]
        ] + lessons.sorted { $0.date < $1.date }.map { lesson in
            [
                Fmt.long.string(from: lesson.date),
                Fmt.time.string(from: lesson.date),
                lesson.student?.name ?? "",
                lesson.student?.subject ?? "",
                "\(lesson.duration)",
                lesson.status.title,
                lesson.cancellationReason == .none ? "" : lesson.cancellationReason.title,
                lesson.topic,
                lesson.note,
                moneyValue(lesson.fee)
            ]
        })
    }

    private static func paymentsCSV(_ payments: [Payment]) -> String {
        rows([
            ["Tarih", "Öğrenci", "Tutar", "Yöntem", "Not"]
        ] + payments.sorted { $0.date < $1.date }.map { payment in
            [
                Fmt.long.string(from: payment.date),
                payment.student?.name ?? "",
                moneyValue(payment.amount),
                payment.method.title,
                payment.note
            ]
        })
    }

    private static func homeworksCSV(_ homeworks: [Homework]) -> String {
        rows([
            ["Öğrenci", "Başlık", "Açıklama", "Veriliş", "Teslim", "Durum", "Tamamlanma"]
        ] + homeworks.sorted { $0.dueDate < $1.dueDate }.map { homework in
            [
                homework.student?.name ?? "",
                homework.title,
                homework.detail,
                Fmt.long.string(from: homework.assignedDate),
                Fmt.long.string(from: homework.dueDate),
                homework.isDone ? "Tamamlandı" : (homework.isLate ? "Gecikti" : "Bekliyor"),
                homework.doneDate.map { Fmt.long.string(from: $0) } ?? ""
            ]
        })
    }

    private static func rows(_ rows: [[String]]) -> String {
        rows.map { row in
            row.map(csvEscape).joined(separator: ",")
        }
        .joined(separator: "\n") + "\n"
    }

    /// `map(csvEscape)` bunu ana iş parçacığından bağımsız bir işlev değeri
    /// olarak geçirdiği için yalıtımdan muaf tutulur.
    nonisolated private static func csvEscape(_ value: String) -> String {
        let escaped = value.replacingOccurrences(of: "\"", with: "\"\"")
        return "\"\(escaped)\""
    }

    private static func moneyValue(_ value: Double) -> String {
        String(format: "%.2f", value).replacingOccurrences(of: ".", with: ",")
    }

    private static func exportStamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.dateFormat = "yyyyMMdd-HHmm"
        return formatter.string(from: Date())
    }
}
