//
//  LessonPackages.swift
//  One — Ders Defteri
//
//  Ders paketleri: paketten düşme, paket formu ve öğrenci sayfasındaki kart.
//

import SwiftUI
import SwiftData

// MARK: - Paket defteri

enum PackageLedger {
    /// Dersin paket bağını durumuna göre düzeltir. İşlenen ders, başlangıcı
    /// dersten önce olan en eski dolu pakete bağlanır ve ders başı paket
    /// ücretiyle hesaplanır; işlendi olmaktan çıkan ders paketten ayrılır ve
    /// normal ücretine döner.
    static func reconcile(_ lesson: Lesson) {
        guard lesson.status == .completed, let student = lesson.student else {
            if lesson.package != nil { detach(lesson) }
            return
        }
        if let current = lesson.package {
            if current.student?.persistentModelID == student.persistentModelID {
                applyPrice(of: current, to: lesson)
                return
            }
            // Başka öğrenciye taşınan ders eski öğrencinin paketinden çıkar.
            detach(lesson)
        }
        if let package = availablePackage(for: student, on: lesson.date) {
            attach(lesson, to: package)
        }
    }

    static func availablePackage(for student: Student, on date: Date) -> LessonPackage? {
        student.allPackages.first { !$0.isFinished && $0.startDate.startOfDay <= date }
    }

    /// Paket eklendi ya da ders sayısı, ücret veya başlangıç değişti.
    static func packageDidChange(_ package: LessonPackage) {
        // Fazla kalan dersler (ders sayısı azaltıldıysa ya da başlangıç
        // ileri alındıysa) paketten çıkar, varsa başka pakete geçer.
        var linked = package.usedLessons
        let outOfRange = linked.filter { $0.date < package.startDate.startOfDay }
        let overflow = linked.filter { !outOfRange.contains($0) }.dropFirst(package.lessonCount)
        let released = outOfRange + overflow
        for lesson in released { detach(lesson) }
        linked.removeAll { released.contains($0) }

        for lesson in linked { applyPrice(of: package, to: lesson) }

        // Başlangıçtan sonra işlenmiş, pakete bağlı olmayan dersler sırayla düşülür.
        var free = package.lessonCount - linked.count
        if free > 0, let student = package.student {
            let candidates = student.completedLessons
                .filter { $0.package == nil && $0.date >= package.startDate.startOfDay }
                .sorted { $0.date < $1.date }
            for lesson in candidates where free > 0 {
                attach(lesson, to: package)
                free -= 1
            }
        }
        for lesson in released { reconcile(lesson) }
    }

    /// Paketi siler; dersleri normal ücretine döner ya da başka pakete geçer.
    /// Paketle birlikte alınan ödeme silinmez.
    static func delete(_ package: LessonPackage, in context: ModelContext) {
        let lessons = package.usedLessons
        for lesson in lessons { detach(lesson) }
        context.delete(package)
        try? context.save()
        for lesson in lessons { reconcile(lesson) }
        try? context.save()
    }

    /// Paket formunda gösterilir: yeni paket kaydedilince hemen düşülecek dersler.
    static func lessonsToCount(for student: Student, from start: Date, limit: Int) -> Int {
        let count = student.completedLessons
            .filter { $0.package == nil && $0.date >= start.startOfDay }
            .count
        return min(count, max(limit, 0))
    }

    private static func attach(_ lesson: Lesson, to package: LessonPackage) {
        lesson.package = package
        applyPrice(of: package, to: lesson)
    }

    private static func detach(_ lesson: Lesson) {
        lesson.package = nil
        lesson.feeOverride = Lesson.standardFee(for: lesson.student, duration: lesson.duration)
        lesson.usesCustomFee = false
    }

    private static func applyPrice(of package: LessonPackage, to lesson: Lesson) {
        let unit = (package.unitPrice * 100).rounded() / 100
        if lesson.feeOverride != unit || !lesson.usesCustomFee {
            lesson.feeOverride = unit
            lesson.usesCustomFee = true
        }
    }
}

// MARK: - Görünüm yardımcıları

extension LessonPackage {
    /// Kalan derse göre renk: bitti kırmızı, son iki ders amber.
    var tint: Color {
        if isFinished { return Theme.red }
        if remaining <= 2 { return Theme.amber }
        return Theme.accent
    }

