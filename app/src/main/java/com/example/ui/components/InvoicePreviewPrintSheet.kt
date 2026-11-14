package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.Invoice
import com.example.data.model.PosSeeds
import com.example.data.model.StoreInfo
import com.example.data.model.WaRecipient
import com.example.data.model.formatDa
import com.example.data.model.formatM2
import com.example.data.model.toDzWhatsAppDigits
import com.example.ui.theme.Amber300
import com.example.ui.theme.Amber500
import com.example.ui.theme.Amber950
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Emerald300
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePreviewPrintSheet(
    invoice: Invoice,
    storeInfo: StoreInfo,
    settings: AppSettings,
    autoEmailPdf: Boolean = false,
    autoWhatsappPhoto: Boolean = false,
    canHideInvoicePrices: Boolean = false,
    onToggleHidePrices: (invoiceId: String, hide: Boolean) -> Unit = { _, _ -> },
    onMarkWhatsappSent: (invoiceId: String) -> Unit = {},
    onUpdateSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var format by remember { mutableStateOf(settings.printFormat) }
    var density by remember { mutableStateOf(settings.a5Density) }
    var hideBain by remember { mutableStateOf(settings.hideBainTeinteFacture) }
    var hidePrices by remember(invoice.id, invoice.hidePrices) { mutableStateOf(invoice.hidePrices) }
    var whatsappSent by remember(invoice.id, invoice.whatsappSentBeforePrint) { mutableStateOf(invoice.whatsappSentBeforePrint) }

    var savedPdfFile by remember { mutableStateOf<File?>(null) }
    var savedPngFile by remember { mutableStateOf<File?>(null) }
    var autoWaLaunchedForId by remember { mutableStateOf<String?>(null) }

    val logoBitmap = remember(context) {
        val f = InvoicePdfGenerator.getStoreLogoFile(context)
        if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull() else null
    }

    val effectiveInvoice = remember(invoice, hidePrices, whatsappSent) {
        invoice.copy(hidePrices = hidePrices, whatsappSentBeforePrint = whatsappSent)
    }

    val soleWaTarget = PosSeeds.WHATSAPP_MANAGEMENT

    LaunchedEffect(invoice.id, format, hideBain, hidePrices) {
        val (pdf, png) = withContext(Dispatchers.IO) {
            val p = runCatching {
                InvoicePdfGenerator.generateAndSavePdf(
                    context = context,
                    invoice = effectiveInvoice,
                    storeInfo = storeInfo,
                    format = format,
                    hideBain = hideBain,
                    hidePrices = hidePrices
                )
            }.getOrNull()
            val img = runCatching {
                InvoicePdfGenerator.generateAndSaveInvoicePhotoPng(
                    context = context,
                    invoice = effectiveInvoice,
                    storeInfo = storeInfo,
                    hideBain = hideBain,
                    hidePrices = hidePrices
                )
            }.getOrNull()
            p to img
        }
        savedPdfFile = pdf
        savedPngFile = png
        if (autoWhatsappPhoto && !whatsappSent && png != null && autoWaLaunchedForId != invoice.id) {
            autoWaLaunchedForId = invoice.id
            val launched = if (pdf != null) {
                InvoicePdfGenerator.shareInvoicePhotoAndPdfViaWhatsApp(
                    context = context,
                    pngFile = png,
                    pdfFile = pdf,
                    phone213 = soleWaTarget,
                    message = "",
                    invoiceRef = effectiveInvoice.reference
                )
            } else {
                InvoicePdfGenerator.shareInvoicePhotoViaWhatsApp(
                    context = context,
                    pngFile = png,
                    phone213 = soleWaTarget,
                    message = ""
                )
            }
            if (launched) {
                whatsappSent = true
                onMarkWhatsappSent(invoice.id)
            }
        }
    }

    fun dispatchInvoicePhotoAndPdfWhatsApp() {
        val png = savedPngFile ?: InvoicePdfGenerator.generateAndSaveInvoicePhotoPng(
            context = context,
            invoice = effectiveInvoice,
            storeInfo = storeInfo,
            hideBain = hideBain,
            hidePrices = hidePrices
        ).also { savedPngFile = it }
        val pdf = savedPdfFile ?: InvoicePdfGenerator.generateAndSavePdf(
            context = context,
            invoice = effectiveInvoice,
            storeInfo = storeInfo,
            format = format,
            hideBain = hideBain,
            hidePrices = hidePrices
        ).also { savedPdfFile = it }
        InvoicePdfGenerator.shareInvoicePhotoAndPdfViaWhatsApp(
            context = context,
            pngFile = png,
            pdfFile = pdf,
            phone213 = soleWaTarget,
            message = "",
            invoiceRef = effectiveInvoice.reference
        )
        if (!whatsappSent) {
            whatsappSent = true
            onMarkWhatsappSent(invoice.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate950,
        contentColor = Slate100,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("invoice_preview_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Banner (Light Architectural Showroom Style)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Slate900,
                border = BorderStroke(1.dp, Slate700)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Facture #${invoice.reference}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Navy950,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${invoice.magasin} • Vendeur: ${invoice.vendeur}${if (invoice.vendeurTel.isNotBlank()) " (${invoice.vendeurTel})" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Cyan400,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("invoice_preview_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("FERMER", fontWeight = FontWeight.Black)
                    }
                }
            }

            // Mandatory WhatsApp Gate Alert Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (whatsappSent) Emerald950 else Amber950,
                border = BorderStroke(1.5.dp, if (whatsappSent) Emerald500 else Amber500)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (whatsappSent) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (whatsappSent) Emerald300 else Amber300,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (whatsappSent) {
                                "Photo + PDF envoyés sur WhatsApp — Impression déverrouillée ✓"
                            } else {
                                "Envoi Photo + PDF WhatsApp obligatoire avant impression / إرسال الصورة و PDF إلزامي قبل الطباعة"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (whatsappSent) Emerald300 else Amber300,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Button(
                        onClick = { dispatchInvoicePhotoAndPdfWhatsApp() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("invoice_wa_bureau_photo_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = OnDark
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Photo + PDF → WhatsApp 0676754124",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Format & Density Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("A4", "A5").forEach { fmt ->
                    FilterChip(
                        selected = format == fmt,
                        onClick = {
                            format = fmt
                            onUpdateSettings(settings.copy(printFormat = fmt))
                        },
                        label = { Text("Format $fmt", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate900,
                            labelColor = Slate400
                        )
                    )
                }
                if (format == "A5") {
                    listOf("ultra" to "Ultra", "compact" to "Compact", "normal" to "Normal").forEach { (key, label) ->
                        FilterChip(
                            selected = density == key,
                            onClick = {
                                density = key
                                onUpdateSettings(settings.copy(a5Density = key))
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber500,
                                selectedLabelColor = OnDark,
                                containerColor = Slate900,
                                labelColor = Slate400
                            )
                        )
                    }
                }
            }

            // Options Card (Bain + Admin-only Reseller Hide Prices)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Slate900,
                border = BorderStroke(1.dp, Slate700)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Masquer Bain/Teinte sur facture client",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate100,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = hideBain,
                            onCheckedChange = {
                                hideBain = it
                                onUpdateSettings(settings.copy(hideBainTeinteFacture = it))
                            }
                        )
                    }
                    if (canHideInvoicePrices) {
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Amber500, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = "Masquer Prix & Total (Bon Revendeur)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate100,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Réservé Admin / Gérant — Édite un bon sans prix",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400
                                    )
                                }
                            }
                            Switch(
                                checked = hidePrices,
                                onCheckedChange = {
                                    hidePrices = it
                                    onToggleHidePrices(invoice.id, it)
                                },
                                modifier = Modifier.testTag("toggle_hide_invoice_prices")
                            )
                        }
                    }
                }
            }

            // Reusable Eco-Ink Printer-Friendly Summary Component
            PrinterFriendlyInvoiceSummary(
                invoice = effectiveInvoice,
                storeInfo = storeInfo,
                logoBitmap = logoBitmap,
                hideBain = hideBain,
                hidePrices = hidePrices,
                compact = format == "A5" && density == "ultra"
            )

            // Print Button (LOCKED until WhatsApp Photo sent!)
            Button(
                onClick = {
                    val file = savedPdfFile ?: InvoicePdfGenerator.generateAndSavePdf(
                        context = context,
                        invoice = effectiveInvoice,
                        storeInfo = storeInfo,
                        format = format,
                        hideBain = hideBain,
                        hidePrices = hidePrices
                    ).also { savedPdfFile = it }
                    printInvoicePdf(
                        context = context,
                        pdfFile = file,
                        invoice = effectiveInvoice,
                        format = format
                    )
                },
                enabled = whatsappSent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("invoice_print_pdf_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    contentColor = OnDark,
                    disabledContainerColor = Slate700,
                    disabledContentColor = Slate400
                )
            ) {
                Icon(
                    imageVector = if (whatsappSent) Icons.Default.Print else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (whatsappSent) "Imprimer Direct ($format)" else "Envoyer Photo + PDF WA d'abord pour Imprimer",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = {
                    val file = savedPdfFile ?: InvoicePdfGenerator.generateAndSavePdf(
                        context, effectiveInvoice, storeInfo, format, hideBain, hidePrices
                    ).also { savedPdfFile = it }
                    InvoicePdfGenerator.openPdfViewer(context, file)
                },
                enabled = whatsappSent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("invoice_open_pdf_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan500,
                    contentColor = OnDark,
                    disabledContainerColor = Slate700,
                    disabledContentColor = Slate400
                )
            ) {
                Icon(
                    imageVector = if (whatsappSent) Icons.Default.PictureAsPdf else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ouvrir PDF ($format)", fontWeight = FontWeight.Black)
            }
        }
    }
}

