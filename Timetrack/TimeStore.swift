import Foundation
import LocalAuthentication
import SwiftUI
import UIKit

enum VatType: Int, Codable, CaseIterable, Identifiable {
    case none = 0
    case included = 1
    case excluded = 2

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .none:
            return "No VAT"
        case .included:
            return "VAT Included"
        case .excluded:
            return "Add VAT"
        }
    }
}

enum InvoiceLanguage: String, Codable, CaseIterable, Identifiable {
    case english = "en"
    case hebrew = "he"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .english:
            return "English"
        case .hebrew:
            return "Hebrew"
        }
    }
}

struct Project: Identifiable, Codable, Equatable {
    let id: UUID
    var name: String
    var colorHex: String
    var rate: Double
    var timesheetEmails: String
    var timesheetSubject: String
    var timesheetPretext: String
    var invoiceHeader: String
    var invoiceItemDetails: String
    var invoiceLanguage: InvoiceLanguage
    var greenInvoiceCustomerKey: String
    var greenInvoiceClientId: String
    var vatType: VatType

    init(
        id: UUID = UUID(),
        name: String,
        colorHex: String,
        rate: Double = 0,
        timesheetEmails: String = "",
        timesheetSubject: String = "",
        timesheetPretext: String = "",
        invoiceHeader: String = "",
        invoiceItemDetails: String = "",
        invoiceLanguage: InvoiceLanguage = .english,
        greenInvoiceCustomerKey: String = "",
        greenInvoiceClientId: String = "",
        vatType: VatType = .none
    ) {
        self.id = id
        self.name = name
        self.colorHex = colorHex
        self.rate = rate
        self.timesheetEmails = timesheetEmails
        self.timesheetSubject = timesheetSubject
        self.timesheetPretext = timesheetPretext
        self.invoiceHeader = invoiceHeader
        self.invoiceItemDetails = invoiceItemDetails
        self.invoiceLanguage = invoiceLanguage
        self.greenInvoiceCustomerKey = greenInvoiceCustomerKey
        self.greenInvoiceClientId = greenInvoiceClientId
        self.vatType = vatType
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case colorHex
        case rate
        case timesheetEmails
        case timesheetSubject
        case timesheetPretext
        case invoiceHeader
        case invoiceItemDetails
        case invoiceLanguage
        case greenInvoiceCustomerKey
        case greenInvoiceClientId
        case vatType
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        colorHex = try container.decode(String.self, forKey: .colorHex)
        rate = try container.decodeIfPresent(Double.self, forKey: .rate) ?? 0
        timesheetEmails = try container.decodeIfPresent(String.self, forKey: .timesheetEmails) ?? ""
        timesheetSubject = try container.decodeIfPresent(String.self, forKey: .timesheetSubject) ?? ""
        timesheetPretext = try container.decodeIfPresent(String.self, forKey: .timesheetPretext) ?? ""
        invoiceHeader = try container.decodeIfPresent(String.self, forKey: .invoiceHeader) ?? ""
        invoiceItemDetails = try container.decodeIfPresent(String.self, forKey: .invoiceItemDetails) ?? ""
        if let rawLanguage = try container.decodeIfPresent(String.self, forKey: .invoiceLanguage),
           let decodedLanguage = InvoiceLanguage(rawValue: rawLanguage) {
            invoiceLanguage = decodedLanguage
        } else if let decodedLanguage = try? container.decodeIfPresent(InvoiceLanguage.self, forKey: .invoiceLanguage) {
            invoiceLanguage = decodedLanguage
        } else {
            invoiceLanguage = .english
        }
        greenInvoiceCustomerKey = try container.decodeIfPresent(String.self, forKey: .greenInvoiceCustomerKey) ?? ""
        greenInvoiceClientId = try container.decodeIfPresent(String.self, forKey: .greenInvoiceClientId) ?? ""
        if let rawVat = try container.decodeIfPresent(Int.self, forKey: .vatType),
           let decodedVat = VatType(rawValue: rawVat) {
            vatType = decodedVat
        } else if let decodedVat = try? container.decodeIfPresent(VatType.self, forKey: .vatType) {
            vatType = decodedVat
        } else {
            vatType = .none
        }
    }
}

struct DayLog: Codable {
    var slots: [UUID?]
    var labels: [String?]?
}

