//
//  SettingsView.swift
//  One — Ders Defteri
//
//  Bildirim tercihleri ve veri bilgisi.
//

import SwiftUI
import SwiftData
import UserNotifications

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \Student.name) private var students: [Student]
    @Query(sort: \Lesson.date) private var lessons: [Lesson]
    @Query(sort: \Payment.date) private var payments: [Payment]
    @Query(sort: \Homework.dueDate) private var homeworks: [Homework]

    @AppStorage("remindersEnabled") private var remindersEnabled = true
    @AppStorage("reminderMinutes") private var reminderMinutes = 60
    @AppStorage("homeworkRemindersEnabled") private var homeworkRemindersEnabled = true
    @AppStorage("homeworkReminderHour") private var homeworkReminderHour = 9
    @AppStorage("dailyDigestEnabled") private var dailyDigestEnabled = false
    @AppStorage("dailyDigestHour") private var dailyDigestHour = 8
    @AppStorage("teacherName") private var teacherName = ""
    @Environment(ProStore.self) private var proStore
    @Environment(AuthManager.self) private var auth
    @State private var permissionDenied = false
    @State private var exportURLs: [URL] = []
    @State private var showPaywall = false

    private let leadOptions = [15, 30, 60, 120]
    private let homeworkHourOptions = [9, 12, 18, 21]
    private let digestHourOptions = [6, 7, 8, 9]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if proStore.isPro {
                        Label {
                            Text("Ders Defteri Pro aktif")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.ink)
                        } icon: {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundStyle(Theme.green)
                        }
                    } else {
                        Button {
                            showPaywall = true
                        } label: {
                            HStack {
                                Label {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Ders Defteri Pro'ya Geç")
                                            .font(.subheadline.weight(.bold))
                                            .foregroundStyle(Theme.ink)
                                        Text("Eşitleme, sınırsız öğrenci ve PDF veli raporu")
                                            .font(.caption)
                                            .foregroundStyle(Theme.inkSoft)
                                    }
                                } icon: {
                                    Image(systemName: "crown.fill")
                                        .foregroundStyle(Theme.amber)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(Theme.inkSoft.opacity(0.5))
                            }
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("Abonelik")
                }

                Section {
                    TextField("Öğretmen adı", text: $teacherName)
                        .textContentType(.name)
                        .textInputAutocapitalization(.words)
                    Label("Ana sayfada \(teacherDisplayName) olarak görünür.",
                          systemImage: "person.crop.circle")
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                } header: {
                    Text("Profil")
                } footer: {
                    Text("Boş bırakırsan ana sayfada Öğretmenim hitabı kullanılır.")
                }

                Section {
                    Toggle("Ders hatırlatıcıları", isOn: $remindersEnabled)
                    if remindersEnabled {
                        Picker("Ne kadar önce?", selection: $reminderMinutes) {
                            ForEach(leadOptions, id: \.self) { m in
                                Text(m >= 60 ? "\(m / 60) saat önce" : "\(m) dakika önce").tag(m)
                            }
                        }
                    }
                    if permissionDenied {
                        Label("Bildirim izni kapalı. iPhone Ayarlar > Bildirimler > One'dan izin verebilirsin.",
                              systemImage: "exclamationmark.triangle.fill")
                            .font(.caption)
                            .foregroundStyle(Theme.red)
                    }
                } header: {
                    Text("Ders Bildirimleri")
                } footer: {
                    Text("Planlanan her ders için, ders saatinden önce hatırlatma bildirimi gönderilir.")
                }

                Section {
                    Toggle("Ödev hatırlatıcıları", isOn: $homeworkRemindersEnabled)
                    if homeworkRemindersEnabled {
                        Picker("Bildirim saati", selection: $homeworkReminderHour) {
                            ForEach(homeworkHourOptions, id: \.self) { hour in
                                Text(String(format: "%02d:00", hour)).tag(hour)
                            }
                        }
                    }
                } header: {
                    Text("Ödev Bildirimleri")
                } footer: {
                    Text("Aktif ödevler için teslimden bir gün önce, teslim günü ve gecikirse ertesi gün hatırlatma kurulur.")
                }

                Section {
                    if proStore.isPro {
                        Toggle("Günlük program özeti", isOn: $dailyDigestEnabled)
                        if dailyDigestEnabled {
                            Picker("Bildirim saati", selection: $dailyDigestHour) {
                                ForEach(digestHourOptions, id: \.self) { hour in
                                    Text(String(format: "%02d:00", hour)).tag(hour)
                                }
                            }
                        }
                    } else {
                        Button {
                            showPaywall = true
                        } label: {
                            HStack {
                                Label {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Günlük Program Özeti")
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(Theme.ink)
                                        Text("Sabah tek bildirimde günün tüm dersleri")
                                            .font(.caption)
                                            .foregroundStyle(Theme.inkSoft)
                                    }
                                } icon: {
                                    Image(systemName: "sun.horizon.fill")
                                        .foregroundStyle(Theme.amber)
                                }
                                Spacer()
                                Image(systemName: "lock.fill")
                                    .font(.caption)
                                    .foregroundStyle(Theme.amber)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("Günlük Özet")
                } footer: {
                    Text(proStore.isPro
                         ? "Ders olan her sabah, o günün programı tek bildirimde gelir."
                         : "Günlük program özeti Ders Defteri Pro aboneliğiyle kullanılabilir.")
                }

                Section {
                    NavigationLink {
                        AccountView()
                    } label: {
                        HStack {
                            Label("Eşitleme ve Hesap", systemImage: "arrow.triangle.2.circlepath")
                            Spacer()
                            Text(syncSummary)
                                .font(.caption)
                                .foregroundStyle(Theme.inkSoft)
                        }
                    }
                } header: {
                    Text("Eşitleme")
                } footer: {
                    Text(proStore.isPro
                         ? "Kayıtların cihazında saklanır ve hesabına eşitlenir."
                         : "Defterin yalnızca bu cihazda tutuluyor. Eşitleme Ders Defteri Pro ile açılır.")
                }

                Section {
                    Link(destination: URL(string: "https://dersdefteri.adilemree.xyz/destek")!) {
                        Label("Destek", systemImage: "questionmark.circle")
                    }
                    Link(destination: URL(string: "https://dersdefteri.adilemree.xyz/gizlilik")!) {
                        Label("Gizlilik Politikası", systemImage: "hand.raised")
                    }
                    Link(destination: URL(string: "https://dersdefteri.adilemree.xyz/kosullar")!) {
                        Label("Kullanım Koşulları", systemImage: "doc.text")
                    }
                } header: {
                    Text("Hakkında")
                }

                Section {
                    if proStore.isPro {
                        Button {
                            exportURLs = AppDataExport.makeCSVFiles(students: students,
                                                                     lessons: lessons,
                                                                     payments: payments,
                                                                     homeworks: homeworks)
                        } label: {
                            Label("CSV Dosyalarını Hazırla", systemImage: "tablecells")
                        }
                        if !exportURLs.isEmpty {
                            ShareLink(items: exportURLs) {
                                Label("CSV Dosyalarını Paylaş", systemImage: "square.and.arrow.up")
                            }
                        }
                    } else {
                        Button {
                            showPaywall = true
                        } label: {
                            HStack {
                                Label("CSV Dışa Aktarma", systemImage: "tablecells")
                                Spacer()
                                Image(systemName: "lock.fill")
                                    .font(.caption)
                                    .foregroundStyle(Theme.amber)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("Dışa Aktar")
                } footer: {
                    Text(proStore.isPro
                         ? "Öğrenci, ders, ödeme ve ödev kayıtları ayrı CSV dosyaları olarak hazırlanır."
                         : "CSV dışa aktarma Ders Defteri Pro aboneliğiyle kullanılabilir.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle("Ayarlar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tamam") { dismiss() }
                }
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
            }
            .task {
                let settings = await UNUserNotificationCenter.current().notificationSettings()
                permissionDenied = settings.authorizationStatus == .denied
            }
            .onChange(of: remindersEnabled) {
                Task { await AppNotifications.resync(context: context) }
            }
            .onChange(of: reminderMinutes) {
                Task { await AppNotifications.resync(context: context) }
            }
            .onChange(of: homeworkRemindersEnabled) {
                Task { await AppNotifications.resync(context: context) }
            }
            .onChange(of: homeworkReminderHour) {
                Task { await AppNotifications.resync(context: context) }
            }
            .onChange(of: dailyDigestEnabled) {
                Task { await AppNotifications.resync(context: context) }
            }
            .onChange(of: dailyDigestHour) {
                Task { await AppNotifications.resync(context: context) }
            }
        }
    }

    private var syncSummary: String {
        if !proStore.isPro { return "Kapalı" }
        return auth.state == .signedIn ? "Açık" : "Giriş gerekli"
    }

    private var teacherDisplayName: String {
        let trimmed = teacherName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "Öğretmenim" }
        let formatted = trimmed.capitalized(with: Locale(identifier: "tr_TR"))
        return "\(formatted) öğretmenim"
    }
}
