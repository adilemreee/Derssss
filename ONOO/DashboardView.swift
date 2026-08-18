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
    @State private var showResetConfirm = false
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
                        headerBoard
                        quickLessonButton
                        statsGrid
                        todaySection
                        upcomingSection
                        debtorsSection
                        homeworkSection
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Ders Defteri")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button {
                            showSettings = true
                        } label: {
                            Label("Ayarlar", systemImage: "gearshape")
                        }
                        Divider()
                        Button(role: .destructive) {
                            showResetConfirm = true
                        } label: {
                            Label("Tüm Verileri Sil", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .confirmationDialog("Tüm öğrenciler, dersler, ödemeler ve ödevler silinecek. Emin misin?",
                                isPresented: $showResetConfirm, titleVisibility: .visible) {
                Button("Hepsini Sil", role: .destructive) { deleteAllData() }
                Button("Vazgeç", role: .cancel) {}
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
                    Text("Hoş geldin \(teacherDisplayName) 👋")
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
                featureRow(icon: "arrow.triangle.2.circlepath", tint: Theme.board,
                           title: "Hesabında yedekli",
                           text: "Yeni telefonda kayıtların geri gelir")
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

    private func featureRow(icon: String, tint: Color, title: String, text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 36, height: 36)
                .background(Circle().fill(tint.opacity(0.12)))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
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
        case 5..<12: return "Günaydın \(teacherDisplayName) 👋"
        case 12..<18: return "İyi dersler \(teacherDisplayName) 👋"
        default: return "İyi akşamlar \(teacherDisplayName) 👋"
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

    private var statsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible())], spacing: 12) {
            StatCard(icon: "sun.max.fill", title: "Bugünkü Ders",
                     value: "\(todayLessons.filter { $0.status != .cancelled }.count)",
                     tint: Theme.amber)
            StatCard(icon: "clock.fill", title: "Bu Hafta",
                     value: Fmt.hours(weekMinutes),
                     tint: Theme.blue)
            StatCard(icon: "banknote.fill", title: "Bu Ay Kazanç",
                     value: Fmt.money(monthCollected),
                     tint: Theme.green)
            StatCard(icon: "exclamationmark.circle.fill", title: "Bekleyen Alacak",
                     value: Fmt.money(pendingTotal),
                     tint: Theme.red)
        }
    }

    // MARK: - Hızlı ders ekle

    private var quickLessonButton: some View {
        Button {
            showQuickLesson = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "bolt.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(Theme.amber))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Hızlı Ders Ekle")
                        .font(.headline.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(Theme.ink)
                    Text("Öğrenci, süre ve zamanı seçip hemen planla")
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                }
                Spacer()
                Image(systemName: "plus.circle.fill")
                    .font(.title3)
                    .foregroundStyle(Theme.board)
            }
            .card(14)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Bugünün dersleri

    private var todaySection: some View {
        VStack(spacing: 10) {
            SectionHeader(title: "Bugünün Dersleri", systemImage: "sun.max.fill")
            if todayLessons.isEmpty {
                EmptyStateView(icon: "moon.zzz", title: "Bugün ders yok",
                               message: "Program sekmesinden yeni ders ekleyebilirsin.")
            } else {
                ForEach(todayLessons) { lesson in
                    dashboardLessonRow(lesson)
                }
            }
        }
    }

    private func dashboardLessonRow(_ lesson: Lesson) -> some View {
        HStack(spacing: 10) {
            LessonRow(lesson: lesson)
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
            SectionHeader(title: "En Yakın Planlı Ders", systemImage: "calendar.badge.clock")
            if upcomingDayGroups.isEmpty {
                EmptyStateView(icon: "calendar", title: "Planlanmış ders yok")
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
                    .foregroundStyle(Theme.board)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.board.opacity(0.08)))
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
                            .foregroundStyle(Theme.board)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Text("\(Calendar.tr.component(.day, from: group.date))")
                            .font(.title3.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(Theme.ink)
                    }
                    .frame(width: 56, height: 54)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.board.opacity(0.1)))

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

    private var upcomingDayGroups: [UpcomingDayGroup] {
        let now = Date()
        let futureLessons = lessons
            .filter { $0.status == .planned && $0.date > now }
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

    private func deleteAllData() {
        for student in students { context.delete(student) }
        for lesson in lessons where lesson.student == nil { context.delete(lesson) }
        for hw in homeworks where hw.student == nil { context.delete(hw) }
        try? context.save()
    }
}
