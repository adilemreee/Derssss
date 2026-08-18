//
//  SignInView.swift
//  One — Ders Defteri
//
//  Eşitlemeyi açmak için Apple ile Giriş.
//
//  Bu ekran bir kapı değil: uygulama hesapsız da tam çalışır. Buraya yalnızca
//  Pro abonesi eşitlemeyi açmak istediğinde gelinir.
//

import AuthenticationServices
import SwiftUI

struct SignInView: View {
    @Environment(AuthManager.self) private var auth
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    Chalkboard {
                        VStack(alignment: .leading, spacing: 12) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.system(size: 32))
                                .foregroundStyle(.white)
                            Text("Eşitlemeyi Aç")
                                .font(.title.weight(.bold))
                                .fontDesign(.serif)
                                .foregroundStyle(.white)
                            Text("Defterin hesabına yedeklensin, yeni telefonda kaldığın yerden devam et.")
                                .font(.subheadline)
                                .foregroundStyle(.white.opacity(0.85))
                        }
                    }
                    .padding(.horizontal, 4)

                    VStack(alignment: .leading, spacing: 18) {
                        feature("iphone.and.arrow.forward", "Yeni telefona geçiş",
                                "Giriş yap, öğrencilerin ve kayıtların geri gelsin.")
                        feature("lock.shield.fill", "Şifre yok",
                                "Apple ile tek dokunuşta giriş. E-postanı gizlemeyi seçebilirsin.")
                        feature("trash", "Dilediğinde sil",
                                "Hesabını ve sunucudaki tüm kayıtlarını uygulamadan kalıcı silebilirsin.")
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 28)

                    VStack(spacing: 12) {
                        SignInWithAppleButton(.signIn) { request in
                            auth.prepareRequest(request)
                        } onCompletion: { result in
                            Task {
                                await auth.handle(result)
                                if auth.state == .signedIn { dismiss() }
                            }
                        }
                        .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                        .frame(height: 50)
                        .clipShape(Capsule())

                        if let error = auth.errorMessage {
                            Text(error)
                                .font(.caption)
                                .foregroundStyle(Theme.red)
                                .multilineTextAlignment(.center)
                        }

                        Text("Giriş yaparak Kullanım Koşulları'nı ve Gizlilik Politikası'nı kabul edersin.")
                            .font(.caption2)
                            .foregroundStyle(Theme.inkSoft)
                            .multilineTextAlignment(.center)

                        HStack(spacing: 16) {
                            Link("Gizlilik", destination: URL(string: "https://dersdefteri.adilemree.xyz/gizlilik")!)
                            Link("Koşullar", destination: URL(string: "https://dersdefteri.adilemree.xyz/kosullar")!)
                        }
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Theme.board)
                    }
                    .padding(.top, 32)
                }
                .padding(16)
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Eşitleme")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") { dismiss() }
                }
            }
        }
    }

    private func feature(_ icon: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(Theme.board)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }
        }
    }
}

#Preview {
    SignInView()
        .environment(AuthManager())
}
