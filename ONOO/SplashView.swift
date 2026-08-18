//
//  SplashView.swift
//  One — Ders Defteri
//
//  Açılış ekranı: logo, isim ve amber çizgi animasyonu.
//

import SwiftUI

struct SplashView: View {
    @State private var iconShown = false
    @State private var titleShown = false
    @State private var underlineShown = false

    var body: some View {
        ZStack {
            Theme.paper.ignoresSafeArea()

            VStack(spacing: 22) {
                ZStack {
                    RoundedRectangle(cornerRadius: 26, style: .continuous)
                        .fill(Theme.board)
                        .frame(width: 96, height: 96)
                        .shadow(color: Theme.board.opacity(0.22), radius: 16, y: 8)
                    Image(systemName: "book.closed.fill")
                        .font(.system(size: 42, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .scaleEffect(iconShown ? 1 : 0.65)
                .opacity(iconShown ? 1 : 0)

                VStack(spacing: 10) {
                    Text("Ders Defteri")
                        .font(.system(size: 32, weight: .bold, design: .serif))
                        .foregroundStyle(Theme.ink)
                        .opacity(titleShown ? 1 : 0)
                        .offset(y: titleShown ? 0 : 12)

                    Capsule()
                        .fill(Theme.amber)
                        .frame(width: underlineShown ? 72 : 0, height: 3)

                    Text("Öğrenciler • Program • Ödemeler")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Theme.inkSoft)
                        .opacity(titleShown ? 1 : 0)
                }
            }
            .padding(.bottom, 40)
        }
        .onAppear {
            withAnimation(.spring(response: 0.55, dampingFraction: 0.72)) {
                iconShown = true
            }
            withAnimation(.easeOut(duration: 0.5).delay(0.3)) {
                titleShown = true
            }
            withAnimation(.easeOut(duration: 0.45).delay(0.55)) {
                underlineShown = true
            }
        }
    }
}

#Preview {
    SplashView()
}
