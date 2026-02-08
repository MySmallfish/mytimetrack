import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

struct Palette {
    // Pastel background (soft blue -> warm peach).
    static let backgroundStart = CGColor(red: 0.90, green: 0.96, blue: 1.00, alpha: 1.0)
    static let backgroundEnd = CGColor(red: 1.00, green: 0.93, blue: 0.90, alpha: 1.0)

    static let ink = CGColor(red: 0.12, green: 0.18, blue: 0.22, alpha: 1.0)
    static let plate = CGColor(red: 1.00, green: 1.00, blue: 1.00, alpha: 0.30)
    // Matches the onboarding "Continue" button tint (OnboardingTheme.accent).
    static let accent = CGColor(red: 0.25, green: 0.62, blue: 0.60, alpha: 1.0)
}

func degreesToRadians(_ degrees: CGFloat) -> CGFloat {
    degrees * .pi / 180
}

func makeImage(size: Int, includeBackground: Bool) -> CGImage? {
    let width = size
    let height = size
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue

    guard let context = CGContext(
        data: nil,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: width * 4,
        space: colorSpace,
        bitmapInfo: bitmapInfo
    ) else {
        return nil
    }

    context.setAllowsAntialiasing(true)
    context.setShouldAntialias(true)
    context.interpolationQuality = .high

    let rect = CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height))

    if includeBackground {
        let colors = [Palette.backgroundStart, Palette.backgroundEnd] as CFArray
        if let gradient = CGGradient(colorsSpace: colorSpace, colors: colors, locations: [0.0, 1.0]) {
            let start = CGPoint(x: rect.minX, y: rect.maxY)
            let end = CGPoint(x: rect.maxX, y: rect.minY)
            context.drawLinearGradient(gradient, start: start, end: end, options: [])
        }
    }

    if includeBackground {
        // Subtle "glass" plate behind the clock.
        let plateInset = CGFloat(size) * 0.12
        let plateRect = rect.insetBy(dx: plateInset, dy: plateInset)
        context.setFillColor(Palette.plate)
        context.fillEllipse(in: plateRect)
    }

    let ringInset = CGFloat(size) * 0.18
    let ringRect = rect.insetBy(dx: ringInset, dy: ringInset)
    let ringWidth = CGFloat(size) * 0.078

    let ringAlpha: CGFloat = includeBackground ? 0.22 : 0.70
    let ringColor = CGColor(
        red: 0.12,
        green: 0.18,
        blue: 0.22,
        alpha: ringAlpha
    )

    // Outer ring with a soft shadow.
    context.saveGState()
    context.setShadow(
        offset: CGSize(width: 0, height: CGFloat(size) * 0.02),
        blur: CGFloat(size) * 0.04,
        color: CGColor(red: 0, green: 0, blue: 0, alpha: includeBackground ? 0.10 : 0.18)
    )
    context.setStrokeColor(ringColor)
    context.setLineWidth(ringWidth)
    context.strokeEllipse(in: ringRect)
    context.restoreGState()

    let center = CGPoint(x: rect.midX, y: rect.midY)
    let radius = ringRect.width / 2

    context.setStrokeColor(Palette.accent)
    context.setLineWidth(ringWidth)
    context.setLineCap(.round)
    context.addArc(center: center, radius: radius, startAngle: degreesToRadians(315), endAngle: degreesToRadians(75), clockwise: false)
    context.strokePath()

    context.setStrokeColor(ringColor)
    context.setLineWidth(ringWidth * 0.34)
    context.setLineCap(.round)
    context.move(to: center)
    let handAngle = degreesToRadians(35)
    let handLength = radius * 0.55
    let handEnd = CGPoint(x: center.x + cos(handAngle) * handLength, y: center.y + sin(handAngle) * handLength)
    context.addLine(to: handEnd)
    context.strokePath()

    context.setFillColor(ringColor)
    let dotRadius = ringWidth * 0.22
    let dotRect = CGRect(x: center.x - dotRadius, y: center.y - dotRadius, width: dotRadius * 2, height: dotRadius * 2)
    context.fillEllipse(in: dotRect)

    return context.makeImage()
}

func writePNG(_ image: CGImage, to path: String) throws {
    let url = URL(fileURLWithPath: path)
    guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        return
    }
    CGImageDestinationAddImage(destination, image, nil)
    CGImageDestinationFinalize(destination)
}

func writeImage(size: Int, includeBackground: Bool, path: String) throws {
    guard let image = makeImage(size: size, includeBackground: includeBackground) else { return }
    try writePNG(image, to: path)
}

let iconOutputs: [(Int, String)] = [
    (40, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-20@2x.png"),
    (60, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-20@3x.png"),
    (58, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-29@2x.png"),
    (87, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-29@3x.png"),
    (80, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-40@2x.png"),
    (120, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-40@3x.png"),
    (120, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-60@2x.png"),
    (180, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-60@3x.png"),
    (1024, "Timetrack/Assets.xcassets/AppIcon.appiconset/icon-1024.png")
]

for (size, path) in iconOutputs {
    try writeImage(size: size, includeBackground: true, path: path)
}

let launchOutputs: [(Int, String)] = [
    (300, "Timetrack/Assets.xcassets/LaunchLogo.imageset/launch-logo@1x.png"),
    (600, "Timetrack/Assets.xcassets/LaunchLogo.imageset/launch-logo@2x.png"),
    (900, "Timetrack/Assets.xcassets/LaunchLogo.imageset/launch-logo@3x.png")
]

for (size, path) in launchOutputs {
    try writeImage(size: size, includeBackground: false, path: path)
}
