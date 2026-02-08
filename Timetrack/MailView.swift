import Foundation
import MessageUI
import SwiftUI

struct MailAttachment {
    let data: Data
    let mimeType: String
    let fileName: String
}

struct MailData {
    let recipients: [String]
    let subject: String
    let body: String
    let attachments: [MailAttachment]
}

struct MailView: UIViewControllerRepresentable {
    static var canSendMail: Bool {
        MFMailComposeViewController.canSendMail()
    }

    @Binding var isPresented: Bool
    let data: MailData

    func makeCoordinator() -> Coordinator {
        Coordinator(isPresented: $isPresented)
    }

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let controller = MFMailComposeViewController()
        controller.setToRecipients(data.recipients)
        controller.setSubject(data.subject)
        controller.setMessageBody(data.body, isHTML: false)
        for attachment in data.attachments {
            controller.addAttachmentData(
                attachment.data,
                mimeType: attachment.mimeType,
                fileName: attachment.fileName
            )
        }
        controller.mailComposeDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: MFMailComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        @Binding var isPresented: Bool

        init(isPresented: Binding<Bool>) {
            _isPresented = isPresented
        }

        func mailComposeController(
            _ controller: MFMailComposeViewController,
            didFinishWith result: MFMailComposeResult,
            error: Error?
        ) {
            isPresented = false
        }
    }
}
