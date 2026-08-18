//
//  PaymentsView.swift
//  One — Ders Defteri
//
//  Ödeme takibi: bakiyeler, tahsilatlar ve ödeme alma formu.
//

import SwiftUI
import SwiftData

struct PaymentsView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Payment.date, order: .reverse) private var payments: [Payment]
    @Query(sort: \Student.name) private var students: [Student]

    @State private var showForm = false
    @State private var payingStudent: Student?
    @State private var reminderStudent: Student?

    private var monthCollected: Double {
        let start = Date().startOfMonth
        return payments.filter { $0.date >= start }.reduce(0) { $0 + $1.amount }
    }

    private var pendingTotal: Double {
        students.reduce(0) { $0 + max($1.balance, 0) }
    }

    private var balances: [Student] {
        students
            .filter { !$0.isArchived && abs($0.balance) > 0.5 }
            .sorted { $0.balance > $1.balance }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    HStack(spacing: 12) {
                        StatCard(icon: "tray.and.arrow.down.fill", title: "Bu Ay Tahsilat",
                                 value: Fmt.money(monthCollected), tint: Theme.green)
                        StatCard(icon: "hourglass", title: "Bekleyen Alacak",
                                 value: Fmt.money(pendingTotal), tint: Theme.red)
                    }
                    .padding(.top, 4)

                    balancesSection
                    historySection
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
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
                    HStack(spacing: 12) {
                        StudentAvatar(student: student, size: 42)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(student.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.ink)
                            Text("\(Fmt.money(student.totalEarned)) ders tutarı • \(Fmt.money(student.totalPaid)) ödendi")
                                .font(.caption)
                                .foregroundStyle(Theme.inkSoft)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 4) {
                            Text(student.balance > 0 ? Fmt.money(student.balance) : Fmt.money(-student.balance))
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(student.balance > 0 ? Theme.red : Theme.blue)
                            if student.balance > 0 {
                                HStack(spacing: 6) {
                                    Button("Hatırlat") {
                                        reminderStudent = student
                                    }
                                    .font(.caption.weight(.bold))
                                    .buttonStyle(.bordered)
                                    .buttonBorderShape(.capsule)
                                    .controlSize(.mini)
                                    .tint(Theme.red)

                                    Button("Ödeme Al") {
                                        payingStudent = student
                                    }
                                    .font(.caption.weight(.bold))
                                    .buttonStyle(.bordered)
                                    .buttonBorderShape(.capsule)
                                    .controlSize(.mini)
                                    .tint(Theme.green)
                                }
                            } else {
                                Chip(text: "Avans", tint: Theme.blue)
                            }
                        }
                    }
                    .card(12)
                }
            }
        }
    }

    // MARK: - Geçmiş

    private var historySection: some View {
        VStack(spacing: 10) {
            SectionHeader(title: "Son Ödemeler", systemImage: "clock.arrow.circlepath")
            if payments.isEmpty {
                EmptyStateView(icon: "turkishlirasign.circle", title: "Henüz ödeme kaydı yok")
            } else {
                ForEach(payments.prefix(25)) { payment in
                    PaymentRow(payment: payment)
                        .contextMenu {
                            Button(role: .destructive) {
                                context.delete(payment)
                                try? context.save()
                            } label: {
                                Label("Sil", systemImage: "trash")
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

    @State private var studentID: PersistentIdentifier?
    @State private var amount: Double = 0
    @State private var date = Date()
    @State private var method: PaymentMethod = .transfer
    @State private var note = ""

    init(student: Student? = nil) {
        self.student = student
        _studentID = State(initialValue: student?.persistentModelID)
    }

    private var selectedStudent: Student? {
        students.first { $0.persistentModelID == studentID }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Ödeme") {
                    Picker("Öğrenci", selection: $studentID) {
                        Text("Seçiniz").tag(nil as PersistentIdentifier?)
                        ForEach(students.filter { !$0.isArchived }) { s in
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

                    if let s = selectedStudent, s.balance > 0.5 {
                        Button("Bakiyenin tamamı: \(Fmt.money(s.balance))") {
                            amount = s.balance
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.board)
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
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle("Ödeme Al")
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
        let payment = Payment(date: date, amount: amount, method: method, note: note)
        context.insert(payment)
        payment.student = student
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