struct TimeStoreSnapshot: Codable {
    var lastUpdated: Date
    var projects: [Project]
    var logs: [String: DayLog]
    var invoiceRecords: [InvoiceRecord]
    var greenInvoiceApiKey: String
    var greenInvoiceApiSecret: String
    var greenInvoiceTestMode: Bool
    var invoiceLogoData: Data?
    var invoiceSignatureData: Data?
    var invoiceStartNumber: Int
    var invoiceNextNumber: Int?
    var lastSelectedProjectId: UUID?
}

struct TimeEntry: Identifiable {
    let id = UUID()
    let day: Date
    let startIndex: Int
    let endIndex: Int
    let projectId: UUID
    let label: String?
}

struct InvoiceRecord: Identifiable, Codable {
    let id: UUID
    let clientKey: String
    let clientId: String?
    let date: Date
    let isSandbox: Bool

    init(
        id: UUID = UUID(),
        clientKey: String,
        clientId: String?,
        date: Date,
        isSandbox: Bool
    ) {
        self.id = id
        self.clientKey = clientKey
        self.clientId = clientId
        self.date = date
        self.isSandbox = isSandbox
    }
}

final class TimeStore: ObservableObject {
    static let slotsPerDay = 96
    static let slotMinutes = 15

    @Published var projects: [Project] = [] {
        didSet {
            saveProjects()
            if let lastId = lastSelectedProjectId,
               !projects.contains(where: { $0.id == lastId }) {
                lastSelectedProjectId = nil
            }
        }
    }
    @Published private var logs: [String: DayLog] = [:] {
        didSet { saveLogs() }
    }
    @Published private var invoiceRecords: [InvoiceRecord] = [] {
        didSet { saveInvoiceRecords() }
    }
    @Published var greenInvoiceApiKey: String = "" {
        didSet { saveGreenInvoiceApiKey() }
    }
    @Published var greenInvoiceApiSecret: String = "" {
        didSet { saveGreenInvoiceApiSecret() }
    }
    @Published var greenInvoiceTestMode: Bool = false {
        didSet { saveGreenInvoiceTestMode() }
    }
    @Published var invoiceLogoData: Data? {
        didSet { saveInvoiceLogoData() }
    }
    @Published var invoiceSignatureData: Data? {
        didSet { saveInvoiceSignatureData() }
    }
    @Published var invoiceStartNumber: Int = 1 {
        didSet { saveInvoiceStartNumber() }
    }
    @Published var invoiceNextNumber: Int? {
        didSet { saveInvoiceNextNumber() }
    }
    @Published var lastSelectedProjectId: UUID? {
        didSet { saveLastSelectedProjectId() }
    }
    @Published private(set) var lastCloudSync: Date? {
        didSet { saveLastCloudSync() }
    }

    private let projectsKey = "projects"
    private let logsKey = "dayLogs"
    private let invoiceRecordsKey = "invoiceRecords"
    private let greenInvoiceKey = "greenInvoiceApiKey"
    private let greenInvoiceSecretKey = "greenInvoiceApiSecret"
    private let greenInvoiceTestModeKey = "greenInvoiceTestMode"
    private let invoiceLogoKey = "invoiceLogoData"
    private let invoiceSignatureKey = "invoiceSignatureData"
    private let invoiceStartNumberKey = "invoiceStartNumber"
    private let invoiceNextNumberKey = "invoiceNextNumber"
    private let lastSelectedProjectKey = "lastSelectedProjectId"
    private let lastUpdatedKey = "lastUpdated"
    private let lastCloudSyncKey = "lastCloudSync"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let calendar = Calendar.current
    private var isLoaded = false
    private var isApplyingCloudUpdate = false
    private var lastUpdated: Date = .distantPast
    private var cloudQuery: NSMetadataQuery?
    private var cloudSaveWorkItem: DispatchWorkItem?
    private let cloudFileName = "TimetrackStore.json"
    private let cloudContainerIdentifier = "iCloud.il.co.simplevision.timetrack"
    private var hasLocalData: Bool {
        !projects.isEmpty ||
        !logs.isEmpty ||
        !invoiceRecords.isEmpty ||
        !greenInvoiceApiKey.isEmpty ||
        !greenInvoiceApiSecret.isEmpty ||
        greenInvoiceTestMode ||
        invoiceLogoData != nil ||
        invoiceSignatureData != nil ||
        invoiceStartNumber > 1 ||
        invoiceNextNumber != nil ||
        lastSelectedProjectId != nil
    }

    init() {
        load()
        isLoaded = true
        startCloudQuery()
        loadFromCloudIfAvailable()
    }

    deinit {
        if let cloudQuery {
            cloudQuery.stop()
        }
        NotificationCenter.default.removeObserver(self)
    }

