//
//  Models.swift
//  One — Ders Defteri
//
//  SwiftData modelleri: Öğrenci, Ders, Ödeme, Ödev.
//

import Foundation
import SwiftData
import SwiftUI

// MARK: - Ders durumu

enum LessonStatus: String, CaseIterable, Codable {
    case planned
    case completed
    case cancelled

    var title: String {
        switch self {
        case .planned: return "Planlandı"
        case .completed: return "İşlendi"
        case .cancelled: return "İptal"
        }
    }

    var icon: String {
        switch self {
        case .planned: return "clock"
        case .completed: return "checkmark.circle.fill"
        case .cancelled: return "xmark.circle"
        }
    }

    var tint: Color {
        switch self {
        case .planned: return Theme.blue
        case .completed: return Theme.green
        case .cancelled: return Theme.red
        }
    }
}

// MARK: - İptal sebebi

enum CancellationReason: String, CaseIterable, Codable {
    case none
    case student
    case teacher
    case makeup

    var title: String {
        switch self {
        case .none: return "Sebep yok"
        case .student: return "Öğrenci iptal etti"
        case .teacher: return "Öğretmen iptal etti"
        case .makeup: return "Telafi edilecek"
        }
    }

    var shortTitle: String {
        switch self {
        case .none: return "İptal"
        case .student: return "Öğrenci iptal"
        case .teacher: return "Öğretmen iptal"
        case .makeup: return "Telafi"
        }
    }

    var icon: String {
        switch self {
        case .none: return "xmark.circle"
        case .student: return "person.crop.circle.badge.xmark"
        case .teacher: return "person.badge.minus"
        case .makeup: return "arrow.clockwise.circle"
        }
    }
}

// MARK: - Ödeme yöntemi

enum PaymentMethod: String, CaseIterable, Codable {
    case cash
    case transfer
    case other

    var title: String {
        switch self {
        case .cash: return "Nakit"
        case .transfer: return "Havale/EFT"
        case .other: return "Diğer"
        }
    }

    var icon: String {
        switch self {
        case .cash: return "banknote"
        case .transfer: return "arrow.left.arrow.right"
        case .other: return "creditcard"
        }
    }
}

// MARK: - Öğrenci

@Model
final class Student {
    /// Sunucuyla ortak kimlik. Cihazda üretilir; sunucu kimliği hiç bilinmez.
    var uuid: UUID = UUID()
    var name: String = ""
    var subject: String = ""
    var grade: String = ""
    var phone: String = ""
    var parentName: String = ""
    var parentPhone: String = ""
    var hourlyRate: Double = 0
    var startDate: Date = Date()
    var colorIndex: Int = 0
    var notes: String = ""
    var isArchived: Bool = false

    // CloudKit senkronizasyonu için ilişkiler opsiyonel tutulur
    @Relationship(deleteRule: .cascade, inverse: \Lesson.student)
    var lessons: [Lesson]? = []
    @Relationship(deleteRule: .cascade, inverse: \Payment.student)
    var payments: [Payment]? = []
    @Relationship(deleteRule: .cascade, inverse: \Homework.student)
    var homeworks: [Homework]? = []
    @Relationship(deleteRule: .cascade, inverse: \RecurringLessonTemplate.student)
    var recurringTemplates: [RecurringLessonTemplate]? = []

    init(name: String,
         subject: String = "",
         grade: String = "",
         phone: String = "",
         parentName: String = "",
         parentPhone: String = "",
         hourlyRate: Double = 0,
         startDate: Date = Date(),
         colorIndex: Int = 0,
         notes: String = "") {
        self.name = name
        self.subject = subject
        self.grade = grade
        self.phone = phone
        self.parentName = parentName
        self.parentPhone = parentPhone
        self.hourlyRate = hourlyRate
        self.startDate = startDate
        self.colorIndex = colorIndex
        self.notes = notes
    }

    var initials: String {
        name.split(separator: " ").prefix(2)
            .compactMap { $0.first }
            .map(String.init)
            .joined()
            .uppercased()
    }

    var color: Color {
        Theme.palette[abs(colorIndex) % Theme.palette.count]
    }

    var allLessons: [Lesson] { lessons ?? [] }
    var allPayments: [Payment] { payments ?? [] }
    var allHomeworks: [Homework] { homeworks ?? [] }
    var allRecurringTemplates: [RecurringLessonTemplate] { recurringTemplates ?? [] }

    var completedLessons: [Lesson] {
        allLessons.filter { $0.status == .completed }
    }

    /// Toplam işlenen ders süresi (dakika)
    var totalMinutes: Int {
        completedLessons.reduce(0) { $0 + $1.duration }
    }

    /// İşlenen derslerin toplam ders tutarı
    var totalEarned: Double {
        completedLessons.reduce(0) { $0 + $1.fee }
    }

    var totalPaid: Double {
        allPayments.reduce(0) { $0 + $1.amount }
    }

    /// Pozitif = öğrencinin borcu var, negatif = avans ödemiş
    var balance: Double {
        totalEarned - totalPaid
    }
}

// MARK: - Ders

@Model
final class Lesson {
    var uuid: UUID = UUID()
    var date: Date = Date()
    /// Dakika cinsinden süre
    var duration: Int = 60
    var statusRaw: String = LessonStatus.planned.rawValue
    var cancellationReasonRaw: String = CancellationReason.none.rawValue
    var topic: String = ""
    var note: String = ""
    /// Saatlik ücret yerine derse özel sabit ücret
    var feeOverride: Double? = nil
    /// Ücret elle girildiyse true; false ise kayıt anındaki saatlik ücretten kilitlenmiştir
    var usesCustomFee: Bool = false
    var student: Student? = nil
    /// Ders bir tekrar şablonundan üretildiyse kaynağı
    var sourceTemplate: RecurringLessonTemplate? = nil

