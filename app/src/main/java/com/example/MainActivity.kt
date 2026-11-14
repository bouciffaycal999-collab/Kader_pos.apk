package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthAttemptResult
import com.example.auth.GoogleAuthManager
import com.example.auth.GoogleAuthManager.findActivity
import com.example.data.local.AppDatabase
import com.example.data.remote.FirestorePosRepository
import com.example.data.repository.PosRepository
import com.example.service.DepotAlertMonitorService
import com.example.ui.PosUiState
import com.example.ui.PosViewModel
import com.example.ui.components.InvoicePreviewPrintSheet
import com.example.ui.components.SmartTileCalculatorSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CaisseScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.M2PerCartonCalculatorScreen
import com.example.ui.screens.NotificationsSheet
import com.example.ui.screens.PosScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StaffLockScreen
import com.example.ui.screens.StockScreen
import com.example.ui.screens.SuppliersScreen
import com.example.ui.screens.UsersScreen
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Amber950
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan950
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald950
import com.example.ui.theme.KaderPosTheme
import com.example.ui.theme.Navy950
import com.example.ui.theme.OnDark
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
import com.example.util.NetworkConnectivityObserver
import com.example.util.SaleSystemNotifier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingNotificationNav = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    NavTabMeta("suppliers", "Usines", Icons.Default.Factory),
    NavTabMeta("caisse", "Caisse", Icons.Default.PointOfSale),
    NavTabMeta("users", "Équipe", Icons.Default.ManageAccounts),
    NavTabMeta("settings", "Réglages", Icons.Default.Settings),
    NavTabMeta("calculator", "Calc m²", Icons.Default.Calculate)
)

