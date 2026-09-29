//
//  ContentView.swift
//  One — Ders Defteri
//

import SwiftUI
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var context
    @Environment(ProStore.self) private var proStore
    @AppStorage("didMigrateLockedLessonFeesV1") private var didMigrateLockedLessonFees = false
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @State private var resyncTask: Task<Void, Never>?

    var body: some View {
        TabView {
            Tab("Özet", systemImage: "square.grid.2x2.fill") {
                DashboardView()
            }
            Tab("Öğrenciler", systemImage: "graduationcap.fill") {
                StudentsView()
            }
            Tab("Program", systemImage: "calendar") {
                ScheduleView()
            }
            Tab("Ödemeler", systemImage: "wallet.bifold") {
                PaymentsView()
            }
            Tab("Ödevler", systemImage: "checklist") {
                HomeworkView()
            }
        }
        .tint(Theme.accent)
        .environment(\.locale, Locale(identifier: "tr_TR"))
        .task {
            if !didMigrateLockedLessonFees {
                LessonFeeMigration.lockExistingFees(context: context)
                didMigrateLockedLessonFees = true
            }
            // Eşitleme açıksa bu işi eşitleme motoru, sunucudan güncel hâli
            // aldıktan sonra yapar.
            if !(await SyncEngine.shared.handlesRecurringLessons(isPro: proStore.isPro)) {
                RecurringLessons.topUp(context: context)
            }
            // İzin, tanıtım bitmeden sorulursa uygulamayı daha görmemiş
            // kullanıcının önüne açılışta bir sistem penceresi çıkar.
            if hasCompletedOnboarding {
                await AppNotifications.requestPermissionIfNeeded()
            }
            await AppNotifications.resync(context: context)
            WidgetBridge.refresh(context: context)
        }
        .onChange(of: hasCompletedOnboarding) { _, completed in
            guard completed else { return }
            Task {
                await AppNotifications.requestPermissionIfNeeded()
                await AppNotifications.resync(context: context)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: ModelContext.didSave)) { _ in
            scheduleResync()
        }
    }

    /// Art arda kayıtlarda bildirimleri tek seferde yeniden kurmak için kısa bir bekleme
    private func scheduleResync() {
        resyncTask?.cancel()
        resyncTask = Task {
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            await AppNotifications.resync(context: context)
            WidgetBridge.publish(context: context)
        }
    }
}

enum LessonFeeMigration {
    static func lockExistingFees(context: ModelContext) {
        let lessons = (try? context.fetch(FetchDescriptor<Lesson>())) ?? []
        let templates = (try? context.fetch(FetchDescriptor<RecurringLessonTemplate>())) ?? []
        var didChange = false

        for lesson in lessons {
            if lesson.feeOverride == nil {
                lesson.lockCurrentStandardFeeIfNeeded()
            } else {
                lesson.usesCustomFee = true
            }
            didChange = true
        }

        for template in templates {
            template.usesCustomFee = template.feeOverride != nil
            didChange = true
        }

        if didChange {
            try? context.save()
        }
    }
}

#Preview {
    ContentView()
        .modelContainer(for: [Student.self, Lesson.self, Payment.self, Homework.self, RecurringLessonTemplate.self], inMemory: true)
        .environment(ProStore())
        .environment(AuthManager())
}
