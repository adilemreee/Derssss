//
//  AuthManager.swift
//  One — Ders Defteri
//
//  Apple ile Giriş, oturum durumu ve hesap silme.
//

import AuthenticationServices
import CryptoKit
import Foundation
import SwiftUI

@MainActor
@Observable
final class AuthManager {
    enum State {
        case checking
        case signedOut
        case signedIn
    }

    private(set) var state: State = .checking
    private(set) var displayName: String?
    var errorMessage: String?

    /// Apple isteğine gönderilen nonce'un ham hâli. Sunucu, kimlik jetonundaki
    /// özetle bunu karşılaştırarak jetonun bu cihaz için üretildiğini doğrular.
    private var currentNonce: String?

    init() {
        displayName = UserDefaults.standard.string(forKey: "accountName")
    }

    func restoreSession() async {
        state = await APIClient.shared.isSignedIn ? .signedIn : .signedOut
    }

    // MARK: - Apple ile Giriş

    func prepareRequest(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = Self.randomNonce()
        currentNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
    }

    func handle(_ result: Result<ASAuthorization, Error>) async {
        errorMessage = nil

        switch result {
        case .failure(let error):
            // Kullanıcı vazgeçtiyse hata gösterme.
            if (error as? ASAuthorizationError)?.code == .canceled { return }
            errorMessage = "Apple girişi tamamlanamadı. Lütfen tekrar dene."

        case .success(let auth):
            guard let credential = auth.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken,
                  let identityToken = String(data: tokenData, encoding: .utf8) else {
                errorMessage = "Apple kimlik bilgisi okunamadı."
                return
            }

            // Ad yalnızca ilk girişte gelir; sonraki girişlerde boştur.
            let name = [credential.fullName?.givenName, credential.fullName?.familyName]
                .compactMap { $0 }
                .joined(separator: " ")
                .trimmingCharacters(in: .whitespaces)

            var payload: [String: String] = ["identityToken": identityToken]
            if let currentNonce { payload["rawNonce"] = currentNonce }
            if !name.isEmpty { payload["fullName"] = name }

            do {
                let response: SignInResponse = try await APIClient.shared
                    .requestPublic("/v1/auth/apple", body: payload)
                await APIClient.shared.storeTokens(access: response.accessToken,
                                                   refresh: response.refreshToken)
                if let serverName = response.user.name ?? (name.isEmpty ? nil : name) {
                    displayName = serverName
                    UserDefaults.standard.set(serverName, forKey: "accountName")
                }
                currentNonce = nil
                state = .signedIn
            } catch {
                errorMessage = (error as? LocalizedError)?.errorDescription
                    ?? "Girişte bir sorun oldu. Lütfen tekrar dene."
            }
        }
    }

    // MARK: - Çıkış ve silme

    func signOut() async {
        _ = try? await APIClient.shared.request("/v1/auth/logout", method: "POST") as EmptyResponse
        await finishSignOut()
    }

    /// Sunucudaki hesabı ve tüm kayıtları kalıcı olarak siler.
    /// App Store yönergesi 5.1.1(v) bunu uygulama içinden zorunlu tutuyor.
    func deleteAccount() async throws {
        let _: EmptyResponse = try await APIClient.shared.request("/v1/account", method: "DELETE")
        await finishSignOut()
    }

    private func finishSignOut() async {
        await APIClient.shared.clearTokens()
        SyncEngine.shared.reset()
        displayName = nil
        UserDefaults.standard.removeObject(forKey: "accountName")
        state = .signedOut
    }

    // MARK: - Nonce

    private static func randomNonce(length: Int = 32) -> String {
        var bytes = [UInt8](repeating: 0, count: length)
        // Tahmin edilemezlik güvenliğin temeli olduğu için sistem RNG'si kullanılır.
        guard SecRandomCopyBytes(kSecRandomDefault, length, &bytes) == errSecSuccess else {
            return UUID().uuidString + UUID().uuidString
        }
        let charset = Array("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-._")
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }
}

// MARK: - Yanıt tipleri

nonisolated struct SignInResponse: Decodable {
    struct User: Decodable {
        let id: String
        let email: String?
        let name: String?
    }
    let accessToken: String
    let refreshToken: String
    let user: User
}
