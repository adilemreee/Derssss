//
//  OnboardingView.swift
//  One — Ders Defteri
//
//  İlk açılışta gösterilen tanıtım akışı.
//

import SwiftUI

struct OnboardingView: View {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @AppStorage("teacherName") private var teacherName = ""

    @State private var page = 0
    @FocusState private var nameFocused: Bool

    private let lastPage = 3

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if page < lastPage {
                    Button("Atla") {
                        withAnimation(.snappy) { page = lastPage }
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.inkSoft)
                    .padding(.trailing, 20)
                    .padding(.top, 12)
                }
            }
            .frame(height: 44)

            TabView(selection: $page) {
                OnboardingPage(title: "Ders Defterine\nHoş Geldin",
                               message: "Öğrencilerini, derslerini, ödemelerini ve ödevlerini tek bir defterde topla. Kağıt karalamalara son.") {
                    BrandIcon(size: 104)
                }
                .tag(0)

                OnboardingPage(title: "Programın\nHep Hazır",
                               message: "\u{201C}Her Salı 17:00\u{201D} gibi haftalık derslerini bir kez gir, sonraki haftalar otomatik planlansın. Ders öncesi bildirimle hatırla.") {
                    ProgramPreview()
                }
                .tag(1)

                OnboardingPage(title: "Kazancını\nTakip Et",
                               message: "İşlenen dersler bakiyeye yansır; kim ne kadar ödedi, kimde ne kaldı anında görürsün.") {
                    BalancePreview()
                }
                .tag(2)

                namePage
                    .tag(3)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.snappy, value: page)

            pageIndicator
                .padding(.bottom, 18)

            Button {
                if page < lastPage {
                    withAnimation(.snappy) { page += 1 }
                } else {
                    finish()
                }
            } label: {
                Text(page < lastPage ? "Devam" : "Başla")
                    .font(.headline.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(Capsule().fill(Theme.board))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .background(Theme.paper.ignoresSafeArea())
    }

    // MARK: - İsim sayfası

    private var namePage: some View {
        VStack(spacing: 18) {
            Spacer()
            // Ana sayfanın kara tahtası, yazılan isimle anında güncellenir;
            // ismin nerede kullanılacağını göstermenin en kısa yolu.
            Chalkboard {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Günaydın \(greetingName)\u{00A0}👋")
                        .font(.title3.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Text("Bugün 3 ders • 4 sa")
                        .font(.subheadline)
                        .fontDesign(.serif)
                        .italic()
                        .foregroundStyle(.white.opacity(0.85))
                }
            }
            .padding(.horizontal, 36)
            .animation(.snappy, value: teacherName)
            Text("Sana Nasıl\nHitap Edelim?")
                .font(.system(size: 28, weight: .bold, design: .serif))
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.center)
            Text("Adını yazarsan ana sayfa sana isminle seslenir. Boş bırakabilirsin.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 36)

            TextField("Adın (isteğe bağlı)", text: $teacherName)
                .textInputAutocapitalization(.words)
                .focused($nameFocused)
                .multilineTextAlignment(.center)
                .font(.headline)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Theme.card)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(nameFocused ? Theme.accent : Theme.line, lineWidth: nameFocused ? 1.5 : 1)
                )
                .padding(.horizontal, 44)
            Spacer()
            Spacer()
        }
    }

    private var pageIndicator: some View {
        HStack(spacing: 7) {
            ForEach(0...lastPage, id: \.self) { i in
                Capsule()
                    .fill(i == page ? Theme.accent : Theme.inkSoft.opacity(0.3))
                    .frame(width: i == page ? 22 : 7, height: 7)
            }
        }
    }

    private var greetingName: String {
        let trimmed = teacherName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "Öğretmenim" }
        return "\(trimmed.capitalized(with: Locale(identifier: "tr_TR"))) öğretmenim"
    }

    private func finish() {
        teacherName = teacherName.trimmingCharacters(in: .whitespacesAndNewlines)
        withAnimation(.easeOut(duration: 0.4)) {
            hasCompletedOnboarding = true
        }
    }
}

// MARK: - Tanıtım sayfası

private struct OnboardingPage<Art: View>: View {
    let title: String
    let message: String
    @ViewBuilder var art: Art

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            art
                .frame(minHeight: 120)
            Text(title)
                .font(.system(size: 28, weight: .bold, design: .serif))
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.center)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
                .multilineTextAlignment(.center)
                .lineSpacing(3)
                .padding(.horizontal, 36)
            Spacer()
            Spacer()
        }
    }
}

// MARK: - Tanıtım görselleri

/// Program ekranından küçük bir kesit: haftalık şerit ve iki ders.
private struct ProgramPreview: View {
    private let days = [("Pzt", "15"), ("Sal", "16"), ("Çar", "17"), ("Per", "18"), ("Cum", "19")]

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 5) {
                ForEach(days, id: \.0) { day, number in
                    let selected = day == "Sal"
                    VStack(spacing: 1) {
                        Text(day)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(selected ? .white.opacity(0.8) : Theme.inkSoft)
                        Text(number)
                            .font(.subheadline.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(selected ? .white : Theme.ink)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(selected ? Theme.board : Theme.paper))
                }
            }
            lesson(time: "16:00", name: "Ayşe", subject: "Matematik", color: Theme.palette[0])
            lesson(time: "18:00", name: "Can", subject: "Fizik", color: Theme.palette[1])
        }
        .padding(12)
        .frame(width: 260)
        .card(0)
        .accessibilityHidden(true)
    }

    private func lesson(time: String, name: String, subject: String, color: Color) -> some View {
        HStack(spacing: 8) {
            Text(time)
                .font(.caption.weight(.bold))
                .fontDesign(.serif)
                .foregroundStyle(Theme.ink)
            Capsule().fill(color).frame(width: 3, height: 22)
            Text(name)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.ink)
            Chip(text: subject, tint: color)
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(Theme.green)
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Theme.paper))
    }
}

/// Ödemeler ekranından küçük bir kesit: bakiye ve ödenen oranı.
private struct BalancePreview: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Circle()
                    .fill(Theme.palette[0])
                    .frame(width: 34, height: 34)
                    .overlay(Text("AY").font(.system(size: 13, weight: .bold, design: .serif)).foregroundStyle(.white))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Ayşe Yılmaz")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.ink)
                    Text("₺2.400 / ₺3.600 ödendi")
                        .font(.caption2)
                        .foregroundStyle(Theme.inkSoft)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 0) {
                    Text("₺1.200")
                        .font(.headline.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(Theme.red)
                    Text("kalan")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Theme.inkSoft)
                }
            }
            PaidProgressBar(paid: 2400, total: 3600)
        }
        .padding(14)
        .frame(width: 280)
        .card(0)
        .accessibilityHidden(true)
    }
}

#Preview {
    OnboardingView()
}
