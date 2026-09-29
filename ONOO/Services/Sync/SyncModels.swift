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
    /// Toplu ödeme bağı
    var paymentClientId: UUID?
    var date: Date
    var duration: Int
    var status: String
    var cancellationReason: String
    var topic: String
    var note: String
    var feeOverride: Double?
    var usesCustomFee: Bool

    /// Diğer alanlar hazır kodlamayla aynı yazılır. `paymentClientId` boşken
    /// yalnızca sunucuya giderken açıkça `null` yazılır: dersi ödemeden
    /// çıkarmak sunucudaki bağı da silmeli. Özet çıkarılırken yazılmaz; yoksa
    /// ödemeye bağlı olmayan her dersin özeti değişir ve hepsi yeniden gider.
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(clientId, forKey: .clientId)
        try c.encode(clientUpdatedAt, forKey: .clientUpdatedAt)
        try c.encodeIfPresent(deletedAt, forKey: .deletedAt)
        try c.encodeIfPresent(studentClientId, forKey: .studentClientId)
        try c.encodeIfPresent(templateClientId, forKey: .templateClientId)
        if let paymentClientId {
            try c.encode(paymentClientId, forKey: .paymentClientId)
        } else if encoder.userInfo[.explicitNulls] as? Bool == true {
            try c.encodeNil(forKey: .paymentClientId)
        }
        try c.encode(date, forKey: .date)
        try c.encode(duration, forKey: .duration)
        try c.encode(status, forKey: .status)
        try c.encode(cancellationReason, forKey: .cancellationReason)
        try c.encode(topic, forKey: .topic)
        try c.encode(note, forKey: .note)
        try c.encodeIfPresent(feeOverride, forKey: .feeOverride)
        try c.encode(usesCustomFee, forKey: .usesCustomFee)
    }
}

extension CodingUserInfoKey {
    /// Sunucuya giden istekte boş bağların açıkça `null` yazılması
    nonisolated static let explicitNulls = CodingUserInfoKey(rawValue: "explicitNulls")!
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

    /// İtmede bir istekte gidecek en fazla kayıt. Sunucu tür başına 500
    /// kabul eder; tek istekte toplam 200 bununla birlikte gövdeyi de küçük
    /// tutar (yavaş bağlantıda zaman aşımına düşmesin).
    static let chunkSize = 200

    /// Değişiklikleri sunucunun kabul edeceği büyüklükte parçalara böler.
    ///
    /// Uzun süredir kullanılan bir defter ilk kez (ya da çıkış yapıp yeniden
    /// girince) eşitlenirken yüzlerce ders birden gider; tek istek sunucu
    /// sınırını aşıp reddedilince eşitleme hiç ilerlemiyordu.
    ///
    /// Sıra: öğrenciler, haftalık dersler, ödemeler, dersler, ödevler. Dersler
    /// öğrencilerine, haftalık derslerine ve toplu ödemelerine bağlanır; başka
    /// bir cihaz arada çekerse dersin bağlandığı kayıt orada zaten bulunur.
    func chunked(maxRecords: Int = SyncPayload.chunkSize) -> [SyncPayload] {
        precondition(maxRecords > 0)
        var chunks: [SyncPayload] = []
        var current = SyncPayload()

        func add<T>(_ items: [T], to keyPath: WritableKeyPath<SyncPayload, [T]>) {
            for item in items {
                if current.count >= maxRecords {
                    chunks.append(current)
                    current = SyncPayload()
                }
                current[keyPath: keyPath].append(item)
            }
        }

        add(students, to: \.students)
        add(templates, to: \.templates)
        add(payments, to: \.payments)
        add(lessons, to: \.lessons)
        add(homeworks, to: \.homeworks)
        if !current.isEmpty { chunks.append(current) }
        return chunks
    }
}

nonisolated struct PullResponse: Decodable {
    var cursor: String
    var students: [StudentDTO] = []
    var lessons: [LessonDTO] = []
    var payments: [PaymentDTO] = []
    var homeworks: [HomeworkDTO] = []
    var templates: [TemplateDTO] = []
    /// Derslerin ödeme bağını bilen sunucu `true` gönderir. Eski sunucu bu
    /// alanı hiç göndermez; o zaman cihazdaki bağlar olduğu gibi kalır.
    var lessonPayments: Bool? = nil
}

nonisolated struct PushResponse: Decodable {
    var ok: Bool
    var applied: Int
}
