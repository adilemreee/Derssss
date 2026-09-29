//
//  StudentsView.swift
//  One — Ders Defteri
//
//  Öğrenci listesi ve öğrenci ekleme/düzenleme formu.
//

import SwiftUI
import SwiftData

struct StudentsView: View {
    @Environment(\.modelContext) private var context
    @Environment(ProStore.self) private var proStore
    @Query(sort: \Student.name) private var students: [Student]
    @State private var search = ""
    @State private var showForm = false
    @State private var showArchive = false
    @State private var showPaywall = false
    @State private var archiveTarget: Student?

    private var activeCount: Int {
        students.filter { !$0.isArchived }.count
    }

    private var filtered: [Student] {
        let active = students.filter { !$0.isArchived }
        guard !search.isEmpty else { return active }
        return active.filter {
            $0.name.localizedCaseInsensitiveContains(search) ||
            $0.subject.localizedCaseInsensitiveContains(search) ||
            $0.grade.localizedCaseInsensitiveContains(search)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    if !filtered.isEmpty {
                        HStack {
                            Text("\(filtered.count) öğrenci")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.inkSoft)
                            Spacer()
                        }
                        .padding(.top, 4)
                    }
                    ForEach(filtered) { student in
                        NavigationLink {
                            StudentDetailView(student: student)
                        } label: {
                            StudentCard(student: student)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button {
                                archiveTarget = student
                            } label: {
                                Label("Arşivle", systemImage: "archivebox")
                            }
                        }
                    }
                    if filtered.isEmpty {
                        EmptyStateView(icon: "graduationcap",
                                       title: search.isEmpty ? "Henüz öğrenci yok" : "Sonuç bulunamadı",
                                       actionTitle: search.isEmpty ? "Öğrenci Ekle" : nil,
                                       action: {
                                           if proStore.canAddStudent(activeCount: activeCount) {
                                               showForm = true
                                           } else {
                                               showPaywall = true
                                           }
                                       })
                            .padding(.top, 40)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
                .readableWidth()
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Öğrenciler")
            .searchable(text: $search, prompt: "Öğrenci, ders veya sınıf ara")
            .toolbar {
                if students.contains(where: \.isArchived) {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            showArchive = true
                        } label: {
                            Image(systemName: "archivebox")
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if proStore.canAddStudent(activeCount: activeCount) {
                            showForm = true
                        } else {
                            showPaywall = true
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showForm) {
                StudentFormView()
            }
            .sheet(isPresented: $showArchive) {
                ArchivedStudentsView()
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
            }
            .studentArchiveDialog($archiveTarget, context: context)
        }
    }
}

// MARK: - Arşivlenmiş öğrenciler

struct ArchivedStudentsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(ProStore.self) private var proStore
    @Query(sort: \Student.name) private var students: [Student]
    @State private var showPaywall = false
    @State private var deleteTarget: Student?

    private var archived: [Student] {
        students.filter(\.isArchived)
    }

    /// Aktife almak da öğrenci limitine tabidir
    private func unarchive(_ student: Student) {
        let activeCount = students.filter { !$0.isArchived }.count
        if proStore.canAddStudent(activeCount: activeCount) {
            StudentActions.unarchive(student, in: context)
        } else {
            showPaywall = true
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    if archived.isEmpty {
                        EmptyStateView(icon: "archivebox", title: "Arşiv boş",
                                       message: "Mezun olan veya ara veren öğrenciler burada görünür.")
                            .padding(.top, 40)
                    } else {
                        ForEach(archived) { student in
                            HStack(spacing: 12) {
                                NavigationLink {
                                    StudentDetailView(student: student)
                                } label: {
                                    StudentCard(student: student)
                                }
                                .buttonStyle(.plain)

                                Button {
                                    unarchive(student)
                                } label: {
                                    Image(systemName: "arrow.uturn.backward.circle.fill")
                                        .font(.title2)
                                        .foregroundStyle(Theme.green)
                                }
                                .buttonStyle(.plain)
                            }
                            .contextMenu {
                                Button {
                                    unarchive(student)
                                } label: {
                                    Label("Aktife Al", systemImage: "arrow.uturn.backward")
                                }
                                Button(role: .destructive) {
                                    deleteTarget = student
                                } label: {
                                    Label("Sil", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Arşiv")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tamam") { dismiss() }
                }
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
            }
            .confirmationDialog("Öğrenci silinsin mi?",
                                isPresented: Binding(get: { deleteTarget != nil },
                                                     set: { if !$0 { deleteTarget = nil } }),
                                titleVisibility: .visible,
                                presenting: deleteTarget) { student in
                Button("Sil", role: .destructive) {
                    context.delete(student)
                    try? context.save()
                    deleteTarget = nil
                }
                Button("Vazgeç", role: .cancel) { deleteTarget = nil }
            } message: { student in
                Text("\(student.name) ve tüm ders, ödeme ve ödev kayıtları kalıcı olarak silinir.")
            }
        }
    }
}

// MARK: - Öğrenci kartı

struct StudentCard: View {
    let student: Student

    var body: some View {
        HStack(spacing: 14) {
            StudentAvatar(student: student, size: 50)
            VStack(alignment: .leading, spacing: 5) {
                Text(student.name)
                    .font(.headline)
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
                Text("\(student.subject) • \(student.grade)")
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
                HStack(spacing: 6) {
                    Chip(text: "\(Fmt.money(student.hourlyRate))/sa", tint: Theme.accent)
                    Chip(text: "\(student.completedLessons.count) ders", tint: Theme.blue)
                }
                lessonTimelineText
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 6) {
                BalanceBadge(balance: student.balance,
                             earned: student.totalEarned,
                             paid: student.totalPaid,
                             plannedCount: student.allLessons.filter { $0.status == .planned }.count)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.inkSoft.opacity(0.5))
            }
        }
        .card(14)
    }

    @ViewBuilder
    private var lessonTimelineText: some View {
        if let text = lessonTimeline {
            Text(text)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Theme.inkSoft)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }

    private var lessonTimeline: String? {
        let now = Date()
        let last = student.allLessons
            .filter { $0.status == .completed && $0.date <= now }
            .sorted { $0.date > $1.date }
            .first
        let next = student.allLessons
            .filter { $0.status == .planned && $0.date >= now }
            .sorted { $0.date < $1.date }
            .first

        // Dar kartta ikisi yan yana sığmıyor ve saat "…" ile kesiliyordu.
        // Asıl lazım olan sıradaki ders; yoksa son dersin tarihi gösterilir.
        if let next {
            let day = next.date.isToday ? "Bugün" : (next.date.isSameDay(as: now.adding(days: 1)) ? "Yarın" : Fmt.dayMonthShort.string(from: next.date))
            return "Sıradaki ders: \(day) \(Fmt.time.string(from: next.date))"
        }
        if let last {
            return "Son ders: \(Fmt.dayMonthShort.string(from: last.date))"
        }
        return nil
    }
}

// MARK: - Öğrenci formu (ekle / düzenle)

struct StudentFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query private var allLessons: [Lesson]

    var student: Student? = nil

    @State private var name: String
    @State private var subject: String
    @State private var grade: String
    @State private var phone: String
    @State private var parentName: String
    @State private var parentPhone: String
    @State private var hourlyRate: Double
    @State private var startDate: Date
    @State private var colorIndex: Int
    @State private var notes: String
    // Yalnızca yeni öğrencide: haftalık ders günleri
    @State private var weeklyDays: Set<Int> = []
    @State private var weeklyTime: Date = Calendar.tr.date(bySettingHour: 17, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var weeklyDuration = 60

    private let subjectSuggestions = ["Matematik", "Fizik", "Kimya", "Biyoloji", "İngilizce", "Türkçe", "Edebiyat", "Tarih"]

    init(student: Student? = nil) {
        self.student = student
        _name = State(initialValue: student?.name ?? "")
        _subject = State(initialValue: student?.subject ?? "")
        _grade = State(initialValue: student?.grade ?? "")
        _phone = State(initialValue: TurkishPhoneFormat.format(student?.phone ?? ""))
        _parentName = State(initialValue: student?.parentName ?? "")
        _parentPhone = State(initialValue: TurkishPhoneFormat.format(student?.parentPhone ?? ""))
        _hourlyRate = State(initialValue: student?.hourlyRate ?? 0)
        _startDate = State(initialValue: student?.startDate ?? Date())
        _colorIndex = State(initialValue: student?.colorIndex ?? Int.random(in: 0..<Theme.palette.count))
        _notes = State(initialValue: student?.notes ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Öğrenci") {
                    TextField("Ad Soyad", text: $name)
                    TextField("Ders (ör. Matematik)", text: $subject)
                    if subject.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(subjectSuggestions, id: \.self) { s in
                                    Button(s) { subject = s }
                                        .font(.caption.weight(.semibold))
                                        .buttonStyle(.bordered)
                                        .buttonBorderShape(.capsule)
                                        .controlSize(.mini)
                                        .tint(Theme.accent)
                                }
                            }
                        }
                    }
                    TextField("Sınıf (ör. 11. Sınıf)", text: $grade)
                }

