package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SquareFoot
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FreeLineItem
import com.example.data.model.TileBoxCalculationResult
import com.example.data.model.TileCalculatorMath
import com.example.data.model.TileDimensionUnit
import com.example.data.model.TileProduct
import com.example.data.model.formatDa
import com.example.data.model.formatM2
import com.example.ui.components.TileCalculator
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Emerald300
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
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

private val QUICK_AREAS_M2 = listOf(10, 20, 25, 40, 60, 100)

@Composable
fun M2PerCartonCalculatorScreen(
    modifier: Modifier = Modifier,
    products: List<TileProduct> = emptyList(),
    onAddProductToCart: ((TileProduct, Int, Double) -> Unit)? = null,
    onAddCalculatedLineToCart: ((FreeLineItem) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var calculatorTabMode by rememberSaveable { mutableStateOf("surface") }
    var specModeName by rememberSaveable { mutableStateOf(TileSpecMode.DIMENSIONS.name) }
    val specMode = remember(specModeName) { TileSpecMode.valueOf(specModeName) }

    var unitName by rememberSaveable { mutableStateOf(TileDimensionUnit.CM.name) }
    val unit = remember(unitName) { TileDimensionUnit.valueOf(unitName) }

    var requiredAreaInput by rememberSaveable { mutableStateOf("25") }
    var marginPercent by rememberSaveable { mutableIntStateOf(0) }

    var lengthInput by rememberSaveable { mutableStateOf("60") }
    var widthInput by rememberSaveable { mutableStateOf("60") }
    var piecesInput by rememberSaveable { mutableStateOf("4") }

    var directM2PerCartonInput by rememberSaveable { mutableStateOf("1.44") }
    var directPiecesInput by rememberSaveable { mutableStateOf("4") }

    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedProduct = remember(products, selectedProductId) {
        products.find { it.id == selectedProductId } ?: products.firstOrNull()
    }

    // Automatic real-time calculation whenever area, margin, or tile specs change
    val liveResult: TileBoxCalculationResult? = remember(
        specMode,
        unit,
        requiredAreaInput,
        marginPercent,
        lengthInput,
        widthInput,
        piecesInput,
        directM2PerCartonInput,
        directPiecesInput,
        selectedProduct
    ) {
        val area = parseCalcDecimal(requiredAreaInput) ?: return@remember null
        if (area <= 0.0) return@remember null

        when (specMode) {
            TileSpecMode.DIMENSIONS -> {
                val l = parseCalcDecimal(lengthInput) ?: return@remember null
                val w = parseCalcDecimal(widthInput) ?: return@remember null
                val pcs = piecesInput.trim().toIntOrNull() ?: return@remember null
                if (l <= 0.0 || w <= 0.0 || pcs <= 0) return@remember null
                TileCalculatorMath.calculateM2PerCartonAndCartons(
                    tileLength = l,
                    tileWidth = w,
                    unit = unit,
                    piecesPerCarton = pcs,
                    requiredAreaM2 = area,
                    marginPercent = marginPercent
                )
            }

            TileSpecMode.COLISAGE -> {
                val m2Ctn = parseCalcDecimal(directM2PerCartonInput) ?: return@remember null
                val pcs = (directPiecesInput.trim().toIntOrNull() ?: 1).coerceAtLeast(1)
                if (m2Ctn <= 0.0) return@remember null
                TileCalculatorMath.calculateCartonsFromM2PerCarton(
                    m2PerCarton = m2Ctn,
                    piecesPerCarton = pcs,
                    requiredAreaM2 = area,
                    marginPercent = marginPercent
                )
            }

            TileSpecMode.CATALOG -> {
                val prod = selectedProduct ?: return@remember null
                if (prod.m2Carton <= 0.0) return@remember null
                TileCalculatorMath.calculateCartonsFromM2PerCarton(
                    m2PerCarton = prod.m2Carton,
                    piecesPerCarton = prod.pcs.coerceAtLeast(1),
                    requiredAreaM2 = area,
                    marginPercent = marginPercent
                )
            }
        }
    }

    val hasInputError = remember(
        specMode,
        requiredAreaInput,
        lengthInput,
        widthInput,
        piecesInput,
        directM2PerCartonInput,
        liveResult
    ) {
        val hasAnyAreaText = requiredAreaInput.isNotBlank()
        hasAnyAreaText && liveResult == null
    }

    fun switchUnit(targetUnit: TileDimensionUnit) {
        if (targetUnit == unit) return
        val factor = if (targetUnit == TileDimensionUnit.METER) 0.01 else 100.0
        parseCalcDecimal(lengthInput)?.let { lengthInput = formatCalcNumber(it * factor) }
        parseCalcDecimal(widthInput)?.let { widthInput = formatCalcNumber(it * factor) }
        unitName = targetUnit.name
    }

    fun resetAll() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        requiredAreaInput = ""
        marginPercent = 0
        when (specMode) {
            TileSpecMode.DIMENSIONS -> {
                lengthInput = "60"
                widthInput = "60"
                piecesInput = "4"
            }
            TileSpecMode.COLISAGE -> {
                directM2PerCartonInput = "1.44"
                directPiecesInput = "4"
            }
            TileSpecMode.CATALOG -> Unit
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            val isWide = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 980.dp)
                    .align(Alignment.TopCenter)
                    .testTag("m2_per_carton_calculator_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CalculatorHeaderCard()

                CalculatorTopModeToggle(
                    activeMode = calculatorTabMode,
                    onSelectMode = { calculatorTabMode = it }
                )

                if (calculatorTabMode == "room") {
                    TileCalculator(
                        onAddToCart = if (onAddCalculatedLineToCart != null) {
                            { res ->
                                onAddCalculatedLineToCart(
                                    FreeLineItem(
                                        id = "room_calc_${UUID.randomUUID().toString().take(8)}",
                                        designation = "Carrelage Pièce ${formatCalcNumber(res.roomLengthM)}×${formatCalcNumber(res.roomWidthM)}m (${res.cartonsNeeded} ctn • ${formatM2(res.m2PerCarton)} m²/ctn)",
                                        quantite = res.deliveredAreaM2,
                                        unite = "m²",
                                        prixUnitaire = 0.0,
                                        sousTotal = 0.0
                                    )
                                )
                            }
                        } else null
                    )
                } else if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AreaInputCard(
                                requiredAreaInput = requiredAreaInput,
                                marginPercent = marginPercent,
                                onAreaChange = { requiredAreaInput = it },
                                onSelectMargin = { marginPercent = it },
                                onReset = ::resetAll,
                                onDone = {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            )
                            TileSpecsCard(
                                specMode = specMode,
                                unit = unit,
                                lengthInput = lengthInput,
                                widthInput = widthInput,
                                piecesInput = piecesInput,
                                directM2PerCartonInput = directM2PerCartonInput,
                                directPiecesInput = directPiecesInput,
                                products = products,
                                selectedProduct = selectedProduct,
                                onSelectMode = { specModeName = it.name },
                                onSwitchUnit = ::switchUnit,
                                onLengthChange = { lengthInput = it },
                                onWidthChange = { widthInput = it },
                                onPiecesChange = { piecesInput = it },
                                onSelectDimensionPreset = { l, w, pcs ->
                                    lengthInput = l
                                    widthInput = w
                                    piecesInput = pcs
                                },
                                onDirectM2Change = { directM2PerCartonInput = it },
                                onDirectPiecesChange = { directPiecesInput = it },
                                onSelectProduct = { selectedProductId = it.id }
                            )
                        }

                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            LiveResultSection(
                                result = liveResult,
                                hasInputError = hasInputError,
                                activeProduct = if (specMode == TileSpecMode.CATALOG) selectedProduct else null,
                                specSummaryLabel = buildSpecSummary(
                                    specMode = specMode,
                                    lengthInput = lengthInput,
                                    widthInput = widthInput,
                                    unit = unit,
                                    directM2PerCartonInput = directM2PerCartonInput,
                                    selectedProduct = selectedProduct
                                ),
                                onAddToCart = { res ->
                                    val catalogProd = if (specMode == TileSpecMode.CATALOG) selectedProduct else null
                                    if (catalogProd != null && onAddProductToCart != null) {
                                        onAddProductToCart(catalogProd, res.cartonsNeeded, res.deliveredAreaM2)
                                    } else {
                                        onAddCalculatedLineToCart?.invoke(
                                            FreeLineItem(
                                                id = "calc_${UUID.randomUUID().toString().take(8)}",
                                                designation = "Carrelage (${formatCalcNumber(res.m2PerCarton)} m²/ctn • ${res.cartonsNeeded} ctn)",
                                                quantite = res.deliveredAreaM2,
                                                unite = "m²",
                                                prixUnitaire = 0.0,
                                                sousTotal = 0.0
                                            )
                                        )
                                    }
                                },
                                canAddToCart = onAddCalculatedLineToCart != null || onAddProductToCart != null
                            )
                        }
                    }
                } else {
                    AreaInputCard(
                        requiredAreaInput = requiredAreaInput,
                        marginPercent = marginPercent,
                        onAreaChange = { requiredAreaInput = it },
                        onSelectMargin = { marginPercent = it },
                        onReset = ::resetAll,
                        onDone = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    )

                    LiveResultSection(
                        result = liveResult,
                        hasInputError = hasInputError,
                        activeProduct = if (specMode == TileSpecMode.CATALOG) selectedProduct else null,
                        specSummaryLabel = buildSpecSummary(
                            specMode = specMode,
                            lengthInput = lengthInput,
                            widthInput = widthInput,
                            unit = unit,
                            directM2PerCartonInput = directM2PerCartonInput,
                            selectedProduct = selectedProduct
                        ),
                        onAddToCart = { res ->
                            val catalogProd = if (specMode == TileSpecMode.CATALOG) selectedProduct else null
                            if (catalogProd != null && onAddProductToCart != null) {
                                onAddProductToCart(catalogProd, res.cartonsNeeded, res.deliveredAreaM2)
                            } else {
                                onAddCalculatedLineToCart?.invoke(
                                    FreeLineItem(
                                        id = "calc_${UUID.randomUUID().toString().take(8)}",
                                        designation = "Carrelage (${formatCalcNumber(res.m2PerCarton)} m²/ctn • ${res.cartonsNeeded} ctn)",
                                        quantite = res.deliveredAreaM2,
                                        unite = "m²",
                                        prixUnitaire = 0.0,
                                        sousTotal = 0.0
                                    )
                                )
                            }
                        },
                        canAddToCart = onAddCalculatedLineToCart != null || onAddProductToCart != null
                    )

                    TileSpecsCard(
                        specMode = specMode,
                        unit = unit,
                        lengthInput = lengthInput,
                        widthInput = widthInput,
                        piecesInput = piecesInput,
                        directM2PerCartonInput = directM2PerCartonInput,
                        directPiecesInput = directPiecesInput,
                        products = products,
                        selectedProduct = selectedProduct,
                        onSelectMode = { specModeName = it.name },
                        onSwitchUnit = ::switchUnit,
                        onLengthChange = { lengthInput = it },
                        onWidthChange = { widthInput = it },
                        onPiecesChange = { piecesInput = it },
                        onSelectDimensionPreset = { l, w, pcs ->
                            lengthInput = l
                            widthInput = w
                            piecesInput = pcs
                        },
                        onDirectM2Change = { directM2PerCartonInput = it },
                        onDirectPiecesChange = { directPiecesInput = it },
                        onSelectProduct = { selectedProductId = it.id }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CalculatorHeaderCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Navy950,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(14.dp),
                color = Amber500
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = stringResource(R.string.m2_calc_screen_title),
                        tint = OnDark,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.m2_calc_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = OnDark,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.m2_calc_screen_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber500,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CalculatorTopModeToggle(
    activeMode: String,
    onSelectMode: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Slate900,
        border = BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "surface" to stringResource(R.string.tile_calc_tab_surface),
                "room" to stringResource(R.string.tile_calc_tab_room)
            ).forEach { (key, label) ->
                val selected = activeMode == key
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectMode(key) }
                        .testTag("calc_top_mode_$key"),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Navy950 else Slate850,
                    border = BorderStroke(1.5.dp, if (selected) Amber500 else Slate700)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Amber500 else Slate100,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaInputCard(
    requiredAreaInput: String,
    marginPercent: Int,
    onAreaChange: (String) -> Unit,
    onSelectMargin: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.m2_calc_required_area_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = Navy950,
                    fontWeight = FontWeight.Black
                )
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.height(40.dp).testTag("calc_screen_reset_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Rose500)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Rose400, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.m2_calc_reset_btn), color = Rose400, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = requiredAreaInput,
                onValueChange = onAreaChange,
                placeholder = { Text("Ex: 25.5") },
                leadingIcon = { Icon(Icons.Default.SquareFoot, contentDescription = null, tint = Emerald400) },
                trailingIcon = if (requiredAreaInput.isNotEmpty()) {
                    {
                        IconButton(onClick = { onAreaChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                        }
                    }
                } else null,
                suffix = { Text("m²", color = Emerald400, fontWeight = FontWeight.Black) },
                textStyle = PosNumericTextStyle.copy(fontSize = 22.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                shape = RoundedCornerShape(14.dp),
                colors = calcFieldColors(),
                modifier = Modifier.fillMaxWidth().testTag("required_area_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val curArea = parseCalcDecimal(requiredAreaInput)
                QUICK_AREAS_M2.forEach { preset ->
                    FilterChip(
                        selected = curArea != null && kotlin.math.abs(curArea - preset) < 0.01,
                        onClick = { onAreaChange(preset.toString()) },
                        label = { Text("$preset m²", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Emerald500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate850,
                            labelColor = Slate100
                        ),
                        modifier = Modifier.testTag("area_preset_$preset")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Marge :",
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )
                TileCalculatorMath.MARGIN_PRESETS.forEach { pct ->
                    val selected = marginPercent == pct
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectMargin(pct) }
                            .testTag("margin_chip_$pct"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Amber500 else Slate850,
                        border = BorderStroke(1.dp, if (selected) Amber500 else Slate700)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (pct == 0) "Exact 0%" else "+$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) OnDark else Slate100,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileSpecsCard(
    specMode: TileSpecMode,
    unit: TileDimensionUnit,
    lengthInput: String,
    widthInput: String,
    piecesInput: String,
    directM2PerCartonInput: String,
    directPiecesInput: String,
    products: List<TileProduct>,
    selectedProduct: TileProduct?,
    onSelectMode: (TileSpecMode) -> Unit,
    onSwitchUnit: (TileDimensionUnit) -> Unit,
    onLengthChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onPiecesChange: (String) -> Unit,
    onSelectDimensionPreset: (String, String, String) -> Unit,
    onDirectM2Change: (String) -> Unit,
    onDirectPiecesChange: (String) -> Unit,
    onSelectProduct: (TileProduct) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.m2_calc_tile_dimensions_section),
                style = MaterialTheme.typography.titleMedium,
                color = Navy950,
                fontWeight = FontWeight.Black
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    TileSpecMode.DIMENSIONS to stringResource(R.string.m2_calc_mode_dimensions),
                    TileSpecMode.COLISAGE to stringResource(R.string.m2_calc_mode_colisage),
                    TileSpecMode.CATALOG to stringResource(R.string.m2_calc_mode_catalog)
                )
                modes.forEach { (mode, label) ->
                    val selected = mode == specMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectMode(mode) }
                            .testTag("spec_mode_${mode.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) Navy950 else Slate850,
                        border = BorderStroke(1.5.dp, if (selected) Amber500 else Slate700)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Amber500 else Slate100,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            when (specMode) {
                TileSpecMode.DIMENSIONS -> TileDimensionsSpecsEditor(
                    unit = unit,
                    lengthInput = lengthInput,
                    widthInput = widthInput,
                    piecesInput = piecesInput,
                    onSwitchUnit = onSwitchUnit,
                    onLengthChange = onLengthChange,
                    onWidthChange = onWidthChange,
                    onPiecesChange = onPiecesChange,
                    onSelectPreset = onSelectDimensionPreset
                )

                TileSpecMode.COLISAGE -> DirectColisageSpecsEditor(
                    m2PerCartonInput = directM2PerCartonInput,
                    piecesInput = directPiecesInput,
                    onM2PerCartonChange = onDirectM2Change,
                    onPiecesChange = onDirectPiecesChange
                )

                TileSpecMode.CATALOG -> CatalogTileSpecsSelector(
                    products = products,
                    selectedProduct = selectedProduct,
                    onSelectProduct = onSelectProduct
                )
            }
        }
    }
}

@Composable
private fun LiveResultSection(
    result: TileBoxCalculationResult?,
    hasInputError: Boolean,
    activeProduct: TileProduct?,
    specSummaryLabel: String,
    canAddToCart: Boolean,
    onAddToCart: (TileBoxCalculationResult) -> Unit
) {
    AnimatedVisibility(
        visible = result != null,
        enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically()
    ) {
        result?.let { res ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().testTag("calc_result_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Navy950) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.m2_calc_result_cartons_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnDark.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${res.cartonsNeeded} Carton${if (res.cartonsNeeded > 1) "s" else ""}",
                                    style = PosNumericTextStyle.copy(fontSize = 26.sp),
                                    color = Amber500,
                                    modifier = Modifier.testTag("calc_result_cartons")
                                )
                                if (specSummaryLabel.isNotBlank()) {
                                    Text(
                                        text = specSummaryLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnDark.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(shape = RoundedCornerShape(12.dp), color = Cyan500) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = stringResource(R.string.m2_calc_result_m2_per_carton_title),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnDark.copy(alpha = 0.9f)
                                    )
                                    Text(
                                        text = "${formatCalcNumber(res.m2PerCarton)} m²",
                                        style = PosNumericTextStyle.copy(fontSize = 20.sp),
                                        color = OnDark,
                                        modifier = Modifier.testTag("calc_result_m2_per_carton")
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Slate800)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResultMetricTile(
                            icon = Icons.Default.SquareFoot,
                            label = "Surface Livrée",
                            value = "${formatM2(res.deliveredAreaM2)} m²",
                            accent = Emerald400,
                            modifier = Modifier.weight(1f)
                        )
                        ResultMetricTile(
                            icon = Icons.Default.Inventory2,
                            label = if (res.marginPercent > 0) "Surplus (+${res.marginPercent}% coupe)" else "Surplus",
                            value = "+${formatM2(res.surplusAreaM2)} m²",
                            accent = Emerald300,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResultMetricTile(
                            icon = Icons.Default.GridOn,
                            label = "1 Carreau",
                            value = "${formatCalcNumber(res.tileAreaM2)} m²",
                            accent = Cyan400,
                            modifier = Modifier.weight(1f)
                        )
                        ResultMetricTile(
                            icon = Icons.Default.Layers,
                            label = "Total Pièces",
                            value = "${res.totalPieces} pcs",
                            accent = Amber400,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (activeProduct != null) {
                        ResultMetricTile(
                            icon = Icons.Default.ShoppingCart,
                            label = "Montant estimé (${formatDa(activeProduct.prix)}/m²)",
                            value = formatDa(res.deliveredAreaM2 * activeProduct.prix),
                            accent = Emerald500,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (canAddToCart && res.cartonsNeeded > 0 && res.deliveredAreaM2 > 0.0) {
                        Button(
                            onClick = { onAddToCart(res) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("calc_send_to_pos_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald500,
                                contentColor = OnDark
                            )
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.m2_calc_send_to_pos),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }

    if (result == null) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (hasInputError) Rose950 else Slate900,
            border = BorderStroke(1.dp, if (hasInputError) Rose500 else Slate800)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag(if (hasInputError) "calc_error_banner" else "calc_empty_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = if (hasInputError) Rose400 else Slate400,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        if (hasInputError) R.string.m2_calc_error_invalid_inputs
                        else R.string.m2_calc_empty_hint
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasInputError) Rose400 else Slate400,
                    fontWeight = if (hasInputError) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun buildSpecSummary(
    specMode: TileSpecMode,
    lengthInput: String,
    widthInput: String,
    unit: TileDimensionUnit,
    directM2PerCartonInput: String,
    selectedProduct: TileProduct?
): String = when (specMode) {
    TileSpecMode.DIMENSIONS -> "Format ${lengthInput.trim()}×${widthInput.trim()} ${unit.suffix}"
    TileSpecMode.COLISAGE -> "Colisage ${directM2PerCartonInput.trim()} m²/ctn"
    TileSpecMode.CATALOG -> selectedProduct?.let { "${it.name} (${it.dimension})" } ?: ""
}
