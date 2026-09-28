//
//  WidgetShared.swift
//  One — Ders Defteri
//
//  Uygulama ile widget arasında paylaşılan küçük veri: önümüzdeki günlerin
//  dersleri ve widget'tan "İşlendi" denen derslerin kuyruğu. İkisi de App
//  Group klasöründe JSON olarak durur; widget uygulamanın veritabanını açmaz.
//

import Foundation

nonisolated struct WidgetLesson: Codable, Hashable, Identifiable {
    var id: String
    var date: Date
    var duration: Int
    var studentName: String
    var subject: String
    var colorIndex: Int
    /// "planned" | "completed"
    var status: String

    var endDate: Date { date.addingTimeInterval(Double(duration) * 60) }
    var isCompleted: Bool { status == "completed" }
    var needsMarking: Bool { !isCompleted && endDate <= Date() }
}

nonisolated struct WidgetSnapshot: Codable {
    var generatedAt: Date
    /// Dünden bir hafta sonrasına kadar, iptal edilmemiş dersler (tarihe göre sıralı).
    var lessons: [WidgetLesson]
    /// Dünden önce kalmış, hâlâ planlı dersler.
    var olderUnmarkedCount: Int

    static let empty = WidgetSnapshot(generatedAt: .distantPast, lessons: [], olderUnmarkedCount: 0)
}

nonisolated enum WidgetStore {
    static let appGroup = "group.adilemre.ONOOOO"
    static let widgetKind = "DersDefteriToday"

    private static var folder: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
    }

    private static var snapshotURL: URL? { folder?.appending(path: "widget-snapshot.json") }
    private static var pendingURL: URL? { folder?.appending(path: "widget-pending.json") }

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    // MARK: Özet

    /// Widget'ın gördüğü hâl: kuyruktaki dersler işlendi sayılır, böylece
    /// uygulama açılmadan önce de tik düğmesi kaybolur.
    static func loadSnapshot() -> WidgetSnapshot {
        guard let url = snapshotURL,
              let data = try? Data(contentsOf: url),
              var snapshot = try? decoder.decode(WidgetSnapshot.self, from: data) else {
            return .empty
        }
        let pending = Set(loadPending())
        if !pending.isEmpty {
            for index in snapshot.lessons.indices where pending.contains(snapshot.lessons[index].id) {
                snapshot.lessons[index].status = "completed"
            }
        }
        return snapshot
    }

    static func save(_ snapshot: WidgetSnapshot) {
        guard let url = snapshotURL, let data = try? encoder.encode(snapshot) else { return }
        try? data.write(to: url, options: .atomic)
    }

    // MARK: İşlendi kuyruğu

    static func loadPending() -> [String] {
        guard let url = pendingURL,
              let data = try? Data(contentsOf: url),
              let ids = try? decoder.decode([String].self, from: data) else { return [] }
        return ids
    }

    static func enqueueCompletion(_ id: String) {
        var ids = loadPending()
        guard !ids.contains(id) else { return }
        ids.append(id)
        writePending(ids)
    }

    /// Uygulama kuyruğu işledikten sonra yalnız işlediklerini siler; bu arada
    /// widget'tan yeni gelen işaret kaybolmaz.
    static func removePending(_ processed: [String]) {
        let remaining = loadPending().filter { !processed.contains($0) }
        writePending(remaining)
    }

    private static func writePending(_ ids: [String]) {
        guard let url = pendingURL, let data = try? encoder.encode(ids) else { return }
        try? data.write(to: url, options: .atomic)
    }

    // MARK: Renkler

    /// Öğrenci renkleri (uygulamadaki `Theme.palette` ile aynı sıra).
    static let paletteHex: [UInt32] = [0x3C66AE, 0xC3503E, 0x3E8E5F, 0xDF9E3B,
                                       0x7B5CB8, 0x2E8F9E, 0xC85C8E, 0x8A6D3B]
}
