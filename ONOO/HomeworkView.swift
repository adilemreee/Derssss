//
//  HomeworkView.swift
//  One — Ders Defteri
//
//  Ödev takibi: yapışkan not görünümlü kartlar ve ödev formu.
//

import SwiftUI
import SwiftData

struct HomeworkView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Homework.dueDate) private var homeworks: [Homework]

    private enum Filter: String, CaseIterable {
        case active = "Aktif"
        case late = "Geciken"
        case done = "Tamamlanan"
    }

    @State private var filter: Filter = .active
    @State private var showForm = false
    @State private var editingHomework: Homework?

    private var filtered: [Homework] {
        switch filter {
        case .active: return homeworks.filter { !$0.isDone && !$0.isLate }
        case .late: return homeworks.filter { $0.isLate }
        case .done: return homeworks.filter { $0.isDone }.sorted { ($0.doneDate ?? $0.dueDate) > ($1.doneDate ?? $1.dueDate) }
        }
    }

    private func count(_ f: Filter) -> Int {
        switch f {
        case .active: return homeworks.filter { !$0.isDone && !$0.isLate }.count
        case .late: return homeworks.filter { $0.isLate }.count
        case .done: return homeworks.filter { $0.isDone }.count
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("Filtre", selection: $filter) {
                    ForEach(Filter.allCases, id: \.self) { f in
                        Text("\(f.rawValue) (\(count(f)))").tag(f)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                ScrollView {
                    if filtered.isEmpty {
                        EmptyStateView(icon: emptyIcon,
                                       title: emptyTitle,
                                       message: filter == .active ? "Sağ üstteki + ile ödev verebilirsin." : "")
                            .padding(16)
                            .padding(.top, 30)
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible())],
                                  spacing: 14) {
                            ForEach(Array(filtered.enumerated()), id: \.element.persistentModelID) { idx, hw in
                                StickyNoteCard(homework: hw, index: idx) {
                                    editingHomework = hw
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                    }
                }
            }
            .background(Theme.paper.ignoresSafeArea())
            .navigationTitle("Ödevler")
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
                HomeworkFormView()
            }
            .sheet(item: $editingHomework) { hw in
                HomeworkFormView(homework: hw)
            }
        }
    }

    private var emptyIcon: String {
        switch filter {
        case .active: return "book"
        case .late: return "checkmark.seal"
        case .done: return "tray"
        }
    }

    private var emptyTitle: String {
        switch filter {
        case .active: return "Aktif ödev yok"
        case .late: return "Geciken ödev yok 🎉"
        case .done: return "Tamamlanan ödev yok"
        }
    }
}

// MARK: - Yapışkan not kartı

struct StickyNoteCard: View {
    @Environment(\.modelContext) private var context
    let homework: Homework
    let index: Int
    var onEdit: () -> Void

    private static let noteColors: [Color] = [
        Theme.dynamic(light: 0xFDF0C2, dark: 0x46402A),
        Theme.dynamic(light: 0xE3F2DC, dark: 0x2E4433),
        Theme.dynamic(light: 0xE4EDFB, dark: 0x2C3B50),
        Theme.dynamic(light: 0xFBE5DE, dark: 0x4A322B)
    ]

    private var noteColor: Color {
        Self.noteColors[index % Self.noteColors.count]
    }

    var body: some View {
        ZStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 5) {
                    Circle()
                        .fill(homework.student?.color ?? Theme.inkSoft)
                        .frame(width: 7, height: 7)
                    Text(homework.student?.name ?? "—")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(Theme.inkSoft)
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.top, 6)

