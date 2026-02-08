import SwiftUI
import UIKit
import PhotosUI

struct ContentView: View {
    @EnvironmentObject private var store: TimeStore
    @EnvironmentObject private var appLock: AppLock
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @AppStorage("hasSeenWalkthrough") private var hasSeenWalkthrough = false
    @AppStorage("debugRunOnboarding") private var debugRunOnboarding = false
    @State private var hasConsumedEnvOnboarding = false
    @State private var showOnboarding = false
    @State private var isSchedulingOnboarding = false

    var body: some View {
        ZStack {
            if appLock.isUnlocked {
                DayView()
            } else {
                LockView()
            }
        }
        .onAppear {
            appLock.requestUnlock()
            maybeStartOnboarding()
        }
        .onChange(of: appLock.isUnlocked) { _ in
            maybeStartOnboarding()
        }
        .onChange(of: debugRunOnboarding) { _ in
            maybeStartOnboarding()
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                appLock.requestUnlock()
                maybeStartOnboarding()
            case .background:
                appLock.lock()
            default:
                break
            }
        }
        .fullScreenCover(isPresented: $showOnboarding) {
            OnboardingFlowView {
                hasSeenOnboarding = true
                debugRunOnboarding = false
                showOnboarding = false
            }
            .interactiveDismissDisabled(true)
        }
    }

    private func maybeStartOnboarding() {
        guard !showOnboarding, !isSchedulingOnboarding else { return }

        let envForceOnboarding = ProcessInfo.processInfo.environment["DEBUG_RUN_ONBOARDING"] == "1"
        let forceOnboarding = debugRunOnboarding || (envForceOnboarding && !hasConsumedEnvOnboarding)

        if !forceOnboarding {
            // Avoid showing onboarding to users who already have data (e.g. after an update).
            if !hasSeenOnboarding, !store.projects.isEmpty {
                hasSeenOnboarding = true
                hasSeenWalkthrough = true
            }
        }

        let shouldShowNormally = !hasSeenOnboarding && store.projects.isEmpty
        if appLock.isUnlocked, (forceOnboarding || shouldShowNormally) {
            isSchedulingOnboarding = true
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 180_000_000)
                isSchedulingOnboarding = false
                guard appLock.isUnlocked else { return }
                guard !showOnboarding else { return }
                showOnboarding = true
                if debugRunOnboarding {
                    debugRunOnboarding = false
                }
                if envForceOnboarding {
                    hasConsumedEnvOnboarding = true
                }
            }
        }
    }
}

struct LockView: View {
    @EnvironmentObject private var appLock: AppLock

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "faceid")
                .font(.system(size: 52))
            Text("Unlock MyTimetrack")
                .font(.headline)
            Button("Unlock") {
                appLock.unlock()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
        .onAppear {
            appLock.requestUnlock()
        }
    }
}

struct OnboardingFlowView: View {
    let onFinish: () -> Void

    @State private var stepIndex: Int = 0

    private let steps: [OnboardingStep] = [
        OnboardingStep(
            id: 0,
            title: "Welcome to MyTimetrack",
            message: "Track your day in 15-minute slots. Drag on the timeline to log work fast.",
            preview: .day
        ),
        OnboardingStep(
            id: 1,
            title: "Create Your Projects",
            message: "Add a color and hourly rate so totals, timesheets, and invoices are automatic.",
            preview: .settings
        ),
        OnboardingStep(
            id: 2,
            title: "Review And Edit",
            message: "Use the list view to filter by date or project, and fine-tune labels anytime.",
            preview: .list
        ),
        OnboardingStep(
            id: 3,
            title: "Export And Invoice",
            message: "Get monthly totals, export a timesheet, and send a proforma PDF invoice when it's time to bill.",
            preview: .summary
        )
    ]

    var body: some View {
        ZStack {
            OnboardingBackground()
                .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Text("MyTimetrack")
                        .font(.system(.title3, design: .rounded).weight(.semibold))
                        .foregroundStyle(OnboardingTheme.titleColor)

                    Spacer()

                    Button("Skip") {
                        onFinish()
                    }
                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                    .foregroundStyle(OnboardingTheme.accent)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 8)

                TabView(selection: $stepIndex) {
                    ForEach(steps) { step in
                        OnboardingPage(step: step)
                            .tag(step.id)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .padding(.horizontal, 20)

                HStack(spacing: 12) {
                    OnboardingDots(count: steps.count, index: stepIndex)
                    Spacer()
                    Button {
                        advance()
                    } label: {
                        Text(stepIndex == steps.count - 1 ? "Get Started" : "Continue")
                            .frame(minWidth: 120)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(OnboardingTheme.accent)
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 22)
            }
        }
    }

    private func advance() {
        if stepIndex >= steps.count - 1 {
            onFinish()
            return
        }
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            stepIndex += 1
        }
    }
}

private struct OnboardingStep: Identifiable {
    let id: Int
    let title: String
    let message: String
    let preview: OnboardingPreviewKind
}

private enum OnboardingPreviewKind {
    case day
    case settings
    case list
    case summary

    var assetName: String {
        switch self {
        case .day:
            return "OnboardingDay"
        case .settings:
            return "OnboardingSettings"
        case .list:
            return "OnboardingList"
        case .summary:
            return "OnboardingSummary"
        }
    }
}

private enum OnboardingTheme {
    static let backgroundTop = Color(red: 0.93, green: 0.98, blue: 0.99)
    static let backgroundBottom = Color(red: 1.0, green: 0.96, blue: 0.94)
    static let accent = Color(red: 0.25, green: 0.62, blue: 0.60)
    static let titleColor = Color(red: 0.12, green: 0.18, blue: 0.22)
    static let subtitleColor = Color(red: 0.12, green: 0.18, blue: 0.22).opacity(0.72)
    static let card = Color.white.opacity(0.88)
}

private struct OnboardingBackground: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [OnboardingTheme.backgroundTop, OnboardingTheme.backgroundBottom],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            Circle()
                .fill(OnboardingTheme.accent.opacity(0.20))
                .frame(width: 360, height: 360)
                .blur(radius: 26)
                .offset(x: -170, y: -240)

            Circle()
                .fill(Color(red: 0.98, green: 0.63, blue: 0.55).opacity(0.18))
                .frame(width: 320, height: 320)
                .blur(radius: 28)
                .offset(x: 180, y: 220)
        }
    }
}

private struct OnboardingPage: View {
    let step: OnboardingStep

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Spacer(minLength: 8)

            Text(step.title)
                .font(.system(.largeTitle, design: .rounded).weight(.bold))
                .foregroundStyle(OnboardingTheme.titleColor)
                .minimumScaleFactor(0.8)

            Text(step.message)
                .font(.system(.body, design: .rounded))
                .foregroundStyle(OnboardingTheme.subtitleColor)
                .fixedSize(horizontal: false, vertical: true)

            OnboardingPreviewCard(kind: step.preview)
                .frame(maxWidth: .infinity)
                .frame(height: 360)

            Spacer(minLength: 0)
        }
        .padding(.bottom, 8)
    }
}

private struct OnboardingDots: View {
    let count: Int
    let index: Int

    var body: some View {
        HStack(spacing: 7) {
            ForEach(0..<count, id: \.self) { i in
                Capsule(style: .continuous)
                    .fill(i == index ? OnboardingTheme.titleColor.opacity(0.35) : Color.black.opacity(0.10))
                    .frame(width: i == index ? 18 : 7, height: 7)
                    .animation(.easeInOut(duration: 0.2), value: index)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Onboarding step \(index + 1) of \(count)")
    }
}

private struct OnboardingPreviewCard: View {
    let kind: OnboardingPreviewKind

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(OnboardingTheme.card)

            OnboardingPreview(kind: kind)
                .padding(18)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            GeometryReader { proxy in
                // Half-shaded overlay for depth and to mimic a "real" screenshot.
                let overlayHeight = proxy.size.height * 0.55
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.16)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: overlayHeight)
                .frame(maxHeight: .infinity, alignment: .bottom)
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            }
            .allowsHitTesting(false)

            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(Color.white.opacity(0.55), lineWidth: 1)
        }
        .shadow(color: Color.black.opacity(0.12), radius: 18, x: 0, y: 12)
    }
}

private struct OnboardingPreview: View {
    let kind: OnboardingPreviewKind

    var body: some View {
        OnboardingScreenshotPreview(imageName: kind.assetName)
    }
}

private struct OnboardingScreenshotPreview: View {
    let imageName: String

    var body: some View {
        GeometryReader { proxy in
            Image(imageName)
                .resizable()
                .scaledToFill()
                .frame(width: proxy.size.width, height: proxy.size.height)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .stroke(Color.black.opacity(0.10), lineWidth: 1)
                }
        }
    }
}

struct DayView: View {
    @EnvironmentObject private var store: TimeStore

    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @AppStorage("hasSeenWalkthrough") private var hasSeenWalkthrough = false
    @AppStorage("debugRunWalkthrough") private var debugRunWalkthrough = false
    @State private var hasConsumedEnvWalkthrough = false

    @State private var day: Date
    @State private var selection: Selection?
    @State private var showSettings = false
    @State private var showSummary = false
    @State private var showList = false
    @State private var showDatePicker = false
    @State private var showNoProjectsAlert = false
    @State private var showWalkthrough = false

    private let calendar = Calendar.current
    private let daySwipeThreshold: CGFloat = 80

