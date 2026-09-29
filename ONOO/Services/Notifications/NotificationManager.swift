//
//  NotificationManager.swift
//  One — Ders Defteri
//
//  Yaklaşan dersler için yerel bildirim planlama.
//

import Foundation
import SwiftData
import UserNotifications

enum AppNotifications {
    static func requestPermissionIfNeeded() async {
        #if DEBUG
        // Simülatör otomasyonu sistem izin penceresine dokunamıyor.
        if ProcessInfo.processInfo.arguments.contains("-bildirimIzniSorma") { return }
        #endif
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    /// "Ders bitti" bildiriminin kategorisi ve "İşlendi" düğmesi.
    nonisolated static let lessonDoneCategory = "LESSON_DONE"
    nonisolated static let markDoneAction = "MARK_DONE"
    nonisolated static let lessonIDKey = "lessonID"

    /// Bildirimdeki düğmeler kategoriyle tanımlanır; açılışta bir kez kaydedilir.
    static func registerCategories() {
        let markDone = UNNotificationAction(identifier: markDoneAction,
                                            title: "İşlendi",
                                            options: [])
        let category = UNNotificationCategory(identifier: lessonDoneCategory,
                                              actions: [markDone],
                                              intentIdentifiers: [],
                                              options: [])
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    /// Planlanan dersler için bekleyen bildirimleri baştan kurar.
    /// Her veri kaydından sonra çağrılır; böylece eklenen, taşınan,
    /// iptal edilen veya silinen dersler her zaman güncel kalır.
    ///
    /// iOS bekleyen bildirimlerden en yakın 64'ünü tutar. Pay: ders
    /// hatırlatması 20, "ders bitti" 10, ödev 8 × 3, günlük özet 7.
    @MainActor
    static func resync(context: ModelContext) async {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()

        let defaults = UserDefaults.standard
        let now = Date()

        // Ders hatırlatması kapalıyken ödev ve özet bildirimleri de
        // kurulmuyordu; her biri kendi ayarına bakar.
        if defaults.object(forKey: "remindersEnabled") as? Bool ?? true {
            await scheduleLessonReminders(center: center, context: context, now: now)
        }
        if defaults.object(forKey: "lessonDonePromptEnabled") as? Bool ?? true {
            await scheduleLessonDonePrompts(center: center, context: context, now: now)
        }
        await scheduleHomeworkReminders(center: center, context: context, now: now)
        await scheduleDailyDigest(center: center, context: context, now: now)
    }

    @MainActor
    private static func scheduleLessonReminders(center: UNUserNotificationCenter,
                                                context: ModelContext,
                                                now: Date) async {
        let leadMinutes = UserDefaults.standard.object(forKey: "reminderMinutes") as? Int ?? 60
        let plannedRaw = LessonStatus.planned.rawValue
        var descriptor = FetchDescriptor<Lesson>(
            predicate: #Predicate { $0.statusRaw == plannedRaw && $0.date > now },
            sortBy: [SortDescriptor(\.date)]
        )
        descriptor.fetchLimit = 20
        guard let lessons = try? context.fetch(descriptor) else { return }

        for lesson in lessons {
            let fireDate = lesson.date.addingTimeInterval(-Double(leadMinutes) * 60)
            guard fireDate > now else { continue }

            let content = UNMutableNotificationContent()
            content.title = "Yaklaşan ders 📚"
            content.body = lessonLine(lesson)
            content.sound = .default
            await add(content, at: fireDate, center: center)
        }
    }

    /// Ders bitince "İşlendi mi?" diye sorar. İşlendi denmeyen ders bakiyeye
    /// yansımaz; bildirimdeki düğmeyle uygulamayı açmadan işaretlenir.
    @MainActor
    private static func scheduleLessonDonePrompts(center: UNUserNotificationCenter,
                                                  context: ModelContext,
                                                  now: Date) async {
        let plannedRaw = LessonStatus.planned.rawValue
        // Şu an süren ders de dahil: bitişi henüz gelmedi.
        let earliestStart = now.addingTimeInterval(-6 * 3600)
        var descriptor = FetchDescriptor<Lesson>(
            predicate: #Predicate { $0.statusRaw == plannedRaw && $0.date > earliestStart },
            sortBy: [SortDescriptor(\.date)]
        )
        descriptor.fetchLimit = 16
        guard let lessons = try? context.fetch(descriptor) else { return }

        for lesson in lessons.filter({ $0.endDate > now }).prefix(10) {
            let content = UNMutableNotificationContent()
            content.title = "Ders bitti mi? ✅"
            content.body = "\(lessonLine(lesson)) — yapıldıysa İşlendi olarak işaretle."
            content.sound = .default
            content.categoryIdentifier = lessonDoneCategory
            content.userInfo = [lessonIDKey: lesson.uuid.uuidString]
            // Ders bitiminden birkaç dakika sonra; öğrenci kapıdan çıkarken değil.
            await add(content, at: lesson.endDate.addingTimeInterval(5 * 60), center: center)
        }
    }

    private static func lessonLine(_ lesson: Lesson) -> String {
        let time = Fmt.time.string(from: lesson.date)
        let name = lesson.student?.name ?? "Öğrenci"
        let subject = lesson.student?.subject ?? ""
        return subject.isEmpty ? "\(time) — \(name)" : "\(time) — \(name) • \(subject)"
    }

    private static func add(_ content: UNMutableNotificationContent,
                            at fireDate: Date,
                            center: UNUserNotificationCenter) async {
        let comps = Calendar.tr.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let request = UNNotificationRequest(identifier: UUID().uuidString,
                                            content: content,
                                            trigger: trigger)
        try? await center.add(request)
    }

    /// Günün derslerini sabahtan tek bildirimde özetler (Pro).
    ///
    /// Tekrarlayan bir tetikleyici kullanılamaz: bildirim metni o günün ders
    /// listesini içerdiği için her gün farklıdır. Bu yüzden önümüzdeki bir
    /// hafta için ayrı ayrı, içeriği o güne göre yazılmış bildirimler kurulur;
    /// her veri değişiminde bunlar baştan üretilir.
    @MainActor
    private static func scheduleDailyDigest(center: UNUserNotificationCenter,
                                            context: ModelContext,
                                            now: Date) async {
        let defaults = UserDefaults.standard
        guard defaults.bool(forKey: "dailyDigestEnabled") else { return }
        guard defaults.bool(forKey: "proActiveCache") else { return }
        let hour = defaults.object(forKey: "dailyDigestHour") as? Int ?? 8

        let plannedRaw = LessonStatus.planned.rawValue
        let horizon = now.startOfDay.adding(days: 8)
        var descriptor = FetchDescriptor<Lesson>(
            predicate: #Predicate { $0.statusRaw == plannedRaw && $0.date > now && $0.date < horizon },
            sortBy: [SortDescriptor(\.date)]
        )
        descriptor.fetchLimit = 120
        guard let lessons = try? context.fetch(descriptor) else { return }

        let byDay = Dictionary(grouping: lessons) { $0.date.startOfDay }

        for offset in 0..<7 {
            let day = now.startOfDay.adding(days: offset)
            guard let dayLessons = byDay[day], !dayLessons.isEmpty else { continue }

            var comps = Calendar.tr.dateComponents([.year, .month, .day], from: day)
            comps.hour = hour
            comps.minute = 0
            guard let fireDate = Calendar.tr.date(from: comps), fireDate > now else { continue }

            let content = UNMutableNotificationContent()
            content.title = dayLessons.count == 1
                ? "Bugün 1 dersin var 📖"
                : "Bugün \(dayLessons.count) dersin var 📖"
            content.body = dayLessons.prefix(4).map { lesson in
                "\(Fmt.time.string(from: lesson.date)) \(lesson.student?.name ?? "Öğrenci")"
            }.joined(separator: " • ")
            if dayLessons.count > 4 {
                content.body += " +\(dayLessons.count - 4)"
            }
            content.sound = .default

            let trigger = UNCalendarNotificationTrigger(
                dateMatching: Calendar.tr.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate),
                repeats: false
            )
            try? await center.add(UNNotificationRequest(identifier: UUID().uuidString,
                                                        content: content,
                                                        trigger: trigger))
        }
    }

    @MainActor
    private static func scheduleHomeworkReminders(center: UNUserNotificationCenter,
                                                  context: ModelContext,
                                                  now: Date) async {
        let defaults = UserDefaults.standard
        let enabled = defaults.object(forKey: "homeworkRemindersEnabled") as? Bool ?? true
        guard enabled else { return }
        let reminderHour = defaults.object(forKey: "homeworkReminderHour") as? Int ?? 9

        var descriptor = FetchDescriptor<Homework>(
            predicate: #Predicate { !$0.isDone },
            sortBy: [SortDescriptor(\.dueDate)]
        )
        descriptor.fetchLimit = 8
        guard let homeworks = try? context.fetch(descriptor) else { return }

        for homework in homeworks {
            let dueMorning = reminderDate(for: homework.dueDate, hour: reminderHour)
            let upcoming = dueMorning.adding(days: -1)
            let late = dueMorning.adding(days: 1)

            if upcoming > now {
                await addHomeworkNotification(center: center,
                                              homework: homework,
                                              fireDate: upcoming,
                                              title: "Ödev teslimi yaklaşıyor",
                                              prefix: "Yarın teslim")
            }
            if dueMorning > now {
                await addHomeworkNotification(center: center,
                                              homework: homework,
                                              fireDate: dueMorning,
                                              title: "Bugün ödev teslim günü",
                                              prefix: "Bugün teslim")
            }
            if late > now {
                await addHomeworkNotification(center: center,
                                              homework: homework,
                                              fireDate: late,
                                              title: "Geciken ödev var",
                                              prefix: "Gecikti")
            }
        }
    }

    private static func reminderDate(for date: Date, hour: Int) -> Date {
        var comps = Calendar.tr.dateComponents([.year, .month, .day], from: date)
        comps.hour = hour
        comps.minute = 0
        return Calendar.tr.date(from: comps) ?? date
    }

    private static func addHomeworkNotification(center: UNUserNotificationCenter,
                                                homework: Homework,
                                                fireDate: Date,
                                                title: String,
                                                prefix: String) async {
        let content = UNMutableNotificationContent()
        content.title = title
        let name = homework.student?.name ?? "Öğrenci"
        content.body = "\(prefix) — \(name) • \(homework.title)"
        content.sound = .default

        let comps = Calendar.tr.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let request = UNNotificationRequest(identifier: UUID().uuidString,
                                            content: content,
                                            trigger: trigger)
        try? await center.add(request)
    }
}

// MARK: - Bildirim düğmeleri

/// Bildirimdeki "İşlendi" düğmesini işler ve uygulama açıkken de bildirimin
/// görünmesini sağlar (temsilci yokken ön plandaki bildirimler sessizce düşer).
final class NotificationActions: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationActions()

    var container: ModelContainer?

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            didReceive response: UNNotificationResponse) async {
        guard response.actionIdentifier == AppNotifications.markDoneAction,
              let id = response.notification.request.content.userInfo[AppNotifications.lessonIDKey] as? String,
              let uuid = UUID(uuidString: id) else { return }
        await markDone(uuid)
    }

    @MainActor
    private func markDone(_ uuid: UUID) {
        guard let context = container?.mainContext else { return }
        var descriptor = FetchDescriptor<Lesson>(predicate: #Predicate { $0.uuid == uuid })
        descriptor.fetchLimit = 1
        // Bu arada iptal edilmiş ya da silinmiş derse dokunulmaz.
        guard let lesson = try? context.fetch(descriptor).first, lesson.status == .planned else { return }
        lesson.status = .completed
        try? context.save()
    }
}
