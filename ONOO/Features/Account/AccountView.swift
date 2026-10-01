//
//  AccountView.swift
//  One — Ders Defteri
//
//  Eşitleme durumu, hesap, çıkış ve hesap silme.
//

import StoreKit
import SwiftUI

struct AccountView: View {
    @Environment(AuthManager.self) private var auth
    @Environment(ProStore.self) private var proStore
    @Environment(\.dismiss) private var dismiss

    @State private var sync = SyncEngine.shared
    @State private var showSignIn = false
    @State private var showPaywall = false
    @State private var confirmDelete = false
    @State private var confirmSignOut = false
    @State private var isDeleting = false
    @State private var deleteError: String?
    @State private var showManageSubscriptions = false

    var body: some View {
        Form {
            syncSection

            if auth.state == .signedIn {
                Section {
                    HStack(spacing: 12) {
                        Image(systemName: "apple.logo")
                            .font(.title3)
                            .foregroundStyle(Theme.card)
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(Theme.ink))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(auth.displayName.flatMap { $0.isEmpty ? nil : $0 } ?? "Apple hesabı")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.ink)
                            Text("Apple ile giriş yapıldı")
                                .font(.caption)
                                .foregroundStyle(Theme.inkSoft)
                        }
                    }
                    .padding(.vertical, 2)
                    Button {
                        confirmSignOut = true
                    } label: {
                        Label("Çıkış Yap", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                } header: {
                    Text("Hesap")
                } footer: {
                    Text("Çıkış yapsan da defterin cihazında kalır.")
                }

                Section {
                    Button(role: .destructive) {
                        confirmDelete = true
                    } label: {
                        if isDeleting {
                            ProgressView()
                        } else {
                            Label("Hesabı Sil", systemImage: "trash")
                        }
                    }
                    .disabled(isDeleting)
                    if let deleteError {
                        Text(deleteError)
                            .font(.caption)
                            .foregroundStyle(Theme.red)
                    }
                    if proStore.showsSubscriptionManagement {
                        Button {
                            showManageSubscriptions = true
                        } label: {
                            Label("Aboneliği Yönet", systemImage: "creditcard")
                        }
                    }
                } footer: {
                    Text("Hesabın ve sunucudaki tüm kayıtların kalıcı olarak silinir. Bu işlem geri alınamaz. Cihazındaki defter silinmez.\n\nHesabı silmek aboneliği iptal etmez. Aktif bir aboneliğin varsa App Store'dan ayrıca iptal etmelisin.")
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.paper)
        .navigationTitle("Eşitleme")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showSignIn) { SignInView() }
        .sheet(isPresented: $showPaywall) { PaywallView() }
        .manageSubscriptionsSheet(isPresented: $showManageSubscriptions)
        .confirmationDialog("Çıkış yapılsın mı? Defterin cihazda kalır.",
                            isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Çıkış Yap", role: .destructive) {
                Task { await auth.signOut() }
            }
            Button("Vazgeç", role: .cancel) {}
        }
        .confirmationDialog("Hesabın ve sunucudaki tüm kayıtların kalıcı olarak silinecek. Aboneliğin varsa bu işlem onu iptal etmez. Emin misin?",
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Hesabı Sil", role: .destructive) {
                Task { await performDelete() }
            }
            Button("Vazgeç", role: .cancel) {}
        }
    }

    /// Eşitlemenin üç hâli var: abonelik yok, abonelik var ama hesap yok,
    /// ve çalışır durumda. Her biri kullanıcıya tek bir sonraki adım gösterir.
    @ViewBuilder
    private var syncSection: some View {
        if !proStore.isPro {
            Section {
                Button {
                    showPaywall = true
                } label: {
                    HStack {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Eşitlemeyi Aç")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundStyle(Theme.ink)
                                Text("Ders Defteri Pro ile kayıtların hesabına yedeklenir")
                                    .font(.caption)
                                    .foregroundStyle(Theme.inkSoft)
                            }
                        } icon: {
                            Image(systemName: "crown.fill")
                                .foregroundStyle(Theme.amber)
                        }
                        Spacer()
                        Image(systemName: "lock.fill")
                            .font(.caption)
                            .foregroundStyle(Theme.amber)
                    }
                }
                .buttonStyle(.plain)
            } header: {
                Text("Eşitleme")
            } footer: {
                Text("Şu an defterin yalnızca bu cihazda tutuluyor. Telefonun kaybolur ya da uygulama silinirse kayıtların geri getirilemez.")
            }
        } else if auth.state != .signedIn {
            Section {
                Button {
                    showSignIn = true
                } label: {
                    Label("Apple ile Giriş Yap", systemImage: "apple.logo")
                        .font(.subheadline.weight(.semibold))
                }
            } header: {
                Text("Eşitleme")
            } footer: {
                Text("Aboneliğin aktif. Eşitlemeyi başlatmak için giriş yapman yeterli; mevcut kayıtların hesabına yüklenir.")
            }
        } else {
            Section {
                // Göreli zaman ("2 dakika önce") yarım dakikada bir tazelenir.
                TimelineView(.periodic(from: .now, by: 30)) { context in
                    syncStatusRow(now: context.date)
                }
                Button {
                    Task { await sync.sync(isPro: proStore.isPro) }
                } label: {
                    Label("Şimdi Eşitle", systemImage: "arrow.triangle.2.circlepath")
                }
                .disabled(sync.status == .syncing)
            } header: {
                Text("Eşitleme")
            } footer: {
                Text("Kayıtların cihazında saklanır ve hesabına eşitlenir. Yeni bir telefona geçtiğinde aynı Apple hesabıyla giriş yapman yeterli.")
            }
        }
    }

    private func performDelete() async {
        isDeleting = true
        deleteError = nil
        do {
            try await auth.deleteAccount()
        } catch {
            deleteError = (error as? LocalizedError)?.errorDescription
                ?? "Hesap silinemedi. Lütfen tekrar dene."
        }
        isDeleting = false
    }

    private func syncStatusRow(now: Date) -> some View {
        HStack(spacing: 12) {
            Image(systemName: syncIcon)
                .font(.title3)
                .foregroundStyle(syncTint)
                .symbolEffect(.pulse, isActive: sync.status == .syncing)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(syncTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                if let detail = syncDetail(now: now) {
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                }
            }
        }
        .padding(.vertical, 2)
    }

    private var syncIcon: String {
        switch sync.status {
        case .synced: return "checkmark.icloud.fill"
        case .syncing: return "arrow.triangle.2.circlepath.icloud.fill"
        case .failed: return "exclamationmark.icloud.fill"
        case .disabled, .needsAccount: return "icloud.slash.fill"
        case .idle: return "icloud.fill"
        }
    }

    private var syncTitle: String {
        switch sync.status {
        case .synced: return "Eşitlendi"
        case .syncing: return "Eşitleniyor…"
        case .failed: return "Eşitlenemedi"
        case .disabled: return "Eşitleme kapalı"
        case .needsAccount: return "Giriş gerekli"
        case .idle: return "Eşitleme bekliyor"
        }
    }

    private func syncDetail(now: Date) -> String? {
        switch sync.status {
        case .synced(let date):
            if now.timeIntervalSince(date) < 60 { return "Az önce" }
            return Self.relative.localizedString(for: date, relativeTo: now)
        case .failed(let message):
            return message
        default:
            return nil
        }
    }

    private static let relative: RelativeDateTimeFormatter = {
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.unitsStyle = .full
        return f
    }()

    private var syncTint: Color {
        switch sync.status {
        case .failed: return Theme.red
        case .synced: return Theme.green
        default: return Theme.inkSoft
        }
    }
}
