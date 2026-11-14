package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Warehouse
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PosSeeds
import com.example.data.model.StoreInfo
import com.example.data.model.TileProduct
import com.example.data.model.formatDa
import com.example.data.model.formatM2
import com.example.ui.components.ClearableOutlinedTextField
import com.example.ui.components.DispoDepotRecipientsDialog
import com.example.ui.components.NonImeNumericKeypad
import com.example.ui.components.NumericDisplayField
import com.example.ui.components.ProductPhotoLightboxDialog
import com.example.ui.components.TileSwatchImage
import com.example.ui.components.applyKeypadInput
import com.example.ui.components.openWhatsApp
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
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
import com.example.ui.theme.PosNumericTextStyle
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose950
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.util.UUID

@Composable
fun StockScreen(
    products: List<TileProduct>,
    storeInfo: StoreInfo = PosSeeds.DEFAULT_STORE_INFO,
    canModifyStock: Boolean,
    canAddProducts: Boolean = true,
    canViewPrixAchat: Boolean = false,
    onAdjustStock: (String, Int) -> Unit,
    onSetStock: (String, Int) -> Unit,
    onSaveProduct: (TileProduct) -> Unit,
    onRequestDepotDispo: (TileProduct) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var dimFilter by remember { mutableStateOf("Toutes") }
    var usineFilter by remember { mutableStateOf("Toutes") }
    var finitionFilter by remember { mutableStateOf("Toutes") }
    var isGridView by remember { mutableStateOf(false) }

    var ficheProduct by remember { mutableStateOf<TileProduct?>(null) }
    var showNewFiche by remember { mutableStateOf(false) }
    var previewProduct by remember { mutableStateOf<TileProduct?>(null) }
    var dispoTargetProduct by remember { mutableStateOf<TileProduct?>(null) }

    val dimensions = remember(products) {
        listOf("Toutes") + products.map { it.dimension.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val usines = remember(products) {
        listOf("Toutes") + products.map { it.usine.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val finitions = remember(products) {
        listOf("Toutes") + products.map { it.finition.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    val filtered = remember(products, searchQuery, dimFilter, usineFilter, finitionFilter) {
        val q = searchQuery.trim().lowercase()
        products.filter { p ->
            (q.isEmpty() || p.name.lowercase().contains(q) || p.usine.lowercase().contains(q) ||
                p.dimension.lowercase().contains(q) || p.finition.lowercase().contains(q)) &&
                (dimFilter == "Toutes" || p.dimension.trim() == dimFilter) &&
                (usineFilter == "Toutes" || p.usine.trim() == usineFilter) &&
                (finitionFilter == "Toutes" || p.finition.trim().equals(finitionFilter, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Slate950)) {
        // Dark Velvet Studio Header + 3 Dynamic Filter Strips
        Surface(color = Slate900) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Catalogue & Stock (${filtered.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = Slate100,
                            fontWeight = FontWeight.Black
                        )
                        if (!canModifyStock) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Cyan950,
                                border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = if (canAddProducts) {
                                        "Mode Vendeur • Ajout produits actif"
                                    } else {
                                        "Consultation catalogue"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Cyan300,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Grid / List segmented toggle
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate950,
                            border = BorderStroke(1.dp, Slate800)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isGridView) Cyan500 else Slate950,
                                    modifier = Modifier
                                        .clickable { isGridView = true }
                                        .testTag("stock_view_grid_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Vue Grille",
                                        tint = if (isGridView) OnDark else Slate400,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp).size(18.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (!isGridView) Cyan500 else Slate950,
                                    modifier = Modifier
                                        .clickable { isGridView = false }
                                        .testTag("stock_view_list_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableRows,
                                        contentDescription = "Vue Liste",
                                        tint = if (!isGridView) OnDark else Slate400,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp).size(18.dp)
                                    )
                                }
                            }
                        }

                        if (canAddProducts || canModifyStock) {
                            Button(
                                onClick = { showNewFiche = true },
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("stock_add_model_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = OnDark),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Modèle", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                ClearableOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrer par nom, usine, dimension, finition...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400) },
                    modifier = Modifier.fillMaxWidth().testTag("stock_search_input")
                )

                StockFilterLine(
                    title = "Format",
                    options = dimensions,
                    selected = dimFilter,
                    activeColor = Cyan500,
                    labelColor = Cyan300,
                    onSelect = { opt -> dimFilter = if (opt != "Toutes" && dimFilter == opt) "Toutes" else opt }
                )
                StockFilterLine(
                    title = "Usine",
                    options = usines,
                    selected = usineFilter,
                    activeColor = Amber500,
                    labelColor = Amber300,
                    onSelect = { opt -> usineFilter = if (opt != "Toutes" && usineFilter == opt) "Toutes" else opt }
                )
                StockFilterLine(
                    title = "Finition",
                    options = finitions,
                    selected = finitionFilter,
                    activeColor = Emerald500,
                    labelColor = Emerald300,
                    formatOption = { f -> if (f == "Toutes") f else f.replaceFirstChar { it.uppercase() } },
                    onSelect = { opt -> finitionFilter = if (opt != "Toutes" && finitionFilter.equals(opt, ignoreCase = true)) "Toutes" else opt }
                )
            }
        }
        HorizontalDivider(color = Slate800)

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = Slate400, modifier = Modifier.size(50.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aucun article dans ce filtre", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Bold)
                }
            }
        } else if (isGridView) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val gridColumns = when {
                    maxWidth >= 1000.dp -> 4
                    maxWidth >= 600.dp -> 3
                    else -> 2
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().testTag("stock_catalog_grid")
                ) {
                    items(filtered, key = { it.id }) { p ->
                        StockGridProductCard(
                            product = p,
                            canModifyStock = canModifyStock,
                            canViewPrixAchat = canViewPrixAchat,
                            onPreviewImage = { previewProduct = p },
                            onEditFiche = { ficheProduct = p },
                            onRequestDispo = {
                                onRequestDepotDispo(p)
                                dispoTargetProduct = p
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().testTag("stock_catalog_list")
            ) {
                items(filtered, key = { it.id }) { p ->
                    StockListProductCard(
                        product = p,
                        canModifyStock = canModifyStock,
                        canViewPrixAchat = canViewPrixAchat,
                        onPreviewImage = { previewProduct = p },
                        onEditFiche = { ficheProduct = p },
                        onRequestDispo = {
                            onRequestDepotDispo(p)
                            dispoTargetProduct = p
                        }
                    )
                }
            }
        }
    }

    if (showNewFiche || ficheProduct != null) {
        ProductFicheDialog(
            initial = ficheProduct,
            canViewPrixAchat = canViewPrixAchat,
            onDismiss = {
                ficheProduct = null
                showNewFiche = false
            },
            onSave = {
                onSaveProduct(it)
                ficheProduct = null
                showNewFiche = false
            }
        )
    }

    previewProduct?.let { prod ->
        ProductPhotoLightboxDialog(
            product = prod,
            onDismiss = { previewProduct = null },
            onCheckDepotDispo = {
                previewProduct = null
                onRequestDepotDispo(prod)
                dispoTargetProduct = prod
            },
            onOpenCalculator = null
        )
    }

    dispoTargetProduct?.let { prod ->
        DispoDepotRecipientsDialog(
            product = prod,
            recipients = storeInfo.dispoWaRecipients.ifEmpty { PosSeeds.DEFAULT_DISPO_WA_RECIPIENTS },
            onDismiss = { dispoTargetProduct = null }
        )
    }
}

@Composable
private fun StockFilterLine(
    title: String,
    options: List<String>,
    selected: String,
    activeColor: Color,
    labelColor: Color,
    formatOption: (String) -> String = { it },
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Slate950,
            border = BorderStroke(1.dp, activeColor.copy(alpha = 0.45f))
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        options.forEach { opt ->
            val isSel = if (opt == "Toutes") selected == "Toutes" else selected.equals(opt, ignoreCase = true)
            FilterChip(
                selected = isSel,
                onClick = { onSelect(opt) },
                label = { Text(formatOption(opt), fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = activeColor,
                    selectedLabelColor = OnDark,
                    containerColor = Slate950,
                    labelColor = Slate400
                )
            )
        }
    }
}

@Composable
private fun StockGridProductCard(
    product: TileProduct,
    canModifyStock: Boolean,
    canViewPrixAchat: Boolean,
    onPreviewImage: () -> Unit,
    onEditFiche: () -> Unit,
    onRequestDispo: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Surface(
            color = Slate900,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Slate800)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TileSwatchImage(
                        imageUrl = product.image,
                        colorHex = product.color,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clickable(onClick = onPreviewImage)
                            .testTag("stock_photo_${product.id}"),
                        cornerRadius = 14.dp
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Navy950.copy(alpha = 0.90f)
                    ) {
                        Text(
                            text = product.dimension,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnDark,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Slate950.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = product.finition.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan300,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Slate100,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${product.usine} • ${formatM2(product.m2Carton)} m²/ctn • ${product.pcs} pcs",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val priceStr = if (product.isSoldByPiece && product.prixPiece > 0.0) {
                        "${formatDa(product.prixPiece)}/pc"
                    } else {
                        "${formatDa(product.prix)}/m²"
                    }
                    Text(
                        text = priceStr,
                        style = PosNumericTextStyle.copy(fontSize = 15.sp),
                        color = Emerald400
                    )
                    if (canViewPrixAchat && product.prixAchat > 0.0) {
                        Text(
                            text = "Ach: ${formatDa(product.prixAchat)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber400,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onRequestDispo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("stock_dispo_btn_${product.id}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Warehouse, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dispo Dépôt", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                }

                if (canModifyStock) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onEditFiche),
                        shape = RoundedCornerShape(8.dp),
                        color = Amber950,
                        border = BorderStroke(1.dp, Amber500)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Modifier Fiche",
                                color = Amber300,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockListProductCard(
    product: TileProduct,
    canModifyStock: Boolean,
    canViewPrixAchat: Boolean,
    onPreviewImage: () -> Unit,
    onEditFiche: () -> Unit,
    onRequestDispo: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Surface(
            color = Slate900,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Slate800)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        TileSwatchImage(
                            imageUrl = product.image,
                            colorHex = product.color,
                            contentDescription = product.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable(onClick = onPreviewImage)
                                .testTag("stock_photo_${product.id}"),
                            cornerRadius = 12.dp
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp),
                            shape = RoundedCornerShape(5.dp),
                            color = Navy950.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = product.dimension,
                                style = MaterialTheme.typography.labelSmall,
                                color = OnDark,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate100,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val priceStr = if (product.isSoldByPiece && product.prixPiece > 0.0) {
                            "${formatDa(product.prixPiece)}/pc (${formatDa(product.prix)}/m²)"
                        } else {
                            "${formatDa(product.prix)}/m²"
                        }
                        Text(
                            text = "${product.usine} • ${formatM2(product.m2Carton)} m²/ctn (${product.pcs} pcs) • $priceStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Slate850,
                                border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = product.finition.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Cyan300,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (product.isSoldByPiece) Cyan950 else Slate850
                            ) {
                                Text(
                                    text = if (product.isSoldByPiece) "Vente: Pièce" else "Vente: Carton",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (product.isSoldByPiece) Cyan300 else Slate400,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (canViewPrixAchat && product.prixAchat > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Amber950
                                ) {
                                    Text(
                                        text = "Achat: ${formatDa(product.prixAchat)}/m²",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber300,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRequestDispo,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("stock_dispo_btn_${product.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Warehouse, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dispo Dépôt", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    }
                    if (canModifyStock) {
                        OutlinedButton(
                            onClick = onEditFiche,
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, Amber500)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Éditer fiche", tint = Amber400, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fiche", color = Amber400, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

private enum class FicheNumField(val decimal: Boolean) { M2_CTN(true), PCS(false), PRIX(false), PRIX_PIECE(false), PRIX_ACHAT(false) }

@Composable
private fun ProductFicheDialog(
    initial: TileProduct?,
    canViewPrixAchat: Boolean,
    onDismiss: () -> Unit,
    onSave: (TileProduct) -> Unit
) {
    BackHandler { onDismiss() }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var usine by remember { mutableStateOf(initial?.usine ?: "BCRAM") }
    var dimension by remember { mutableStateOf(initial?.dimension ?: "60×60") }
    var finition by remember { mutableStateOf(initial?.finition ?: "brillant") }
    var colorHex by remember { mutableStateOf(initial?.color ?: "#D6D3CD") }
    var colorName by remember { mutableStateOf(initial?.colorName ?: "Crème") }
    var choix by remember { mutableStateOf(initial?.choix ?: "1er Choix") }
    var pricingUnit by remember { mutableStateOf(initial?.pricingUnit ?: "carton") }
    var m2CtnStr by remember { mutableStateOf(formatM2(initial?.m2Carton ?: 1.44)) }
    var pcsStr by remember { mutableStateOf((initial?.pcs ?: 4).toString()) }
    var prixStr by remember { mutableStateOf((initial?.prix ?: 1200.0).toInt().toString()) }
    var prixPieceStr by remember { mutableStateOf((initial?.prixPiece ?: 0.0).toInt().toString()) }
    var prixAchatStr by remember { mutableStateOf((initial?.prixAchat ?: 950.0).toInt().toString()) }

    var activeField by remember { mutableStateOf(FicheNumField.PRIX) }
    var showKeypad by remember { mutableStateOf(false) }
    var isPristine by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(14.dp)
                .testTag("product_fiche_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = Slate900,
            border = BorderStroke(1.5.dp, Slate700)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = Navy950) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (initial == null) "Nouveau Modèle" else "Modifier Modèle",
                            style = MaterialTheme.typography.titleLarge,
                            color = Amber500,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.height(44.dp).testTag("fiche_close_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FERMER", fontWeight = FontWeight.Black)
                        }
                    }
                }

                ClearableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du modèle") },
                    onDismissKeypad = { showKeypad = false },
                    modifier = Modifier.fillMaxWidth().testTag("fiche_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClearableOutlinedTextField(
                        value = usine,
                        onValueChange = { usine = it },
                        label = { Text("Usine") },
                        autoClearValues = setOf("BCRAM"),
                        onDismissKeypad = { showKeypad = false },
                        modifier = Modifier.weight(1f).testTag("fiche_usine_input")
                    )
                    ClearableOutlinedTextField(
                        value = dimension,
                        onValueChange = { dimension = it },
                        label = { Text("Dimension (Format)") },
                        onDismissKeypad = { showKeypad = false },
                        modifier = Modifier.weight(1f).testTag("fiche_dimension_input")
                    )
                }

                // Quick Dimension Presets
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Format :", style = MaterialTheme.typography.labelSmall, color = Slate400, fontWeight = FontWeight.Bold)
                    listOf("60×60", "30×60", "45×45", "80×80", "120×60", "30×90").forEach { dim ->
                        FilterChip(
                            selected = dimension == dim,
                            onClick = { dimension = dim; showKeypad = false },
                            label = { Text(dim, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan500,
                                selectedLabelColor = OnDark,
                                containerColor = Slate950,
                                labelColor = Slate400
                            )
                        )
                    }
                }

                // Finition Input + Quick Chips (updates 3rd POS filter line automatically!)
                ClearableOutlinedTextField(
                    value = finition,
                    onValueChange = { finition = it },
                    label = { Text("Finition (ex: Brillant, Mat, Rectifié)") },
                    onDismissKeypad = { showKeypad = false },
                    modifier = Modifier.fillMaxWidth().testTag("fiche_finition_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("brillant", "mat", "satiné", "rectifié", "lappato", "rustique").forEach { fin ->
                        FilterChip(
                            selected = finition.equals(fin, ignoreCase = true),
                            onClick = { finition = fin; showKeypad = false },
                            label = { Text(fin.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald500,
                                selectedLabelColor = OnDark,
                                containerColor = Slate950,
                                labelColor = Slate400
                            )
                        )
                    }
                }

                // Color swatch presets + Choix
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("1er Choix", "2ème Choix").forEach { c ->
                        FilterChip(
                            selected = choix == c,
                            onClick = { choix = c; showKeypad = false },
                            label = { Text(c, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan500,
                                selectedLabelColor = OnDark,
                                containerColor = Slate950,
                                labelColor = Slate400
                            )
                        )
                    }
                    listOf(
                        "#D6D3CD" to "Crème",
                        "#9CA3AF" to "Gris",
                        "#334155" to "Anthracite",
                        "#B45309" to "Chêne",
                        "#1E293B" to "Noir"
                    ).forEach { (hex, cLabel) ->
                        FilterChip(
                            selected = colorHex.equals(hex, ignoreCase = true),
                            onClick = {
                                colorHex = hex
                                colorName = cLabel
                                showKeypad = false
                            },
                            label = { Text(cLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber500,
                                selectedLabelColor = OnDark,
                                containerColor = Slate950,
                                labelColor = Slate400
                            )
                        )
                    }
                }

                // Mode de vente: Carton (m²) vs Pièce
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vendu par :", style = MaterialTheme.typography.labelMedium, color = Slate400, fontWeight = FontWeight.Bold)
                    FilterChip(
                        selected = pricingUnit == "carton",
                        onClick = { pricingUnit = "carton"; showKeypad = false },
                        label = { Text("Carton (m²)", fontWeight = FontWeight.Black) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate950,
                            labelColor = Slate400
                        ),
                        modifier = Modifier.testTag("fiche_unit_carton")
                    )
                    FilterChip(
                        selected = pricingUnit == "piece",
                        onClick = { pricingUnit = "piece"; showKeypad = false },
                        label = { Text("Pièce", fontWeight = FontWeight.Black) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate950,
                            labelColor = Slate400
                        ),
                        modifier = Modifier.testTag("fiche_unit_piece")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericDisplayField(
                        label = "m²/Ctn",
                        value = m2CtnStr,
                        isActive = showKeypad && activeField == FicheNumField.M2_CTN,
                        onSelect = { activeField = FicheNumField.M2_CTN; isPristine = true; showKeypad = true },
                        onClear = { m2CtnStr = ""; isPristine = false },
                        suffix = "m²",
                        modifier = Modifier.weight(1f),
                        testTag = "fiche_m2ctn_field"
                    )
                    NumericDisplayField(
                        label = "Pcs/Ctn",
                        value = pcsStr,
                        isActive = showKeypad && activeField == FicheNumField.PCS,
                        onSelect = { activeField = FicheNumField.PCS; isPristine = true; showKeypad = true },
                        onClear = { pcsStr = ""; isPristine = false },
                        suffix = "pcs",
                        modifier = Modifier.weight(1f),
                        testTag = "fiche_pcs_field"
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericDisplayField(
                        label = "Prix Vente/m²",
                        value = prixStr,
                        isActive = showKeypad && activeField == FicheNumField.PRIX,
                        onSelect = { activeField = FicheNumField.PRIX; isPristine = true; showKeypad = true },
                        onClear = { prixStr = ""; isPristine = false },
                        suffix = "DA",
                        accentColor = Emerald500,
                        modifier = Modifier.weight(1f),
                        testTag = "fiche_prix_field"
                    )
                    NumericDisplayField(
                        label = if (pricingUnit == "piece") "Prix / Pièce *" else "Prix / Pièce",
                        value = prixPieceStr,
                        isActive = showKeypad && activeField == FicheNumField.PRIX_PIECE,
                        onSelect = { activeField = FicheNumField.PRIX_PIECE; isPristine = true; showKeypad = true },
                        onClear = { prixPieceStr = ""; isPristine = false },
                        suffix = "DA",
                        accentColor = Cyan500,
                        modifier = Modifier.weight(1f),
                        testTag = "fiche_prix_piece_field"
                    )
                }

                if (canViewPrixAchat) {
                    NumericDisplayField(
                        label = "Prix Achat/m² (Confidentiel Admin)",
                        value = prixAchatStr,
                        isActive = showKeypad && activeField == FicheNumField.PRIX_ACHAT,
                        onSelect = { activeField = FicheNumField.PRIX_ACHAT; isPristine = true; showKeypad = true },
                        onClear = { prixAchatStr = ""; isPristine = false },
                        suffix = "DA",
                        accentColor = Amber500,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "fiche_prix_achat_field"
                    )
                }

                AnimatedVisibility(visible = showKeypad, enter = expandVertically(), exit = shrinkVertically()) {
                    NonImeNumericKeypad(
                        onKeyPress = { key ->
                            val cur = when (activeField) {
                                FicheNumField.M2_CTN -> m2CtnStr
                                FicheNumField.PCS -> pcsStr
                                FicheNumField.PRIX -> prixStr
                                FicheNumField.PRIX_PIECE -> prixPieceStr
                                FicheNumField.PRIX_ACHAT -> prixAchatStr
                            }
                            val (next, nextPristine) = applyKeypadInput(cur, key, activeField.decimal, isPristine)
                            when (activeField) {
                                FicheNumField.M2_CTN -> m2CtnStr = next
                                FicheNumField.PCS -> pcsStr = next
                                FicheNumField.PRIX -> prixStr = next
                                FicheNumField.PRIX_PIECE -> prixPieceStr = next
                                FicheNumField.PRIX_ACHAT -> prixAchatStr = next
                            }
                            isPristine = nextPristine
                        },
                        allowDecimal = activeField.decimal,
                        onValidate = { showKeypad = false },
                        validateLabel = "OK"
                    )
                }

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val safePrixAchat = if (canViewPrixAchat) {
                                (prixAchatStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                            } else {
                                initial?.prixAchat ?: 0.0
                            }
                            onSave(
                                TileProduct(
                                    id = initial?.id ?: "p_${UUID.randomUUID().toString().take(8)}",
                                    name = name.trim(),
                                    usine = usine.trim().ifBlank { "AUTRE" },
                                    dimension = dimension.trim().ifBlank { "60×60" },
                                    m2Carton = (m2CtnStr.toDoubleOrNull() ?: 1.44).coerceAtLeast(0.01),
                                    pcs = (pcsStr.toIntOrNull() ?: 4).coerceAtLeast(1),
                                    prix = (prixStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                    prixAchat = safePrixAchat,
                                    stock = initial?.stock ?: 0,
                                    color = colorHex,
                                    colorName = colorName,
                                    image = initial?.image ?: "",
                                    finition = finition.trim().ifBlank { "brillant" }.lowercase(),
                                    choix = choix,
                                    categorie = initial?.categorie ?: "sol",
                                    description = initial?.description ?: "${name.trim()} - ${usine.trim()}",
                                    pricingUnit = pricingUnit,
                                    prixPiece = (prixPieceStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                )
                            )
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("fiche_save_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                ) {
                    Text("Enregistrer Modèle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