fun openWhatsApp(context: Context, phone213: String?, message: String) {
    dispatchWhatsAppText(context, phone213, message)
}

fun buildWhatsAppDepotMessage(invoice: Invoice): String {
    val lines = invoice.articles.joinToString("\n") {
        val qtyTag = when {
            it.isSoldByPiece -> "${it.pieces} pcs [Pièce]"
            it.pieces > 0 -> "${it.cartons} ctn + ${it.pieces} pcs"
            else -> "${it.cartons} ctn (${formatM2(it.m2Carton)} m²/crt)"
        }
        "• ${it.name} [${it.usine} ${it.dimension}] — $qtyTag • ${formatM2(it.m2Total)} m²"
    }
    return buildString {
        appendLine("📦 *PRÉPARATION DÉPÔT - ${invoice.magasin}*")
        appendLine("Réf: #${invoice.reference} | Vendeur: ${invoice.vendeur}")
        appendLine("Client: ${invoice.client.nom}")
        appendLine("----------------------")
        appendLine(lines)
        appendLine("Surface totale: ${formatM2(invoice.articles.sumOf { it.m2Total })} m²")
    }
}

fun buildWhatsAppClientMessage(invoice: Invoice, store: StoreInfo): String {
    val items = invoice.articles.joinToString("\n") {
        val qtyStr = if (it.isSoldByPiece) "${it.pieces} pcs" else "${it.cartons} ctn (${formatM2(it.m2Total)} m²)"
        val pricePart = if (invoice.hidePrices) "" else " = ${formatDa(it.sousTotal)}"
        "• ${it.name}: $qtyStr$pricePart"
    }
    return buildString {
        appendLine("🏛️ *${store.nom}*")
        appendLine("Facture #${invoice.reference} (${invoice.date.take(10)})")
        appendLine("Client: ${invoice.client.nom}")
        appendLine(items)
        if (invoice.lignesLibres.isNotEmpty()) {
            invoice.lignesLibres.forEach {
                val pricePart = if (invoice.hidePrices) "" else " = ${formatDa(it.sousTotal)}"
                appendLine("• ${it.designation}: ${formatM2(it.quantite)} ${it.unite}$pricePart")
            }
        }
        if (!invoice.hidePrices) {
            appendLine("----------------------")
            appendLine("Total Net: *${formatDa(invoice.totalNet)}*")
            appendLine("Payé: ${formatDa(invoice.montantPaye)} | Reste: ${formatDa(invoice.resteAPayer)}")
        }
        appendLine("Merci de votre confiance !")
    }
}

