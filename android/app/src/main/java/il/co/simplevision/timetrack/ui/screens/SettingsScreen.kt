package il.co.simplevision.timetrack.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import il.co.simplevision.timetrack.BuildConfig
import il.co.simplevision.timetrack.data.AppPrefs
import il.co.simplevision.timetrack.data.Project
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.util.colorFromHex
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: TimeStore,
    prefs: AppPrefs,
    hostActivity: FragmentActivity,
    onDone: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val snap by store.state.collectAsState()

    var startNumberText by remember { mutableStateOf(snap.invoiceStartNumber.toString()) }
    var showSignatureCapture by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val data = hostActivity.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val resized = resizeToPng(data, maxDimension = 600) ?: return@rememberLauncherForActivityResult
        store.setInvoiceLogoPng(resized)
    }

    val syncFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        hostActivity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        store.setCloudSyncUri(uri)
        store.syncNowToCloud()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Done") }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SectionHeader("Projects")
            }
            items(snap.projects) { project ->
                ProjectRow(
                    project = project,
                    onClick = { onOpenProject(project.id) },
                    onDelete = { store.removeProject(project.id) },
                )
                Divider()
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { store.addProject() }) {
                        Text("Add Project")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
            item { SectionHeader("Cloud Sync") }
            item {
                KeyValueRow("Status", store.cloudSyncStatusLabel())
                KeyValueRow("Last Sync", store.lastCloudSyncLabel())
                Text(
                    "Sync uses a user-selected file (e.g. in Google Drive) to keep data in sync across devices.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { syncFilePicker.launch("TimetrackStore.json") }) {
                        Text(if (snap.cloudSyncUri.isNullOrBlank()) "Choose Sync File" else "Change Sync File")
                    }
                    OutlinedButton(
                        onClick = { store.syncNowToCloud() },
                        enabled = !snap.cloudSyncUri.isNullOrBlank(),
                    ) {
                        Text("Sync Now")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
            item { SectionHeader("Invoice PDF") }
            item {
                val invoiceLogoBytes = snap.invoiceLogoPng
                if (invoiceLogoBytes != null) {
                    val bmp = remember(invoiceLogoBytes) {
                        BitmapFactory.decodeByteArray(invoiceLogoBytes, 0, invoiceLogoBytes.size)
                    }
                    if (bmp != null) {
                        Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .height(80.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = {
                        logoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) {
                        Text(if (invoiceLogoBytes == null) "Add Logo" else "Change Logo")
                    }
                    if (invoiceLogoBytes != null) {
                        OutlinedButton(onClick = { store.setInvoiceLogoPng(null) }) { Text("Remove") }
                    }
                }

                val invoiceSignatureBytes = snap.invoiceSignaturePng
                if (invoiceSignatureBytes != null) {
                    val bmp = remember(invoiceSignatureBytes) {
                        BitmapFactory.decodeByteArray(invoiceSignatureBytes, 0, invoiceSignatureBytes.size)
                    }
                    if (bmp != null) {
                        Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .height(60.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { showSignatureCapture = true }) {
                        Text(if (invoiceSignatureBytes == null) "Add Signature" else "Change Signature")
                    }
                    if (invoiceSignatureBytes != null) {
                        OutlinedButton(onClick = { store.setInvoiceSignaturePng(null) }) { Text("Remove") }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Start Number")
                    Spacer(modifier = Modifier.weight(1f))
                    TextField(
                        value = startNumberText,
                        onValueChange = {
                            startNumberText = it.filter { ch -> ch.isDigit() }
                            val parsed = startNumberText.toIntOrNull()
                            if (parsed != null) store.setInvoiceStartNumber(parsed)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.35f),
                    )
                }
                Text(
                    "Next invoice number: ${store.nextInvoiceNumber()}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Logo and signature appear on the proforma PDF invoice.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (BuildConfig.DEBUG) {
                item { Spacer(modifier = Modifier.height(10.dp)) }
                item { SectionHeader("Debug") }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = {
                            prefs.setDebugRunOnboarding(true)
                            prefs.setDebugRunWalkthrough(true)
                            onDone()
                        }) { Text("Run Onboarding + Tour") }
                        OutlinedButton(onClick = {
                            prefs.setDebugRunWalkthrough(true)
                            onDone()
                        }) { Text("Run Walkthrough Only") }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        OutlinedButton(onClick = { prefs.resetIntroFlags() }) { Text("Reset Intro Flags") }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showSignatureCapture) {
        SignatureCaptureDialog(
            initialPng = snap.invoiceSignaturePng,
            onDismiss = { showSignatureCapture = false },
            onSave = { png ->
                store.setInvoiceSignaturePng(png)
                showSignatureCapture = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(color = colorFromHex(project.colorHex), shape = CircleShape) {
            Spacer(modifier = Modifier.padding(6.dp))
        }
        Spacer(modifier = Modifier.padding(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (project.name.isBlank()) "Untitled Project" else project.name)
            Text(
                String.format(Locale.US, "$%.2f", project.rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClick) {
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

private fun resizeToPng(data: ByteArray, maxDimension: Int): ByteArray? {
    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
    val width = bmp.width
    val height = bmp.height
    val scale = minOf(maxDimension.toFloat() / maxOf(width, height).toFloat(), 1f)
    val newW = (width * scale).toInt()
    val newH = (height * scale).toInt()
    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, newW, newH, true)
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
