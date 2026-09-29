//
//  BulkPayments.swift
//  One — Ders Defteri
//
//  Toplu ödeme: bir ödeme seçilen derslere bağlanır. Dersler ayrı ayrı kalır,
//  ücretleri değişmez; bakiye her zamanki gibi "işlenen dersler − ödemeler".
//

import SwiftUI
import SwiftData

// MARK: - Dersin ödeme durumu

enum LessonPayState {
    /// Toplu ödemeyle ödendi
    case bulk(Payment)
    /// Derse bağlı olmayan ödemelerle (en eski dersten başlayarak) kapandı
    case paid
    /// İşlendi, ödenmedi
    case unpaid
    /// Planlı, henüz ödenmedi (borç değil)
    case open
}

/// Öğrencinin derslerinden hangisinin ödendiğini hesaplar.
///
/// Toplu ödemeye bağlı dersler o ödemeyle ödenmiştir. Kalan para (derse
/// bağlı olmayan ödemeler ve toplu ödemenin artan kısmı) en eski işlenmiş
/// dersten başlayarak dersleri kapatır, sonra sıradaki planlı dersleri
/// (avans). Böylece eskiden "Ödeme Al" ile ödenmiş dersler de ödenmiş görünür.
struct PaymentLedger {
    private var states: [PersistentIdentifier: LessonPayState] = [:]
    /// Toplu ödemeye eklenebilecek dersler, tarihe göre
    private(set) var candidates: [Lesson] = []

    /// - Parameter excluded: Düzenlenen ödeme; onun dersleri yeniden seçilebilir olsun diye hesaba katılmaz.
    init(student: Student, excluding excluded: Payment? = nil) {
        let studentID = student.persistentModelID
        let active = student.allLessons.filter { $0.status != .cancelled }
        var pool = student.allPayments
            .filter { $0 !== excluded }
            .reduce(0.0) { $0 + $1.amount }

        var unlinked: [Lesson] = []
        for lesson in active {
            if let payment = lesson.payment, payment !== excluded,
               payment.student?.persistentModelID == studentID {
                states[lesson.persistentModelID] = .bulk(payment)
                pool -= lesson.fee
            } else {
                unlinked.append(lesson)
            }
        }

        let ordered = unlinked.filter { $0.status == .completed }.sorted { $0.date < $1.date }
            + unlinked.filter { $0.status == .planned }.sorted { $0.date < $1.date }
        var covering = true
        for lesson in ordered {
            let fee = lesson.fee
            if covering && pool + 0.5 >= fee {
                pool -= fee
                states[lesson.persistentModelID] = .paid
            } else {
                covering = false
                states[lesson.persistentModelID] = lesson.status == .completed ? .unpaid : .open
                if fee > 0.005 { candidates.append(lesson) }
            }
        }
        candidates.sort { $0.date < $1.date }
    }

    func state(of lesson: Lesson) -> LessonPayState? {
        states[lesson.persistentModelID]
    }
}

extension Payment {
    /// "4 ders • 3.000 ₺" gibi kısa açıklama
    var bulkSummary: String {
        let active = coveredLessons.filter { $0.status != .cancelled }
        return "\(active.count) ders"
    }

    /// İptal edilen ya da ücreti değişen dersler yüzünden ödeme tutarıyla
    /// derslerin toplamı arasındaki fark (pozitif: fazla ödendi).
    var bulkDifference: Double {
        let total = coveredLessons.filter { $0.status != .cancelled }.reduce(0.0) { $0 + $1.fee }
        return amount - total
    }
}

// MARK: - Ders seçimi

/// Toplu ödemenin kapsayacağı dersler. Ödenmemiş işlenmiş dersler ve
/// (peşin ödeme için) planlı dersler seçilebilir.
struct LessonSelectionView: View {
    let student: Student
    var excluding: Payment? = nil
    @Binding var selection: Set<PersistentIdentifier>
    @Environment(\.dismiss) private var dismiss

    private var candidates: [Lesson] {
        PaymentLedger(student: student, excluding: excluding).candidates
    }

    private var months: [(month: Date, lessons: [Lesson])] {
        let grouped = Dictionary(grouping: candidates) { $0.date.startOfMonth }
        return grouped.keys.sorted().map { ($0, grouped[$0] ?? []) }
    }

    private var selectedLessons: [Lesson] {
        candidates.filter { selection.contains($0.persistentModelID) }
    }

    private var total: Double {
        selectedLessons.reduce(0.0) { $0 + $1.fee }
    }

