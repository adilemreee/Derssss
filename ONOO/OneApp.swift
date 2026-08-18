//
//  OneApp.swift
//  One — Ders Defteri
//

import SwiftUI
import SwiftData

@main
struct OneApp: App {
    let container: ModelContainer
    @Environment(\.scenePhase) private var scenePhase
    @State private var showSplash = true
    @State private var proStore = ProStore()
    @State private var auth = AuthManager()
    @State private var sync = SyncEngine.shared
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false

    init() {
        let schema = Schema([Student.self, Lesson.self, Payment.self,
                             Homework.self, RecurringLessonTemplate.self])
        do {
            // Veriler cihazda tutulur ve kullanıcının hesabına eşitlenir.
            // CloudKit yerine kendi sunucumuz kullanıldığı için burada
            // bulut yapılandırması yok.
            let config = ModelConfiguration(schema: schema, cloudKitDatabase: .none)
            container = try ModelContainer(for: schema, configurations: [config])
        } catch {
            fatalError("Model container oluşturulamadı: \(error)")
        }
        Self.configureAppearance()
    }

    var body: some Scene {
        WindowGroup {
            // Uygulama hesapsız da tam çalışır: defter cihazda tutulur.
            // Hesap yalnızca eşitleme için gerekir ve o da Pro'ya aittir.
            // Kullanamayacağı bir özellik için kullanıcıyı kayda zorlamak
            // hem gereksiz sürtünme hem App Store yönergesi 5.1.1(v) ihlali.
            ZStack {
                ContentView()
                if !hasCompletedOnboarding {
                    OnboardingView()
                        .transition(.opacity)
                        .zIndex(1)
                }
                if showSplash {
                    SplashView()
                        .transition(.opacity)
                        .zIndex(2)
                }
            }
            .task {
                SyncEngine.shared.configure(context: container.mainContext)
                SyncIdentity.assignMissingUUIDs(context: container.mainContext)
                await auth.restoreSession()

                try? await Task.sleep(for: .seconds(1.6))
                withAnimation(.easeOut(duration: 0.5)) {
                    showSplash = false
                }
            }
            .task(id: auth.state) {
                guard auth.state == .signedIn else { return }
                await proStore.refreshFromServer()
                await sync.sync(isPro: proStore.isPro)
            }
            .environment(proStore)
            .environment(auth)
        }
        .modelContainer(container)
        .onChange(of: scenePhase) { _, phase in
            // Uygulama öne geldiğinde ve arkaya giderken eşitlenir; böylece
            // başka bir cihazdaki değişiklik açılışta görünür ve bu cihazdaki
            // değişiklik kapanmadan gönderilir.
            guard phase == .active || phase == .background else { return }
            Task { await sync.sync(isPro: proStore.isPro) }
        }
    }

    /// Navigasyon başlıklarına "defter" hissi veren serif yazı tipi
    private static func configureAppearance() {
        func serif(_ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
            let base = UIFont.systemFont(ofSize: size, weight: weight)
            if let descriptor = base.fontDescriptor.withDesign(.serif) {
                return UIFont(descriptor: descriptor, size: size)
            }
            return base
        }
        let nav = UINavigationBar.appearance()
        nav.largeTitleTextAttributes = [
            .font: serif(32, .bold),
            .foregroundColor: Theme.inkUI
        ]
        nav.titleTextAttributes = [
            .font: serif(17, .semibold),
            .foregroundColor: Theme.inkUI
        ]
    }
}

/// Senkronizasyon kimliklerinin bir kereye mahsus onarımı.
///
/// `uuid` alanı mevcut kurulumlara sonradan eklendi. SwiftData hafif göç
/// sırasında eski satırlara varsayılan değeri yazar ve bu değer tüm satırlarda
/// aynı olabilir. Aynı kimlikli iki kayıt sunucuda birbirinin üstüne yazacağı
/// için, çakışan kimlikler burada yeniden dağıtılır.
enum SyncIdentity {
    static func assignMissingUUIDs(context: ModelContext) {
        var seen = Set<UUID>()
        var didChange = false

        func repair<T: PersistentModel>(_ items: [T], uuid: ReferenceWritableKeyPath<T, UUID>) {
            for item in items {
                let current = item[keyPath: uuid]
                if seen.contains(current) {
                    item[keyPath: uuid] = UUID()
                    didChange = true
                }
                seen.insert(item[keyPath: uuid])
            }
        }

        repair(context.fetchAll(Student.self), uuid: \.uuid)
        repair(context.fetchAll(Lesson.self), uuid: \.uuid)
        repair(context.fetchAll(Payment.self), uuid: \.uuid)
        repair(context.fetchAll(Homework.self), uuid: \.uuid)
        repair(context.fetchAll(RecurringLessonTemplate.self), uuid: \.uuid)

        if didChange { try? context.save() }
    }
}
