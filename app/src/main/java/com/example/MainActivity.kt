package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AppDatabase
import com.example.data.repository.PosRepository
import com.example.ui.PosUiState
import com.example.ui.PosViewModel
import com.example.ui.components.InvoicePreviewPrintSheet
import com.example.ui.components.SmartTileCalculatorSheet
import com.example.ui.screens.CaisseScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.NotificationsSheet
import com.example.ui.screens.PosScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StaffLockScreen
import com.example.ui.screens.StockScreen
import com.example.ui.screens.SuppliersScreen
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Amber950
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.KaderPosTheme
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.SaleSystemNotifier
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pendingNotificationNav = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        SaleSystemNotifier.ensureChannels(applicationContext)
        captureNotificationIntent(intent)
        setContent {
            KaderPosTheme {
                val navTarget by pendingNotificationNav.collectAsStateWithLifecycle()
                KaderCeramicPosApp(
                    pendingNotificationTarget = navTarget,
                    onConsumeNotificationTarget = { pendingNotificationNav.value = false to null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationIntent(intent)
    }

    private fun captureNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val openNotifs = intent.getBooleanExtra(SaleSystemNotifier.EXTRA_OPEN_NOTIFICATIONS, false)
        val invoiceId = intent.getStringExtra(SaleSystemNotifier.EXTRA_INVOICE_ID)
        if (openNotifs || !invoiceId.isNullOrBlank()) {
            pendingNotificationNav.value = true to invoiceId
        }
    }
}

private data class NavTabMeta(
    val key: String,
    val label: String,
    val icon: ImageVector
)

private val ALL_NAV_TABS = listOf(
    NavTabMeta("pos", "Vente", Icons.Default.Storefront),
    NavTabMeta("stock", "Stock", Icons.Default.Inventory2),
    NavTabMeta("invoices", "Factures", Icons.Default.ReceiptLong),
    NavTabMeta("dashboard", "Stats", Icons.Default.BarChart),
    NavTabMeta("customers", "Clients", Icons.Default.Group),
    NavTabMeta("suppliers", "Fournisseurs", Icons.Default.Factory),
    NavTabMeta("caisse", "Caisse", Icons.Default.PointOfSale),
    NavTabMeta("settings", "Réglages", Icons.Default.Settings)
)

@Composable
fun KaderCeramicPosApp(
    pendingNotificationTarget: Pair<Boolean, String?> = false to null,
    onConsumeNotificationTarget: () -> Unit = {}
) {
    val viewModel: PosViewModel = viewModel(
        key = "offline_local",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localRepo = PosRepository(app.applicationContext, AppDatabase.getInstance(app.applicationContext))
                PosViewModel(
                    repository = localRepo,
                    appContext = app.applicationContext
                )
            }
        }
    )

    KaderPosSignedInContent(
        viewModel = viewModel,
        pendingNotificationTarget = pendingNotificationTarget,
        onConsumeNotificationTarget = onConsumeNotificationTarget
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KaderPosSignedInContent(
    viewModel: PosViewModel,
    pendingNotificationTarget: Pair<Boolean, String?>,
    onConsumeNotificationTarget: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMoreTabsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pendingNotificationTarget, state.isLoading, state.isLocked) {
        val (shouldOpen, invoiceId) = pendingNotificationTarget
        if (shouldOpen && !state.isLoading && !state.isLocked) {
            if (!invoiceId.isNullOrBlank()) {
                viewModel.openInvoiceById(invoiceId)
            } else {
                viewModel.openNotificationsSheet()
            }
            onConsumeNotificationTarget()
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    if (state.isLoading) {
        Surface(modifier = Modifier.fillMaxSize(), color = Slate950) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Cyan400)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Chargement catalogue CERAMICA...",
                    color = Slate100,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    if (state.isLocked) {
        StaffLockScreen(
            users = state.users,
            initialSelectedUser = state.currentUser,
            pinError = state.pinError,
            onClearError = { viewModel.clearPinError() },
            onUnlock = { user, pin -> viewModel.attemptUnlock(user, pin) }
        )
        return
    }

    val effectivePerms = state.effectivePermissions
    val isAdmin = state.currentUser?.role == "admin"
    val allowedTabKeys = effectivePerms.allowedTabs
    val allowedTabs = remember(allowedTabKeys) {
        ALL_NAV_TABS.filter { allowedTabKeys.contains(it.key) }
    }

    if (state.activeTab != "pos") {
        BackHandler { viewModel.selectTab("pos") }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMediumOrExpanded = maxWidth >= 600.dp
        val primaryPhoneTabs = remember(allowedTabs) { allowedTabs.take(4) }
        val overflowPhoneTabs = remember(allowedTabs) { allowedTabs.drop(4) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Slate950,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Slate900,
                            titleContentColor = Slate100
                        ),
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = state.storeInfo.nom.ifBlank { "CERAMICA" },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Slate100,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val roleBadgeColor = if (isAdmin) Amber400 else Cyan400
                                    Surface(
                                        onClick = {
                                            if (isAdmin) viewModel.switchToSellerMode() else viewModel.lockSession()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAdmin) Amber950 else Cyan950,
                                        border = BorderStroke(1.dp, roleBadgeColor.copy(alpha = 0.55f)),
                                        modifier = Modifier.testTag("topbar_role_badge")
                                    ) {
                                        Text(
                                            text = if (isAdmin) "ADMIN" else "VENDEUR",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = roleBadgeColor,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isAdmin) "Accès complet Administrateur" else "Vente & Stock • Sans PIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate400,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                if (isAdmin) {
                                    FilledTonalIconButton(
                                        onClick = { viewModel.openNotificationsSheet() },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Slate850,
                                            contentColor = Slate100
                                        ),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("topbar_notifications_btn")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (state.unreadNotificationsCount > 0) {
                                                    Badge(containerColor = Rose500, contentColor = OnDark) {
                                                        Text(
                                                            state.unreadNotificationsCount.toString(),
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = "Alertes Direction & Dépôt",
                                                tint = Slate100
                                            )
                                        }
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = { viewModel.lockSession() },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (isAdmin) Amber950 else Slate850,
                                        contentColor = if (isAdmin) Amber400 else Slate100
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("topbar_lock_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isAdmin) Icons.Default.Lock else Icons.Default.AdminPanelSettings,
                                        contentDescription = if (isAdmin) "Changer / Verrouiller session" else "Accès Administrateur (PIN)",
                                        tint = if (isAdmin) Amber400 else Cyan400
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = Slate800, thickness = 1.dp)
                }
            },
            bottomBar = {
                if (!isMediumOrExpanded) {
                    Column {
                        HorizontalDivider(color = Slate800, thickness = 1.dp)
                        NavigationBar(
                            containerColor = Slate900,
                            tonalElevation = 0.dp
                        ) {
                            primaryPhoneTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = state.activeTab == tab.key,
                                    onClick = { viewModel.selectTab(tab.key) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = {
                                        Text(
                                            tab.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Cyan400,
                                        selectedTextColor = Cyan400,
                                        indicatorColor = Cyan950,
                                        unselectedIconColor = Slate400,
                                        unselectedTextColor = Slate400
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.key}")
                                )
                            }
                            if (overflowPhoneTabs.isNotEmpty()) {
                                val isOverflowSelected = overflowPhoneTabs.any { it.key == state.activeTab }
                                NavigationBarItem(
                                    selected = isOverflowSelected,
                                    onClick = { showMoreTabsSheet = true },
                                    icon = { Icon(Icons.Default.Menu, contentDescription = "Plus") },
                                    label = { Text("Plus", maxLines = 1, fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Cyan400,
                                        selectedTextColor = Cyan400,
                                        indicatorColor = Cyan950,
                                        unselectedIconColor = Slate400,
                                        unselectedTextColor = Slate400
                                    ),
                                    modifier = Modifier.testTag("nav_more")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isMediumOrExpanded) {
                    NavigationRail(
                        containerColor = Slate900,
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))
                        allowedTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = state.activeTab == tab.key,
                                onClick = { viewModel.selectTab(tab.key) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1, fontWeight = FontWeight.Bold) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Cyan400,
                                    selectedTextColor = Cyan400,
                                    indicatorColor = Cyan950,
                                    unselectedIconColor = Slate400,
                                    unselectedTextColor = Slate400
                                ),
                                modifier = Modifier.testTag("rail_${tab.key}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ActiveTabContent(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }

        if (showMoreTabsSheet) {
            BackHandler { showMoreTabsSheet = false }
            ModalBottomSheet(
                onDismissRequest = { showMoreTabsSheet = false },
                containerColor = Slate950,
                contentColor = Slate100
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Autres Modules",
                        style = MaterialTheme.typography.titleLarge,
                        color = Slate100,
                        fontWeight = FontWeight.Black
                    )
                    overflowPhoneTabs.forEach { tab ->
                        val isSel = state.activeTab == tab.key
                        Surface(
                            onClick = {
                                viewModel.selectTab(tab.key)
                                showMoreTabsSheet = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("more_nav_${tab.key}"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSel) Navy950 else Slate900,
                            contentColor = if (isSel) OnDark else Slate100,
                            border = if (isSel) BorderStroke(1.5.dp, Cyan400) else BorderStroke(1.dp, Slate700),
                            shadowElevation = if (isSel) 4.dp else 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    tint = if (isSel) Amber400 else Cyan400
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSel) OnDark else Slate100,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        state.calculatorTarget?.let { target ->
            SmartTileCalculatorSheet(
                product = target.product,
                editingCartItem = target.editingCartItem,
                isOneTimeProduct = target.isOneTimeProduct,
                canViewPrixAchat = effectivePerms.canViewPrixAchat,
                canModifyPrices = effectivePerms.canModifyPrices,
                onDismiss = { viewModel.closeCalculator() },
                onConfirmProductCart = { product, cartons, pieces, m2Total, prixPerM2, bain, calibre, editId, prixPiece ->
                    viewModel.confirmCalculatorToCart(
                        product = product,
                        cartons = cartons,
                        pieces = pieces,
                        m2Total = m2Total,
                        prixPerM2 = prixPerM2,
                        bain = bain,
                        calibre = calibre,
                        editingItemId = editId,
                        prixPiece = prixPiece
                    )
                },
                onConfirmStandaloneFreeLine = { freeLine ->
                    viewModel.addFreeLine(freeLine)
                }
            )
        }

        state.previewInvoice?.let { inv ->
            InvoicePreviewPrintSheet(
                invoice = inv,
                storeInfo = state.storeInfo,
                settings = state.settings,
                autoEmailPdf = false,
                autoWhatsappPhoto = state.autoWhatsappPhoto,
                canHideInvoicePrices = effectivePerms.canHideInvoicePrices,
                onToggleHidePrices = { id, hide -> viewModel.setInvoiceHidePrices(id, hide) },
                onMarkWhatsappSent = { id -> viewModel.markInvoiceWhatsappSent(id) },
                onUpdateSettings = { viewModel.saveSettings(it) },
                onDismiss = { viewModel.closeInvoicePreview() }
            )
        }

        if (state.showNotificationsSheet) {
            NotificationsSheet(
                notifications = state.notifications,
                storeInfo = state.storeInfo,
                onOpenInvoice = { invoiceId -> viewModel.openInvoiceById(invoiceId) },
                onDismiss = { viewModel.closeNotificationsSheet() }
            )
        }
    }
}

@Composable
private fun ActiveTabContent(
    state: PosUiState,
    viewModel: PosViewModel
) {
    val perms = state.effectivePermissions
    val canViewPrixAchat = perms.canViewPrixAchat
    val isAdmin = state.currentUser?.role == "admin"
    when (state.activeTab) {
        "pos" -> PosScreen(
            products = state.products,
            cart = state.cart,
            freeLines = state.freeLines,
            customers = state.customers,
            storeInfo = state.storeInfo,
            editingInvoice = state.editingInvoice,
            canModifyPrices = perms.canModifyPrices,
            onOpenCalculatorForProduct = { viewModel.openProductCalculator(it) },
            onRequestDepotDispo = { viewModel.requestDepotAvailability(it) },
            onOpenOneTimeProduct = { viewModel.openOneTimeProductCalculator() },
            onEditCartItem = { viewModel.openCartItemCalculator(it) },
            onUpdateCartItemDirect = { id, name, cartons, pieces, m2Ctn, pcs, prix, prixPiece ->
                viewModel.updateCartItemDirect(id, name, cartons, pieces, m2Ctn, pcs, prix, prixPiece)
            },
            onAdjustCartItemCarton = { id, delta -> viewModel.adjustCartItemCartons(id, delta) },
            onRemoveCartItem = { viewModel.removeCartItem(it) },
            onAddFreeLine = { viewModel.addFreeLine(it) },
            onUpdateFreeLine = { viewModel.updateFreeLine(it) },
            onAdjustFreeLineQty = { id, delta -> viewModel.adjustFreeLineQuantity(id, delta) },
            onRemoveFreeLine = { viewModel.removeFreeLine(it) },
            onClearCart = { viewModel.clearCart() },
            onFinalizeSale = { client, remise, paye, mode, notes ->
                viewModel.finalizeSale(client, remise, paye, mode, notes)
            }
        )

        "stock" -> StockScreen(
            products = state.products,
            storeInfo = state.storeInfo,
            canModifyStock = perms.canModifyStock,
            canAddProducts = perms.canAddProducts,
            canViewPrixAchat = canViewPrixAchat,
            onAdjustStock = { id, delta -> viewModel.adjustStock(id, delta) },
            onSetStock = { id, stock -> viewModel.setStock(id, stock) },
            onSaveProduct = { viewModel.saveProduct(it) },
            onRequestDepotDispo = { viewModel.requestDepotAvailability(it) }
        )

        "invoices" -> InvoicesScreen(
            invoices = state.invoices,
            canEdit = perms.canEditInvoices,
            canDelete = perms.canDeleteInvoices,
            onPreviewInvoice = { viewModel.openInvoicePreview(it) },
            onEditInvoice = { viewModel.startEditingInvoice(it) },
            onDeleteInvoice = { viewModel.deleteInvoice(it) }
        )

        "dashboard" -> DashboardScreen(
            invoices = state.invoices,
            customers = state.customers,
            canViewPrixAchat = canViewPrixAchat
        )

        "customers" -> CustomersScreen(
            customers = state.customers,
            onSaveCustomer = { viewModel.saveCustomer(it) },
            onPayDebt = { id, amt -> viewModel.payCustomerDebt(id, amt) }
        )

        "suppliers" -> SuppliersScreen(
            suppliers = state.suppliers,
            movements = state.supplierMovements,
            onSaveSupplier = { viewModel.saveSupplier(it) },
            onRecordVersement = { id, amt, motif, ref -> viewModel.recordSupplierVersement(id, amt, motif, ref) },
            onRecordInvoice = { id, amt, motif, ref -> viewModel.recordSupplierInvoice(id, amt, motif, ref) }
        )

        "caisse" -> CaisseScreen(
            movements = state.caisseMovements,
            dailyCounts = state.dailyCaisseCounts,
            activeStore = state.settings.activeStore,
            onAddMovement = { type, motif, amt -> viewModel.addCaisseMovement(type, motif, amt) },
            onSaveDailyCount = { f, v, e, s, th, c, n ->
                viewModel.saveDailyCaisseCount(f, v, e, s, th, c, n)
            }
        )

        "settings" -> SettingsScreen(
            storeInfo = state.storeInfo,
            settings = state.settings,
            autoWhatsappPhoto = state.autoWhatsappPhoto,
            isAdmin = isAdmin,
            onChangeAdminPin = { viewModel.changeAdminPin(it) },
            onSaveStoreInfo = { viewModel.saveStoreInfo(it) },
            onSaveSettings = { viewModel.saveSettings(it) },
            onSetAutoWhatsappPhoto = { viewModel.setAutoWhatsappPhoto(it) },
            onResetCatalog = { viewModel.resetCatalog() },
            onExportJson = { viewModel.exportAllDataJson(it) },
            onImportJson = { viewModel.importAllDataJson(it) }
        )
    }
}
