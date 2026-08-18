//
//  StudentDetailView.swift
//  One — Ders Defteri
//
//  Öğrenci profili: dersler, ödemeler, ödevler ve iletişim bilgileri.
//

import SwiftUI
import SwiftData
import UIKit

struct StudentDetailView: View {
    @Bindable var student: Student
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    private enum DetailTab: String, CaseIterable {
        case lessons = "Dersler"
        case payments = "Ödemeler"
        case homework = "Ödevler"
        case info = "Bilgi"
    }

    @State private var tab: DetailTab = .lessons
    @State private var showEdit = false
    @State private var showLessonForm = false
    @State private var showPaymentForm = false
    @State private var showHomeworkForm = false
    @State private var showSummary = false
    @State private var showReminder = false
    @State private var editingLesson: Lesson?
    @State private var cancellationTarget: Lesson?
    @State private var confirmDelete = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                header
                statsRow
                balanceBanner
                quickActions
                Picker("Bölüm", selection: $tab) {
                    ForEach(DetailTab.allCases, id: \.self) { t in
                        Text(t.rawValue).tag(t)
                    }
                }
                .pickerStyle(.segmented)

                switch tab {
                case .lessons: lessonsTab
                case .payments: paymentsTab
                case .homework: homeworkTab
                case .info: infoTab
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .background(Theme.paper.ignoresSafeArea())
        .navigationTitle(student.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { showLessonForm = true } label: { Label("Ders Ekle", systemImage: "calendar.badge.plus") }
                    Button { showPaymentForm = true } label: { Label("Ödeme Al", systemImage: "turkishlirasign.circle") }
                    Button { showHomeworkForm = true } label: { Label("Ödev Ver", systemImage: "book") }
                    Divider()
                    Button {
                        student.isArchived.toggle()
                        try? context.save()
                        if student.isArchived { dismiss() }
                    } label: {
                        Label(student.isArchived ? "Aktife Al" : "Arşivle",
                              systemImage: student.isArchived ? "arrow.uturn.backward" : "archivebox")
                    }
                    Button { showEdit = true } label: { Label("Düzenle", systemImage: "pencil") }
                    Button(role: .destructive) { confirmDelete = true } label: { Label("Öğrenciyi Sil", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .confirmationDialog("\(student.name) ve tüm ders/ödeme/ödev kayıtları silinecek. Emin misin?",
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Sil", role: .destructive) {
                context.delete(student)
                try? context.save()
                dismiss()
            }
            Button("Vazgeç", role: .cancel) {}
        }
        .sheet(isPresented: $showEdit) { StudentFormView(student: student) }
        .sheet(isPresented: $showLessonForm) { LessonFormView(defaultStudent: student) }
        .sheet(isPresented: $showPaymentForm) { PaymentFormView(student: student) }
        .sheet(isPresented: $showHomeworkForm) { HomeworkFormView(defaultStudent: student) }
        .sheet(isPresented: $showSummary) { StudentSummarySheet(student: student) }
        .sheet(isPresented: $showReminder) { PaymentReminderSheet(student: student) }
        .sheet(item: $editingLesson) { lesson in
            LessonFormView(lesson: lesson)
        }
        .confirmationDialog("İptal sebebi seç",
                            isPresented: Binding(
                                get: { cancellationTarget != nil },
                                set: { if !$0 { cancellationTarget = nil } }
                            ),
                            titleVisibility: .visible) {
            Button(CancellationReason.student.title) { cancelTargetLesson(.student) }
            Button(CancellationReason.teacher.title) { cancelTargetLesson(.teacher) }
            Button(CancellationReason.makeup.title) { cancelTargetLesson(.makeup) }
            Button("Vazgeç", role: .cancel) { cancellationTarget = nil }
        }
    }

    // MARK: - Başlık

    private var header: some View {
        HStack(spacing: 16) {
            StudentAvatar(student: student, size: 64)
            VStack(alignment: .leading, spacing: 6) {
                Text(student.name)
                    .font(.title3.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
                HStack(spacing: 6) {
                    Chip(text: student.subject.isEmpty ? "Ders yok" : student.subject, tint: student.color, filled: true)
                    if !student.grade.isEmpty {
                        Chip(text: student.grade, tint: Theme.inkSoft)
                    }
                }
                Text("Saatlik ücret: \(Fmt.money(student.hourlyRate)) • Başlangıç: \(Fmt.dayMonthShort.string(from: student.startDate))")
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }
            Spacer()
        }
        .card()
        .padding(.top, 8)
    }

    private var statsRow: some View {
        HStack(spacing: 12) {
            miniStat(value: "\(student.completedLessons.count)", label: "İşlenen Ders")
            miniStat(value: Fmt.hours(student.totalMinutes), label: "Toplam Süre")
            miniStat(value: Fmt.money(student.totalEarned), label: "Ders Tutarı")
        }
    }

    private func miniStat(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.headline.weight(.bold))
                .fontDesign(.serif)
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.caption2)
                .foregroundStyle(Theme.inkSoft)
        }
        .frame(maxWidth: .infinity)
        .card(12)
    }

