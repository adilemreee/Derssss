//
//  DersDefteriWidget.swift
//  Ders Defteri Widget
//
//  Ana ekran ve kilit ekranı: bugünün dersleri, sıradaki ders ve biten
//  derslerde uygulamayı açmadan "İşlendi" düğmesi.
//

import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Zaman çizelgesi

struct TodayEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot

    private var calendar: Calendar { WidgetTheme.calendar }

    var todayLessons: [WidgetLesson] {
        snapshot.lessons.filter { calendar.isDate($0.date, inSameDayAs: date) }
    }

    /// Şu an süren ya da sıradaki planlı ders
    var nextLesson: WidgetLesson? {
        snapshot.lessons.first { !$0.isCompleted && $0.endDate > date }
    }

    /// Saati geçmiş, işaretlenmemiş dersler (dün ve bugün) + daha eskiler
    var unmarkedCount: Int {
        snapshot.lessons.filter { !$0.isCompleted && $0.endDate <= date }.count + snapshot.olderUnmarkedCount
    }

    func isOngoing(_ lesson: WidgetLesson) -> Bool {
        lesson.date <= date && lesson.endDate > date
    }

    func needsMarking(_ lesson: WidgetLesson) -> Bool {
        !lesson.isCompleted && lesson.endDate <= date
    }

    static let preview = TodayEntry(date: .now, snapshot: .preview)
}

struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry { .preview }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        let snapshot = WidgetStore.loadSnapshot()
        completion(context.isPreview && snapshot.lessons.isEmpty
                   ? .preview
                   : TodayEntry(date: .now, snapshot: snapshot))
    }

    /// Ders başlarken ve biterken (tik düğmesi o an çıkar) ve gece yarısı
    /// yeni kayıtlar; uygulama veriyi değiştirdikçe zaten yeniden yüklenir.
    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        let snapshot = WidgetStore.loadSnapshot()
        let now = Date.now
        let calendar = WidgetTheme.calendar
        let tomorrow = calendar.startOfDay(for: now).addingTimeInterval(86_400 + 60)

        var dates: Set<Date> = [now, tomorrow]
        for lesson in snapshot.lessons where lesson.endDate > now && lesson.date < tomorrow {
            if lesson.date > now { dates.insert(lesson.date) }
            dates.insert(lesson.endDate)
        }
        let entries = dates.sorted().prefix(40).map { TodayEntry(date: $0, snapshot: snapshot) }
        completion(Timeline(entries: Array(entries), policy: .after(tomorrow)))
    }
}

// MARK: - İşlendi düğmesi

struct MarkLessonDoneIntent: AppIntent {
    static let title: LocalizedStringResource = "Dersi İşlendi Yap"
    static let isDiscoverable = false

    @Parameter(title: "Ders")
    var lessonID: String

    init() {}

    init(lessonID: String) {
        self.lessonID = lessonID
    }

    /// Widget uygulamanın veritabanına yazmaz: işaret kuyruğa girer, uygulama
    /// açılınca asıl kayda (bakiye, paket, eşitleme) işlenir.
    func perform() async throws -> some IntentResult {
        WidgetStore.enqueueCompletion(lessonID)
        return .result()
    }
}

// MARK: - Widget

struct DersDefteriWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetStore.widgetKind, provider: TodayProvider()) { entry in
            TodayWidgetView(entry: entry)
                .containerBackground(for: .widget) { WidgetTheme.background }
        }
        .configurationDisplayName("Bugünün Dersleri")
        .description("Sıradaki ders ve bugünün programı. Biten dersi buradan işlendi olarak işaretle.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge,
                            .accessoryRectangular, .accessoryInline, .accessoryCircular])
    }
}

@main
struct DersDefteriWidgetBundle: WidgetBundle {
    var body: some Widget {
        DersDefteriWidget()
    }
}

// MARK: - Önizleme verisi

extension WidgetSnapshot {
    static var preview: WidgetSnapshot {
        let cal = WidgetTheme.calendar
        let today = cal.startOfDay(for: .now)
        func at(_ hour: Int, _ minute: Int = 0) -> Date {
            cal.date(bySettingHour: hour, minute: minute, second: 0, of: today) ?? today
        }
        return WidgetSnapshot(generatedAt: .now, lessons: [
            WidgetLesson(id: "1", date: at(15), duration: 60, studentName: "Ece Kaya",
                         subject: "İngilizce", colorIndex: 4, status: "completed"),
            WidgetLesson(id: "2", date: at(16, 30), duration: 90, studentName: "Ayşe Yılmaz",
                         subject: "Matematik", colorIndex: 0, status: "planned"),
            WidgetLesson(id: "3", date: at(18, 30), duration: 90, studentName: "Can Demir",
                         subject: "Fizik", colorIndex: 1, status: "planned"),
        ], olderUnmarkedCount: 0)
    }
}
