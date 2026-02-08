package il.co.simplevision.timetrack.util

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

data class MailAttachment(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

fun shareEmail(
    activity: Activity,
    recipients: List<String>,
    subject: String,
    body: String,
    attachments: List<MailAttachment>,
): Boolean {
    val dir = File(activity.cacheDir, "share")
    dir.mkdirs()

    val uris = ArrayList<android.net.Uri>()
    attachments.forEach { attachment ->
        val file = File(dir, attachment.fileName)
        file.writeBytes(attachment.bytes)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        uris.add(uri)
    }

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val resolved = activity.packageManager.queryIntentActivities(intent, 0)
    if (resolved.isNullOrEmpty()) return false

    activity.startActivity(Intent.createChooser(intent, "Send email"))
    return true
}

