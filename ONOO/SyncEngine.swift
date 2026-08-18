//
//  SyncEngine.swift
//  One — Ders Defteri
//
//  Yerel SwiftData deposunu sunucuyla eşitler.
//
//  Tasarım: uygulama kodunun hiçbir yerinde "bu kayıt değişti" işareti
//  tutulmaz. Her eşitlemede yerel kayıtların içerik özeti çıkarılır ve bir
//  önceki başarılı itmedeki özetlerle karşılaştırılır. Bunun iki faydası var:
//
//   1. Mevcut 15 silme ve 37 kayıt noktasının hiçbirine dokunmak gerekmez;
//      unutulan bir çağrı yüzünden sessizce bozulan senkronizasyon olmaz.
//   2. Öğrenci silinince zincirleme silinen dersler/ödemeler de kendiliğinden
//      yakalanır — SwiftData bunları haber vermeden siler.
//

import Foundation
import CryptoKit
import SwiftData

@MainActor
@Observable
final class SyncEngine {
    static let shared = SyncEngine()

    enum Status: Equatable {
        case idle
        /// Abonelik yok — eşitleme kapalı, defter yalnızca cihazda.
        case disabled
        /// Abonelik var ama henüz giriş yapılmamış.
        case needsAccount
        case syncing
        case failed(String)
        case synced(Date)
    }

    private(set) var status: Status = .idle

    private var context: ModelContext?
    private var state = SyncState.load()
    private var inFlight = false

    private init() {}

    func configure(context: ModelContext) {
        self.context = context
    }

    /// Oturum kapanınca yerel eşitleme defteri sıfırlanır; başka bir hesapla
    /// girildiğinde önceki hesabın kayıtları sunucuya itilmemeli.
    func reset() {
        state = SyncState()
        state.save()
        status = .idle
    }

    // MARK: - Ana akış

    /// Eşitleme Pro'ya aittir ve hesap gerektirir.
    ///
    /// Abonelik sona erdiğinde eşitleme durur ama hiçbir şey silinmez: defter
    /// cihazda olduğu gibi kalır, sunucudaki kopya da durur. Yeniden abone
    /// olunduğunda kaldığı yerden devam eder.
    func sync(isPro: Bool) async {
        guard isPro else {
            status = .disabled
            return
        }
        guard !inFlight, let context else { return }
        guard await APIClient.shared.isSignedIn else {
            status = .needsAccount
            return
        }

        inFlight = true
        status = .syncing
        defer { inFlight = false }

        do {
            try await push(context: context)
            try await pull(context: context)
            state.save()
            status = .synced(Date())
        } catch {
            let message = (error as? LocalizedError)?.errorDescription ?? "Eşitlenemedi."
            status = .failed(message)
        }
    }

    // MARK: - İtme

    private func push(context: ModelContext) async throws {
        let snapshot = Snapshot(context: context)
        var payload = SyncPayload()
        var seen = Set<String>()
        let now = Date()

        // Özeti değişen kayıtlar gönderilir; değişmeyenler ağdan hiç geçmez.
        func collect<T: SyncRecord>(_ records: [T], into list: inout [T]) {
            for var record in records {
                let key = record.clientId.uuidString
                seen.insert(key)
                let digest = Self.digest(of: record)
                guard state.hashes[key] != digest else { continue }
                record.clientUpdatedAt = now
                list.append(record)
            }
        }

        collect(snapshot.students, into: &payload.students)
        collect(snapshot.lessons, into: &payload.lessons)
        collect(snapshot.payments, into: &payload.payments)
        collect(snapshot.homeworks, into: &payload.homeworks)
        collect(snapshot.templates, into: &payload.templates)

        // Defterde olup artık cihazda olmayan her kayıt silinmiş demektir.
        let deleted = Set(state.hashes.keys).subtracting(seen)
        for key in deleted {
            guard let uuid = UUID(uuidString: key), let kind = state.kinds[key] else { continue }
            appendTombstone(kind: kind, uuid: uuid, at: now, to: &payload)
        }

        guard !payload.isEmpty else { return }

        let _: PushResponse = try await APIClient.shared.request(
            "/v1/sync", method: "POST", body: payload
        )

        // Yalnız sunucu kabul ettikten sonra defter güncellenir; istek
        // başarısız olursa aynı değişiklikler bir dahaki sefere tekrar gider.
        for record in payload.students where record.deletedAt == nil {
            state.remember(record, kind: .student)
        }
        for record in payload.lessons where record.deletedAt == nil {
            state.remember(record, kind: .lesson)
        }
        for record in payload.payments where record.deletedAt == nil {
            state.remember(record, kind: .payment)
        }
        for record in payload.homeworks where record.deletedAt == nil {
            state.remember(record, kind: .homework)
        }
        for record in payload.templates where record.deletedAt == nil {
            state.remember(record, kind: .template)
        }
        for key in deleted {
            state.forget(key)
        }
    }

