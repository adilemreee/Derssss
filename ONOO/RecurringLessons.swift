//
//  RecurringLessons.swift
//  One — Ders Defteri
//
//  Tekrarlayan ders şablonlarından ileri tarihli dersleri otomatik üretir.
//

import CryptoKit
import Foundation
import SwiftData

enum RecurringLessons {
    /// Kaç gün ilerisi için ders üretilir
    static let horizonDays = 28

    /// Şablondan üretilen dersin kimliği şablon ve saatten türetilir.
    ///
    /// Eşitlenen iki cihaz aynı şablondan aynı haftanın dersini birbirinden
    /// habersiz üretirse, kimlik rastgele olsaydı sunucuda iki ayrı ders
    /// oluşurdu. Türetilmiş kimlikle ikisi aynı kayda düşer.
    static func lessonID(template: RecurringLessonTemplate, slot: Date) -> UUID {
        let seed = "\(template.uuid.uuidString)|\(Int(slot.timeIntervalSince1970))"
        var b = Array(SHA256.hash(data: Data(seed.utf8)).prefix(16))
        // RFC 4122: ad tabanlı (sürüm 5) ve standart varyant bitleri
        b[6] = (b[6] & 0x0F) | 0x50
        b[8] = (b[8] & 0x3F) | 0x80
        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7],
                           b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }

    /// Tüm şablonlar için ufka kadar eksik dersleri üretir.
    /// Uygulama açılışında ve şablon kaydedildiğinde çağrılır.
    static func topUp(context: ModelContext) {
        let templates = (try? context.fetch(FetchDescriptor<RecurringLessonTemplate>())) ?? []
        guard !templates.isEmpty else { return }

        var existing = (try? context.fetch(FetchDescriptor<Lesson>())) ?? []
        let now = Date()
        let horizon = now.startOfDay.adding(days: horizonDays)
        var didChange = false

        for template in templates {
            guard !template.isPaused,
                  let student = template.student,
                  !student.isArchived else { continue }

            // Daha önce üretilen aralığı tekrar üretme; silinen ders geri gelmesin
            let from = max(template.generatedUntil ?? now, now)
            guard from < horizon else { continue }

            for slot in occurrences(of: template, from: from, to: horizon) {
                let end = slot.addingTimeInterval(Double(template.duration) * 60)
                let clash = existing.contains {
                    $0.status != .cancelled && slot < $0.endDate && $0.date < end
                }
                if clash { continue }

                let fee = template.feeOverride ?? Lesson.standardFee(for: student, duration: template.duration)
                let lesson = Lesson(student: student,
                                    date: slot,
                                    duration: template.duration,
                                    feeOverride: fee,
                                    usesCustomFee: template.usesCustomFee)
                lesson.uuid = lessonID(template: template, slot: slot)
                context.insert(lesson)
                lesson.student = student
                lesson.sourceTemplate = template
                existing.append(lesson)
                didChange = true
            }

            if template.generatedUntil != horizon {
                template.generatedUntil = horizon
                didChange = true
            }
        }

        if didChange {
            try? context.save()
        }
    }

    // MARK: - Haftalık ders serisi
    //
    // Kullanıcı "şablon" görmez: bir derse "Her hafta" dediğinde arkada şablon
    // oluşur, o ders de serinin ilk dersi olur. Seri dersin kendisinden
    // düzenlenir ve bitirilir.

    /// Bir dersi haftalık serinin ilk dersi yapar. Sonraki haftalar `topUp`
    /// ile ufka kadar üretilir.
    @discardableResult
    static func startSeries(from lesson: Lesson, in context: ModelContext) -> RecurringLessonTemplate? {
        guard let student = lesson.student, lesson.sourceTemplate == nil else { return lesson.sourceTemplate }
        let cal = Calendar.tr
        let template = RecurringLessonTemplate(weekday: cal.component(.weekday, from: lesson.date),
                                               hour: cal.component(.hour, from: lesson.date),
                                               minute: cal.component(.minute, from: lesson.date),
                                               duration: lesson.duration,
                                               feeOverride: lesson.usesCustomFee ? lesson.feeOverride : nil,
                                               usesCustomFee: lesson.usesCustomFee)
        context.insert(template)
        template.student = student
        // Bu dersin haftası zaten var; üretim bir sonraki haftadan başlar.
        template.generatedUntil = lesson.date.addingTimeInterval(60)
        lesson.sourceTemplate = template
        try? context.save()
        topUp(context: context)
        return template
    }

    /// "Bu ve sonraki dersler": serinin `from` tarihinden itibaren planlı
    /// derslerini silmeden yeni gün/saat/süre/ücrete taşır; konu ve notlar
    /// korunur. Önceki dersler olduğu gibi kalır.
    ///
    /// - Parameters:
    ///   - dayShift: Derslerin kaç gün kaydırılacağı (Salı → Perşembe = 2).
    ///   - weekday: Serinin yeni günü (`Calendar.weekday`).
    static func applyToFollowing(template: RecurringLessonTemplate,
                                 from: Date,
                                 excluding excluded: Lesson? = nil,
                                 dayShift: Int,
                                 weekday newWeekday: Int,
                                 hour: Int,
                                 minute: Int,
                                 duration: Int,
                                 feeOverride: Double?,
                                 usesCustomFee: Bool,
                                 in context: ModelContext) {
        let cal = Calendar.tr
        let lessons = template.allGeneratedLessons
            .filter { $0.status == .planned && $0.date >= from && $0 !== excluded }
            .sorted { $0.date < $1.date }

        func moved(_ date: Date, by shift: Int) -> Date {
            let day = date.startOfDay.adding(days: shift)
            return cal.date(bySettingHour: hour, minute: minute, second: 0, of: day) ?? date
        }

        let now = Date()
        for lesson in lessons {
            // Yeni yeri geçmişte kalan ders (bugün Çarşamba, seri Perşembe →
            // Pazartesi) bu hafta eski gününde yapılır; taşıma sonraki
            // haftadan başlar. Geçmişte "planlı" ders bırakılmaz.
            let newDate = moved(lesson.date, by: dayShift)
            if newDate >= now || excluded != nil {
                lesson.date = newDate
            }
            lesson.duration = duration
            if usesCustomFee, let feeOverride {
                lesson.feeOverride = feeOverride
                lesson.usesCustomFee = true
            } else {
                lesson.feeOverride = Lesson.standardFee(for: lesson.student, duration: duration)
                lesson.usesCustomFee = false
            }
        }

        template.weekday = newWeekday
        template.hour = hour
        template.minute = minute
        template.duration = duration
        template.feeOverride = usesCustomFee ? feeOverride : nil
        template.usesCustomFee = usesCustomFee
        try? context.save()
        topUp(context: context)
    }

    /// Takvim günü farkı; `applyToFollowing` için kaydırma miktarı.
    static func dayShift(from old: Date, to new: Date) -> Int {
        Calendar.tr.dateComponents([.day], from: old.startOfDay, to: new.startOfDay).day ?? 0
    }

    /// Hafta içi sıra farkı (Pazartesi başlangıçlı); şablon listesinden gün
    /// değiştirildiğinde dersleri o hafta içinde kaydırmak için.
    static func dayShift(fromWeekday old: Int, toWeekday new: Int) -> Int {
        let order = RecurringLessonTemplate.weekdayOrder
        guard let a = order.firstIndex(of: old), let b = order.firstIndex(of: new) else { return 0 }
        return b - a
    }

    /// Seriyi bitirir: `after` tarihinden sonraki planlı dersler silinir, seri
    /// yeni ders üretmez. Önceki ve işlenmiş dersler kalır.
    static func endSeries(_ template: RecurringLessonTemplate, after date: Date, in context: ModelContext) {
        for lesson in template.allGeneratedLessons where lesson.status == .planned && lesson.date > date {
            context.delete(lesson)
        }
        context.delete(template)
        try? context.save()
    }

    /// Şablondan üretilmiş, henüz işlenmemiş gelecek dersleri siler.
    static func deleteUpcomingLessons(of template: RecurringLessonTemplate, in context: ModelContext) {
        let now = Date()
        for lesson in template.allGeneratedLessons where lesson.date > now && lesson.status == .planned {
            context.delete(lesson)
        }
    }

    /// Kaydedilmeden önce formda gösterilir: seçilen gün ve saatte ufuk
    /// içinde başka bir ders varsa o hafta üretilmeyecek (`topUp` atlar).
    static func clashes(weekdays: Set<Int>,
                        hour: Int,
                        minute: Int,
                        duration: Int,
                        ignoring template: RecurringLessonTemplate? = nil,
                        in lessons: [Lesson]) -> [Lesson] {
        let now = Date()
        let horizon = now.startOfDay.adding(days: horizonDays)
        let seconds = Double(duration) * 60
        let slots = weekdays.flatMap {
            occurrences(weekday: $0, hour: hour, minute: minute, from: now, to: horizon)
        }
        return lessons
            .filter { lesson in
                lesson.status != .cancelled
                    && (template == nil || lesson.sourceTemplate !== template)
                    && slots.contains { $0 < lesson.endDate && lesson.date < $0.addingTimeInterval(seconds) }
            }
            .sorted { $0.date < $1.date }
    }

    /// Şablonun [from, to) aralığındaki ders başlangıç zamanları
    static func occurrences(of template: RecurringLessonTemplate, from: Date, to: Date) -> [Date] {
        occurrences(weekday: template.weekday, hour: template.hour, minute: template.minute, from: from, to: to)
    }

    static func occurrences(weekday: Int, hour: Int, minute: Int, from: Date, to: Date) -> [Date] {
        var result: [Date] = []
        var day = from.startOfDay
        while day < to {
            if Calendar.tr.component(.weekday, from: day) == weekday {
                var comps = Calendar.tr.dateComponents([.year, .month, .day], from: day)
                comps.hour = hour
                comps.minute = minute
                if let slot = Calendar.tr.date(from: comps), slot >= from, slot < to {
                    result.append(slot)
                }
                day = day.adding(days: 7)
            } else {
                day = day.adding(days: 1)
            }
        }
        return result
    }
}
