//
//  ProStore.swift
//  One — Ders Defteri
//
//  StoreKit 2 abonelik yönetimi: ürünler, satın alma, sunucu doğrulaması.
//

import SwiftUI
import StoreKit

@MainActor
@Observable
final class ProStore {
    static let monthlyID = "dersdefteri.pro.monthly"
    static let yearlyID = "dersdefteri.pro.yearly"
    static let productIDs: Set<String> = [monthlyID, yearlyID]

    /// Ücretsiz sürümde izin verilen aktif öğrenci sayısı
    static let freeStudentLimit = 2
    /// Ücretsiz sürümde izin verilen tekrarlayan ders şablonu sayısı
    static let freeTemplateLimit = 1

    /// Uygulama genelindeki kilitler buna bakar.
    var isPro: Bool { entitlementActive }

    /// Doğrulanmış abonelik durumu. Açılışta önbellekten gelir; ardından
    /// cihazdaki StoreKit kaydı ve sunucu yanıtıyla güncellenir.
    private(set) var entitlementActive: Bool = UserDefaults.standard.bool(forKey: "proActiveCache") {
        didSet { UserDefaults.standard.set(entitlementActive, forKey: "proActiveCache") }
    }

    private(set) var products: [Product] = []
    private(set) var isLoadingProducts = false
    var purchaseError: String?

    private var updatesTask: Task<Void, Never>?

    init() {
        updatesTask = Task { await listenForTransactions() }
        Task {
            await refreshEntitlements()
            await loadProducts()
        }
    }

    var monthly: Product? { products.first { $0.id == Self.monthlyID } }
    var yearly: Product? { products.first { $0.id == Self.yearlyID } }

    /// Yıllık planın aylığa göre yüzde kazancı
    var yearlySavingsPercent: Int? {
        guard let monthly, let yearly else { return nil }
        let fullYear = monthly.price * 12
        guard fullYear > 0 else { return nil }
        let saving = (fullYear - yearly.price) / fullYear * 100
        let percent = Int(NSDecimalNumber(decimal: saving).doubleValue.rounded())
        return percent > 0 ? percent : nil
    }

    func canAddStudent(activeCount: Int) -> Bool {
        isPro || activeCount < Self.freeStudentLimit
    }

    func canAddTemplate(activeCount: Int) -> Bool {
        isPro || activeCount < Self.freeTemplateLimit
    }

    func loadProducts() async {
        guard products.isEmpty else { return }
        isLoadingProducts = true
        defer { isLoadingProducts = false }
        do {
            products = try await Product.products(for: Self.productIDs)
                .sorted { $0.price < $1.price }
        } catch {
            purchaseError = "Ürünler yüklenemedi. İnternet bağlantını kontrol et."
        }
    }

    func purchase(_ product: Product) async {
        purchaseError = nil
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                if case .verified(let transaction) = verification {
                    await transaction.finish()
                    // Cihazdaki kayıt hemen açılır, sunucuya arkadan bildirilir;
                    // böylece ödeme sonrası ekran beklemeden Pro'ya geçer.
                    entitlementActive = true
                    await sendToServer(verification.jwsRepresentation)
                }
            case .userCancelled, .pending:
                break
            @unknown default:
                break
            }
        } catch {
            purchaseError = "Satın alma tamamlanamadı. Lütfen tekrar dene."
        }
    }

    func restore() async {
        purchaseError = nil
        try? await AppStore.sync()
        await refreshEntitlements()
        if !entitlementActive {
            purchaseError = "Geri yüklenecek aktif abonelik bulunamadı."
        }
    }

    /// Cihazdaki App Store kaydını okur ve sunucuya bildirir.
    func refreshEntitlements() async {
        var active = false
        var latestJWS: String?

        for await entitlement in Transaction.currentEntitlements {
            if case .verified(let transaction) = entitlement,
               Self.productIDs.contains(transaction.productID),
               transaction.revocationDate == nil {
                active = true
                latestJWS = entitlement.jwsRepresentation
            }
        }
        entitlementActive = active

        if let latestJWS {
            await sendToServer(latestJWS)
        }
    }

    /// Sunucudaki kaydı doğrudan okur. Abonelik iptal veya iade edildiğinde
    /// cihazdaki kayıt hemen düşmeyebilir; sunucu bunu webhook ile öğrenir.
    func refreshFromServer() async {
        guard await APIClient.shared.isSignedIn else { return }
        do {
            let entitlement: ServerEntitlement = try await APIClient.shared.request("/v1/subscription")
            entitlementActive = entitlement.isPro
        } catch {
            // Sunucuya ulaşılamazsa cihazdaki son bilinen durum korunur.
        }
    }

    private func sendToServer(_ signedTransaction: String) async {
        guard await APIClient.shared.isSignedIn else { return }
        do {
            let entitlement: ServerEntitlement = try await APIClient.shared.request(
                "/v1/subscription/verify",
                method: "POST",
                body: ["signedTransaction": signedTransaction]
            )
            entitlementActive = entitlement.isPro
        } catch {
            // Doğrulama şimdi başarısız olsa da cihazdaki StoreKit kaydı
            // geçerli; bir sonraki açılışta tekrar denenir.
        }
    }

    private func listenForTransactions() async {
        for await update in Transaction.updates {
            if case .verified(let transaction) = update {
                await transaction.finish()
                await refreshEntitlements()
            }
        }
    }
}

nonisolated struct ServerEntitlement: Decodable {
    let isPro: Bool
    let productId: String?
    let expiresAt: Date?
    let status: String
}
