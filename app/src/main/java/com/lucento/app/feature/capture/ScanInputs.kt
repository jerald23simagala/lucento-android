package com.lucento.app.feature.capture

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.lucento.app.core.designsystem.component.FactTick
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.capture.HeldLine
import com.lucento.app.domain.capture.PageOrigin
import com.lucento.app.domain.capture.StatementReport
import java.io.File
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The ways a document comes into the composer. Every one is read on this phone. */
class ScanLaunchers(
    /** ML Kit's Document Scanner: flattened, straightened, several pages; the camera opens instead when it can't run. */
    val scanDocument: () -> Unit,
    /** The system camera, one photo (the fallback, and for phones without Google Play services). */
    val takePhoto: () -> Unit,
    /** Images already on the phone (screenshots of an app's history, saved receipts), up to [MAX_IMAGES]. */
    val chooseImages: () -> Unit,
    /** A PDF statement or invoice. */
    val importPdf: () -> Unit,
)

/** A scan session's page limit: a month's statement, or a long receipt in parts. */
const val SCAN_PAGE_LIMIT = 10
const val MAX_IMAGES = 10

/**
 * The launchers behind [ScanLaunchers]. [onPdfLocked] asks for a locked PDF's password ([PdfPasswordDialog]);
 * [onUnavailable] reports a launcher this phone doesn't have.
 */
