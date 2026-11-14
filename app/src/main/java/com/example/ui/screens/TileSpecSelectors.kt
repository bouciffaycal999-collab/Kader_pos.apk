package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.TileCalculatorMath
import com.example.data.model.TileDimensionUnit
import com.example.data.model.TileProduct
import com.example.data.model.formatDa
import com.example.data.model.formatM2
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
import com.example.ui.theme.PosNumericTextStyle
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
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
import kotlin.math.abs
import kotlin.math.round

enum class TileSpecMode {
    DIMENSIONS,
    COLISAGE,
    CATALOG
}

private val preciseM2Fmt = DecimalFormat("0.####", DecimalFormatSymbols(Locale.US))

internal fun parseCalcDecimal(raw: String): Double? {
    val cleaned = raw.trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    return cleaned.toDoubleOrNull()
}

internal fun formatCalcNumber(value: Double): String {
    val clean = round(value * 10000.0) / 10000.0
    return preciseM2Fmt.format(clean)
}

@Composable
internal fun calcFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan500,
    unfocusedBorderColor = Slate700,
    focusedLabelColor = Cyan400,
    unfocusedLabelColor = Slate400,
    focusedTextColor = Slate100,
    unfocusedTextColor = Slate100,
    cursorColor = Amber500,
    focusedContainerColor = Slate950,
    unfocusedContainerColor = Slate950
)

