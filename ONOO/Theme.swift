//
//  Theme.swift
//  One — Ders Defteri
//
//  Tasarım sistemi: renkler, tipografi, formatlayıcılar ve ortak bileşenler.
//

import SwiftUI

// MARK: - Renkler

extension Color {
    init(hex: UInt) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: 1)
    }
}

extension UIColor {
    convenience init(hex: UInt) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255,
                  green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255,
                  alpha: 1)
    }
}

enum Theme {
    /// Açık/koyu moda göre otomatik renk
    static func dynamic(light: UInt, dark: UInt) -> Color {
        Color(uiColor: UIColor { trait in
            trait.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }

    /// Kara tahta yeşili — ana marka rengi
    static let board = dynamic(light: 0x1E4B39, dark: 0x2F6C51)
    static let boardDark = dynamic(light: 0x143528, dark: 0x224E3B)
    /// Kağıt/krem zemin — koyu modda gece defteri
    static let paper = dynamic(light: 0xF7F2E7, dark: 0x1A1915)
    /// Kart yüzeyi
    static let card = dynamic(light: 0xFFFFFF, dark: 0x262420)
    static let ink = Color(uiColor: inkUI)
    static let inkSoft = dynamic(light: 0x77808D, dark: 0x9C988D)
    static let amber = dynamic(light: 0xDF9E3B, dark: 0xE6AF58)
    static let red = dynamic(light: 0xC3503E, dark: 0xDE705F)
    static let blue = dynamic(light: 0x3C66AE, dark: 0x7397D0)
    static let green = dynamic(light: 0x3E8E5F, dark: 0x5CB283)
    static let line = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(hex: 0xEAE6DB).withAlphaComponent(0.10)
            : UIColor(hex: 0x26303E).withAlphaComponent(0.08)
    })

    /// Navigasyon başlıkları gibi UIKit tarafı için dinamik mürekkep
    static let inkUI = UIColor { trait in
        trait.userInterfaceStyle == .dark ? UIColor(hex: 0xEAE6DB) : UIColor(hex: 0x26303E)
    }

    /// Öğrenci renk paleti
    static let palette: [Color] = [
        Color(hex: 0x3C66AE), Color(hex: 0xC3503E), Color(hex: 0x3E8E5F),
        Color(hex: 0xDF9E3B), Color(hex: 0x7B5CB8), Color(hex: 0x2E8F9E),
        Color(hex: 0xC85C8E), Color(hex: 0x8A6D3B)
    ]
}

// MARK: - Formatlayıcılar

enum Fmt {
    static let currency: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.locale = Locale(identifier: "tr_TR")
        f.maximumFractionDigits = 0
        return f
    }()

    static func money(_ value: Double) -> String {
        currency.string(from: NSNumber(value: value)) ?? "₺0"
    }

    static func hours(_ minutes: Int) -> String {
        let h = Double(minutes) / 60
        if h == h.rounded() { return "\(Int(h)) sa" }
        return String(format: "%.1f sa", h).replacingOccurrences(of: ".", with: ",")
    }

    private static func make(_ format: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = format
        return f
    }

    static let time = make("HH:mm")
    static let dayMonth = make("d MMMM")
    static let dayMonthShort = make("d MMM")
    static let weekday = make("EEEE")
    static let weekdayShort = make("EEE")
    static let long = make("d MMMM yyyy")
    static let monthYear = make("MMMM yyyy")
    static let monthShort = make("MMM")
    /// Dışa aktarılan dosya adlarında kullanılır
    static let fileStamp = make("yyyyMMdd-HHmm")

    /// Sunucu tarihleri. Bunlar kullanıcıya gösterilmez, ağda taşınır; bu yüzden
    /// yerel takvim değil sabit ISO-8601 kullanılır.
    static let iso8601Fractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    static let iso8601Plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
}

// MARK: - Takvim & Tarih

extension Calendar {
    /// Pazartesi ile başlayan Türkçe takvim
    static let tr: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.locale = Locale(identifier: "tr_TR")
        c.firstWeekday = 2
        return c
    }()
}

extension Date {
    var startOfDay: Date { Calendar.tr.startOfDay(for: self) }
    var startOfWeek: Date { Calendar.tr.dateInterval(of: .weekOfYear, for: self)?.start ?? self }
    var startOfMonth: Date { Calendar.tr.dateInterval(of: .month, for: self)?.start ?? self }
    func adding(days: Int) -> Date { Calendar.tr.date(byAdding: .day, value: days, to: self) ?? self }
    func adding(months: Int) -> Date { Calendar.tr.date(byAdding: .month, value: months, to: self) ?? self }
    var isToday: Bool { Calendar.tr.isDateInToday(self) }
    func isSameDay(as other: Date) -> Bool { Calendar.tr.isDate(self, inSameDayAs: other) }
}

// MARK: - Kart stili

extension View {
    func card(_ padding: CGFloat = 16) -> some View {
        self
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Theme.card)
                    .shadow(color: .black.opacity(0.05), radius: 10, y: 4)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Theme.line, lineWidth: 1)
            )
    }
}

// MARK: - Kara tahta paneli