    init(initialDate: Date = Date()) {
        _day = State(initialValue: initialDate)
    }


    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            TimeGridView(day: day) { range in
                guard !store.projects.isEmpty else {
                    showNoProjectsAlert = true
                    return
                }
                selection = Selection(range: range)
            }
            .anchorPreference(key: WalkthroughAnchorPreferenceKey.self, value: .bounds) { anchor in
                [.grid: anchor]
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView()
        }
        .sheet(isPresented: $showSummary) {
            MonthSummaryView(monthDate: summaryMonthDate)
        }
        .fullScreenCover(isPresented: $showList) {
            TimeListView(referenceDate: summaryMonthDate)
        }
        .sheet(isPresented: $showDatePicker) {
            NavigationStack {
                DatePicker("Select Date", selection: $day, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding()
                    .navigationTitle("Jump to Date")
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Done") {
                                showDatePicker = false
                            }
                        }
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Today") {
                                day = Date()
                            }
                        }
                    }
            }
        }
        .sheet(item: $selection) { selection in
            let defaults = selectionDefaults(for: selection.range)
            EntryEditorView(
                projects: store.projects,
                existingSlots: store.slots(for: day),
                initialRange: selection.range,
                initialProjectId: defaults.projectId,
                initialLabel: defaults.label
            ) { projectId, range, label in
                store.setEntry(projectId, label: label, for: range, on: day)
            }
        }
        .alert("No projects yet", isPresented: $showNoProjectsAlert) {
            Button("Open Settings") {
                showSettings = true
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Add your active projects in Settings before tracking time.")
        }
        .safeAreaInset(edge: .bottom) {
            LegendView(
                projects: store.projects,
                totals: store.dayTotals(for: day),
                totalMinutes: store.dayTotalMinutes(for: day)
            )
        }
        .contentShape(Rectangle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 20)
                .onEnded { value in
                    let horizontal = value.translation.width
                    let vertical = value.translation.height
                    guard abs(horizontal) > daySwipeThreshold,
                          abs(horizontal) > abs(vertical) else {
                        return
                    }
                    if horizontal < 0 {
                        nextDay()
                    } else {
                        previousDay()
                    }
                }
        )
        .task {
            scheduleWalkthroughIfNeeded()
        }
        .onChange(of: hasSeenOnboarding) { _ in
            scheduleWalkthroughIfNeeded()
        }
        .onChange(of: debugRunWalkthrough) { _ in
            scheduleWalkthroughIfNeeded()
        }
        .overlayPreferenceValue(WalkthroughAnchorPreferenceKey.self) { anchors in
            WalkthroughOverlay(
                isPresented: $showWalkthrough,
                anchors: anchors,
                onFinish: {
                    hasSeenWalkthrough = true
                    debugRunWalkthrough = false
                    showWalkthrough = false
                }
            )
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            HStack(spacing: 12) {
                Button(action: previousDay) {
                    Image(systemName: "chevron.left")
                }
                Button(action: nextDay) {
                    Image(systemName: "chevron.right")
                }
            }
            .frame(width: 80, alignment: .leading)

            Spacer()

            Button(action: { showDatePicker = true }) {
                VStack(spacing: 2) {
                    Text(dayTitle)
                        .font(.headline)
                    Text(daySubtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)

            Spacer()

            HStack(spacing: 12) {
                Button(action: { showList = true }) {
                    Image(systemName: "list.bullet")
                }
                Button(action: { showSummary = true }) {
                    Image(systemName: "chart.bar")
                }
                .anchorPreference(key: WalkthroughAnchorPreferenceKey.self, value: .bounds) { anchor in
                    [.summary: anchor]
                }
                Button(action: { showSettings = true }) {
                    Image(systemName: "gearshape")
                }
                .anchorPreference(key: WalkthroughAnchorPreferenceKey.self, value: .bounds) { anchor in
                    [.settings: anchor]
                }
            }
            .frame(width: 136, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var dayTitle: String {
        if calendar.isDateInToday(day) {
            return "Today"
        }
        return Self.titleFormatter.string(from: day)
    }

    private var daySubtitle: String {
        Self.subtitleFormatter.string(from: day)
    }

    private func previousDay() {
        guard let updated = calendar.date(byAdding: .day, value: -1, to: day) else { return }
        day = updated
    }

    private func nextDay() {
        guard let updated = calendar.date(byAdding: .day, value: 1, to: day) else { return }
        day = updated
    }

    private var summaryMonthDate: Date {
        let today = Date()
        let dayOfMonth = calendar.component(.day, from: today)
        if dayOfMonth < 10, let previous = calendar.date(byAdding: .month, value: -1, to: today) {
            return previous
        }
        return today
    }

    private func selectionDefaults(for range: ClosedRange<Int>) -> (projectId: UUID?, label: String?) {
        let slots = store.slots(for: day)
        let labels = store.labels(for: day)
        let indices = range.lowerBound...range.upperBound

        let slotValues = indices.map { slots[$0] }
        let labelValues = indices.map { labels[$0] }

        let uniqueProjects = Set(slotValues.compactMap { $0 })
        let hasNilSlots = slotValues.contains { $0 == nil }
        let projectId = (uniqueProjects.count == 1 && !hasNilSlots) ? uniqueProjects.first : nil

        let uniqueLabels = Set(labelValues.compactMap { $0 })
        let hasNilLabels = labelValues.contains { $0 == nil }
        let label = (uniqueLabels.count == 1 && !hasNilLabels) ? uniqueLabels.first : nil

        let lastProjectId = store.lastSelectedProjectId
        let fallbackProjectId = lastProjectId.flatMap { store.project(for: $0)?.id }

        return (projectId ?? fallbackProjectId, label)
    }

    private struct Selection: Identifiable {
        let id = UUID()
        let range: ClosedRange<Int>
    }

    private static let titleFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE"
        return formatter
    }()

    private static let subtitleFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        return formatter
    }()

    private func scheduleWalkthroughIfNeeded() {
        let envForceWalkthrough = ProcessInfo.processInfo.environment["DEBUG_RUN_WALKTHROUGH"] == "1"
        let forceWalkthrough = debugRunWalkthrough || (envForceWalkthrough && !hasConsumedEnvWalkthrough)

        guard forceWalkthrough || (hasSeenOnboarding && !hasSeenWalkthrough) else { return }
        guard !showSettings, !showSummary, !showList, selection == nil else { return }
        guard !showWalkthrough else { return }

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard forceWalkthrough || (hasSeenOnboarding && !hasSeenWalkthrough) else { return }
            guard !showSettings, !showSummary, !showList, selection == nil else { return }
            showWalkthrough = true
            if envForceWalkthrough {
                hasConsumedEnvWalkthrough = true
            }
        }
    }
}

private enum WalkthroughAnchorID: Hashable {
    case settings
    case grid
    case summary
}

private struct WalkthroughAnchorPreferenceKey: PreferenceKey {
    static var defaultValue: [WalkthroughAnchorID: Anchor<CGRect>] = [:]

    static func reduce(value: inout [WalkthroughAnchorID: Anchor<CGRect>], nextValue: () -> [WalkthroughAnchorID: Anchor<CGRect>]) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

private struct WalkthroughOverlay: View {
    @Binding var isPresented: Bool
    let anchors: [WalkthroughAnchorID: Anchor<CGRect>]
    let onFinish: () -> Void

    @State private var stepIndex: Int = 0

    var body: some View {
        if isPresented {
            GeometryReader { proxy in
                let step = steps[min(stepIndex, steps.count - 1)]
                let targetRect = step.target.flatMap { anchors[$0] }.map { proxy[$0] } ?? .zero
                let highlightRect = highlightRect(for: targetRect)

                ZStack(alignment: .bottom) {
                    WalkthroughCutoutShape(cutoutRect: highlightRect, cornerRadius: 18)
                        .fill(Color.black.opacity(0.56), style: FillStyle(eoFill: true))
                        .ignoresSafeArea()
                        .animation(.easeInOut(duration: 0.25), value: stepIndex)

                    if targetRect != .zero {
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(Color.white.opacity(0.88), lineWidth: 2)
                            .frame(width: highlightRect.width, height: highlightRect.height)
                            .position(x: highlightRect.midX, y: highlightRect.midY)
                            .shadow(color: Color.black.opacity(0.28), radius: 18, x: 0, y: 12)
                            .animation(.easeInOut(duration: 0.25), value: stepIndex)
                    }

                    WalkthroughCard(
                        title: step.title,
                        message: step.message,
                        stepIndex: stepIndex,
                        totalSteps: steps.count,
                        isLast: stepIndex == steps.count - 1,
                        onSkip: onFinish,
                        onNext: advance
                    )
                    .padding(16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .onAppear {
                stepIndex = 0
            }
            .onChange(of: isPresented) { presented in
                if presented {
                    stepIndex = 0
                }
            }
        }
    }

    private func advance() {
        if stepIndex >= steps.count - 1 {
            onFinish()
            return
        }
        withAnimation(.easeInOut(duration: 0.22)) {
            stepIndex += 1
        }
    }

    private func highlightRect(for targetRect: CGRect) -> CGRect {
        guard targetRect != .zero else { return .zero }

        var rect = targetRect.insetBy(dx: -10, dy: -10)
        let minSize: CGFloat = 56
        if rect.width < minSize {
            let delta = (minSize - rect.width) / 2
            rect = rect.insetBy(dx: -delta, dy: 0)
        }
        if rect.height < minSize {
            let delta = (minSize - rect.height) / 2
            rect = rect.insetBy(dx: 0, dy: -delta)
        }
        return rect
    }

    private struct WalkthroughStep {
        let target: WalkthroughAnchorID?
        let title: String
        let message: String
    }

    private var steps: [WalkthroughStep] {
        [
            WalkthroughStep(
                target: .settings,
                title: "Create a project",
                message: "Open Settings to add a project. Set a color and hourly rate so totals are automatic."
            ),
            WalkthroughStep(
                target: .grid,
                title: "Report time",
                message: "Drag on the timeline to select time blocks (snaps to 30 minutes). Release to pick a project and optional label."
            ),
            WalkthroughStep(
                target: .summary,
                title: "Produce an invoice",
                message: "Use Month Summary to review totals and send a proforma PDF invoice with a CSV timesheet."
            )
        ]
    }
}

private struct WalkthroughCard: View {
    let title: String
    let message: String
    let stepIndex: Int
    let totalSteps: Int
    let isLast: Bool
    let onSkip: () -> Void
    let onNext: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Quick tour")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(stepIndex + 1) / \(totalSteps)")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundStyle(.secondary)
            }

            Text(title)
                .font(.system(.title3, design: .rounded).weight(.bold))

            Text(message)
                .font(.system(.body, design: .rounded))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 10) {
                Button("Skip") {
                    onSkip()
                }
                .buttonStyle(.bordered)
                .tint(Color.black.opacity(0.08))

                Spacer()

                Button(isLast ? "Done" : "Next") {
                    onNext()
                }
                .buttonStyle(.borderedProminent)
                .tint(OnboardingTheme.accent)
            }
        }
        .padding(16)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(Color.white.opacity(0.45), lineWidth: 1)
        }
        .shadow(color: Color.black.opacity(0.22), radius: 26, x: 0, y: 16)
    }
}

private struct WalkthroughCutoutShape: Shape {
    let cutoutRect: CGRect
    let cornerRadius: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(rect)
        if cutoutRect != .zero {
            path.addPath(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).path(in: cutoutRect))
        }
        return path
    }
}

struct TimeGridView: View {
    let day: Date
    let onRangeSelected: (ClosedRange<Int>) -> Void

    @EnvironmentObject private var store: TimeStore

    @State private var now: Date = Date()
    @State private var dragStart: Int?
    @State private var dragCurrent: Int?
    @State private var movingSegment: TimeSegment?
    @State private var movingStartIndex: Int = 0
    @State private var movingMinStart: Int = 0
    @State private var movingMaxStart: Int = 0
    @State private var movingOffset: Int = 0
    @State private var dragMode: DragMode?
    @State private var lastTranslation: CGSize = .zero
    @State private var ignoreDrag = false
    @State private var dragStartLocation: CGPoint?

    private let calendar = Calendar.current
    private let minuteTimer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()
    private let slotsPerDay = TimeStore.slotsPerDay
    private let rowsPerDay = TimeStore.slotsPerDay / 2
    private let dragSnapSlots = 2
    private let rowHeight: CGFloat = 24
    private let labelWidth: CGFloat = 60
    private let columnSpacing: CGFloat = 12
    private var slotHeight: CGFloat { rowHeight / 2 }
    private var gridOffsetY: CGFloat { rowHeight / 2 }

    var body: some View {
        let slots = store.slots(for: day)
        let labels = store.labels(for: day)
        let projectMap = Dictionary(uniqueKeysWithValues: store.projects.map { ($0.id, $0) })
        let segments = timeSegments(slots: slots, labels: labels, projectMap: projectMap)

        ScrollViewReader { proxy in
            ScrollView {
                ZStack(alignment: .topLeading) {
                    LazyVStack(spacing: 0) {
                        ForEach(0..<rowsPerDay, id: \.self) { index in
                            TimeRow(
                                index: index,
                                rowHeight: rowHeight,
                                labelWidth: labelWidth,
                                columnSpacing: columnSpacing
                            )
                            .id(index)
                        }
                    }

                    GeometryReader { geometry in
                        let totalHeight = rowHeight * CGFloat(rowsPerDay) + gridOffsetY
                        let timeWidth = max(0, geometry.size.width - labelWidth - columnSpacing)

                        ZStack(alignment: .topLeading) {
                            HStack(spacing: columnSpacing) {
                                Color.clear
                                    .frame(width: labelWidth, height: totalHeight)

                                ZStack(alignment: .topLeading) {
                                    ForEach(segments) { segment in
                                        let isMoving = movingSegment?.matches(segment) == true
                                        TimeSegmentView(
                                            segment: segment,
                                            timeWidth: timeWidth,
                                            slotHeight: slotHeight,
                                            baseOffsetY: gridOffsetY
                                        )
                                        .opacity(isMoving ? 0.05 : 1)
                                        .contentShape(Rectangle())
                                    }

                                    if let movingSegment,
                                       let movingRange = movingRange(for: movingSegment) {
                                        TimeSegmentView(
                                            segment: movingSegment,
                                            timeWidth: timeWidth,
                                            slotHeight: slotHeight,
                                            baseOffsetY: gridOffsetY,
                                            overrideRange: movingRange
                                        )
                                        .opacity(0.9)
                                        .allowsHitTesting(false)
                                    }

                                    if let selectionRange = selectedRange {
                                        SelectionOverlay(
                                            range: selectionRange,
                                            timeWidth: timeWidth,
                                            slotHeight: slotHeight,
                                            baseOffsetY: gridOffsetY
                                        )
                                        .allowsHitTesting(false)
                                    }

                                    Rectangle()
                                        .fill(Color.clear)
                                        .frame(width: timeWidth, height: totalHeight)
                                        .contentShape(Rectangle())
                                        .gesture(
                                            DragGesture(minimumDistance: 0)
                                                .onChanged { value in
                                                    handleDragChanged(value)
                                                }
                                                .onEnded { _ in
                                                    handleDragEnded()
                                                }
                                        )
                                }
                                .frame(width: timeWidth, height: totalHeight)
                            }

                            if shouldShowNowLine {
                                let lineOffsetY = nowLineOffsetY(totalHeight: totalHeight)
                                let labelOffsetY = nowLabelOffsetY(lineOffsetY: lineOffsetY)

                                Rectangle()
                                    .fill(Color.red)
                                    .frame(width: geometry.size.width, height: 2)
                                    .offset(x: 0, y: lineOffsetY)
                                    .allowsHitTesting(false)

                                HStack(spacing: columnSpacing) {
                                    Text(nowTimeLabel)
                                        .font(.caption2)
                                        .foregroundStyle(.red)
                                        .frame(width: labelWidth, alignment: .trailing)
                                    Spacer()
                                }
                                .frame(width: geometry.size.width, alignment: .leading)
                                .offset(x: 0, y: labelOffsetY)
                                .allowsHitTesting(false)
                            }
                        }
                    }
                    .frame(height: rowHeight * CGFloat(rowsPerDay) + gridOffsetY)
                }
                .frame(height: rowHeight * CGFloat(rowsPerDay) + gridOffsetY)
                .padding(.horizontal, 16)
            }
            .onAppear {
                scrollToStartHour(using: proxy)
            }
            .onChange(of: day) { _ in
                scrollToStartHour(using: proxy)
                now = Date()
            }
        }
        .onAppear {
            now = Date()
        }
        .onReceive(minuteTimer) { _ in
            now = Date()
        }
    }

