//
//  AccountView.swift
//  One — Ders Defteri
//
//  Eşitleme durumu, hesap, çıkış ve hesap silme.
//

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

    var body: some View {
        Form {
            syncSection

            if auth.state == .signedIn {
                Section {
                    LabeledContent("Giriş") {
                        Label("Apple ile", systemImage: "apple.logo")
                            .foregroundStyle(Theme.inkSoft)
                    }
                    if let name = auth.displayName, !name.isEmpty {
                        LabeledContent("Ad", value: name)
                    }
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
                } footer: {
                    Text("Hesabın ve sunucudaki tüm kayıtların kalıcı olarak silinir. Bu işlem geri alınamaz. Cihazındaki defter silinmez.")
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.paper)
        .navigationTitle("Eşitleme")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showSignIn) { SignInView() }
        .sheet(isPresented: $showPaywall) { PaywallView() }
        .confirmationDialog("Çıkış yapılsın mı? Defterin cihazda kalır.",
                            isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Çıkış Yap", role: .destructive) {
                Task { await auth.signOut() }
            }
            Button("Vazgeç", role: .cancel) {}
        }
        .confirmationDialog("Hesabın ve sunucudaki tüm kayıtların kalıcı olarak silinecek. Emin misin?",
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
                LabeledContent("Durum") {
                    Text(syncText)
                        .foregroundStyle(syncTint)
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

    private var syncText: String {
        switch sync.status {
        case .idle: return "Bekliyor"
        case .disabled: return "Kapalı"
        case .needsAccount: return "Giriş gerekli"
        case .syncing: return "Eşitleniyor…"
        case .synced(let date): return "Son: \(Fmt.time.string(from: date))"
        case .failed(let message): return message
        }
    }

    private var syncTint: Color {
        switch sync.status {
        case .failed: return Theme.red
        case .synced: return Theme.green
        default: return Theme.inkSoft
        }
    }
}