struct Chalkboard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(LinearGradient(colors: [Theme.board, Theme.boardDark],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                    .shadow(color: Theme.boardDark.opacity(0.35), radius: 12, y: 6)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .stroke(Color.white.opacity(0.22), style: StrokeStyle(lineWidth: 1.5, dash: [7, 7]))
                    .padding(7)
            )
    }
}

// MARK: - Bölüm başlığı

struct SectionHeader: View {
    let title: String
    var systemImage: String? = nil

    var body: some View {
        HStack(spacing: 8) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.amber)
            }
            Text(title)
                .font(.title3.weight(.bold))
                .fontDesign(.serif)
                .foregroundStyle(Theme.ink)
            Spacer()
        }
    }
}

// MARK: - İstatistik kartı

struct StatCard: View {
    let icon: String
    let title: String
    let value: String
    var tint: Color = Theme.board

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 34, height: 34)
                .background(Circle().fill(tint.opacity(0.12)))
            Text(value)
                .font(.title2.weight(.bold))
                .fontDesign(.serif)
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.55)
            Text(title)
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}

// MARK: - Etiket / rozet

struct Chip: View {
    let text: String
    var tint: Color = Theme.inkSoft
    var filled: Bool = false

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            // Etiket dar bir satırda sıkışınca kelimeyi ortadan bölmemeli;
            // yanındaki esnek metin (ör. konu) kısalsın, etiket bütün kalsın.
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(Capsule().fill(filled ? tint : tint.opacity(0.13)))
            .foregroundStyle(filled ? Color.white : tint)
    }
}

struct StatusChip: View {
    let status: LessonStatus

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: status.icon)
                .font(.system(size: 9, weight: .bold))
            Text(status.title)
                .font(.caption2.weight(.bold))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Capsule().fill(status.tint.opacity(0.14)))
        .foregroundStyle(status.tint)
    }
}

struct BalanceBadge: View {
    let balance: Double
    var earned: Double = 0
    var paid: Double = 0
    var plannedCount: Int = 0

    var body: some View {
        if balance > 0.5 {
            Chip(text: "\(Fmt.money(balance)) borç", tint: Theme.red)
        } else if balance < -0.5 {
            Chip(text: "\(Fmt.money(-balance)) avans", tint: Theme.blue)
        } else if earned <= 0.5 && paid <= 0.5 && plannedCount > 0 {
            Chip(text: "\(plannedCount) planlı ders", tint: Theme.amber)
        } else if earned <= 0.5 && paid <= 0.5 {
            Chip(text: "Borç yok", tint: Theme.inkSoft)
        } else {
            Chip(text: "Ödendi ✓", tint: Theme.green)
        }
    }
}

// MARK: - Öğrenci avatarı

struct StudentAvatar: View {
    let student: Student
    var size: CGFloat = 44

    var body: some View {
        Circle()
            .fill(LinearGradient(colors: [student.color, student.color.opacity(0.75)],
                                 startPoint: .topLeading, endPoint: .bottomTrailing))
            .frame(width: size, height: size)
            .overlay(
                Text(student.initials)
                    .font(.system(size: size * 0.4, weight: .bold, design: .serif))
                    .foregroundStyle(.white)
            )
    }
}

// MARK: - Boş durum

struct EmptyStateView: View {
    let icon: String
    let title: String
    var message: String = ""

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 30))
                .foregroundStyle(Theme.inkSoft.opacity(0.7))
            Text(title)
                .font(.headline)
                .fontDesign(.serif)
                .foregroundStyle(Theme.ink)
            if !message.isEmpty {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 28)
        .padding(.horizontal, 16)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Theme.inkSoft.opacity(0.35), style: StrokeStyle(lineWidth: 1.2, dash: [6, 6]))
        )
    }
}

// MARK: - Ders satırı (ortak)

struct LessonRow: View {
    let lesson: Lesson
    var showDate: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 2) {
                Text(showDate ? Fmt.dayMonthShort.string(from: lesson.date) : Fmt.time.string(from: lesson.date))
                    .font(.subheadline.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
                Text(showDate ? Fmt.time.string(from: lesson.date) : "\(lesson.duration) dk")
                    .font(.caption2)
                    .foregroundStyle(Theme.inkSoft)
            }
            .frame(width: 54)

            RoundedRectangle(cornerRadius: 2)
                .fill(lesson.student?.color ?? Theme.inkSoft)
                .frame(width: 3, height: 34)

            VStack(alignment: .leading, spacing: 2) {
                Text(lesson.student?.name ?? "—")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 4) {
                Text(Fmt.money(lesson.fee))
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.ink)
                StatusChip(status: lesson.status)
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.card))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.line, lineWidth: 1))
    }

    private var subtitle: String {
        let subject = lesson.student?.subject ?? ""
        if lesson.status == .cancelled && lesson.cancellationReason != .none {
            return subject.isEmpty ? lesson.cancellationReason.title : "\(subject) • \(lesson.cancellationReason.shortTitle)"
        }
        if lesson.topic.isEmpty { return subject }
        return subject.isEmpty ? lesson.topic : "\(subject) • \(lesson.topic)"
    }
}