    private var selectedRange: ClosedRange<Int>? {
        guard let start = dragStart, let current = dragCurrent else { return nil }
        return min(start, current)...max(start, current)
    }

    private var shouldShowNowLine: Bool {
        calendar.isDateInToday(day)
    }

    private var nowTimeLabel: String {
        Self.nowFormatter.string(from: now)
    }

    private func nowLineOffsetY(totalHeight: CGFloat) -> CGFloat {
        let components = calendar.dateComponents([.hour, .minute], from: now)
        let minutes = CGFloat((components.hour ?? 0) * 60 + (components.minute ?? 0))
        let rawOffset = (minutes / 15) * slotHeight + gridOffsetY
        return min(max(rawOffset, gridOffsetY), totalHeight)
    }

    private func nowLabelOffsetY(lineOffsetY: CGFloat) -> CGFloat {
        max(lineOffsetY - 16, 0)
    }

    private static let nowFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = .autoupdatingCurrent
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter
    }()

    private func handleDragChanged(_ value: DragGesture.Value) {
        lastTranslation = value.translation
        if dragStartLocation == nil {
            dragStartLocation = value.startLocation
        }
        if dragMode == nil {
            let horizontal = abs(value.translation.width)
            let vertical = abs(value.translation.height)
            if horizontal < 6 && vertical < 6 {
                return
            }
            if horizontal > vertical {
                ignoreDrag = true
                return
            }
            let index = slotIndex(for: value.location.y)
            if let segment = segmentAt(slotIndex: index) {
                dragMode = .move
                beginMoveIfNeeded(segment)
            } else {
                dragMode = .selection
                let startRow = rowIndex(for: value.location.y)
                let startIndex = startRow * dragSnapSlots
                dragStart = startIndex
                dragCurrent = startIndex + (dragSnapSlots - 1)
            }
        }

        guard !ignoreDrag else { return }
        switch dragMode {
        case .move:
            updateMove(with: value)
        case .selection:
            updateSelection(with: value)
        case .none:
            break
        }
    }

    private func handleDragEnded() {
        if ignoreDrag {
            ignoreDrag = false
            dragMode = nil
            lastTranslation = .zero
            dragStartLocation = nil
            return
        }
        if dragMode == nil {
            if let location = dragStartLocation {
                let index = slotIndex(for: location.y)
                if let segment = segmentAt(slotIndex: index) {
                    onRangeSelected(segment.startIndex...segment.endIndex)
                }
            }
            lastTranslation = .zero
            dragStartLocation = nil
            return
        }
        defer {
            dragMode = nil
            lastTranslation = .zero
            dragStartLocation = nil
        }
        switch dragMode {
        case .move:
            if abs(movingOffset) == 0,
               abs(lastTranslation.height) < 4,
               abs(lastTranslation.width) < 4,
               let segment = movingSegment {
                onRangeSelected(segment.startIndex...segment.endIndex)
                movingSegment = nil
                movingOffset = 0
            } else {
                finishMove()
            }
        case .selection:
            finishSelection()
        case .none:
            break
        }
    }

    private func updateSelection(with value: DragGesture.Value) {
        guard movingSegment == nil else { return }
        guard let start = dragStart else { return }
        let currentRow = rowIndex(for: value.location.y)
        let startRow = start / dragSnapSlots
        let lowerRow = min(startRow, currentRow)
        let upperRow = max(startRow, currentRow)
        dragStart = lowerRow * dragSnapSlots
        dragCurrent = upperRow * dragSnapSlots + (dragSnapSlots - 1)
    }

    private func finishSelection() {
        guard movingSegment == nil else {
            dragStart = nil
            dragCurrent = nil
            return
        }
        if let range = selectedRange {
            onRangeSelected(normalizedRange(range))
        }
        dragStart = nil
        dragCurrent = nil
    }

    private func beginMoveIfNeeded(_ segment: TimeSegment) {
        guard movingSegment == nil else { return }
        movingSegment = segment
        movingStartIndex = segment.startIndex
        movingOffset = 0
        dragStart = nil
        dragCurrent = nil

        let bounds = moveBounds(for: segment)
        movingMinStart = bounds.minStart
        movingMaxStart = bounds.maxStart
    }

    private func updateMove(with value: DragGesture.Value) {
        guard let segment = movingSegment else { return }
        let deltaRows = Int(round(value.translation.height / rowHeight))
        let deltaSlots = deltaRows * dragSnapSlots
        let length = segment.endIndex - segment.startIndex
        let maxStart = min(movingMaxStart, slotsPerDay - length - 1)
        let minStartAligned = snapUpToGrid(movingMinStart)
        let maxStartAligned = snapDownToGrid(maxStart)
        guard minStartAligned <= maxStartAligned else {
            movingOffset = 0
            return
        }

        let desiredStart = movingStartIndex + deltaSlots
        let boundedDesired = min(max(desiredStart, minStartAligned), maxStartAligned)
        var snappedStart = snapToGrid(boundedDesired)
        snappedStart = min(max(snappedStart, minStartAligned), maxStartAligned)
        movingOffset = snappedStart - movingStartIndex
    }

    private func snapToGrid(_ index: Int) -> Int {
        guard dragSnapSlots > 1 else { return index }
        let ratio = Double(index) / Double(dragSnapSlots)
        return Int(ratio.rounded()) * dragSnapSlots
    }

    private func snapUpToGrid(_ index: Int) -> Int {
        guard dragSnapSlots > 1 else { return index }
        let remainder = index % dragSnapSlots
        if remainder == 0 { return index }
        return index + (dragSnapSlots - remainder)
    }

    private func snapDownToGrid(_ index: Int) -> Int {
        guard dragSnapSlots > 1 else { return index }
        let remainder = index % dragSnapSlots
        if remainder == 0 { return index }
        return index - remainder
    }

    private func finishMove() {
        guard let segment = movingSegment else { return }
        let length = segment.endIndex - segment.startIndex
        let newStart = movingStartIndex + movingOffset
        let newRange = newStart...(newStart + length)
        let originalRange = segment.startIndex...segment.endIndex

        if newRange != originalRange {
            store.setEntry(nil, label: nil, for: originalRange, on: day)
            store.setEntry(segment.project.id, label: segment.label, for: newRange, on: day)
        }

        movingSegment = nil
        movingOffset = 0
    }

    private func movingRange(for segment: TimeSegment) -> ClosedRange<Int>? {
        let length = segment.endIndex - segment.startIndex
        let start = movingStartIndex + movingOffset
        guard start >= 0, start + length < slotsPerDay else { return nil }
        return start...(start + length)
    }

    private func moveBounds(for segment: TimeSegment) -> (minStart: Int, maxStart: Int) {
        let length = segment.endIndex - segment.startIndex
        let slots = store.slots(for: day)
        let start = segment.startIndex
        let end = segment.endIndex

        var previousOccupied: Int?
        if start > 0 {
            for index in stride(from: start - 1, through: 0, by: -1) {
                if slots[index] != nil {
                    previousOccupied = index
                    break
                }
            }
        }

        var nextOccupied: Int?
        if end < slotsPerDay - 1 {
            for index in (end + 1)..<slotsPerDay {
                if slots[index] != nil {
                    nextOccupied = index
                    break
                }
            }
        }

        let minStart = max(0, (previousOccupied ?? -1) + 1)
        let maxStart = min(slotsPerDay - length - 1, (nextOccupied ?? slotsPerDay) - length)
        return (minStart, maxStart)
    }

    private func slotIndex(for locationY: CGFloat) -> Int {
        let adjustedY = locationY - gridOffsetY
        let rawIndex = Int(floor(adjustedY / slotHeight))
        return min(max(rawIndex, 0), slotsPerDay - 1)
    }
    
    private func rowIndex(for locationY: CGFloat) -> Int {
        let adjustedY = locationY - gridOffsetY
        let rawIndex = Int(floor(adjustedY / rowHeight))
        return min(max(rawIndex, 0), rowsPerDay - 1)
    }

    private func segmentAt(slotIndex: Int) -> TimeSegment? {
        let slots = store.slots(for: day)
        let labels = store.labels(for: day)
        guard slotIndex >= 0, slotIndex < slotsPerDay,
              let projectId = slots[slotIndex],
              let project = store.project(for: projectId) else {
            return nil
        }

        let label = labels[slotIndex]
        var start = slotIndex
        while start > 0, slots[start - 1] == projectId, labels[start - 1] == label {
            start -= 1
        }
        var end = slotIndex
        while end < slotsPerDay - 1, slots[end + 1] == projectId, labels[end + 1] == label {
            end += 1
        }

        return TimeSegment(startIndex: start, endIndex: end, project: project, label: label)
    }

    private func normalizedRange(_ range: ClosedRange<Int>) -> ClosedRange<Int> {
        var lower = range.lowerBound
        var upper = range.upperBound
        if lower > upper {
            swap(&lower, &upper)
        }
        let count = upper - lower + 1
        guard count % dragSnapSlots != 0 else { return lower...upper }

        if upper < slotsPerDay - 1 {
            upper += 1
        } else if lower > 0 {
            lower -= 1
        }
        return lower...upper
    }

    private func scrollToStartHour(using proxy: ScrollViewProxy) {
        let targetIndex = min(rowsPerDay - 1, 8 * 2)
        DispatchQueue.main.async {
            proxy.scrollTo(targetIndex, anchor: .top)
        }
    }

    private func timeSegments(
        slots: [UUID?],
        labels: [String?],
        projectMap: [UUID: Project]
    ) -> [TimeSegment] {
        var segments: [TimeSegment] = []
        var currentId: UUID?
        var currentLabel: String?
        var currentStart: Int?

        func closeSegment(at endIndex: Int) {
            guard let start = currentStart, let id = currentId, let project = projectMap[id] else { return }
            segments.append(TimeSegment(
                startIndex: start,
                endIndex: endIndex,
                project: project,
                label: currentLabel
            ))
            currentStart = nil
            currentId = nil
            currentLabel = nil
        }

        for index in 0..<slotsPerDay {
            let slotId = slots[index]
            let label = labels[index]

            guard let slotId else {
                if currentStart != nil {
                    closeSegment(at: index - 1)
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
                closeSegment(at: index - 1)
                currentStart = index
                currentId = slotId
                currentLabel = label
            }
        }

        if currentStart != nil {
            closeSegment(at: slotsPerDay - 1)
        }

        return segments
    }

    private enum DragMode {
        case selection
        case move
    }
}

