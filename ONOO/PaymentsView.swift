//
//  PaymentsView.swift
//  One — Ders Defteri
//
//  Ödeme takibi: bakiyeler, tahsilatlar ve ödeme alma formu.
//

import SwiftUI
import Charts
import SwiftData

struct PaymentsView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Payment.date, order: .reverse) private var payments: [Payment]
    @Query(sort: \Student.name) private var students: [Student]

    @State private var showForm = false
    @State private var payingStudent: Student?
    @State private var reminderStudent: Student?
    @State private var editingPayment: Payment?
    @State private var pendingDelete: Payment?

    private var monthCollected: Double {
        let start = Date().startOfMonth
        return payments.filter { $0.date >= start }.reduce(0) { $0 + $1.amount }
    }

    private var pendingTotal: Double {
        students.reduce(0) { $0 + max($1.balance, 0) }
    }

    /// Arşivdeki öğrencinin borcu da burada kalır; üstteki "Bekleyen"
    /// toplamı onu sayıyor, liste saymazsa rakamlar tutmuyordu.
    private var balances: [Student] {
        students
            .filter { abs($0.balance) > 0.5 }
            .sorted { $0.balance > $1.balance }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    collectionCard
                        .padding(.top, 4)

                    balancesSection
                    historySection
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
                .readableWidth()
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Ödemeler")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showForm = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showForm) {
                PaymentFormView()
            }
            .sheet(item: $payingStudent) { student in
                PaymentFormView(student: student)
            }
            .sheet(item: $reminderStudent) { student in
                PaymentReminderSheet(student: student)
            }
            .sheet(item: $editingPayment) { payment in
                PaymentFormView(payment: payment)
            }
            .paymentDeleteDialog($pendingDelete, context: context)
        }
    }

    // MARK: - Tahsilat kartı

    /// İki büyük sayı kartı yerine tek kart: bu ay, bekleyen ve son altı ayın
    /// tahsilatı. Hem daha az yer kaplar hem gidişatı gösterir.
    private var collectionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Bu ay tahsilat")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.inkSoft)
                    Text(Fmt.money(monthCollected))
                        .monospacedDigit()
                        .font(.title2.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(Theme.ink)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("Bekleyen")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.inkSoft)
                    Text(Fmt.money(pendingTotal))
                        .monospacedDigit()
                        .font(.headline.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(pendingTotal > 0.5 ? Theme.red : Theme.ink)
                }
            }

            Chart(monthlyTotals) { item in
                BarMark(x: .value("Ay", item.label),
                        y: .value("Tahsilat", item.total))
                    .foregroundStyle(item.isCurrent ? Theme.green : Theme.green.opacity(0.35))
                    .cornerRadius(4)
            }
            .chartYAxis(.hidden)
            // Yalnız ay adları; dikey kılavuz çizgileri küçük kartı kalabalıklaştırıyordu.
            .chartXAxis {
                AxisMarks { _ in
                    AxisValueLabel()
                }
            }
            .frame(height: 90)
        }
        .card(14)
    }

    private struct MonthTotal: Identifiable {
        let month: Date
        let label: String
        let total: Double
        let isCurrent: Bool
        var id: Date { month }
    }

    private var monthlyTotals: [MonthTotal] {
        let current = Date().startOfMonth
        return (0..<6).reversed().map { back in
            let month = current.adding(months: -back)
            return MonthTotal(month: month,
                              label: Fmt.monthShort.string(from: month),
                              total: monthTotal(month),
                              isCurrent: back == 0)
        }
    }

    private func monthTotal(_ month: Date) -> Double {
        let next = month.adding(months: 1)
        return payments
            .filter { $0.date >= month && $0.date < next }
            .reduce(0) { $0 + $1.amount }
    }

    private var paymentGroups: [(month: Date, payments: [Payment])] {
        let grouped = Dictionary(grouping: payments.prefix(25)) { $0.date.startOfMonth }
        return grouped.keys.sorted(by: >).map { month in
            (month, grouped[month, default: []].sorted { $0.date > $1.date })
        }
    }

    // MARK: - Bakiyeler

    private var balancesSection: some View {
        VStack(spacing: 10) {
            SectionHeader(title: "Bakiyeler", systemImage: "scalemass")
            if balances.isEmpty {
                EmptyStateView(icon: "checkmark.seal.fill",
                               title: "Tüm hesaplar kapalı 🎉",
                               message: "Hiçbir öğrencinin borcu veya avansı yok.")
            } else {
                ForEach(balances) { student in
                    balanceRow(student)
                }
            }
        }
    }

    /// Üstte kim ve ne kadar, altta ödenen oranı ve eylemler. Düğmeler ayrı
    /// satırda durur; dar ekranda yazıları ikiye bölünmesin diye sabitlenir.
    private func balanceRow(_ student: Student) -> some View {
        let owes = student.balance > 0
        return VStack(spacing: 10) {
            HStack(spacing: 12) {
                StudentAvatar(student: student, size: 42)
                    .opacity(student.isArchived ? 0.55 : 1)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(student.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.ink)
                            .lineLimit(1)
                        if student.isArchived {
                            Chip(text: "Arşivde", tint: Theme.inkSoft)
                        }
                    }
                    // Avansta "₺3.300 / ₺3.000 ödendi" tuhaf okunuyordu.
                    Text(owes
                         ? "\(Fmt.money(student.totalPaid)) / \(Fmt.money(student.totalEarned)) ödendi"
                         : "Derslerden \(Fmt.money(-student.balance)) fazla ödendi")
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(Fmt.money(abs(student.balance)))
                        .monospacedDigit()
                        .font(.headline.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(owes ? Theme.red : Theme.blue)
                    Text(owes ? "kalan" : "avans")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Theme.inkSoft)
                }
            }

            if !owes {
                PaidProgressBar(paid: 1, total: 1, tint: Theme.blue)
            }

            if owes {
                PaidProgressBar(paid: student.totalPaid, total: student.totalEarned)

                HStack(spacing: 8) {
                    Spacer()
                    Button {
                        reminderStudent = student
                    } label: {
                        Label("Hatırlat", systemImage: "bell")
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .tint(Theme.red)

                    Button {
                        payingStudent = student
                    } label: {
                        Label("Ödeme Al", systemImage: "plus")
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .tint(Theme.green)
                }
                .font(.caption.weight(.bold))
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .controlSize(.small)
            }
        }
        .card(12)
    }

    // MARK: - Geçmiş

    private var historySection: some View {
        VStack(spacing: 10) {
            SectionHeader(title: "Son Ödemeler", systemImage: "clock.arrow.circlepath")
            if payments.isEmpty {
                EmptyStateView(icon: "turkishlirasign.circle", title: "Henüz ödeme kaydı yok",
                               actionTitle: "Ödeme Ekle", action: { showForm = true })
            } else {
                // Ay ay gruplanır; her ayın başında o ayın toplamı yazar.
                ForEach(paymentGroups, id: \.month) { group in
                    HStack {
                        Text(Fmt.monthYear.string(from: group.month))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Theme.inkSoft)
                        Spacer()
                        Text(Fmt.money(monthTotal(group.month)))
                            .monospacedDigit()
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Theme.green)
                    }
                    .padding(.horizontal, 4)
                    .padding(.top, 4)

                    ForEach(group.payments) { payment in
                        PaymentRow(payment: payment)
                            .contentShape(Rectangle())
                            .onTapGesture { editingPayment = payment }
                            .contextMenu {
                                Button {
                                    editingPayment = payment
                                } label: {
                                    Label("Düzenle", systemImage: "pencil")
                                }
                                Button(role: .destructive) {
                                    pendingDelete = payment
                                } label: {
                                    Label("Sil", systemImage: "trash")
                                }
                            }
                    }
                }
            }
        }
    }
}