    init(student: Student? = nil,
         date: Date,
         duration: Int = 60,
         status: LessonStatus = .planned,
         cancellationReason: CancellationReason = .none,
         topic: String = "",
         note: String = "",
         feeOverride: Double? = nil,
         usesCustomFee: Bool = false) {
        self.student = student
        self.date = date
        self.duration = duration
        self.statusRaw = status.rawValue
        self.cancellationReasonRaw = cancellationReason.rawValue
        self.topic = topic
        self.note = note
        self.feeOverride = feeOverride
        self.usesCustomFee = usesCustomFee
    }

    var status: LessonStatus {
        get { LessonStatus(rawValue: statusRaw) ?? .planned }
        set {
            statusRaw = newValue.rawValue
            if newValue != .cancelled {
                cancellationReasonRaw = CancellationReason.none.rawValue
            }
        }
    }

    var cancellationReason: CancellationReason {
        get { CancellationReason(rawValue: cancellationReasonRaw) ?? .none }
        set { cancellationReasonRaw = newValue.rawValue }
    }

    var endDate: Date {
        date.addingTimeInterval(Double(duration) * 60)
    }

    var fee: Double {
        if let feeOverride { return feeOverride }
        return Self.standardFee(for: student, duration: duration)
    }

    static func standardFee(for student: Student?, duration: Int) -> Double {
        Double(duration) / 60.0 * (student?.hourlyRate ?? 0)
    }

    func lockCurrentStandardFeeIfNeeded() {
        guard feeOverride == nil else { return }
        feeOverride = Self.standardFee(for: student, duration: duration)
        usesCustomFee = false
    }
}

// MARK: - Ödeme

@Model
final class Payment {
    var uuid: UUID = UUID()
    var date: Date = Date()
    var amount: Double = 0
    var methodRaw: String = PaymentMethod.cash.rawValue
    var note: String = ""
    var student: Student? = nil

    init(student: Student? = nil,
         date: Date = Date(),
         amount: Double,
         method: PaymentMethod = .cash,
         note: String = "") {
        self.student = student
        self.date = date
        self.amount = amount
        self.methodRaw = method.rawValue
        self.note = note
    }

    var method: PaymentMethod {
        get { PaymentMethod(rawValue: methodRaw) ?? .other }
        set { methodRaw = newValue.rawValue }
    }
}

// MARK: - Ödev

@Model
final class Homework {
    var uuid: UUID = UUID()
    var title: String = ""
    var detail: String = ""
    var assignedDate: Date = Date()
    var dueDate: Date = Date()
    var isDone: Bool = false
    var doneDate: Date? = nil
    var student: Student? = nil

    init(student: Student? = nil,
         title: String,
         detail: String = "",
         assignedDate: Date = Date(),
         dueDate: Date,
         isDone: Bool = false,
         doneDate: Date? = nil) {
        self.student = student
        self.title = title
        self.detail = detail
        self.assignedDate = assignedDate
        self.dueDate = dueDate
        self.isDone = isDone
        self.doneDate = doneDate
    }

    /// Teslim tarihi geçti ve hâlâ yapılmadı
    var isLate: Bool {
        !isDone && dueDate < Date().startOfDay
    }
}

// MARK: - Tekrarlayan ders şablonu

@Model
final class RecurringLessonTemplate {
    var uuid: UUID = UUID()
    /// Calendar.weekday değeri (1 = Pazar ... 7 = Cumartesi)
    var weekday: Int = 3
    var hour: Int = 17
    var minute: Int = 0
    /// Dakika cinsinden süre
    var duration: Int = 60
    /// Saatlik ücret yerine derse özel sabit ücret
    var feeOverride: Double? = nil
    /// Ücret elle girildiyse true; false ise üretilen derslerde o günkü saatlik ücret kilitlenir
    var usesCustomFee: Bool = false
    /// Duraklatılan şablon yeni ders üretmez
    var isPaused: Bool = false
    var createdAt: Date = Date()
    /// Bu tarihe kadar dersler üretildi; ileri tarihli üretim buradan devam eder
    var generatedUntil: Date? = nil
    var student: Student? = nil

    @Relationship(deleteRule: .nullify, inverse: \Lesson.sourceTemplate)
    var generatedLessons: [Lesson]? = []

    init(student: Student? = nil,
         weekday: Int = 3,
         hour: Int = 17,
         minute: Int = 0,
         duration: Int = 60,
         feeOverride: Double? = nil,
         usesCustomFee: Bool = false,
         isPaused: Bool = false) {
        self.student = student
        self.weekday = weekday
        self.hour = hour
        self.minute = minute
        self.duration = duration
        self.feeOverride = feeOverride
        self.usesCustomFee = usesCustomFee
        self.isPaused = isPaused
    }

    var allGeneratedLessons: [Lesson] { generatedLessons ?? [] }

    /// UI'daki hafta sırası (Pazartesi başlangıçlı) → Calendar.weekday değerleri
    static let weekdayOrder: [Int] = [2, 3, 4, 5, 6, 7, 1]

    static func weekdayName(_ weekday: Int) -> String {
        let names = [1: "Pazar", 2: "Pazartesi", 3: "Salı", 4: "Çarşamba",
                     5: "Perşembe", 6: "Cuma", 7: "Cumartesi"]
        return names[weekday] ?? "—"
    }

    var weekdayName: String { Self.weekdayName(weekday) }

    var timeText: String {
        String(format: "%02d:%02d", hour, minute)
    }
}