    private var cloudFileURL: URL? {
        guard let containerURL = FileManager.default.url(forUbiquityContainerIdentifier: cloudContainerIdentifier) else {
            return nil
        }
        let documentsURL = containerURL.appendingPathComponent("Documents", isDirectory: true)
        try? FileManager.default.createDirectory(at: documentsURL, withIntermediateDirectories: true, attributes: nil)
        return documentsURL.appendingPathComponent(cloudFileName)
    }

    var isICloudAvailable: Bool {
        FileManager.default.url(forUbiquityContainerIdentifier: cloudContainerIdentifier) != nil
    }

    var lastCloudSyncLabel: String {
        guard isICloudAvailable else { return "Unavailable" }
        guard let lastCloudSync else { return "Not yet" }
        return Self.syncFormatter.string(from: lastCloudSync)
    }

    func slots(for day: Date) -> [UUID?] {
        let key = dayKey(for: day)
        if let log = logs[key], log.slots.count == Self.slotsPerDay {
            let validIds = Set(projects.map(\.id))
            return log.slots.map { id in
                guard let id else { return nil }
                return validIds.contains(id) ? id : nil
            }
        }
        return Array(repeating: nil, count: Self.slotsPerDay)
    }

    func labels(for day: Date) -> [String?] {
        let key = dayKey(for: day)
        if let log = logs[key], let labels = log.labels, labels.count == Self.slotsPerDay {
            return labels
        }
        return Array(repeating: nil, count: Self.slotsPerDay)
    }

    func setProject(_ projectId: UUID?, for range: ClosedRange<Int>, on day: Date) {
        setEntry(projectId, label: nil, for: range, on: day)
    }

    func setEntry(_ projectId: UUID?, label: String?, for range: ClosedRange<Int>, on day: Date) {
        let key = dayKey(for: day)
        var log = logs[key] ?? DayLog(
            slots: Array(repeating: nil, count: Self.slotsPerDay),
            labels: Array(repeating: nil, count: Self.slotsPerDay)
        )
        if log.slots.count != Self.slotsPerDay {
            log.slots = Array(repeating: nil, count: Self.slotsPerDay)
        }

        var labels = log.labels ?? Array(repeating: nil, count: Self.slotsPerDay)
        if labels.count != Self.slotsPerDay {
            labels = Array(repeating: nil, count: Self.slotsPerDay)
        }

        let trimmed = label?.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedLabel = (trimmed?.isEmpty ?? true) ? nil : trimmed

        for index in range {
            guard index >= 0 && index < Self.slotsPerDay else { continue }
            log.slots[index] = projectId
            labels[index] = projectId == nil ? nil : normalizedLabel
        }
        log.labels = labels
        logs[key] = log
        if let projectId {
            lastSelectedProjectId = projectId
        }
    }

    func project(for id: UUID?) -> Project? {
        guard let id else { return nil }
        return projects.first { $0.id == id }
    }

    func addProject() {
        let color = Self.palette[projects.count % Self.palette.count]
        let project = Project(name: "New Project", colorHex: color)
        projects.append(project)
    }

    func removeProjects(at offsets: IndexSet) {
        projects.remove(atOffsets: offsets)
    }

    func dayTotals(for day: Date) -> [UUID: Int] {
        var totals: [UUID: Int] = [:]
        for slot in slots(for: day) {
            guard let id = slot else { continue }
            totals[id, default: 0] += Self.slotMinutes
        }
        return totals
    }

    func dayTotalMinutes(for day: Date) -> Int {
        dayTotals(for: day).values.reduce(0, +)
    }

    func monthTotals(for monthDate: Date) -> [UUID: Int] {
        var totals: [UUID: Int] = [:]
        for day in daysInMonth(containing: monthDate) {
            for slot in slots(for: day) {
                guard let id = slot else { continue }
                totals[id, default: 0] += Self.slotMinutes
            }
        }
        return totals
    }

    func monthTotalMinutes(for monthDate: Date, projectId: UUID?) -> Int {
        let totals = monthTotals(for: monthDate)
        if let projectId {
            return totals[projectId] ?? 0
        }
        return totals.values.reduce(0, +)
    }

    func totalMinutes(from startDate: Date, to endDate: Date, projectId: UUID?) -> Int {
        entries(from: startDate, to: endDate, filterProjectId: projectId).reduce(0) { total, entry in
            total + (entry.endIndex - entry.startIndex + 1) * Self.slotMinutes
        }
    }

