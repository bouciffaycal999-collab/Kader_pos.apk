package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.model.AppSettings
import com.example.data.model.CaisseMovement
import com.example.data.model.DailyCaisseCount
import com.example.data.model.DepotNotification
import com.example.data.model.EffectivePermissions
import com.example.data.model.PosSeeds
import com.example.data.model.RolePolicy
import com.example.data.model.StaffUser
import com.example.data.model.StoreInfo
import com.example.data.model.WaRecipient
import com.example.data.model.formatDa
import com.example.util.SaleSystemNotifier
import com.example.data.model.formatM2
import com.example.data.model.toDzWhatsAppDigits
import com.example.ui.components.ClearableOutlinedTextField
import com.example.ui.components.InvoicePdfGenerator
import com.example.ui.components.NonImeNumericKeypad
import com.example.ui.components.NumericDisplayField
import com.example.ui.components.applyKeypadInput
import com.example.ui.components.applyKeypadQuickDelta
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
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun CaisseScreen(
    movements: List<CaisseMovement>,
    dailyCounts: List<DailyCaisseCount>,
    activeStore: String,
    onAddMovement: (type: String, motif: String, montant: Double) -> Unit,
    onSaveDailyCount: (Double, Double, Double, Double, Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    var showMoveType by remember { mutableStateOf<String?>(null) }
    var showPointageDialog by remember { mutableStateOf(false) }

    val solde = remember(movements) { movements.sumOf { if (it.type == "entree") it.montant else -it.montant } }
    val todayPrefix = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayMoves = remember(movements) { movements.filter { it.date.startsWith(todayPrefix) } }

    Column(modifier = Modifier.fillMaxSize().background(Slate950)) {
        Surface(color = Navy950) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Caisse & Trésorerie", style = MaterialTheme.typography.titleLarge, color = OnDark, fontWeight = FontWeight.Black)
                        Text("Solde Actuel : ${formatDa(solde)}", style = PosNumericTextStyle.copy(fontSize = 18.sp), color = Emerald500)
                    }
                    Button(
                        onClick = { showPointageDialog = true },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = OnDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pointage", fontWeight = FontWeight.Black)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showMoveType = "entree" },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Entrée", fontWeight = FontWeight.Black)
                    }
                    Button(
                        onClick = { showMoveType = "sortie" },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dépense / Sortie", fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dailyCounts.isNotEmpty()) {
                item {
                    val last = dailyCounts.first()
                    Surface(shape = RoundedCornerShape(14.dp), color = Slate900, border = BorderStroke(1.5.dp, Amber500)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dernier Pointage (${last.date.take(10)})", style = MaterialTheme.typography.labelMedium, color = Amber400, fontWeight = FontWeight.Black)
                                Text("Compté: ${formatDa(last.compteReel)} • Théorique: ${formatDa(last.theorique)}", style = MaterialTheme.typography.bodySmall, color = Slate100)
                            }
                            Text(
                                text = "Écart: ${formatDa(last.ecart)}",
                                color = if (kotlin.math.abs(last.ecart) < 1.0) Emerald400 else Rose400,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
            items(movements, key = { it.id }) { m ->
                Surface(shape = RoundedCornerShape(14.dp), color = Slate900, border = BorderStroke(1.dp, Slate800)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.motif, style = MaterialTheme.typography.titleSmall, color = Slate100, fontWeight = FontWeight.Bold)
                            Text("${m.date.take(16).replace('T', ' ')} • ${m.auteur}", style = MaterialTheme.typography.labelSmall, color = Slate400)
                        }
                        Text(
                            text = (if (m.type == "entree") "+" else "-") + formatDa(m.montant),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (m.type == "entree") Emerald400 else Rose400,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    showMoveType?.let { type ->
        BackHandler { showMoveType = null }
        val defaultMotif = if (type == "entree") "Apport Caisse" else "Dépense magasin"
        var amtStr by remember { mutableStateOf("0") }
        var motif by remember { mutableStateOf(defaultMotif) }
        var showKeypad by remember { mutableStateOf(true) }
        var isPristine by remember { mutableStateOf(true) }
        Dialog(onDismissRequest = { showMoveType = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(14.dp),
                shape = RoundedCornerShape(22.dp),
                color = Slate900,
                border = BorderStroke(2.dp, Navy950)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Navy950) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (type == "entree") "Nouvelle Entrée" else "Nouvelle Dépense",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (type == "entree") Emerald500 else Amber500,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { showMoveType = null },
                                modifier = Modifier.height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark)
                            ) { Text("FERMER", fontWeight = FontWeight.Black) }
                        }
                    }
                    NumericDisplayField(
                        label = "Montant (DA)",
                        value = amtStr,
                        isActive = showKeypad,
                        onSelect = { isPristine = true; showKeypad = true },
                        onClear = { amtStr = ""; isPristine = false },
                        suffix = "DA",
                        accentColor = if (type == "entree") Emerald500 else Rose500
                    )
                    ClearableOutlinedTextField(
                        value = motif,
                        onValueChange = { motif = it },
                        label = { Text("Motif") },
                        autoClearValues = setOf("Apport Caisse", "Dépense magasin"),
                        onDismissKeypad = { showKeypad = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(visible = showKeypad, enter = expandVertically(), exit = shrinkVertically()) {
                        NonImeNumericKeypad(
                            onKeyPress = { k ->
                                val (n, p) = applyKeypadInput(amtStr, k, false, isPristine)
                                amtStr = n; isPristine = p
                            },
                            quickChips = listOf("+500" to 500.0, "+2000" to 2000.0, "+5000" to 5000.0),
                            onQuickDelta = { d -> amtStr = applyKeypadQuickDelta(amtStr, d, false); isPristine = false },
                            allowDecimal = false,
                            onValidate = {
                                val v = amtStr.toDoubleOrNull() ?: 0.0
                                val finalMotif = motif.trim().ifBlank { defaultMotif }
                                if (v > 0) {
                                    onAddMovement(type, finalMotif, v)
                                    showMoveType = null
                                }
                            },
                            accentColor = if (type == "entree") Emerald500 else Rose500
                        )
                    }
                }
            }
        }
    }

    if (showPointageDialog) {
        BackHandler { showPointageDialog = false }
        val ventesJour = todayMoves.filter { it.type == "entree" && it.referenceFacture != null }.sumOf { it.montant }
        val entreesDiv = todayMoves.filter { it.type == "entree" && it.referenceFacture == null }.sumOf { it.montant }
        val sortiesJour = todayMoves.filter { it.type == "sortie" }.sumOf { it.montant }
        var compteStr by remember { mutableStateOf(solde.coerceAtLeast(0.0).toInt().toString()) }
        var notes by remember { mutableStateOf("") }
        var showKeypad by remember { mutableStateOf(true) }
        var isPristine by remember { mutableStateOf(true) }
        val compteReel = compteStr.toDoubleOrNull() ?: 0.0
        val ecart = compteReel - solde

        Dialog(onDismissRequest = { showPointageDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(14.dp),
                shape = RoundedCornerShape(22.dp),
                color = Slate900,
                border = BorderStroke(2.dp, Navy950)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Navy950) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pointage ($activeStore)", style = MaterialTheme.typography.titleMedium, color = OnDark, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { showPointageDialog = false },
                                modifier = Modifier.height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark)
                            ) { Text("FERMER", fontWeight = FontWeight.Black) }
                        }
                    }
                    Text("Solde Théorique : ${formatDa(solde)}", style = MaterialTheme.typography.titleSmall, color = Cyan400, fontWeight = FontWeight.Black)
                    NumericDisplayField(
                        label = "Montant Compté Réel",
                        value = compteStr,
                        isActive = showKeypad,
                        onSelect = { isPristine = true; showKeypad = true },
                        onClear = { compteStr = ""; isPristine = false },
                        suffix = "DA",
                        accentColor = Emerald500
                    )
                    Text("Écart : ${formatDa(ecart)}", style = MaterialTheme.typography.titleSmall, color = if (kotlin.math.abs(ecart) < 1.0) Emerald400 else Rose400, fontWeight = FontWeight.Black)
                    ClearableOutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes clôture") },
                        onDismissKeypad = { showKeypad = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(visible = showKeypad, enter = expandVertically(), exit = shrinkVertically()) {
                        NonImeNumericKeypad(
                            onKeyPress = { k -> val (n, p) = applyKeypadInput(compteStr, k, false, isPristine); compteStr = n; isPristine = p },
                            allowDecimal = false,
                            onValidate = {
                                onSaveDailyCount(50000.0, ventesJour, entreesDiv, sortiesJour, solde, compteReel, notes)
                                showPointageDialog = false
                            },
                            validateLabel = "Clôturer Caisse",
                            accentColor = Emerald500
                        )
                    }
                    Button(
                        onClick = {
                            val msg = "💰 *CLÔTURE CAISSE $activeStore — CERAMICA*\nThéorique: ${formatDa(solde)}\nCompté: ${formatDa(compteReel)}\nÉcart: ${formatDa(ecart)}"
                            openWhatsApp(context, PosSeeds.WHATSAPP_MANAGEMENT, msg)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Envoyer Bilan Direction (WA)", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun UsersScreen(
    users: List<StaffUser>,
    currentUser: StaffUser?,
    rolePolicies: List<RolePolicy> = PosSeeds.DEFAULT_ROLE_POLICIES,
    effectivePermissions: EffectivePermissions = PosSeeds.resolveEffectivePermissions(currentUser, rolePolicies),
    onSaveUser: (StaffUser) -> Unit,
    onDeleteUser: (String) -> Unit,
    onSaveRolePolicy: (RolePolicy) -> Unit = {}
) {
    var editingUser by remember { mutableStateOf<StaffUser?>(null) }
    var editingPolicy by remember { mutableStateOf<RolePolicy?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    val orderedPolicies = remember(rolePolicies) {
        val map = rolePolicies.associateBy { it.roleId }
        PosSeeds.VALID_ROLES.map { rid -> map[rid] ?: PosSeeds.defaultPolicyForRole(rid) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Slate950)) {
        Surface(color = Navy950) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Gestion Équipe & Rôles",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnDark,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "${users.size} comptes • ${orderedPolicies.size} rôles Firestore (Admin / Manager / Seller)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber500,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (effectivePermissions.canManageUsers) {
                    Button(
                        onClick = { showCreate = true },
                        modifier = Modifier.height(48.dp).testTag("users_add_profile_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Profil", fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Règles de Rôles Cloud (Firestore role_policies)",
                    style = MaterialTheme.typography.titleSmall,
                    color = Amber400,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            items(orderedPolicies, key = { "policy_${it.roleId}" }) { policy ->
                val badgeColor = when (policy.roleId) {
                    "admin" -> Amber400
                    "gerant" -> Cyan400
                    else -> Slate400
                }
                ElevatedCard(
                    modifier = Modifier.testTag("role_policy_card_${policy.roleId}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = policy.displayName.ifBlank { policy.roleId.uppercase() },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Black
                                )
                                if (policy.description.isNotBlank()) {
                                    Text(
                                        text = policy.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                }
                            }
                            if (policy.roleId != "admin" && effectivePermissions.canManageUsers) {
                                OutlinedButton(
                                    onClick = { editingPolicy = policy },
                                    modifier = Modifier.height(40.dp).testTag("edit_policy_btn_${policy.roleId}"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.5.dp, Cyan500)
                                ) { Text("Règles", color = Cyan400, fontWeight = FontWeight.Black) }
                            } else if (policy.roleId == "admin") {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Navy950,
                                    border = BorderStroke(1.dp, Amber500.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "Complet",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber400,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Prix Achat: ${if (policy.canViewPrixAchat) "Visible" else "Masqué"} • Modif Tarifs: ${if (policy.canModifyPrices) "Autorisée" else "Verrouillée"} • Stock: ${if (policy.canModifyStock) "Modifiable" else "Lecture seule"} • Caisse: ${if (policy.canViewCaisse) "Oui" else "Non"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate100,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Comptes Utilisateurs (${users.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Cyan400,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            items(users, key = { it.id }) { u ->
                val resolved = PosSeeds.resolveEffectivePermissions(u, orderedPolicies)
                val roleLabel = when (u.role) {
                    "admin" -> "Admin"
                    "gerant" -> "Manager (Owner & Gérant)"
                    else -> "Seller (Vendeur)"
                }
                ElevatedCard(
                    modifier = Modifier.testTag("staff_card_${u.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(u.name, style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Black)
                            Text(
                                text = "$roleLabel • ${u.magasin} • PIN: ${u.pin}" + if (u.phone.isNotBlank()) " • Tél: ${u.phone}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Cyan400,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Stock: ${if (resolved.canModifyStock) "Modif" else "Lecture"} • Prix Achat: ${if (resolved.canViewPrixAchat) "Visible" else "Masqué"} • Tarifs: ${if (resolved.canModifyPrices) "Libre" else "Verrouillés"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                        }
                        if (effectivePermissions.canManageUsers) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { editingUser = u },
                                    modifier = Modifier.height(44.dp).testTag("edit_user_btn_${u.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.5.dp, Amber500)
                                ) { Text("Éditer", color = Amber400, fontWeight = FontWeight.Black) }
                                if (u.id != currentUser?.id && u.id != "u_kader") {
                                    IconButton(onClick = { onDeleteUser(u.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Rose500)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingPolicy?.let { pol ->
        BackHandler { editingPolicy = null }
        var pViewCost by remember(pol) { mutableStateOf(pol.canViewPrixAchat) }
        var pModifyPrices by remember(pol) { mutableStateOf(pol.canModifyPrices) }
        var pModifyStock by remember(pol) { mutableStateOf(pol.canModifyStock) }
        var pEditInvoices by remember(pol) { mutableStateOf(pol.canEditInvoices) }
        var pDeleteInvoices by remember(pol) { mutableStateOf(pol.canDeleteInvoices) }
        var pViewCaisse by remember(pol) { mutableStateOf(pol.canViewCaisse) }
        var pViewSuppliers by remember(pol) { mutableStateOf(pol.canViewSuppliers) }

        Dialog(onDismissRequest = { editingPolicy = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(14.dp),
                shape = RoundedCornerShape(22.dp),
                color = Slate900,
                border = BorderStroke(2.dp, Navy950)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Navy950) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Règles : ${pol.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Amber500,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { editingPolicy = null },
                                modifier = Modifier.height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark)
                            ) { Text("FERMER", fontWeight = FontWeight.Black) }
                        }
                    }

                    Surface(shape = RoundedCornerShape(12.dp), color = Slate950, border = BorderStroke(1.dp, Slate800)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Voir Prix d'Achat & Marges", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = pViewCost, onCheckedChange = { pViewCost = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Modifier Prix / m² & Remises", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = pModifyPrices, onCheckedChange = { pModifyPrices = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Modifier Stock & Catalogue", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = pModifyStock, onCheckedChange = { pModifyStock = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Modifier Factures (alerte)", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = pEditInvoices, onCheckedChange = { pEditInvoices = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Supprimer Factures", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = pDeleteInvoices, onCheckedChange = { pDeleteInvoices = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Accès Caisse & Stats", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = pViewCaisse, onCheckedChange = { pViewCaisse = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Accès Usines & Fournisseurs", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = pViewSuppliers, onCheckedChange = { pViewSuppliers = it })
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val nextTabs = buildList {
                                addAll(listOf("pos", "stock", "invoices", "customers", "calculator"))
                                if (pViewCaisse || pViewCost) add("dashboard")
                                if (pViewSuppliers) add("suppliers")
                                if (pViewCaisse) add("caisse")
                                if (pModifyStock || pol.roleId == "gerant") add("settings")
                            }.distinct()
                            onSaveRolePolicy(
                                pol.copy(
                                    canViewPrixAchat = pViewCost,
                                    canModifyPrices = pModifyPrices,
                                    canModifyStock = pModifyStock,
                                    canEditInvoices = pEditInvoices,
                                    canDeleteInvoices = pDeleteInvoices,
                                    canViewSuppliers = pViewSuppliers,
                                    canViewCaisse = pViewCaisse,
                                    allowedTabs = nextTabs
                                )
                            )
                            editingPolicy = null
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_role_policy_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                    ) { Text("Enregistrer Règles Firestore", fontWeight = FontWeight.Black) }
                }
            }
        }
    }

    if (showCreate || editingUser != null) {
        BackHandler { showCreate = false; editingUser = null }
        val init = editingUser
        var name by remember(init) { mutableStateOf(init?.name ?: "") }
        var phone by remember(init) { mutableStateOf(init?.phone ?: "") }
        var pinStr by remember(init) { mutableStateOf(init?.pin ?: "0000") }
        var role by remember(init) { mutableStateOf(init?.role ?: "vendeur") }
        var magasin by remember(init) { mutableStateOf(init?.magasin ?: "Depot Remchi") }
        val defaultPolicy = orderedPolicies.find { it.roleId == role } ?: PosSeeds.defaultPolicyForRole(role)
        var canViewPrixAchat by remember(init) { mutableStateOf(init?.canViewPrixAchat ?: defaultPolicy.canViewPrixAchat) }
        var canModifyPrices by remember(init) { mutableStateOf(init?.canModifyPrices ?: defaultPolicy.canModifyPrices) }
        var canEditInvoices by remember(init) { mutableStateOf(init?.canEditInvoices ?: defaultPolicy.canEditInvoices) }
        var canModifyStock by remember(init) { mutableStateOf(init?.canModifyStock ?: defaultPolicy.canModifyStock) }
        var canViewCaisse by remember(init) { mutableStateOf(init?.canViewCaisse ?: defaultPolicy.canViewCaisse) }
        var canDeleteInvoices by remember(init) { mutableStateOf(init?.canDeleteInvoices ?: defaultPolicy.canDeleteInvoices) }
        var showKeypad by remember { mutableStateOf(false) }
        var isPristine by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { showCreate = false; editingUser = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(14.dp),
                shape = RoundedCornerShape(22.dp),
                color = Slate900,
                border = BorderStroke(2.dp, Navy950)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Navy950) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (init == null) "Nouveau Profil Staff" else "Modifier ${init.name}", style = MaterialTheme.typography.titleLarge, color = Amber500, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { showCreate = false; editingUser = null },
                                modifier = Modifier.height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = OnDark)
                            ) { Text("FERMER", fontWeight = FontWeight.Black) }
                        }
                    }

                    ClearableOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom") },
                        onDismissKeypad = { showKeypad = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ClearableOutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Numéro WhatsApp / Tél (ex: 0676...)") },
                        onDismissKeypad = { showKeypad = false },
                        modifier = Modifier.fillMaxWidth().testTag("user_phone_input")
                    )
                    NumericDisplayField(
                        label = "Code PIN (4 chiffres)",
                        value = pinStr,
                        isActive = showKeypad,
                        onSelect = { isPristine = true; showKeypad = true },
                        onClear = { pinStr = ""; isPristine = false },
                        accentColor = Amber500
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("admin" to "Admin", "gerant" to "Manager (Gérant)", "vendeur" to "Seller (Vendeur)").forEach { (r, label) ->
                            FilterChip(
                                selected = role == r,
                                onClick = {
                                    role = r
                                    showKeypad = false
                                    val pol = orderedPolicies.find { it.roleId == r } ?: PosSeeds.defaultPolicyForRole(r)
                                    canViewPrixAchat = pol.canViewPrixAchat
                                    canModifyPrices = pol.canModifyPrices
                                    canEditInvoices = pol.canEditInvoices
                                    canModifyStock = pol.canModifyStock
                                    canViewCaisse = pol.canViewCaisse
                                    canDeleteInvoices = pol.canDeleteInvoices
                                },
                                label = { Text(label, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Navy950, selectedLabelColor = OnDark)
                            )
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PosSeeds.SITES.forEach { s ->
                            FilterChip(
                                selected = magasin == s,
                                onClick = { magasin = s; showKeypad = false },
                                label = { Text(s.replace("Magasin ", "Mag "), fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan500, selectedLabelColor = OnDark)
                            )
                        }
                    }

                    Surface(shape = RoundedCornerShape(12.dp), color = Slate950, border = BorderStroke(1.dp, Slate800)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Peut voir prix d'achat & marges", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(
                                    checked = canViewPrixAchat,
                                    onCheckedChange = { canViewPrixAchat = it },
                                    enabled = role != "admin"
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Peut modifier prix vente & remises", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(
                                    checked = canModifyPrices,
                                    onCheckedChange = { canModifyPrices = it },
                                    enabled = role != "admin"
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Peut modifier stock & articles", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(
                                    checked = canModifyStock,
                                    onCheckedChange = { canModifyStock = it },
                                    enabled = role != "admin"
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Peut modifier factures (alerte direction)", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = canEditInvoices, onCheckedChange = { canEditInvoices = it }, enabled = role != "admin")
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Accès Caisse & Fournisseurs", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = canViewCaisse, onCheckedChange = { canViewCaisse = it }, enabled = role != "admin")
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Peut supprimer factures", style = MaterialTheme.typography.bodySmall, color = Slate100)
                                Switch(checked = canDeleteInvoices, onCheckedChange = { canDeleteInvoices = it }, enabled = role != "admin")
                            }
                            Text(
                                text = "Les permissions effectives sont l'intersection entre ce profil et la politique Firestore du rôle.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber400,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    AnimatedVisibility(visible = showKeypad, enter = expandVertically(), exit = shrinkVertically()) {
                        NonImeNumericKeypad(
                            onKeyPress = { k -> val (n, p) = applyKeypadInput(pinStr, k, false, isPristine); pinStr = n.take(4); isPristine = p },
                            allowDecimal = false,
                            onValidate = { showKeypad = false },
                            validateLabel = "OK"
                        )
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank() && pinStr.length == 4) {
                                val isAdmin = role == "admin"
                                val tabs = mutableListOf("pos", "stock", "invoices", "customers", "calculator")
                                if (canViewCaisse || role == "gerant" || isAdmin) {
                                    tabs.addAll(listOf("dashboard", "suppliers", "caisse", "settings"))
                                }
                                if (isAdmin) tabs.add("users")
                                onSaveUser(
                                    StaffUser(
                                        id = init?.id ?: "u_${UUID.randomUUID().toString().take(6)}",
                                        name = name.trim(),
                                        role = role,
                                        pin = pinStr,
                                        magasin = magasin,
                                        allowedTabs = tabs.distinct(),
                                        canEditInvoices = if (isAdmin) true else canEditInvoices,
                                        canDeleteInvoices = if (isAdmin) true else canDeleteInvoices,
                                        canModifyStock = if (isAdmin) true else canModifyStock,
                                        canViewSuppliers = if (isAdmin) true else canViewCaisse,
                                        canViewCaisse = if (isAdmin) true else canViewCaisse,
                                        canManageUsers = isAdmin,
                                        active = true,
                                        phone = phone.trim(),
                                        canViewPrixAchat = if (isAdmin) true else canViewPrixAchat,
                                        canModifyPrices = if (isAdmin) true else canModifyPrices
                                    )
                                )
                                showCreate = false; editingUser = null
                            }
                        },
                        enabled = name.isNotBlank() && pinStr.length == 4,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_user_profile_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                    ) { Text("Enregistrer Profil", fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    storeInfo: StoreInfo,
    settings: AppSettings,
    autoWhatsappPhoto: Boolean = true,
    isAdmin: Boolean = true,
    onChangeAdminPin: (String) -> Boolean = { true },
    onSaveStoreInfo: (StoreInfo) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onSetAutoWhatsappPhoto: (Boolean) -> Unit = {},
    onResetCatalog: () -> Unit,
    onExportJson: ((String) -> Unit) -> Unit,
    onImportJson: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var nom by remember(storeInfo) { mutableStateOf(storeInfo.nom) }
    var adresse by remember(storeInfo) { mutableStateOf(storeInfo.adresse) }
    var tel by remember(storeInfo) { mutableStateOf(storeInfo.tel.ifBlank { "0676754124" }) }
    var tel2 by remember(storeInfo) { mutableStateOf(storeInfo.tel2.ifBlank { "0676754124" }) }
    var gerantWaPhone by remember(storeInfo) { mutableStateOf(storeInfo.gerantWaPhone) }
    var deviceSellerName by remember(settings) { mutableStateOf(settings.deviceSellerName) }
    var deviceSellerPhone by remember(settings) { mutableStateOf(settings.deviceSellerPhone) }
    var invoiceRecipients by remember(storeInfo) { mutableStateOf(storeInfo.invoiceWaRecipients) }
    var dispoRecipients by remember(storeInfo) { mutableStateOf(storeInfo.dispoWaRecipients) }
    var newAdminPin by remember { mutableStateOf("") }
    var confirmAdminPin by remember { mutableStateOf("") }
    var pinFeedback by remember { mutableStateOf<String?>(null) }
    var logoVersion by remember { mutableIntStateOf(0) }
    val logoBitmap = remember(logoVersion) { InvoicePdfGenerator.loadStoreLogoBitmap(context) }

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var isSharingApk by remember { mutableStateOf(false) }
    var shareApkError by remember { mutableStateOf<String?>(null) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && InvoicePdfGenerator.saveStoreLogoFromUri(context, uri)) {
            logoVersion++
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
        pendingExportJson = null
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!text.isNullOrBlank()) onImportJson(text)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Slate950).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = Navy950) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Paramètres CERAMICA", style = MaterialTheme.typography.titleLarge, color = OnDark, fontWeight = FontWeight.Black)
                Text("Code PIN Admin, numéros WhatsApp (Facture & Dépôt) et impression", style = MaterialTheme.typography.bodySmall, color = Amber400, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isAdmin) {
                // Code PIN Administrateur (Modifiable à tout moment)
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Amber400, modifier = Modifier.size(20.dp))
                            Text("Code PIN Administrateur (Défaut : 0408)", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Black)
                        }
                        Text(
                            "Le Vendeur accède sans code PIN. L'Administrateur utilise ce code PIN pour voir les prix d'achat, fournisseurs, caisse et réglages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                        ClearableOutlinedTextField(
                            value = newAdminPin,
                            onValueChange = { raw -> newAdminPin = raw.filter { it.isDigit() }.take(4) },
                            label = { Text("Nouveau PIN (4 chiffres)") },
                            modifier = Modifier.fillMaxWidth().testTag("settings_new_pin_input")
                        )
                        ClearableOutlinedTextField(
                            value = confirmAdminPin,
                            onValueChange = { raw -> confirmAdminPin = raw.filter { it.isDigit() }.take(4) },
                            label = { Text("Confirmer le nouveau PIN") },
                            modifier = Modifier.fillMaxWidth().testTag("settings_confirm_pin_input")
                        )
                        if (pinFeedback != null) {
                            Text(
                                text = pinFeedback ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pinFeedback?.contains("mis à jour", ignoreCase = true) == true) Emerald400 else Rose400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                when {
                                    newAdminPin.length != 4 -> pinFeedback = "Entrez 4 chiffres pour le nouveau code PIN."
                                    newAdminPin != confirmAdminPin -> pinFeedback = "Les deux codes PIN ne correspondent pas."
                                    else -> {
                                        val ok = onChangeAdminPin(newAdminPin)
                                        if (ok) {
                                            pinFeedback = "Code PIN mis à jour avec succès ($newAdminPin)."
                                            newAdminPin = ""
                                            confirmAdminPin = ""
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_save_pin_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = OnDark)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Changer le Code PIN Admin", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // WhatsApp Factures : Principal (0676754124) + 2ème numéro optionnel Gérant
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("WhatsApp Factures (Photo PNG + PDF)", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Black)
                    Text(
                        "Envoi immédiat après vente vers le numéro principal (0676754124) + un deuxième numéro Gérant optionnel si souhaité.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                    ClearableOutlinedTextField(
                        value = tel,
                        onValueChange = { tel = it },
                        label = { Text("WhatsApp Admin Principal (0676754124)") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_wa_principal_input")
                    )
                    ClearableOutlinedTextField(
                        value = gerantWaPhone,
                        onValueChange = { gerantWaPhone = it },
                        label = { Text("2ème Numéro WhatsApp Gérant (Optionnel)") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_wa_gerant_input")
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Envoyer Photo + PDF WhatsApp auto après vente", color = Slate100, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Ouvre WhatsApp automatiquement avec l'image et le PDF vers 0676754124 après validation", color = Slate400, style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(
                            checked = autoWhatsappPhoto,
                            onCheckedChange = onSetAutoWhatsappPhoto,
                            modifier = Modifier.testTag("settings_auto_wa_photo_switch")
                        )
                    }
                    Button(
                        onClick = {
                            val cleanMain = tel.trim().ifBlank { "0676754124" }
                            val cleanGerant = gerantWaPhone.trim()
                            val nextInvoiceList = buildList {
                                add(WaRecipient("Principal / Admin ($cleanMain)", cleanMain))
                                if (cleanGerant.isNotBlank() && cleanGerant != cleanMain) {
                                    add(WaRecipient("Gérant ($cleanGerant)", cleanGerant))
                                }
                            }
                            invoiceRecipients = nextInvoiceList
                            onSaveStoreInfo(
                                storeInfo.copy(
                                    nom = nom.trim().ifBlank { storeInfo.nom },
                                    adresse = adresse.trim().ifBlank { storeInfo.adresse },
                                    tel = cleanMain,
                                    tel2 = tel2.trim().ifBlank { cleanMain },
                                    email = "",
                                    gerantWaPhone = cleanGerant,
                                    invoiceWaRecipients = nextInvoiceList,
                                    dispoWaRecipients = dispoRecipients
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_save_wa_invoice_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer Numéros Factures", fontWeight = FontWeight.Black)
                    }
                }
            }

            // Numéros WhatsApp Dispo Dépôt (Principal 0676754124 + multiples contacts manuels)
            WaRecipientsEditorCard(
                title = "WhatsApp Demande Dispo Dépôt",
                subtitle = "Principal : 0676754124. Ajoutez ici manuellement tous les numéros de dépôt pour envoyer une demande de disponibilité.",
                recipients = dispoRecipients,
                onUpdateRecipients = { dispoRecipients = it },
                onSaveAll = {
                    val safeList = if (dispoRecipients.any { it.phone.contains("0676754124") }) {
                        dispoRecipients
                    } else {
                        listOf(WaRecipient("Principal / Admin (0676754124)", "0676754124")) + dispoRecipients
                    }
                    dispoRecipients = safeList
                    onSaveStoreInfo(storeInfo.copy(invoiceWaRecipients = invoiceRecipients, dispoWaRecipients = safeList, gerantWaPhone = gerantWaPhone.trim()))
                },
                accentColor = Cyan400,
                testTagPrefix = "wa_dispo"
            )

            // Logo Magasin sur Facture (Photo Picker zero-permission)
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Logo sur Facture & Bon de Livraison", style = MaterialTheme.typography.titleMedium, color = Cyan400, fontWeight = FontWeight.Black)
                    if (logoBitmap != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = logoBitmap.asImageBitmap(),
                                contentDescription = "Logo magasin",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Slate850)
                                    .padding(4.dp)
                            )
                            Text(
                                "Logo enregistré — imprimé automatiquement en mode éco-encre.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Emerald400,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            "Aucun logo personnalisé. Importez votre logo pour l'afficher en haut des factures.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                logoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1.3f).height(48.dp).testTag("settings_pick_logo_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (logoBitmap != null) "Changer Logo" else "Choisir Logo", fontWeight = FontWeight.Black)
                        }
                        if (logoBitmap != null) {
                            OutlinedButton(
                                onClick = {
                                    InvoicePdfGenerator.deleteStoreLogo(context)
                                    logoVersion++
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, Rose500)
                            ) {
                                Text("Supprimer", color = Rose400, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // Vendeur configuré sur ce téléphone
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vendeur de cet Appareil (Factures)", style = MaterialTheme.typography.titleMedium, color = Cyan400, fontWeight = FontWeight.Black)
                    Text(
                        "Ce nom et numéro s'impriment automatiquement comme contact vendeur sur chaque facture créée depuis ce téléphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                    ClearableOutlinedTextField(
                        value = deviceSellerName,
                        onValueChange = { deviceSellerName = it },
                        label = { Text("Nom du Vendeur sur ce téléphone") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_seller_name_input")
                    )
                    ClearableOutlinedTextField(
                        value = deviceSellerPhone,
                        onValueChange = { deviceSellerPhone = it },
                        label = { Text("Tél Vendeur (sur facture)") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_seller_phone_input")
                    )
                    Button(
                        onClick = {
                            onSaveSettings(
                                settings.copy(
                                    deviceSellerName = deviceSellerName.trim(),
                                    deviceSellerPhone = deviceSellerPhone.trim()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_save_seller_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer Vendeur Appareil", fontWeight = FontWeight.Black)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Slate900, border = BorderStroke(1.5.dp, Slate800)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Site Actif & Impression", style = MaterialTheme.typography.titleMedium, color = Cyan400, fontWeight = FontWeight.Black)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PosSeeds.SITES.forEach { s ->
                            FilterChip(
                                selected = settings.activeStore == s,
                                onClick = { onSaveSettings(settings.copy(activeStore = s)) },
                                label = { Text(s.replace("Magasin ", "Mag "), fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Navy950, selectedLabelColor = OnDark)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Masquer Bain/Teinte sur facture", color = Slate100, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.hideBainTeinteFacture,
                            onCheckedChange = { onSaveSettings(settings.copy(hideBainTeinteFacture = it)) }
                        )
                    }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Slate900, border = BorderStroke(1.5.dp, Slate800)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Identité Magasin (Factures)", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Black)
                    ClearableOutlinedTextField(value = nom, onValueChange = { nom = it }, label = { Text("Nom commercial") }, modifier = Modifier.fillMaxWidth())
                    ClearableOutlinedTextField(value = adresse, onValueChange = { adresse = it }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onSaveStoreInfo(
                                storeInfo.copy(
                                    nom = nom.trim(),
                                    adresse = adresse.trim(),
                                    tel = tel.trim().ifBlank { "0676754124" },
                                    tel2 = tel2.trim().ifBlank { "0676754124" },
                                    email = "",
                                    gerantWaPhone = gerantWaPhone.trim()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sauvegarder Identité Magasin", fontWeight = FontWeight.Black)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Slate900, border = BorderStroke(1.5.dp, Slate800)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sauvegarde JSON & Catalogue", style = MaterialTheme.typography.titleMedium, color = Emerald400, fontWeight = FontWeight.Black)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onExportJson { json ->
                                    pendingExportJson = json
                                    exportLauncher.launch("CERAMICA_BACKUP.json")
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exporter", fontWeight = FontWeight.Black)
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, Cyan500)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Cyan400, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importer", color = Cyan400, fontWeight = FontWeight.Black)
                        }
                    }
                    OutlinedButton(
                        onClick = onResetCatalog,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Rose500)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = Rose400, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Réinitialiser Catalogue Usines", color = Rose400, fontWeight = FontWeight.Black)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Slate900, border = BorderStroke(1.5.dp, Slate800)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Partager l'application (.apk)", style = MaterialTheme.typography.titleMedium, color = Cyan400, fontWeight = FontWeight.Black)
                    Text(
                        "Envoyez directement le fichier d'installation CERAMICA_POS.apk vers un autre téléphone via WhatsApp, Telegram ou Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                    if (shareApkError != null) {
                        Text(
                            text = shareApkError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Rose400
                        )
                    }
                    Button(
                        onClick = {
                            if (isSharingApk) return@Button
                            isSharingApk = true
                            shareApkError = null
                            coroutineScope.launch {
                                val result = runCatching {
                                    val apkFile = withContext(Dispatchers.IO) {
                                        val sourcePath = context.applicationInfo.sourceDir
                                            ?: error("Fichier APK introuvable sur cet appareil.")
                                        val sourceApk = File(sourcePath)
                                        if (!sourceApk.exists() || !sourceApk.canRead()) {
                                            error("Impossible de lire le fichier APK installé.")
                                        }
                                        val shareDir = File(context.cacheDir, "shared_apks").apply { mkdirs() }
                                        val targetApk = File(shareDir, "CERAMICA_POS.apk")
                                        sourceApk.copyTo(targetApk, overwrite = true)
                                        targetApk
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        apkFile
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.android.package-archive"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "CERAMICA POS (.apk)")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Installez CERAMICA POS sur votre téléphone Android en ouvrant le fichier CERAMICA_POS.apk joint."
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooserIntent = Intent.createChooser(sendIntent, "Partager CERAMICA POS (.apk)").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(chooserIntent)
                                }
                                result.onFailure { err ->
                                    shareApkError = err.localizedMessage ?: "Impossible de partager l'APK."
                                }
                                isSharingApk = false
                            }
                        },
                        enabled = !isSharingApk,
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("share_apk_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark)
                    ) {
                        if (isSharingApk) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OnDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Préparation de CERAMICA_POS.apk...", fontWeight = FontWeight.Black)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Partager CERAMICA_POS.apk", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun WaRecipientsEditorCard(
    title: String,
    subtitle: String,
    recipients: List<WaRecipient>,
    onUpdateRecipients: (List<WaRecipient>) -> Unit,
    onSaveAll: () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
    testTagPrefix: String
) {
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Slate900),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = accentColor, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate400)

            recipients.forEachIndexed { idx, rec ->
                Surface(shape = RoundedCornerShape(12.dp), color = Slate950, border = BorderStroke(1.dp, Slate800)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ClearableOutlinedTextField(
                                value = rec.name,
                                onValueChange = { updatedName ->
                                    val mutable = recipients.toMutableList()
                                    mutable[idx] = rec.copy(name = updatedName)
                                    onUpdateRecipients(mutable)
                                },
                                label = { Text("Nom / Rôle") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            ClearableOutlinedTextField(
                                value = rec.phone,
                                onValueChange = { updatedPhone ->
                                    val mutable = recipients.toMutableList()
                                    mutable[idx] = rec.copy(phone = updatedPhone)
                                    onUpdateRecipients(mutable)
                                },
                                label = { Text("Numéro WhatsApp") },
                                modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_phone_$idx")
                            )
                        }
                        if (recipients.size > 1) {
                            IconButton(
                                onClick = {
                                    val mutable = recipients.toMutableList()
                                    mutable.removeAt(idx)
                                    onUpdateRecipients(mutable)
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer contact", tint = Rose500)
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ClearableOutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Ajouter Nom") },
                    modifier = Modifier.weight(1f)
                )
                ClearableOutlinedTextField(
                    value = newPhone,
                    onValueChange = { newPhone = it },
                    label = { Text("Tél") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onUpdateRecipients(recipients + WaRecipient(newName.trim(), newPhone.trim()))
                            newName = ""
                            newPhone = ""
                        }
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = OnDark),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter")
                }
            }

            Button(
                onClick = onSaveAll,
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("${testTagPrefix}_save_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enregistrer Contacts WhatsApp", fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<DepotNotification>,
    storeInfo: StoreInfo,
    onOpenInvoice: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    var categoryFilter by remember { mutableStateOf("Toutes") }

    val filtered = remember(notifications, categoryFilter) {
        notifications.filter { n ->
            val ref = n.reference.uppercase()
            when (categoryFilter) {
                "Ventes" -> ref.contains("[VENTE]")
                "Modifs" -> ref.contains("[MODIF]") || ref.contains("[SUPPR]")
                "Stock/Alertes" -> ref.contains("[STOCK]") || ref.contains("[ALERTE]")
                "Dispo Dépôt" -> ref.contains("[DISPO]")
                else -> true
            }
        }
    }

    val waManagementDigits = PosSeeds.WHATSAPP_MANAGEMENT
    val waDepotDigits = PosSeeds.WHATSAPP_MANAGEMENT

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Slate950,
        contentColor = Slate100
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("notifications_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Slate900,
                border = BorderStroke(1.dp, Slate800)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Amber500, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Alertes Direction & Dépôt (${filtered.size})", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Black)
                            Text("Ventes • Modifs Factures • Stock • Dispo", style = MaterialTheme.typography.labelSmall, color = Cyan400, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(44.dp).testTag("notifications_close_btn"),
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

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Toutes", "Ventes", "Modifs", "Stock/Alertes", "Dispo Dépôt").forEach { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { categoryFilter = cat },
                        label = { Text(cat, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan500,
                            selectedLabelColor = OnDark,
                            containerColor = Slate900,
                            labelColor = Slate100
                        )
                    )
                }
            }

            HorizontalDivider(color = Slate800)

            if (filtered.isEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = Slate900, border = BorderStroke(1.dp, Slate800)) {
                    Text(
                        text = "Aucune alerte dans cette catégorie.",
                        color = Slate400,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(18.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { n ->
                        val upperRef = n.reference.uppercase()
                        val badgeColor = when {
                            upperRef.contains("[MODIF]") || upperRef.contains("[ALERTE]") -> Amber500
                            upperRef.contains("[SUPPR]") -> Rose500
                            upperRef.contains("[STOCK]") || upperRef.contains("[DISPO]") -> Cyan500
                            else -> Emerald500
                        }
                        val badgeLabel = when {
                            upperRef.contains("[MODIF]") -> "MODIF"
                            upperRef.contains("[SUPPR]") -> "SUPPR"
                            upperRef.contains("[ALERTE]") -> "ALERTE STOCK"
                            upperRef.contains("[STOCK]") -> "STOCK"
                            upperRef.contains("[DISPO]") -> "DISPO DÉPÔT"
                            else -> "VENTE"
                        }
                        val hasOpenableInvoice = n.invoiceId.isNotBlank() &&
                            !upperRef.contains("[DISPO]") &&
                            !upperRef.contains("[STOCK]") &&
                            !upperRef.contains("[ALERTE]") &&
                            !upperRef.contains("[SUPPR]")

                        Surface(shape = RoundedCornerShape(14.dp), color = Slate900, border = BorderStroke(1.5.dp, badgeColor.copy(alpha = 0.6f))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                                        Text(
                                            text = badgeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OnDark,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "${n.cartonsTotal} ctn • ${formatM2(n.m2Total)} m² • ${n.date.take(16).replace('T', ' ')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("#${n.reference} • ${n.client}", style = MaterialTheme.typography.titleSmall, color = Slate100, fontWeight = FontWeight.Black)
                                Text(n.articlesSummary, style = MaterialTheme.typography.bodySmall, color = Slate400)
                                if (hasOpenableInvoice) {
                                    OutlinedButton(
                                        onClick = { onOpenInvoice(n.invoiceId) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("notif_open_invoice_${n.invoiceId}"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.5.dp, Cyan500)
                                    ) {
                                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Cyan400, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ouvrir la Facture & Photo", color = Cyan400, fontWeight = FontWeight.Black)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            val msg = "🔔 *ALERTE DIRECTION #${n.reference}*\nClient/Auteur: ${n.client}\n${n.articlesSummary}"
                                            openWhatsApp(context, waManagementDigits, msg)
                                        },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = OnDark),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("WA Direction", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                                    }
                                    Button(
                                        onClick = {
                                            val msg = "📦 *DÉPÔT #${n.reference}*\nClient: ${n.client}\n${n.articlesSummary}"
                                            openWhatsApp(context, waDepotDigits, msg)
                                        },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Warehouse, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("WA Dépôt", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
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
