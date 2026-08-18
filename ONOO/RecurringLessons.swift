//
//  RecurringLessons.swift
//  One — Ders Defteri
//
//  Tekrarlayan ders şablonlarından ileri tarihli dersleri otomatik üretir.
//

import Foundation
import SwiftData

enum RecurringLessons {
    /// Kaç gün ilerisi için ders üretilir
    static let horizonDays = 28

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

    /// Şablonun gelecekteki planlanan derslerini silip baştan üretir.
    /// Şablonun günü/saati değiştiğinde çağrılır.
    static func regenerate(_ template: RecurringLessonTemplate, in context: ModelContext) {
        deleteUpcomingLessons(of: template, in: context)
        template.generatedUntil = nil
        topUp(context: context)
    }

    /// Şablondan üretilmiş, henüz işlenmemiş gelecek dersleri siler.
    static func deleteUpcomingLessons(of template: RecurringLessonTemplate, in context: ModelContext) {
        let now = Date()
        for lesson in template.allGeneratedLessons where lesson.date > now && lesson.status == .planned {
            context.delete(lesson)
        }
    }

    /// Şablonun [from, to) aralığındaki ders başlangıç zamanları
    static func occurrences(of template: RecurringLessonTemplate, from: Date, to: Date) -> [Date] {
        var result: [Date] = []
        var day = from.startOfDay
        while day < to {
            if Calendar.tr.component(.weekday, from: day) == template.weekday {
                var comps = Calendar.tr.dateComponents([.year, .month, .day], from: day)
                comps.hour = template.hour
                comps.minute = template.minute
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