// MARK: - Ödeme formu

struct PaymentFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \Student.name) private var students: [Student]

    var student: Student? = nil
    var payment: Payment? = nil

    @State private var studentID: PersistentIdentifier?
    @State private var amount: Double
    @State private var date: Date
    @State private var method: PaymentMethod
    @State private var note: String
    @State private var confirmDelete = false

    init(student: Student? = nil, payment: Payment? = nil) {
        self.student = student
        self.payment = payment
        _studentID = State(initialValue: (payment?.student ?? student)?.persistentModelID)
        _amount = State(initialValue: payment?.amount ?? 0)
        _date = State(initialValue: payment?.date ?? Date())
        _method = State(initialValue: payment?.method ?? .transfer)
        _note = State(initialValue: payment?.note ?? "")
    }

    private var selectedStudent: Student? {
        students.first { $0.persistentModelID == studentID }
    }

    /// Aktif öğrenciler; arşivdeki bir öğrenciden ödeme alınıyorsa o da.
    private var pickerStudents: [Student] {
        students.filter { !$0.isArchived || $0.persistentModelID == studentID }
    }

    /// Düzenlenen ödeme bakiyede zaten sayılı; "bakiyenin tamamı" onu hariç tutar.
    private func balanceExcludingThisPayment(_ student: Student) -> Double {
        guard let payment, payment.student?.persistentModelID == student.persistentModelID else {
            return student.balance
        }
        return student.balance + payment.amount
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Ödeme") {
                    Picker("Öğrenci", selection: $studentID) {
                        Text("Seçiniz").tag(nil as PersistentIdentifier?)
                        ForEach(pickerStudents) { s in
                            Text(s.name).tag(Optional(s.persistentModelID))
                        }
                    }

                    if let s = selectedStudent {
                        LabeledContent("Güncel bakiye") {
                            Text(currentBalanceText(for: s))
                                .foregroundStyle(currentBalanceTint(for: s))
                                .fontWeight(.semibold)
                        }
                    }

                    HStack {
                        Text("Tutar")
                        Spacer()
                        TextField("0", value: $amount, format: .number)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 120)
                        Text("₺")
                            .foregroundStyle(Theme.inkSoft)
                    }

                    if let s = selectedStudent, balanceExcludingThisPayment(s) > 0.5 {
                        Button("Bakiyenin tamamı: \(Fmt.money(balanceExcludingThisPayment(s)))") {
                            amount = balanceExcludingThisPayment(s)
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                    }

                    DatePicker("Tarih", selection: $date, displayedComponents: .date)

                    Picker("Yöntem", selection: $method) {
                        ForEach(PaymentMethod.allCases, id: \.self) { m in
                            Text(m.title).tag(m)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Not") {
                    TextField("Not (ör. Temmuz dersleri)", text: $note)
                }

                if payment != nil {
                    Section {
                        Button("Ödemeyi Sil", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle(payment == nil ? "Ödeme Al" : "Ödemeyi Düzenle")
            .confirmationDialog("Ödeme silinsin mi?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Ödemeyi Sil", role: .destructive) {
                    if let payment {
                        context.delete(payment)
                        try? context.save()
                    }
                    dismiss()
                }
                Button("Vazgeç", role: .cancel) {}
            } message: {
                Text("\(Fmt.money(amount)) tutarındaki ödeme silinir ve öğrencinin bakiyesi buna göre değişir.")
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                        .disabled(studentID == nil || amount <= 0)
                }
            }
        }
    }

    private func save() {
        guard let student = selectedStudent else { return }
        if let payment {
            payment.student = student
            payment.amount = amount
            payment.date = date
            payment.method = method
            payment.note = note
        } else {
            let new = Payment(date: date, amount: amount, method: method, note: note)
            context.insert(new)
            new.student = student
        }
        try? context.save()
        dismiss()
    }

    private func currentBalanceText(for student: Student) -> String {
        if student.balance > 0.5 {
            return "\(Fmt.money(student.balance)) borç"
        }
        if student.balance < -0.5 {
            return "\(Fmt.money(-student.balance)) avans"
        }
        if student.totalEarned <= 0.5 && student.totalPaid <= 0.5 {
            return "Borç yok"
        }
        return "Ödendi"
    }

    private func currentBalanceTint(for student: Student) -> Color {
        if student.balance > 0.5 { return Theme.red }
        if student.balance < -0.5 { return Theme.blue }
        if student.totalEarned <= 0.5 && student.totalPaid <= 0.5 { return Theme.inkSoft }
        return Theme.green
    }
}

// MARK: - Ödeme silme onayı

extension View {
    /// Ödeme uzun basıp "Sil" ile onaysız gidiyordu; tutar bakiyeyi etkilediği
    /// için ne silindiği söylenir.
    func paymentDeleteDialog(_ target: Binding<Payment?>, context: ModelContext) -> some View {
        confirmationDialog("Ödeme silinsin mi?",
                           isPresented: Binding(get: { target.wrappedValue != nil },
                                                set: { if !$0 { target.wrappedValue = nil } }),
                           titleVisibility: .visible,
                           presenting: target.wrappedValue) { payment in
            Button("Ödemeyi Sil", role: .destructive) {
                context.delete(payment)
                try? context.save()
                target.wrappedValue = nil
            }
            Button("Vazgeç", role: .cancel) { target.wrappedValue = nil }
        } message: { payment in
            Text("\(payment.student?.name ?? "Öğrenci") • \(Fmt.money(payment.amount)) • \(Fmt.dayMonthShort.string(from: payment.date))")
        }
    }
}