    func dailyTotals(for monthDate: Date, projectId: UUID) -> [(Date, Int)] {
        daysInMonth(containing: monthDate).compactMap { day in
            let total = slots(for: day).reduce(0) { result, slot in
                result + (slot == projectId ? Self.slotMinutes : 0)
            }
            return total > 0 ? (day, total) : nil
        }
    }

    func entries(for day: Date, filterProjectId: UUID?) -> [TimeEntry] {
        let daySlots = slots(for: day)
        let dayLabels = labels(for: day)
        var entries: [TimeEntry] = []
        var currentId: UUID?
        var currentLabel: String?
        var currentStart: Int?

        func closeEntry(at endIndex: Int) {
            guard let start = currentStart, let id = currentId else { return }
            entries.append(TimeEntry(day: day, startIndex: start, endIndex: endIndex, projectId: id, label: currentLabel))
            currentStart = nil
            currentId = nil
            currentLabel = nil
        }

        for index in 0..<Self.slotsPerDay {
            let slotId = daySlots[index]
            let label = dayLabels[index]
            let matchesFilter = filterProjectId == nil || slotId == filterProjectId

            guard let slotId, matchesFilter else {
                if currentStart != nil {
                    closeEntry(at: index - 1)
                }
                continue
            }

            if currentStart == nil {
                currentStart = index
                currentId = slotId
                currentLabel = label
                continue
            }

            if slotId != currentId || label != currentLabel {
                closeEntry(at: index - 1)
                currentStart = index
                currentId = slotId
                currentLabel = label
            }
        }

        if currentStart != nil {
            closeEntry(at: Self.slotsPerDay - 1)
        }

        return entries
    }

    func entries(from startDate: Date, to endDate: Date, filterProjectId: UUID?) -> [TimeEntry] {
        daysBetween(startDate, endDate).flatMap { day in
            entries(for: day, filterProjectId: filterProjectId)
        }
    }

    func timesheetCSV(from startDate: Date, to endDate: Date, filterProjectId: UUID?) -> String {
        var lines = ["Date,Start,End,Project,Label,Hours,Rate,Amount"]
        for entry in entries(from: startDate, to: endDate, filterProjectId: filterProjectId) {
            let project = project(for: entry.projectId)
            let projectName = project?.name ?? "Unknown"
            let label = escapeCSV(entry.label ?? "")
            let start = Self.timeString(for: entry.startIndex)
            let end = Self.timeString(for: entry.endIndex + 1)
            let minutes = (entry.endIndex - entry.startIndex + 1) * Self.slotMinutes
            let hours = Double(minutes) / 60.0
            let hoursText = String(format: "%.2f", hours)
            let rate = project?.rate ?? 0
            let rateText = String(format: "%.2f", rate)
            let amountText = String(format: "%.2f", hours * rate)
            let dateText = Self.dayFormatter.string(from: entry.day)
            lines.append("\"\(dateText)\",\"\(start)\",\"\(end)\",\"\(escapeCSV(projectName))\",\"\(label)\",\"\(hoursText)\",\"\(rateText)\",\"\(amountText)\"")
        }
        return lines.joined(separator: "\n")
    }

    func seedSampleData(referenceDate: Date) {
        let baseDay = calendar.startOfDay(for: referenceDate)

        let sampleProjects = [
            Project(name: "Acme Mobile", colorHex: Self.palette[0], rate: 120, vatType: .included),
            Project(name: "Nimbus", colorHex: Self.palette[1], rate: 95, vatType: .excluded),
            Project(name: "Studio Ops", colorHex: Self.palette[2], rate: 80, vatType: .none),
            Project(name: "Admin", colorHex: Self.palette[3], rate: 60, vatType: .none)
        ]

        projects = sampleProjects
        logs = [:]

        func slotIndex(hour: Int, minute: Int) -> Int {
            let totalMinutes = hour * 60 + minute
            return max(0, min(Self.slotsPerDay - 1, totalMinutes / Self.slotMinutes))
        }

        func addEntry(
            _ project: Project,
            label: String?,
            dayOffset: Int,
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int
        ) {
            let day = calendar.date(byAdding: .day, value: dayOffset, to: baseDay) ?? baseDay
            let start = slotIndex(hour: startHour, minute: startMinute)
            let end = slotIndex(hour: endHour, minute: endMinute)
            let rangeEnd = max(start, end - 1)
            setEntry(project.id, label: label, for: start...rangeEnd, on: day)
        }

        if let first = sampleProjects.first,
           sampleProjects.count >= 4 {
            addEntry(first, label: "Sprint review", dayOffset: 0, startHour: 8, startMinute: 0, endHour: 10, endMinute: 30)
            addEntry(sampleProjects[1], label: "Wireframes", dayOffset: 0, startHour: 10, startMinute: 30, endHour: 12, endMinute: 0)
            addEntry(first, label: "Build", dayOffset: 0, startHour: 13, startMinute: 0, endHour: 15, endMinute: 30)
            addEntry(sampleProjects[2], label: "Ops review", dayOffset: 0, startHour: 15, startMinute: 30, endHour: 16, endMinute: 30)
            addEntry(sampleProjects[3], label: "Admin", dayOffset: 0, startHour: 16, startMinute: 30, endHour: 17, endMinute: 30)
        }

        let offsets = [-1, -2, -3, -5, -7, -9, -11, -13]
        for (index, offset) in offsets.enumerated() {
            let primary = sampleProjects[index % sampleProjects.count]
            let secondary = sampleProjects[(index + 1) % sampleProjects.count]
            addEntry(primary, label: nil, dayOffset: offset, startHour: 9, startMinute: 0, endHour: 12, endMinute: 0)
            addEntry(secondary, label: "Client sync", dayOffset: offset, startHour: 13, startMinute: 0, endHour: 16, endMinute: 0)
        }
    }