@Composable
fun rememberScanLaunchers(
    viewModel: ComposerViewModel,
    onPdfLocked: (uri: Uri, wrongPassword: Boolean) -> Unit,
    onNotice: (String) -> Unit,
): ScanLaunchers {
    val context = LocalContext.current
    val app = context.applicationContext
    var pendingPhoto by remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhoto
        pendingPhoto = null
        if (saved && file != null) {
            viewModel.readReceipt(app, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), cleanup = file)
        } else {
            file?.delete()
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) viewModel.readImages(app, uris, PageOrigin.PHOTO)
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readPdf(app, uri) { wrong -> onPdfLocked(uri, wrong) }
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri }
        // The scanner's JPEGs land in Lucento's own cache: delete them once read, as with camera photos.
        if (pages.isNotEmpty()) viewModel.readImages(app, pages, PageOrigin.SCANNER, cleanup = pages.mapNotNull { ownCacheFile(app, it) })
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        pendingPhoto = file
        try {
            takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: ActivityNotFoundException) {
            pendingPhoto = null
            file.delete()
            viewModel.receiptUnavailable("No camera app is available. Choose an image instead.")
        }
    }

    fun launchScanner() {
        val activity = context.findActivity() ?: return launchCamera()
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(SCAN_PAGE_LIMIT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener {
                // No Google Play services, too little memory, or the scanner couldn't be fetched: the camera still works.
                onNotice("The document scanner isn’t available on this phone right now, so the camera opened instead.")
                launchCamera()
            }
    }

    return remember(viewModel) {
        ScanLaunchers(
            scanDocument = ::launchScanner,
            takePhoto = ::launchCamera,
            chooseImages = {
                try {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: ActivityNotFoundException) {
                    viewModel.receiptUnavailable("No photo picker is available on this phone.")
                }
            },
            importPdf = {
                try {
                    pickPdf.launch(arrayOf("application/pdf"))
                } catch (e: ActivityNotFoundException) {
                    viewModel.receiptUnavailable("No file picker is available on this phone.")
                }
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A `file:` URI inside Lucento's own cache (where the Document Scanner writes its pages), as a file to delete. */
private fun ownCacheFile(context: Context, uri: Uri): File? {
    if (uri.scheme != "file") return null
    val file = File(uri.path ?: return null)
    val cache = context.cacheDir.canonicalPath
    return file.takeIf { runCatching { it.canonicalPath.startsWith(cache) }.getOrDefault(false) }
}

/**
 * A locked PDF's password, asked once. It opens this file on this phone and is never saved — not even across screen
 * rotation (plain [remember], not saved state).
 */
@Composable
fun PdfPasswordDialog(wrong: Boolean, onUnlock: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This PDF is locked") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Enter the password your bank gave for it. Lucento uses it to open this file once, on this phone, and doesn’t keep it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (wrong) {
                    Text("That password didn’t open it. Try again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("PDF password") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onUnlock(password) }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onUnlock(password) }, enabled = password.isNotEmpty()) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * What a scan found besides its rows (docs/SCAN.md "Checking a statement"): lines already saved and held back (each can
 * still be added), lines marked below, the statement's balance next to Lucento's, fees, and a card's figures. Nothing
 * here changes a saved transaction.
 */
@Composable
fun StatementCheck(read: MessageState.Read, wallets: List<Wallet>, onAddHeld: (Int) -> Unit) {
    val format = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    var showHeld by rememberSaveable { mutableStateOf(false) }
    var showMissing by rememberSaveable { mutableStateOf(false) }
    val savedHeld = read.held.count { it.saved != null }
    val waitingHeld = read.held.size - savedHeld
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (read.statements.isNotEmpty()) {
            Text(
                "Checked against Lucento",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
        }
        if (read.held.isNotEmpty()) {
            val parts = buildList {
                if (savedHeld > 0) add(if (savedHeld == 1) "1 line is saved already" else "$savedHeld lines are saved already")
                if (waitingHeld > 0) add(if (waitingHeld == 1) "1 is already among the rows above" else "$waitingHeld are already among the rows above")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${parts.joinToString("; ").replaceFirstChar { it.titlecase() }} — not added.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                LucentoTextButton(if (showHeld) "Hide" else "Show", onClick = { showHeld = !showHeld })
            }
            if (showHeld) {
                read.held.forEachIndexed { index, line ->
                    HeldRow(line, wallets, format, onAdd = { onAddHeld(index) })
                }
            }
        }
        read.statements.forEach { report -> ReportFacts(report, wallets, format, showMissing, onToggleMissing = { showMissing = !showMissing }) }
    }
}

@Composable
private fun HeldRow(line: HeldLine, wallets: List<Wallet>, format: DateTimeFormatter, onAdd: () -> Unit) {
    val c = line.candidate
    val wallet = wallets.firstOrNull { it.id == (c.walletId ?: c.toWalletId) }
    val amount = c.amount?.let { a -> wallet?.let { w -> money(a, w.currencyCode) } }
    val signed = amount?.let { m ->
        when (c.type) {
            TransactionType.EXPENSE -> MoneyFormatter.formatSigned(-m)
            TransactionType.INCOME -> MoneyFormatter.formatSigned(m)
            else -> MoneyFormatter.format(m)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(c.description, c.date.format(format), signed).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(line.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.xs))
        LucentoTextButton("Add anyway", onClick = onAdd)
    }
}

@Composable
private fun ReportFacts(report: StatementReport, wallets: List<Wallet>, format: DateTimeFormatter, showMissing: Boolean, onToggleMissing: () -> Unit) {
    val code = report.currency ?: wallets.firstOrNull { it.id == report.accountId }?.currencyCode
    fun show(value: BigDecimal) = code?.let { c -> money(value, c)?.let(MoneyFormatter::format) } ?: value.toPlainString()
    val marked = buildList {
        if (report.possible > 0) add(if (report.possible == 1) "1 may be saved already" else "${report.possible} may be saved already")
        if (report.repeated > 0) add(if (report.repeated == 1) "1 appears twice on the statement" else "${report.repeated} appear twice on the statement")
        if (report.pending > 0) add("${report.pending} pending at the bank")
        if (report.cancelling > 0) add(if (report.cancelling == 1) "1 charge was reversed on the statement" else "${report.cancelling} charges were reversed on the statement")
        if (report.refundsMatched > 0) add(if (report.refundsMatched == 1) "1 refund matches an earlier purchase" else "${report.refundsMatched} refunds match earlier purchases")
    }
    if (marked.isNotEmpty()) {
        Text("${marked.joinToString("; ").replaceFirstChar { it.titlecase() }} — see the marked rows.", style = MaterialTheme.typography.bodySmall)
    }
    if (report.notOnStatement.isNotEmpty()) {
        val n = report.notOnStatement.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${if (n == 1) "1 transaction" else "$n transactions"} in Lucento for these dates ${if (n == 1) "isn’t" else "aren’t"} on this statement.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            LucentoTextButton(if (showMissing) "Hide" else "Show", onClick = onToggleMissing)
        }
        if (showMissing) {
            report.notOnStatement.take(20).forEach { t -> Text(describe(t, format), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    report.balance?.let { b ->
        val owed = report.card
        if (b.matches) {
            FactTick(if (owed) "Owed on ${b.date.format(format)}: ${show(b.statement)} on the statement and in Lucento once these are saved" else "Balance on ${b.date.format(format)}: ${show(b.statement)} on the statement and in Lucento once these are saved")
        } else {
            InfoNote(
                (if (owed) "The statement says ${show(b.statement)} owed on ${b.date.format(format)}; Lucento will show ${show(b.afterSaving)}" else "The statement’s balance on ${b.date.format(format)} is ${show(b.statement)}; Lucento’s will be ${show(b.afterSaving)}") +
                    " after saving these rows — ${show(b.difference.abs())} apart. A transaction missing from Lucento or the scan, or a balance set by hand, would explain it. Nothing has been changed.",
            )
        }
    }
    if (report.addsUp == false) {
        InfoNote("The statement’s lines don’t add up from its opening to its closing balance as read — a line may be missing from the scan, or misread. Check the rows against the paper.")
    }
    if (report.feeLines > 0) {
        Text(
            "Fees and interest on this statement: ${show(report.fees)} (${if (report.feeLines == 1) "1 line" else "${report.feeLines} lines"}).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val s = report.summary
    if (report.card && (s.minimumDue != null || s.dueDate != null || s.creditLimit != null || s.availableCredit != null)) {
        val facts = listOfNotNull(
            s.minimumDue?.let { m -> "Minimum due ${show(m)}" + (s.dueDate?.let { " by ${it.format(format)}" } ?: "") } ?: s.dueDate?.let { "Payment due ${it.format(format)}" },
            s.creditLimit?.let { "Credit limit ${show(it)}" },
            s.availableCredit?.let { "Available ${show(it)}" },
        )
        Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun describe(t: Transaction, format: DateTimeFormatter): String {
    val signed = when {
        t.type.isInflow -> MoneyFormatter.formatSigned(t.amount)
        else -> MoneyFormatter.formatSigned(-t.amount)
    }
    return listOf(t.date.format(format), t.note.ifBlank { "(no note)" }, signed).joinToString(" · ")
}

/** A major-unit amount as [Money] in [code]; null when it doesn't fit the currency's decimals. */
private fun money(value: BigDecimal, code: String): Money? = runCatching {
    val digits = CurrencyRules.fractionDigits(code)
    Money(value.setScale(digits, java.math.RoundingMode.UNNECESSARY).movePointRight(digits).longValueExact(), code)
}.getOrNull()