@Composable
fun KaderCeramicPosApp(
    pendingNotificationTarget: Pair<Boolean, String?> = false to null,
    onConsumeNotificationTarget: () -> Unit = {}
) {
    val context = LocalContext.current
    val hasFirebase = remember(context) { GoogleAuthManager.isFirebaseAvailable(context) }
    val auth = remember(hasFirebase) {
        if (hasFirebase) runCatching { Firebase.auth }.getOrNull() else null
    }
    val currentUser by remember(auth) {
        GoogleAuthManager.authStateFlow(auth)
    }.collectAsStateWithLifecycle(initialValue = auth?.currentUser)
    val user = currentUser

    val prefs = remember(context) {
        context.getSharedPreferences("kader_pos_prefs", Context.MODE_PRIVATE)
    }
    LaunchedEffect(Unit) {
        if (prefs.contains("offline_mode_enabled")) {
            prefs.edit().remove("offline_mode_enabled").apply()
        }
    }
    var allowOfflineMode by remember(hasFirebase) {
        mutableStateOf(!hasFirebase)
    }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            allowOfflineMode = false
        }
    }

    if (user == null && !allowOfflineMode) {
        AuthScreen(
            onContinueOffline = {
                allowOfflineMode = true
            }
        )
        return
    }

    val activityContext = remember(context) { context.findActivity() ?: context }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(activityContext) { CredentialManager.create(activityContext) }

    val viewModel: PosViewModel = viewModel(
        key = user?.uid ?: "offline_local",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localRepo = PosRepository(app.applicationContext, AppDatabase.getInstance(app.applicationContext))
                val remoteRepo = if (user != null && hasFirebase) {
                    val databaseId = FirestorePosRepository.resolveDatabaseId(app.applicationContext)
                    FirestorePosRepository(FirebaseFirestore.getInstance(databaseId))
                } else {
                    null
                }
                PosViewModel(
                    repository = localRepo,
                    firestoreRepo = remoteRepo,
                    currentUserId = user?.uid,
                    userEmail = user?.email,
                    userDisplayName = user?.displayName,
                    isNetworkConnectedFlow = NetworkConnectivityObserver.observeIsOnline(app.applicationContext),
                    appContext = app.applicationContext
                )
            }
        }
    )

    KaderPosSignedInContent(
        viewModel = viewModel,
        hasFirebase = hasFirebase,
        isGoogleSignedIn = user != null,
        pendingNotificationTarget = pendingNotificationTarget,
        onConsumeNotificationTarget = onConsumeNotificationTarget,
        onSignInGoogle = {
            when (val res = GoogleAuthManager.signInInteractive(activityContext, credentialManager, auth)) {
                AuthAttemptResult.Success -> null
                AuthAttemptResult.Cancelled -> "Connexion Google annulée."
                AuthAttemptResult.NoSavedCredential -> "Aucun compte Google détecté sur cet appareil. Ajoutez un compte Google dans les paramètres Android puis réessayez."
                is AuthAttemptResult.Error -> res.message
            }
        },
        onSignOutGoogle = {
            DepotAlertMonitorService.syncServiceState(context, isSignedIn = false, isAdminOrManager = false)
            allowOfflineMode = !hasFirebase
            GoogleAuthManager.signOut(credentialManager, scope, auth)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KaderPosSignedInContent(
    viewModel: PosViewModel,
    hasFirebase: Boolean,
    isGoogleSignedIn: Boolean,
    pendingNotificationTarget: Pair<Boolean, String?>,
    onConsumeNotificationTarget: () -> Unit,
    onSignInGoogle: suspend () -> String?,
    onSignOutGoogle: () -> Unit
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMoreTabsSheet by remember { mutableStateOf(false) }
    var showCloudStatusDialog by remember { mutableStateOf(false) }
    var requestedNotifPermissionOnce by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val role = state.currentUser?.role
        val isAdminOrMgr = role == null || role == "admin" || role == "gerant"
        DepotAlertMonitorService.syncServiceState(
            context = context,
            isSignedIn = isGoogleSignedIn,
            isAdminOrManager = isAdminOrMgr && granted
        )
    }

    LaunchedEffect(isGoogleSignedIn, state.currentUser?.role, state.depotWatchEnabled, state.isLocked) {
        val role = state.currentUser?.role
        val isAdminOrMgr = role == null || role == "admin" || role == "gerant"
        if (isGoogleSignedIn && isAdminOrMgr && state.depotWatchEnabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                !SaleSystemNotifier.hasNotificationPermission(context) &&
                !state.isLocked &&
                !requestedNotifPermissionOnce
            ) {
                requestedNotifPermissionOnce = true
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                DepotAlertMonitorService.syncServiceState(
                    context = context,
                    isSignedIn = true,
                    isAdminOrManager = true
                )
            }
        } else {
            DepotAlertMonitorService.syncServiceState(
                context = context,
                isSignedIn = false,
                isAdminOrManager = false
            )
        }
    }

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
                CircularProgressIndicator(color = Cyan500)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Chargement catalogue CERAMICA...",
                    color = Navy950,
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
                                        color = Navy950,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val cloudLabel = when {
                                        !isGoogleSignedIn -> "LOCAL"
                                        !state.isNetworkAvailable -> "HORS-LIGNE"
                                        state.isSyncingCloud || state.pendingCloudWrites > 0 ->
                                            if (state.pendingCloudWrites > 0) "SYNC (${state.pendingCloudWrites})" else "SYNC..."
                                        state.isCloudSynced -> "ONLINE"
                                        state.cloudSyncError != null -> "ERREUR SYNC"
                                        else -> "CONNEXION..."
                                    }
                                    val cloudBadgeColor = when {
                                        !isGoogleSignedIn -> Amber500
                                        !state.isNetworkAvailable || state.cloudSyncError != null -> Amber500
                                        state.isSyncingCloud || state.pendingCloudWrites > 0 -> Cyan400
                                        state.isCloudSynced -> Emerald500
                                        else -> Cyan400
                                    }
                                    Surface(
                                        onClick = { showCloudStatusDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = cloudBadgeColor.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, cloudBadgeColor.copy(alpha = 0.50f)),
                                        modifier = Modifier.testTag("topbar_cloud_status_badge")
                                    ) {
                                        Text(
                                            text = cloudLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cloudBadgeColor,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${state.settings.deviceSellerName.ifBlank { state.currentUser?.name ?: "" }} • ${state.settings.activeStore}",
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
                                FilledTonalIconButton(
                                    onClick = {
                                        if (state.activeTab == "calculator") {
                                            viewModel.selectTab("pos")
                                        } else {
                                            viewModel.selectTab("calculator")
                                        }
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (state.activeTab == "calculator") Cyan500 else Cyan950,
                                        contentColor = if (state.activeTab == "calculator") OnDark else Cyan400
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("topbar_calc_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Calculate,
                                        contentDescription = "Calculateur m²/Ctn"
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = { viewModel.openNotificationsSheet() },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Slate850,
                                        contentColor = Navy950
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
                                            tint = Navy950
                                        )
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = { viewModel.lockSession() },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Slate850,
                                        contentColor = Navy950
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("topbar_lock_btn")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Verrouiller / Changer profil")
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = Slate800, thickness = 1.dp)
                    if (!isGoogleSignedIn && hasFirebase && state.isNetworkAvailable) {
                        Surface(
                            onClick = { showCloudStatusDialog = true },
                            color = Cyan950,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_connect_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Cyan500, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Mode Local • Connectez Google Cloud pour synchroniser en ligne",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Cyan400,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Passer en ligne",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Cyan500,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        HorizontalDivider(color = Slate800, thickness = 1.dp)
                    } else if (isGoogleSignedIn && state.cloudSyncError != null) {
                        Surface(
                            onClick = { showCloudStatusDialog = true },
                            color = Amber950,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_error_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Amber500, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = state.cloudSyncError ?: "Synchronisation cloud en attente",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Amber400,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Diagnostiquer",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Amber500,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        HorizontalDivider(color = Slate800, thickness = 1.dp)
                    }
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
                        viewModel = viewModel,
                        onSignInGoogle = onSignInGoogle,
                        onSignOutGoogle = onSignOutGoogle
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
                        color = Navy950,
                        fontWeight = FontWeight.Black
                    )
                    overflowPhoneTabs.forEach { tab ->
                        val isSel = state.activeTab == tab.key
                        ElevatedCard(
                            onClick = {
                                viewModel.selectTab(tab.key)
                                showMoreTabsSheet = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("more_nav_${tab.key}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isSel) Navy950 else Slate900,
                                contentColor = if (isSel) OnDark else Slate100
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSel) 4.dp else 2.dp)
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
                                    tint = if (isSel) Amber500 else Cyan500
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
                autoEmailPdf = state.autoEmailPdf,
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

        if (showCloudStatusDialog) {
            BackHandler { showCloudStatusDialog = false }
            CloudStatusDialog(
                state = state,
                isGoogleSignedIn = isGoogleSignedIn,
                hasFirebase = hasFirebase,
                onSignInGoogle = onSignInGoogle,
                onRetryCloudSync = { viewModel.retryCloudSync() },
                onSignOutGoogle = {
                    showCloudStatusDialog = false
                    onSignOutGoogle()
                },
                onAddGoogleAccount = {
                    val addIntent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                    }
                    runCatching { activityContext.startActivity(addIntent) }
                        .onFailure {
                            runCatching {
                                activityContext.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                            }
                        }
                },
                onDismiss = { showCloudStatusDialog = false }
            )
        }
    }
}

@Composable
private fun CloudStatusDialog(
    state: PosUiState,
    isGoogleSignedIn: Boolean,
    hasFirebase: Boolean,
    onSignInGoogle: suspend () -> String?,
    onRetryCloudSync: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isConnecting by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when {
                        !isGoogleSignedIn -> Icons.Default.CloudOff
                        state.isSyncingCloud -> Icons.Default.CloudSync
                        state.isCloudSynced -> Icons.Default.CloudDone
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when {
                        !isGoogleSignedIn -> Amber500
                        state.isSyncingCloud -> Cyan500
                        state.isCloudSynced -> Emerald500
                        else -> Amber500
                    }
                )
                Text(
                    text = "État Google Cloud & Sync",
                    style = MaterialTheme.typography.titleLarge,
                    color = Navy950,
                    fontWeight = FontWeight.Black
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        !isGoogleSignedIn || !state.isNetworkAvailable || state.cloudSyncError != null -> Amber950
                        state.isCloudSynced -> Emerald950
                        else -> Cyan950
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            !isGoogleSignedIn || !state.isNetworkAvailable || state.cloudSyncError != null -> Amber500.copy(alpha = 0.4f)
                            state.isCloudSynced -> Emerald500.copy(alpha = 0.4f)
                            else -> Cyan500.copy(alpha = 0.4f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when {
                                !hasFirebase -> "Configuration Firebase absente"
                                !isGoogleSignedIn -> "Mode Local (Hors-Ligne)"
                                !state.isNetworkAvailable -> "Pas de connexion Internet (Wi-Fi / 4G coupé)"
                                state.isSyncingCloud -> "Synchronisation Cloud Firestore en cours..."
                                state.cloudSyncError != null -> "Sync Cloud en attente"
                                state.isCloudSynced -> "Connecté en temps réel (ONLINE)"
                                else -> "Connexion Cloud en cours..."
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate100,
                            fontWeight = FontWeight.Black
                        )
                        if (!state.firebaseUserEmail.isNullOrBlank()) {
                            Text(
                                text = "Compte : ${state.firebaseUserEmail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Emerald400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (state.pendingCloudWrites > 0) {
                            Text(
                                text = "${state.pendingCloudWrites} écriture(s) en cours d'envoi...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        state.cloudSyncError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = Rose400,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = "Pour synchroniser stock, factures et alertes entre le Dépôt (Admin) et les Vendeurs d'autres villes, connectez le même compte Google sur chaque appareil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )

                signInError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Rose950,
                        border = BorderStroke(1.dp, Rose400)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = Rose400,
                                fontWeight = FontWeight.Bold
                            )
                            if (err.contains("Aucun compte Google", ignoreCase = true)) {
                                TextButton(onClick = onAddGoogleAccount) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Cyan500, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ajouter un compte Google", color = Cyan500, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                if (!isGoogleSignedIn && hasFirebase) {
                    Button(
                        onClick = {
                            if (isConnecting) return@Button
                            isConnecting = true
                            signInError = null
                            scope.launch {
                                val err = onSignInGoogle()
                                isConnecting = false
                                if (err != null) {
                                    signInError = err
                                } else {
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isConnecting,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cloud_dialog_signin_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connexion Google...", fontWeight = FontWeight.Black)
                        } else {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecter Google Cloud", fontWeight = FontWeight.Black)
                        }
                    }
                } else if (isGoogleSignedIn) {
                    Button(
                        onClick = onRetryCloudSync,
                        enabled = state.isNetworkAvailable && !state.isSyncingCloud,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cloud_dialog_retry_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = OnDark)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synchroniser maintenant", fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = onSignOutGoogle,
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("cloud_dialog_signout_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Rose500)
                    ) {
                        Text("Changer / Déconnecter compte Google", color = Rose400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = Slate400, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ActiveTabContent(
    state: PosUiState,
    viewModel: PosViewModel,
    onSignInGoogle: suspend () -> String?,
    onSignOutGoogle: () -> Unit
) {
    val perms = state.effectivePermissions
    val canViewPrixAchat = perms.canViewPrixAchat
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

        "users" -> UsersScreen(
            users = state.users,
            currentUser = state.currentUser,
            rolePolicies = state.rolePolicies,
            effectivePermissions = perms,
            onSaveUser = { viewModel.saveUser(it) },
            onDeleteUser = { viewModel.deleteUser(it) },
            onSaveRolePolicy = { viewModel.saveRolePolicy(it) }
        )

        "settings" -> SettingsScreen(
            storeInfo = state.storeInfo,
            settings = state.settings,
            autoEmailPdf = state.autoEmailPdf,
            firebaseUserEmail = state.firebaseUserEmail,
            isCloudSynced = state.isCloudSynced,
            isNetworkAvailable = state.isNetworkAvailable,
            isSyncingCloud = state.isSyncingCloud,
            pendingCloudWrites = state.pendingCloudWrites,
            cloudSyncError = state.cloudSyncError,
            depotWatchEnabled = state.depotWatchEnabled,
            onSignInGoogle = onSignInGoogle,
            onSignOutGoogle = onSignOutGoogle,
            onRetryCloudSync = { viewModel.retryCloudSync() },
            onToggleDepotWatch = { viewModel.setDepotWatchEnabled(it) },
            onSendTestNotification = { viewModel.sendTestSystemNotification() },
            onSaveStoreInfo = { viewModel.saveStoreInfo(it) },
            onSaveSettings = { viewModel.saveSettings(it) },
            onSetAutoEmailPdf = { viewModel.setAutoEmailPdf(it) },
            onResetCatalog = { viewModel.resetCatalog() },
            onExportJson = { viewModel.exportAllDataJson(it) },
            onImportJson = { viewModel.importAllDataJson(it) }
        )

        "calculator" -> M2PerCartonCalculatorScreen(
            products = state.products,
            onAddProductToCart = { product, cartons, m2Total ->
                viewModel.confirmCalculatorToCart(
                    product = product,
                    cartons = cartons,
                    pieces = 0,
                    m2Total = m2Total,
                    prixPerM2 = product.prix,
                    bain = "",
                    calibre = "",
                    editingItemId = null,
                    prixPiece = 0.0
                )
                viewModel.selectTab("pos")
            },
            onAddCalculatedLineToCart = { freeLine ->
                viewModel.addFreeLine(freeLine)
                viewModel.selectTab("pos")
            }
        )
    }
}
