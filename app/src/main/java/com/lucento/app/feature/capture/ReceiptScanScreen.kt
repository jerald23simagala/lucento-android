package com.lucento.app.feature.capture

import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.LucentoLottieAnimation
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.capture.ReceiptParser
import com.lucento.app.domain.capture.ReceiptSuggestion
import com.lucento.app.domain.capture.TotalConfidence
import com.lucento.app.feature.plan.formatDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** What the user confirmed on the scan screen; the transaction editor opens with these values. */
data class ReceiptResult(
    val merchant: String?,
    val amount: BigDecimal?,
    val date: LocalDate?,
    /** Currency printed on the receipt, if one was recognised unambiguously. Never converted. */
    val currencyCode: String? = null,
)

private sealed interface ScanState {
    data object Idle : ScanState
    data object Reading : ScanState
    data class Done(val suggestion: ReceiptSuggestion, val lines: List<String>, val preview: Bitmap?) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Receipt capture: take a photo (via the system camera app — Lucento itself needs no camera
 * permission) or pick an image, read it on the device, then hand editable suggestions to the normal
 * transaction editor. Nothing is saved from this screen.
 */
@Composable
fun ReceiptScanScreen(
    onBack: () -> Unit,
    onReview: (ReceiptResult) -> Unit,
    /** Currency of the wallet the expense will most likely go to; decides how "1,250" or "12.500" is read. */
    expectedCurrency: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ScanState>(ScanState.Idle) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var chosenAmount by remember { mutableStateOf<BigDecimal?>(null) }

    fun read(uri: Uri, cleanup: File?) {
        state = ScanState.Reading
        scope.launch {
            state = try {
                val fragments = ReceiptOcr.recognize(context, uri)
                val preview = ReceiptOcr.preview(context, uri)
                val lines = ReceiptParser.mergeRows(fragments)
                if (lines.isEmpty()) {
                    ScanState.Failed("No text was found. Try again with the whole receipt in view, flat and well lit.")
                } else {
                    val suggestion = ReceiptParser.parse(lines, LocalDate.now(), expectedCurrency)
                    chosenAmount = suggestion.total
                    ScanState.Done(suggestion, lines, preview)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanState.Failed("Lucento couldn’t read that image. You can try another photo or enter the details yourself.")
            } finally {
                // The photo was only needed for reading; remove it so receipts don't pile up on the device.
                cleanup?.delete()
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhoto
        pendingPhoto = null
        if (saved && file != null) {
            read(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), cleanup = file)
        } else {
            file?.delete()
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(uri, cleanup = null)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        pendingPhoto = file
        try {
            takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: ActivityNotFoundException) {
            pendingPhoto = null
            state = ScanState.Failed("No camera app is available. Choose an image instead.")
        }
    }

    fun launchPicker() {
        try {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            state = ScanState.Failed("No photo picker is available on this device.")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Scan receipt", onBack = onBack)
        when (val s = state) {
            ScanState.Idle -> {
                EmptyState(
                    title = "Capture a receipt",
                    body = "Lucento reads the shop, date and total on this device. You’ll check everything before it’s saved.",
                    illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
                )
                CaptureButtons(::launchCamera, ::launchPicker)
                Spacer(Modifier.height(Spacing.md))
                InfoNote("Photos are read on your phone and deleted afterwards. Nothing is uploaded — Lucento has no internet access.")
            }
            ScanState.Reading -> {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Loops only while recognition is actually running; replaced by results or an error as soon as
                    // it finishes, which disposes the animation. Under reduced motion it is a still frame.
                    LucentoLottieAnimation(
                        animation = LucentoAnimation.ReceiptScanning,
                        modifier = Modifier.size(width = 220.dp, height = 184.dp),
                        fallback = { CircularProgressIndicator() },
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "Reading the receipt on this phone…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        "Nothing is saved until you review it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ScanState.Failed -> {
                EmptyState(
                    title = "Couldn’t read this receipt",
                    body = s.message,
                    illustration = { LucentoMascot(size = 88.dp, mood = MascotMood.Curious) },
                )
                CaptureButtons(::launchCamera, ::launchPicker)
                Spacer(Modifier.height(Spacing.md))
                LucentoTextButton(text = "Enter it manually instead", onClick = { onReview(ReceiptResult(null, null, null)) })
            }
            is ScanState.Done -> ScanResult(
                state = s,
                chosenAmount = chosenAmount,
                onChooseAmount = { chosenAmount = it },
                onRetake = { state = ScanState.Idle },
                onReview = { onReview(ReceiptResult(s.suggestion.merchant, chosenAmount, s.suggestion.date, s.suggestion.currencyCode)) },
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun CaptureButtons(onCamera: () -> Unit, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LucentoPrimaryButton(text = "Take a photo", icon = Icons.Outlined.CameraAlt, onClick = onCamera)
        LucentoSecondaryButton(text = "Choose an image", icon = Icons.Outlined.PhotoLibrary, onClick = onPick)
    }
}

@Composable
private fun ScanResult(
    state: ScanState.Done,
    chosenAmount: BigDecimal?,
    onChooseAmount: (BigDecimal) -> Unit,
    onRetake: () -> Unit,
    onReview: () -> Unit,
) {
    val s = state.suggestion
    var showText by remember { mutableStateOf(false) }
    state.preview?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "The receipt photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(MaterialTheme.shapes.large),
        )
        Spacer(Modifier.height(Spacing.md))
    }
    LucentoCard(Modifier.fillMaxWidth()) {
        Text("Suggestions — please check", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.md))
        Figure("Shop") { Text(s.merchant ?: "Not found", style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Figure("Total", Modifier.weight(1f)) {
                Text(chosenAmount?.toPlainString() ?: "Not found", style = MaterialTheme.typography.titleLarge)
            }
            when (s.totalConfidence) {
                TotalConfidence.LABELLED -> StatusPill("Read from “Total”", StatusTones.positive)
                TotalConfidence.LARGEST_AMOUNT -> StatusPill("Best guess", StatusTones.warning)
                TotalConfidence.NONE -> StatusPill("Enter it yourself", StatusTones.neutral)
            }
        }
        if (s.amounts.size > 1) {
            Spacer(Modifier.height(Spacing.sm))
            Text("Other amounts on the receipt:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                s.amounts.forEach { amount ->
                    ChoicePill(label = amount.toPlainString(), selected = amount == chosenAmount, onClick = { onChooseAmount(amount) })
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Figure("Date") {
            Text(
                (s.date?.let(::formatDate) ?: "Not found — today will be used") + if (s.dateAmbiguous) " (day/month order unclear)" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    LucentoPrimaryButton(text = "Review and save", onClick = onReview)
    Spacer(Modifier.height(Spacing.sm))
    LucentoSecondaryButton(text = "Scan another", onClick = onRetake)
    Spacer(Modifier.height(Spacing.md))
    LucentoTextButton(text = if (showText) "Hide what Lucento read" else "Show what Lucento read", onClick = { showText = !showText })
    if (showText) {
        SectionHeader("Recognised text")
        state.lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