    private func appendTombstone(kind: RecordKind, uuid: UUID, at now: Date, to payload: inout SyncPayload) {
        switch kind {
        case .student:
            payload.students.append(StudentDTO(
                clientId: uuid, clientUpdatedAt: now, deletedAt: now,
                name: "", subject: "", grade: "", phone: "", parentName: "", parentPhone: "",
                hourlyRate: 0, startDate: now, colorIndex: 0, notes: "", isArchived: false))
        case .lesson:
            payload.lessons.append(LessonDTO(
                clientId: uuid, clientUpdatedAt: now, deletedAt: now,
                date: now, duration: 60, status: "planned", cancellationReason: "none",
                topic: "", note: "", usesCustomFee: false))
        case .payment:
            payload.payments.append(PaymentDTO(
                clientId: uuid, clientUpdatedAt: now, deletedAt: now,
                date: now, amount: 0, method: "cash", note: ""))
        case .homework:
            payload.homeworks.append(HomeworkDTO(
                clientId: uuid, clientUpdatedAt: now, deletedAt: now,
                title: "", detail: "", assignedDate: now, dueDate: now, isDone: false))
        case .template:
            payload.templates.append(TemplateDTO(
                clientId: uuid, clientUpdatedAt: now, deletedAt: now,
                weekday: 3, hour: 17, minute: 0, duration: 60,
                usesCustomFee: false, isPaused: false))
        }
    }

    // MARK: - Çekme

    private func pull(context: ModelContext) async throws {
        var query: [URLQueryItem] = []
        if let cursor = state.cursor {
            query.append(URLQueryItem(name: "since", value: cursor))
        }

        let response: PullResponse = try await APIClient.shared.request("/v1/sync", query: query)

        // Öğrenciler önce uygulanır; ders, ödeme ve ödevler onlara bağlanır.
        applyStudents(response.students, context: context)
        let students = Self.map(context.fetchAll(Student.self), by: \.uuid)
        let templates = applyTemplates(response.templates, students: students, context: context)
        applyLessons(response.lessons, students: students, templates: templates, context: context)
        applyPayments(response.payments, students: students, context: context)
        applyHomeworks(response.homeworks, students: students, context: context)

        try? context.save()
        state.cursor = response.cursor

        // Sunucudan gelen hâli deftere yaz; yoksa bir sonraki itmede aynı
        // kayıtlar değişmiş sanılıp geri gönderilir.
        let snapshot = Snapshot(context: context)
        for record in snapshot.students { state.remember(record, kind: .student) }
        for record in snapshot.lessons { state.remember(record, kind: .lesson) }
        for record in snapshot.payments { state.remember(record, kind: .payment) }
        for record in snapshot.homeworks { state.remember(record, kind: .homework) }
        for record in snapshot.templates { state.remember(record, kind: .template) }
    }

    private func applyStudents(_ records: [StudentDTO], context: ModelContext) {
        var existing = Self.map(context.fetchAll(Student.self), by: \.uuid)
        for dto in records {
            if dto.deletedAt != nil {
                if let model = existing[dto.clientId] { context.delete(model) }
                state.forget(dto.clientId.uuidString)
                existing[dto.clientId] = nil
                continue
            }
            let model = existing[dto.clientId] ?? {
                let new = Student(name: dto.name)
                new.uuid = dto.clientId
                context.insert(new)
                existing[dto.clientId] = new
                return new
            }()
            model.name = dto.name
            model.subject = dto.subject
            model.grade = dto.grade
            model.phone = dto.phone
            model.parentName = dto.parentName
            model.parentPhone = dto.parentPhone
            model.hourlyRate = dto.hourlyRate
            model.startDate = dto.startDate
            model.colorIndex = dto.colorIndex
            model.notes = dto.notes
            model.isArchived = dto.isArchived
        }
    }

