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
                OnboardingPage(icon: "book.closed.fill",
                               title: "Ders Defterine\nHoş Geldin",
                               message: "Öğrencilerini, derslerini, ödemelerini ve ödevlerini tek bir defterde topla. Kağıt karalamalara son.")
                    .tag(0)

                OnboardingPage(icon: "calendar.badge.clock",
                               title: "Programın\nHep Hazır",
                               message: "\u{201C}Her Salı 17:00\u{201D} gibi tekrarlayan dersler tanımla, dersler otomatik oluşsun. Ders öncesi bildirimle hatırla.")
                    .tag(1)

                OnboardingPage(icon: "turkishlirasign.circle.fill",
                               title: "Kazancını\nTakip Et",
                               message: "İşlenen dersler bakiyeye yansır, kim ne kadar borçlu anında görürsün. Verilerin hesabında güvende.")
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
            ZStack {
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(Theme.board)
                    .frame(width: 92, height: 92)
                Image(systemName: "person.fill")
                    .font(.system(size: 40, weight: .semibold))
                    .foregroundStyle(.white)
            }
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
                        .stroke(nameFocused ? Theme.board : Theme.line, lineWidth: nameFocused ? 1.5 : 1)
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
                    .fill(i == page ? Theme.board : Theme.inkSoft.opacity(0.3))
                    .frame(width: i == page ? 22 : 7, height: 7)
            }
        }
    }

    private func finish() {
        teacherName = teacherName.trimmingCharacters(in: .whitespacesAndNewlines)
        withAnimation(.easeOut(duration: 0.4)) {
            hasCompletedOnboarding = true
        }
    }
}

// MARK: - Tanıtım sayfası

private struct OnboardingPage: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            ZStack {
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(Theme.board)
                    .frame(width: 92, height: 92)
                    .shadow(color: Theme.board.opacity(0.2), radius: 14, y: 7)
                Image(systemName: icon)
                    .font(.system(size: 40, weight: .semibold))
                    .foregroundStyle(.white)
            }
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

#Preview {
    OnboardingView()
}
