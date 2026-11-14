package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TileProduct
import com.example.data.model.WaRecipient
import com.example.data.model.formatM2
import com.example.data.model.toDzWhatsAppDigits
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

fun buildDispoDepotMessage(product: TileProduct): String = buildString {
    appendLine("📦 *DEMANDE DISPO DÉPÔT — CERAMICA*")
    appendLine("Produit : ${product.name}")
    appendLine("Usine / Format : ${product.usine} • ${product.dimension} (${formatM2(product.m2Carton)} m²/ctn)")
    appendLine("Stock actuel appli : ${product.stock} ctn")
    appendLine("Merci de confirmer la disponibilité physique au dépôt.")
}

fun dispatchWhatsAppText(context: Context, phone213: String?, message: String) {
    if (!phone213.isNullOrBlank()) {
        val urlIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$phone213?text=${Uri.encode(message)}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val waStd = Intent(urlIntent).setPackage("com.whatsapp")
        val waBiz = Intent(urlIntent).setPackage("com.whatsapp.w4b")
        val launched = runCatching {
            when {
                waStd.resolveActivity(context.packageManager) != null -> context.startActivity(waStd)
                waBiz.resolveActivity(context.packageManager) != null -> context.startActivity(waBiz)
                else -> context.startActivity(urlIntent)
            }
        }.isSuccess
        if (launched) return
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        if (!phone213.isNullOrBlank()) {
            putExtra("jid", "$phone213@s.whatsapp.net")
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val waSendStd = Intent(sendIntent).setPackage("com.whatsapp")
    val waSendBiz = Intent(sendIntent).setPackage("com.whatsapp.w4b")
    runCatching {
        when {
            waSendStd.resolveActivity(context.packageManager) != null -> context.startActivity(waSendStd)
            waSendBiz.resolveActivity(context.packageManager) != null -> context.startActivity(waSendBiz)
            else -> context.startActivity(
                Intent.createChooser(sendIntent, "Envoyer Demande Dispo via WhatsApp").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

@Composable
fun DispoDepotRecipientsDialog(
    product: TileProduct,
    recipients: List<WaRecipient>,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val message = remember(product) { buildDispoDepotMessage(product) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(14.dp),
            shape = RoundedCornerShape(22.dp),
            color = Slate900,
            border = BorderStroke(1.5.dp, Slate800)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("dispo_recipients_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Cyan950,
                    border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Cyan500
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warehouse,
                                    contentDescription = null,
                                    tint = OnDark,
                                    modifier = Modifier.padding(8.dp).size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Demande Dispo Dépôt",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Navy950,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${product.name} (${product.stock} ctn)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Cyan300,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FERMER", fontWeight = FontWeight.Black)
                        }
                    }
                }

                Text(
                    text = "Alerte Direction enregistrée. Choisir un responsable pour envoyer sur WhatsApp :",
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )

                recipients.forEach { rec ->
                    val waDigits = toDzWhatsAppDigits(rec.phone)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate950,
                        border = BorderStroke(1.dp, Slate800)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Slate100,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = rec.phone.ifBlank { "Numéro à configurer dans Réglages" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (waDigits != null) Cyan400 else Rose400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    dispatchWhatsAppText(context, waDigits, message)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("dispo_wa_recipient_${rec.name}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (waDigits != null) Emerald500 else Cyan500,
                                    contentColor = OnDark
                                )
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (waDigits != null) "WhatsApp" else "Choisir WA",
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        dispatchWhatsAppText(context, null, message)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dispo_wa_other_btn"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Cyan500)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Cyan400, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Autre Contact WhatsApp", color = Cyan400, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