    var statusText: String {
        if isFinished { return "Paket bitti" }
        if remaining <= 2 { return "Bitmek üzere" }
        return "Devam ediyor"
    }
}

extension Student {
    /// Kartta gösterilecek paket: bitmemiş en eski, yoksa en son biten.
    var currentPackage: LessonPackage? {
        let all = allPackages
        return all.first { !$0.isFinished } ?? all.last
    }

    /// Normal bir dersin süresi: haftalık dersten ya da son dersten.
    var typicalLessonDuration: Int {
        if let template = allRecurringTemplates.first { return template.duration }
        return allLessons.max { $0.date < $1.date }?.duration ?? 60
    }
}

// MARK: - Öğrenci sayfasındaki paket kartı

struct PackageCard: View {
    let student: Student
    let package: LessonPackage
    var onOpen: () -> Void
    var onNew: () -> Void

    private var otherRemaining: Int {
        student.allPackages
            .filter { $0.persistentModelID != package.persistentModelID && !$0.isFinished }
            .reduce(0) { $0 + $1.remaining }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("Ders paketi", systemImage: "shippingbox.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(package.tint)
                Spacer()
                Text(package.statusText)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(package.tint)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(package.tint.opacity(0.12)))
            }

            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("\(package.remaining)")
                    .font(.title2.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
                    .contentTransition(.numericText())
                Text("/ \(package.lessonCount) ders kaldı")
                    .font(.subheadline)
                    .foregroundStyle(Theme.inkSoft)
                Spacer()
                Text(Fmt.money(package.price))
                    .monospacedDigit()
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
            }

            PaidProgressBar(paid: Double(package.used), total: Double(package.lessonCount), tint: package.tint)

            Text(detailText)
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)

            if package.isFinished && otherRemaining == 0 && !student.isArchived {
                Button(action: onNew) {
                    Label("Yeni Paket", systemImage: "plus")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(Theme.board)
            }
        }
        .card(14)
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    private var detailText: String {
        var parts = ["Ders başı \(Fmt.money(package.unitPrice))",
                     "\(Fmt.dayMonthYearShort(package.startDate)) başlangıç"]
        if otherRemaining > 0 {
            parts.append("sırada \(otherRemaining) ders daha")
        }
        return parts.joined(separator: " • ")
    }
}

/// Öğrencinin Ödemeler sekmesindeki paket satırı.
struct PackageRow: View {
    let package: LessonPackage

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "shippingbox.fill")
                .font(.subheadline)
                .foregroundStyle(package.tint)
                .frame(width: 36, height: 36)
                .background(Circle().fill(package.tint.opacity(0.12)))
            VStack(alignment: .leading, spacing: 2) {
                Text("\(package.lessonCount) derslik paket")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                Text("\(Fmt.dayMonthYearShort(package.startDate)) • \(package.used) ders kullanıldı\(package.note.isEmpty ? "" : " • \(package.note)")")
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
                    .lineLimit(1)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(Fmt.money(package.price))
                    .monospacedDigit()
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                Text(package.isFinished ? "bitti" : "\(package.remaining) kaldı")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(package.tint)
            }
        }
        .card(12)
    }
}

// MARK: - Paket formu (ekle / düzenle)