                Text(homework.title)
                    .font(.subheadline.weight(.semibold))
                    .fontDesign(.serif)
                    .foregroundStyle(Theme.ink)
                    .strikethrough(homework.isDone)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)

                if !homework.detail.isEmpty {
                    Text(homework.detail)
                        .font(.caption2)
                        .foregroundStyle(Theme.inkSoft)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 0)

                HStack {
                    Text(footerText)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(homework.isLate ? Theme.red : Theme.inkSoft)
                    Spacer()
                    Button {
                        withAnimation(.snappy) {
                            homework.isDone.toggle()
                            homework.doneDate = homework.isDone ? Date() : nil
                            try? context.save()
                        }
                    } label: {
                        Image(systemName: homework.isDone ? "checkmark.circle.fill" : "circle")
                            .font(.title3)
                            .foregroundStyle(homework.isDone ? Theme.green : Theme.inkSoft.opacity(0.6))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 140, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(noteColor)
                    .shadow(color: .black.opacity(0.1), radius: 5, y: 3)
            )

            Image(systemName: "pin.fill")
                .font(.subheadline)
                .foregroundStyle(Theme.red)
                .rotationEffect(.degrees(38))
                .offset(y: -7)
        }
        .rotationEffect(.degrees(index.isMultiple(of: 2) ? -0.8 : 0.8))
        .contentShape(Rectangle())
        .onTapGesture { onEdit() }
        .contextMenu {
            Button { onEdit() } label: { Label("Düzenle", systemImage: "pencil") }
            Button(role: .destructive) {
                context.delete(homework)
                try? context.save()
            } label: {
                Label("Sil", systemImage: "trash")
            }
        }
    }

    private var footerText: String {
        if homework.isDone {
            if let d = homework.doneDate {
                return "Yapıldı: \(Fmt.dayMonthShort.string(from: d))"
            }
            return "Yapıldı"
        }
        if homework.isLate {
            return "Gecikti! Son: \(Fmt.dayMonthShort.string(from: homework.dueDate))"
        }
        return "Son: \(Fmt.dayMonthShort.string(from: homework.dueDate))"
    }
}

// MARK: - Ödev formu

struct HomeworkFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \Student.name) private var students: [Student]

    var homework: Homework? = nil
    var defaultStudent: Student? = nil

    @State private var studentID: PersistentIdentifier?
    @State private var title: String
    @State private var detail: String
    @State private var dueDate: Date

    init(homework: Homework? = nil, defaultStudent: Student? = nil) {
        self.homework = homework
        self.defaultStudent = defaultStudent
        let initialStudent = homework?.student ?? defaultStudent
        _studentID = State(initialValue: initialStudent?.persistentModelID)
        _title = State(initialValue: homework?.title ?? "")
        _detail = State(initialValue: homework?.detail ?? "")
        _dueDate = State(initialValue: homework?.dueDate ?? Date().startOfDay.adding(days: 7))
    }

    private var selectedStudent: Student? {
        students.first { $0.persistentModelID == studentID }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Ödev") {
                    Picker("Öğrenci", selection: $studentID) {
                        Text("Seçiniz").tag(nil as PersistentIdentifier?)
                        ForEach(students.filter { !$0.isArchived }) { s in
                            Text(s.name).tag(Optional(s.persistentModelID))
                        }
                    }
                    TextField("Başlık (ör. Türev testi 1-20)", text: $title)
                    TextField("Açıklama", text: $detail, axis: .vertical)
                        .lineLimit(2...5)
                    DatePicker("Teslim tarihi", selection: $dueDate, displayedComponents: .date)
                }

                if let homework {
                    Section {
                        Button("Ödevi Sil", role: .destructive) {
                            context.delete(homework)
                            try? context.save()
                            dismiss()
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle(homework == nil ? "Yeni Ödev" : "Ödevi Düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                        .disabled(studentID == nil || title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func save() {
        guard let student = selectedStudent else { return }
        if let homework {
            homework.student = student
            homework.title = title
            homework.detail = detail
            homework.dueDate = dueDate
        } else {
            let new = Homework(title: title, detail: detail, dueDate: dueDate)
            context.insert(new)
            new.student = student
        }
        try? context.save()
        dismiss()
    }
}
