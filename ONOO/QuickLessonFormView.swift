//
//  QuickLessonFormView.swift
//  One — Ders Defteri
//
//  Ana ekrandan hızlı ders planlama formu.
//

import SwiftUI
import SwiftData

struct QuickLessonFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \Student.name) private var students: [Student]
    @Query(sort: \Lesson.date) private var lessons: [Lesson]

    private enum DayChoice: String, CaseIterable {
        case today = "Bugün"
        case tomorrow = "Yarın"
        case custom = "Tarih"
    }

    @State private var studentID: PersistentIdentifier?
    @State private var dayChoice: DayChoice = .today
    @State private var customDate = Date()
    @State private var time: Date
    @State private var duration = 60
    @State private var repeatsWeekly = false
    @State private var showConflictAlert = false

    init(defaultDate: Date = Date()) {
        var comps = Calendar.tr.dateComponents([.year, .month, .day], from: defaultDate)
        comps.hour = Calendar.tr.component(.hour, from: Date().addingTimeInterval(3600))
        comps.minute = 0
        _time = State(initialValue: Calendar.tr.date(from: comps) ?? defaultDate)
    }

    private var selectedStudent: Student? {
        students.first { $0.persistentModelID == studentID }
    }

    private var selectedDay: Date {
        switch dayChoice {
        case .today: return Date()
        case .tomorrow: return Date().adding(days: 1)
        case .custom: return customDate
        }
    }

    private var startDate: Date {
        var comps = Calendar.tr.dateComponents([.year, .month, .day], from: selectedDay)
        let timeParts = Calendar.tr.dateComponents([.hour, .minute], from: time)
        comps.hour = timeParts.hour
        comps.minute = timeParts.minute
        return Calendar.tr.date(from: comps) ?? selectedDay
    }

    private var activeStudents: [Student] {
        students.filter { !$0.isArchived }
    }

    /// Her hafta seçiliyse sonraki üç haftanın aynı saati de kontrol edilir.
    private var conflicts: [Lesson] {
        let seconds = Double(duration) * 60
        let slots = (0..<(repeatsWeekly ? 4 : 1)).map { startDate.adding(days: $0 * 7) }
        return lessons.filter { lesson in
            lesson.status != .cancelled &&
            slots.contains { $0 < lesson.endDate && lesson.date < $0.addingTimeInterval(seconds) }
        }
    }

    private var conflictText: String {
        conflicts.prefix(3).map { lesson in
            "\(Fmt.dayMonthShort.string(from: lesson.date)) \(Fmt.time.string(from: lesson.date)) - \(lesson.student?.name ?? "Öğrenci")"
        }
        .joined(separator: "\n")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    StudentChipPicker(students: activeStudents, selection: $studentID)
                        .listRowInsets(EdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12))
                } header: {
                    Text("Öğrenci")
                }

                Section {
                    Picker("Gün", selection: $dayChoice) {
                        ForEach(DayChoice.allCases, id: \.self) { choice in
                            Text(choice.rawValue).tag(choice)
                        }
                    }
                    .pickerStyle(.segmented)

                    if dayChoice == .custom {
                        DatePicker("Tarih", selection: $customDate, displayedComponents: .date)
                    }

                    DatePicker("Saat", selection: $time, displayedComponents: .hourAndMinute)
                    TimeChipPicker(time: $time)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Süre (dakika)")
                        DurationChipPicker(selection: $duration)
                    }
                    .padding(.vertical, 2)

                    if !conflicts.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Label("Bu saatte ders var", systemImage: "exclamationmark.triangle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.red)
                            Text(conflictText)
                                .font(.caption)
                                .foregroundStyle(Theme.inkSoft)
                        }
                        .padding(.vertical, 4)
                    }
                } header: {
                    Text("Zaman")
                }

                Section {
                    Picker("Tekrar", selection: $repeatsWeekly) {
                        Text("Bir kez").tag(false)
                        Text("Her hafta").tag(true)
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("Tekrar")
                } footer: {
                    if repeatsWeekly {
                        Text("Her \(RecurringLessonTemplate.weekdayName(Calendar.tr.component(.weekday, from: startDate))) \(Fmt.time.string(from: startDate)) otomatik planlanır. Tatil haftasında o dersi silmen yeterli; sonraki haftalar devam eder.")
                    } else {
                        Text("Ders planlandı olarak kaydedilir; işlenince ödeme bakiyesine yansır.")
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle("Hızlı Ders Ekle")
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
            .onAppear {
                // Tek öğrenci varsa seçmek için dokunmaya gerek yok.
                if studentID == nil, activeStudents.count == 1 {
                    studentID = activeStudents[0].persistentModelID
                }
            }
            .alert("Ders çakışması var", isPresented: $showConflictAlert) {
                Button("Yine de Kaydet", role: .destructive) { save() }
                Button("Düzenle", role: .cancel) {}
            } message: {
                Text(conflictText)
            }
        }
    }

    private func attemptSave() {
        if conflicts.isEmpty {
            save()
        } else {
            showConflictAlert = true
        }
    }

    private func save() {
        guard let student = selectedStudent else { return }
        let lesson = Lesson(student: student,
                            date: startDate,
                            duration: duration,
                            status: .planned,
                            topic: "",
                            note: "",
                            feeOverride: Lesson.standardFee(for: student, duration: duration),
                            usesCustomFee: false)
        context.insert(lesson)
        lesson.student = student
        try? context.save()
        if repeatsWeekly {
            RecurringLessons.startSeries(from: lesson, in: context)
        }
        dismiss()
    }
}