    private func applyTemplates(_ records: [TemplateDTO],
                                students: [UUID: Student],
                                context: ModelContext) -> [UUID: RecurringLessonTemplate] {
        var existing = Self.map(context.fetchAll(RecurringLessonTemplate.self), by: \.uuid)
        for dto in records {
            if dto.deletedAt != nil {
                if let model = existing[dto.clientId] { context.delete(model) }
                state.forget(dto.clientId.uuidString)
                existing[dto.clientId] = nil
                continue
            }
            let model = existing[dto.clientId] ?? {
                let new = RecurringLessonTemplate()
                new.uuid = dto.clientId
                context.insert(new)
                existing[dto.clientId] = new
                return new
            }()
            model.weekday = dto.weekday
            model.hour = dto.hour
            model.minute = dto.minute
            model.duration = dto.duration
            model.feeOverride = dto.feeOverride
            model.usesCustomFee = dto.usesCustomFee
            model.isPaused = dto.isPaused
            model.generatedUntil = dto.generatedUntil
            model.student = dto.studentClientId.flatMap { students[$0] }
        }
        return existing
    }

    private func applyLessons(_ records: [LessonDTO],
                              students: [UUID: Student],
                              templates: [UUID: RecurringLessonTemplate],
                              context: ModelContext) {
        var existing = Self.map(context.fetchAll(Lesson.self), by: \.uuid)
        for dto in records {
            if dto.deletedAt != nil {
                if let model = existing[dto.clientId] { context.delete(model) }
                state.forget(dto.clientId.uuidString)
                existing[dto.clientId] = nil
                continue
            }
            let model = existing[dto.clientId] ?? {
                let new = Lesson(date: dto.date)
                new.uuid = dto.clientId
                context.insert(new)
                existing[dto.clientId] = new
                return new
            }()
            model.date = dto.date
            model.duration = dto.duration
            model.statusRaw = dto.status
            model.cancellationReasonRaw = dto.cancellationReason
            model.topic = dto.topic
            model.note = dto.note
            model.feeOverride = dto.feeOverride
            model.usesCustomFee = dto.usesCustomFee
            model.student = dto.studentClientId.flatMap { students[$0] }
            model.sourceTemplate = dto.templateClientId.flatMap { templates[$0] }
        }
    }

    private func applyPayments(_ records: [PaymentDTO],
                               students: [UUID: Student],
                               context: ModelContext) {
        var existing = Self.map(context.fetchAll(Payment.self), by: \.uuid)
        for dto in records {
            if dto.deletedAt != nil {
                if let model = existing[dto.clientId] { context.delete(model) }
                state.forget(dto.clientId.uuidString)
                existing[dto.clientId] = nil
                continue
            }
            let model = existing[dto.clientId] ?? {
                let new = Payment(amount: dto.amount)
                new.uuid = dto.clientId
                context.insert(new)
                existing[dto.clientId] = new
                return new
            }()
            model.date = dto.date
            model.amount = dto.amount
            model.methodRaw = dto.method
            model.note = dto.note
            model.student = dto.studentClientId.flatMap { students[$0] }
        }
    }

    private func applyHomeworks(_ records: [HomeworkDTO],
                                students: [UUID: Student],
                                context: ModelContext) {
        var existing = Self.map(context.fetchAll(Homework.self), by: \.uuid)
        for dto in records {
            if dto.deletedAt != nil {
                if let model = existing[dto.clientId] { context.delete(model) }
                state.forget(dto.clientId.uuidString)
                existing[dto.clientId] = nil
                continue
            }
            let model = existing[dto.clientId] ?? {
                let new = Homework(title: dto.title, dueDate: dto.dueDate)
                new.uuid = dto.clientId
                context.insert(new)
                existing[dto.clientId] = new
                return new
            }()
            model.title = dto.title
            model.detail = dto.detail
            model.assignedDate = dto.assignedDate
            model.dueDate = dto.dueDate
            model.isDone = dto.isDone
            model.doneDate = dto.doneDate
            model.student = dto.studentClientId.flatMap { students[$0] }
        }
    }

    // MARK: - Yardımcılar

