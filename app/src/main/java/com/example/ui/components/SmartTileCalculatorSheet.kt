package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalcInputMode
import com.example.data.model.CartItem
import com.example.data.model.FreeLineItem
import com.example.data.model.TileCalculatorMath
import com.example.data.model.TileDimensionUnit
import com.example.data.model.TileProduct
import com.example.data.model.formatDa
import com.example.data.model.formatM2
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
import com.example.ui.theme.Navy800
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
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID
import kotlin.math.round

private enum class CalcFieldTarget(val allowDecimal: Boolean) {
    SURFACE(true),
    CARTONS(false),
    PIECES(false),
    TILE_LENGTH(true),
    TILE_WIDTH(true),
    COLISAGE_M2(true),
    PCS_PER_CARTON(false),
    PRICE_PER_M2(true),
    PRICE_PER_PIECE(true)
}

private val cleanDecimalFormat = DecimalFormat("0.####", DecimalFormatSymbols(Locale.US))

private fun formatDimNum(v: Double): String {
    val clean = (v * 10000.0).let { round(it) / 10000.0 }
    return cleanDecimalFormat.format(clean)
}

private fun formatMoneyInput(v: Double): String {
    return if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}

private fun parseInitialTileDims(dimension: String?): Pair<String, String> {
    if (dimension.isNullOrBlank()) return "60" to "60"
    val nums = Regex("""\d+(?:[.,]\d+)?""").findAll(dimension).map { it.value.replace(',', '.') }.toList()
    return if (nums.size >= 2) nums[0] to nums[1] else "60" to "60"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTileCalculatorSheet(
    product: TileProduct?,
    editingCartItem: CartItem?,
    isOneTimeProduct: Boolean = false,
    canViewPrixAchat: Boolean = false,
    canModifyPrices: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmProductCart: (
        product: TileProduct,
        cartons: Int,
        pieces: Int,
        m2Total: Double,
        prixPerM2: Double,
        bain: String,
        calibre: String,
        editingItemId: String?,
        prixPiece: Double
    ) -> Unit,
    onConfirmStandaloneFreeLine: (FreeLineItem) -> Unit
) {
    BackHandler { onDismiss() }
    val allowCostView = canViewPrixAchat

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isCatalogBound = product != null && !isOneTimeProduct
    val canEditPrice = canModifyPrices || isOneTimeProduct || !isCatalogBound
    val isProductCartMode = isCatalogBound || isOneTimeProduct || editingCartItem != null
    val startsInPieceMode = editingCartItem?.isSoldByPiece ?: (product?.isSoldByPiece == true)

    var customProductName by remember {
        mutableStateOf(
            when {
                editingCartItem != null -> editingCartItem.name
                isOneTimeProduct -> ""
                else -> ""
            }
        )
    }

    val (initTileL, initTileW) = remember(product?.dimension, editingCartItem?.dimension) {
        parseInitialTileDims(editingCartItem?.dimension ?: product?.dimension)
    }
    var tileUnit by remember { mutableStateOf(TileDimensionUnit.CM) }
    var tileLengthStr by remember { mutableStateOf(initTileL) }
    var tileWidthStr by remember { mutableStateOf(initTileW) }
    var showColisageBuilder by remember { mutableStateOf(!isCatalogBound) }

    var mode by remember {
        mutableStateOf(
            if (editingCartItem != null || startsInPieceMode) CalcInputMode.CARTONS_PIECES
            else CalcInputMode.SURFACE
        )
    }
    var marginPercent by remember { mutableIntStateOf(0) }

    var surfaceStr by remember {
        mutableStateOf(
            if (editingCartItem != null) formatM2(editingCartItem.m2Total)
            else "20"
        )
    }
    var cartonsStr by remember {
        mutableStateOf(
            when {
                editingCartItem != null -> editingCartItem.cartons.toString()
                startsInPieceMode -> "0"
                else -> "1"
            }
        )
    }
    var piecesStr by remember {
        mutableStateOf(
            when {
                editingCartItem != null -> editingCartItem.pieces.toString()
                startsInPieceMode -> (product?.pcs?.coerceAtLeast(1) ?: 1).toString()
                else -> "0"
            }
        )
    }
    var colisageM2Str by remember {
        mutableStateOf(
            formatDimNum(editingCartItem?.m2Carton ?: product?.m2Carton ?: 1.44)
        )
    }
    var pcsPerCartonStr by remember {
        mutableStateOf((editingCartItem?.pcs ?: product?.pcs ?: 4).toString())
    }

    val initM2Price = editingCartItem?.prixUnitaire ?: product?.prix ?: 1200.0
    val initM2Ctn = (editingCartItem?.m2Carton ?: product?.m2Carton ?: 1.44).coerceAtLeast(0.01)
    val initPcs = (editingCartItem?.pcs ?: product?.pcs ?: 4).coerceAtLeast(1)
    val catalogPiecePrice = product?.prixPiece?.takeIf { it > 0.0 }

    var pricePerM2Str by remember { mutableStateOf(formatMoneyInput(initM2Price)) }
    var pricePerPieceStr by remember {
        val initPiecePrice = editingCartItem?.prixPiece?.takeIf { it > 0.0 }
            ?: catalogPiecePrice
            ?: round(initM2Price * (initM2Ctn / initPcs))
        mutableStateOf(formatMoneyInput(initPiecePrice))
    }
    var hasCustomPiecePrice by remember {
        mutableStateOf((editingCartItem?.prixPiece ?: 0.0) > 0.0 || (startsInPieceMode && catalogPiecePrice != null))
    }

    var bainNote by remember { mutableStateOf(editingCartItem?.bain ?: "") }
    var calibreNote by remember { mutableStateOf(editingCartItem?.calibre ?: "") }

    var activeField by remember {
        mutableStateOf(
            when {
                startsInPieceMode -> CalcFieldTarget.PIECES
                editingCartItem != null -> CalcFieldTarget.CARTONS
                else -> CalcFieldTarget.SURFACE
            }
        )
    }
    var isPristine by remember { mutableStateOf(true) }
    var showKeypad by remember { mutableStateOf(true) }

    val m2PerCartonVal = (colisageM2Str.toDoubleOrNull() ?: product?.m2Carton ?: 1.44).coerceAtLeast(0.01)
    val pcsPerCartonVal = (pcsPerCartonStr.toIntOrNull() ?: product?.pcs ?: 4).coerceAtLeast(1)
    val prixVal = (pricePerM2Str.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val prixPieceVal = (pricePerPieceStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val loosePiecesCount = piecesStr.toIntOrNull() ?: 0

    val singleTileAreaPreview = remember(tileLengthStr, tileWidthStr, tileUnit) {
        val l = tileLengthStr.toDoubleOrNull() ?: 0.0
        val w = tileWidthStr.toDoubleOrNull() ?: 0.0
        if (l > 0.0 && w > 0.0) {
            val lm = if (tileUnit == TileDimensionUnit.CM) l / 100.0 else l
            val wm = if (tileUnit == TileDimensionUnit.CM) w / 100.0 else w
            lm * wm
        } else 0.0
    }

    fun recomputeColisageFromTileDims(
        nextL: String = tileLengthStr,
        nextW: String = tileWidthStr,
        nextPcs: String = pcsPerCartonStr,
        unit: TileDimensionUnit = tileUnit
    ) {
        val l = nextL.toDoubleOrNull() ?: 0.0
        val w = nextW.toDoubleOrNull() ?: 0.0
        val pcs = nextPcs.toIntOrNull() ?: 0
        if (l > 0.0 && w > 0.0 && pcs > 0) {
            val boxRes = TileCalculatorMath.calculateM2PerCartonAndCartons(
                tileLength = l,
                tileWidth = w,
                unit = unit,
                piecesPerCarton = pcs,
                requiredAreaM2 = 1.0,
                marginPercent = 0
            )
            if (boxRes.m2PerCarton > 0.0) {
                colisageM2Str = formatDimNum(boxRes.m2PerCarton)
                if (!hasCustomPiecePrice) {
                    val pM2 = pricePerM2Str.toDoubleOrNull() ?: 0.0
                    pricePerPieceStr = formatMoneyInput(round(pM2 * (boxRes.m2PerCarton / pcs.coerceAtLeast(1))))
                }
            }
        }
    }

    val result = remember(
        mode, surfaceStr, cartonsStr, piecesStr,
        marginPercent, m2PerCartonVal, pcsPerCartonVal, prixVal, prixPieceVal
    ) {
        when (mode) {
            CalcInputMode.CARTONS_PIECES -> TileCalculatorMath.calculateFromCartonsAndPieces(
                cartons = cartonsStr.toIntOrNull() ?: 0,
                loosePieces = piecesStr.toIntOrNull() ?: 0,
                m2PerCarton = m2PerCartonVal,
                pcsPerCarton = pcsPerCartonVal,
                prixPerM2 = prixVal,
                prixParPiece = prixPieceVal
            )
            else -> TileCalculatorMath.calculateFromSurface(
                surfaceM2 = surfaceStr.toDoubleOrNull() ?: 0.0,
                marginPercent = marginPercent,
                m2PerCarton = m2PerCartonVal,
                pcsPerCarton = pcsPerCartonVal,
                prixPerM2 = prixVal
            )
        }
    }

    fun selectNumericField(target: CalcFieldTarget) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        activeField = target
        isPristine = true
        showKeypad = true
    }

    fun currentFieldValue(): String = when (activeField) {
        CalcFieldTarget.SURFACE -> surfaceStr
        CalcFieldTarget.CARTONS -> cartonsStr
        CalcFieldTarget.PIECES -> piecesStr
        CalcFieldTarget.TILE_LENGTH -> tileLengthStr
        CalcFieldTarget.TILE_WIDTH -> tileWidthStr
        CalcFieldTarget.COLISAGE_M2 -> colisageM2Str
        CalcFieldTarget.PCS_PER_CARTON -> pcsPerCartonStr
        CalcFieldTarget.PRICE_PER_M2 -> pricePerM2Str
        CalcFieldTarget.PRICE_PER_PIECE -> pricePerPieceStr
    }

    fun setFieldValue(newVal: String) {
        when (activeField) {
            CalcFieldTarget.SURFACE -> surfaceStr = newVal
            CalcFieldTarget.CARTONS -> cartonsStr = newVal
            CalcFieldTarget.PIECES -> piecesStr = newVal
            CalcFieldTarget.TILE_LENGTH -> {
                tileLengthStr = newVal
                recomputeColisageFromTileDims(nextL = newVal)
            }
            CalcFieldTarget.TILE_WIDTH -> {
                tileWidthStr = newVal
                recomputeColisageFromTileDims(nextW = newVal)
            }
            CalcFieldTarget.COLISAGE_M2 -> {
                colisageM2Str = newVal
                if (!hasCustomPiecePrice) {
                    val m2c = (newVal.toDoubleOrNull() ?: m2PerCartonVal).coerceAtLeast(0.01)
                    pricePerPieceStr = formatMoneyInput(round(prixVal * (m2c / pcsPerCartonVal)))
                }
            }
            CalcFieldTarget.PCS_PER_CARTON -> {
                pcsPerCartonStr = newVal
                recomputeColisageFromTileDims(nextPcs = newVal)
            }
            CalcFieldTarget.PRICE_PER_M2 -> {
                pricePerM2Str = newVal
                if (!hasCustomPiecePrice) {
                    val pM2 = (newVal.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    pricePerPieceStr = formatMoneyInput(round(pM2 * (m2PerCartonVal / pcsPerCartonVal)))
                }
            }
            CalcFieldTarget.PRICE_PER_PIECE -> {
                hasCustomPiecePrice = true
                pricePerPieceStr = newVal
            }
        }
    }

    fun submitCalculation() {
        if (result.deliveredM2 <= 0.0) return
        val dimText = if (tileLengthStr.isNotBlank() && tileWidthStr.isNotBlank()) {
            "${tileLengthStr}×${tileWidthStr}${tileUnit.suffix}"
        } else {
            product?.dimension ?: "Standard"
        }
        val activePieceCost = if (result.loosePieces > 0) prixPieceVal else 0.0

        if (isOneTimeProduct) {
            val fallbackName = "Carrelage $dimText (${formatM2(m2PerCartonVal)}m²/ctn)"
            val finalProduct = TileProduct(
                id = product?.id ?: "onetime_${UUID.randomUUID().toString().take(8)}",
                name = customProductName.trim().ifBlank { fallbackName },
                usine = product?.usine?.ifBlank { "Vente unique" } ?: "Vente unique",
                dimension = dimText,
                m2Carton = m2PerCartonVal,
                pcs = pcsPerCartonVal,
                prix = prixVal,
                prixAchat = product?.prixAchat ?: 0.0,
                stock = 0,
                color = product?.color ?: "#D97706",
                colorName = product?.colorName ?: "Cuivre",
                image = product?.image ?: ""
            )
            onConfirmProductCart(
                finalProduct,
                result.cartons,
                result.loosePieces,
                result.deliveredM2,
                prixVal,
                bainNote.trim(),
                calibreNote.trim(),
                editingCartItem?.id,
                activePieceCost
            )
        } else if (isCatalogBound && product != null) {
            val updatedProduct = product.copy(
                m2Carton = m2PerCartonVal,
                pcs = pcsPerCartonVal
            )
            onConfirmProductCart(
                updatedProduct,
                result.cartons,
                result.loosePieces,
                result.deliveredM2,
                prixVal,
                bainNote.trim(),
                calibreNote.trim(),
                editingCartItem?.id,
                activePieceCost
            )
        } else {
            val titlePrefix = customProductName.trim().ifBlank { "Carrelage $dimText" }
            onConfirmStandaloneFreeLine(
                FreeLineItem(
                    id = "calc_${UUID.randomUUID().toString().take(8)}",
                    designation = "$titlePrefix (${formatM2(m2PerCartonVal)}m²/ctn • ${result.cartons} ctn)",
                    quantite = result.deliveredM2,
                    unite = "m²",
                    prixUnitaire = prixVal,
                    sousTotal = result.totalDa
                )
            )
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
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("smart_tile_calculator_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Sleek Product Header (Zero Stock Clutter)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Navy950,
                border = BorderStroke(1.dp, Navy800)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isCatalogBound && product != null) {
                            TileSwatchImage(
                                imageUrl = product.image,
                                colorHex = product.color,
                                contentDescription = product.name,
                                modifier = Modifier.size(44.dp),
                                cornerRadius = 11.dp
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = if (isOneTimeProduct) Amber500 else Cyan500
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isOneTimeProduct) Icons.Default.AddBusiness else Icons.Default.Calculate,
                                        contentDescription = "Calculateur",
                                        tint = OnDark
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when {
                                    isOneTimeProduct && editingCartItem != null -> "Modifier Vente Unique"
                                    isOneTimeProduct -> "Produit 1×"
                                    product != null -> product.name
                                    editingCartItem != null -> editingCartItem.name
                                    else -> "Calculer & Ajouter"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = Slate100,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = when {
                                isOneTimeProduct -> "Format & prix libres"
                                product != null -> "${product.usine} • ${product.dimension} • ${product.finition.replaceFirstChar { it.uppercase() }}"
                                editingCartItem != null -> "${editingCartItem.usine} • ${formatM2(m2PerCartonVal)} m²/ctn"
                                else -> "Surface ↔ Cartons"
                            }
                            Text(
                                text = subtitle + if (allowCostView && product != null && product.prixAchat > 0.0) {
                                    " • Ach: ${formatDa(product.prixAchat)}"
                                } else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = Cyan300,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("calc_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("FERMER", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // 2. UNMISTAKABLE RESULT HERO CARD AT THE TOP (Pops at a Glance!)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calc_result_hero_card"),
                shape = RoundedCornerShape(18.dp),
                color = Emerald950,
                border = BorderStroke(2.dp, Emerald400),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Emerald950, Cyan950.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Box 1: Cartons / Pcs
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Slate950.copy(alpha = 0.85f),
                            border = BorderStroke(1.2.dp, Amber400.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "CARTONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Amber300,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = buildString {
                                        append("${result.cartons}")
                                        if (result.loosePieces > 0) append("+${result.loosePieces}p")
                                    },
                                    style = PosNumericTextStyle.copy(fontSize = 24.sp),
                                    color = Amber400,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${result.cartons * result.pcsPerCarton + result.loosePieces} pcs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Box 2: Surface m²
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Slate950.copy(alpha = 0.85f),
                            border = BorderStroke(1.2.dp, Cyan400.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SURFACE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Cyan300,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${formatM2(result.deliveredM2)}",
                                    style = PosNumericTextStyle.copy(fontSize = 22.sp),
                                    color = Cyan400,
                                    maxLines = 1
                                )
                                Text(
                                    text = "m² livrés",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Box 3: Total DA
                        Surface(
                            modifier = Modifier.weight(1.35f),
                            shape = RoundedCornerShape(12.dp),
                            color = Slate950.copy(alpha = 0.92f),
                            border = BorderStroke(1.5.dp, Emerald400)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "TOTAL À PAYER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald300,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = formatDa(result.totalDa),
                                    style = PosNumericTextStyle.copy(fontSize = 21.sp),
                                    color = Emerald400,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatDa(prixVal * result.m2PerCarton)} / ctn",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate400,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (mode != CalcInputMode.CARTONS_PIECES && result.surplusM2 > 0.005) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (marginPercent > 0) {
                                    "Demandé ${formatM2(result.requestedM2)} m² (+$marginPercent%)"
                                } else {
                                    "Demandé ${formatM2(result.requestedM2)} m²"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate100.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Surplus +${formatM2(result.surplusM2)} m²",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber300,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Custom Name input for One-Time Sale
            if (isOneTimeProduct || !isCatalogBound) {
                ClearableOutlinedTextField(
                    value = customProductName,
                    onValueChange = { customProductName = it },
                    label = {
                        Text(if (isOneTimeProduct) "Nom du produit (vente unique)" else "Désignation (optionnel)")
                    },
                    placeholder = { Text("Ex: Carrelage Marbre Beige 60×60", color = Slate400) },
                    onDismissKeypad = { showKeypad = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc_onetime_name_input")
                )
            }

            // 3. Clean 2-Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalcModeChip(
                    selected = mode != CalcInputMode.CARTONS_PIECES,
                    label = "Surface (m²)",
                    icon = Icons.Default.SquareFoot,
                    onClick = {
                        mode = CalcInputMode.SURFACE
                        selectNumericField(CalcFieldTarget.SURFACE)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "calc_tab_surface"
                )
                CalcModeChip(
                    selected = mode == CalcInputMode.CARTONS_PIECES,
                    label = "Cartons & Pièces",
                    icon = Icons.Default.GridOn,
                    onClick = {
                        mode = CalcInputMode.CARTONS_PIECES
                        if (cartonsStr == "0") cartonsStr = result.cartons.coerceAtLeast(1).toString()
                        selectNumericField(CalcFieldTarget.CARTONS)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "calc_tab_cartons"
                )
            }

            // 4. Crisp Active Input Fields
            if (mode != CalcInputMode.CARTONS_PIECES) {
                NumericDisplayField(
                    label = "Surface demandée par le client",
                    value = surfaceStr,
                    isActive = showKeypad && activeField == CalcFieldTarget.SURFACE,
                    onSelect = { selectNumericField(CalcFieldTarget.SURFACE) },
                    onClear = { surfaceStr = "" },
                    suffix = "m²",
                    accentColor = Amber400,
                    testTag = "calc_surface_field"
                )
                MarginChipsRow(
                    selectedMargin = marginPercent,
                    onSelectMargin = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        marginPercent = it
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumericDisplayField(
                            label = "Cartons",
                            value = cartonsStr,
                            isActive = showKeypad && activeField == CalcFieldTarget.CARTONS,
                            onSelect = { selectNumericField(CalcFieldTarget.CARTONS) },
                            onClear = { cartonsStr = "" },
                            suffix = "ctn",
                            accentColor = Amber400,
                            testTag = "calc_cartons_field"
                        )
                        StepperRow(
                            onMinus = {
                                val c = (cartonsStr.toIntOrNull() ?: 0).minus(1).coerceAtLeast(0)
                                cartonsStr = c.toString()
                            },
                            onPlus = {
                                val c = (cartonsStr.toIntOrNull() ?: 0) + 1
                                cartonsStr = c.toString()
                            }
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumericDisplayField(
                            label = "Pièces",
                            value = piecesStr,
                            isActive = showKeypad && activeField == CalcFieldTarget.PIECES,
                            onSelect = { selectNumericField(CalcFieldTarget.PIECES) },
                            onClear = { piecesStr = "" },
                            suffix = "pcs",
                            accentColor = Cyan400,
                            testTag = "calc_pieces_field"
                        )
                        StepperRow(
                            onMinus = {
                                val p = (piecesStr.toIntOrNull() ?: 0).minus(1).coerceAtLeast(0)
                                piecesStr = p.toString()
                            },
                            onPlus = {
                                val p = (piecesStr.toIntOrNull() ?: 0) + 1
                                piecesStr = p.toString()
                            }
                        )
                    }
                }
            }

            // 5. Price + Optional Spec Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumericDisplayField(
                    label = if (canEditPrice) "Prix / m²" else "Prix / m² (Fixe)",
                    value = pricePerM2Str,
                    isActive = canEditPrice && showKeypad && activeField == CalcFieldTarget.PRICE_PER_M2,
                    onSelect = { if (canEditPrice) selectNumericField(CalcFieldTarget.PRICE_PER_M2) },
                    onClear = if (canEditPrice) ({ pricePerM2Str = "" }) else null,
                    enabled = canEditPrice,
                    suffix = "DA",
                    accentColor = Emerald400,
                    modifier = Modifier.weight(1f),
                    testTag = "calc_price_field"
                )
                if (mode == CalcInputMode.CARTONS_PIECES || loosePiecesCount > 0) {
                    NumericDisplayField(
                        label = if (canEditPrice) "Prix / Pièce" else "Prix / Pièce (Fixe)",
                        value = pricePerPieceStr,
                        isActive = canEditPrice && showKeypad && activeField == CalcFieldTarget.PRICE_PER_PIECE,
                        onSelect = { if (canEditPrice) selectNumericField(CalcFieldTarget.PRICE_PER_PIECE) },
                        onClear = if (canEditPrice) ({
                            hasCustomPiecePrice = true
                            pricePerPieceStr = ""
                        }) else null,
                        enabled = canEditPrice,
                        suffix = "DA/p",
                        accentColor = Cyan400,
                        modifier = Modifier.weight(1f),
                        testTag = "calc_piece_price_field"
                    )
                } else if (isProductCartMode) {
                    ClearableOutlinedTextField(
                        value = bainNote,
                        onValueChange = { bainNote = it },
                        label = { Text("Bain / Calibre", maxLines = 1) },
                        onDismissKeypad = { showKeypad = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calc_bain_input")
                    )
                }
            }

            // Collapsible Colisage Bar (compact & unobtrusive)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showColisageBuilder = !showColisageBuilder },
                shape = RoundedCornerShape(12.dp),
                color = Slate900,
                border = BorderStroke(1.dp, if (showColisageBuilder) Cyan500.copy(alpha = 0.6f) else Slate800)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Colisage : ${formatDimNum(m2PerCartonVal)} m²/ctn • $pcsPerCartonVal pcs/ctn",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cyan300,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (showColisageBuilder) "Masquer" else "Ajuster",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (showColisageBuilder) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showColisageBuilder) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate900,
                    border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TileCalculatorMath.TILE_FORMAT_PRESETS.forEach { preset ->
                                val targetL = if (tileUnit == TileDimensionUnit.CM) preset.lengthCm else preset.lengthCm / 100.0
                                val targetW = if (tileUnit == TileDimensionUnit.CM) preset.widthCm else preset.widthCm / 100.0
                                val isSel = kotlin.math.abs((tileLengthStr.toDoubleOrNull() ?: 0.0) - targetL) < 0.001 &&
                                    kotlin.math.abs((tileWidthStr.toDoubleOrNull() ?: 0.0) - targetW) < 0.001 &&
                                    pcsPerCartonVal == preset.piecesPerCarton
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        val nextL = formatDimNum(targetL)
                                        val nextW = formatDimNum(targetW)
                                        val nextPcs = preset.piecesPerCarton.toString()
                                        tileLengthStr = nextL
                                        tileWidthStr = nextW
                                        pcsPerCartonStr = nextPcs
                                        recomputeColisageFromTileDims(nextL, nextW, nextPcs, tileUnit)
                                    },
                                    label = {
                                        Text("${preset.label} (${preset.piecesPerCarton}p)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Amber500,
                                        selectedLabelColor = OnDark,
                                        containerColor = Slate950,
                                        labelColor = Slate400
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NumericDisplayField(
                                label = "Longueur",
                                value = tileLengthStr,
                                isActive = showKeypad && activeField == CalcFieldTarget.TILE_LENGTH,
                                onSelect = { selectNumericField(CalcFieldTarget.TILE_LENGTH) },
                                onClear = { tileLengthStr = "" },
                                suffix = tileUnit.suffix,
                                accentColor = Cyan400,
                                modifier = Modifier.weight(1f),
                                testTag = "calc_tile_len_field"
                            )
                            NumericDisplayField(
                                label = "Largeur",
                                value = tileWidthStr,
                                isActive = showKeypad && activeField == CalcFieldTarget.TILE_WIDTH,
                                onSelect = { selectNumericField(CalcFieldTarget.TILE_WIDTH) },
                                onClear = { tileWidthStr = "" },
                                suffix = tileUnit.suffix,
                                accentColor = Cyan400,
                                modifier = Modifier.weight(1f),
                                testTag = "calc_tile_wid_field"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NumericDisplayField(
                                label = "Pcs / Carton",
                                value = pcsPerCartonStr,
                                isActive = showKeypad && activeField == CalcFieldTarget.PCS_PER_CARTON,
                                onSelect = { selectNumericField(CalcFieldTarget.PCS_PER_CARTON) },
                                onClear = { pcsPerCartonStr = "" },
                                suffix = "pcs",
                                accentColor = Amber400,
                                modifier = Modifier.weight(1f),
                                testTag = "calc_pcs_per_carton_field"
                            )
                            NumericDisplayField(
                                label = "m² / Carton",
                                value = colisageM2Str,
                                isActive = showKeypad && activeField == CalcFieldTarget.COLISAGE_M2,
                                onSelect = { selectNumericField(CalcFieldTarget.COLISAGE_M2) },
                                onClear = { colisageM2Str = "" },
                                suffix = "m²",
                                accentColor = Emerald400,
                                modifier = Modifier.weight(1f),
                                testTag = "calc_colisage_field"
                            )
                        }

                        if (singleTileAreaPreview > 0.0) {
                            Text(
                                text = "1 carreau = ${formatDimNum(singleTileAreaPreview)} m² × $pcsPerCartonVal pcs = ${formatDimNum(m2PerCartonVal)} m²/ctn",
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald300,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Embedded Non-IME Keypad
            AnimatedVisibility(
                visible = showKeypad,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val chips = when (activeField) {
                    CalcFieldTarget.SURFACE -> listOf("+5" to 5.0, "+10" to 10.0, "+25" to 25.0, "+50" to 50.0)
                    CalcFieldTarget.CARTONS -> listOf("+1" to 1.0, "+5" to 5.0, "+10" to 10.0, "+20" to 20.0)
                    CalcFieldTarget.PIECES -> listOf("+1" to 1.0, "+2" to 2.0, "+3" to 3.0)
                    CalcFieldTarget.PRICE_PER_M2, CalcFieldTarget.PRICE_PER_PIECE -> listOf("+50" to 50.0, "+100" to 100.0, "-50" to -50.0)
                    else -> emptyList()
                }
                NonImeNumericKeypad(
                    onKeyPress = { key ->
                        val (nextVal, nextPristine) = applyKeypadInput(
                            current = currentFieldValue(),
                            key = key,
                            allowDecimal = activeField.allowDecimal,
                            isPristine = isPristine
                        )
                        setFieldValue(nextVal)
                        isPristine = nextPristine
                    },
                    allowDecimal = activeField.allowDecimal,
                    quickChips = chips,
                    onQuickDelta = { delta ->
                        val next = applyKeypadQuickDelta(
                            current = currentFieldValue(),
                            delta = delta,
                            allowDecimal = activeField.allowDecimal
                        )
                        setFieldValue(next)
                        isPristine = false
                    },
                    onValidate = { submitCalculation() },
                    validateLabel = when {
                        editingCartItem != null -> "Valider"
                        isOneTimeProduct -> "Ajouter 1×"
                        else -> "Ajouter (${result.cartons} ctn)"
                    },
                    accentColor = Amber500
                )
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isCatalogBound) {
                    OutlinedButton(
                        onClick = {
                            surfaceStr = "0"
                            cartonsStr = "0"
                            piecesStr = "0"
                            marginPercent = 0
                            selectNumericField(CalcFieldTarget.SURFACE)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("calc_reset_btn"),
                        border = BorderStroke(1.5.dp, Slate700),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Effacer", color = Slate100, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { submitCalculation() },
                    enabled = result.deliveredM2 > 0.0,
                    modifier = Modifier
                        .weight(1.7f)
                        .height(54.dp)
                        .testTag("calc_confirm_action_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = OnDark
                    )
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (editingCartItem != null) {
                            "Valider Modification"
                        } else {
                            "Ajouter au Panier • ${formatDa(result.totalDa)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcModeChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Amber500 else Slate900,
        border = BorderStroke(1.5.dp, if (selected) Amber400 else Slate700)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) OnDark else Slate400,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) OnDark else Slate100,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MarginChipsRow(
    selectedMargin: Int,
    onSelectMargin: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Coupe :",
            style = MaterialTheme.typography.labelMedium,
            color = Slate400,
            fontWeight = FontWeight.Bold
        )
        TileCalculatorMath.MARGIN_PRESETS.forEach { margin ->
            val isSel = selectedMargin == margin
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectMargin(margin) }
                    .testTag("calc_margin_$margin"),
                shape = RoundedCornerShape(10.dp),
                color = if (isSel) Amber950 else Slate900,
                border = BorderStroke(1.5.dp, if (isSel) Amber400 else Slate700)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (margin == 0) "0%" else "+$margin%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) Amber300 else Slate400,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onMinus),
            shape = RoundedCornerShape(10.dp),
            color = Rose950,
            border = BorderStroke(1.dp, Rose500.copy(alpha = 0.5f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Remove, contentDescription = "Moins", tint = Rose400, modifier = Modifier.size(18.dp))
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPlus),
            shape = RoundedCornerShape(10.dp),
            color = Emerald950,
            border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.5f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "Plus", tint = Emerald300, modifier = Modifier.size(18.dp))
            }
        }
    }
}
