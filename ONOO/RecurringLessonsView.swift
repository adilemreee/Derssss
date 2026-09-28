//
//  RecurringLessonsView.swift
//  One — Ders Defteri
//
//  Haftalık dersler: liste ve ekleme/düzenleme formu. Kodda "şablon"
//  (RecurringLessonTemplate) olarak geçer; kullanıcı yalnızca "haftalık ders" görür.
//

import SwiftUI
import SwiftData

struct RecurringLessonsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \RecurringLessonTemplate.createdAt) private var templates: [RecurringLessonTemplate]

    @State private var showForm = false
    @State private var editingTemplate: RecurringLessonTemplate?
    @State private var deletingTemplate: RecurringLessonTemplate?

    private var sortedTemplates: [RecurringLessonTemplate] {
        templates.sorted { a, b in
            let ai = RecurringLessonTemplate.weekdayOrder.firstIndex(of: a.weekday) ?? 0
            let bi = RecurringLessonTemplate.weekdayOrder.firstIndex(of: b.weekday) ?? 0
            if ai != bi { return ai < bi }
            return (a.hour, a.minute) < (b.hour, b.minute)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    if sortedTemplates.isEmpty {
                        EmptyStateView(icon: "repeat",
                                       title: "Haftalık ders yok",
                                       message: "\"Her Salı 17:00\" gibi bir haftalık ders ekle; dersler 4 hafta ilerisi için otomatik planlansın. Ders eklerken \"Her hafta\" seçmen de yeterli.",
                                       actionTitle: "Haftalık Ders Ekle",
                                       action: { showForm = true })
                    } else {
                        infoNote
                        ForEach(sortedTemplates) { template in
                            RecurringTemplateCard(template: template) {
                                editingTemplate = template
                            } onDelete: {
                                deletingTemplate = template
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 24)
                .readableWidth()
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Haftalık Dersler")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") { dismiss() }
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
                RecurringTemplateFormView()
            }
            .sheet(item: $editingTemplate) { template in
                RecurringTemplateFormView(template: template)
            }
            .confirmationDialog("Haftalık ders silinsin mi?",
                                isPresented: Binding(get: { deletingTemplate != nil },
                                                     set: { if !$0 { deletingTemplate = nil } }),
                                titleVisibility: .visible) {
                Button("Gelecek Planlı Derslerle Birlikte Sil", role: .destructive) {
                    delete(alsoUpcoming: true)
                }
                Button("Yalnızca Tekrarı Durdur", role: .destructive) {
                    delete(alsoUpcoming: false)
                }
                Button("Vazgeç", role: .cancel) { deletingTemplate = nil }
            } message: {
                Text("İşlenmiş dersler her durumda korunur.")
            }
        }
    }

    private var infoNote: some View {
        HStack(spacing: 8) {
            Image(systemName: "info.circle")
                .foregroundStyle(Theme.inkSoft)
            Text("Dersler \(RecurringLessons.horizonDays / 7) hafta ilerisi için otomatik planlanır. Tatil haftasında o dersi silmen yeterli; seri devam eder.")
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
            Spacer()
        }
        .padding(.bottom, 2)
    }

    private func delete(alsoUpcoming: Bool) {
        guard let template = deletingTemplate else { return }
        if alsoUpcoming {
            RecurringLessons.deleteUpcomingLessons(of: template, in: context)
        }
        context.delete(template)
        try? context.save()
        deletingTemplate = nil
    }
}

// MARK: - Haftalık ders kartı

struct RecurringTemplateCard: View {
    @Environment(\.modelContext) private var context
    let template: RecurringLessonTemplate
    var onEdit: () -> Void
    var onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Capsule()
                .fill(template.student?.color ?? Theme.inkSoft)
                .frame(width: 4, height: 46)

