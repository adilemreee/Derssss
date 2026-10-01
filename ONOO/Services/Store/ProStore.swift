//
//  ProStore.swift
//  One — Ders Defteri
//
//  StoreKit 2 Pro yönetimi: abonelikler ve ömür boyu satın alma, sunucu
//  doğrulaması.
//

import SwiftUI
import StoreKit

@MainActor
@Observable
final class ProStore {
    static let monthlyID = "dersdefteri.abonelik.aylik"
    static let yearlyID = "dersdefteri.abonelik.yillik"
    /// Tek seferlik (tüketilmeyen) satın alma; abonelik grubunda değildir.
    static let lifetimeID = "dersdefteri.omurboyu"
    static let productIDs: Set<String> = [monthlyID, yearlyID, lifetimeID]

    /// Ücretsiz sürümde izin verilen aktif öğrenci sayısı
    static let freeStudentLimit = 2

    /// Uygulama genelindeki kilitler buna bakar.
    ///
    /// İki kaynaktan biri yeterlidir. Cihazdaki App Store kaydı, sunucu henüz
    /// haberdar olmasa da (ör. hesapsızken alınıp sonra giriş yapıldığında)
    /// geçerlidir; sunucu da cihaz kaydı gecikse bile aboneliği bilebilir.
    /// Birinin "hayır" demesi diğerinin "evet"ini ezmemeli.
    var isPro: Bool { storeKitActive || serverActive }

    /// Cihazdaki doğrulanmış StoreKit kaydı. Açılışta önbellekten gelir.
    private(set) var storeKitActive: Bool = UserDefaults.standard.bool(forKey: "proActiveCache") {
        didSet { UserDefaults.standard.set(storeKitActive, forKey: "proActiveCache") }
    }

    /// Sunucunun bildiği abonelik durumu. Yalnızca girişliyken anlamlıdır.
    private(set) var serverActive: Bool = UserDefaults.standard.bool(forKey: "proServerCache") {
        didSet { UserDefaults.standard.set(serverActive, forKey: "proServerCache") }
    }

    /// Cihazdaki App Store kaydında ömür boyu satın alma var.
    private(set) var isLifetime: Bool = UserDefaults.standard.bool(forKey: "proLifetimeCache") {
        didSet { UserDefaults.standard.set(isLifetime, forKey: "proLifetimeCache") }
    }

    /// Cihazdaki App Store kaydında yenilenen bir abonelik de var. Ömür boyu
    /// alan biri aboneliğini iptal edebilsin diye yönetim düğmesi buna bakar.
    private(set) var hasStoreKitSubscription = false

    /// Ömür boyu satın almada yönetilecek bir abonelik yoktur.
    var showsSubscriptionManagement: Bool {
        isPro && (!isLifetime || hasStoreKitSubscription)
    }

    private(set) var products: [Product] = []
    private(set) var isLoadingProducts = false
    /// Tanıtım teklifi (ücretsiz deneme) Apple hesabı başına bir kez kullanılır.
    /// Daha önce kullanmış birine "ücretsiz dene" göstermek yanıltıcı olur.
    private(set) var isEligibleForTrial = false
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
    var lifetime: Product? { products.first { $0.id == Self.lifetimeID } }

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

    func loadProducts() async {
        guard products.isEmpty else { return }
        isLoadingProducts = true
        defer { isLoadingProducts = false }
        do {
            products = try await Product.products(for: Self.productIDs)
                .sorted { $0.price < $1.price }
            if let subscription = products.first(where: { $0.subscription != nil })?.subscription {
                isEligibleForTrial = await subscription.isEligibleForIntroOffer
            }
        } catch {
            purchaseError = "Ürünler yüklenemedi. İnternet bağlantını kontrol et."
        }
    }

    func purchase(_ product: Product) async {
        purchaseError = nil
        do {
            // Girişliyken satın alma hesaba damgalanır; sunucu bu damgayla
            // aboneliğin başka bir hesaba taşınmasını engeller.
            var options: Set<Product.PurchaseOption> = []
            if let accountID = await APIClient.shared.accountID {
                options.insert(.appAccountToken(accountID))
            }
            let result = try await product.purchase(options: options)
            switch result {
            case .success(let verification):
                if case .verified(let transaction) = verification {
                    await transaction.finish()
                    // Cihazdaki kayıt hemen açılır, sunucuya arkadan bildirilir;
                    // böylece ödeme sonrası ekran beklemeden Pro'ya geçer.
                    storeKitActive = true
                    if transaction.productID == Self.lifetimeID { isLifetime = true }
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
        if !isPro {
            purchaseError = "Geri yüklenecek aktif abonelik bulunamadı."
        }
    }

    /// Cihazdaki App Store kaydını okur ve sunucuya bildirir.
    func refreshEntitlements() async {
        var lifetimeJWS: String?
        var subscriptionJWS: String?
        var subscriptionExpiry = Date.distantPast

        for await entitlement in Transaction.currentEntitlements {
            guard case .verified(let transaction) = entitlement,
                  Self.productIDs.contains(transaction.productID),
                  transaction.revocationDate == nil else { continue }
            if transaction.productID == Self.lifetimeID {
                lifetimeJWS = entitlement.jwsRepresentation
            } else if (transaction.expirationDate ?? .distantFuture) > subscriptionExpiry {
                subscriptionExpiry = transaction.expirationDate ?? .distantFuture
                subscriptionJWS = entitlement.jwsRepresentation
            }
        }
        isLifetime = lifetimeJWS != nil
        hasStoreKitSubscription = subscriptionJWS != nil
        storeKitActive = isLifetime || hasStoreKitSubscription

        // Sunucu kullanıcı başına tek kayıt tutar. Ömür boyu varken abonelik
        // gönderilirse, abonelik bitince sunucu eşitlemeyi kapatırdı.
        if let jws = lifetimeJWS ?? subscriptionJWS {
            await sendToServer(jws)
        }
    }

    /// Sunucudaki kaydı doğrudan okur. Abonelik iptal veya iade edildiğinde
    /// cihazdaki kayıt hemen düşmeyebilir; sunucu bunu webhook ile öğrenir.
    func refreshFromServer() async {
        guard await APIClient.shared.isSignedIn else {
            serverActive = false
            return
        }
        do {
            let entitlement: ServerEntitlement = try await APIClient.shared.request("/v1/subscription")
            serverActive = entitlement.isPro
        } catch {
            // Sunucuya ulaşılamazsa son bilinen durum korunur.
        }
    }

    /// Oturum kapanınca sunucu durumu artık bu cihaz için geçerli değildir.
    func clearServerState() {
        serverActive = false
    }

    private func sendToServer(_ signedTransaction: String) async {
        guard await APIClient.shared.isSignedIn else { return }
        do {
            let entitlement: ServerEntitlement = try await APIClient.shared.request(
                "/v1/subscription/verify",
                method: "POST",
                body: ["signedTransaction": signedTransaction]
            )
            serverActive = entitlement.isPro
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
