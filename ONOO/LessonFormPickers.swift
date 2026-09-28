//
//  LessonFormPickers.swift
//  One — Ders Defteri
//
//  Ders formlarındaki hızlı seçiciler: öğrenci çipleri, süre ve saat
//  düğmeleri. Açılır listelerde her seçim iki dokunuş ve bir liste taraması
//  istiyordu; burada en sık seçenekler tek dokunuşla seçilir.
//

import SwiftUI
import SwiftData

// MARK: - Ortak çip görünümü

struct FormChip: View {
    let title: String
    var isSelected: Bool
    var showsChevron = false
    /// Satırı eşit paylaşan düğmeler için: genişliği doldurur, iç boşluk azalır.
    var fillsWidth = false

    var body: some View {
        HStack(spacing: 3) {
            Text(title)
                .lineLimit(1)
            if showsChevron {
                Image(systemName: "chevron.down")
                    .font(.caption2.weight(.bold))
            }
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(isSelected ? .white : Theme.ink)
        .padding(.horizontal, fillsWidth ? 4 : 12)
        .padding(.vertical, 7)
        .frame(minWidth: fillsWidth ? 0 : 56, maxWidth: fillsWidth ? .infinity : nil)
        .background(Capsule().fill(isSelected ? Theme.board : Theme.paper))
        .overlay(Capsule().stroke(isSelected ? .clear : Theme.line, lineWidth: 1))
        .contentShape(Capsule())
    }
}

// MARK: - Öğrenci

/// Öğrenciler renkli avatar çipleri olarak yan yana; seçili olan kendi
/// renginde dolar.
struct StudentChipPicker: View {
    let students: [Student]
    @Binding var selection: PersistentIdentifier?

    /// Aynı ilk isimden iki öğrenci varsa tam ad gösterilir.
    private var duplicateFirstNames: Set<String> {
        let names = students.map(firstName)
        return Set(names.filter { name in names.filter { $0 == name }.count > 1 })
    }

    var body: some View {
        if students.isEmpty {
            Text("Önce bir öğrenci eklemelisin.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSoft)
        } else {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(students) { student in
                            chip(student)
                                .id(student.persistentModelID)
                        }
                    }
                    .padding(.vertical, 2)
                }
                .onAppear {
                    if let selection {
                        proxy.scrollTo(selection, anchor: .center)
                    }
                }
                // Kenarda yarım görünen çip seçilince ortaya gelsin.
                .onChange(of: selection) { _, new in
                    if let new {
                        withAnimation(.snappy) { proxy.scrollTo(new, anchor: .center) }
                    }
                }
            }
        }
    }

    private func chip(_ student: Student) -> some View {
        let isSelected = student.persistentModelID == selection
        let first = firstName(student)
        return Button {
            withAnimation(.snappy) { selection = student.persistentModelID }
        } label: {
            HStack(spacing: 7) {
                StudentAvatar(student: student, size: 28)
                    .overlay(Circle().stroke(.white, lineWidth: isSelected ? 2 : 0))
                VStack(alignment: .leading, spacing: 0) {
                    Text(duplicateFirstNames.contains(first) ? student.name : first)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(isSelected ? .white : Theme.ink)
                        .lineLimit(1)
                    if !student.subject.isEmpty {
                        Text(student.subject)
                            .font(.caption2)
                            .foregroundStyle(isSelected ? .white.opacity(0.85) : Theme.inkSoft)
                            .lineLimit(1)
                    }
                }
            }
            .padding(.leading, 5)
            .padding(.trailing, 12)
            .padding(.vertical, 5)
            .background(Capsule().fill(isSelected ? student.color : Theme.paper))
            .overlay(Capsule().stroke(isSelected ? .clear : Theme.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(student.name)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func firstName(_ student: Student) -> String {
        student.name.split(separator: " ").first.map(String.init) ?? student.name
    }
}

// MARK: - Süre

/// En sık dört süre tek dokunuşta; diğerleri "Diğer" menüsünde. Beş düğme
/// dar ekrana sığsın diye yalnız sayı yazılır; birim satır başlığındadır.
struct DurationChipPicker: View {
    @Binding var selection: Int

    private let common = [45, 60, 90, 120]
    private let all = [30, 45, 60, 75, 90, 105, 120, 150, 180, 240]

    var body: some View {
        HStack(spacing: 6) {
            ForEach(common, id: \.self) { minutes in
                Button {
                    selection = minutes
                } label: {
                    FormChip(title: "\(minutes)", isSelected: selection == minutes, fillsWidth: true)
                }
                .buttonStyle(.plain)
            }
            Menu {
                ForEach(all, id: \.self) { minutes in
                    Button("\(minutes) dk") { selection = minutes }
                }
            } label: {
                FormChip(title: common.contains(selection) ? "Diğer" : "\(selection)",
                         isSelected: !common.contains(selection),
                         showsChevron: true,
                         fillsWidth: true)
            }
        }
    }
}

// MARK: - Saat

/// Özel derslerin en sık saatleri tek dokunuşta. Tam saat için formdaki
/// saat seçici yerinde kalır.
struct TimeChipPicker: View {
    @Binding var time: Date

    private let hours = [10, 13, 15, 16, 17, 18, 19, 20]

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(hours, id: \.self) { hour in
                        Button {
                            time = Calendar.tr.date(bySettingHour: hour, minute: 0, second: 0, of: time) ?? time
                        } label: {
                            FormChip(title: String(format: "%02d:00", hour), isSelected: isSelected(hour))
                        }
                        .buttonStyle(.plain)
                        .id(hour)
                    }
                }
                .padding(.vertical, 2)
            }
            .onAppear {
                proxy.scrollTo(Calendar.tr.component(.hour, from: time), anchor: .center)
            }
        }
    }

    private func isSelected(_ hour: Int) -> Bool {
        Calendar.tr.component(.hour, from: time) == hour && Calendar.tr.component(.minute, from: time) == 0
    }
}

// MARK: - Haftanın günleri

/// Pazartesi'den başlayan kısa gün adları. Yeni haftalık derste birden çok
/// gün seçilebilir (Salı ve Perşembe gibi); düzenlemede tek gün.
struct WeekdayChipPicker: View {
    @Binding var selection: Set<Int>
    var allowsMultiple = true

    private static let shortNames = [2: "Pzt", 3: "Sal", 4: "Çar", 5: "Per", 6: "Cum", 7: "Cmt", 1: "Paz"]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(RecurringLessonTemplate.weekdayOrder, id: \.self) { day in
                Button {
                    if allowsMultiple {
                        if selection.contains(day) { selection.remove(day) } else { selection.insert(day) }
                    } else {
                        selection = [day]
                    }
                } label: {
                    FormChip(title: Self.shortNames[day] ?? "", isSelected: selection.contains(day), fillsWidth: true)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(RecurringLessonTemplate.weekdayName(day))
            }
        }
    }
}