            VStack(alignment: .leading, spacing: 4) {
                Text(template.student?.name ?? "—")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                Text("\(template.weekdayName) \(template.timeText) • \(template.duration) dk")
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 5) {
                Text(Fmt.money(fee))
                    .monospacedDigit()
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                if template.isPaused {
                    Chip(text: "Duraklatıldı", tint: Theme.amber, filled: true)
                } else {
                    Chip(text: "Aktif", tint: Theme.green)
                }
            }
        }
        .card(14)
        .contentShape(Rectangle())
        .onTapGesture { onEdit() }
        .contextMenu {
            Button { onEdit() } label: { Label("Düzenle", systemImage: "pencil") }
            Button {
                template.isPaused.toggle()
                try? context.save()
                if !template.isPaused {
                    RecurringLessons.topUp(context: context)
                }
            } label: {
                Label(template.isPaused ? "Devam Ettir" : "Duraklat",
                      systemImage: template.isPaused ? "play.circle" : "pause.circle")
            }
            Divider()
            Button(role: .destructive) { onDelete() } label: {
                Label("Sil", systemImage: "trash")
            }
        }
    }

    private var fee: Double {
        if let feeOverride = template.feeOverride { return feeOverride }
        return Lesson.standardFee(for: template.student, duration: template.duration)
    }
}

// MARK: - Haftalık ders formu (ekle / düzenle)

