//
//  PaywallView.swift
//  One — Ders Defteri
//
//  One Pro abonelik ekranı.
//

import SwiftUI
import StoreKit

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(ProStore.self) private var store

    @State private var selectedID: String = ProStore.yearlyID
    @State private var isPurchasing = false

    private var selectedProduct: Product? {
        store.products.first { $0.id == selectedID }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                // Planlar ve fiyat ilk ekranda görünsün diye özellikler kısa
                // bir onay listesi; satın alma düğmesi altta sabit durur.
                VStack(spacing: 18) {
                    header
                    featureList
                    planSection
                    if let error = store.purchaseError {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(Theme.red)
                            .multilineTextAlignment(.center)
                    }
                    restoreButton
                    legalNote
                }
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 20)
                .readableWidth(560)
            }
            .background(Theme.paper.ignoresSafeArea())
            .safeAreaInset(edge: .bottom) { purchaseBar }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.caption.weight(.bold))
                    }
                }
            }
            .onChange(of: store.isPro) {
                if store.isPro { dismiss() }
            }
        }
    }

    // MARK: - Başlık

    private var header: some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Theme.board)
                    .frame(width: 60, height: 60)
                Image(systemName: "crown.fill")
                    .font(.system(size: 27))
                    .foregroundStyle(Theme.amber)
            }
            Text("Ders Defteri Pro")
                .font(.system(size: 28, weight: .bold, design: .serif))
                .foregroundStyle(Theme.ink)
            Text("Defterini sınırsız kullan")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
        }
    }

    // MARK: - Özellikler

    private var featureList: some View {
        VStack(alignment: .leading, spacing: 11) {
            featureItem("Sınırsız öğrenci", note: "ücretsizde \(ProStore.freeStudentLimit)")
            featureItem("Cihazlar arası eşitleme ve yedek")
            featureItem("PDF veli raporu")
            featureItem("Her sabah günlük program özeti")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(16)
    }

    private func featureItem(_ title: String, note: String? = nil) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .font(.subheadline)
                .foregroundStyle(Theme.green)
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.ink)
            if let note {
                Text(note)
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }
        }
    }

    // MARK: - Planlar

    @ViewBuilder
    private var planSection: some View {
        if store.products.isEmpty {
            VStack(spacing: 10) {
                if store.isLoadingProducts {
                    ProgressView()
                    Text("Planlar yükleniyor…")
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                } else {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.title3)
                        .foregroundStyle(Theme.inkSoft)
                    Text("Planlar şu an yüklenemedi. İnternet bağlantını kontrol edip tekrar dene.")
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                        .multilineTextAlignment(.center)
                    Button("Tekrar Dene") {
                        Task { await store.loadProducts() }
                    }
                    .font(.caption.weight(.bold))
                    .tint(Theme.accent)
                }
            }
            .padding(.vertical, 16)
        } else {
            VStack(spacing: 10) {
                ForEach(store.products.reversed(), id: \.id) { product in
                    planCard(product)
                }
            }
        }
    }

    private func planCard(_ product: Product) -> some View {
        let isSelected = product.id == selectedID
        let isYearly = product.id == ProStore.yearlyID
        return Button {
            selectedID = product.id
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Theme.accent : Theme.inkSoft.opacity(0.5))
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(isYearly ? "Yıllık" : "Aylık")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(Theme.ink)
                            .fixedSize()
                        if isYearly, let percent = store.yearlySavingsPercent {
                            Chip(text: "%\(percent) avantajlı", tint: Theme.green, filled: true)
                        }
                    }
                    // Denemeden sonra ne ödeneceği denemeyle aynı yerde
                    // yazmalı; yalnızca "ücretsiz" demek yanıltıcı sayılır.
                    if let trial = freeTrial(product) {
                        Text("İlk \(trialLength(trial)) ücretsiz, sonra \(product.displayPrice)/\(isYearly ? "yıl" : "ay")")
                            .font(.caption)
                            .foregroundStyle(Theme.green)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(product.displayPrice)
                        .font(.headline.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(Theme.ink)
                    Text(isYearly ? "yılda bir" : "ayda bir")
                        .font(.caption2)
                        .foregroundStyle(Theme.inkSoft)
                    if isYearly {
                        Text("ayda \((product.price / 12).formatted(product.priceFormatStyle))")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Theme.green)
                    }
                }
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Theme.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(isSelected ? Theme.accent : Theme.line, lineWidth: isSelected ? 1.8 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    /// Ücretsiz deneme yalnızca kullanıcı hâlâ hak sahibiyse gösterilir.
    private func freeTrial(_ product: Product) -> Product.SubscriptionOffer? {
        guard store.isEligibleForTrial,
              let offer = product.subscription?.introductoryOffer,
              offer.paymentMode == .freeTrial else { return nil }
        return offer
    }

    private func trialLength(_ offer: Product.SubscriptionOffer) -> String {
        let n = offer.period.value
        switch offer.period.unit {
        case .day: return "\(n) gün"
        case .week: return "\(n * 7) gün"
        case .month: return "\(n) ay"
        case .year: return "\(n) yıl"
        @unknown default: return "deneme süresi"
        }
    }

    // MARK: - Butonlar

    private var purchaseButton: some View {
        Button {
            guard let product = selectedProduct else { return }
            isPurchasing = true
            Task {
                await store.purchase(product)
                isPurchasing = false
            }
        } label: {
            HStack {
                if isPurchasing {
                    ProgressView().tint(.white)
                } else {
                    Text(buttonTitle)
                        .font(.headline.weight(.bold))
                        .fontDesign(.serif)
                }
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .background(Capsule().fill(Theme.board))
        }
        .buttonStyle(.plain)
        .disabled(selectedProduct == nil || isPurchasing)
        .opacity(selectedProduct == nil ? 0.5 : 1)
    }

    /// Ekranın altına sabitlenen düğme. Hemen altında ne ödeneceği yazar;
    /// ücretsiz deneme düğmede, fiyat görünmeden durmamalı.
    private var purchaseBar: some View {
        VStack(spacing: 6) {
            purchaseButton
            if let product = selectedProduct {
                Text(priceSummary(product))
                    .font(.caption2)
                    .foregroundStyle(Theme.inkSoft)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .readableWidth(560)
        .background(Theme.paper.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.line).frame(height: 1)
        }
    }

    private func priceSummary(_ product: Product) -> String {
        let period = product.id == ProStore.yearlyID ? "yıl" : "ay"
        if let trial = freeTrial(product) {
            return "\(trialLength(trial).capitalized(with: Locale(identifier: "tr_TR"))) ücretsiz, sonra \(product.displayPrice)/\(period). İstediğin zaman iptal edebilirsin."
        }
        return "\(product.displayPrice)/\(period). İstediğin zaman iptal edebilirsin."
    }

    private var buttonTitle: String {
        if let product = selectedProduct, let trial = freeTrial(product) {
            return "\(trialLength(trial).capitalized(with: Locale(identifier: "tr_TR"))) Ücretsiz Dene"
        }
        return "Pro'ya Geç"
    }

    private var restoreButton: some View {
        Button("Satın Alımları Geri Yükle") {
            Task { await store.restore() }
        }
        .font(.caption.weight(.semibold))
        .tint(Theme.inkSoft)
    }

    /// Otomatik yenilenen abonelikte satın alma ekranında koşullar ve gizlilik
    /// bağlantısı bulunmalı (App Store yönergesi 3.1.2).
    private var legalNote: some View {
        VStack(spacing: 8) {
            Text("Abonelik, dönem sonunda iptal edilmediği sürece otomatik yenilenir. İstediğin zaman App Store hesabından iptal edebilirsin. Mevcut verilerin abonelik durumundan bağımsız olarak sende kalır.")
                .font(.caption2)
                .foregroundStyle(Theme.inkSoft.opacity(0.8))
                .multilineTextAlignment(.center)

            HStack(spacing: 16) {
                Link("Kullanım Koşulları", destination: URL(string: "https://dersdefteri.adilemree.xyz/kosullar")!)
                Link("Gizlilik Politikası", destination: URL(string: "https://dersdefteri.adilemree.xyz/gizlilik")!)
            }
            .font(.caption2.weight(.semibold))
            .foregroundStyle(Theme.accent)
        }
    }
}


#Preview {
    PaywallView()
        .environment(ProStore())
}