fun buildWhatsAppManagementMessage(invoice: Invoice): String {
    val totalStr = if (invoice.hidePrices) "Bon Revendeur (Sans Prix)" else "Total Net: ${formatDa(invoice.totalNet)} | Encaissé: ${formatDa(invoice.montantPaye)} (${invoice.modePaiement})"
    val itemsPreview = invoice.articles.take(5).joinToString("\n") {
        val qty = if (it.isSoldByPiece) "${it.pieces} pcs" else "${it.cartons} ctn (${formatM2(it.m2Total)} m²)"
        "• ${it.name}: $qty"
    }
    return buildString {
        appendLine("🧾 *FACTURE #${invoice.reference}* (${invoice.magasin})")
        appendLine("Vendeur: ${invoice.vendeur}${if (invoice.vendeurTel.isNotBlank()) " (${invoice.vendeurTel})" else ""} | Client: ${invoice.client.nom}${if (invoice.client.tel.isNotBlank()) " (${invoice.client.tel})" else ""}")
        if (itemsPreview.isNotBlank()) appendLine(itemsPreview)
        append(totalStr)
    }
}

private fun printInvoicePdf(
    context: Context,
    pdfFile: File,
    invoice: Invoice,
    format: String
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val mediaSize = if (format.equals("A5", ignoreCase = true)) {
        PrintAttributes.MediaSize.ISO_A5
    } else {
        PrintAttributes.MediaSize.ISO_A4
    }
    val jobName = "Facture_${invoice.reference}"
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(pdfFile.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            val dest = destination ?: run {
                callback.onWriteFailed("Destination introuvable")
                return
            }
            runCatching {
                pdfFile.inputStream().use { input ->
                    ParcelFileDescriptor.AutoCloseOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
            }.onSuccess {
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure { err ->
                callback.onWriteFailed(err.localizedMessage ?: "Erreur impression PDF")
            }
        }
    }
    printManager.print(
        jobName,
        adapter,
        PrintAttributes.Builder().setMediaSize(mediaSize).build()
    )
}