    private static func map<T>(_ items: [T], by key: KeyPath<T, UUID>) -> [UUID: T] {
        Dictionary(items.map { ($0[keyPath: key], $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// Kaydın içerik özeti. `clientUpdatedAt` bilerek dışarıda bırakılır:
    /// yoksa her eşitlemede damga değişir ve hiçbir şey değişmemiş kayıtlar
    /// sonsuza dek yeniden gönderilir.
    nonisolated static func digest<T: SyncRecord>(of record: T) -> String {
        var copy = record
        copy.clientUpdatedAt = Date(timeIntervalSince1970: 0)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(copy) else { return UUID().uuidString }
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

// MARK: - Yerel eşitleme defteri

nonisolated enum RecordKind: String, Codable {
    case student, lesson, payment, homework, template
}

/// Son başarılı eşitlemenin hafızası. SwiftData'ya değil dosyaya yazılır:
/// bu veri kullanıcının değil senkronizasyonun defteri, sunucuya gitmez.
nonisolated struct SyncState: Codable {
    var cursor: String?
    var hashes: [String: String] = [:]
    var kinds: [String: RecordKind] = [:]

    mutating func remember<T: SyncRecord>(_ record: T, kind: RecordKind) {
        let key = record.clientId.uuidString
        hashes[key] = SyncEngine.digest(of: record)
        kinds[key] = kind
    }

    mutating func forget(_ key: String) {
        hashes[key] = nil
        kinds[key] = nil
    }

    private static var url: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appending(path: "sync-state.json")
    }

    static func load() -> SyncState {
        guard let data = try? Data(contentsOf: url),
              let state = try? JSONDecoder().decode(SyncState.self, from: data) else {
            return SyncState()
        }
        return state
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        try? data.write(to: Self.url, options: .atomic)
    }
}

// MARK: - Yerel anlık görüntü

private struct Snapshot {
    let students: [StudentDTO]
    let lessons: [LessonDTO]
    let payments: [PaymentDTO]
    let homeworks: [HomeworkDTO]
    let templates: [TemplateDTO]

    init(context: ModelContext) {
        students = context.fetchAll(Student.self).map { s in
            StudentDTO(clientId: s.uuid, deletedAt: nil,
                       name: s.name, subject: s.subject, grade: s.grade, phone: s.phone,
                       parentName: s.parentName, parentPhone: s.parentPhone,
                       hourlyRate: s.hourlyRate, startDate: s.startDate,
                       colorIndex: s.colorIndex, notes: s.notes, isArchived: s.isArchived)
        }
        lessons = context.fetchAll(Lesson.self).map { l in
            LessonDTO(clientId: l.uuid, deletedAt: nil,
                      studentClientId: l.student?.uuid, templateClientId: l.sourceTemplate?.uuid,
                      date: l.date, duration: l.duration, status: l.statusRaw,
                      cancellationReason: l.cancellationReasonRaw, topic: l.topic, note: l.note,
                      feeOverride: l.feeOverride, usesCustomFee: l.usesCustomFee)
        }
        payments = context.fetchAll(Payment.self).map { p in
            PaymentDTO(clientId: p.uuid, deletedAt: nil,
                       studentClientId: p.student?.uuid, date: p.date,
                       amount: p.amount, method: p.methodRaw, note: p.note)
        }
        homeworks = context.fetchAll(Homework.self).map { h in
            HomeworkDTO(clientId: h.uuid, deletedAt: nil,
                        studentClientId: h.student?.uuid, title: h.title, detail: h.detail,
                        assignedDate: h.assignedDate, dueDate: h.dueDate,
                        isDone: h.isDone, doneDate: h.doneDate)
        }
        templates = context.fetchAll(RecurringLessonTemplate.self).map { t in
            TemplateDTO(clientId: t.uuid, deletedAt: nil,
                        studentClientId: t.student?.uuid, weekday: t.weekday, hour: t.hour,
                        minute: t.minute, duration: t.duration, feeOverride: t.feeOverride,
                        usesCustomFee: t.usesCustomFee, isPaused: t.isPaused,
                        generatedUntil: t.generatedUntil)
        }
    }
}

extension ModelContext {
    func fetchAll<T: PersistentModel>(_ type: T.Type) -> [T] {
        (try? fetch(FetchDescriptor<T>())) ?? []
    }
}
