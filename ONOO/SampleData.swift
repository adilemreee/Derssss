//
//  SampleData.swift
//  One — Ders Defteri
//
//  Yalnızca geliştirme derlemesinde: ekranları dolu hâliyle görmek ve App
//  Store ekran görüntüsü çekmek için örnek defter. Yayın derlemesine girmez.
//
//  Kullanım: Xcode → Scheme → Run → Arguments'a `-ornekVeri` ekle ya da
//  `xcrun simctl launch booted adilemre.ONOOOO -ornekVeri`. Defter boşsa
//  doldurulur ve tanıtım ekranı atlanır; doluysa hiçbir şeye dokunulmaz.
//

#if DEBUG
import Foundation
import SwiftData

enum SampleData {
    static func seedIfRequested(context: ModelContext) {
        guard ProcessInfo.processInfo.arguments.contains("-ornekVeri") else { return }
        guard context.fetchAll(Student.self).isEmpty else { return }

        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
        UserDefaults.standard.set("Deniz", forKey: "teacherName")

        let today = Date().startOfDay
        func at(_ dayOffset: Int, _ hour: Int, _ minute: Int = 0) -> Date {
            Calendar.tr.date(bySettingHour: hour, minute: minute, second: 0,
                             of: today.adding(days: dayOffset)) ?? today
        }

        struct Seed {
            let name, subject, grade: String
            let rate: Double
            let color: Int
            let hour: Int
            let topics: [String]
        }
        let seeds = [
            Seed(name: "Ayşe Yılmaz", subject: "Matematik", grade: "12. sınıf", rate: 900, color: 0, hour: 16,
                 topics: ["Türev", "Limit", "İntegral", "Türev uygulamaları", "Deneme çözümü"]),
            Seed(name: "Can Demir", subject: "Fizik", grade: "11. sınıf", rate: 950, color: 1, hour: 18,
                 topics: ["Newton yasaları", "Enerji", "Momentum", "Tork", "Atışlar"]),
            Seed(name: "Ece Kaya", subject: "İngilizce", grade: "8. sınıf", rate: 750, color: 4, hour: 15,
                 topics: ["Present perfect", "Passive voice", "Reading", "Vocabulary", "Deneme"]),
            Seed(name: "Mert Aydın", subject: "Kimya", grade: "10. sınıf", rate: 850, color: 2, hour: 17,
                 topics: ["Mol kavramı", "Asit ve baz", "Tepkimeler", "Karışımlar", "Gazlar"]),
        ]

        for (i, seed) in seeds.enumerated() {
            let student = Student(name: seed.name, subject: seed.subject, grade: seed.grade,
                                  phone: "0555 000 00 0\(i + 1)", parentName: "Veli",
                                  parentPhone: "0555 000 00 1\(i + 1)", hourlyRate: seed.rate,
                                  startDate: today.adding(days: -60), colorIndex: seed.color)
            context.insert(student)

            // Son dört hafta işlenmiş, bu hafta ve gelecek hafta planlı dersler.
            // İlk iki öğrencinin dersi bugün, diğerleri hafta içine dağılır.
            let offset = i < 2 ? 0 : i - 1
            let duration = i == 2 ? 60 : 90
            // İlk üç öğrencinin dersleri haftalık seri; sonuncusu tek tek
            // eklenmiş, "Haftalık ders ekle" boş hâli görülsün.
            var template: RecurringLessonTemplate?
            if i < 3 {
                let t = RecurringLessonTemplate(weekday: Calendar.tr.component(.weekday, from: at(offset, seed.hour)),
                                                hour: seed.hour, duration: duration)
                context.insert(t)
                t.student = student
                t.generatedUntil = at(offset + 7, seed.hour).addingTimeInterval(60)
                template = t
            }
            for week in -4...1 {
                let day = offset + week * 7
                let date = at(day, seed.hour)
                let status: LessonStatus = date < Date() ? .completed : .planned
                let lesson = Lesson(student: student, date: date, duration: duration,
                                    status: status, topic: seed.topics[(week + 4) % seed.topics.count])
                lesson.lockCurrentStandardFeeIfNeeded()
                context.insert(lesson)
                lesson.sourceTemplate = template
            }

            // Tahsilat öğrenciden öğrenciye farklı: biri avanslı, biri çok borçlu.
            let earned = seed.rate * (i == 2 ? 1 : 1.5) * 4
            let paidRatio = [0.5, 0.75, 1.1, 0.25][i]
            context.insert(Payment(student: student, date: today.adding(days: -10),
                                   amount: (earned * paidRatio).rounded(), method: i.isMultiple(of: 2) ? .transfer : .cash,
                                   note: "Aylık ödeme"))
        }

        let students = context.fetchAll(Student.self).sorted { $0.name < $1.name }
        let homework: [(Int, String, String, Int, Bool)] = [
            (0, "Türev soru seti", "Sayfa 84–96, çift numaralı sorular", 2, false),
            (1, "Enerji korunumu testi", "40 soruluk deneme", 3, false),
            (2, "Present perfect alıştırma", "Workbook ünite 5", -2, false),
            (3, "Mol hesaplama çalışması", "", -5, true),
        ]
        for (index, title, detail, due, done) in homework where index < students.count {
            context.insert(Homework(student: students[index], title: title, detail: detail,
                                    assignedDate: today.adding(days: -4), dueDate: today.adding(days: due),
                                    isDone: done, doneDate: done ? today.adding(days: -6) : nil))
        }

        try? context.save()
    }
}
#endif
