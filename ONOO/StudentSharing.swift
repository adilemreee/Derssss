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
    case all = "Tüm zamanlar"

    var id: String { rawValue }

    var title: String { rawValue }

    /// Başlıklarda: "Haftalık Ders Özeti", "Tüm Zamanlar Ders Özeti".
    var headline: String {
        self == .all ? "Tüm Zamanlar" : rawValue
    }

    /// Tüm zamanlar: öğrencinin başlangıcından (ya da ilk dersinden) bugünün
    /// sonuna kadar. İleri tarihli planlı dersler dahil edilmez; özet
    /// yapılanı anlatır.
    func dateInterval(for student: Student) -> DateInterval {
        switch self {
        case .week:
            let start = Date().startOfWeek
            return DateInterval(start: start, end: start.adding(days: 7))
        case .month:
            let start = Date().startOfMonth
            return DateInterval(start: start, end: start.adding(months: 1))
        case .all:
            let firstLesson = student.allLessons.map(\.date).min() ?? student.startDate
            let start = min(student.startDate, firstLesson).startOfDay
            // İleri tarihli ama işaretlenmiş (işlendi/iptal) ders de sayılır.
            let lastMarked = student.allLessons.filter { $0.status != .planned }.map(\.date).max()
            let end = max(Date().startOfDay.adding(days: 1),
                          (lastMarked ?? start).startOfDay.adding(days: 1))
            return DateInterval(start: start, end: end)
        }
    }

    /// Uzun listelerde hangi dersler gösterilir: kısa dönemde ilkler, tüm
    /// zamanlarda en yeniler.
    func visible<T>(_ items: [T], limit: Int) -> (items: [T], hidden: Int) {
        guard items.count > limit else { return (items, 0) }
        return self == .all
            ? (Array(items.suffix(limit)), items.count - limit)
            : (Array(items.prefix(limit)), items.count - limit)
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
        let interval = period.dateInterval(for: student)
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
        lines.append("\(student.name) - \(period.headline) Ders Özeti")
        lines.append("\(Fmt.long.string(from: interval.start)) - \(Fmt.long.string(from: interval.end.adding(days: -1)))")
        lines.append("")
        lines.append("Genel durum")
        lines.append("• İşlenen ders: \(completed.count)")
        lines.append("• Toplam süre: \(Fmt.hours(minutes))")
        lines.append("• İşlenen ders tutarı: \(Fmt.money(earned))")
        if period == .all {
            let cancelled = lessons.filter { $0.status == .cancelled }.count
            if cancelled > 0 { lines.append("• İptal edilen ders: \(cancelled)") }
            lines.append("• Toplam ödenen: \(Fmt.money(student.totalPaid))")
        }
        lines.append("• Güncel bakiye: \(balanceText(for: student))")
        if let remaining = student.packageLessonsRemaining {
            lines.append(remaining > 0
                         ? "• Ders paketi: \(remaining) ders kaldı"
                         : "• Ders paketi bitti")
        }

        if !lessons.isEmpty {
            lines.append("")
            lines.append(period == .all ? "Son dersler" : "Dersler")
            let shown = period.visible(lessons, limit: 12)
            for lesson in shown.items {
                let topic = lesson.topic.isEmpty ? (student.subject.isEmpty ? "Konu belirtilmedi" : student.subject) : lesson.topic
                // Tüm zamanlarda dersler farklı yıllardan olabilir.
                let day = period == .all ? Fmt.dayMonthYearShort(lesson.date) : Fmt.dayMonthShort.string(from: lesson.date)
                lines.append("• \(day) \(Fmt.time.string(from: lesson.date)) - \(lesson.status.title) - \(topic)")
            }
            if shown.hidden > 0 {
                lines.append(period == .all ? "• ve daha önceki \(shown.hidden) ders" : "• +\(shown.hidden) ders daha")
            }
        }

        if !homeworks.isEmpty {
            lines.append("")
            lines.append("Ödevler")
            let shownHomeworks = period.visible(homeworks, limit: 8)
            for homework in shownHomeworks.items {
                let status = homework.isDone ? "tamamlandı" : (homework.isLate ? "gecikti" : "bekliyor")
                lines.append("• \(homework.title) - \(status), son: \(Fmt.dayMonthShort.string(from: homework.dueDate))")
            }
            if shownHomeworks.hidden > 0 {
                lines.append("• +\(shownHomeworks.hidden) ödev daha")
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