    var body: some View {
        List {
            if candidates.isEmpty {
                Section {
                    Text("Ödenmemiş ders yok. İşlenen dersler de planlı dersler de ödenmiş görünüyor.")
                        .font(.subheadline)
                        .foregroundStyle(Theme.inkSoft)
                }
            } else {
                Section {
                    quickPicks
                        .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                } footer: {
                    Text("Planlı dersleri seçersen peşin ödeme olur; ders iptal edilirse tutarı avansa geçer.")
                }

                ForEach(months, id: \.month) { group in
                    Section {
                        ForEach(group.lessons) { lesson in
                            row(lesson)
                        }
                    } header: {
                        HStack {
                            Text(Fmt.monthYear.string(from: group.month).capitalized(with: Locale(identifier: "tr_TR")))
                            Spacer()
                            Button(allSelected(group.lessons) ? "Hiçbiri" : "Tümü") {
                                toggleAll(group.lessons)
                            }
                            .font(.caption.weight(.semibold))
                            .textCase(nil)
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.paper)
        .navigationTitle("Dersleri Seç")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            if !candidates.isEmpty {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(selection.count) ders seçildi")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.inkSoft)
                        Text(Fmt.money(total))
                            .monospacedDigit()
                            .font(.title3.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(Theme.ink)
                            .contentTransition(.numericText())
                    }
                    Spacer()
                    Button("Tamam") { dismiss() }
                        .buttonStyle(.borderedProminent)
                        .tint(Theme.board)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(.bar)
            }
        }
    }

    private var quickPicks: some View {
        let now = Date()
        let thisMonth = now.startOfMonth
        let lastMonth = thisMonth.adding(months: -1)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                pick("Bu ay") { $0.date >= thisMonth && $0.date < thisMonth.adding(months: 1) }
                pick("Geçen ay") { $0.date >= lastMonth && $0.date < thisMonth }
                pick("İşlenenler") { $0.status == .completed }
                pick("Gelecek 2 hafta") { $0.date >= now.startOfDay && $0.date < now.startOfDay.adding(days: 14) }
                pick("Tümü") { _ in true }
                Button {
                    withAnimation(.snappy) { selection.removeAll() }
                } label: {
                    FormChip(title: "Temizle", isSelected: false)
                }
                .buttonStyle(.plain)
                .disabled(selection.isEmpty)
            }
            .padding(.vertical, 2)
        }
    }

    /// Hızlı seçim mevcut seçimi değiştirir; aynı seçim tekrar dokunulunca kalkar.
    private func pick(_ title: String, _ filter: @escaping (Lesson) -> Bool) -> some View {
        let ids = Set(candidates.filter(filter).map(\.persistentModelID))
        let isOn = !ids.isEmpty && ids == selection
        return Button {
            withAnimation(.snappy) { selection = isOn ? [] : ids }
        } label: {
            FormChip(title: title, isSelected: isOn)
        }
        .buttonStyle(.plain)
        .disabled(ids.isEmpty)
        .opacity(ids.isEmpty ? 0.45 : 1)
    }

    private func row(_ lesson: Lesson) -> some View {
        let isOn = selection.contains(lesson.persistentModelID)
        return Button {
            withAnimation(.snappy) {
                if isOn { selection.remove(lesson.persistentModelID) } else { selection.insert(lesson.persistentModelID) }
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isOn ? Theme.accent : Theme.inkSoft.opacity(0.5))
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(Fmt.dayMonthShort.string(from: lesson.date)) \(Fmt.weekdayShort.string(from: lesson.date)) · \(Fmt.time.string(from: lesson.date))")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.ink)
                    Text(lessonDetail(lesson))
                        .font(.caption)
                        .foregroundStyle(lesson.status == .planned ? Theme.blue : Theme.inkSoft)
                        .lineLimit(1)
                }
                Spacer()
                Text(Fmt.money(lesson.fee))
                    .monospacedDigit()
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private func lessonDetail(_ lesson: Lesson) -> String {
        var parts = [lesson.status == .planned ? "Planlı" : "İşlendi", "\(lesson.duration) dk"]
        if !lesson.topic.isEmpty { parts.append(lesson.topic) }
        return parts.joined(separator: " · ")
    }

    private func allSelected(_ lessons: [Lesson]) -> Bool {
        lessons.allSatisfy { selection.contains($0.persistentModelID) }
    }

    private func toggleAll(_ lessons: [Lesson]) {
        let ids = lessons.map(\.persistentModelID)
        withAnimation(.snappy) {
            if allSelected(lessons) {
                ids.forEach { selection.remove($0) }
            } else {
                ids.forEach { selection.insert($0) }
            }
        }
    }
}
