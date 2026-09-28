//
//  DashboardView.swift
//  One — Ders Defteri
//
//  Özet ekranı: günün dersleri, haftalık yük, kazanç ve bekleyen alacaklar.
//

import SwiftUI
import SwiftData

struct DashboardView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Lesson.date) private var lessons: [Lesson]
    @Query(sort: \Student.name) private var students: [Student]
    @Query(sort: \Homework.dueDate) private var homeworks: [Homework]
    @Query(sort: \Payment.date) private var payments: [Payment]
    @AppStorage("teacherName") private var teacherName = ""

    @State private var payingStudent: Student?
    @State private var showSettings = false
    @State private var showStudentForm = false
    @State private var showQuickLesson = false
    @State private var newLessonForStudentFrom: Lesson?
    @State private var cancellationTarget: Lesson?

    private struct UpcomingDayGroup: Identifiable {
        let date: Date
        let lessons: [Lesson]

        var id: Date { date.startOfDay }

        var minutes: Int {
            lessons.reduce(0) { $0 + $1.duration }
        }

        var expected: Double {
            lessons.reduce(0.0) { $0 + $1.fee }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    if students.isEmpty {
                        welcomeContent
                    } else {
                        // Günün dersleri en üstte: ekran açıldığında ilk
                        // bakılan şey bu. Sayılar tek satırlık şeride indi.
                        headerBoard
                        todaySection
                        statsStrip
                        upcomingSection
                        debtorsSection
                        homeworkSection
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
                .readableWidth()
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Ders Defteri")
            .toolbar {
                // "Tüm Verileri Sil" Ayarlar'ın en altına taşındı; Ayarlar'ın
                // hemen altında durması yanlışlıkla basılmaya çok açıktı.
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Ayarlar")
                }
            }
            .sheet(item: $payingStudent) { student in
                PaymentFormView(student: student)
            }
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
            .sheet(isPresented: $showStudentForm) {
                StudentFormView()
            }
            .sheet(isPresented: $showQuickLesson) {
                QuickLessonFormView()
            }
            .sheet(item: $newLessonForStudentFrom) { lesson in
                LessonFormView(defaultStudent: lesson.student, defaultDate: lesson.date.adding(days: 7))
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
    }

    // MARK: - Hoş geldin (boş başlangıç)

    private var welcomeContent: some View {
        VStack(spacing: 22) {
            Chalkboard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Hoş geldin \(teacherDisplayName)\u{00A0}👋")
                        .font(.title2.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(.white)
                    Text("Ders Defteri; özel ders programını, ücretleri, ödemeleri ve ödevleri tek yerden takip etmen için hazır. İlk öğrencini ekleyerek başla.")
                        .font(.subheadline)
                        .fontDesign(.serif)
                        .italic()
                        .foregroundStyle(.white.opacity(0.88))
                }
            }
            .padding(.top, 4)

            VStack(spacing: 10) {
                featureRow(icon: "graduationcap.fill", tint: Theme.blue,
                           title: "Öğrenci kartları",
                           text: "Ders, sınıf, saatlik ücret ve veli bilgileri")
                featureRow(icon: "calendar", tint: Theme.amber,
                           title: "Haftalık program",
                           text: "Dersleri planla, işle ya da iptal et")
                featureRow(icon: "turkishlirasign.circle.fill", tint: Theme.green,
                           title: "Ödeme takibi",
                           text: "Kim ne kadar ödedi, kimde bakiye kaldı")
                featureRow(icon: "bell.badge.fill", tint: Theme.red,
                           title: "Ders hatırlatıcıları",
                           text: "Ders yaklaşınca bildirim al")
                featureRow(icon: "arrow.triangle.2.circlepath", tint: Theme.accent,
                           title: "Hesabında yedekli",
                           text: "Yeni telefonda kayıtların geri gelir",
                           isPro: true)
            }

            Button {
                showStudentForm = true
            } label: {
                Label("İlk Öğrencini Ekle", systemImage: "plus")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .tint(Theme.board)
        }
    }

    /// `isPro`: ücretsiz kullanıcıya Pro özelliğini ücretsizmiş gibi göstermemek için.
    private func featureRow(icon: String, tint: Color, title: String, text: String,
                            isPro: Bool = false) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 36, height: 36)
                .background(Circle().fill(tint.opacity(0.12)))
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(title)
                        .font(.subheadline.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(Theme.ink)
                    if isPro {
                        Chip(text: "Pro", tint: Theme.amber, filled: true)
                    }
                }
                Text(text)
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }
            Spacer()
        }
        .card(12)
    }

    // MARK: - Kara tahta başlık

    private var headerBoard: some View {
        Chalkboard {
            VStack(alignment: .leading, spacing: 6) {
                Text(Fmt.long.string(from: Date()) + " · " + Fmt.weekday.string(from: Date()))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.65))
                Text(greeting)
                    .font(.title2.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(.white)
                Text(todaySummary)
                    .font(.subheadline)
                    .fontDesign(.serif)
                    .italic()
                    .foregroundStyle(.white.opacity(0.88))
            }
        }
        .padding(.top, 4)
    }

    private var greeting: String {
        let hour = Calendar.tr.component(.hour, from: Date())
        switch hour {
        case 5..<12: return "Günaydın \(teacherDisplayName)\u{00A0}👋"
        case 12..<18: return "İyi dersler \(teacherDisplayName)\u{00A0}👋"
        default: return "İyi akşamlar \(teacherDisplayName)\u{00A0}👋"
        }
    }

    private var teacherDisplayName: String {
        let trimmed = teacherName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "Öğretmenim" }
        let formatted = trimmed.capitalized(with: Locale(identifier: "tr_TR"))
        return "\(formatted) öğretmenim"
    }

    private var todaySummary: String {
        let active = todayLessons.filter { $0.status != .cancelled }
        if active.isEmpty { return "Bugün ders yok — kendine bir çay ısmarla ☕️" }
        let minutes = active.reduce(0) { $0 + $1.duration }
        let expected = active.reduce(0.0) { $0 + $1.fee }
        return "Bugün \(active.count) ders • \(Fmt.hours(minutes)) • \(Fmt.money(expected))"
    }

    // MARK: - İstatistikler

    private var statsStrip: some View {
        HStack(spacing: 0) {
            statCell(title: "Bu hafta", value: Fmt.hours(weekMinutes), dot: Theme.blue)
            Divider().frame(height: 36)
            statCell(title: "Bu ay tahsilat", value: Fmt.money(monthCollected), dot: Theme.green)
            Divider().frame(height: 36)
            statCell(title: "Bekleyen", value: Fmt.money(pendingTotal), dot: Theme.red,
                     valueColor: pendingTotal > 0.5 ? Theme.red : Theme.ink)
        }
        .card(12)
    }

    private func statCell(title: String, value: String, dot: Color,
                          valueColor: Color = Theme.ink) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.headline.weight(.bold))
                .fontDesign(.serif)
                .foregroundStyle(valueColor)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            HStack(spacing: 4) {
                Circle().fill(dot).frame(width: 6, height: 6)
                Text(title)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Theme.inkSoft)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Bugünün dersleri

    private var todaySection: some View {
        VStack(spacing: 10) {
            HStack {
                SectionHeader(title: "Bugünün Dersleri", systemImage: "sun.max.fill")
                Button {
                    showQuickLesson = true
                } label: {
                    Label("Ders Ekle", systemImage: "plus")
                        .font(.caption.weight(.bold))
                        .lineLimit(1)
                        .fixedSize()
                        .foregroundStyle(Theme.accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(Theme.accent.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }
            if todayLessons.isEmpty {
                EmptyStateView(icon: "moon.zzz", title: "Bugün ders yok",
                               message: "Kendine bir çay ısmarla ya da hemen bir ders planla.",
                               actionTitle: "Ders Ekle", action: { showQuickLesson = true })
            } else {
                ForEach(todayLessons) { lesson in
                    dashboardLessonRow(lesson)
                }
            }
        }
    }

    private func dashboardLessonRow(_ lesson: Lesson) -> some View {
        HStack(spacing: 10) {
            LessonRow(lesson: lesson, hidesPlannedStatus: true)
            if lesson.status == .planned {
                Button {
                    lesson.status = .completed
                    try? context.save()
                } label: {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(Theme.green)
                }
                .buttonStyle(.plain)
            }
        }
        .contextMenu { statusMenu(for: lesson) }
    }

    @ViewBuilder
    private func statusMenu(for lesson: Lesson) -> some View {
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
            newLessonForStudentFrom = lesson
        } label: {
            Label("Aynı Öğrenciye Yeni Ders", systemImage: "person.crop.circle.badge.plus")
        }
        Button {
            LessonActions.copyFourWeeks(lesson, in: context)
        } label: {
            Label("4 Hafta Tekrar Oluştur", systemImage: "repeat")
        }
    }

    private func cancelTargetLesson(_ reason: CancellationReason) {
        cancellationTarget?.status = .cancelled
        cancellationTarget?.cancellationReason = reason
        try? context.save()
        cancellationTarget = nil
    }

    // MARK: - Yaklaşan dersler

    private var upcomingSection: some View {
        VStack(spacing: 10) {
            SectionHeader(title: "Yaklaşan Dersler", systemImage: "calendar.badge.clock")
            if upcomingDayGroups.isEmpty {
                EmptyStateView(icon: "calendar", title: "Yarından itibaren planlı ders yok",
                               actionTitle: "Ders Planla", action: { showQuickLesson = true })
            } else {
                ForEach(upcomingDayGroups) { group in
                    upcomingDayCard(group)
                }

                NavigationLink {
                    ScheduleView(initialDate: upcomingDayGroups.first?.date ?? Date())
                } label: {
                    HStack {
                        Image(systemName: "calendar")
                            .font(.subheadline.weight(.semibold))
                        Text("Tüm programı gör")
                            .font(.subheadline.weight(.bold))
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.bold))
                    }
                    .foregroundStyle(Theme.accent)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.accent.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func upcomingDayCard(_ group: UpcomingDayGroup) -> some View {
        NavigationLink {
            ScheduleView(initialDate: group.date)
        } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    VStack(spacing: 2) {
                        Text(upcomingDayLabel(group.date))
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Theme.accent)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Text("\(Calendar.tr.component(.day, from: group.date))")
                            .font(.title3.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(Theme.ink)
                    }
                    .frame(width: 56, height: 54)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.accent.opacity(0.12)))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(upcomingFullDayTitle(group.date))
                            .font(.subheadline.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(Theme.ink)
                        Text("\(group.lessons.count) ders • \(Fmt.hours(group.minutes)) • \(Fmt.money(group.expected))")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.inkSoft)
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Theme.inkSoft.opacity(0.6))
                }

                VStack(spacing: 7) {
                    ForEach(Array(group.lessons.enumerated()), id: \.element.persistentModelID) { _, lesson in
                        HStack(spacing: 8) {
                            Text(Fmt.time.string(from: lesson.date))
                                .font(.caption.weight(.bold))
                                .fontDesign(.serif)
                                .foregroundStyle(Theme.ink)
                                .frame(width: 42, alignment: .leading)
                            Circle()
                                .fill(lesson.student?.color ?? Theme.inkSoft)
                                .frame(width: 7, height: 7)
                            Text(lesson.student?.name ?? "Öğrenci")
                                .font(.caption)
                                .foregroundStyle(Theme.ink)
                                .lineLimit(1)
                            Spacer()
                            if let subject = lesson.student?.subject, !subject.isEmpty {
                                Text(subject)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(Theme.inkSoft)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
                .padding(.leading, 2)
            }
            .card(14)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Ödeme bekleyenler

    @ViewBuilder
    private var debtorsSection: some View {
        if !debtors.isEmpty {
            VStack(spacing: 10) {
                SectionHeader(title: "Ödeme Bekleyenler", systemImage: "turkishlirasign")
                ForEach(debtors) { student in
                    HStack(spacing: 12) {
                        StudentAvatar(student: student, size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(student.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.ink)
                            Text("\(student.completedLessons.count) işlenen ders • \(Fmt.money(student.totalPaid)) ödendi")
                                .font(.caption)
                                .foregroundStyle(Theme.inkSoft)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 4) {
                            Text(Fmt.money(student.balance))
                                .monospacedDigit()
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(Theme.red)
                            Button("Ödeme Al") {
                                payingStudent = student
                            }
                            .font(.caption.weight(.bold))
                            .buttonStyle(.bordered)
                            .buttonBorderShape(.capsule)
                            .controlSize(.mini)
                            .tint(Theme.green)
                        }
                    }
                    .card(12)
                }
            }
        }
    }

    // MARK: - Aktif ödevler

    private var homeworkSection: some View {
        VStack(spacing: 10) {
            SectionHeader(title: "Aktif Ödevler", systemImage: "book.fill")
            if activeHomeworks.isEmpty {
                EmptyStateView(icon: "checkmark.seal", title: "Bekleyen ödev yok")
            } else {
                ForEach(activeHomeworks.prefix(4)) { hw in
                    HStack(spacing: 12) {
                        Circle()
                            .fill(hw.student?.color ?? Theme.inkSoft)
                            .frame(width: 8, height: 8)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(hw.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.ink)
                                .lineLimit(1)
                            Text(hw.student?.name ?? "—")
                                .font(.caption)
                                .foregroundStyle(Theme.inkSoft)
                        }
                        Spacer()
                        Text(hw.isLate ? "Gecikti!" : "Son: " + Fmt.dayMonthShort.string(from: hw.dueDate))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(hw.isLate ? Theme.red : Theme.inkSoft)
                    }
                    .card(12)
                }
            }
        }
    }

    // MARK: - Hesaplamalar

    private var todayLessons: [Lesson] {
        lessons.filter { $0.date.isToday }
    }

    /// Bugünün dersleri hemen üstte listelendiği için yaklaşan kart yarından
    /// itibaren ilk ders gününü gösterir; yoksa aynı dersler iki kez görünüyordu.
    private var upcomingDayGroups: [UpcomingDayGroup] {
        let tomorrow = Date().startOfDay.adding(days: 1)
        let futureLessons = lessons
            .filter { $0.status == .planned && $0.date >= tomorrow }
            .sorted { $0.date < $1.date }
        guard let nearestDay = futureLessons.first?.date.startOfDay else { return [] }
        let nextDay = nearestDay.adding(days: 1)
        let sameDayLessons = futureLessons
            .filter { $0.date >= nearestDay && $0.date < nextDay }
            .sorted { $0.date < $1.date }
        return [UpcomingDayGroup(date: nearestDay, lessons: sameDayLessons)]
    }

    private func upcomingDayLabel(_ date: Date) -> String {
        if date.isToday { return "Bugün" }
        if date.isSameDay(as: Date().adding(days: 1)) { return "Yarın" }
        return Fmt.weekdayShort.string(from: date)
    }

    private func upcomingFullDayTitle(_ date: Date) -> String {
        "\(Fmt.dayMonth.string(from: date)) \(Fmt.weekday.string(from: date))"
    }

    private var weekMinutes: Int {
        let start = Date().startOfWeek
        let end = start.adding(days: 7)
        return lessons
            .filter { $0.date >= start && $0.date < end && $0.status != .cancelled }
            .reduce(0) { $0 + $1.duration }
    }

    private var monthCollected: Double {
        let start = Date().startOfMonth
        return payments
            .filter { $0.date >= start }
            .reduce(0) { $0 + $1.amount }
    }

    private var pendingTotal: Double {
        students.reduce(0) { $0 + max($1.balance, 0) }
    }

    private var debtors: [Student] {
        students.filter { $0.balance > 0.5 }.sorted { $0.balance > $1.balance }
    }

    private var activeHomeworks: [Homework] {
        homeworks.filter { !$0.isDone }
    }

}