struct PackageFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context

    let student: Student
    var package: LessonPackage? = nil

    @State private var lessonCount: Int
    @State private var price: Double
    @State private var priceEdited: Bool
    @State private var startDate: Date
    @State private var note: String
    @State private var recordPayment = true
    @State private var method: PaymentMethod = .transfer
    @State private var confirmDelete = false

    private let commonCounts = [4, 8, 10, 12]

    init(student: Student, package: LessonPackage? = nil) {
        self.student = student
        self.package = package
        let count = package?.lessonCount ?? 8
        _lessonCount = State(initialValue: count)
        _price = State(initialValue: package?.price
                       ?? Self.suggestedPrice(student: student, count: count))
        _priceEdited = State(initialValue: package != nil)
        _startDate = State(initialValue: package?.startDate ?? Date())
        _note = State(initialValue: package?.note ?? "")
    }

    /// Normal ücretle bu kadar dersin tutarı; paket fiyatı için başlangıç önerisi.
    private static func suggestedPrice(student: Student, count: Int) -> Double {
        let perLesson = Lesson.standardFee(for: student, duration: student.typicalLessonDuration)
        return (perLesson * Double(count)).rounded()
    }

    private var unitPrice: Double {
        lessonCount > 0 ? price / Double(lessonCount) : 0
    }

    private var priceBinding: Binding<Double> {
        Binding(get: { price }, set: { price = $0; priceEdited = true })
    }

    private var countsNow: Int {
        guard package == nil else { return 0 }
        return PackageLedger.lessonsToCount(for: student, from: startDate, limit: lessonCount)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Ders sayısı")
                        HStack(spacing: 6) {
                            ForEach(commonCounts, id: \.self) { count in
                                Button {
                                    setCount(count)
                                } label: {
                                    FormChip(title: "\(count)", isSelected: lessonCount == count, fillsWidth: true)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.vertical, 2)

                    Stepper(value: Binding(get: { lessonCount }, set: { setCount($0) }), in: 1...200) {
                        LabeledContent("Toplam", value: "\(lessonCount) ders")
                    }

                    HStack {
                        Text("Paket ücreti")
                        Spacer()
                        TextField("0", value: priceBinding, format: .number)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 120)
                        Text("₺")
                            .foregroundStyle(Theme.inkSoft)
                    }

                    LabeledContent("Ders başı", value: Fmt.money(unitPrice))

                    DatePicker("Başlangıç", selection: $startDate, displayedComponents: .date)
                } header: {
                    Text("Paket")
                } footer: {
                    Text(footerText)
                }

                if package == nil {
                    Section {
                        Toggle("Ödemesi alındı", isOn: $recordPayment)
                        if recordPayment {
                            Picker("Yöntem", selection: $method) {
                                ForEach(PaymentMethod.allCases, id: \.self) { m in
                                    Text(m.title).tag(m)
                                }
                            }
                            .pickerStyle(.segmented)
                        }
                    } header: {
                        Text("Ödeme")
                    } footer: {
                        Text(recordPayment
                             ? "Paket ücreti \(Fmt.money(price)) ödeme olarak kaydedilir; dersler işlendikçe bakiyeden düşer."
                             : "Ödemeyi sonra Ödeme Al ile ekleyebilirsin.")
                    }
                }

                Section("Not") {
                    TextField("Not (ör. Ekim paketi)", text: $note)
                }

                if let package {
                    Section {
                        LabeledContent("Kullanılan", value: "\(package.used) ders")
                        Button("Paketi Sil", role: .destructive) { confirmDelete = true }
                    } footer: {
                        Text("Silinirse paketten düşülen dersler normal ücretine döner. Paketle alınan ödeme silinmez.")
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle(package == nil ? "Yeni Ders Paketi" : "Ders Paketi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                        .disabled(lessonCount < 1 || price <= 0)
                }
            }
            .confirmationDialog("Paket silinsin mi?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Paketi Sil", role: .destructive) {
                    if let package { PackageLedger.delete(package, in: context) }
                    dismiss()
                }
                Button("Vazgeç", role: .cancel) {}
            } message: {
                Text("Paketten düşülen dersler normal ücretine döner. Paketle alınan ödeme silinmez.")
            }
        }
    }

    private var footerText: String {
        var text = "Bu tarihten sonra işlenen dersler paketten düşer ve ders başı ücretle hesaplanır."
        let normal = Self.suggestedPrice(student: student, count: lessonCount)
        if normal > 0, abs(normal - price) > 0.5 {
            text += " Normal ücretle \(lessonCount) ders \(Fmt.money(normal))."
        }
        if countsNow > 0 {
            text += " Başlangıçtan beri işlenmiş \(countsNow) ders hemen düşülecek."
        }
        return text
    }

    private func setCount(_ count: Int) {
        lessonCount = count
        if !priceEdited {
            price = Self.suggestedPrice(student: student, count: count)
        }
    }

    private func save() {
        if let package {
            package.lessonCount = lessonCount
            package.price = price
            package.startDate = startDate
            package.note = note
            PackageLedger.packageDidChange(package)
        } else {
            let new = LessonPackage(startDate: startDate, lessonCount: lessonCount, price: price, note: note)
            context.insert(new)
            new.student = student
            if recordPayment {
                let payment = Payment(date: startDate, amount: price, method: method,
                                      note: note.isEmpty ? "\(lessonCount) derslik paket" : note)
                context.insert(payment)
                payment.student = student
            }
            PackageLedger.packageDidChange(new)
        }
        try? context.save()
        dismiss()
    }
}
