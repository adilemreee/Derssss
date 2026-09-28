//
//  StudentActions.swift
//  One — Ders Defteri
//
//  Öğrenciyi arşivleme, aktife alma ve ücret değişikliğinin derslere yansıması.
//

import SwiftUI
import SwiftData

enum StudentActions {
    /// Arşivlenince programdan kalkacak dersler: henüz yapılmamış planlı dersler.
    static func upcomingPlannedLessons(of student: Student, after now: Date = Date()) -> [Lesson] {
        student.allLessons.filter { $0.status == .planned && $0.date > now }
    }

    /// Arşivdeki öğrencinin dersi programda ve bildirimlerde durmasın.
    /// İşlenmiş dersler, ödemeler ve ödevler kalır; haftalık dersler de silinmez,
    /// yalnız öğrenci arşivdeyken yeni ders üretmez.
    static func archive(_ student: Student, in context: ModelContext) {
        for lesson in upcomingPlannedLessons(of: student) {
            context.delete(lesson)
        }
        student.isArchived = true
        try? context.save()
    }

    /// Aktife alınan öğrencinin haftalık dersleri bugünden itibaren yeniden planlanır.
    static func unarchive(_ student: Student, in context: ModelContext) {
        student.isArchived = false
        for template in student.allRecurringTemplates {
            // Ders kimlikleri şablon kimliğinden türetilir. Arşivlerken silinen
            // haftalar aynı kimlikle geri gelirse sunucu onları silinmiş sayar
            // ve bir sonraki eşitlemede yeniden siler; yeni kimlik bunu önler.
            template.uuid = UUID()
            template.generatedUntil = nil
        }
        try? context.save()
        RecurringLessons.topUp(context: context)
    }

    /// Saatlik ücret değişince henüz işlenmemiş dersler yeni ücrete geçer.
    ///
    /// Ücret ders oluşturulurken kilitlenir; dersler haftalar önceden
    /// üretildiği için ücret sonradan girilirse (ya da zam yapılırsa) planlı
    /// dersler eski tutarda kalıyordu. İşlenmiş, iptal ve derse özel ücretli
    /// dersler değişmez. Bugünden önceki planlı dersler de eski ücretle
    /// kalır; yalnız hiç fiyatlanmamış (0 ₺) olanlar yeni ücreti alır.
    static func applyRateToPlannedLessons(of student: Student) {
        let today = Date().startOfDay
        for lesson in student.allLessons
        where lesson.status == .planned && !lesson.usesCustomFee
            && (lesson.date >= today || (lesson.feeOverride ?? 0) < 0.005) {
            lesson.feeOverride = Lesson.standardFee(for: student, duration: lesson.duration)
        }
    }
}

// MARK: - Onaylar

extension View {
    /// Gelecek planlı dersi olan öğrenci arşivlenmeden önce ne olacağını söyler.
    func studentArchiveDialog(_ target: Binding<Student?>,
                              context: ModelContext,
                              onArchived: @escaping () -> Void = {}) -> some View {
        confirmationDialog("Öğrenci arşivlensin mi?",
                           isPresented: Binding(get: { target.wrappedValue != nil },
                                                set: { if !$0 { target.wrappedValue = nil } }),
                           titleVisibility: .visible,
                           presenting: target.wrappedValue) { student in
            Button("Arşivle", role: .destructive) {
                StudentActions.archive(student, in: context)
                target.wrappedValue = nil
                onArchived()
            }
            Button("Vazgeç", role: .cancel) { target.wrappedValue = nil }
        } message: { student in
            let count = StudentActions.upcomingPlannedLessons(of: student).count
            Text(count > 0
                 ? "Gelecek \(count) planlı ders programdan kaldırılır. İşlenmiş dersler, ödemeler ve ödevler kalır; aktife alınca haftalık dersler yeniden planlanır."
                 : "İşlenmiş dersler, ödemeler ve ödevler kalır.")
        }
    }
}