    func addInvoiceRecord(clientId: String?, clientKey: String, date: Date, isSandbox: Bool) {
        let trimmedKey = clientKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedKey.isEmpty else { return }
        let trimmedId = clientId?.trimmingCharacters(in: .whitespacesAndNewlines)
        let record = InvoiceRecord(
            clientKey: trimmedKey,
            clientId: (trimmedId?.isEmpty ?? true) ? nil : trimmedId,
            date: date,
            isSandbox: isSandbox
        )
        invoiceRecords.append(record)
    }

    func invoiceCount(clientKey: String, clientId: String?, date: Date, isSandbox: Bool) -> Int {
        let monthKey = invoiceMonthKey(for: date)
        let normalizedKey = clientKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let normalizedId = clientId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        return invoiceRecords.filter { record in
            guard record.isSandbox == isSandbox else { return false }
            guard invoiceMonthKey(for: record.date) == monthKey else { return false }
            if let normalizedId, !normalizedId.isEmpty,
               let recordId = record.clientId?.lowercased(), !recordId.isEmpty {
                return recordId == normalizedId
            }
            return record.clientKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == normalizedKey
        }.count
    }

    private func saveProjects() {
        guard isLoaded else { return }
        guard let data = try? encoder.encode(projects) else { return }
        UserDefaults.standard.set(data, forKey: projectsKey)
        recordChange()
    }

    private func saveLogs() {
        guard isLoaded else { return }
        guard let data = try? encoder.encode(logs) else { return }
        UserDefaults.standard.set(data, forKey: logsKey)
        recordChange()
    }

    private func saveInvoiceRecords() {
        guard isLoaded else { return }
        guard let data = try? encoder.encode(invoiceRecords) else { return }
        UserDefaults.standard.set(data, forKey: invoiceRecordsKey)
        recordChange()
    }

    private func saveGreenInvoiceApiKey() {
        guard isLoaded else { return }
        UserDefaults.standard.set(greenInvoiceApiKey, forKey: greenInvoiceKey)
        recordChange()
    }

    private func saveGreenInvoiceApiSecret() {
        guard isLoaded else { return }
        UserDefaults.standard.set(greenInvoiceApiSecret, forKey: greenInvoiceSecretKey)
        recordChange()
    }

    private func saveGreenInvoiceTestMode() {
        guard isLoaded else { return }
        UserDefaults.standard.set(greenInvoiceTestMode, forKey: greenInvoiceTestModeKey)
        recordChange()
    }

    private func saveInvoiceLogoData() {
        guard isLoaded else { return }
        if let data = invoiceLogoData {
            UserDefaults.standard.set(data, forKey: invoiceLogoKey)
        } else {
            UserDefaults.standard.removeObject(forKey: invoiceLogoKey)
        }
        recordChange()
    }

    private func saveInvoiceSignatureData() {
        guard isLoaded else { return }
        if let data = invoiceSignatureData {
            UserDefaults.standard.set(data, forKey: invoiceSignatureKey)
        } else {
            UserDefaults.standard.removeObject(forKey: invoiceSignatureKey)
        }
        recordChange()
    }

    private func saveInvoiceStartNumber() {
        guard isLoaded else { return }
        let normalized = max(invoiceStartNumber, 1)
        if invoiceStartNumber != normalized {
            invoiceStartNumber = normalized
            return
        }
        UserDefaults.standard.set(invoiceStartNumber, forKey: invoiceStartNumberKey)
        if let nextNumber = invoiceNextNumber, nextNumber < invoiceStartNumber {
            invoiceNextNumber = invoiceStartNumber
        }
        recordChange()
    }