@Composable
internal fun TileDimensionsSpecsEditor(
    unit: TileDimensionUnit,
    lengthInput: String,
    widthInput: String,
    piecesInput: String,
    onSwitchUnit: (TileDimensionUnit) -> Unit,
    onLengthChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onPiecesChange: (String) -> Unit,
    onSelectPreset: (length: String, width: String, pcs: String) -> Unit
) {
    val colors = calcFieldColors()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Formats courants :",
                style = MaterialTheme.typography.labelLarge,
                color = Slate400,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TileDimensionUnit.entries.forEach { option ->
                    val selected = option == unit
                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSwitchUnit(option) }
                            .testTag("unit_toggle_${option.suffix}"),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TileCalculatorMath.TILE_FORMAT_PRESETS.forEach { preset ->
                val targetL = if (unit == TileDimensionUnit.CM) preset.lengthCm else preset.lengthCm / 100.0
                val targetW = if (unit == TileDimensionUnit.CM) preset.widthCm else preset.widthCm / 100.0
                val curL = parseCalcDecimal(lengthInput)
                val curW = parseCalcDecimal(widthInput)
                val curPcs = piecesInput.trim().toIntOrNull()
                val isSelected = curL != null && curW != null && curPcs == preset.piecesPerCarton &&
                    abs(curL - targetL) < 0.001 && abs(curW - targetW) < 0.001

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSelectPreset(
                            formatCalcNumber(targetL),
                            formatCalcNumber(targetW),
                            preset.piecesPerCarton.toString()
                        )
                    },
                    label = {
                        Text(
                            text = "${preset.label} • ${preset.piecesPerCarton} pcs",
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
                    modifier = Modifier.testTag("preset_${preset.label}")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = lengthInput,
                onValueChange = onLengthChange,
                label = {
                    Text(
                        text = "${stringResource(R.string.m2_calc_length_label)} (${unit.suffix})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, tint = Cyan400) },
                trailingIcon = if (lengthInput.isNotEmpty()) {
                    {
                        IconButton(onClick = { onLengthChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                        }
                    }
                } else null,
                suffix = { Text(unit.suffix, color = Cyan400, fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                colors = colors,
                modifier = Modifier.weight(1f).testTag("tile_length_input")
            )

            OutlinedTextField(
                value = widthInput,
                onValueChange = onWidthChange,
                label = {
                    Text(
                        text = "${stringResource(R.string.m2_calc_width_label)} (${unit.suffix})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, tint = Cyan400) },
                trailingIcon = if (widthInput.isNotEmpty()) {
                    {
                        IconButton(onClick = { onWidthChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                        }
                    }
                } else null,
                suffix = { Text(unit.suffix, color = Cyan400, fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                colors = colors,
                modifier = Modifier.weight(1f).testTag("tile_width_input")
            )
        }

        PiecesPerCartonInputField(
            value = piecesInput,
            onValueChange = { onPiecesChange(it.filter(Char::isDigit)) },
            colors = colors
        )
    }
}

@Composable
internal fun DirectColisageSpecsEditor(
    m2PerCartonInput: String,
    piecesInput: String,
    onM2PerCartonChange: (String) -> Unit,
    onPiecesChange: (String) -> Unit
) {
    val colors = calcFieldColors()
    val currentM2 = parseCalcDecimal(m2PerCartonInput)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TileCalculatorMath.COLISAGE_PRESETS.forEach { preset ->
                val isSelected = currentM2 != null && abs(currentM2 - preset) < 0.001
                FilterChip(
                    selected = isSelected,
                    onClick = { onM2PerCartonChange(formatM2(preset)) },
                    label = {
                        Text(
                            text = "${formatM2(preset)} m²/ctn",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Amber500,
                        selectedLabelColor = OnDark,
                        containerColor = Slate850,
                        labelColor = Slate100
                    ),
                    modifier = Modifier.testTag("colisage_preset_${formatM2(preset)}")
                )
            }
        }

        OutlinedTextField(
            value = m2PerCartonInput,
            onValueChange = onM2PerCartonChange,
            label = {
                Text(
                    text = stringResource(R.string.m2_calc_colisage_label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Default.SquareFoot, contentDescription = null, tint = Cyan400) },
            trailingIcon = if (m2PerCartonInput.isNotEmpty()) {
                {
                    IconButton(onClick = { onM2PerCartonChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                    }
                }
            } else null,
            suffix = { Text("m²/ctn", color = Cyan400, fontWeight = FontWeight.Bold) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            colors = colors,
            modifier = Modifier.fillMaxWidth().testTag("direct_m2_per_carton_input")
        )

        PiecesPerCartonInputField(
            value = piecesInput,
            onValueChange = { onPiecesChange(it.filter(Char::isDigit)) },
            colors = colors
        )
    }
}

@Composable
internal fun CatalogTileSpecsSelector(
    products: List<TileProduct>,
    selectedProduct: TileProduct?,
    onSelectProduct: (TileProduct) -> Unit
) {
    var showPickerDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selectedProduct != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Cyan950,
                border = BorderStroke(1.5.dp, Cyan500)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedProduct.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Cyan300,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${selectedProduct.usine} • ${selectedProduct.dimension} • ${formatM2(selectedProduct.m2Carton)} m²/ctn • ${selectedProduct.pcs} pcs/ctn",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cyan400,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Prix: ${formatDa(selectedProduct.prix)}/m² • Stock: ${selectedProduct.stock} ctn",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedProduct.stock > 0) Emerald400 else Rose400,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { showPickerDialog = true },
            enabled = products.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("open_catalog_tile_picker_btn"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, Cyan500)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedProduct == null) "Choisir un carreau du stock" else "Changer d'article",
                color = Cyan400,
                fontWeight = FontWeight.Black
            )
        }
    }

    if (showPickerDialog) {
        CatalogTilePickerDialog(
            products = products,
            onDismiss = { showPickerDialog = false },
            onSelect = { prod ->
                onSelectProduct(prod)
                showPickerDialog = false
            }
        )
    }
}

@Composable
private fun CatalogTilePickerDialog(
    products: List<TileProduct>,
    onDismiss: () -> Unit,
    onSelect: (TileProduct) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) products.take(40)
        else products.filter {
            it.name.lowercase().contains(q) ||
                it.usine.lowercase().contains(q) ||
                it.dimension.lowercase().contains(q)
        }.take(40)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 580.dp),
            shape = RoundedCornerShape(20.dp),
            color = Slate950,
            border = BorderStroke(1.5.dp, Navy950)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sélectionner un Carreau",
                        style = MaterialTheme.typography.titleMedium,
                        color = Navy950,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Fermer", tint = Rose500)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Nom, usine ou dimension...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400) },
                    singleLine = true,
                    colors = calcFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("catalog_tile_search_input")
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { prod ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(prod) }
                                .testTag("catalog_tile_item_${prod.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = Slate900,
                            border = BorderStroke(1.dp, Slate800)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate100,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${prod.dimension} • ${formatM2(prod.m2Carton)} m²/ctn • ${prod.pcs} pcs",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Cyan400,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatDa(prod.prix),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Amber400,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "${prod.stock} ctn",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (prod.stock > 0) Emerald400 else Rose400,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PiecesPerCartonInputField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: TextFieldColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = stringResource(R.string.m2_calc_pieces_label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, tint = Amber400) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = Rose500)
                }
            }
        } else null,
        suffix = { Text("pcs", color = Amber400, fontWeight = FontWeight.Bold) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(14.dp),
        colors = colors,
        modifier = Modifier.fillMaxWidth().testTag("pieces_per_carton_input")
    )
}

@Composable
internal fun ResultMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        color = Slate950,
        border = BorderStroke(1.dp, Slate800)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = PosNumericTextStyle.copy(fontSize = 17.sp),
                color = Slate100,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
