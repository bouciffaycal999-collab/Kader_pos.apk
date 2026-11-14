package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.data.model.RoomTileCalculationResult
import com.example.data.model.TileCalculatorMath
import com.example.data.model.TileDimensionUnit
import com.example.data.model.formatM2
import com.example.ui.screens.ResultMetricTile
import com.example.ui.screens.calcFieldColors
import com.example.ui.screens.formatCalcNumber
import com.example.ui.screens.parseCalcDecimal
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.Emerald300
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
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
import kotlin.math.abs

private val ROOM_PRESETS = listOf(
    "3×4 m" to ("3" to "4"),
    "4×4 m" to ("4" to "4"),
    "5×4 m" to ("5" to "4"),
    "6×5 m" to ("6" to "5"),
    "8×6 m" to ("8" to "6")
)

/**
 * Reusable 'Tile Calculator' UI component allowing users to enter room dimensions (length, width),
 * tile dimensions, and a configurable m² per carton value to compute total room area, estimated
 * tile count, and cartons needed automatically in real time.
 */
@Composable
fun TileCalculator(
    modifier: Modifier = Modifier,
    initialRoomLengthM: String = "5",
    initialRoomWidthM: String = "4",
    initialTileLength: String = "60",
    initialTileWidth: String = "60",
    initialTileUnit: TileDimensionUnit = TileDimensionUnit.CM,
    initialM2PerCarton: String = "1.44",
    initialMarginPercent: Int = 0,
    onResultChanged: ((RoomTileCalculationResult?) -> Unit)? = null,
    onAddToCart: ((RoomTileCalculationResult) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var roomLengthInput by rememberSaveable { mutableStateOf(initialRoomLengthM) }
    var roomWidthInput by rememberSaveable { mutableStateOf(initialRoomWidthM) }
    var marginPercent by rememberSaveable { mutableIntStateOf(initialMarginPercent) }

    var tileUnitName by rememberSaveable { mutableStateOf(initialTileUnit.name) }
    val tileUnit = remember(tileUnitName) { TileDimensionUnit.valueOf(tileUnitName) }
    var tileLengthInput by rememberSaveable { mutableStateOf(initialTileLength) }
    var tileWidthInput by rememberSaveable { mutableStateOf(initialTileWidth) }
    var m2PerCartonInput by rememberSaveable { mutableStateOf(initialM2PerCarton) }

    val liveResult: RoomTileCalculationResult? = remember(
        roomLengthInput,
        roomWidthInput,
        tileLengthInput,
        tileWidthInput,
        tileUnit,
        m2PerCartonInput,
        marginPercent
    ) {
        val rLen = parseCalcDecimal(roomLengthInput) ?: return@remember null
        val rWid = parseCalcDecimal(roomWidthInput) ?: return@remember null
        val tLen = parseCalcDecimal(tileLengthInput) ?: return@remember null
        val tWid = parseCalcDecimal(tileWidthInput) ?: return@remember null
        val m2Ctn = parseCalcDecimal(m2PerCartonInput) ?: return@remember null
        if (rLen <= 0.0 || rWid <= 0.0 || tLen <= 0.0 || tWid <= 0.0 || m2Ctn <= 0.0) {
            return@remember null
        }
        TileCalculatorMath.calculateRoomTilePlan(
            roomLengthM = rLen,
            roomWidthM = rWid,
            tileLength = tLen,
            tileWidth = tWid,
            tileUnit = tileUnit,
            m2PerCarton = m2Ctn,
            marginPercent = marginPercent
        )
    }

    LaunchedEffect(liveResult) {
        onResultChanged?.invoke(liveResult)
    }

    val hasAnyRoomOrTileText = roomLengthInput.isNotBlank() || roomWidthInput.isNotBlank()
    val hasInputError = hasAnyRoomOrTileText && liveResult == null

    fun switchTileUnit(target: TileDimensionUnit) {
        if (target == tileUnit) return
        val factor = if (target == TileDimensionUnit.METER) 0.01 else 100.0
        parseCalcDecimal(tileLengthInput)?.let { tileLengthInput = formatCalcNumber(it * factor) }
        parseCalcDecimal(tileWidthInput)?.let { tileWidthInput = formatCalcNumber(it * factor) }
        tileUnitName = target.name
    }

    fun resetDefaults() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        roomLengthInput = ""
        roomWidthInput = ""
        marginPercent = 0
        tileUnitName = TileDimensionUnit.CM.name
        tileLengthInput = "60"
        tileWidthInput = "60"
        m2PerCartonInput = "1.44"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tile_calc_component"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Room Dimensions Input Card
        RoomDimensionsCard(
            roomLengthInput = roomLengthInput,
            roomWidthInput = roomWidthInput,
            marginPercent = marginPercent,
            onRoomLengthChange = { roomLengthInput = it },
            onRoomWidthChange = { roomWidthInput = it },
            onSelectPreset = { l, w ->
                roomLengthInput = l
                roomWidthInput = w
            },
            onSelectMargin = { marginPercent = it },
            onReset = ::resetDefaults,
            onDone = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        )

        // 2. Tile Size & Configurable m² per Carton Card
        TileSizeAndCoverageCard(
            tileUnit = tileUnit,
            tileLengthInput = tileLengthInput,
            tileWidthInput = tileWidthInput,
            m2PerCartonInput = m2PerCartonInput,
            onSwitchUnit = ::switchTileUnit,
            onTileLengthChange = { tileLengthInput = it },
            onTileWidthChange = { tileWidthInput = it },
            onM2PerCartonChange = { m2PerCartonInput = it },
            onSelectPreset = { tLen, tWid, presetM2Ctn ->
                tileLengthInput = tLen
                tileWidthInput = tWid
                m2PerCartonInput = presetM2Ctn
            },
            onDone = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        )

        // 3. Live Total Area & Cartons Result Card
        RoomTileResultCard(
            result = liveResult,
            hasInputError = hasInputError,
            tileLengthLabel = tileLengthInput.trim(),
            tileWidthLabel = tileWidthInput.trim(),
            tileUnit = tileUnit,
            canAddToCart = onAddToCart != null,
            onAddToCart = { res -> onAddToCart?.invoke(res) }
        )
    }
}

