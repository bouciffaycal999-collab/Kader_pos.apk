package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Invoice
import com.example.data.model.PosSeeds
import com.example.data.model.StoreInfo
import com.example.data.model.formatDa
import com.example.data.model.formatM2
import com.example.data.remote.FirestorePosRepository
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald950
import com.example.ui.theme.OnDark
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose950
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

private val EcoInkDark = Color(0xFF0F172A)
private val EcoInkMuted = Color(0xFF475569)
private val EcoPaperBorder = Color(0xFF1E293B)
private val EcoPaperDivider = Color(0xFFCBD5E1)

sealed interface FirestoreInvoiceUiState {
    data object Loading : FirestoreInvoiceUiState
    data class Ready(
        val invoice: Invoice,
        val storeInfo: StoreInfo,
        val fromFirestore: Boolean
    ) : FirestoreInvoiceUiState
    data class Error(val message: String) : FirestoreInvoiceUiState
}

/**
 * Reusable printer-friendly invoice summary component.
 * Uses pure white paper + crisp hairlines with zero heavy black toner fills.
 */
@Composable
fun PrinterFriendlyInvoiceSummary(
    invoice: Invoice,
    storeInfo: StoreInfo = PosSeeds.DEFAULT_STORE_INFO,
    logoBitmap: ImageBitmap? = null,
    hideBain: Boolean = true,
    hidePrices: Boolean = invoice.hidePrices,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("printer_friendly_invoice_summary"),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, EcoPaperBorder)
    ) {
        val pad = if (compact) 10.dp else 14.dp
        Column(
            modifier = Modifier.padding(pad),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Eco-ink Header with optional Store Logo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EcoPaperBorder, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1.2f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap,
                            contentDescription = "Logo magasin",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                    Column {
                        Text(
                            text = storeInfo.nom.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = EcoInkDark
                        )
                        if (storeInfo.nomArabe.isNotBlank()) {
                            Text(
                                text = storeInfo.nomArabe,
                                fontSize = 11.sp,
                                color = EcoInkDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(storeInfo.adresse, fontSize = 10.sp, color = EcoInkMuted)
                        val phones = listOf(storeInfo.tel, storeInfo.tel2).filter { it.isNotBlank() }.joinToString(" / ")
                        if (phones.isNotBlank()) {
                            Text("Tél: $phones", fontSize = 10.sp, color = EcoInkDark)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (hidePrices) "BON DE LIVRAISON / وصل تسليم" else "FACTURE / فاتورة",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = EcoInkDark
                    )
                    Text(
                        text = "N° ${invoice.reference}",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = EcoInkDark
                    )
                    Text("Date: ${invoice.date.take(10)}", fontSize = 10.sp, color = EcoInkMuted)
                    Text(
                        text = "Site: ${invoice.magasin}",
                        fontSize = 10.sp,
                        color = EcoInkMuted
                    )
                    Text(
                        text = "Vendeur / البائع: ${invoice.vendeur}",
                        fontSize = 10.sp,
                        color = EcoInkDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (invoice.vendeurTel.isNotBlank()) {
                        Text("Tél: ${invoice.vendeurTel}", fontSize = 10.sp, color = EcoInkMuted)
                    }
                }
            }

            // Client Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.8.dp, EcoPaperDivider, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Client / الزبون : ${invoice.client.nom}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = EcoInkDark
                    )
                    if (invoice.client.tel.isNotBlank() || invoice.client.adresse.isNotBlank()) {
                        val clientMeta = listOf(invoice.client.tel, invoice.client.adresse)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        Text(clientMeta, fontSize = 10.sp, color = EcoInkMuted)
                    }
                }
                Text(
                    text = if (hidePrices) "SANS PRIX" else invoice.statut.uppercase(),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = EcoInkDark
                )
            }

            // Table Header
            HorizontalDivider(thickness = 1.2.dp, color = EcoPaperBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("[ ]", color = EcoInkDark, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Text("Désignation / التعيين", color = EcoInkDark, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(2.2f))
                Text("Qté & m²", color = EcoInkDark, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                Text(if (hidePrices) "---" else "P.U", color = EcoInkDark, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.85f), textAlign = TextAlign.End)
                Text(if (hidePrices) "---" else "Total", color = EcoInkDark, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1.05f), textAlign = TextAlign.End)
            }
            HorizontalDivider(thickness = 1.2.dp, color = EcoPaperBorder)

            // Product Lines
            invoice.articles.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(13.dp)
                            .border(1.1.dp, EcoPaperBorder)
                    )
                    Column(modifier = Modifier.weight(2.2f)) {
                        Text(
                            text = "${item.name} • ${item.usine} • ${item.dimension}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoInkDark
                        )
                        val unitLabel = if (item.isSoldByPiece) {
                            "Vendu à la Pièce / بالقطعة (${item.pcs} pcs/crt)"
                        } else {
                            "${formatM2(item.m2Carton)} m²/Crt"
                        }
                        val extra = buildString {
                            append(unitLabel)
                            if (!hideBain && item.bain.isNotBlank()) append(" • Bain: ${item.bain}")
                        }
                        Text(extra, fontSize = 9.5.sp, color = EcoInkMuted)
                    }
                    val qtyStr = when {
                        item.isSoldByPiece && item.cartons > 0 -> "${item.cartons} ctn +${item.pieces}p"
                        item.isSoldByPiece -> "${item.pieces} pcs"
                        item.pieces > 0 -> "${item.cartons} ctn +${item.pieces}p"
                        else -> "${item.cartons} ctn"
                    }
                    Text(
                        text = "$qtyStr\n${formatM2(item.m2Total)} m²",
                        fontSize = 9.5.sp,
                        color = EcoInkDark,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.0f)
                    )
                    Text(
                        text = when {
                            hidePrices -> "---"
                            item.isSoldByPiece -> "${(if (item.prixPiece > 0) item.prixPiece else item.prixUnitaire).toInt()}/p"
                            item.pieces > 0 && item.prixPiece > 0 -> "${item.prixUnitaire.toInt()}\n(${item.prixPiece.toInt()}/p)"
                            else -> "${item.prixUnitaire.toInt()} DA"
                        },
                        fontSize = 9.5.sp,
                        color = EcoInkDark,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.85f)
                    )
                    Text(
                        text = if (hidePrices) "---" else formatDa(item.sousTotal),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoInkDark,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.05f)
                    )
                }
                HorizontalDivider(thickness = 0.6.dp, color = EcoPaperDivider)
            }

            // Free / Accessory Lines
            invoice.lignesLibres.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(13.dp)
                            .border(1.1.dp, EcoPaperBorder)
                    )
                    Text(
                        text = line.designation,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EcoInkDark,
                        modifier = Modifier.weight(2.2f)
                    )
                    Text(
                        text = "${formatM2(line.quantite)} ${line.unite}",
                        fontSize = 9.5.sp,
                        color = EcoInkDark,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.0f)
                    )
                    Text(
                        text = if (hidePrices) "---" else line.prixUnitaire.toInt().toString(),
                        fontSize = 9.5.sp,
                        color = EcoInkDark,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.85f)
                    )
                    Text(
                        text = if (hidePrices) "---" else formatDa(line.sousTotal),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoInkDark,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.05f)
                    )
                }
                HorizontalDivider(thickness = 0.6.dp, color = EcoPaperDivider)
            }

            // Delivery & Totals Box (NO Total Cartons printed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EcoPaperBorder, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1.15f)) {
                    Text(
                        text = "Surface totale / المساحة: ${formatM2(invoice.articles.sumOf { it.m2Total })} m²",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoInkDark
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(12.dp).border(1.1.dp, EcoPaperBorder))
                        Text("Livraison Complète / تسليم كامل", fontSize = 9.5.sp, color = EcoInkDark)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(12.dp).border(1.1.dp, EcoPaperBorder))
                        Text("Livraison Partielle / تسليم جزئي", fontSize = 9.5.sp, color = EcoInkDark)
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.95f)) {
                    if (hidePrices) {
                        Text("BON DE LIVRAISON", fontSize = 12.sp, fontWeight = FontWeight.Black, color = EcoInkDark)
                        Text("Prix masqués (Revendeur)", fontSize = 9.5.sp, color = EcoInkMuted)
                    } else {
                        if (invoice.remise > 0) {
                            Text("Remise: -${formatDa(invoice.remise)}", fontSize = 10.sp, color = EcoInkDark)
                        }
                        Text(
                            text = "TOTAL NET: ${formatDa(invoice.totalNet)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = EcoInkDark
                        )
                        Text(
                            text = "Payé (${invoice.modePaiement}): ${formatDa(invoice.montantPaye)}",
                            fontSize = 10.sp,
                            color = EcoInkDark
                        )
                        if (invoice.resteAPayer > 0.01) {
                            Text(
                                text = "Reste / Crédit: ${formatDa(invoice.resteAPayer)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoInkDark
                            )
                        }
                    }
                }
            }

            // Open Handwritten Pen Notes Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .border(0.9.dp, EcoPaperBorder, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = if (invoice.notes.isNotBlank()) "Notes : ${invoice.notes}" else "Notes / ملاحظات : (espace libre pour écriture au stylo)",
                    fontSize = 9.5.sp,
                    color = EcoInkMuted
                )
            }
        }
    }
}

