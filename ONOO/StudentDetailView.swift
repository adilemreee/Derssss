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
    @Environment(ProStore.self) private var proStore

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
    @State private var pendingDelete: Lesson?
    @State private var stopRepeatTarget: Lesson?
    @State private var editingTemplate: RecurringLessonTemplate?
    @State private var showWeeklyForm = false
    @State private var confirmDelete = false
    @State private var archiveTarget: Student?
    @State private var showPaywall = false
    @State private var editingPayment: Payment?
    @State private var pendingPaymentDelete: Payment?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // İstatistikler ve iletişim başlık kartına alındı; ders listesi
                // eskiden beş bloğun altında, ekranın ancak sonunda başlıyordu.
                header
                balanceBanner
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
            .readableWidth()
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
                        if student.isArchived {
                            unarchive()
                        } else {
                            archiveTarget = student
                        }
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
        .sheet(item: $editingTemplate) { template in
            RecurringTemplateFormView(template: template)
        }
        .sheet(item: $editingPayment) { payment in
            PaymentFormView(payment: payment)
        }
        .sheet(isPresented: $showPaywall) { PaywallView() }
        .studentArchiveDialog($archiveTarget, context: context, onArchived: { dismiss() })
        .paymentDeleteDialog($pendingPaymentDelete, context: context)
        .sheet(isPresented: $showWeeklyForm) {
            RecurringTemplateFormView(defaultStudent: student)
        }
        .lessonDeleteDialog($pendingDelete, context: context)
        .stopRepeatingDialog($stopRepeatTarget, context: context)
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
        VStack(spacing: 14) {
            profileRow
            weeklyRow
            Divider()
            statsRow
            quickActions
        }
        .card()
        .padding(.top, 8)
    }

    private var profileRow: some View {
        HStack(spacing: 14) {
            StudentAvatar(student: student, size: 56)
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
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            Spacer(minLength: 0)
        }
    }

    // MARK: - Haftalık program

    private var weeklyTemplates: [RecurringLessonTemplate] {
        student.allRecurringTemplates.sorted { a, b in
            let ai = RecurringLessonTemplate.weekdayOrder.firstIndex(of: a.weekday) ?? 0
            let bi = RecurringLessonTemplate.weekdayOrder.firstIndex(of: b.weekday) ?? 0
            if ai != bi { return ai < bi }
            return (a.hour, a.minute) < (b.hour, b.minute)
        }
    }

    /// "Her Salı 17:00 · 90 dk" çipleri; dokununca haftalık ders düzenlenir.
    @ViewBuilder
    private var weeklyRow: some View {
        let templates = weeklyTemplates
        if !templates.isEmpty || !student.isArchived {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(templates) { template in
                        Button {
                            editingTemplate = template
                        } label: {
                            weeklyChip(template)
                        }
                        .buttonStyle(.plain)
                    }
                    if !student.isArchived {
                        Button {
                            showWeeklyForm = true
                        } label: {
                            Label(templates.isEmpty ? "Haftalık ders ekle" : "Ekle", systemImage: "plus")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.accent)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .overlay(Capsule().strokeBorder(Theme.line, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 1)
            }
            .scrollClipDisabled()
        }
    }

    private func weeklyChip(_ template: RecurringLessonTemplate) -> some View {
        HStack(spacing: 5) {
            Image(systemName: template.isPaused ? "pause.fill" : "repeat")
                .font(.caption2.weight(.bold))
                .foregroundStyle(template.isPaused ? Theme.inkSoft : student.color)
            Text("\(template.weekdayName) \(template.timeText)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.ink)
            Text("· \(template.duration) dk")
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
        }
        .monospacedDigit()
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(Capsule().fill(student.color.opacity(template.isPaused ? 0.05 : 0.12)))
        .opacity(template.isPaused ? 0.6 : 1)
        .accessibilityLabel("Her \(template.weekdayName) \(template.timeText), \(template.duration) dakika\(template.isPaused ? ", duraklatıldı" : "")")
        .accessibilityHint("Düzenlemek için dokun")
    }

    private var statsRow: some View {
        HStack(spacing: 0) {
            miniStat(value: "\(student.completedLessons.count)", label: "İşlenen ders")
            Divider().frame(height: 30)
            miniStat(value: Fmt.hours(student.totalMinutes), label: "Toplam süre")
            Divider().frame(height: 30)
            miniStat(value: Fmt.money(student.totalEarned), label: "Ders tutarı")
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
    }

    @ViewBuilder
    private var balanceBanner: some View {
        if student.balance > 0.5 {
            // Dolu kırmızı bant sayfanın geri kalanını bastırıyordu. Açık
            // zemin + ödenen oranı çubuğu hem daha sakin hem daha bilgilendirici.
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Bekleyen ödeme")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.red)
                        Text(Fmt.money(student.balance))
                            .monospacedDigit()
                            .font(.title3.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(Theme.ink)
                    }
                    Spacer()
                    Text("\(Fmt.money(student.totalPaid)) / \(Fmt.money(student.totalEarned)) ödendi")
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }

                PaidProgressBar(paid: student.totalPaid, total: student.totalEarned)

                HStack(spacing: 8) {
                    Button {
                        showReminder = true
                    } label: {
                        Label("Hatırlat", systemImage: "bell")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(Theme.red)

                    Button {
                        showPaymentForm = true
                    } label: {
                        Label("Ödeme Al", systemImage: "plus")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.board)
                }
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.red.opacity(0.08)))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.red.opacity(0.25), lineWidth: 1))
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
            ContactActionButton(icon: "phone.fill", title: "Ara", tint: Theme.green, isDisabled: !hasPhone, isBordered: false) {
                if let url = StudentSharing.dialURL(for: phone) { openURL(url) }
            }
            ContactActionButton(icon: "message.fill", title: "SMS", tint: Theme.blue, isDisabled: !hasPhone, isBordered: false) {
                if let url = StudentSharing.smsURL(for: phone) { openURL(url) }
            }
            ContactActionButton(icon: "bubble.left.and.bubble.right.fill", title: "WhatsApp", tint: Theme.accent, isDisabled: !hasPhone, isBordered: false) {
                let text = "Merhaba, \(student.name) için ders durumunu paylaşmak istiyorum."
                if let url = StudentSharing.whatsappURL(for: phone, text: text) { openURL(url) }
            }
            ContactActionButton(icon: "square.and.arrow.up.fill", title: "Özet", tint: Theme.amber, isBordered: false) {
                showSummary = true
            }
        }
    }

    // MARK: - Dersler sekmesi

    private var lessonsTab: some View {
        VStack(spacing: 10) {
            if sortedLessons.isEmpty {
                EmptyStateView(icon: "calendar", title: "Ders kaydı yok",
                               actionTitle: "Ders Ekle", action: { showLessonForm = true })
            } else {
                ForEach(sortedLessons) { lesson in
                    LessonRow(lesson: lesson, showDate: true, showStudent: false)
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
                            if lesson.sourceTemplate == nil {
                                Button {
                                    LessonActions.copyNextWeek(lesson, in: context)
                                } label: {
                                    Label("Haftaya Aynı Ders", systemImage: "calendar.badge.plus")
                                }
                            }
                            Button {
                                showLessonForm = true
                            } label: {
                                Label("Aynı Öğrenciye Yeni Ders", systemImage: "person.crop.circle.badge.plus")
                            }
                            if lesson.sourceTemplate == nil {
                                Button {
                                    LessonActions.makeWeekly(lesson, in: context)
                                } label: {
                                    Label("Her Hafta Tekrarla", systemImage: "repeat")
                                }
                            } else {
                                Button(role: .destructive) {
                                    stopRepeatTarget = lesson
                                } label: {
                                    Label("Tekrarı Durdur", systemImage: "repeat.circle")
                                }
                            }
                            Divider()
                            Button(role: .destructive) {
                                pendingDelete = lesson
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
                EmptyStateView(icon: "turkishlirasign.circle", title: "Ödeme kaydı yok",
                               actionTitle: "Ödeme Al", action: { showPaymentForm = true })
            } else {
                ForEach(sortedPayments) { payment in
                    PaymentRow(payment: payment, showStudent: false)
                        .contentShape(Rectangle())
                        .onTapGesture { editingPayment = payment }
                        .contextMenu {
                            Button {
                                editingPayment = payment
                            } label: {
                                Label("Düzenle", systemImage: "pencil")
                            }
                            Button(role: .destructive) {
                                pendingPaymentDelete = payment
                            } label: {
                                Label("Sil", systemImage: "trash")
                            }
                        }
                }
            }
        }
    }

    /// Arşivden dönmek de ücretsiz sürümün öğrenci sınırına tabidir.
    private func unarchive() {
        let activeCount = (try? context.fetchCount(FetchDescriptor<Student>(predicate: #Predicate { !$0.isArchived }))) ?? 0
        if proStore.canAddStudent(activeCount: activeCount) {
            StudentActions.unarchive(student, in: context)
        } else {
            showPaywall = true
        }
    }

    private var sortedPayments: [Payment] {
        student.allPayments.sorted { $0.date > $1.date }
    }

    // MARK: - Ödevler sekmesi

    private var homeworkTab: some View {
        VStack(spacing: 10) {
            if sortedHomeworks.isEmpty {
                EmptyStateView(icon: "book", title: "Ödev yok",
                               actionTitle: "Ödev Ver", action: { showHomeworkForm = true })
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
                .foregroundStyle(Theme.accent)
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
    /// Başka bir kartın içindeyken kendi çerçevesi olmaz.
    var isBordered = true
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
            .padding(.vertical, isBordered ? 10 : 0)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(isBordered ? Theme.card : .clear))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(isBordered ? Theme.line : .clear, lineWidth: 1))
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

                    // Veliye gidecek mesaj, gideceği biçimde: bir mesaj balonu.
                    VStack(alignment: .trailing, spacing: 6) {
                        Text("Veliye gidecek mesaj")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.inkSoft)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(text)
                            .font(.subheadline)
                            .foregroundStyle(Theme.ink)
                            .textSelection(.enabled)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(
                                UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 18,
                                                       bottomTrailingRadius: 4, topTrailingRadius: 18,
                                                       style: .continuous)
                                    .fill(Theme.green.opacity(0.14))
                            )
                            .padding(.leading, 24)
                    }

                    // Ana eylem veliye WhatsApp'tan göndermek; kopyala ve
                    // paylaş ikincil. Soluk yeşil düğme basılamaz gibi görünüyordu.
                    Button {
                        let phone = StudentSharing.bestContactPhone(for: student)
                        if let url = StudentSharing.whatsappURL(for: phone, text: text) {
                            openURL(url)
                        }
                    } label: {
                        Label("WhatsApp'ta Gönder", systemImage: "bubble.left.and.bubble.right.fill")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 4)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(hex: 0x128C7E))
                    .disabled(StudentSharing.cleanPhone(StudentSharing.bestContactPhone(for: student)).isEmpty)

                    HStack(spacing: 10) {
                        Button {
                            UIPasteboard.general.string = text
                            copied = true
                        } label: {
                            Label(copied ? "Kopyalandı" : "Kopyala", systemImage: copied ? "checkmark" : "doc.on.doc")
                                .frame(maxWidth: .infinity)
                        }

                        ShareLink(item: text) {
                            Label("Paylaş", systemImage: "square.and.arrow.up")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.bordered)
                    .tint(Theme.accent)

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
                .tint(Theme.accent)

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
                        .foregroundStyle(Theme.accent)
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
                    .tint(Theme.accent)

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
                .monospacedDigit()
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
