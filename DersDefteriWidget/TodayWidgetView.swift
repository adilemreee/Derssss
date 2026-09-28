//
//  TodayWidgetView.swift
//  Ders Defteri Widget
//

import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Tema

enum WidgetTheme {
    static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.locale = Locale(identifier: "tr_TR")
        c.firstWeekday = 2
        return c
    }()

    static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255,
              green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255)
    }

    static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { trait in
            let hex = trait.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255,
                           green: CGFloat((hex >> 8) & 0xFF) / 255,
                           blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
        })
    }

    static let background = dynamic(light: 0xF7F2E7, dark: 0x1A1915)
    static let ink = dynamic(light: 0x26303E, dark: 0xEAE6DB)
    static let inkSoft = dynamic(light: 0x77808D, dark: 0x9C988D)
    static let accent = dynamic(light: 0x1E4B39, dark: 0x7FC79F)
    static let green = dynamic(light: 0x3E8E5F, dark: 0x5CB283)
    static let amber = dynamic(light: 0xDF9E3B, dark: 0xE6AF58)
    static let card = dynamic(light: 0xFFFFFF, dark: 0x262420)

    static func studentColor(_ index: Int) -> Color {
        let palette = WidgetStore.paletteHex
        return color(palette[abs(index) % palette.count])
    }

    static let time: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = "HH:mm"
        return f
    }()

    static let dayTitle: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = "d MMMM EEEE"
        return f
    }()

    static let weekdayShort: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = "EEE"
        return f
    }()

    static func dayLabel(_ date: Date, relativeTo now: Date) -> String {
        if calendar.isDate(date, inSameDayAs: now) { return "Bugün" }
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now),
           calendar.isDate(date, inSameDayAs: tomorrow) { return "Yarın" }
        return weekdayShort.string(from: date)
    }

    static func firstName(_ name: String) -> String {
        name.split(separator: " ").first.map(String.init) ?? name
    }
}

// MARK: - Ana görünüm

struct TodayWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TodayEntry

    var body: some View {
        switch family {
        case .systemSmall: SmallTodayView(entry: entry)
        case .systemMedium: ListTodayView(entry: entry, limit: 3)
        case .systemLarge: ListTodayView(entry: entry, limit: 7)
        case .accessoryRectangular: RectangularView(entry: entry)
        case .accessoryInline: InlineView(entry: entry)
        case .accessoryCircular: CircularView(entry: entry)
        default: SmallTodayView(entry: entry)
        }
    }
}

// MARK: - Küçük

private struct SmallTodayView: View {
    let entry: TodayEntry

    private var toMark: WidgetLesson? {
        entry.todayLessons.last { entry.needsMarking($0) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Text("Bugün")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(WidgetTheme.accent)
                Spacer(minLength: 0)
                Text("\(entry.todayLessons.count) ders")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(WidgetTheme.inkSoft)
            }

            Spacer(minLength: 0)

            if let lesson = toMark {
                // Biten ders öncelikli: işaretlenmeden bakiyeye yansımaz.
                Text("Ders bitti mi?")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(WidgetTheme.amber)
                LessonHeadline(lesson: lesson, entryDate: entry.date)
                Button(intent: MarkLessonDoneIntent(lessonID: lesson.id)) {
                    Label("İşlendi", systemImage: "checkmark")
                        .font(.caption.weight(.bold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(WidgetTheme.green)
                .controlSize(.small)
            } else if let next = entry.nextLesson {
                Text(entry.isOngoing(next) ? "Şu an" : WidgetTheme.dayLabel(next.date, relativeTo: entry.date))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(entry.isOngoing(next) ? WidgetTheme.green : WidgetTheme.inkSoft)
                LessonHeadline(lesson: next, entryDate: entry.date)
            } else {
                Image(systemName: "cup.and.saucer.fill")
                    .font(.title3)
                    .foregroundStyle(WidgetTheme.amber)
                Text("Planlı ders yok")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(WidgetTheme.ink)
            }
        }
    }
}

private struct LessonHeadline: View {
    let lesson: WidgetLesson
    let entryDate: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(WidgetTheme.time.string(from: lesson.date))
                .font(.system(.title, design: .serif).weight(.bold))
                .foregroundStyle(WidgetTheme.ink)
                .minimumScaleFactor(0.7)
            HStack(spacing: 5) {
                Circle()
                    .fill(WidgetTheme.studentColor(lesson.colorIndex))
                    .frame(width: 7, height: 7)
                Text(lesson.studentName)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(WidgetTheme.ink)
                    .lineLimit(1)
            }
            if !lesson.subject.isEmpty {
                Text("\(lesson.subject) · \(lesson.duration) dk")
                    .font(.caption2)
                    .foregroundStyle(WidgetTheme.inkSoft)
                    .lineLimit(1)
            }
        }
    }
}

// MARK: - Orta ve büyük: liste

private struct ListTodayView: View {
    let entry: TodayEntry
    let limit: Int

    /// Bugünün dersleri; bugün bittiyse yarının dersleri.
    private var dayLessons: (title: String, lessons: [WidgetLesson]) {
        let today = entry.todayLessons
        if today.contains(where: { !$0.isCompleted }) || today.contains(where: { entry.needsMarking($0) }) {
            return ("Bugün", today)
        }
        if let next = entry.nextLesson {
            let day = entry.snapshot.lessons.filter { WidgetTheme.calendar.isDate($0.date, inSameDayAs: next.date) }
            return (WidgetTheme.dayLabel(next.date, relativeTo: entry.date), day)
        }
        return ("Bugün", today)
    }