struct TimeRow: View {
    let index: Int
    let rowHeight: CGFloat
    let labelWidth: CGFloat
    let columnSpacing: CGFloat

    var body: some View {
        HStack(spacing: columnSpacing) {
            Text(labelText)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(width: labelWidth, alignment: .trailing)

            Rectangle()
                .fill(Color.clear)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(
                Rectangle()
                    .fill(lineColor)
                    .frame(height: lineHeight)
                    .offset(y: lineOffsetY),
                alignment: .top
            )
        }
        .frame(height: rowHeight)
    }

    private var labelText: String {
        guard index % 2 == 0 else { return "" }
        return String(format: "%02d:00", index / 2)
    }

    private var lineColor: Color {
        let base = Color.gray
        return index % 2 == 0 ? base.opacity(0.35) : base.opacity(0.18)
    }

    private var lineHeight: CGFloat {
        index % 2 == 0 ? 1.5 : 1
    }

    private var lineOffsetY: CGFloat {
        (rowHeight / 2) - (lineHeight / 2)
    }
}

struct TimeSegment: Identifiable {
    let startIndex: Int
    let endIndex: Int
    let project: Project
    let label: String?

    var id: String {
        let labelValue = label ?? ""
        return "\(project.id.uuidString)-\(startIndex)-\(endIndex)-\(labelValue)"
    }

    func matches(_ other: TimeSegment) -> Bool {
        project.id == other.project.id
            && startIndex == other.startIndex
            && endIndex == other.endIndex
            && label == other.label
    }
}

struct TimeSegmentView: View {
    let segment: TimeSegment
    let timeWidth: CGFloat
    let slotHeight: CGFloat
    let baseOffsetY: CGFloat
    var overrideRange: ClosedRange<Int>? = nil

    var body: some View {
        let range = overrideRange ?? (segment.startIndex...segment.endIndex)
        let height = CGFloat(range.upperBound - range.lowerBound + 1) * slotHeight
        let offsetY = CGFloat(range.lowerBound) * slotHeight + baseOffsetY
        RoundedRectangle(cornerRadius: 8)
            .fill(Color(hex: segment.project.colorHex))
            .frame(width: timeWidth, height: height)
            .overlay(alignment: .topLeading) {
                if let label = segment.label, !label.isEmpty {
                    Text(label)
                        .font(.caption2)
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                        .lineLimit(1)
                }
            }
            .offset(x: 0, y: offsetY)
    }
}

struct SelectionOverlay: View {
    let range: ClosedRange<Int>
    let timeWidth: CGFloat
    let slotHeight: CGFloat
    let baseOffsetY: CGFloat

    var body: some View {
        let start = min(range.lowerBound, range.upperBound)
        let end = max(range.lowerBound, range.upperBound)
        let height = CGFloat(end - start + 1) * slotHeight
        let offsetY = CGFloat(start) * slotHeight + baseOffsetY

        RoundedRectangle(cornerRadius: 8)
            .fill(Color.accentColor.opacity(0.22))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.accentColor.opacity(0.6), lineWidth: 1)
            )
            .frame(width: timeWidth, height: height)
            .offset(x: 0, y: offsetY)
    }
}

struct EntryEditorView: View {
    let projects: [Project]
    let existingSlots: [UUID?]
    let initialRange: ClosedRange<Int>
    let initialProjectId: UUID?
    let initialLabel: String?
    let onSave: (UUID?, ClosedRange<Int>, String?) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var startSlot: Int
    @State private var endSlot: Int
    @State private var label: String
    @State private var selectedProjectId: UUID?
    @State private var showOverlapAlert = false

    init(
        projects: [Project],
        existingSlots: [UUID?],
        initialRange: ClosedRange<Int>,
        initialProjectId: UUID?,
        initialLabel: String?,
        onSave: @escaping (UUID?, ClosedRange<Int>, String?) -> Void
    ) {
        self.projects = projects
        self.existingSlots = existingSlots
        self.initialRange = initialRange
        self.initialProjectId = initialProjectId
        self.initialLabel = initialLabel
        self.onSave = onSave

        let lower = min(initialRange.lowerBound, initialRange.upperBound)
        let upper = max(initialRange.lowerBound, initialRange.upperBound)
        _startSlot = State(initialValue: lower)
        _endSlot = State(initialValue: min(upper + 1, TimeStore.slotsPerDay))
        _label = State(initialValue: initialLabel ?? "")
        _selectedProjectId = State(initialValue: initialProjectId ?? projects.first?.id)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Time") {
                    Picker("Start", selection: $startSlot) {
                        ForEach(0..<TimeStore.slotsPerDay, id: \.self) { index in
                            Text(TimeStore.timeString(for: index)).tag(index)
                        }
                    }
                    Picker("End", selection: $endSlot) {
                        ForEach(1...TimeStore.slotsPerDay, id: \.self) { index in
                            Text(TimeStore.timeString(for: index)).tag(index)
                        }
                    }
                }

                Section("Label") {
                    TextField("Optional label", text: $label)
                }