@Composable
private fun RoomDimensionsCard(
    roomLengthInput: String,
    roomWidthInput: String,
    marginPercent: Int,
    onRoomLengthChange: (String) -> Unit,
    onRoomWidthChange: (String) -> Unit,
    onSelectPreset: (String, String) -> Unit,
    onSelectMargin: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    val fieldColors = calcFieldColors()

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SquareFoot,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tile_calc_room_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = Navy950,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .height(42.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("tile_calc_reset_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Rose500)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Rose400, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.m2_calc_reset_btn), color = Rose400, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = roomLengthInput,
                    onValueChange = onRoomLengthChange,
                    label = {
                        Text(
                            text = stringResource(R.string.tile_calc_room_length),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = { Text("5.0") },
                    leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, tint = Emerald400) },
                    trailingIcon = if (roomLengthInput.isNotEmpty()) {
                        {
                            IconButton(onClick = { onRoomLengthChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                            }
                        }
                    } else null,
                    suffix = { Text("m", color = Emerald400, fontWeight = FontWeight.Black) },
                    textStyle = PosNumericTextStyle.copy(fontSize = 19.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tile_calc_room_length_input")
                )

                OutlinedTextField(
                    value = roomWidthInput,
                    onValueChange = onRoomWidthChange,
                    label = {
                        Text(
                            text = stringResource(R.string.tile_calc_room_width),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = { Text("4.0") },
                    leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, tint = Emerald400) },
                    trailingIcon = if (roomWidthInput.isNotEmpty()) {
                        {
                            IconButton(onClick = { onRoomWidthChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                            }
                        }
                    } else null,
                    suffix = { Text("m", color = Emerald400, fontWeight = FontWeight.Black) },
                    textStyle = PosNumericTextStyle.copy(fontSize = 19.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tile_calc_room_width_input")
                )
            }

            // Quick room presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val curL = parseCalcDecimal(roomLengthInput)
                val curW = parseCalcDecimal(roomWidthInput)
                ROOM_PRESETS.forEach { (label, dims) ->
                    val pL = dims.first.toDoubleOrNull() ?: 0.0
                    val pW = dims.second.toDoubleOrNull() ?: 0.0
                    val selected = curL != null && curW != null &&
                        abs(curL - pL) < 0.01 && abs(curW - pW) < 0.01
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(dims.first, dims.second) },
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Emerald500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate850,
                            labelColor = Slate100
                        ),
                        modifier = Modifier.testTag("tile_calc_room_preset_${dims.first}x${dims.second}")
                    )
                }
            }

            // Margin / waste percentage
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
                            .testTag("tile_calc_margin_chip_$pct"),
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
private fun TileSizeAndCoverageCard(
    tileUnit: TileDimensionUnit,
    tileLengthInput: String,
    tileWidthInput: String,
    m2PerCartonInput: String,
    onSwitchUnit: (TileDimensionUnit) -> Unit,
    onTileLengthChange: (String) -> Unit,
    onTileWidthChange: (String) -> Unit,
    onM2PerCartonChange: (String) -> Unit,
    onSelectPreset: (length: String, width: String, m2PerCarton: String) -> Unit,
    onDone: () -> Unit
) {
    val fieldColors = calcFieldColors()
    val currentM2Ctn = parseCalcDecimal(m2PerCartonInput)

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tile_calc_tile_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = Navy950,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TileDimensionUnit.entries.forEach { option ->
                        val selected = option == tileUnit
                        Surface(
                            modifier = Modifier
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSwitchUnit(option) }
                                .testTag("tile_calc_unit_${option.suffix}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Cyan500 else Slate850,
                            border = BorderStroke(1.5.dp, if (selected) Cyan500 else Slate700)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.suffix.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) OnDark else Slate100,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            // Tile format presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TileCalculatorMath.TILE_FORMAT_PRESETS.forEach { preset ->
                    val targetL = if (tileUnit == TileDimensionUnit.CM) preset.lengthCm else preset.lengthCm / 100.0
                    val targetW = if (tileUnit == TileDimensionUnit.CM) preset.widthCm else preset.widthCm / 100.0
                    val presetM2PerCtn = (preset.lengthCm / 100.0) * (preset.widthCm / 100.0) * preset.piecesPerCarton
                    val curL = parseCalcDecimal(tileLengthInput)
                    val curW = parseCalcDecimal(tileWidthInput)
                    val isSelected = curL != null && curW != null &&
                        abs(curL - targetL) < 0.001 && abs(curW - targetW) < 0.001

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelectPreset(
                                formatCalcNumber(targetL),
                                formatCalcNumber(targetW),
                                formatM2(presetM2PerCtn)
                            )
                        },
                        label = {
                            Text(
                                text = "${preset.label} • ${formatM2(presetM2PerCtn)} m²",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate850,
                            labelColor = Slate100
                        ),
                        modifier = Modifier.testTag("tile_calc_preset_${preset.label}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = tileLengthInput,
                    onValueChange = onTileLengthChange,
                    label = {
                        Text(
                            text = "${stringResource(R.string.tile_calc_tile_length)} (${tileUnit.suffix})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, tint = Cyan400) },
                    trailingIcon = if (tileLengthInput.isNotEmpty()) {
                        {
                            IconButton(onClick = { onTileLengthChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                            }
                        }
                    } else null,
                    suffix = { Text(tileUnit.suffix, color = Cyan400, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tile_calc_tile_length_input")
                )

                OutlinedTextField(
                    value = tileWidthInput,
                    onValueChange = onTileWidthChange,
                    label = {
                        Text(
                            text = "${stringResource(R.string.tile_calc_tile_width)} (${tileUnit.suffix})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, tint = Cyan400) },
                    trailingIcon = if (tileWidthInput.isNotEmpty()) {
                        {
                            IconButton(onClick = { onTileWidthChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                            }
                        }
                    } else null,
                    suffix = { Text(tileUnit.suffix, color = Cyan400, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tile_calc_tile_width_input")
                )
            }

            HorizontalDivider(color = Slate800)

            // Configurable m² per carton input + quick chips
            OutlinedTextField(
                value = m2PerCartonInput,
                onValueChange = onM2PerCartonChange,
                label = {
                    Text(
                        text = stringResource(R.string.tile_calc_m2_per_carton),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null, tint = Amber400) },
                trailingIcon = if (m2PerCartonInput.isNotEmpty()) {
                    {
                        IconButton(onClick = { onM2PerCartonChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                        }
                    }
                } else null,
                suffix = { Text("m²/ctn", color = Amber400, fontWeight = FontWeight.Black) },
                textStyle = PosNumericTextStyle.copy(fontSize = 18.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tile_calc_m2_per_carton_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TileCalculatorMath.COLISAGE_PRESETS.forEach { preset ->
                    val isSelected = currentM2Ctn != null && abs(currentM2Ctn - preset) < 0.001
                    FilterChip(
                        selected = isSelected,
                        onClick = { onM2PerCartonChange(formatM2(preset)) },
                        label = {
                            Text("${formatM2(preset)} m²/ctn", fontWeight = FontWeight.Bold)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate850,
                            labelColor = Slate100
                        ),
                        modifier = Modifier.testTag("tile_calc_colisage_preset_${formatM2(preset)}")
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomTileResultCard(
    result: RoomTileCalculationResult?,
    hasInputError: Boolean,
    tileLengthLabel: String,
    tileWidthLabel: String,
    tileUnit: TileDimensionUnit,
    canAddToCart: Boolean,
    onAddToCart: (RoomTileCalculationResult) -> Unit
) {
    AnimatedVisibility(
        visible = result != null,
        enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically()
    ) {
        result?.let { res ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tile_calc_result_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Obsidian Showroom Hero Summary
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Navy950
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(Navy950, Navy800)))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.tile_calc_cartons_needed_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OnDark.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "${res.cartonsNeeded} Carton${if (res.cartonsNeeded > 1) "s" else ""}",
                                        style = PosNumericTextStyle.copy(fontSize = 26.sp),
                                        color = Amber500,
                                        modifier = Modifier.testTag("tile_calc_cartons_needed")
                                    )
                                    Text(
                                        text = "Colisage : ${formatCalcNumber(res.m2PerCarton)} m²/ctn • Format ${tileLengthLabel}×${tileWidthLabel} ${tileUnit.suffix}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnDark.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Cyan500
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = stringResource(R.string.tile_calc_total_area_title),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OnDark.copy(alpha = 0.92f)
                                        )
                                        Text(
                                            text = "${formatM2(res.totalAreaM2)} m²",
                                            style = PosNumericTextStyle.copy(fontSize = 20.sp),
                                            color = OnDark,
                                            modifier = Modifier.testTag("tile_calc_total_area")
                                        )
                                    }
                                }
                            }

                            // Proportional Room × Tile Blueprint Visual
                            RoomTileBlueprintPreview(
                                roomLengthM = res.roomLengthM,
                                roomWidthM = res.roomWidthM,
                                tileAreaM2 = res.tileAreaM2
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResultMetricTile(
                            icon = Icons.Default.Layers,
                            label = stringResource(R.string.tile_calc_tiles_needed_title),
                            value = "${res.tilesNeeded} pcs",
                            accent = Cyan400,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tile_calc_tiles_needed")
                        )
                        ResultMetricTile(
                            icon = Icons.Default.SquareFoot,
                            label = if (res.marginPercent > 0) "Cible (+${res.marginPercent}%)" else "1 Carreau",
                            value = if (res.marginPercent > 0) "${formatM2(res.targetAreaWithMarginM2)} m²" else "${formatCalcNumber(res.tileAreaM2)} m²",
                            accent = Amber400,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResultMetricTile(
                            icon = Icons.Default.Inventory2,
                            label = "Surface Livrée",
                            value = "${formatM2(res.deliveredAreaM2)} m²",
                            accent = Emerald400,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tile_calc_delivered_area")
                        )
                        ResultMetricTile(
                            icon = Icons.Default.GridOn,
                            label = "Surplus Coupe",
                            value = "+${formatM2(res.surplusAreaM2)} m²",
                            accent = Emerald300,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tile_calc_surplus_area")
                        )
                    }

                    if (canAddToCart && res.cartonsNeeded > 0 && res.deliveredAreaM2 > 0.0) {
                        Button(
                            onClick = { onAddToCart(res) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("tile_calc_send_to_pos_btn"),
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
                    .testTag(if (hasInputError) "tile_calc_error_banner" else "tile_calc_empty_banner"),
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
                    text = stringResource(R.string.tile_calc_invalid_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasInputError) Rose400 else Slate400,
                    fontWeight = if (hasInputError) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun RoomTileBlueprintPreview(
    roomLengthM: Double,
    roomWidthM: Double,
    tileAreaM2: Double
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Cyan950.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(width = 76.dp, height = 52.dp)
            ) {
                val aspect = (roomLengthM / roomWidthM.coerceAtLeast(0.1)).toFloat().coerceIn(0.45f, 2.2f)
                val maxW = size.width - 6f
                val maxH = size.height - 6f
                val rectW: Float
                val rectH: Float
                if (maxW / maxH > aspect) {
                    rectH = maxH
                    rectW = rectH * aspect
                } else {
                    rectW = maxW
                    rectH = rectW / aspect
                }
                val left = (size.width - rectW) / 2f
                val top = (size.height - rectH) / 2f

                drawRect(
                    color = Cyan300.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = Size(rectW, rectH)
                )
                val cols = 5
                val rows = 4
                for (c in 1 until cols) {
                    val x = left + rectW * (c.toFloat() / cols)
                    drawLine(
                        color = Cyan300.copy(alpha = 0.55f),
                        start = Offset(x, top),
                        end = Offset(x, top + rectH),
                        strokeWidth = 1f
                    )
                }
                for (r in 1 until rows) {
                    val y = top + rectH * (r.toFloat() / rows)
                    drawLine(
                        color = Cyan300.copy(alpha = 0.55f),
                        start = Offset(left, y),
                        end = Offset(left + rectW, y),
                        strokeWidth = 1f
                    )
                }
                drawRect(
                    color = Amber500,
                    topLeft = Offset(left, top),
                    size = Size(rectW, rectH),
                    style = Stroke(width = 2.5f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Plan : ${formatM2(roomLengthM)} m × ${formatM2(roomWidthM)} m = ${formatM2(roomLengthM * roomWidthM)} m²",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnDark,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Surface 1 carreau : ${formatCalcNumber(tileAreaM2)} m²",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan300,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
