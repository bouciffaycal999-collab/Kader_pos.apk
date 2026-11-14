package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Invoice
import com.example.data.model.formatDa
import com.example.data.model.formatM2
import com.example.ui.components.ClearableOutlinedTextField
import com.example.ui.theme.Amber300
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Amber950
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.Emerald300
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
import com.example.ui.theme.PosNumericTextStyle
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose950
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun InvoicesScreen(
    invoices: List<Invoice>,
    canEdit: Boolean,
    canDelete: Boolean,
    onPreviewInvoice: (Invoice) -> Unit,
    onEditInvoice: (Invoice) -> Unit,
    onDeleteInvoice: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("Toutes") }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val todayPrefix = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val yesterdayPrefix = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    val monthPrefix = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
    val weekCutoff = remember { System.currentTimeMillis() - 7L * 24 * 3600 * 1000 }

    val filtered = remember(invoices, search, dateFilter) {
        val q = search.trim().lowercase()
        invoices.filter { inv ->
            val matchQ = q.isEmpty() ||
                inv.reference.lowercase().contains(q) ||
                inv.client.nom.lowercase().contains(q) ||
                inv.client.tel.lowercase().contains(q) ||
                inv.articles.any { it.name.lowercase().contains(q) }
            val matchDate = when (dateFilter) {
                "Aujourd'hui" -> inv.date.startsWith(todayPrefix)
                "Hier" -> inv.date.startsWith(yesterdayPrefix)
                "7 jours" -> parseIsoMillis(inv.date) >= weekCutoff
                "Ce mois" -> inv.date.startsWith(monthPrefix)
                else -> true
            }
            matchQ && matchDate
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Slate950)) {
        Surface(color = Slate900, border = BorderStroke(1.dp, Slate800)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Historique des Factures", style = MaterialTheme.typography.titleLarge, color = Slate100, fontWeight = FontWeight.Black)
                    Surface(shape = RoundedCornerShape(8.dp), color = Cyan950) {
                        Text(
                            text = "${filtered.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Cyan300,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                ClearableOutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Rechercher réf, client, tél ou carreau...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400) },
                    modifier = Modifier.fillMaxWidth().testTag("invoices_search_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Toutes", "Aujourd'hui", "Hier", "7 jours", "Ce mois").forEach { d ->
                        FilterChip(
                            selected = dateFilter == d,
                            onClick = { dateFilter = d },
                            label = { Text(d, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan500,
                                selectedLabelColor = OnDark,
                                containerColor = Slate950,
                                labelColor = Slate400
                            )
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Slate400, modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aucune facture enregistrée", color = Slate100, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Les ventes encaissées apparaîtront ici.", color = Slate400, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { inv ->
                    val expanded = expandedId == inv.id
                    ElevatedCard(
                        onClick = { expandedId = if (expanded) null else inv.id },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("#${inv.reference} • ${inv.client.nom}", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Black)
                                    Text("${inv.date.take(10)} • ${inv.magasin} • ${inv.vendeur}", style = MaterialTheme.typography.bodySmall, color = Slate400, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (inv.whatsappSentBeforePrint) Emerald950 else Amber950,
                                            border = BorderStroke(1.dp, if (inv.whatsappSentBeforePrint) Emerald300 else Amber400)
                                        ) {
                                            Text(
                                                text = if (inv.whatsappSentBeforePrint) "WA Envoyé ✓" else "WA Requis",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (inv.whatsappSentBeforePrint) Emerald300 else Amber300,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (inv.hidePrices) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Cyan950,
                                                border = BorderStroke(1.dp, Cyan400)
                                            ) {
                                                Text(
                                                    text = "Sans Prix (Revendeur)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Cyan300,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(formatDa(inv.totalNet), style = PosNumericTextStyle.copy(fontSize = 18.sp), color = Emerald400)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (inv.resteAPayer <= 0.01) Emerald950 else Rose950,
                                        border = BorderStroke(1.dp, if (inv.resteAPayer <= 0.01) Emerald300 else Rose400)
                                    ) {
                                        Text(
                                            text = if (inv.resteAPayer <= 0.01) inv.statut else "Reste ${formatDa(inv.resteAPayer)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (inv.resteAPayer <= 0.01) Emerald300 else Rose400,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (expanded) {
                                HorizontalDivider(color = Slate800)
                                inv.articles.forEach { a ->
                                    val qtyLabel = if (a.isSoldByPiece) "${a.pieces} pcs" else "${a.cartons} ctn • ${formatM2(a.m2Total)}m²"
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${a.name} ($qtyLabel)", style = MaterialTheme.typography.bodySmall, color = Slate100, modifier = Modifier.weight(1f))
                                        Text(formatDa(a.sousTotal), style = PosNumericTextStyle.copy(fontSize = 13.sp), color = Cyan400)
                                    }
                                }
                                inv.lignesLibres.forEach { l ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${l.designation} (${formatM2(l.quantite)} ${l.unite})", style = MaterialTheme.typography.bodySmall, color = Slate100, modifier = Modifier.weight(1f))
                                        Text(formatDa(l.sousTotal), style = PosNumericTextStyle.copy(fontSize = 13.sp), color = Amber400)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onPreviewInvoice(inv) },
                                    modifier = Modifier.weight(1.4f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = OnDark)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Photo / PDF / WA", fontWeight = FontWeight.Black)
                                }
                                if (canEdit) {
                                    Button(
                                        onClick = { onEditInvoice(inv) },
                                        modifier = Modifier.height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Modifier facture", modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (canDelete) {
                                    IconButton(
                                        onClick = { onDeleteInvoice(inv.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Supprimer facture", tint = Rose500)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseIsoMillis(iso: String): Long {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.take(19))?.time ?: 0L
    }.getOrDefault(0L)
}