/**
 * Reusable Firestore-connected invoice summary card.
 * Observes live transaction data directly from Firestore (`users/{userId}/invoices/{invoiceId}`
 * and `users/{userId}/store_info/default`) and renders a clean, printer-friendly layout.
 */
@Composable
fun FirestoreInvoiceSummaryCard(
    userId: String,
    invoiceId: String,
    firestoreRepo: FirestorePosRepository? = null,
    initialInvoice: Invoice? = null,
    initialStoreInfo: StoreInfo = PosSeeds.DEFAULT_STORE_INFO,
    hideBain: Boolean = true,
    onPrintClick: ((Invoice, StoreInfo) -> Unit)? = null,
    onSharePhotoAndPdfClick: ((Invoice, StoreInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedRepo = remember(firestoreRepo, context) {
        firestoreRepo ?: runCatching { FirestorePosRepository(context) }.getOrNull()
    }
    val logoBitmap = remember(context) {
        val f = InvoicePdfGenerator.getStoreLogoFile(context)
        if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull() else null
    }

    val uiState by produceState<FirestoreInvoiceUiState>(
        initialValue = if (initialInvoice != null) {
            FirestoreInvoiceUiState.Ready(initialInvoice, initialStoreInfo, fromFirestore = false)
        } else {
            FirestoreInvoiceUiState.Loading
        },
        key1 = userId,
        key2 = invoiceId,
        key3 = resolvedRepo
    ) {
        if (userId.isBlank() || invoiceId.isBlank() || resolvedRepo == null) {
            value = if (initialInvoice != null) {
                FirestoreInvoiceUiState.Ready(initialInvoice, initialStoreInfo, fromFirestore = false)
            } else {
                FirestoreInvoiceUiState.Error("Connexion Firestore requise pour charger la facture.")
            }
            return@produceState
        }

        combine(
            resolvedRepo.observeInvoiceById(userId, invoiceId),
            resolvedRepo.observeStoreInfo(userId)
        ) { remoteInv, remoteStore ->
            remoteInv to (remoteStore ?: initialStoreInfo)
        }
            .catch { err ->
                if (initialInvoice != null) {
                    value = FirestoreInvoiceUiState.Ready(initialInvoice, initialStoreInfo, fromFirestore = false)
                } else {
                    value = FirestoreInvoiceUiState.Error(err.message ?: "Erreur lecture Firestore")
                }
            }
            .collect { (inv, store) ->
                value = when {
                    inv != null -> FirestoreInvoiceUiState.Ready(inv, store, fromFirestore = true)
                    initialInvoice != null -> FirestoreInvoiceUiState.Ready(initialInvoice, store, fromFirestore = false)
                    else -> FirestoreInvoiceUiState.Error("Transaction #$invoiceId introuvable dans Firestore.")
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("firestore_invoice_summary_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (val s = uiState) {
            FirestoreInvoiceUiState.Loading -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Slate900,
                    border = BorderStroke(1.dp, Slate800)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Cyan500, strokeWidth = 2.5.dp)
                        Text(
                            text = "Chargement transaction Firestore...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            is FirestoreInvoiceUiState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Rose950,
                    border = BorderStroke(1.dp, Rose400.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Rose400)
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Rose400,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            is FirestoreInvoiceUiState.Ready -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (s.fromFirestore) Emerald950 else Cyan950,
                        border = BorderStroke(1.dp, if (s.fromFirestore) Emerald500.copy(alpha = 0.45f) else Cyan500.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (s.fromFirestore) Emerald400 else Cyan400,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (s.fromFirestore) "Firestore Temps Réel" else "Aperçu Local",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.fromFirestore) Emerald400 else Cyan400,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = "${s.invoice.articles.size} article(s) • ${s.invoice.magasin}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                        fontWeight = FontWeight.Bold
                    )
                }

                PrinterFriendlyInvoiceSummary(
                    invoice = s.invoice,
                    storeInfo = s.storeInfo,
                    logoBitmap = logoBitmap,
                    hideBain = hideBain,
                    hidePrices = s.invoice.hidePrices
                )

                if (onSharePhotoAndPdfClick != null || onPrintClick != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onSharePhotoAndPdfClick != null) {
                            Button(
                                onClick = { onSharePhotoAndPdfClick(s.invoice, s.storeInfo) },
                                modifier = Modifier.weight(1.1f).height(48.dp).testTag("summary_wa_photo_pdf_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Photo + PDF WA", fontWeight = FontWeight.Black)
                            }
                        }
                        if (onPrintClick != null) {
                            OutlinedButton(
                                onClick = { onPrintClick(s.invoice, s.storeInfo) },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("summary_print_btn"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, Cyan500)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Cyan400, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Imprimer", color = Cyan400, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
