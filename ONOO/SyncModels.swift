//
//  SyncModels.swift
//  One — Ders Defteri
//
//  Sunucuyla taşınan kayıt biçimleri. Alan adları sunucu şemasıyla birebir aynıdır.
//

import Foundation

/// Her senkronize kaydın ortak alanları.
nonisolated protocol SyncRecord: Codable {
    var clientId: UUID { get }
    var clientUpdatedAt: Date { get set }
    var deletedAt: Date? { get set }
}

nonisolated struct StudentDTO: SyncRecord {
    var clientId: UUID
    var clientUpdatedAt: Date = .now
    var deletedAt: Date?

    var name: String
    var subject: String
    var grade: String
    var phone: String
    var parentName: String
    var parentPhone: String
    var hourlyRate: Double
    var startDate: Date
    var colorIndex: Int
    var notes: String
    var isArchived: Bool
}

nonisolated struct LessonDTO: SyncRecord {
    var clientId: UUID
    var clientUpdatedAt: Date = .now
    var deletedAt: Date?

    var studentClientId: UUID?
    var templateClientId: UUID?
    var date: Date
    var duration: Int
    var status: String
    var cancellationReason: String
    var topic: String
    var note: String
    var feeOverride: Double?
    var usesCustomFee: Bool
}

nonisolated struct PaymentDTO: SyncRecord {
    var clientId: UUID
    var clientUpdatedAt: Date = .now
    var deletedAt: Date?

    var studentClientId: UUID?
    var date: Date
    var amount: Double
    var method: String
    var note: String
}

nonisolated struct HomeworkDTO: SyncRecord {
    var clientId: UUID
    var clientUpdatedAt: Date = .now
    var deletedAt: Date?

    var studentClientId: UUID?
    var title: String
    var detail: String
    var assignedDate: Date
    var dueDate: Date
    var isDone: Bool
    var doneDate: Date?
}

nonisolated struct TemplateDTO: SyncRecord {
    var clientId: UUID
    var clientUpdatedAt: Date = .now
    var deletedAt: Date?

    var studentClientId: UUID?
    var weekday: Int
    var hour: Int
    var minute: Int
    var duration: Int
    var feeOverride: Double?
    var usesCustomFee: Bool
    var isPaused: Bool
    var generatedUntil: Date?
}

/// İtme ve çekme gövdesi aynı biçimi paylaşır.
nonisolated struct SyncPayload: Codable {
    var students: [StudentDTO] = []
    var lessons: [LessonDTO] = []
    var payments: [PaymentDTO] = []
    var homeworks: [HomeworkDTO] = []
    var templates: [TemplateDTO] = []

    var isEmpty: Bool {
        students.isEmpty && lessons.isEmpty && payments.isEmpty
            && homeworks.isEmpty && templates.isEmpty
    }

    var count: Int {
        students.count + lessons.count + payments.count + homeworks.count + templates.count
    }
}

nonisolated struct PullResponse: Decodable {
    var cursor: String
    var students: [StudentDTO] = []
    var lessons: [LessonDTO] = []
    var payments: [PaymentDTO] = []
    var homeworks: [HomeworkDTO] = []
    var templates: [TemplateDTO] = []
}

nonisolated struct PushResponse: Decodable {
    var ok: Bool
    var applied: Int
}
