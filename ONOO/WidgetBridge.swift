//
//  WidgetBridge.swift
//  One — Ders Defteri
//
//  Widget'a gidecek ders özetini yazar ve widget'ta "İşlendi" denen dersleri
//  asıl kayda işler.
//

import Foundation
import SwiftData
import WidgetKit

enum WidgetBridge {
    /// Önce widget'tan gelen işaretler işlenir, sonra güncel özet yazılır;
    /// aksi halde eski özet kuyruktaki işaretin üstüne yazılırdı.
    @MainActor
    static func refresh(context: ModelContext) {
        applyPendingCompletions(context: context)
        publish(context: context)
    }

    /// Widget'taki "İşlendi" düğmesi yalnızca kuyruğa yazar. Ders bu arada
    /// silinmiş ya da iptal edilmişse dokunulmaz.
    @MainActor
    static func applyPendingCompletions(context: ModelContext) {
        let pending = WidgetStore.loadPending()
        guard !pending.isEmpty else { return }
        let ids = Set(pending.compactMap(UUID.init(uuidString:)))
        let lessons = (try? context.fetch(FetchDescriptor<Lesson>(predicate: #Predicate { ids.contains($0.uuid) }))) ?? []
        var changed = false
        for lesson in lessons where lesson.status == .planned {
            lesson.status = .completed
            changed = true
        }
        if changed { try? context.save() }
        WidgetStore.removePending(pending)
    }

    @MainActor
    static func publish(context: ModelContext) {
        let now = Date()
        let from = now.startOfDay.adding(days: -1)
        let to = now.startOfDay.adding(days: 8)
        let cancelledRaw = LessonStatus.cancelled.rawValue
        let plannedRaw = LessonStatus.planned.rawValue

        let descriptor = FetchDescriptor<Lesson>(
            predicate: #Predicate { $0.date >= from && $0.date < to && $0.statusRaw != cancelledRaw },
            sortBy: [SortDescriptor(\.date)]
        )
        let lessons = ((try? context.fetch(descriptor)) ?? [])
            .filter { !($0.student?.isArchived ?? false) }

        let olderDescriptor = FetchDescriptor<Lesson>(
            predicate: #Predicate { $0.date < from && $0.statusRaw == plannedRaw }
        )
        let olderUnmarked = (try? context.fetchCount(olderDescriptor)) ?? 0

        let snapshot = WidgetSnapshot(
            generatedAt: now,
            lessons: lessons.map { lesson in
                WidgetLesson(id: lesson.uuid.uuidString,
                             date: lesson.date,
                             duration: lesson.duration,
                             studentName: lesson.student?.name ?? "Öğrenci",
                             subject: lesson.student?.subject ?? "",
                             colorIndex: lesson.student?.colorIndex ?? 0,
                             status: lesson.statusRaw)
            },
            olderUnmarkedCount: olderUnmarked
        )
        WidgetStore.save(snapshot)
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetStore.widgetKind)
    }
}