struct RecurringTemplateFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \Student.name) private var students: [Student]
    @Query private var lessons: [Lesson]

    var template: RecurringLessonTemplate? = nil

    @State private var studentID: PersistentIdentifier?
    @State private var weekdays: Set<Int>
    @State private var time: Date
    @State private var duration: Int
    @State private var useCustomFee: Bool
    @State private var customFee: Double
    @State private var isPaused: Bool
    @State private var confirmEnd = false

    init(template: RecurringLessonTemplate? = nil, defaultStudent: Student? = nil) {
        self.template = template
        _studentID = State(initialValue: (template?.student ?? defaultStudent)?.persistentModelID)
        _weekdays = State(initialValue: [template?.weekday ?? 3])

        var comps = DateComponents()
        comps.hour = template?.hour ?? 17
        comps.minute = template?.minute ?? 0
        _time = State(initialValue: Calendar.tr.date(from: comps) ?? Date())

        _duration = State(initialValue: template?.duration ?? 60)
        _useCustomFee = State(initialValue: template?.usesCustomFee ?? false)
        _customFee = State(initialValue: template?.feeOverride ?? 0)
        _isPaused = State(initialValue: template?.isPaused ?? false)
    }

    private var selectedStudent: Student? {
        students.first { $0.persistentModelID == studentID }
    }

    /// Aktif öğrenciler; arşivlenmiş bir öğrencinin dersi düzenleniyorsa o da.
    private var chipStudents: [Student] {
        students.filter { !$0.isArchived || $0.persistentModelID == studentID }
    }

    private var clashes: [Lesson] {
        let t = Calendar.tr.dateComponents([.hour, .minute], from: time)
        return RecurringLessons.clashes(weekdays: weekdays,
                                        hour: t.hour ?? 17,
                                        minute: t.minute ?? 0,
                                        duration: duration,
                                        ignoring: template,
                                        in: lessons)
    }

    private var defaultFee: Double {
        Lesson.standardFee(for: selectedStudent, duration: duration)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Öğrenci") {
                    StudentChipPicker(students: chipStudents, selection: $studentID)
                        .listRowInsets(EdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12))
                }

                Section {
                    WeekdayChipPicker(selection: $weekdays, allowsMultiple: template == nil)
                    DatePicker("Saat", selection: $time, displayedComponents: .hourAndMinute)
                    TimeChipPicker(time: $time)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Süre (dakika)")
                        DurationChipPicker(selection: $duration)
                    }
                    .padding(.vertical, 2)
                    if !clashes.isEmpty {
                        WeeklyClashNotice(lessons: clashes)
                    }
                } header: {
                    Text(template == nil ? "Günler ve saat" : "Gün ve saat")
                } footer: {
                    if template == nil {
                        Text("Birden çok gün seçersen her gün için ayrı haftalık ders oluşur.")
                    }
                }

                Section("Ücret") {
                    LabeledContent("Standart ücret", value: Fmt.money(defaultFee))
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

                if template != nil {
                    Section {
                        Toggle("Duraklat", isOn: $isPaused)
                    } footer: {
                        Text("Duraklatılan haftalık ders yeni ders planlamaz; mevcut dersler silinmez.")
                    }
                }

                if template != nil {
                    Section {
                        Button("Haftalık Dersi Bitir", role: .destructive) { confirmEnd = true }
                    }
                }

                Section {
                } footer: {
                    Text(template == nil
                         ? "Dersler \(RecurringLessons.horizonDays / 7) hafta ilerisi için otomatik planlanır."
                         : "Gün, saat, süre ya da ücret değişirse gelecekteki planlı dersler yeni düzene taşınır; konu ve notları korunur.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle(template == nil ? "Yeni Haftalık Ders" : "Haftalık Ders")
            .confirmationDialog("Haftalık ders bitirilsin mi?", isPresented: $confirmEnd, titleVisibility: .visible) {
                Button("Gelecek Planlı Dersleri de Sil", role: .destructive) { end(alsoUpcoming: true) }
                Button("Planlı Dersler Kalsın", role: .destructive) { end(alsoUpcoming: false) }
                Button("Vazgeç", role: .cancel) {}
            } message: {
                Text("İşlenmiş dersler her durumda korunur.")
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                        .disabled(studentID == nil || weekdays.isEmpty)
                }
            }
        }
    }

    private func end(alsoUpcoming: Bool) {
        guard let template else { return }
        if alsoUpcoming {
            RecurringLessons.endSeries(template, after: Date(), in: context)
        } else {
            context.delete(template)
            try? context.save()
        }
        dismiss()
    }

    private func save() {
        guard let student = selectedStudent else { return }
        let t = Calendar.tr.dateComponents([.hour, .minute], from: time)
        let hour = t.hour ?? 17
        let minute = t.minute ?? 0
        let fee: Double? = useCustomFee ? customFee : nil

        if let template {
            let newWeekday = weekdays.first ?? template.weekday
            let scheduleChanged = template.weekday != newWeekday
                || template.hour != hour
                || template.minute != minute
                || template.duration != duration
                || template.usesCustomFee != useCustomFee
                || (useCustomFee && template.feeOverride != fee)
            if template.student?.persistentModelID != student.persistentModelID {
                // Gelecek planlı dersler de yeni öğrenciye geçer.
                for lesson in template.allGeneratedLessons where lesson.status == .planned && lesson.date > Date() {
                    lesson.student = student
                }
                template.student = student
            }
            template.isPaused = isPaused
            if scheduleChanged {
                // Silip yeniden üretmek yerine taşır: konu ve notlar kalır.
                RecurringLessons.applyToFollowing(template: template,
                                                  from: Date(),
                                                  dayShift: RecurringLessons.dayShift(fromWeekday: template.weekday,
                                                                                      toWeekday: newWeekday),
                                                  weekday: newWeekday,
                                                  hour: hour,
                                                  minute: minute,
                                                  duration: duration,
                                                  feeOverride: fee,
                                                  usesCustomFee: useCustomFee,
                                                  in: context)
            } else {
                try? context.save()
                RecurringLessons.topUp(context: context)
            }
        } else {
            for weekday in weekdays {
                let new = RecurringLessonTemplate(weekday: weekday,
                                                  hour: hour,
                                                  minute: minute,
                                                  duration: duration,
                                                  feeOverride: fee,
                                                  usesCustomFee: useCustomFee)
                context.insert(new)
                new.student = student
            }
            try? context.save()
            RecurringLessons.topUp(context: context)
        }
        dismiss()
    }
}

// MARK: - Çakışma uyarısı

/// Haftalık ders formlarında: seçilen saatte başka ders olan haftalar
/// atlanır; kullanıcı bunu kaydetmeden görsün.
struct WeeklyClashNotice: View {
    let lessons: [Lesson]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("Bu saatte ders var", systemImage: "exclamationmark.triangle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.red)
            Text(lessons.prefix(3).map { lesson in
                "\(Fmt.dayMonthShort.string(from: lesson.date)) \(Fmt.time.string(from: lesson.date)) - \(lesson.student?.name ?? "Öğrenci")"
            }
            .joined(separator: "\n"))
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
            Text("Çakışan haftalarda ders oluşturulmaz.")
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
        }
        .padding(.vertical, 4)
    }
}
