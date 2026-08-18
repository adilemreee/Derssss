//
//  RecurringLessonsView.swift
//  One — Ders Defteri
//
//  Tekrarlayan ders şablonları: liste ve ekleme/düzenleme formu.
//

import SwiftUI
import SwiftData

struct RecurringLessonsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \RecurringLessonTemplate.createdAt) private var templates: [RecurringLessonTemplate]

    @Environment(ProStore.self) private var proStore
    @State private var showForm = false
    @State private var showPaywall = false
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
                                       title: "Tekrarlayan ders yok",
                                       message: "Sağ üstteki + ile \"Her Salı 17:00\" gibi bir şablon ekle; dersler 4 hafta ilerisi için otomatik oluşturulsun.")
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
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Tekrarlayan Dersler")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if proStore.canAddTemplate(activeCount: templates.count) {
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
                RecurringTemplateFormView()
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
            }
            .sheet(item: $editingTemplate) { template in
                RecurringTemplateFormView(template: template)
            }
            .confirmationDialog("Şablon silinsin mi?",
                                isPresented: Binding(get: { deletingTemplate != nil },
                                                     set: { if !$0 { deletingTemplate = nil } }),
                                titleVisibility: .visible) {
                Button("Şablonu ve Gelecek Dersleri Sil", role: .destructive) {
                    delete(alsoUpcoming: true)
                }
                Button("Sadece Şablonu Sil", role: .destructive) {
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
            Text("Dersler \(RecurringLessons.horizonDays / 7) hafta ilerisi için otomatik oluşturulur. Sildiğin bir ders geri eklenmez.")
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

// MARK: - Şablon kartı

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

// MARK: - Şablon formu (ekle / düzenle)

struct RecurringTemplateFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \Student.name) private var students: [Student]

    var template: RecurringLessonTemplate? = nil

    @State private var studentID: PersistentIdentifier?
    @State private var weekday: Int
    @State private var time: Date
    @State private var duration: Int
    @State private var useCustomFee: Bool
    @State private var customFee: Double
    @State private var isPaused: Bool

    init(template: RecurringLessonTemplate? = nil) {
        self.template = template
        _studentID = State(initialValue: template?.student?.persistentModelID)
        _weekday = State(initialValue: template?.weekday ?? 3)

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
                    Picker("Gün", selection: $weekday) {
                        ForEach(RecurringLessonTemplate.weekdayOrder, id: \.self) { day in
                            Text(RecurringLessonTemplate.weekdayName(day)).tag(day)
                        }
                    }
                    DatePicker("Saat", selection: $time, displayedComponents: .hourAndMinute)
                    Picker("Süre", selection: $duration) {
                        ForEach(durationOptions, id: \.self) { d in
                            Text("\(d) dk").tag(d)
                        }
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
                        Text("Duraklatılan şablon yeni ders üretmez; mevcut dersler silinmez.")
                    }
                }

                Section {
                } footer: {
                    Text("Dersler \(RecurringLessons.horizonDays / 7) hafta ilerisi için otomatik oluşturulur. Gün veya saat değişirse gelecek planlanan dersler yeniden düzenlenir.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle(template == nil ? "Yeni Tekrarlayan Ders" : "Şablonu Düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                        .disabled(studentID == nil)
                }
            }
        }
    }

    private func save() {
        guard let student = selectedStudent else { return }
        let t = Calendar.tr.dateComponents([.hour, .minute], from: time)
        let hour = t.hour ?? 17
        let minute = t.minute ?? 0
        let fee: Double? = useCustomFee ? customFee : nil

        if let template {
            let scheduleChanged = template.weekday != weekday
                || template.hour != hour
                || template.minute != minute
                || template.duration != duration
            template.student = student
            template.weekday = weekday
            template.hour = hour
            template.minute = minute
            template.duration = duration
            template.feeOverride = fee
            template.usesCustomFee = useCustomFee
            template.isPaused = isPaused
            if scheduleChanged {
                RecurringLessons.regenerate(template, in: context)
            } else {
                try? context.save()
                RecurringLessons.topUp(context: context)
            }
        } else {
            let new = RecurringLessonTemplate(weekday: weekday,
                                              hour: hour,
                                              minute: minute,
                                              duration: duration,
                                              feeOverride: fee,
                                              usesCustomFee: useCustomFee)
            context.insert(new)
            new.student = student
            try? context.save()
            RecurringLessons.topUp(context: context)
        }
        dismiss()
    }
}
