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
                VStack(spacing: 22) {
                    header
                    featureList
                    planSection
                    if let error = store.purchaseError {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(Theme.red)
                            .multilineTextAlignment(.center)
                    }
                    purchaseButton
                    restoreButton
                    legalNote
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 28)
            }
            .background(Theme.paper.ignoresSafeArea())
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
        VStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Theme.board)
                    .frame(width: 76, height: 76)
                Image(systemName: "crown.fill")
                    .font(.system(size: 34))
                    .foregroundStyle(Theme.amber)
            }
            Text("Ders Defteri Pro")
                .font(.system(size: 30, weight: .bold, design: .serif))
                .foregroundStyle(Theme.ink)
            Text("Defterini sınırsız kullan")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
        }
    }

    // MARK: - Özellikler

    private var featureList: some View {
        VStack(spacing: 0) {
            featureRow(icon: "person.3.fill",
                       title: "Sınırsız öğrenci",
                       detail: "Ücretsiz sürümde \(ProStore.freeStudentLimit) aktif öğrenci")
            Divider().padding(.leading, 46)
            featureRow(icon: "arrow.triangle.2.circlepath",
                       title: "Cihazlar arası eşitleme",
                       detail: "Defterin hesabında yedeklenir, yeni telefonda geri gelir")
            Divider().padding(.leading, 46)
            featureRow(icon: "doc.richtext",
                       title: "PDF veli raporu",
                       detail: "Dersleri, ödevleri ve bakiyeyi tek belgede gönder")
            Divider().padding(.leading, 46)
            featureRow(icon: "sun.horizon.fill",
                       title: "Günlük program özeti",
                       detail: "Her sabah o günün tüm dersleri tek bildirimde")
            Divider().padding(.leading, 46)
            featureRow(icon: "repeat",
                       title: "Sınırsız tekrarlayan ders",
                       detail: "Ücretsiz sürümde \(ProStore.freeTemplateLimit) şablon")
            Divider().padding(.leading, 46)
            featureRow(icon: "tablecells",
                       title: "CSV dışa aktarma",
                       detail: "Tüm kayıtlarını dosya olarak al")
        }
        .card(4)
    }

    private func featureRow(icon: String, title: String, detail: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.board)
                .frame(width: 34, height: 34)
                .background(Circle().fill(Theme.board.opacity(0.1)))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
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
                    Text("Planlar yüklenemedi.")
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                    Button("Tekrar Dene") {
                        Task { await store.loadProducts() }
                    }
                    .font(.caption.weight(.bold))
                    .tint(Theme.board)
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
                    .foregroundStyle(isSelected ? Theme.board : Theme.inkSoft.opacity(0.5))
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(isYearly ? "Yıllık" : "Aylık")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(Theme.ink)
                        if isYearly, let percent = store.yearlySavingsPercent {
                            Chip(text: "%\(percent) avantajlı", tint: Theme.green, filled: true)
                        }
                    }
                    if isYearly, hasFreeTrial(product) {
                        Text("İlk 7 gün ücretsiz")
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
                }
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Theme.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(isSelected ? Theme.board : Theme.line, lineWidth: isSelected ? 1.8 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func hasFreeTrial(_ product: Product) -> Bool {
        product.subscription?.introductoryOffer?.paymentMode == .freeTrial
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

    private var buttonTitle: String {
        if let product = selectedProduct, hasFreeTrial(product) {
            return "7 Gün Ücretsiz Dene"
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

    private var legalNote: some View {
        Text("Abonelik, dönem sonunda iptal edilmediği sürece otomatik yenilenir. İstediğin zaman App Store hesabından iptal edebilirsin. Mevcut verilerin abonelik durumundan bağımsız olarak sende kalır.")
            .font(.caption2)
            .foregroundStyle(Theme.inkSoft.opacity(0.8))
            .multilineTextAlignment(.center)
    }
}


#Preview {
    PaywallView()
        .environment(ProStore())
}
