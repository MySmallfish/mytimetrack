package il.co.simplevision.timetrack.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class VatType(val rawValue: Int) {
    NONE(0),
    INCLUDED(1),
    EXCLUDED(2);

    val title: String
        get() = when (this) {
            NONE -> "No VAT"
            INCLUDED -> "VAT Included"
            EXCLUDED -> "Add VAT"
        }
}

@Serializable
enum class InvoiceLanguage(val rawValue: String) {
    ENGLISH("en"),
    HEBREW("he");

    val title: String
        get() = when (this) {
            ENGLISH -> "English"
            HEBREW -> "Hebrew"
        }
}

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String,
    val rate: Double = 0.0,
    val timesheetEmails: String = "",
    val timesheetSubject: String = "",
    val timesheetPretext: String = "",
    val invoiceHeader: String = "",
    val invoiceItemDetails: String = "",
    val invoiceLanguage: InvoiceLanguage = InvoiceLanguage.ENGLISH,
    val greenInvoiceCustomerKey: String = "",
    val greenInvoiceClientId: String = "",
    val vatType: VatType = VatType.NONE,
)

@Serializable
data class DayLog(
    val slots: List<String?>,
    val labels: List<String?> = List(slots.size) { null },
)

@Serializable
data class InvoiceRecord(
    val id: String = UUID.randomUUID().toString(),
    val clientKey: String,
    val clientId: String? = null,
    val dateEpochMillis: Long,
    val isSandbox: Boolean,
)

@Serializable
data class TimeStoreSnapshot(
    val lastUpdatedEpochMillis: Long = 0L,
    val projects: List<Project> = emptyList(),
    val logs: Map<String, DayLog> = emptyMap(),
    val invoiceRecords: List<InvoiceRecord> = emptyList(),
    val greenInvoiceApiKey: String = "",
    val greenInvoiceApiSecret: String = "",
    val greenInvoiceTestMode: Boolean = false,
    val invoiceLogoPng: ByteArray? = null,
    val invoiceSignaturePng: ByteArray? = null,
    val invoiceStartNumber: Int = 1,
    val invoiceNextNumber: Int? = null,
    val lastSelectedProjectId: String? = null,
    val cloudSyncUri: String? = null,
    val lastCloudSyncEpochMillis: Long? = null,
)

data class TimeEntry(
    val id: String = UUID.randomUUID().toString(),
    val dayKey: String,
    val startIndex: Int,
    val endIndex: Int,
    val projectId: String,
    val label: String?,
)

