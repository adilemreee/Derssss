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
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    /// Planlanan dersler için bekleyen bildirimleri baştan kurar.
    /// Her veri kaydından sonra çağrılır; böylece eklenen, taşınan,
    /// iptal edilen veya silinen dersler her zaman güncel kalır.
    @MainActor
    static func resync(context: ModelContext) async {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()

        let defaults = UserDefaults.standard
        let enabled = defaults.object(forKey: "remindersEnabled") as? Bool ?? true
        guard enabled else { return }
        let leadMinutes = defaults.object(forKey: "reminderMinutes") as? Int ?? 60

        let plannedRaw = LessonStatus.planned.rawValue
        let now = Date()
        var descriptor = FetchDescriptor<Lesson>(
            predicate: #Predicate { $0.statusRaw == plannedRaw && $0.date > now },
            sortBy: [SortDescriptor(\.date)]
        )
        // iOS bekleyen bildirim sayısını 64 ile sınırlar; kalan alan ödevlere
        // ve günlük program özetine ayrılır.
        descriptor.fetchLimit = 31
        guard let lessons = try? context.fetch(descriptor) else { return }

        for lesson in lessons {
            let fireDate = lesson.date.addingTimeInterval(-Double(leadMinutes) * 60)
            guard fireDate > now else { continue }

            let content = UNMutableNotificationContent()
            content.title = "Yaklaşan ders 📚"
            let time = Fmt.time.string(from: lesson.date)
            let name = lesson.student?.name ?? "Öğrenci"
            let subject = lesson.student?.subject ?? ""
            content.body = subject.isEmpty
                ? "\(time) — \(name)"
                : "\(time) — \(name) • \(subject)"
            content.sound = .default

            let comps = Calendar.tr.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            let request = UNNotificationRequest(identifier: UUID().uuidString,
                                                content: content,
                                                trigger: trigger)
            try? await center.add(request)
        }

        await scheduleHomeworkReminders(center: center, context: context, now: now)
        await scheduleDailyDigest(center: center, context: context, now: now)
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
        descriptor.fetchLimit = 18
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