                Section {
                    HStack {
                        Text("Saatlik ücret")
                        Spacer()
                        TextField("0", value: $hourlyRate, format: .number)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 110)
                        Text("₺")
                            .foregroundStyle(Theme.inkSoft)
                    }
                } header: {
                    Text("Ücret")
                } footer: {
                    if student != nil {
                        Text("Ücreti değiştirirsen planlı dersler yeni ücrete geçer; işlenmiş dersler eski ücretle kalır.")
                    }
                }

                if student == nil {
                    Section {
                        WeekdayChipPicker(selection: $weeklyDays)
                        if !weeklyDays.isEmpty {
                            DatePicker("Saat", selection: $weeklyTime, displayedComponents: .hourAndMinute)
                            TimeChipPicker(time: $weeklyTime)
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Süre (dakika)")
                                DurationChipPicker(selection: $weeklyDuration)
                            }
                            .padding(.vertical, 2)
                            if !weeklyClashes.isEmpty {
                                WeeklyClashNotice(lessons: weeklyClashes)
                            }
                        }
                    } header: {
                        Text("Haftalık ders")
                    } footer: {
                        Text(weeklyDays.isEmpty
                             ? "İsteğe bağlı. Gün seçersen dersler her hafta kendiliğinden planlanır."
                             : "Dersler \(RecurringLessons.horizonDays / 7) hafta ilerisi için planlanır; sonra kendiliğinden devam eder.")
                    }
                    .animation(.snappy, value: weeklyDays.isEmpty)
                }

                Section("İletişim") {
                    TextField("05XX XXX XX XX", text: $phone)
                        .keyboardType(.phonePad)
                        .onChange(of: phone) {
                            phone = TurkishPhoneFormat.format(phone)
                        }
                    TextField("Veli adı", text: $parentName)
                    TextField("05XX XXX XX XX", text: $parentPhone)
                        .keyboardType(.phonePad)
                        .onChange(of: parentPhone) {
                            parentPhone = TurkishPhoneFormat.format(parentPhone)
                        }
                }

                Section("Diğer") {
                    DatePicker("Başlangıç tarihi", selection: $startDate, displayedComponents: .date)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Renk")
                        HStack(spacing: 10) {
                            ForEach(0..<Theme.palette.count, id: \.self) { i in
                                Circle()
                                    .fill(Theme.palette[i])
                                    .frame(width: 30, height: 30)
                                    .overlay {
                                        if i == colorIndex {
                                            Image(systemName: "checkmark")
                                                .font(.caption.weight(.black))
                                                .foregroundStyle(.white)
                                        }
                                    }
                                    .onTapGesture { colorIndex = i }
                            }
                        }
                    }
                    TextField("Notlar (hedef, seviye, vb.)", text: $notes, axis: .vertical)
                        .lineLimit(3...6)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle(student == nil ? "Yeni Öğrenci" : "Öğrenciyi Düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private var weeklyClashes: [Lesson] {
        guard student == nil, !weeklyDays.isEmpty else { return [] }
        let t = Calendar.tr.dateComponents([.hour, .minute], from: weeklyTime)
        return RecurringLessons.clashes(weekdays: weeklyDays,
                                        hour: t.hour ?? 17,
                                        minute: t.minute ?? 0,
                                        duration: weeklyDuration,
                                        in: allLessons)
    }

    private func save() {
        if let student {
            let rateChanged = abs(student.hourlyRate - hourlyRate) > 0.001
            if rateChanged {
                lockExistingStandardLessonFees(for: student)
            }
            student.name = name
            student.subject = subject
            student.grade = grade
            student.phone = phone
            student.parentName = parentName
            student.parentPhone = parentPhone
            student.hourlyRate = hourlyRate
            if rateChanged {
                StudentActions.applyRateToPlannedLessons(of: student)
            }
            student.startDate = startDate
            student.colorIndex = colorIndex
            student.notes = notes
        } else {
            let new = Student(name: name,
                              subject: subject,
                              grade: grade,
                              phone: phone,
                              parentName: parentName,
                              parentPhone: parentPhone,
                              hourlyRate: hourlyRate,
                              startDate: startDate,
                              colorIndex: colorIndex,
                              notes: notes)
            context.insert(new)
            if !weeklyDays.isEmpty {
                let t = Calendar.tr.dateComponents([.hour, .minute], from: weeklyTime)
                for weekday in weeklyDays {
                    let template = RecurringLessonTemplate(weekday: weekday,
                                                           hour: t.hour ?? 17,
                                                           minute: t.minute ?? 0,
                                                           duration: weeklyDuration)
                    context.insert(template)
                    template.student = new
                    // İleri tarihli başlangıçta dersler o günden itibaren planlanır.
                    if startDate.startOfDay > Date() {
                        template.generatedUntil = startDate.startOfDay
                    }
                }
                try? context.save()
                RecurringLessons.topUp(context: context)
            }
        }
        try? context.save()
        dismiss()
    }

    private func lockExistingStandardLessonFees(for student: Student) {
        for lesson in student.allLessons where !lesson.usesCustomFee {
            lesson.lockCurrentStandardFeeIfNeeded()
        }
    }
}
