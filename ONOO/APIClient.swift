//
//  APIClient.swift
//  One — Ders Defteri
//
//  Sunucu iletişimi: istek imzalama, jeton tazeleme, hata çevirisi.
//

import Foundation

nonisolated enum APIError: LocalizedError {
    case offline
    case unauthorized
    case server(Int, String?)
    case decoding

    var errorDescription: String? {
        switch self {
        case .offline:
            return "İnternet bağlantısı yok. Değişikliklerin cihazda saklandı, bağlanınca eşitlenecek."
        case .unauthorized:
            return "Oturumun sona erdi. Lütfen tekrar giriş yap."
        case .server(_, let message):
            return message ?? "Sunucuya ulaşılamadı. Birazdan tekrar denenecek."
        case .decoding:
            return "Sunucudan beklenmeyen bir yanıt geldi."
        }
    }
}

actor APIClient {
    static let shared = APIClient()

    private let baseURL = URL(string: "https://dersapi.adilemree.xyz")!
    private let session: URLSession

    /// Aynı anda birden çok istek 401 alırsa tek bir tazeleme yapılır;
    /// aksi halde tek kullanımlık yenileme jetonu yarışa girip iptal olur.
    private var refreshTask: Task<Bool, Never>?

    private init() {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 30
        cfg.waitsForConnectivity = true
        session = URLSession(configuration: cfg)
    }

    // MARK: - Jetonlar

    private var accessToken: String? {
        get { Keychain.get("accessToken") }
        set { Keychain.set(newValue, for: "accessToken") }
    }

    private var refreshToken: String? {
        get { Keychain.get("refreshToken") }
        set { Keychain.set(newValue, for: "refreshToken") }
    }

    var isSignedIn: Bool { refreshToken != nil }

    func storeTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    func clearTokens() {
        accessToken = nil
        refreshToken = nil
    }

    // MARK: - İstekler

    /// Kimlik gerektirmeyen çağrı (giriş, jeton tazeleme).
    func requestPublic<T: Decodable>(_ path: String, method: String = "POST", body: Encodable) async throws -> T {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try Self.encoder.encode(AnyEncodable(body))
        return try await send(request)
    }

    /// Kimlik gerektiren çağrı. 401 alınırsa jeton bir kez tazelenip yeniden denenir.
    func request<T: Decodable>(_ path: String,
                               method: String = "GET",
                               body: Encodable? = nil,
                               query: [URLQueryItem] = []) async throws -> T {
        do {
            return try await authorized(path, method: method, body: body, query: query)
        } catch APIError.unauthorized {
            guard await refreshIfNeeded() else { throw APIError.unauthorized }
            return try await authorized(path, method: method, body: body, query: query)
        }
    }

    private func authorized<T: Decodable>(_ path: String,
                                          method: String,
                                          body: Encodable?,
                                          query: [URLQueryItem]) async throws -> T {
        guard let token = accessToken else { throw APIError.unauthorized }

        var url = baseURL.appending(path: path)
        if !query.isEmpty { url.append(queryItems: query) }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try Self.encoder.encode(AnyEncodable(body))
        }
        return try await send(request)
    }

    private func send<T: Decodable>(_ request: URLRequest) async throws -> T {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .notConnectedToInternet
                                        || error.code == .networkConnectionLost
                                        || error.code == .timedOut {
            throw APIError.offline
        }

        guard let http = response as? HTTPURLResponse else { throw APIError.decoding }

        switch http.statusCode {
        case 200..<300:
            // Gövde beklemeyen uçlar için boş yanıt da geçerli sayılır.
            if T.self == EmptyResponse.self { return EmptyResponse() as! T }
            do {
                return try Self.decoder.decode(T.self, from: data)
            } catch {
                throw APIError.decoding
            }
        case 401:
            throw APIError.unauthorized
        default:
            let message = (try? Self.decoder.decode(ServerError.self, from: data))?.message
            throw APIError.server(http.statusCode, message)
        }
    }

    private func refreshIfNeeded() async -> Bool {
        if let existing = refreshTask { return await existing.value }

        // Görev bu aktörün yalıtımını devraldığı için jeton erişimleri
        // doğrudan yapılır.
        let task = Task<Bool, Never> { [self] in
            guard let refresh = refreshToken else { return false }
            do {
                let result: TokenResponse = try await requestPublic(
                    "/v1/auth/refresh", body: ["refreshToken": refresh]
                )
                storeTokens(access: result.accessToken, refresh: result.refreshToken)
                return true
            } catch {
                // Yenileme jetonu da geçersizse oturum gerçekten bitmiştir.
                clearTokens()
                return false
            }
        }
        refreshTask = task
        let result = await task.value
        refreshTask = nil
        return result
    }

    // MARK: - Kodlayıcılar

    static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        // Sunucu milisaniyeli ISO-8601 üretir; ikisini de kabul et.
        d.dateDecodingStrategy = .custom { decoder in
            let text = try decoder.singleValueContainer().decode(String.self)
            if let date = Fmt.iso8601Fractional.date(from: text) { return date }
            if let date = Fmt.iso8601Plain.date(from: text) { return date }
            throw DecodingError.dataCorrupted(
                .init(codingPath: decoder.codingPath, debugDescription: "Tarih çözümlenemedi: \(text)")
            )
        }
        return d
    }()
}

// MARK: - Yardımcı tipler

nonisolated struct EmptyResponse: Decodable {}

nonisolated struct ServerError: Decodable {
    let error: String?
    let message: String?
}

nonisolated struct TokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String
}

/// `Encodable` bir değeri tür silme ile taşımak için ince sarmalayıcı.
nonisolated struct AnyEncodable: Encodable {
    private let encode: (Encoder) throws -> Void
    init(_ wrapped: Encodable) {
        encode = wrapped.encode
    }
    func encode(to encoder: Encoder) throws {
        try encode(encoder)
    }
}