    private func saveInvoiceNextNumber() {
        guard isLoaded else { return }
        if let invoiceNextNumber {
            UserDefaults.standard.set(invoiceNextNumber, forKey: invoiceNextNumberKey)
        } else {
            UserDefaults.standard.removeObject(forKey: invoiceNextNumberKey)
        }
        recordChange()
    }

    private func saveLastSelectedProjectId() {
        guard isLoaded else { return }
        if let lastSelectedProjectId {
            UserDefaults.standard.set(lastSelectedProjectId.uuidString, forKey: lastSelectedProjectKey)
        } else {
            UserDefaults.standard.removeObject(forKey: lastSelectedProjectKey)
        }
        recordChange()
    }

    private func saveLastCloudSync() {
        guard isLoaded else { return }
        if let lastCloudSync {
            UserDefaults.standard.set(lastCloudSync.timeIntervalSince1970, forKey: lastCloudSyncKey)
        } else {
            UserDefaults.standard.removeObject(forKey: lastCloudSyncKey)
        }
    }

    private func recordChange() {
        guard isLoaded, !isApplyingCloudUpdate else { return }
        lastUpdated = Date()
        saveLastUpdated()
        scheduleCloudSave()
    }

    private func saveLastUpdated() {
        guard isLoaded else { return }
        UserDefaults.standard.set(lastUpdated.timeIntervalSince1970, forKey: lastUpdatedKey)
    }