    var body: some View {
        let group = dayLessons
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(group.title)
                    .font(.system(.headline, design: .serif).weight(.bold))
                    .foregroundStyle(WidgetTheme.ink)
                Text(WidgetTheme.dayTitle.string(from: group.lessons.first?.date ?? entry.date))
                    .font(.caption2)
                    .foregroundStyle(WidgetTheme.inkSoft)
                    .lineLimit(1)
                Spacer(minLength: 0)
                if entry.unmarkedCount > 0 {
                    Label("\(entry.unmarkedCount)", systemImage: "exclamationmark.circle.fill")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(WidgetTheme.amber)
                }
            }

            if group.lessons.isEmpty {
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    Image(systemName: "cup.and.saucer.fill")
                        .foregroundStyle(WidgetTheme.amber)
                    Text("Önümüzdeki günlerde planlı ders yok")
                        .font(.subheadline)
                        .foregroundStyle(WidgetTheme.inkSoft)
                }
                Spacer(minLength: 0)
            } else {
                VStack(spacing: 5) {
                    ForEach(visible(group.lessons)) { lesson in
                        LessonRowView(lesson: lesson, entry: entry)
                    }
                }
                if group.lessons.count > limit {
                    Text("+\(group.lessons.count - limit) ders daha")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(WidgetTheme.inkSoft)
                }
                Spacer(minLength: 0)
            }
        }
    }

    /// Sığmayanlar varsa bitmiş derslerin işaretlenmişleri önce düşer.
    private func visible(_ lessons: [WidgetLesson]) -> [WidgetLesson] {
        guard lessons.count > limit else { return lessons }
        let open = lessons.filter { !($0.isCompleted && $0.endDate <= entry.date) }
        if open.count >= limit { return Array(open.prefix(limit)) }
        return Array(lessons.suffix(limit))
    }
}

private struct LessonRowView: View {
    let lesson: WidgetLesson
    let entry: TodayEntry

    var body: some View {
        HStack(spacing: 8) {
            Text(WidgetTheme.time.string(from: lesson.date))
                .font(.system(.subheadline, design: .serif).weight(.bold))
                .foregroundStyle(lesson.isCompleted ? WidgetTheme.inkSoft : WidgetTheme.ink)
                .frame(width: 44, alignment: .leading)
            RoundedRectangle(cornerRadius: 1.5)
                .fill(WidgetTheme.studentColor(lesson.colorIndex))
                .frame(width: 3, height: 22)
            VStack(alignment: .leading, spacing: 0) {
                Text(lesson.studentName)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(lesson.isCompleted ? WidgetTheme.inkSoft : WidgetTheme.ink)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(entry.isOngoing(lesson) ? WidgetTheme.green : WidgetTheme.inkSoft)
                    .lineLimit(1)
            }
            Spacer(minLength: 4)
            trailing
        }
    }

    private var subtitle: String {
        if entry.isOngoing(lesson) { return "Şu an · \(lesson.duration) dk" }
        return lesson.subject.isEmpty ? "\(lesson.duration) dk" : "\(lesson.subject) · \(lesson.duration) dk"
    }

    @ViewBuilder
    private var trailing: some View {
        if lesson.isCompleted {
            Image(systemName: "checkmark.circle.fill")
                .font(.title3)
                .foregroundStyle(WidgetTheme.green.opacity(0.55))
                .accessibilityLabel("İşlendi")
        } else if entry.needsMarking(lesson) {
            Button(intent: MarkLessonDoneIntent(lessonID: lesson.id)) {
                Image(systemName: "checkmark.circle")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(WidgetTheme.green)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(lesson.studentName) dersini işlendi yap")
        }
    }
}

// MARK: - Kilit ekranı

private struct RectangularView: View {
    let entry: TodayEntry

    var body: some View {
        if let next = entry.nextLesson {
            VStack(alignment: .leading, spacing: 1) {
                Text(entry.isOngoing(next) ? "Şu anki ders" : "\(WidgetTheme.dayLabel(next.date, relativeTo: entry.date)) · sıradaki")
                    .font(.caption2.weight(.semibold))
                    .widgetAccentable()
                Text("\(WidgetTheme.time.string(from: next.date)) \(next.studentName)")
                    .font(.headline)
                    .lineLimit(1)
                Text(next.subject.isEmpty ? "\(next.duration) dk" : "\(next.subject) · \(next.duration) dk")
                    .font(.caption)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(alignment: .leading, spacing: 1) {
                Text("Ders Defteri")
                    .font(.caption2.weight(.semibold))
                    .widgetAccentable()
                Text("Planlı ders yok")
                    .font(.headline)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct InlineView: View {
    let entry: TodayEntry

    var body: some View {
        if let next = entry.nextLesson {
            Text("\(WidgetTheme.time.string(from: next.date)) \(WidgetTheme.firstName(next.studentName))")
        } else {
            Text("Planlı ders yok")
        }
    }
}

private struct CircularView: View {
    let entry: TodayEntry

    private var remainingToday: Int {
        entry.todayLessons.filter { !$0.isCompleted && $0.endDate > entry.date }.count
    }

    var body: some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: 0) {
                Text("\(remainingToday)")
                    .font(.system(.title2, design: .serif).weight(.bold))
                Text("ders")
                    .font(.caption2)
            }
        }
        .accessibilityLabel("Bugün kalan \(remainingToday) ders")
    }
}

// MARK: - Önizlemeler

#Preview(as: .systemSmall) {
    DersDefteriWidget()
} timeline: {
    TodayEntry.preview
}

#Preview(as: .systemMedium) {
    DersDefteriWidget()
} timeline: {
    TodayEntry.preview
}

#Preview(as: .accessoryRectangular) {
    DersDefteriWidget()
} timeline: {
    TodayEntry.preview
}