    @ViewBuilder
    private var balanceBanner: some View {
        if student.balance > 0.5 {
            HStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.white)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Bekleyen ödeme: \(Fmt.money(student.balance))")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                    Text("\(Fmt.money(student.totalPaid)) ödendi / \(Fmt.money(student.totalEarned)) ders tutarı")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.85))
                }
                Spacer()
                VStack(spacing: 6) {
                    Button("Ödeme Al") { showPaymentForm = true }
                        .font(.caption.weight(.bold))
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.capsule)
                        .controlSize(.small)
                        .tint(.white.opacity(0.22))
                    Button("Hatırlat") { showReminder = true }
                        .font(.caption.weight(.bold))
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.capsule)
                        .controlSize(.small)
                        .tint(.white)
                }
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.red))
        } else {
            HStack {
                Image(systemName: balanceZeroIcon)
                    .foregroundStyle(balanceZeroTint)
                Text(balanceZeroText)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                Spacer()
            }
            .card(14)
        }
    }

    private var balanceZeroText: String {
        if student.balance < -0.5 {
            return "Avans var: \(Fmt.money(-student.balance))"
        }
        if student.totalEarned <= 0.5 && student.totalPaid <= 0.5 {
            let planned = student.allLessons.filter { $0.status == .planned }.count
            if planned > 0 {
                return "\(planned) planlı ders var, henüz ödeme beklenmiyor"
            }
            return "Henüz işlenen ders veya ödeme kaydı yok"
        }
        return "Tüm ödemeler alındı"
    }

    private var balanceZeroIcon: String {
        if student.balance < -0.5 { return "arrow.down.circle.fill" }
        if student.totalEarned <= 0.5 && student.totalPaid <= 0.5 { return "calendar.badge.clock" }
        return "checkmark.seal.fill"
    }

    private var balanceZeroTint: Color {
        if student.balance < -0.5 { return Theme.blue }
        if student.totalEarned <= 0.5 && student.totalPaid <= 0.5 { return Theme.amber }
        return Theme.green
    }

    // MARK: - Hızlı aksiyonlar

    private var quickActions: some View {
        let phone = StudentSharing.bestContactPhone(for: student)
        let hasPhone = !StudentSharing.cleanPhone(phone).isEmpty
        return HStack(spacing: 10) {
            ContactActionButton(icon: "phone.fill", title: "Ara", tint: Theme.green, isDisabled: !hasPhone) {
                if let url = StudentSharing.dialURL(for: phone) { openURL(url) }
            }
            ContactActionButton(icon: "message.fill", title: "SMS", tint: Theme.blue, isDisabled: !hasPhone) {
                if let url = StudentSharing.smsURL(for: phone) { openURL(url) }
            }
            ContactActionButton(icon: "bubble.left.and.bubble.right.fill", title: "WhatsApp", tint: Theme.board, isDisabled: !hasPhone) {
                let text = "Merhaba, \(student.name) için ders durumunu paylaşmak istiyorum."
                if let url = StudentSharing.whatsappURL(for: phone, text: text) { openURL(url) }
            }
            ContactActionButton(icon: "square.and.arrow.up.fill", title: "Özet", tint: Theme.amber) {
                showSummary = true
            }
        }
    }

    // MARK: - Dersler sekmesi

    private var lessonsTab: some View {
        VStack(spacing: 10) {
            if sortedLessons.isEmpty {
                EmptyStateView(icon: "calendar", title: "Ders kaydı yok",
                               message: "Sağ üst menüden ders ekleyebilirsin.")
            } else {
                ForEach(sortedLessons) { lesson in
                    LessonRow(lesson: lesson, showDate: true)
                        .onTapGesture { editingLesson = lesson }
                        .contextMenu {
                            ForEach(LessonStatus.allCases, id: \.self) { status in
                                if status != lesson.status {
                                    Button {
                                        if status == .cancelled {
                                            cancellationTarget = lesson
                                        } else {
                                            lesson.status = status
                                            lesson.cancellationReason = .none
                                            try? context.save()
                                        }
                                    } label: {
                                        Label(status.title, systemImage: status.icon)
                                    }
                                }
                            }
                            Divider()
                            Button {
                                LessonActions.copyNextWeek(lesson, in: context)
                            } label: {
                                Label("Haftaya Aynı Ders", systemImage: "calendar.badge.plus")
                            }
                            Button {
                                showLessonForm = true
                            } label: {
                                Label("Aynı Öğrenciye Yeni Ders", systemImage: "person.crop.circle.badge.plus")
                            }
                            Button {
                                LessonActions.copyFourWeeks(lesson, in: context)
                            } label: {
                                Label("4 Hafta Tekrar Oluştur", systemImage: "repeat")
                            }
                            Divider()
                            Button(role: .destructive) {
                                context.delete(lesson)
                                try? context.save()
                            } label: {
                                Label("Sil", systemImage: "trash")
                            }
                        }
                }
            }
        }
    }

    private func cancelTargetLesson(_ reason: CancellationReason) {
        cancellationTarget?.status = .cancelled
        cancellationTarget?.cancellationReason = reason
        try? context.save()
        cancellationTarget = nil
    }

    private var sortedLessons: [Lesson] {
        student.allLessons.sorted { $0.date > $1.date }
    }

    // MARK: - Ödemeler sekmesi

    private var paymentsTab: some View {
        VStack(spacing: 10) {
            if sortedPayments.isEmpty {
                EmptyStateView(icon: "turkishlirasign.circle", title: "Ödeme kaydı yok")
            } else {
                ForEach(sortedPayments) { payment in
                    PaymentRow(payment: payment, showStudent: false)
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

    private var sortedPayments: [Payment] {
        student.allPayments.sorted { $0.date > $1.date }
    }

    // MARK: - Ödevler sekmesi

    private var homeworkTab: some View {
        VStack(spacing: 10) {
            if sortedHomeworks.isEmpty {
                EmptyStateView(icon: "book", title: "Ödev yok")
            } else {
                ForEach(sortedHomeworks) { hw in
                    HStack(spacing: 12) {
                        Button {
                            hw.isDone.toggle()
                            hw.doneDate = hw.isDone ? Date() : nil
                            try? context.save()
                        } label: {
                            Image(systemName: hw.isDone ? "checkmark.circle.fill" : "circle")
                                .font(.title3)
                                .foregroundStyle(hw.isDone ? Theme.green : Theme.inkSoft)
                        }
                        .buttonStyle(.plain)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(hw.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.ink)
                                .strikethrough(hw.isDone)
                            Text(hw.isLate ? "Gecikti! Son: \(Fmt.dayMonthShort.string(from: hw.dueDate))"
                                           : "Son: \(Fmt.dayMonthShort.string(from: hw.dueDate))")
                                .font(.caption)
                                .foregroundStyle(hw.isLate ? Theme.red : Theme.inkSoft)
                        }
                        Spacer()
                    }
                    .card(12)
                    .contextMenu {
                        Button(role: .destructive) {
                            context.delete(hw)
                            try? context.save()
                        } label: {
                            Label("Sil", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    private var sortedHomeworks: [Homework] {
        student.allHomeworks.sorted { $0.dueDate > $1.dueDate }
    }

    // MARK: - Bilgi sekmesi

    private var infoTab: some View {
        VStack(spacing: 10) {
            infoRow(icon: "phone.fill", label: "Öğrenci", value: student.phone)
            infoRow(icon: "person.fill", label: "Veli", value: student.parentName)
            infoRow(icon: "phone.fill", label: "Veli Telefonu", value: student.parentPhone)
            infoRow(icon: "calendar", label: "Başlangıç", value: Fmt.long.string(from: student.startDate))
            if !student.notes.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Notlar", systemImage: "note.text")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Theme.inkSoft)
                    Text(student.notes)
                        .font(.subheadline)
                        .foregroundStyle(Theme.ink)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .card(14)
            }
        }
    }

    private func infoRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(Theme.board)
                .frame(width: 28)
            Text(label)
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
            Spacer()
            Text(value.isEmpty ? "—" : value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.ink)
        }
        .card(14)
    }
}

// MARK: - Profil hızlı aksiyon butonu

struct ContactActionButton: View {
    let icon: String
    let title: String
    let tint: Color
    var isDisabled = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(isDisabled ? Theme.inkSoft : tint)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill((isDisabled ? Theme.inkSoft : tint).opacity(0.12)))
                Text(title)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(isDisabled ? Theme.inkSoft : Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.card))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.55 : 1)
    }
}

// MARK: - Öğrenci özeti

struct StudentSummarySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(ProStore.self) private var proStore
    @AppStorage("teacherName") private var teacherName = ""
    let student: Student

    @State private var period: StudentSummaryPeriod = .week
    @State private var copied = false
    @State private var pdfURL: URL?
    @State private var showPaywall = false

    private var text: String {
        StudentSharing.summary(for: student, period: period)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Picker("Dönem", selection: $period) {
                        ForEach(StudentSummaryPeriod.allCases) { p in
                            Text(p.title).tag(p)
                        }
                    }
                    .pickerStyle(.segmented)

                    Text(text)
                        .font(.subheadline)
                        .foregroundStyle(Theme.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                        .card(14)

                    HStack(spacing: 10) {
                        Button {
                            UIPasteboard.general.string = text
                            copied = true
                        } label: {
                            Label(copied ? "Kopyalandı" : "Kopyala", systemImage: copied ? "checkmark" : "doc.on.doc")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .tint(Theme.board)

                        ShareLink(item: text) {
                            Label("Paylaş", systemImage: "square.and.arrow.up")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Theme.board)
                    }

                    Button {
                        let phone = StudentSharing.bestContactPhone(for: student)
                        if let url = StudentSharing.whatsappURL(for: phone, text: text) {
                            openURL(url)
                        }
                    } label: {
                        Label("WhatsApp'ta Aç", systemImage: "bubble.left.and.bubble.right.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(Theme.green)
                    .disabled(StudentSharing.cleanPhone(StudentSharing.bestContactPhone(for: student)).isEmpty)

                    Divider().padding(.vertical, 4)

                    pdfSection
                }
                .padding(16)
                .padding(.bottom, 12)
            }
            .background(Theme.paper.ignoresSafeArea())
            .sheet(isPresented: $showPaywall) { PaywallView() }
            .onChange(of: period) { pdfURL = nil }
            .navigationTitle("Paylaşılabilir Özet")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tamam") { dismiss() }
                }
            }
        }
    }

    /// Veliye gönderilebilir belge. Düz metin özet herkese açık; biçimli PDF
    /// Pro'ya ait.
    @ViewBuilder
    private var pdfSection: some View {
        if proStore.isPro {
            VStack(spacing: 10) {
                Button {
                    let data = StudentReportData(student: student,
                                                 period: period,
                                                 teacherName: teacherName)
                    pdfURL = StudentReportPDF.make(data: data)
                } label: {
                    Label(pdfURL == nil ? "PDF Rapor Hazırla" : "PDF'i Yenile",
                          systemImage: "doc.richtext")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(Theme.board)

                if let pdfURL {
                    ShareLink(item: pdfURL) {
                        Label("PDF Raporu Paylaş", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.board)
                }
            }
        } else {
            Button {
                showPaywall = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "doc.richtext")
                        .foregroundStyle(Theme.board)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("PDF Veli Raporu")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.ink)
                        Text("Biçimli, yazdırılabilir rapor olarak paylaş")
                            .font(.caption)
                            .foregroundStyle(Theme.inkSoft)
                    }
                    Spacer()
                    Image(systemName: "lock.fill")
                        .font(.caption)
                        .foregroundStyle(Theme.amber)
                }
                .card(12)
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Ödeme hatırlatma

struct PaymentReminderSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let student: Student

    @State private var copied = false

    private var text: String {
        StudentSharing.paymentReminder(for: student)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Chalkboard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(student.name)
                            .font(.title3.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(.white)
                        Text("Bekleyen ödeme: \(StudentSharing.balanceText(for: student))")
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.82))
                    }
                }

                Text(text)
                    .font(.subheadline)
                    .foregroundStyle(Theme.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
                    .card(14)

                HStack(spacing: 10) {
                    Button {
                        UIPasteboard.general.string = text
                        copied = true
                    } label: {
                        Label(copied ? "Kopyalandı" : "Kopyala", systemImage: copied ? "checkmark" : "doc.on.doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(Theme.board)

                    Button {
                        let phone = StudentSharing.bestContactPhone(for: student)
                        if let url = StudentSharing.whatsappURL(for: phone, text: text) {
                            openURL(url)
                        }
                    } label: {
                        Label("WhatsApp", systemImage: "bubble.left.and.bubble.right.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.green)
                    .disabled(StudentSharing.cleanPhone(StudentSharing.bestContactPhone(for: student)).isEmpty)
                }

                Spacer()
            }
            .padding(16)
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Ödeme Hatırlat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tamam") { dismiss() }
                }
            }
        }
    }
}


// MARK: - Ödeme satırı (ortak)

struct PaymentRow: View {
    let payment: Payment
    var showStudent: Bool = true

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: payment.method.icon)
                .font(.subheadline)
                .foregroundStyle(Theme.green)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Theme.green.opacity(0.12)))
            VStack(alignment: .leading, spacing: 2) {
                Text(showStudent ? (payment.student?.name ?? "—") : payment.method.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
                    .lineLimit(1)
            }
            Spacer()
            Text("+\(Fmt.money(payment.amount))")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.green)
        }
        .card(12)
    }

    private var subtitle: String {
        var parts = [Fmt.dayMonthShort.string(from: payment.date)]
        if showStudent { parts.append(payment.method.title) }
        if !payment.note.isEmpty { parts.append(payment.note) }
        return parts.joined(separator: " • ")
    }
}