                Section("Project") {
                    Button(role: .destructive) {
                        selectedProjectId = nil
                    } label: {
                        projectRow(name: "Clear Time", color: .secondary, isSelected: selectedProjectId == nil)
                    }

                    if projects.isEmpty {
                        Text("No projects yet.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(projects) { project in
                            Button {
                                selectedProjectId = project.id
                            } label: {
                                projectRow(
                                    name: project.name,
                                    color: Color(hex: project.colorHex),
                                    isSelected: selectedProjectId == project.id
                                )
                            }
                        }
                    }
                }
            }
            .navigationTitle("Assign Time")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        saveEntry()
                    }
                }
            }
        }
        .alert("Overlapping time", isPresented: $showOverlapAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("This time range overlaps existing time for a different project. Adjust the range or clear time first.")
        }
        .onChange(of: startSlot) { newValue in
            if endSlot <= newValue {
                endSlot = min(newValue + 1, TimeStore.slotsPerDay)
            }
        }
        .onChange(of: endSlot) { newValue in
            if newValue <= startSlot {
                startSlot = max(newValue - 1, 0)
            }
        }
    }

    private func saveEntry() {
        let safeEnd = max(endSlot, startSlot + 1)
        let range = startSlot...(safeEnd - 1)
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedLabel = trimmed.isEmpty ? nil : trimmed
        if hasOverlap(in: range) {
            showOverlapAlert = true
            return
        }
        if initialProjectId != nil {
            onSave(nil, initialRange, nil)
        }
        onSave(selectedProjectId, range, normalizedLabel)
        dismiss()
    }

    private func hasOverlap(in range: ClosedRange<Int>) -> Bool {
        guard selectedProjectId != nil else { return false }
        for index in range {
            guard index >= 0 && index < existingSlots.count else { continue }
            guard existingSlots[index] != nil else { continue }
            if initialProjectId != nil, initialRange.contains(index) {
                continue
            }
            return true
        }
        return false
    }

    @ViewBuilder
    private func projectRow(name: String, color: Color, isSelected: Bool) -> some View {
        HStack(spacing: 12) {
            Circle()
                .fill(color)
                .frame(width: 12, height: 12)
            Text(name)
            Spacer()
            if isSelected {
                Image(systemName: "checkmark")
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct LegendView: View {
    let projects: [Project]
    let totals: [UUID: Int]
    let totalMinutes: Int

    var body: some View {
        VStack(spacing: 0) {
            Divider()
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    if projects.isEmpty {
                        Text("No projects yet")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(projects) { project in
                            let minutes = totals[project.id] ?? 0
                            HStack(spacing: 8) {
                                Circle()
                                    .fill(Color(hex: project.colorHex))
                                    .frame(width: 10, height: 10)
                                Text(project.name)
                                    .font(.caption)
                                if minutes > 0 {
                                    Text(hoursText(from: minutes))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 6)
                            .padding(.horizontal, 10)
                            .background(Color.black.opacity(0.04))
                            .clipShape(Capsule())
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            HStack {
                Text("Total")
                    .font(.caption)
                Spacer()
                Text(hoursText(from: totalMinutes))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground))
    }

    private func hoursText(from minutes: Int) -> String {
        let hours = Double(minutes) / 60.0
        return String(format: "%.1f h", hours)
    }
}

struct TimeListView: View {
    @EnvironmentObject private var store: TimeStore
    @Environment(\.dismiss) private var dismiss

    let referenceDate: Date

    @State private var selectedProjectId: UUID?
    @State private var rangeStart: Date
    @State private var rangeEnd: Date
    @State private var editingEntry: TimeEntry?

    init(referenceDate: Date, selectedProjectId: UUID? = nil) {
        self.referenceDate = referenceDate
        _selectedProjectId = State(initialValue: selectedProjectId)
        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month], from: referenceDate)
        let start = calendar.date(from: components) ?? referenceDate
        let range = calendar.range(of: .day, in: .month, for: start)
        let end = range.flatMap { calendar.date(byAdding: .day, value: $0.count - 1, to: start) } ?? referenceDate
        _rangeStart = State(initialValue: start)
        _rangeEnd = State(initialValue: end)
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Filters") {
                    Picker("Project", selection: $selectedProjectId) {
                        Text("All Projects").tag(Optional<UUID>.none)
                        ForEach(store.projects) { project in
                            Text(project.name).tag(Optional(project.id))
                        }
                    }
                    .pickerStyle(.menu)

                    DatePicker("From", selection: $rangeStart, displayedComponents: .date)
                    DatePicker("To", selection: $rangeEnd, displayedComponents: .date)
                }

                Section {
                    HStack {
                        Text("Total Hours")
                        Spacer()
                        Text(hoursText(from: totalMinutes))
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("Total Amount")
                        Spacer()
                        Text(currencyText(totalAmount))
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Entries") {
                    if entryList.isEmpty {
                        Text("No entries in this range.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(entryList) { entry in
                            let minutes = (entry.endIndex - entry.startIndex + 1) * TimeStore.slotMinutes
                            let hours = Double(minutes) / 60.0
                            let project = store.project(for: entry.projectId)
                            let rate = project?.rate ?? 0
                            Button {
                                editingEntry = entry
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("\(Self.dayFormatter.string(from: entry.day)) · \(TimeStore.timeString(for: entry.startIndex))–\(TimeStore.timeString(for: entry.endIndex + 1))")
                                            .font(.subheadline)
                                        Text(project?.name ?? "Unknown")
                                            .font(.caption)
                                        if let label = entry.label, !label.isEmpty {
                                            Text(label)
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                    VStack(alignment: .trailing, spacing: 2) {
                                        Text(String(format: "%.2f h", hours))
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                        Text(currencyText(hours * rate))
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .contentShape(Rectangle())
                        }
                    }
                }
            }
            .navigationTitle("Time List")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .onChange(of: rangeStart) { _ in
            normalizeRange()
        }
        .onChange(of: rangeEnd) { _ in
            normalizeRange()
        }
        .sheet(item: $editingEntry) { entry in
            EntryEditorView(
                projects: store.projects,
                existingSlots: store.slots(for: entry.day),
                initialRange: entry.startIndex...entry.endIndex,
                initialProjectId: entry.projectId,
                initialLabel: entry.label
            ) { projectId, range, label in
                store.setEntry(projectId, label: label, for: range, on: entry.day)
            }
        }
    }

    private var entryList: [TimeEntry] {
        store.entries(from: rangeStart, to: rangeEnd, filterProjectId: selectedProjectId)
    }

    private var totalMinutes: Int {
        store.totalMinutes(from: rangeStart, to: rangeEnd, projectId: selectedProjectId)
    }

    private var totalAmount: Double {
        if let projectId = selectedProjectId {
            return (Double(totalMinutes) / 60.0) * (store.project(for: projectId)?.rate ?? 0)
        }
        return store.entries(from: rangeStart, to: rangeEnd, filterProjectId: nil).reduce(0) { total, entry in
            let minutes = (entry.endIndex - entry.startIndex + 1) * TimeStore.slotMinutes
            let rate = store.project(for: entry.projectId)?.rate ?? 0
            return total + (Double(minutes) / 60.0) * rate
        }
    }

    private func normalizeRange() {
        if rangeStart > rangeEnd {
            rangeEnd = rangeStart
        }
    }

    private func hoursText(from minutes: Int) -> String {
        let hours = Double(minutes) / 60.0
        return String(format: "%.1f h", hours)
    }

    private func currencyText(_ amount: Double) -> String {
        String(format: "$%.2f", amount)
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter
    }()
}

struct MonthSummaryView: View {
    @EnvironmentObject private var store: TimeStore
    @Environment(\.dismiss) private var dismiss

    let monthDate: Date
    private let vatRate: Double = 0.17

    @State private var selectedProjectId: UUID?
    @State private var rangeStart: Date
    @State private var rangeEnd: Date
    @State private var mailData: MailData?
    @State private var alertMessage: AlertMessage?
    @State private var isCreatingInvoice = false
    @State private var isGeneratingProforma = false
    @State private var invoicePreview: InvoicePreview?
    @State private var isSendingSandbox = false
    @State private var showMailComposer = false
    @State private var pendingInvoice: PendingInvoiceRequest?
    @State private var showDuplicateInvoiceConfirm = false

    init(monthDate: Date, selectedProjectId: UUID? = nil) {
        self.monthDate = monthDate
        _selectedProjectId = State(initialValue: selectedProjectId)
        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month], from: monthDate)
        let start = calendar.date(from: components) ?? monthDate
        let range = calendar.range(of: .day, in: .month, for: start)
        let end = range.flatMap { calendar.date(byAdding: .day, value: $0.count - 1, to: start) } ?? monthDate
        _rangeStart = State(initialValue: start)
        _rangeEnd = State(initialValue: end)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Project", selection: $selectedProjectId) {
                        Text("All Projects").tag(Optional<UUID>.none)
                        ForEach(store.projects) { project in
                            Text(project.name).tag(Optional(project.id))
                        }
                    }
                    .pickerStyle(.menu)

                    HStack {
                        Text("Total Hours")
                        Spacer()
                        Text(hoursText(from: totalMinutes))
                            .foregroundStyle(.secondary)
                    }

                    HStack {
                        Text("Total Amount")
                        Spacer()
                        Text(currencyText(totalAmount))
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text(monthTitle)
                }

                summarySections

                Section("Export") {
                    Button {
                        sendProformaPDF()
                    } label: {
                        if isGeneratingProforma {
                            HStack(spacing: 8) {
                                ProgressView()
                                Text("Preparing Proforma PDF")
                            }
                        } else {
                            Label("Send Proforma PDF + Timesheet", systemImage: "doc.richtext")
                        }
                    }
                    .disabled(selectedProjectId == nil || isGeneratingProforma)

                    if selectedProjectId == nil {
                        Text("Select a project to send.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Month Summary")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .onChange(of: rangeStart) { _ in
            normalizeRange()
        }
        .onChange(of: rangeEnd) { _ in
            normalizeRange()
        }
        .sheet(isPresented: $showMailComposer, onDismiss: {
            mailData = nil
        }) {
            if let mailData {
                MailView(isPresented: $showMailComposer, data: mailData)
            }
        }
        .sheet(item: $invoicePreview, onDismiss: {
            isSendingSandbox = false
        }) { preview in
            InvoicePreviewSheet(
                preview: preview,
                isSending: $isSendingSandbox,
                onCopy: {
                    UIPasteboard.general.string = preview.json
                    alertMessage = AlertMessage(title: "Copied", message: "Invoice JSON copied to clipboard.")
                },
                onCopyResult: {
                    if let response = preview.responseJSON {
                        UIPasteboard.general.string = response
                        alertMessage = AlertMessage(title: "Copied", message: "Result JSON copied to clipboard.")
                    }
                },
                onSend: {
                    sendPreviewToSandbox(preview)
                }
            )
        }
        .confirmationDialog(
            "Send another invoice?",
            isPresented: $showDuplicateInvoiceConfirm,
            titleVisibility: .visible
        ) {
            Button("Send Invoice") {
                if let request = pendingInvoice {
                    pendingInvoice = nil
                    sendInvoice(request)
                }
            }
            Button("Cancel", role: .cancel) {
                pendingInvoice = nil
            }
        } message: {
            if let request = pendingInvoice {
                Text(duplicateInvoiceMessage(for: request))
            }
        }
        .alert(item: $alertMessage) { alert in
            Alert(
                title: Text(alert.title),
                message: Text(alert.message),
                dismissButton: .default(Text("OK"))
            )
        }
    }

    private var summarySections: some View {
        Group {
            if selectedProjectId == nil {
                Section("By Project") {
                    if projectTotals.isEmpty {
                        Text("No tracked time yet.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(projectTotals) { item in
                            HStack {
                                Circle()
                                    .fill(Color(hex: item.project.colorHex))
                                    .frame(width: 10, height: 10)
                                Text(item.project.name)
                                Spacer()
                                VStack(alignment: .trailing, spacing: 2) {
                                    Text(hoursText(from: item.minutes))
                                        .foregroundStyle(.secondary)
                                    Text(currencyText(item.amount))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            } else {
                Section("By Day") {
                    if dailyTotals.isEmpty {
                        Text("No entries in this month.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(dailyTotals, id: \.0) { day, minutes in
                            let amount = (Double(minutes) / 60.0) * projectRate
                            HStack {
                                Text(Self.dayFormatter.string(from: day))
                                Spacer()
                                VStack(alignment: .trailing, spacing: 2) {
                                    Text(hoursText(from: minutes))
                                        .foregroundStyle(.secondary)
                                    Text(currencyText(amount))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var monthTitle: String {
        Self.monthFormatter.string(from: monthDate)
    }

    private var totalMinutes: Int {
        return store.monthTotalMinutes(for: monthDate, projectId: selectedProjectId)
    }

    private var totalAmount: Double {
        if let projectId = selectedProjectId {
            return (Double(totalMinutes) / 60.0) * (store.project(for: projectId)?.rate ?? 0)
        }
        return projectTotals.reduce(0) { $0 + $1.amount }
    }

    private var projectTotals: [ProjectTotal] {
        let totals = store.monthTotals(for: monthDate)
        return store.projects.compactMap { project in
            let minutes = totals[project.id] ?? 0
            guard minutes > 0 else { return nil }
            let amount = (Double(minutes) / 60.0) * project.rate
            return ProjectTotal(project: project, minutes: minutes, amount: amount)
        }
    }

    private var dailyTotals: [(Date, Int)] {
        guard let projectId = selectedProjectId else { return [] }
        return store.dailyTotals(for: monthDate, projectId: projectId)
    }

    private var projectRate: Double {
        guard let projectId = selectedProjectId else { return 0 }
        return store.project(for: projectId)?.rate ?? 0
    }

    private func hoursText(from minutes: Int) -> String {
        let hours = Double(minutes) / 60.0
        return String(format: "%.1f h", hours)
    }

    private func currencyText(_ amount: Double) -> String {
        String(format: "$%.2f", amount)
    }

    private func roundedCurrency(_ amount: Double) -> Double {
        (amount * 100).rounded() / 100
    }

    private func invoiceAmounts(for project: Project, baseAmount: Double) -> (subtotal: Double, vat: Double, total: Double) {
        let base = roundedCurrency(baseAmount)
        switch project.vatType {
        case .none:
            return (subtotal: base, vat: 0, total: base)
        case .included:
            let subtotal = roundedCurrency(base / (1 + vatRate))
            let vat = roundedCurrency(base - subtotal)
            return (subtotal: subtotal, vat: vat, total: base)
        case .excluded:
            let subtotal = base
            let vat = roundedCurrency(subtotal * vatRate)
            let total = roundedCurrency(subtotal + vat)
            return (subtotal: subtotal, vat: vat, total: total)
        }
    }

    private func normalizeRange() {
        if rangeStart > rangeEnd {
            rangeEnd = rangeStart
        }
    }

    private var isSimulator: Bool {
#if targetEnvironment(simulator)
        true
#else
        false
#endif
    }

    private func sendTimesheet() {
        guard let project = selectedProject else {
            alertMessage = AlertMessage(
                title: "Select a project",
                message: "Choose a project before sending a timesheet."
            )
            return
        }

        let recipients = parseEmails(project.timesheetEmails)
        guard !recipients.isEmpty else {
            alertMessage = AlertMessage(
                title: "Missing recipients",
                message: "Add timesheet recipient emails in project settings."
            )
            return
        }

        guard !isSimulator else {
            alertMessage = AlertMessage(
                title: "Mail unavailable on Simulator",
                message: "Send email from a real device."
            )
            return
        }

        guard MailView.canSendMail else {
            alertMessage = AlertMessage(
                title: "Mail not configured",
                message: "Set up a mail account on the device to send the timesheet."
            )
            return
        }

        let csv = store.timesheetCSV(from: rangeStart, to: rangeEnd, filterProjectId: project.id)
        guard let data = csv.data(using: .utf8) else {
            alertMessage = AlertMessage(
                title: "Timesheet unavailable",
                message: "Could not generate the timesheet file."
            )
            return
        }

        let filename = "Timesheet-\(Self.fileFormatter.string(from: rangeStart))-\(Self.fileFormatter.string(from: rangeEnd)).csv"
        mailData = MailData(
            recipients: recipients,
            subject: timesheetSubject(for: project),
            body: timesheetPretext(for: project),
            attachments: [
                MailAttachment(data: data, mimeType: "text/csv", fileName: filename)
            ]
        )
        showMailComposer = true
    }

    private func sendProformaPDF() {
        guard !isGeneratingProforma else { return }
        guard let project = selectedProject else {
            alertMessage = AlertMessage(
                title: "Select a project",
                message: "Choose a project before sending a proforma invoice."
            )
            return
        }

        let recipients = parseEmails(project.timesheetEmails)
        guard !recipients.isEmpty else {
            alertMessage = AlertMessage(
                title: "Missing recipients",
                message: "Add timesheet recipient emails in project settings."
            )
            return
        }

        guard !isSimulator else {
            alertMessage = AlertMessage(
                title: "Mail unavailable on Simulator",
                message: "Send email from a real device."
            )
            return
        }

        guard MailView.canSendMail else {
            alertMessage = AlertMessage(
                title: "Mail not configured",
                message: "Set up a mail account on the device to send the invoice."
            )
            return
        }

        guard let logoData = store.invoiceLogoData else {
            alertMessage = AlertMessage(
                title: "Missing logo",
                message: "Add a logo in Settings > Invoice PDF."
            )
            return
        }

        guard let signatureData = store.invoiceSignatureData else {
            alertMessage = AlertMessage(
                title: "Missing signature",
                message: "Add a signature in Settings > Invoice PDF."
            )
            return
        }

        let minutes = store.totalMinutes(from: rangeStart, to: rangeEnd, projectId: project.id)
        guard minutes > 0 else {
            alertMessage = AlertMessage(
                title: "No time tracked",
                message: "There is no time in this range to invoice."
            )
            return
        }

        let hours = Double(minutes) / 60.0
        let roundedHours = (hours * 100).rounded() / 100
        let baseAmount = roundedHours * project.rate
        let amounts = invoiceAmounts(for: project, baseAmount: baseAmount)
        let invoiceDate = store.greenInvoiceTestMode ? Date() : effectiveInvoiceDate()
        let header = invoiceHeader(for: project)
        let itemDetails = invoiceItemDetails(for: project)
        let invoiceNumber = store.nextInvoiceNumber()
        let rangeText = localizedRangeTitle(for: project)

        let csv = store.timesheetCSV(from: rangeStart, to: rangeEnd, filterProjectId: project.id)
        let safeProject = project.name.replacingOccurrences(of: "/", with: "-")
        let pdfName = "Proforma-\(safeProject)-\(rangeMonthTitle).pdf"
        let csvName = "Timesheet-\(Self.fileFormatter.string(from: rangeStart))-\(Self.fileFormatter.string(from: rangeEnd)).csv"

        isGeneratingProforma = true
        DispatchQueue.global(qos: .userInitiated).async {
            let pdfData = makeProformaPDF(
                logoData: logoData,
                signatureData: signatureData,
                project: project,
                header: header,
                itemDetails: itemDetails,
                hours: roundedHours,
                rate: project.rate,
                subtotal: amounts.subtotal,
                vatAmount: amounts.vat,
                total: amounts.total,
                date: invoiceDate,
                invoiceNumber: invoiceNumber,
                rangeText: rangeText
            )
            let csvData = csv.data(using: .utf8)
            DispatchQueue.main.async {
                isGeneratingProforma = false
                guard let pdfData else {
                    alertMessage = AlertMessage(
                        title: "Invoice failed",
                        message: "Could not generate the PDF invoice."
                    )
                    return
                }
                guard let csvData else {
                    alertMessage = AlertMessage(
                        title: "Timesheet unavailable",
                        message: "Could not generate the timesheet file."
                    )
                    return
                }

                mailData = MailData(
                    recipients: recipients,
                    subject: header,
                    body: proformaEmailBody(for: project),
                    attachments: [
                        MailAttachment(data: pdfData, mimeType: "application/pdf", fileName: pdfName),
                        MailAttachment(data: csvData, mimeType: "text/csv", fileName: csvName)
                    ]
                )
                showMailComposer = true
                store.advanceInvoiceNumber(from: invoiceNumber)
            }
        }
    }

    private func createInvoice() {
        guard let project = selectedProject else {
            alertMessage = AlertMessage(
                title: "Select a project",
                message: "Choose a project before creating an invoice."
            )
            return
        }

        let apiKey = store.greenInvoiceApiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let apiSecret = store.greenInvoiceApiSecret.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !apiKey.isEmpty, !apiSecret.isEmpty else {
            alertMessage = AlertMessage(
                title: "Missing API credentials",
                message: "Add your GreenInvoice API key and secret in Settings."
            )
            return
        }

        let minutes = store.totalMinutes(from: rangeStart, to: rangeEnd, projectId: project.id)
        guard minutes > 0 else {
            alertMessage = AlertMessage(
                title: "No time tracked",
                message: "There is no time in this range to invoice."
            )
            return
        }

        let hours = Double(minutes) / 60.0
        let roundedHours = (hours * 100).rounded() / 100
        let subject = invoiceHeader(for: project)
        let itemDescription = invoiceItemDetails(for: project)
        let invoiceDate = store.greenInvoiceTestMode ? Date() : effectiveInvoiceDate()
        let clientKey = invoiceClientKey(for: project)
        let cachedClientId = project.greenInvoiceClientId.trimmingCharacters(in: .whitespacesAndNewlines)
        let vatType = project.vatType

        guard !clientKey.isEmpty else {
            alertMessage = AlertMessage(
                title: "Missing client key",
                message: "Add the GreenInvoice customer accounting key in the project settings."
            )
            return
        }

        if store.greenInvoiceTestMode {
            guard let previewJSON = makeInvoiceJSON(
                subject: subject,
                itemDescription: itemDescription,
                quantity: roundedHours,
                unitPrice: project.rate,
                date: invoiceDate,
                clientId: cachedClientId.isEmpty ? nil : cachedClientId,
                clientKey: clientKey,
                vatType: vatType
            ) else {
                alertMessage = AlertMessage(
                    title: "Preview unavailable",
                    message: "Could not generate the invoice JSON preview."
                )
                return
            }

            invoicePreview = InvoicePreview(
                json: previewJSON,
                projectId: project.id,
                subject: subject,
                itemDescription: itemDescription,
                quantity: roundedHours,
                unitPrice: project.rate,
                date: invoiceDate,
                clientKey: clientKey,
                vatType: vatType,
                clientId: cachedClientId.isEmpty ? nil : cachedClientId,
                responseJSON: nil
            )
            return
        }
        let request = PendingInvoiceRequest(
            projectId: project.id,
            subject: subject,
            itemDescription: itemDescription,
            quantity: roundedHours,
            unitPrice: project.rate,
            date: invoiceDate,
            clientKey: clientKey,
            clientId: cachedClientId.isEmpty ? nil : cachedClientId,
            vatType: vatType,
            isSandbox: false,
            updatePreview: false
        )
        queueInvoiceSend(request)
    }

    private func sendPreviewToSandbox(_ preview: InvoicePreview) {
        let apiKey = store.greenInvoiceApiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let apiSecret = store.greenInvoiceApiSecret.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !apiKey.isEmpty, !apiSecret.isEmpty else {
            alertMessage = AlertMessage(
                title: "Missing API credentials",
                message: "Add your GreenInvoice API key and secret in Settings."
            )
            return
        }
        let request = PendingInvoiceRequest(
            projectId: preview.projectId,
            subject: preview.subject,
            itemDescription: preview.itemDescription,
            quantity: preview.quantity,
            unitPrice: preview.unitPrice,
            date: preview.date,
            clientKey: preview.clientKey,
            clientId: preview.clientId,
            vatType: preview.vatType,
            isSandbox: true,
            updatePreview: true
        )
        queueInvoiceSend(request)
    }

    private func queueInvoiceSend(_ request: PendingInvoiceRequest) {
        let count = store.invoiceCount(
            clientKey: request.clientKey,
            clientId: request.clientId,
            date: request.date,
            isSandbox: request.isSandbox
        )
        if count > 0 {
            pendingInvoice = request
            showDuplicateInvoiceConfirm = true
            return
        }
        sendInvoice(request)
    }

    private func sendInvoice(_ request: PendingInvoiceRequest) {
        let apiKey = store.greenInvoiceApiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let apiSecret = store.greenInvoiceApiSecret.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !apiKey.isEmpty, !apiSecret.isEmpty else {
            alertMessage = AlertMessage(
                title: "Missing API credentials",
                message: "Add your GreenInvoice API key and secret in Settings."
            )
            return
        }

        if request.isSandbox {
            isSendingSandbox = true
        } else {
            isCreatingInvoice = true
        }

        Task {
            do {
                let client: GreenInvoiceClient
                if request.isSandbox {
                    client = GreenInvoiceClient(
                        apiKey: apiKey,
                        apiSecret: apiSecret,
                        baseURL: GreenInvoiceClient.sandboxBaseURL
                    )
                } else {
                    client = GreenInvoiceClient(apiKey: apiKey, apiSecret: apiSecret)
                }

                let project = store.projects.first { $0.id == request.projectId }
                let resolvedClientId = try await resolveClientId(
                    project: project,
                    client: client,
                    clientKey: request.clientKey,
                    cachedClientId: request.clientId
                )
                guard let resolvedClientId else {
                    await MainActor.run {
                        if request.isSandbox {
                            isSendingSandbox = false
                        } else {
                            isCreatingInvoice = false
                        }
                        alertMessage = AlertMessage(
                            title: "Client not found",
                            message: "No GreenInvoice client matched the accounting key."
                        )
                    }
                    return
                }

                if request.updatePreview,
                   let updatedJSON = makeInvoiceJSON(
                    subject: request.subject,
                    itemDescription: request.itemDescription,
                    quantity: request.quantity,
                    unitPrice: request.unitPrice,
                    date: request.date,
                    clientId: resolvedClientId,
                    clientKey: request.clientKey,
                    vatType: request.vatType
                   ) {
                    updateInvoicePreviewPayload(updatedJSON, clientId: resolvedClientId)
                }

                let response = try await client.createProformaInvoice(
                    subject: request.subject,
                    itemDescription: request.itemDescription,
                    quantity: request.quantity,
                    unitPrice: request.unitPrice,
                    vatType: request.vatType,
                    clientId: resolvedClientId,
                    date: request.date
                )
                await MainActor.run {
                    if request.isSandbox {
                        isSendingSandbox = false
                    } else {
                        isCreatingInvoice = false
                    }
                    store.addInvoiceRecord(
                        clientId: resolvedClientId,
                        clientKey: request.clientKey,
                        date: request.date,
                        isSandbox: request.isSandbox
                    )
                    let message = response?.isEmpty == false ? "Invoice created: \(response!)" : "Invoice created successfully."
                    let title = request.isSandbox ? "Sandbox invoice created" : "Invoice created"
                    alertMessage = AlertMessage(title: title, message: message)
                }
            } catch {
                await MainActor.run {
                    if request.isSandbox {
                        isSendingSandbox = false
                        if request.updatePreview {
                            let responseBody = extractResponseJSON(from: error) ?? "No response body."
                            updateInvoicePreviewResponse(responseBody)
                            alertMessage = AlertMessage(title: "Sandbox invoice failed", message: "See the result JSON in the preview.")
                        } else {
                            alertMessage = AlertMessage(title: "Sandbox invoice failed", message: error.localizedDescription)
                        }
                    } else {
                        isCreatingInvoice = false
                        alertMessage = AlertMessage(title: "Invoice failed", message: error.localizedDescription)
                    }
                }
            }
        }
    }

    private func duplicateInvoiceMessage(for request: PendingInvoiceRequest) -> String {
        let count = store.invoiceCount(
            clientKey: request.clientKey,
            clientId: request.clientId,
            date: request.date,
            isSandbox: request.isSandbox
        )
        let monthTitle = Self.monthFormatter.string(from: request.date)
        let invoiceWord = count == 1 ? "invoice" : "invoices"
        return "You already sent \(count) \(invoiceWord) for \(request.clientKey) in \(monthTitle). Send another?"
    }

    private func effectiveInvoiceDate() -> Date {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let components = calendar.dateComponents([.year, .month], from: today)
        guard let startOfMonth = calendar.date(from: components),
              let range = calendar.range(of: .day, in: .month, for: startOfMonth),
              let lastDayOfMonth = calendar.date(byAdding: .day, value: range.count - 1, to: startOfMonth)
        else {
            return today
        }

        if calendar.isDate(today, inSameDayAs: lastDayOfMonth) {
            return lastDayOfMonth
        }

        return calendar.date(byAdding: .day, value: -1, to: startOfMonth) ?? today
    }

    private func updateInvoicePreviewPayload(_ payload: String, clientId: String) {
        guard let preview = invoicePreview else { return }
        var updated = preview
        updated.json = payload
        updated.clientId = clientId
        invoicePreview = updated
    }

    private func updateInvoicePreviewResponse(_ response: String) {
        guard let preview = invoicePreview else { return }
        var updated = preview
        updated.responseJSON = response
        invoicePreview = updated
    }

    private func extractResponseJSON(from error: Error) -> String? {
        if let greenError = error as? GreenInvoiceError {
            switch greenError {
            case let .server(_, message):
                return message?.trimmingCharacters(in: .whitespacesAndNewlines)
            default:
                return nil
            }
        }
        return nil
    }

    private func makeInvoiceJSON(
        subject: String,
        itemDescription: String,
        quantity: Double,
        unitPrice: Double,
        date: Date,
        clientId: String?,
        clientKey: String?,
        vatType: VatType
    ) -> String? {
        let documentType = 300
        let currency = "USD"
        let trimmedClientId = clientId?.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedClientKey = clientKey?.trimmingCharacters(in: .whitespacesAndNewlines)
        let client = GreenInvoiceClientReference(
            id: (trimmedClientId?.isEmpty ?? true) ? nil : trimmedClientId,
            accountingKey: (trimmedClientId?.isEmpty ?? true) ? (trimmedClientKey?.isEmpty ?? true ? nil : trimmedClientKey) : nil
        )
        let payload = GreenInvoiceDocumentRequest(
            description: subject,
            client: client.id == nil && client.accountingKey == nil ? nil : client,
            type: documentType,
            date: Self.fileFormatter.string(from: date),
            lang: "en",
            currency: currency,
            vatType: vatType.rawValue,
            income: [
                GreenInvoiceIncomeItem(
                    description: itemDescription,
                    quantity: quantity,
                    price: unitPrice,
                    currency: currency,
                    currencyRate: 1,
                    vatType: vatType.rawValue
                )
            ]
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(payload) else { return nil }
        return String(data: data, encoding: .utf8)
    }


    private var selectedProject: Project? {
        store.project(for: selectedProjectId)
    }

    private func parseEmails(_ value: String) -> [String] {
        value
            .split { $0 == "," || $0 == ";" || $0 == "\n" }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private func timesheetSubject(for project: Project) -> String {
        let trimmed = project.timesheetSubject.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            return trimmed
        }
        return "\(project.name) Timesheet \(rangeTitle)"
    }

    private func timesheetPretext(for project: Project) -> String {
        let trimmed = project.timesheetPretext.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            return trimmed
        }
        return "Attached is the timesheet for \(rangeTitle)."
    }

    private func proformaEmailBody(for project: Project) -> String {
        let base = timesheetPretext(for: project)
        let line = invoiceStrings(for: project).emailLine
        return base + "\n\n" + line
    }

    private func invoiceHeader(for project: Project) -> String {
        let trimmed = project.invoiceHeader.trimmingCharacters(in: .whitespacesAndNewlines)
        let month = rangeMonthTitle
        if trimmed.isEmpty {
            return month
        }
        return "\(trimmed) - \(month)"
    }

    private func invoiceItemDetails(for project: Project) -> String {
        let trimmed = project.invoiceItemDetails.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            return trimmed
        }
        return "\(project.name) time"
    }

    private func invoiceClientKey(for project: Project) -> String {
        project.greenInvoiceCustomerKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private struct InvoiceStrings {
        let title: String
        let projectLabel: String
        let periodLabel: String
        let dateLabel: String
        let invoiceNumberLabel: String
        let descriptionHeader: String
        let hoursHeader: String
        let rateHeader: String
        let amountHeader: String
        let subtotalLabel: String
        let vatLabelFormat: String
        let vatIncludedLabel: String
        let totalLabel: String
        let signatureLabel: String
        let emailLine: String
    }

    private func invoiceStrings(for project: Project) -> InvoiceStrings {
        switch project.invoiceLanguage {
        case .hebrew:
            return InvoiceStrings(
                title: "חשבונית פרו-פורמה",
                projectLabel: "פרויקט",
                periodLabel: "טווח",
                dateLabel: "תאריך",
                invoiceNumberLabel: "מספר חשבונית",
                descriptionHeader: "תיאור",
                hoursHeader: "שעות",
                rateHeader: "תעריף",
                amountHeader: "סכום",
                subtotalLabel: "סכום ביניים",
                vatLabelFormat: "מע״מ (%.0f%%)",
                vatIncludedLabel: "מע״מ (כלול)",
                totalLabel: "סה״כ",
                signatureLabel: "חתימה",
                emailLine: "מצורפים חשבונית פרו-פורמה ודוח שעות."
            )
        case .english:
            return InvoiceStrings(
                title: "Proforma Invoice",
                projectLabel: "Project",
                periodLabel: "Period",
                dateLabel: "Date",
                invoiceNumberLabel: "Invoice #",
                descriptionHeader: "Description",
                hoursHeader: "Hours",
                rateHeader: "Rate",
                amountHeader: "Amount",
                subtotalLabel: "Subtotal",
                vatLabelFormat: "VAT (%.0f%%)",
                vatIncludedLabel: "VAT (included)",
                totalLabel: "Total",
                signatureLabel: "Signature",
                emailLine: "Attached are the proforma invoice and timesheet."
            )
        }
    }

    private func localizedRangeTitle(for project: Project) -> String {
        let formatter = DateFormatter()
        formatter.locale = project.invoiceLanguage == .hebrew ? Locale(identifier: "he_IL") : Locale(identifier: "en_US")
        formatter.dateStyle = .medium
        let startText = formatter.string(from: rangeStart)
        let endText = formatter.string(from: rangeEnd)
        if startText == endText {
            return startText
        }
        return "\(startText) - \(endText)"
    }

    private func makeProformaPDF(
        logoData: Data,
        signatureData: Data,
        project: Project,
        header: String,
        itemDetails: String,
        hours: Double,
        rate: Double,
        subtotal: Double,
        vatAmount: Double,
        total: Double,
        date: Date,
        invoiceNumber: Int,
        rangeText: String
    ) -> Data? {
        let strings = invoiceStrings(for: project)
        let isRTL = project.invoiceLanguage == .hebrew
        let locale = isRTL ? Locale(identifier: "he_IL") : Locale(identifier: "en_US")
        let dateFormatter = DateFormatter()
        dateFormatter.locale = locale
        dateFormatter.dateStyle = .medium
        let dateText = dateFormatter.string(from: date)

        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
        let margin: CGFloat = 36
        let contentWidth = pageRect.width - margin * 2
        let headerHeight: CGFloat = 80
        let logoAreaWidth = contentWidth * 0.4
        let titleAreaWidth = contentWidth - logoAreaWidth

        let titleFont = UIFont.systemFont(ofSize: 24, weight: .semibold)
        let subtitleFont = UIFont.systemFont(ofSize: 14, weight: .regular)
        let bodyFont = UIFont.systemFont(ofSize: 12, weight: .regular)
        let boldFont = UIFont.systemFont(ofSize: 12, weight: .semibold)
        let borderColor = UIColor(white: 0.82, alpha: 1)
        let headerFill = UIColor(white: 0.92, alpha: 1)
        let boxFill = UIColor(white: 0.96, alpha: 1)

        let bodyParagraph = NSMutableParagraphStyle()
        bodyParagraph.alignment = isRTL ? .right : .left
        bodyParagraph.baseWritingDirection = isRTL ? .rightToLeft : .leftToRight

        let numberParagraph = NSMutableParagraphStyle()
        numberParagraph.alignment = .right
        numberParagraph.baseWritingDirection = isRTL ? .rightToLeft : .leftToRight

        let headerParagraph = NSMutableParagraphStyle()
        headerParagraph.alignment = .right
        headerParagraph.baseWritingDirection = isRTL ? .rightToLeft : .leftToRight

        func drawText(_ text: String, font: UIFont, rect: CGRect, paragraph: NSParagraphStyle) {
            let attributes: [NSAttributedString.Key: Any] = [
                .font: font,
                .paragraphStyle: paragraph,
                .foregroundColor: UIColor.black
            ]
            text.draw(in: rect, withAttributes: attributes)
        }

        func drawParagraph(_ text: String, font: UIFont, atY y: CGFloat, lineSpacing: CGFloat = 4) -> CGFloat {
            let attributes: [NSAttributedString.Key: Any] = [
                .font: font,
                .paragraphStyle: bodyParagraph
            ]
            let bounding = text.boundingRect(
                with: CGSize(width: contentWidth, height: .greatestFiniteMagnitude),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: attributes,
                context: nil
            )
            let rect = CGRect(x: margin, y: y, width: contentWidth, height: bounding.height)
            text.draw(in: rect, withAttributes: attributes)
            return bounding.height + lineSpacing
        }

        func keyValueText(label: String, value: String) -> String {
            isRTL ? "\(value) :\(label)" : "\(label): \(value)"
        }

        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
        let data = renderer.pdfData { context in
            context.beginPage()
            var cursorY = margin

            if let logo = UIImage(data: logoData) {
                let maxLogoWidth: CGFloat = logoAreaWidth
                let maxLogoHeight: CGFloat = headerHeight
                let scale = min(maxLogoWidth / logo.size.width, maxLogoHeight / logo.size.height, 1)
                let logoSize = CGSize(width: logo.size.width * scale, height: logo.size.height * scale)
                let logoRect = CGRect(x: margin, y: cursorY, width: logoSize.width, height: logoSize.height)
                logo.draw(in: logoRect)
            }

            let titleRect = CGRect(
                x: margin + logoAreaWidth,
                y: cursorY,
                width: titleAreaWidth,
                height: headerHeight
            )
            drawText(strings.title, font: titleFont, rect: titleRect, paragraph: headerParagraph)

            let headerRect = CGRect(
                x: titleRect.minX,
                y: titleRect.minY + 30,
                width: titleAreaWidth,
                height: 24
            )
            drawText(header, font: subtitleFont, rect: headerRect, paragraph: headerParagraph)

            let invoiceMeta = "\(strings.invoiceNumberLabel): \(invoiceNumber)   \(strings.dateLabel): \(dateText)"
            let metaRect = CGRect(
                x: titleRect.minX,
                y: titleRect.minY + 52,
                width: titleAreaWidth,
                height: 20
            )
            drawText(invoiceMeta, font: bodyFont, rect: metaRect, paragraph: headerParagraph)

            cursorY += headerHeight + 12

            context.cgContext.setStrokeColor(borderColor.cgColor)
            context.cgContext.setLineWidth(1)
            context.cgContext.move(to: CGPoint(x: margin, y: cursorY))
            context.cgContext.addLine(to: CGPoint(x: pageRect.width - margin, y: cursorY))
            context.cgContext.strokePath()
            cursorY += 12

            let infoRect = CGRect(x: margin, y: cursorY, width: contentWidth, height: 74)
            context.cgContext.setFillColor(boxFill.cgColor)
            context.cgContext.fill(infoRect)
            context.cgContext.setStrokeColor(borderColor.cgColor)
            context.cgContext.stroke(infoRect)

            var infoY = infoRect.minY + 10
            infoY += drawParagraph(keyValueText(label: strings.projectLabel, value: project.name), font: bodyFont, atY: infoY)
            infoY += drawParagraph(keyValueText(label: strings.periodLabel, value: rangeText), font: bodyFont, atY: infoY, lineSpacing: 0)

            cursorY = infoRect.maxY + 16

            let columnWidths = [
                contentWidth * 0.46,
                contentWidth * 0.18,
                contentWidth * 0.18,
                contentWidth * 0.18
            ]
            var columnX: [CGFloat] = []
            if isRTL {
                var x = pageRect.width - margin
                for width in columnWidths {
                    x -= width
                    columnX.append(x)
                }
            } else {
                var x = margin
                for width in columnWidths {
                    columnX.append(x)
                    x += width
                }
            }

            func drawRow(values: [String], isHeader: Bool, fill: UIColor? = nil) -> CGFloat {
                let font = isHeader ? boldFont : bodyFont
                let attributesForText: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .paragraphStyle: bodyParagraph
                ]
                let attributesForNumber: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .paragraphStyle: numberParagraph
                ]
                var rowHeight: CGFloat = 0
                for index in 0..<values.count {
                    let width = columnWidths[index]
                    let attributes = index == 0 ? attributesForText : attributesForNumber
                    let bounding = values[index].boundingRect(
                        with: CGSize(width: width, height: .greatestFiniteMagnitude),
                        options: [.usesLineFragmentOrigin, .usesFontLeading],
                        attributes: attributes,
                        context: nil
                    )
                    rowHeight = max(rowHeight, bounding.height)
                }
                rowHeight += 10
                if let fill {
                    let rect = CGRect(x: margin, y: cursorY, width: contentWidth, height: rowHeight)
                    context.cgContext.setFillColor(fill.cgColor)
                    context.cgContext.fill(rect)
                }
                for index in 0..<values.count {
                    let rect = CGRect(x: columnX[index], y: cursorY + 4, width: columnWidths[index], height: rowHeight - 8)
                    let attributes = index == 0 ? attributesForText : attributesForNumber
                    values[index].draw(in: rect, withAttributes: attributes)
                }
                context.cgContext.setStrokeColor(borderColor.cgColor)
                context.cgContext.setLineWidth(0.5)
                context.cgContext.move(to: CGPoint(x: margin, y: cursorY + rowHeight))
                context.cgContext.addLine(to: CGPoint(x: pageRect.width - margin, y: cursorY + rowHeight))
                context.cgContext.strokePath()
                return rowHeight
            }

            let headerRowHeight = drawRow(
                values: [strings.descriptionHeader, strings.hoursHeader, strings.rateHeader, strings.amountHeader],
                isHeader: true,
                fill: headerFill
            )
            cursorY += headerRowHeight

            let hoursText = String(format: "%.2f", hours)
            let rateText = String(format: "$%.2f", rate)
            let amountText = String(format: "$%.2f", hours * rate)
            let rowHeight = drawRow(
                values: [itemDetails, hoursText, rateText, amountText],
                isHeader: false
            )
            cursorY += rowHeight + 12

            let summaryWidth = contentWidth * 0.46
            let summaryX = pageRect.width - margin - summaryWidth
            let summaryRect = CGRect(x: summaryX, y: cursorY, width: summaryWidth, height: 90)
            context.cgContext.setFillColor(boxFill.cgColor)
            context.cgContext.fill(summaryRect)
            context.cgContext.setStrokeColor(borderColor.cgColor)
            context.cgContext.stroke(summaryRect)

            var summaryY = summaryRect.minY + 10
            let summaryParagraph = NSMutableParagraphStyle()
            summaryParagraph.alignment = isRTL ? .right : .left
            summaryParagraph.baseWritingDirection = isRTL ? .rightToLeft : .leftToRight

            func drawSummary(label: String, value: String, bold: Bool = false) {
                let font = bold ? boldFont : bodyFont
                let text = keyValueText(label: label, value: value)
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .paragraphStyle: summaryParagraph
                ]
                let rect = CGRect(x: summaryRect.minX + 10, y: summaryY, width: summaryRect.width - 20, height: 18)
                text.draw(in: rect, withAttributes: attributes)
                summaryY += 18
            }

            drawSummary(label: strings.subtotalLabel, value: String(format: "$%.2f", subtotal))
            if project.vatType != .none {
                let vatLabel = project.vatType == .included
                    ? strings.vatIncludedLabel
                    : String(format: strings.vatLabelFormat, vatRate * 100)
                drawSummary(label: vatLabel, value: String(format: "$%.2f", vatAmount))
            }
            drawSummary(label: strings.totalLabel, value: String(format: "$%.2f", total), bold: true)

            let signatureTop = max(summaryRect.maxY + 24, pageRect.height - margin - 110)
            if let signatureImage = UIImage(data: signatureData) {
                let signatureWidth: CGFloat = 200
                let signatureHeight: CGFloat = 70
                let scale = min(signatureWidth / signatureImage.size.width, signatureHeight / signatureImage.size.height, 1)
                let signatureSize = CGSize(
                    width: signatureImage.size.width * scale,
                    height: signatureImage.size.height * scale
                )
                let signatureLabelRect = CGRect(
                    x: margin,
                    y: signatureTop,
                    width: contentWidth,
                    height: 16
                )
                drawText(strings.signatureLabel, font: bodyFont, rect: signatureLabelRect, paragraph: bodyParagraph)

                let signatureX = margin
                let signatureY = signatureTop + 20
                let signatureRect = CGRect(x: signatureX, y: signatureY, width: signatureSize.width, height: signatureSize.height)
                signatureImage.draw(in: signatureRect)
            }
        }
        return data
    }

    private func resolveClientId(
        project: Project?,
        client: GreenInvoiceClient,
        clientKey: String,
        cachedClientId: String? = nil
    ) async throws -> String? {
        let trimmedCached = cachedClientId?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let trimmedCached, !trimmedCached.isEmpty {
            return trimmedCached
        }
        let found = try await client.fetchClientId(accountingKey: clientKey)
        if let found, let project {
            await MainActor.run {
                if let index = store.projects.firstIndex(where: { $0.id == project.id }) {
                    store.projects[index].greenInvoiceClientId = found
                }
            }
        }
        return found
    }

    private var rangeTitle: String {
        let startText = Self.rangeFormatter.string(from: rangeStart)
        let endText = Self.rangeFormatter.string(from: rangeEnd)
        if startText == endText {
            return startText
        }
        return "\(startText) - \(endText)"
    }

    private var rangeMonthTitle: String {
        Self.monthFormatter.string(from: rangeStart)
    }

    private struct PendingInvoiceRequest: Identifiable {
        let id = UUID()
        let projectId: UUID
        let subject: String
        let itemDescription: String
        let quantity: Double
        let unitPrice: Double
        let date: Date
        let clientKey: String
        let clientId: String?
        let vatType: VatType
        let isSandbox: Bool
        let updatePreview: Bool
    }

    private struct ProjectTotal: Identifiable {
        let project: Project
        let minutes: Int
        let amount: Double

        var id: UUID { project.id }
    }

    private struct AlertMessage: Identifiable {
        let id = UUID()
        let title: String
        let message: String
    }

    private struct InvoicePreview: Identifiable {
        let id = UUID()
        var json: String
        let projectId: UUID
        let subject: String
        let itemDescription: String
        let quantity: Double
        let unitPrice: Double
        let date: Date
        let clientKey: String
        let vatType: VatType
        var clientId: String?
        var responseJSON: String?
    }

    private struct InvoicePreviewSheet: View {
        let preview: InvoicePreview
        @Binding var isSending: Bool
        let onCopy: () -> Void
        let onCopyResult: () -> Void
        let onSend: () -> Void

        @Environment(\.dismiss) private var dismiss

        var body: some View {
            NavigationStack {
                VStack(spacing: 16) {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Payload JSON")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(preview.json)
                                    .font(.system(.caption, design: .monospaced))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(12)
                                    .background(Color.black.opacity(0.04))
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }

                            if let responseJSON = preview.responseJSON {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("Result JSON")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Text(responseJSON)
                                        .font(.system(.caption, design: .monospaced))
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(12)
                                        .background(Color.black.opacity(0.04))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                            }
                        }
                    }

                    HStack(spacing: 12) {
                        Button("Copy Payload") {
                            onCopy()
                        }
                        .buttonStyle(.bordered)

                        if preview.responseJSON != nil {
                            Button("Copy Result") {
                                onCopyResult()
                            }
                            .buttonStyle(.bordered)
                        }

                        Spacer()

                        Button {
                            onSend()
                        } label: {
                            if isSending {
                                HStack(spacing: 6) {
                                    ProgressView()
                                    Text("Sending")
                                }
                            } else {
                                Text("Send")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isSending)
                    }
                }
                .padding()
                .navigationTitle("Invoice JSON")
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Close") {
                            dismiss()
                        }
                    }
                }
            }
        }
    }


    private static let monthFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "LLLL yyyy"
        return formatter
    }()

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter
    }()

    private static let fileFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let rangeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        return formatter
    }()
}

struct ScreenshotRootView: View {
    @EnvironmentObject private var store: TimeStore

    private let viewName: String
    private let referenceDate: Date
    @State private var isSeeded = false

    init(viewName: String, referenceDate: Date) {
        self.viewName = viewName
        self.referenceDate = referenceDate
    }

    var body: some View {
        Group {
            switch viewName.lowercased() {
            case "summary":
                MonthSummaryView(monthDate: referenceDate)
            case "list":
                TimeListView(
                    referenceDate: referenceDate,
                    selectedProjectId: store.projects.first?.id
                )
            case "settings":
                SettingsView()
            default:
                DayView(initialDate: referenceDate)
            }
        }
        .onAppear {
            guard !isSeeded else { return }
            store.seedSampleData(referenceDate: referenceDate)
            isSeeded = true
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var store: TimeStore
    @Environment(\.dismiss) private var dismiss
    @State private var logoPickerItem: PhotosPickerItem?
    @State private var showSignatureCapture = false

#if DEBUG
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @AppStorage("hasSeenWalkthrough") private var hasSeenWalkthrough = false
    @AppStorage("debugRunOnboarding") private var debugRunOnboarding = false
    @AppStorage("debugRunWalkthrough") private var debugRunWalkthrough = false
#endif

    var body: some View {
        NavigationStack {
            List {
                Section("Projects") {
                    ForEach($store.projects) { $project in
                        NavigationLink {
                            ProjectDetailView(project: $project)
                        } label: {
                            let projectValue = project
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(Color(hex: projectValue.colorHex))
                                    .frame(width: 12, height: 12)
                                Text(projectValue.name.isEmpty ? "Untitled Project" : projectValue.name)
                                Spacer()
                                Text(String(format: "$%.2f", projectValue.rate))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .onDelete(perform: store.removeProjects)

                    Button {
                        store.addProject()
                    } label: {
                        Label("Add Project", systemImage: "plus")
                    }
                }

                Section("iCloud Sync") {
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(store.isICloudAvailable ? "Available" : "Unavailable")
                            .foregroundColor(store.isICloudAvailable ? .secondary : .red)
                    }
                    HStack {
                        Text("Last Sync")
                        Spacer()
                        Text(store.lastCloudSyncLabel)
                            .foregroundStyle(.secondary)
                    }
                    Text("Sync uses iCloud Drive to keep data in sync across devices.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Invoice PDF") {
                    if let data = store.invoiceLogoData, let image = UIImage(data: data) {
                        HStack {
                            Spacer()
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFit()
                                .frame(maxHeight: 80)
                            Spacer()
                        }
                    }

                    PhotosPicker(selection: $logoPickerItem, matching: .images) {
                        Label(store.invoiceLogoData == nil ? "Add Logo" : "Change Logo", systemImage: "photo")
                    }

                    if store.invoiceLogoData != nil {
                        Button("Remove Logo", role: .destructive) {
                            store.invoiceLogoData = nil
                        }
                    }

                    if let data = store.invoiceSignatureData, let image = UIImage(data: data) {
                        HStack {
                            Spacer()
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFit()
                                .frame(maxHeight: 60)
                            Spacer()
                        }
                    }

                    Button(store.invoiceSignatureData == nil ? "Add Signature" : "Change Signature") {
                        showSignatureCapture = true
                    }

                    if store.invoiceSignatureData != nil {
                        Button("Remove Signature", role: .destructive) {
                            store.invoiceSignatureData = nil
                        }
                    }

                    HStack {
                        Text("Start Number")
                        Spacer()
                        TextField("1", value: $store.invoiceStartNumber, format: .number)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 120)
                    }

                    Text("Next invoice number: \(store.nextInvoiceNumber())")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Text("Logo and signature appear on the proforma PDF invoice.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

#if DEBUG
                Section("Debug") {
                    Button("Run Onboarding + Tour") {
                        debugRunOnboarding = true
                        debugRunWalkthrough = true
                        dismiss()
                    }

                    Button("Run Walkthrough Only") {
                        debugRunWalkthrough = true
                        dismiss()
                    }

                    Button("Reset Intro Flags") {
                        hasSeenOnboarding = false
                        hasSeenWalkthrough = false
                    }
                }
#endif
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    EditButton()
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .onChange(of: logoPickerItem) { newItem in
            guard let newItem else { return }
            Task {
                if let data = try? await newItem.loadTransferable(type: Data.self),
                   let resized = resizedLogoData(from: data) {
                    await MainActor.run {
                        store.invoiceLogoData = resized
                    }
                }
            }
        }
        .sheet(isPresented: $showSignatureCapture) {
            SignatureCaptureSheet(signatureData: $store.invoiceSignatureData)
        }
    }

    private func resizedLogoData(from data: Data) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let maxDimension: CGFloat = 600
        let size = image.size
        let scale = min(maxDimension / max(size.width, size.height), 1)
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let scaledImage = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
        return scaledImage.pngData()
    }
}

struct ProjectDetailView: View {
    @Binding var project: Project

    var body: some View {
        Form {
            Section("Basics") {
                TextField("Project name", text: $project.name)
                ColorPicker(
                    "Color",
                    selection: Binding(
                        get: { Color(hex: project.colorHex) },
                        set: { project.colorHex = $0.toHex() }
                    ),
                    supportsOpacity: false
                )
                HStack(spacing: 8) {
                    Text("$")
                        .foregroundStyle(.secondary)
                    TextField("Rate", value: $project.rate, format: .number)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 120)
                }
            }

            Section("Timesheet Email") {
                TextField("Recipients (comma separated)", text: $project.timesheetEmails)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Subject", text: $project.timesheetSubject)
                TextField("Pretext", text: $project.timesheetPretext, axis: .vertical)
                    .lineLimit(3, reservesSpace: true)
            }

            Section("Invoice") {
                TextField("Header (month added automatically)", text: $project.invoiceHeader)
                TextField("Item details", text: $project.invoiceItemDetails, axis: .vertical)
                    .lineLimit(2, reservesSpace: true)
                Picker("Language", selection: $project.invoiceLanguage) {
                    ForEach(InvoiceLanguage.allCases) { language in
                        Text(language.title).tag(language)
                    }
                }
                .pickerStyle(.menu)
                Picker("VAT", selection: $project.vatType) {
                    ForEach(VatType.allCases) { vatType in
                        Text(vatType.title).tag(vatType)
                    }
                }
                .pickerStyle(.menu)
            }

        }
        .navigationTitle(project.name.isEmpty ? "Project" : project.name)
        .onChange(of: project.greenInvoiceCustomerKey) { _ in
            project.greenInvoiceClientId = ""
        }
    }
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
