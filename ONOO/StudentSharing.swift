//
//  StudentSharing.swift
//  One — Ders Defteri
//
//  Öğrenci iletişimi, ödeme hatırlatma ve paylaşılabilir özet metinleri.
//

import Foundation

enum StudentSummaryPeriod: String, CaseIterable, Identifiable {
    case week = "Haftalık"
    case month = "Aylık"

    var id: String { rawValue }

    var title: String { rawValue }

    var dateInterval: DateInterval {
        switch self {
        case .week:
            let start = Date().startOfWeek
            return DateInterval(start: start, end: start.adding(days: 7))
        case .month:
            let start = Date().startOfMonth
            return DateInterval(start: start, end: start.adding(months: 1))
        }
    }
}

enum StudentSharing {
    static func dialURL(for rawPhone: String) -> URL? {
        let phone = cleanPhone(rawPhone)
        guard !phone.isEmpty else { return nil }
        return URL(string: "tel://\(phone)")
    }

    static func smsURL(for rawPhone: String, text: String? = nil) -> URL? {
        let phone = cleanPhone(rawPhone)
        guard !phone.isEmpty else { return nil }
        if let text, !text.isEmpty {
            return URL(string: "sms:\(phone)&body=\(encoded(text))")
        }
        return URL(string: "sms:\(phone)")
    }

    static func whatsappURL(for rawPhone: String, text: String) -> URL? {
        guard let phone = whatsappPhone(rawPhone) else { return nil }
        return URL(string: "whatsapp://send?phone=\(phone)&text=\(encoded(text))")
    }

    static func paymentReminder(for student: Student) -> String {
        let month = Fmt.monthYear.string(from: Date()).capitalized(with: Locale(identifier: "tr_TR"))
        let balance = Fmt.money(max(student.balance, 0))
        return """
        Merhaba, \(month) ders bakiyeniz \(balance) görünüyor.

        Müsait olduğunuzda ödeme bilgisini paylaşabilir misiniz? Teşekkür ederim.
        """
    }

    static func summary(for student: Student, period: StudentSummaryPeriod) -> String {
        let interval = period.dateInterval
        let lessons = student.allLessons
            .filter { $0.date >= interval.start && $0.date < interval.end }
            .sorted { $0.date < $1.date }
        let homeworks = student.allHomeworks
            .filter { $0.assignedDate < interval.end && ($0.dueDate >= interval.start || !$0.isDone) }
            .sorted { $0.dueDate < $1.dueDate }
        let completed = lessons.filter { $0.status == .completed }
        let minutes = completed.reduce(0) { $0 + $1.duration }
        let earned = completed.reduce(0.0) { $0 + $1.fee }

        var lines: [String] = []
        lines.append("\(student.name) - \(period.title) Ders Özeti")
        lines.append("\(Fmt.long.string(from: interval.start)) - \(Fmt.long.string(from: interval.end.adding(days: -1)))")
        lines.append("")
        lines.append("Genel durum")
        lines.append("• İşlenen ders: \(completed.count)")
        lines.append("• Toplam süre: \(Fmt.hours(minutes))")
        lines.append("• İşlenen ders tutarı: \(Fmt.money(earned))")
        lines.append("• Güncel bakiye: \(balanceText(for: student))")

        if !lessons.isEmpty {
            lines.append("")
            lines.append("Dersler")
            for lesson in lessons.prefix(12) {
                let topic = lesson.topic.isEmpty ? (student.subject.isEmpty ? "Konu belirtilmedi" : student.subject) : lesson.topic
                lines.append("• \(Fmt.dayMonthShort.string(from: lesson.date)) \(Fmt.time.string(from: lesson.date)) - \(lesson.status.title) - \(topic)")
            }
            if lessons.count > 12 {
                lines.append("• +\(lessons.count - 12) ders daha")
            }
        }

        if !homeworks.isEmpty {
            lines.append("")
            lines.append("Ödevler")
            for homework in homeworks.prefix(8) {
                let status = homework.isDone ? "tamamlandı" : (homework.isLate ? "gecikti" : "bekliyor")
                lines.append("• \(homework.title) - \(status), son: \(Fmt.dayMonthShort.string(from: homework.dueDate))")
            }
            if homeworks.count > 8 {
                lines.append("• +\(homeworks.count - 8) ödev daha")
            }
        }

        return lines.joined(separator: "\n")
    }

    static func balanceText(for student: Student) -> String {
        if student.balance > 0.5 {
            return "\(Fmt.money(student.balance)) borç"
        }
        if student.balance < -0.5 {
            return "\(Fmt.money(-student.balance)) avans"
        }
        if student.totalEarned <= 0.5 && student.totalPaid <= 0.5 {
            let planned = student.allLessons.filter { $0.status == .planned }.count
            return planned > 0 ? "\(planned) planlı ders, borç yok" : "borç yok"
        }
        return "ödendi"
    }

    static func bestContactPhone(for student: Student) -> String {
        if !student.parentPhone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return student.parentPhone
        }
        return student.phone
    }

    static func cleanPhone(_ rawPhone: String) -> String {
        rawPhone.filter(\.isNumber)
    }

    private static func whatsappPhone(_ rawPhone: String) -> String? {
        let digits = cleanPhone(rawPhone)
        guard !digits.isEmpty else { return nil }
        if digits.hasPrefix("90") { return digits }
        if digits.hasPrefix("0"), digits.count == 11 {
            return "90" + String(digits.dropFirst())
        }
        if digits.count == 10 {
            return "90" + digits
        }
        return digits
    }

    private static func encoded(_ text: String) -> String {
        text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text
    }
}
