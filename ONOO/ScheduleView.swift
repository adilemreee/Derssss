//
//  ScheduleView.swift
//  One — Ders Defteri
//
//  Haftalık ders programı ve ders ekleme/düzenleme formu.
//

import SwiftUI
import SwiftData

struct ScheduleView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Lesson.date) private var lessons: [Lesson]

    @State private var selectedDate: Date
    @State private var showForm = false
    @State private var editingLesson: Lesson?
    @State private var showMonthCalendar = false
    @State private var showRecurring = false

    init(initialDate: Date = Date()) {
        _selectedDate = State(initialValue: initialDate)
    }

    private var weekStart: Date { selectedDate.startOfWeek }
    private var weekDays: [Date] { (0..<7).map { weekStart.adding(days: $0) } }

    private var dayLessons: [Lesson] {
        lessons.filter { $0.date.isSameDay(as: selectedDate) }
    }

    private var weekLessons: [Lesson] {
        let end = weekStart.adding(days: 7)
        return lessons.filter { $0.date >= weekStart && $0.date < end && $0.status != .cancelled }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                weekHeader
                dayStrip
                ScrollView {
                    VStack(spacing: 12) {
                        dayTitle
                        if dayLessons.isEmpty {
                            EmptyStateView(icon: "moon.zzz",
                                           title: "Bu gün ders yok",
                                           message: "Sağ üstteki + ile ders ekleyebilirsin.")
                        } else {
                            ForEach(dayLessons) { lesson in
                                ScheduleLessonCard(lesson: lesson) {
                                    editingLesson = lesson
                                }
                            }
                        }
                        weekSummary
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 24)
                }
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Program")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Bugün") {
                        withAnimation { selectedDate = Date() }
                    }
                    .disabled(selectedDate.isToday)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showRecurring = true
                    } label: {
                        Image(systemName: "repeat")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showForm = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showForm) {
                LessonFormView(defaultDate: selectedDate)
            }
            .sheet(item: $editingLesson) { lesson in
                LessonFormView(lesson: lesson)
            }
            .sheet(isPresented: $showRecurring) {
                RecurringLessonsView()
            }

            .sheet(isPresented: $showMonthCalendar) {
                MonthCalendarView(selectedDate: $selectedDate, lessons: lessons)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            }
        }
    }

    // MARK: - Hafta gezinme

    private var weekHeader: some View {
        HStack {
            Button {
                withAnimation { selectedDate = selectedDate.adding(days: -7) }
            } label: {
                Image(systemName: "chevron.left")
                    .font(.subheadline.weight(.bold))
            }
            Spacer()
            HStack(spacing: 8) {
                Text("\(Fmt.dayMonthShort.string(from: weekStart)) – \(Fmt.dayMonthShort.string(from: weekStart.adding(days: 6)))")
                    .font(.headline)
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
                Button {
                    showMonthCalendar = true
                } label: {
                    Image(systemName: "calendar")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.board)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(Theme.board.opacity(0.1)))
                }
                .buttonStyle(.plain)
            }
            Spacer()
            Button {
                withAnimation { selectedDate = selectedDate.adding(days: 7) }
            } label: {
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.bold))
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
    }

    private var dayStrip: some View {
        HStack(spacing: 6) {
            ForEach(weekDays, id: \.self) { day in
                dayCell(day)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
    }

    private func dayCell(_ day: Date) -> some View {
        let isSelected = day.isSameDay(as: selectedDate)
        let dayLs = lessons.filter { $0.date.isSameDay(as: day) && $0.status != .cancelled }
        return Button {
            withAnimation(.snappy) { selectedDate = day }
        } label: {
            VStack(spacing: 4) {
                Text(Fmt.weekdayShort.string(from: day))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(isSelected ? .white.opacity(0.8) : Theme.inkSoft)
                Text("\(Calendar.tr.component(.day, from: day))")
                    .font(.headline.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(isSelected ? .white : Theme.ink)
                HStack(spacing: 3) {
                    ForEach(Array(dayLs.prefix(3).enumerated()), id: \.offset) { _, lesson in
                        Circle()
                            .fill(isSelected ? .white : (lesson.student?.color ?? Theme.inkSoft))
                            .frame(width: 4, height: 4)
                    }
                }
                .frame(height: 5)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? Theme.board : Theme.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(day.isToday && !isSelected ? Theme.amber : Theme.line,
                            lineWidth: day.isToday && !isSelected ? 1.8 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var dayTitle: some View {
        HStack {
            Text("\(Fmt.dayMonth.string(from: selectedDate)) \(Fmt.weekday.string(from: selectedDate))")
                .font(.subheadline.weight(.bold))
                .fontDesign(.serif)
                .foregroundStyle(Theme.ink)
            if selectedDate.isToday {
                Chip(text: "Bugün", tint: Theme.amber, filled: true)
            }
            Spacer()
            if !dayLessons.isEmpty {
                Text("\(dayLessons.count) ders")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Theme.inkSoft)
            }
        }
    }

    @ViewBuilder
    private var weekSummary: some View {
        if !weekLessons.isEmpty {
            let minutes = weekLessons.reduce(0) { $0 + $1.duration }
            let expected = weekLessons.reduce(0.0) { $0 + $1.fee }
            Chalkboard {
                HStack(spacing: 16) {
                    Image(systemName: "sum")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.85))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Bu haftanın özeti")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.65))
                        Text("\(weekLessons.count) ders • \(Fmt.hours(minutes)) • \(Fmt.money(expected))")
                            .font(.subheadline.weight(.bold))
                            .fontDesign(.serif)
                            .foregroundStyle(.white)
                    }
                    Spacer()
                }
            }
            .padding(.top, 6)
        }
    }
}

// MARK: - Program ders kartı

struct ScheduleLessonCard: View {
    @Environment(\.modelContext) private var context
    let lesson: Lesson
    var onEdit: () -> Void
    @State private var showSameStudentForm = false
    @State private var showCancellationReasons = false

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 2) {
                Text(Fmt.time.string(from: lesson.date))
                    .font(.headline.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
                Text(Fmt.time.string(from: lesson.endDate))
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }
            .frame(width: 56)

            Capsule()
                .fill(lesson.student?.color ?? Theme.inkSoft)
                .frame(width: 4, height: 46)

            VStack(alignment: .leading, spacing: 4) {
                Text(lesson.student?.name ?? "—")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                HStack(spacing: 6) {
                    if lesson.sourceTemplate != nil {
                        Image(systemName: "repeat")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Theme.inkSoft)
                    }
                    if let subject = lesson.student?.subject, !subject.isEmpty {
                        Chip(text: subject, tint: lesson.student?.color ?? Theme.inkSoft)
                    }
                    if lesson.status == .cancelled && lesson.cancellationReason != .none {
                        Text(lesson.cancellationReason.shortTitle)
                            .font(.caption)
                            .foregroundStyle(Theme.red)
                            .lineLimit(1)
                    }
                    if !lesson.topic.isEmpty {
                        Text(lesson.topic)
                            .font(.caption)
                            .foregroundStyle(Theme.inkSoft)
                            .lineLimit(1)
                    }
                }
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 5) {
                Text(Fmt.money(lesson.fee))
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                StatusChip(status: lesson.status)
            }

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
        .card(14)
        .contentShape(Rectangle())
        .onTapGesture { onEdit() }
        .contextMenu {
            ForEach(LessonStatus.allCases, id: \.self) { status in
                if status != lesson.status {
                    Button {
                        if status == .cancelled {
                            showCancellationReasons = true
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
            Button { onEdit() } label: { Label("Düzenle", systemImage: "pencil") }
            Divider()
            Button {
                LessonActions.copyNextWeek(lesson, in: context)
            } label: {
                Label("Haftaya Aynı Ders", systemImage: "calendar.badge.plus")
            }
            Button {
                showSameStudentForm = true
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
        .sheet(isPresented: $showSameStudentForm) {
            LessonFormView(defaultStudent: lesson.student, defaultDate: lesson.date.adding(days: 7))
        }
        .confirmationDialog("İptal sebebi seç", isPresented: $showCancellationReasons, titleVisibility: .visible) {
            Button(CancellationReason.student.title) { cancelLesson(.student) }
            Button(CancellationReason.teacher.title) { cancelLesson(.teacher) }
            Button(CancellationReason.makeup.title) { cancelLesson(.makeup) }
            Button("Vazgeç", role: .cancel) {}
        }
    }

    private func cancelLesson(_ reason: CancellationReason) {
        lesson.status = .cancelled
        lesson.cancellationReason = reason
        try? context.save()
    }
}

// MARK: - Aylık takvim görünümü

struct MonthCalendarView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedDate: Date
    let lessons: [Lesson]

    @State private var visibleMonth: Date
    @State private var showAddForm = false

    init(selectedDate: Binding<Date>, lessons: [Lesson]) {
        _selectedDate = selectedDate
        self.lessons = lessons
        _visibleMonth = State(initialValue: selectedDate.wrappedValue.startOfMonth)
    }

    /// İptal dahil ayın tüm dersleri
    private var monthLessons: [Lesson] {
        let next = visibleMonth.adding(months: 1)
        return lessons.filter { $0.date >= visibleMonth && $0.date < next }
    }

    private var monthDays: [Date] {
        let weekday = Calendar.tr.component(.weekday, from: visibleMonth)
        let leading = (weekday - Calendar.tr.firstWeekday + 7) % 7
        let gridStart = visibleMonth.adding(days: -leading)
        return (0..<42).map { gridStart.adding(days: $0) }
    }

    private var selectedDayLessons: [Lesson] {
        lessons
            .filter { $0.date.isSameDay(as: selectedDate) }
            .sorted { $0.date < $1.date }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    monthHeader
                    statusBar
                    weekdayHeader
                    calendarGrid
                    selectedDaySection
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Aylık Takvim")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Güne Git") { dismiss() }
                }
            }
            .sheet(isPresented: $showAddForm) {
                LessonFormView(defaultDate: selectedDate)
            }
        }
    }

    // MARK: - Durum dağılımı

    @ViewBuilder
    private var statusBar: some View {
        let all = monthLessons
        if !all.isEmpty {
            let completed = all.filter { $0.status == .completed }.count
            let planned = all.filter { $0.status == .planned }.count
            let cancelled = all.filter { $0.status == .cancelled }.count
            let total = CGFloat(all.count)
            VStack(alignment: .leading, spacing: 6) {
                GeometryReader { geo in
                    HStack(spacing: 0) {
                        Rectangle()
                            .fill(Theme.green)
                            .frame(width: geo.size.width * CGFloat(completed) / total)
                        Rectangle()
                            .fill(Theme.blue)
                            .frame(width: geo.size.width * CGFloat(planned) / total)
                        Rectangle()
                            .fill(Theme.red)
                            .frame(width: geo.size.width * CGFloat(cancelled) / total)
                    }
                    .clipShape(Capsule())
                }
                .frame(height: 6)
                HStack(spacing: 12) {
                    if completed > 0 {
                        statusLegend(color: Theme.green, text: "\(completed) işlendi")
                    }
                    if planned > 0 {
                        statusLegend(color: Theme.blue, text: "\(planned) planlı")
                    }
                    if cancelled > 0 {
                        statusLegend(color: Theme.red, text: "\(cancelled) iptal")
                    }
                    Spacer()
                }
            }
            .padding(.top, -8)
        }
    }

    private func statusLegend(color: Color, text: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2)
                .fill(color)
                .frame(width: 8, height: 8)
            Text(text)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Theme.inkSoft)
        }
    }

    private var monthHeader: some View {
        Chalkboard {
            HStack(spacing: 14) {
                Button {
                    withAnimation(.snappy) { visibleMonth = visibleMonth.adding(months: -1) }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(.white.opacity(0.14)))
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 3) {
                    Text(Fmt.monthYear.string(from: visibleMonth).capitalized(with: Locale(identifier: "tr_TR")))
                        .font(.title3.weight(.bold))
                        .fontDesign(.serif)
                        .foregroundStyle(.white)
                    Text(monthSummary)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.75))
                }

                Spacer()

                Button {
                    withAnimation(.snappy) { visibleMonth = visibleMonth.adding(months: 1) }
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(.white.opacity(0.14)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 8)
    }

    private var weekdayHeader: some View {
        HStack(spacing: 6) {
            ForEach(["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"], id: \.self) { day in
                Text(day)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(Theme.inkSoft)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var calendarGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 7) {
            ForEach(monthDays, id: \.self) { day in
                monthDayCell(day)
            }
        }
    }

    private func monthDayCell(_ day: Date) -> some View {
        let isSelected = day.isSameDay(as: selectedDate)
        let isCurrentMonth = day.startOfMonth == visibleMonth
        let count = lessons.filter { $0.date.isSameDay(as: day) && $0.status != .cancelled }.count

        // Yoğunluğa göre ısı haritası tonu
        let fill: Color = {
            if isSelected { return Theme.board }
            guard isCurrentMonth else { return Theme.card.opacity(0.48) }
            switch count {
            case 0: return Theme.card
            case 1: return Theme.board.opacity(0.10)
            case 2: return Theme.board.opacity(0.22)
            default: return Theme.board.opacity(0.36)
            }
        }()

        let numberColor: Color = {
            if isSelected { return .white }
            if !isCurrentMonth { return Theme.inkSoft.opacity(0.45) }
            return Theme.ink
        }()

        return Button {
            withAnimation(.snappy) { selectedDate = day }
        } label: {
            VStack(spacing: 3) {
                Text("\(Calendar.tr.component(.day, from: day))")
                    .font(.subheadline.weight(.bold))
                    .fontDesign(.serif)
                    .foregroundStyle(numberColor)
                if isCurrentMonth && count > 0 {
                    Text("\(count) ders")
                        .font(.system(size: 8, weight: .semibold))
                        .foregroundStyle(isSelected ? .white.opacity(0.85) : Theme.ink.opacity(0.75))
                        .frame(height: 10)
                } else {
                    Color.clear.frame(height: 10)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(fill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(day.isToday && !isSelected ? Theme.amber : Theme.line,
                            lineWidth: day.isToday && !isSelected ? 1.6 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var selectedDaySection: some View {
        VStack(spacing: 10) {
            HStack {
                SectionHeader(title: "\(Fmt.dayMonth.string(from: selectedDate))", systemImage: "calendar")
                if selectedDate.isToday {
                    Chip(text: "Bugün", tint: Theme.amber, filled: true)
                }
                Button {
                    showAddForm = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                            .font(.caption2.weight(.bold))
                        Text("Ders ekle")
                            .font(.caption.weight(.semibold))
                    }
                    .foregroundStyle(Theme.board)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(Theme.board.opacity(0.1)))
                }
                .buttonStyle(.plain)
            }
            if selectedDayLessons.isEmpty {
                EmptyStateView(icon: "moon.zzz", title: "Bu gün ders yok")
            } else {
                ForEach(selectedDayLessons) { lesson in
                    LessonRow(lesson: lesson)
                }
            }
        }
    }

    private var monthSummary: String {
        let active = monthLessons.filter { $0.status != .cancelled }
        let minutes = active.reduce(0) { $0 + $1.duration }
        let expected = active.reduce(0.0) { $0 + $1.fee }
        guard !active.isEmpty else { return "Bu ay ders yok" }
        return "\(active.count) ders • \(Fmt.hours(minutes)) • \(Fmt.money(expected))"
    }
}

// MARK: - Ders formu (ekle / düzenle)

struct LessonFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \Student.name) private var students: [Student]
    @Query(sort: \Lesson.date) private var allLessons: [Lesson]

    var lesson: Lesson? = nil
    var defaultStudent: Student? = nil
    var defaultDate: Date = Date()

    @State private var studentID: PersistentIdentifier?
    @State private var day: Date
    @State private var time: Date
    @State private var duration: Int
    @State private var status: LessonStatus
    @State private var topic: String
    @State private var note: String
    @State private var useCustomFee: Bool
    @State private var customFee: Double
    @State private var cancellationReason: CancellationReason
    @State private var repeatWeeks: Int = 0
    @State private var showConflictAlert = false

    init(lesson: Lesson? = nil, defaultStudent: Student? = nil, defaultDate: Date = Date()) {
        self.lesson = lesson
        self.defaultStudent = defaultStudent
        self.defaultDate = defaultDate

        let initialStudent = lesson?.student ?? defaultStudent
        _studentID = State(initialValue: initialStudent?.persistentModelID)
        _day = State(initialValue: lesson?.date ?? defaultDate)

        if let lesson {
            _time = State(initialValue: lesson.date)
            _status = State(initialValue: lesson.status)
        } else {
            var comps = Calendar.tr.dateComponents([.year, .month, .day], from: defaultDate)
            comps.hour = 17
            comps.minute = 0
            _time = State(initialValue: Calendar.tr.date(from: comps) ?? defaultDate)
            _status = State(initialValue: .planned)
        }
        _duration = State(initialValue: lesson?.duration ?? 60)
        _topic = State(initialValue: lesson?.topic ?? "")
        _note = State(initialValue: lesson?.note ?? "")
        _useCustomFee = State(initialValue: lesson?.usesCustomFee ?? false)
        _customFee = State(initialValue: lesson?.feeOverride ?? lesson?.fee ?? 0)
        let initialReason = lesson?.cancellationReason ?? .student
        _cancellationReason = State(initialValue: initialReason == .none ? .student : initialReason)
    }

    private var selectedStudent: Student? {
        students.first { $0.persistentModelID == studentID }
    }

    private var durationOptions: [Int] {
        Array(Set([45, 60, 90, 120, 150, 180] + [duration])).sorted()
    }

    private var defaultFee: Double {
        Lesson.standardFee(for: selectedStudent, duration: duration)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Ders") {
                    Picker("Öğrenci", selection: $studentID) {
                        Text("Seçiniz").tag(nil as PersistentIdentifier?)
                        ForEach(students.filter { !$0.isArchived }) { s in
                            Text("\(s.name) — \(s.subject)").tag(Optional(s.persistentModelID))
                        }
                    }
                    DatePicker("Tarih", selection: $day, displayedComponents: .date)
                    DatePicker("Saat", selection: $time, displayedComponents: .hourAndMinute)
                    Picker("Süre", selection: $duration) {
                        ForEach(durationOptions, id: \.self) { d in
                            Text("\(d) dk").tag(d)
                        }
                    }
                    if !conflictingLessons.isEmpty {
                        conflictNotice
                    }
                    Picker("Durum", selection: $status) {
                        ForEach(LessonStatus.allCases, id: \.self) { s in
                            Text(s.title).tag(s)
                        }
                    }
                    .pickerStyle(.segmented)
                    if status == .cancelled {
                        Picker("İptal sebebi", selection: $cancellationReason) {
                            ForEach(CancellationReason.allCases.filter { $0 != .none }, id: \.self) { reason in
                                Text(reason.title).tag(reason)
                            }
                        }
                    }
                }

                Section("İçerik") {
                    TextField("Konu (ör. Türev)", text: $topic)
                    TextField("Not", text: $note, axis: .vertical)
                        .lineLimit(2...4)
                }

                Section("Ücret") {
                    LabeledContent(useCustomFee ? "Standart ücret" : "Kaydedilecek ücret", value: Fmt.money(defaultFee))
                    Toggle("Derse özel ücret", isOn: $useCustomFee)
                    if useCustomFee {
                        HStack {
                            Text("Özel ücret")
                            Spacer()
                            TextField("0", value: $customFee, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 110)
                            Text("₺")
                                .foregroundStyle(Theme.inkSoft)
                        }
                    }
                }

                if lesson == nil {
                    Section("Tekrar") {
                        Stepper(repeatWeeks == 0 ? "Tekrar yok"
                                                 : "Sonraki \(repeatWeeks) hafta aynı gün/saat",
                                value: $repeatWeeks, in: 0...12)
                    }
                }

                if let lesson {
                    Section {
                        Button("Dersi Sil", role: .destructive) {
                            context.delete(lesson)
                            try? context.save()
                            dismiss()
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle(lesson == nil ? "Yeni Ders" : "Dersi Düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { attemptSave() }
                        .disabled(studentID == nil)
                }
            }
            .alert("Ders çakışması var", isPresented: $showConflictAlert) {
                Button("Yine de Kaydet", role: .destructive) { save() }
                Button("Düzenle", role: .cancel) {}
            } message: {
                Text(conflictAlertText)
            }
        }
    }

    private var conflictNotice: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("Bu saat dolu görünüyor", systemImage: "exclamationmark.triangle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.red)
            Text(conflictAlertText)
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
        }
        .padding(.vertical, 4)
    }

    private var proposedSlots: [(start: Date, end: Date)] {
        let cal = Calendar.tr
        var comps = cal.dateComponents([.year, .month, .day], from: day)
        let t = cal.dateComponents([.hour, .minute], from: time)
        comps.hour = t.hour
        comps.minute = t.minute
        let start = cal.date(from: comps) ?? day
        let count = lesson == nil ? repeatWeeks : 0
        return (0...count).map { offset in
            let slotStart = start.adding(days: 7 * offset)
            return (slotStart, slotStart.addingTimeInterval(Double(duration) * 60))
        }
    }

    private var conflictingLessons: [Lesson] {
        guard status != .cancelled else { return [] }
        return allLessons.filter { candidate in
            guard candidate.status != .cancelled else { return false }
            if let lesson, candidate.persistentModelID == lesson.persistentModelID { return false }
            return proposedSlots.contains { slot in
                slot.start < candidate.endDate && candidate.date < slot.end
            }
        }
    }

    private var conflictAlertText: String {
        conflictingLessons.prefix(3).map { lesson in
            let name = lesson.student?.name ?? "Öğrenci"
            return "\(Fmt.dayMonthShort.string(from: lesson.date)) \(Fmt.time.string(from: lesson.date)) - \(name)"
        }
        .joined(separator: "\n")
    }

    private func attemptSave() {
        if conflictingLessons.isEmpty {
            save()
        } else {
            showConflictAlert = true
        }
    }

    private func save() {
        guard let student = selectedStudent else { return }
        let cal = Calendar.tr
        var comps = cal.dateComponents([.year, .month, .day], from: day)
        let t = cal.dateComponents([.hour, .minute], from: time)
        comps.hour = t.hour
        comps.minute = t.minute
        let start = cal.date(from: comps) ?? day
        let fee = useCustomFee ? customFee : Lesson.standardFee(for: student, duration: duration)

        if let lesson {
            lesson.student = student
            lesson.date = start
            lesson.duration = duration
            lesson.status = status
            lesson.cancellationReason = status == .cancelled ? cancellationReason : .none
            lesson.topic = topic
            lesson.note = note
            lesson.feeOverride = fee
            lesson.usesCustomFee = useCustomFee
        } else {
            for i in 0...repeatWeeks {
                let lessonDate = start.adding(days: 7 * i)
                let new = Lesson(date: lessonDate,
                                 duration: duration,
                                 status: i == 0 ? status : .planned,
                                 cancellationReason: i == 0 && status == .cancelled ? cancellationReason : .none,
                                 topic: i == 0 ? topic : "",
                                 note: i == 0 ? note : "",
                                 feeOverride: fee,
                                 usesCustomFee: useCustomFee)
                context.insert(new)
                new.student = student
            }
        }
        try? context.save()
        dismiss()
    }
}
