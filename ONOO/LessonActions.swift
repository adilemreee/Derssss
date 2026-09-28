//
//  LessonActions.swift
//  One — Ders Defteri
//
//  Ders kopyalama ve hızlı ders oluşturma yardımcıları.
//

import Foundation
import SwiftUI
import SwiftData

enum LessonActions {
    static func copy(_ lesson: Lesson, to date: Date, in context: ModelContext) {
        let fee = copiedFee(for: lesson)
        let new = Lesson(student: lesson.student,
                         date: date,
                         duration: lesson.duration,
                         status: .planned,
                         topic: lesson.topic,
                         note: lesson.note,
                         feeOverride: fee,
                         usesCustomFee: lesson.usesCustomFee)
        context.insert(new)
        new.student = lesson.student
        try? context.save()
    }

    static func copyNextWeek(_ lesson: Lesson, in context: ModelContext) {
        copy(lesson, to: lesson.date.adding(days: 7), in: context)
    }

    /// Tek seferlik dersi haftalık seriye çevirir.
    static func makeWeekly(_ lesson: Lesson, in context: ModelContext) {
        RecurringLessons.startSeries(from: lesson, in: context)
    }

    /// Seriyi bu dersten sonra bitirir; bu ders ve öncekiler kalır.
    static func stopRepeating(after lesson: Lesson, in context: ModelContext) {
        guard let template = lesson.sourceTemplate else { return }
        RecurringLessons.endSeries(template, after: lesson.date, in: context)
    }

    static func deleteOnly(_ lesson: Lesson, in context: ModelContext) {
        context.delete(lesson)
        try? context.save()
    }

    /// Bu ders ve serinin sonraki planlı dersleri silinir, seri biter.
    static func deleteThisAndFollowing(_ lesson: Lesson, in context: ModelContext) {
        if let template = lesson.sourceTemplate {
            RecurringLessons.endSeries(template, after: lesson.date, in: context)
        }
        context.delete(lesson)
        try? context.save()
    }

    private static func copiedFee(for lesson: Lesson) -> Double {
        if lesson.usesCustomFee { return lesson.fee }
        return Lesson.standardFee(for: lesson.student, duration: lesson.duration)
    }
}

// MARK: - Onay pencereleri

extension View {
    /// Ders silme onayı. Haftalık serideki bir derste "yalnızca bu" ve
    /// "bu ve sonrakiler" seçenekleri çıkar.
    func lessonDeleteDialog(_ target: Binding<Lesson?>,
                            context: ModelContext,
                            onDeleted: @escaping () -> Void = {}) -> some View {
        confirmationDialog(target.wrappedValue?.sourceTemplate != nil
                               ? "Bu ders her hafta tekrarlanıyor"
                               : "Ders silinsin mi?",
                           isPresented: Binding(get: { target.wrappedValue != nil },
                                                set: { if !$0 { target.wrappedValue = nil } }),
                           titleVisibility: .visible,
                           presenting: target.wrappedValue) { lesson in
            if lesson.sourceTemplate != nil {
                Button("Yalnızca Bu Dersi Sil", role: .destructive) {
                    LessonActions.deleteOnly(lesson, in: context)
                    onDeleted()
                }
                Button("Bu ve Sonraki Dersleri Sil", role: .destructive) {
                    LessonActions.deleteThisAndFollowing(lesson, in: context)
                    onDeleted()
                }
            } else {
                Button("Dersi Sil", role: .destructive) {
                    LessonActions.deleteOnly(lesson, in: context)
                    onDeleted()
                }
            }
            Button("Vazgeç", role: .cancel) {}
        } message: { lesson in
            if lesson.sourceTemplate != nil {
                Text("Yalnızca bu dersi silersen sonraki haftalar devam eder.")
            }
        }
    }

    /// "Tekrarı durdur" onayı: bu dersten sonraki planlı dersler silinir.
    func stopRepeatingDialog(_ target: Binding<Lesson?>, context: ModelContext) -> some View {
        confirmationDialog("Haftalık tekrar durdurulsun mu?",
                           isPresented: Binding(get: { target.wrappedValue != nil },
                                                set: { if !$0 { target.wrappedValue = nil } }),
                           titleVisibility: .visible,
                           presenting: target.wrappedValue) { lesson in
            Button("Tekrarı Durdur", role: .destructive) {
                LessonActions.stopRepeating(after: lesson, in: context)
            }
            Button("Vazgeç", role: .cancel) {}
        } message: { _ in
            Text("Bu dersten sonraki planlı dersler silinir; bu ders ve öncekiler kalır.")
        }
    }
}