    private func scheduleCloudSave() {
        guard cloudFileURL != nil else { return }
        cloudSaveWorkItem?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            self?.saveSnapshotToCloud()
        }
        cloudSaveWorkItem = workItem
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 0.5, execute: workItem)
    }

    private func makeSnapshot() -> TimeStoreSnapshot {
        TimeStoreSnapshot(
            lastUpdated: lastUpdated,
            projects: projects,
            logs: logs,
            invoiceRecords: invoiceRecords,
            greenInvoiceApiKey: greenInvoiceApiKey,
            greenInvoiceApiSecret: greenInvoiceApiSecret,
            greenInvoiceTestMode: greenInvoiceTestMode,
            invoiceLogoData: invoiceLogoData,
            invoiceSignatureData: invoiceSignatureData,
            invoiceStartNumber: invoiceStartNumber,
            invoiceNextNumber: invoiceNextNumber,
            lastSelectedProjectId: lastSelectedProjectId
        )
    }

    private func saveSnapshotToCloud() {
        guard let url = cloudFileURL else { return }
        let snapshot = makeSnapshot()
        guard let data = try? encoder.encode(snapshot) else { return }

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var error: NSError?
        coordinator.coordinate(writingItemAt: url, options: .forReplacing, error: &error) { coordinatedURL in
            do {
                try data.write(to: coordinatedURL, options: .atomic)
                DispatchQueue.main.async { [weak self] in
                    self?.lastCloudSync = Date()
                }
            } catch {
                return
            }
        }
    }

    private func loadFromCloudIfAvailable() {
        guard let url = cloudFileURL else { return }
        guard FileManager.default.fileExists(atPath: url.path) else {
            if hasLocalData {
                scheduleCloudSave()
            }
            return
        }
        loadSnapshotIfAvailable(from: url)
    }

    private func loadSnapshotIfAvailable(from url: URL) {
        if let values = try? url.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey]),
           let status = values.ubiquitousItemDownloadingStatus,
           status != URLUbiquitousItemDownloadingStatus.current {
            try? FileManager.default.startDownloadingUbiquitousItem(at: url)
            return
        }

        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self,
                  let snapshot = self.readSnapshot(from: url) else { return }
            DispatchQueue.main.async {
                self.applySnapshotIfNewer(snapshot)
            }
        }
    }

    private func readSnapshot(from url: URL) -> TimeStoreSnapshot? {
        var data: Data?
        let coordinator = NSFileCoordinator(filePresenter: nil)
        var error: NSError?
        coordinator.coordinate(readingItemAt: url, options: [], error: &error) { coordinatedURL in
            data = try? Data(contentsOf: coordinatedURL)
        }
        guard let data else { return nil }
        return try? decoder.decode(TimeStoreSnapshot.self, from: data)
    }

    private func applySnapshotIfNewer(_ snapshot: TimeStoreSnapshot) {
        guard snapshot.lastUpdated > lastUpdated else { return }
        isApplyingCloudUpdate = true
        projects = snapshot.projects
        logs = snapshot.logs
        invoiceRecords = snapshot.invoiceRecords
        greenInvoiceApiKey = snapshot.greenInvoiceApiKey
        greenInvoiceApiSecret = snapshot.greenInvoiceApiSecret
        greenInvoiceTestMode = snapshot.greenInvoiceTestMode
        invoiceLogoData = snapshot.invoiceLogoData
        invoiceSignatureData = snapshot.invoiceSignatureData
        invoiceStartNumber = snapshot.invoiceStartNumber
        invoiceNextNumber = snapshot.invoiceNextNumber
        lastSelectedProjectId = snapshot.lastSelectedProjectId
        lastUpdated = snapshot.lastUpdated
        saveLastUpdated()
        isApplyingCloudUpdate = false
        lastCloudSync = Date()
    }

    private func startCloudQuery() {
        guard cloudFileURL != nil else { return }
        let query = NSMetadataQuery()
        query.searchScopes = [NSMetadataQueryUbiquitousDocumentsScope]
        query.predicate = NSPredicate(format: "%K == %@", NSMetadataItemFSNameKey, cloudFileName)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleCloudQueryUpdate(_:)),
            name: .NSMetadataQueryDidFinishGathering,
            object: query
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleCloudQueryUpdate(_:)),
            name: .NSMetadataQueryDidUpdate,
            object: query
        )
        cloudQuery = query
        query.start()
    }

    @objc private func handleCloudQueryUpdate(_ notification: Notification) {
        guard let query = notification.object as? NSMetadataQuery else { return }
        query.disableUpdates()
        defer { query.enableUpdates() }

        guard let item = query.results.first as? NSMetadataItem,
              let url = item.value(forAttribute: NSMetadataItemURLKey) as? URL else { return }
        loadSnapshotIfAvailable(from: url)
    }

    private func load() {
        if let data = UserDefaults.standard.data(forKey: projectsKey),
           let saved = try? decoder.decode([Project].self, from: data) {
            projects = saved
        }

        if let data = UserDefaults.standard.data(forKey: logsKey),
           let saved = try? decoder.decode([String: DayLog].self, from: data) {
            logs = saved
        }

        if let data = UserDefaults.standard.data(forKey: invoiceRecordsKey),
           let saved = try? decoder.decode([InvoiceRecord].self, from: data) {
            invoiceRecords = saved
        }

        if let saved = UserDefaults.standard.string(forKey: greenInvoiceKey) {
            greenInvoiceApiKey = saved
        }

        if let saved = UserDefaults.standard.string(forKey: greenInvoiceSecretKey) {
            greenInvoiceApiSecret = saved
        }

        greenInvoiceTestMode = UserDefaults.standard.bool(forKey: greenInvoiceTestModeKey)

        if let data = UserDefaults.standard.data(forKey: invoiceLogoKey) {
            invoiceLogoData = data
        }
        if let data = UserDefaults.standard.data(forKey: invoiceSignatureKey) {
            invoiceSignatureData = data
        }
        let storedStart = UserDefaults.standard.integer(forKey: invoiceStartNumberKey)
        invoiceStartNumber = storedStart > 0 ? storedStart : 1
        if UserDefaults.standard.object(forKey: invoiceNextNumberKey) != nil {
            invoiceNextNumber = UserDefaults.standard.integer(forKey: invoiceNextNumberKey)
        }
        if let next = invoiceNextNumber, next < invoiceStartNumber {
            invoiceNextNumber = invoiceStartNumber
        }

        if let stored = UserDefaults.standard.string(forKey: lastSelectedProjectKey),
           let parsed = UUID(uuidString: stored) {
            lastSelectedProjectId = parsed
        }

        if UserDefaults.standard.object(forKey: lastCloudSyncKey) != nil {
            let timestamp = UserDefaults.standard.double(forKey: lastCloudSyncKey)
            lastCloudSync = Date(timeIntervalSince1970: timestamp)
        }

        if UserDefaults.standard.object(forKey: lastUpdatedKey) != nil {
            let timestamp = UserDefaults.standard.double(forKey: lastUpdatedKey)
            lastUpdated = Date(timeIntervalSince1970: timestamp)
        } else if hasLocalData {
            lastUpdated = Date()
            UserDefaults.standard.set(lastUpdated.timeIntervalSince1970, forKey: lastUpdatedKey)
        }
    }

    func nextInvoiceNumber() -> Int {
        let start = max(invoiceStartNumber, 1)
        if let next = invoiceNextNumber, next >= start {
            return next
        }
        return start
    }

    func advanceInvoiceNumber(from current: Int) {
        let start = max(invoiceStartNumber, 1)
        let next = max(current + 1, start)
        invoiceNextNumber = next
    }

    private func invoiceMonthKey(for date: Date) -> String {
        let components = calendar.dateComponents([.year, .month], from: date)
        let year = components.year ?? 0
        let month = components.month ?? 0
        return String(format: "%04d-%02d", year, month)
    }

    private func dayKey(for date: Date) -> String {
        let start = calendar.startOfDay(for: date)
        return Self.dayFormatter.string(from: start)
    }

    func monthRange(for date: Date) -> (start: Date, end: Date) {
        let components = calendar.dateComponents([.year, .month], from: date)
        guard let start = calendar.date(from: components),
              let range = calendar.range(of: .day, in: .month, for: start),
              let end = calendar.date(byAdding: .day, value: range.count - 1, to: start) else {
            return (date, date)
        }
        return (start, end)
    }

    private func daysInMonth(containing date: Date) -> [Date] {
        let components = calendar.dateComponents([.year, .month], from: date)
        guard let start = calendar.date(from: components),
              let range = calendar.range(of: .day, in: .month, for: start) else {
            return []
        }
        return range.compactMap { calendar.date(byAdding: .day, value: $0 - 1, to: start) }
    }

    private func daysBetween(_ startDate: Date, _ endDate: Date) -> [Date] {
        let start = calendar.startOfDay(for: startDate)
        let end = calendar.startOfDay(for: endDate)
        guard start <= end else { return [] }
        var days: [Date] = []
        var current = start
        while current <= end {
            days.append(current)
            guard let next = calendar.date(byAdding: .day, value: 1, to: current) else { break }
            current = next
        }
        return days
    }

    private func escapeCSV(_ value: String) -> String {
        value.replacingOccurrences(of: "\"", with: "\"\"")
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let syncFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.autoupdatingCurrent
        formatter.timeZone = TimeZone.current
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()

    static func timeString(for slotIndex: Int) -> String {
        let clamped = max(0, min(slotIndex, slotsPerDay))
        let hour = clamped / 4
        let minute = (clamped % 4) * slotMinutes
        return String(format: "%02d:%02d", hour, minute)
    }

    private static let palette: [String] = [
        "#0F9D58",
        "#F4B400",
        "#DB4437",
        "#4285F4",
        "#AB47BC",
        "#00ACC1",
        "#FF7043",
        "#9E9D24"
    ]
}

