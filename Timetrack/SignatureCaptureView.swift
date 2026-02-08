import SwiftUI
import UIKit

struct SignatureCaptureSheet: View {
    @Binding var signatureData: Data?
    @Environment(\.dismiss) private var dismiss

    @State private var lines: [[CGPoint]] = []
    @State private var currentLine: [CGPoint] = []
    @State private var canvasSize: CGSize = .zero
    @State private var showEmptyAlert = false
    @State private var initialImage: UIImage?

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                SignaturePad(
                    lines: $lines,
                    currentLine: $currentLine,
                    canvasSize: $canvasSize,
                    initialImage: initialImage
                )
                .frame(height: 220)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.secondary.opacity(0.4))
                )
                .padding(.horizontal, 16)

                Text("Sign with your finger.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Spacer()
            }
            .padding(.top, 16)
            .navigationTitle("Signature")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        saveSignature()
                    }
                }
                ToolbarItem(placement: .bottomBar) {
                    Button("Clear", role: .destructive) {
                        lines = []
                        currentLine = []
                        initialImage = nil
                    }
                }
            }
        }
        .alert("Signature is empty", isPresented: $showEmptyAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Please sign before saving.")
        }
        .onAppear {
            if let data = signatureData, let image = UIImage(data: data) {
                initialImage = image
            }
        }
    }

    private func saveSignature() {
        let allLines = lines + (currentLine.isEmpty ? [] : [currentLine])
        let hasPoints = allLines.contains { !$0.isEmpty }
        guard canvasSize.width > 0, canvasSize.height > 0 else {
            showEmptyAlert = true
            return
        }

        if !hasPoints {
            if let initialImage, let data = initialImage.pngData() {
                signatureData = data
                dismiss()
                return
            }
            showEmptyAlert = true
            return
        }

        guard let data = renderSignaturePNG(lines: allLines, size: canvasSize, baseImage: initialImage) else {
            showEmptyAlert = true
            return
        }
        signatureData = data
        dismiss()
    }

    private func renderSignaturePNG(lines: [[CGPoint]], size: CGSize, baseImage: UIImage?) -> Data? {
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: size))

            if let baseImage {
                let imageRect = aspectFitRect(for: baseImage.size, in: size)
                baseImage.draw(in: imageRect)
            }

            let path = UIBezierPath()
            path.lineWidth = 2
            path.lineCapStyle = .round
            path.lineJoinStyle = .round

            for line in lines {
                guard let first = line.first else { continue }
                path.move(to: first)
                for point in line.dropFirst() {
                    path.addLine(to: point)
                }
            }

            UIColor.black.setStroke()
            path.stroke()
        }
        return image.pngData()
    }

    private func aspectFitRect(for imageSize: CGSize, in canvasSize: CGSize) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0 else {
            return CGRect(origin: .zero, size: canvasSize)
        }
        let scale = min(canvasSize.width / imageSize.width, canvasSize.height / imageSize.height)
        let width = imageSize.width * scale
        let height = imageSize.height * scale
        let origin = CGPoint(
            x: (canvasSize.width - width) / 2,
            y: (canvasSize.height - height) / 2
        )
        return CGRect(origin: origin, size: CGSize(width: width, height: height))
    }
}

private struct SignaturePad: View {
    @Binding var lines: [[CGPoint]]
    @Binding var currentLine: [CGPoint]
    @Binding var canvasSize: CGSize
    let initialImage: UIImage?

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            ZStack {
                Color.white
                if let initialImage {
                    Image(uiImage: initialImage)
                        .resizable()
                        .scaledToFit()
                        .frame(width: size.width, height: size.height)
                }
                Path { path in
                    for line in lines + (currentLine.isEmpty ? [] : [currentLine]) {
                        guard let first = line.first else { continue }
                        path.move(to: first)
                        for point in line.dropFirst() {
                            path.addLine(to: point)
                        }
                    }
                }
                .stroke(Color.black, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { value in
                        if currentLine.isEmpty {
                            currentLine = [value.location]
                        } else {
                            currentLine.append(value.location)
                        }
                    }
                    .onEnded { _ in
                        lines.append(currentLine)
                        currentLine = []
                    }
            )
            .onAppear {
                canvasSize = size
            }
            .onChange(of: size) { newSize in
                canvasSize = newSize
            }
        }
    }
}