final class AppLock: ObservableObject {
    @Published var isUnlocked = false
    private var isAuthenticating = false
    private var hasPendingRequest = false

    func requestUnlock() {
        guard !isUnlocked, !isAuthenticating, !hasPendingRequest else { return }
        hasPendingRequest = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
            guard let self else { return }
            self.hasPendingRequest = false
            self.unlock()
        }
    }

    func unlock() {
        guard !isUnlocked, !isAuthenticating else { return }
        guard UIApplication.shared.applicationState == .active else {
            requestUnlock()
            return
        }
        isAuthenticating = true
        let context = LAContext()
        var error: NSError?
        let policy = LAPolicy.deviceOwnerAuthentication
        guard context.canEvaluatePolicy(policy, error: &error) else {
            DispatchQueue.main.async {
                self.isAuthenticating = false
                self.isUnlocked = true
            }
            return
        }

        context.evaluatePolicy(policy, localizedReason: "Unlock MyTimetrack") { success, authError in
            DispatchQueue.main.async {
                self.isAuthenticating = false
                self.isUnlocked = success
                if !success, let laError = authError as? LAError,
                   laError.code == .appCancel || laError.code == .systemCancel {
                    self.requestUnlock()
                }
            }
        }
    }

    func lock() {
        isUnlocked = false
    }
}

extension Color {
    init(hex: String) {
        let trimmed = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: trimmed).scanHexInt64(&int)
        let r, g, b: UInt64
        if trimmed.count == 3 {
            (r, g, b) = ((int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        } else {
            (r, g, b) = (int >> 16, int >> 8 & 0xFF, int & 0xFF)
        }
        self.init(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: 1)
    }

    func toHex() -> String {
        let uiColor = UIColor(self)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        guard uiColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return "#000000"
        }
        let r = Int(round(red * 255))
        let g = Int(round(green * 255))
        let b = Int(round(blue * 255))
        return String(format: "#%02X%02X%02X", r, g, b)
    }
}
