package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppSettings
import com.example.data.model.CaisseMovement
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.DailyCaisseCount
import com.example.data.model.DepotNotification
import com.example.data.model.EffectivePermissions
import com.example.data.model.FreeLineItem
import com.example.data.model.Invoice
import com.example.data.model.InvoiceClient
import com.example.data.model.PosSeeds
import com.example.data.model.RolePolicy
import com.example.data.model.StaffUser
import com.example.data.model.StoreInfo
import com.example.data.model.Supplier
import com.example.data.model.SupplierMovement
import com.example.data.model.TileCalculatorMath
import com.example.data.model.TileProduct
import com.example.data.remote.FirestorePosRepository
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.remote.unwrapFirestoreException
import com.example.data.repository.PosRepository
import com.example.service.DepotAlertMonitorService
import com.example.util.SaleSystemNotifier
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt

data class CalculatorTarget(
    val product: TileProduct? = null,
    val editingCartItem: CartItem? = null,
    val isOneTimeProduct: Boolean = false
)

data class PosUiState(
    val isLoading: Boolean = true,
    val products: List<TileProduct> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val supplierMovements: List<SupplierMovement> = emptyList(),
    val caisseMovements: List<CaisseMovement> = emptyList(),
    val dailyCaisseCounts: List<DailyCaisseCount> = emptyList(),
    val users: List<StaffUser> = emptyList(),
    val rolePolicies: List<RolePolicy> = PosSeeds.DEFAULT_ROLE_POLICIES,
    val notifications: List<DepotNotification> = emptyList(),
    val cart: List<CartItem> = emptyList(),
    val freeLines: List<FreeLineItem> = emptyList(),
    val storeInfo: StoreInfo = PosSeeds.DEFAULT_STORE_INFO,
    val settings: AppSettings = PosSeeds.DEFAULT_SETTINGS,
    val currentUser: StaffUser? = null,
    val isLocked: Boolean = true,
    val pinError: String? = null,
    val activeTab: String = "pos",
    val calculatorTarget: CalculatorTarget? = null,
    val previewInvoice: Invoice? = null,
    val showNotificationsSheet: Boolean = false,
    val editingInvoice: Invoice? = null,
    val statusMessage: String? = null,
    val firebaseUserEmail: String? = null,
    val isCloudSynced: Boolean = false,
    val isSyncingCloud: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val pendingCloudWrites: Int = 0,
    val cloudSyncError: String? = null,
    val autoEmailPdf: Boolean = false,
    val autoWhatsappPhoto: Boolean = true,
    val depotWatchEnabled: Boolean = true
) {
    val effectivePermissions: EffectivePermissions
        get() = PosSeeds.resolveEffectivePermissions(currentUser, rolePolicies)
    val unreadNotificationsCount: Int get() = notifications.count { !it.read }
    val cartTotalBrut: Double
        get() = cart.sumOf { it.sousTotal } + freeLines.sumOf { it.sousTotal }
    val cartTotalM2: Double
        get() = cart.sumOf { it.m2Total }
    val cartTotalCartons: Int
        get() = cart.sumOf { it.cartons }
    val caisseSolde: Double
        get() = caisseMovements.sumOf { if (it.type == "entree") it.montant else -it.montant }
}

private data class LocalNavState(
    val isReady: Boolean = false,
    val isLocked: Boolean = true,
    val pinError: String? = null,
    val activeTab: String = "pos",
    val calculatorTarget: CalculatorTarget? = null,
    val previewInvoice: Invoice? = null,
    val showNotificationsSheet: Boolean = false,
    val editingInvoice: Invoice? = null,
    val statusMessage: String? = null,
    val isCloudSynced: Boolean = false,
    val isSyncingCloud: Boolean = false,
    val pendingCloudWrites: Int = 0,
    val cloudSyncError: String? = null,
    val depotWatchEnabled: Boolean = true
)

class PosViewModel(
    private val repository: PosRepository,
    private val firestoreRepo: FirestorePosRepository?,
    private val currentUserId: String?,
    private val userEmail: String?,
    private val userDisplayName: String?,
    private val isNetworkConnectedFlow: Flow<Boolean> = flowOf(true),
    private val appContext: Context? = null
) : ViewModel() {

    private val navState = MutableStateFlow(
        LocalNavState(
            depotWatchEnabled = appContext?.let { SaleSystemNotifier.isDepotWatchEnabled(it) } ?: true
        )
    )
    private val networkOnlineState = MutableStateFlow(true)
    private val bootstrapMutex = Mutex()
    private var initialCloudSeedCompleted = false

    init {
        viewModelScope.launch {
            isNetworkConnectedFlow.distinctUntilChanged().collect { online ->
                val wasOffline = !networkOnlineState.value
                networkOnlineState.value = online
                val remote = firestoreRepo
                val uid = currentUserId
                if (online && wasOffline && remote != null && !uid.isNullOrBlank()) {
                    syncBootstrapAndFlush(remote, uid, notifyUser = false)
                } else if (!online && remote != null && !uid.isNullOrBlank()) {
                    navState.update {
                        it.copy(
                            isCloudSynced = false,
                            cloudSyncError = "Pas de connexion Internet — cache local actif"
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            networkOnlineState.value = isNetworkConnectedFlow.firstOrNull() ?: true
            repository.ensureSeeded()
            navState.update { it.copy(isReady = true) }
            val remote = firestoreRepo
            val uid = currentUserId
            if (remote != null && !uid.isNullOrBlank()) {
                bootstrapAndObserveCloud(remote, uid)
            }
        }
    }

    private fun bootstrapAndObserveCloud(
        remote: FirestorePosRepository,
        uid: String
    ) {
        if (networkOnlineState.value) {
            navState.update { it.copy(isSyncingCloud = true, cloudSyncError = null) }
            viewModelScope.launch {
                syncBootstrapAndFlush(remote, uid, notifyUser = false)
            }
        } else {
            navState.update {
                it.copy(
                    isCloudSynced = false,
                    isSyncingCloud = false,
                    cloudSyncError = "Pas de connexion Internet — cache local actif"
                )
            }
        }

        // Attach all real-time listeners immediately so offline cache and live updates work seamlessly
        collectCloudStream(
            path = "users/$uid/products",
            flow = remote.observeProducts(uid)
        ) { items ->
            if (items.isNotEmpty()) repository.replaceProductsFromCloud(items)
        }
        collectCloudStream(
            path = "users/$uid/invoices",
            flow = remote.observeInvoices(uid)
        ) { items ->
            if (items.isNotEmpty() || !repository.hasPendingOfflineSync()) {
                repository.replaceInvoicesFromCloud(items)
            }
        }
        collectCloudStream(
            path = "users/$uid/customers",
            flow = remote.observeCustomers(uid)
        ) { items ->
            if (items.isNotEmpty()) repository.replaceCustomersFromCloud(items)
        }
        collectCloudStream(
            path = "users/$uid/suppliers",
            flow = remote.observeSuppliers(uid)
        ) { items ->
            if (items.isNotEmpty()) repository.replaceSuppliersFromCloud(items)
        }
        collectCloudStream(
            path = "users/$uid/supplier_movements",
            flow = remote.observeSupplierMovements(uid)
        ) { items ->
            if (items.isNotEmpty() || !repository.hasPendingOfflineSync()) {
                repository.replaceSupplierMovementsFromCloud(items)
            }
        }
        collectCloudStream(
            path = "users/$uid/caisse_movements",
            flow = remote.observeCaisseMovements(uid)
        ) { items ->
            if (items.isNotEmpty()) repository.replaceCaisseFromCloud(items)
        }
        collectCloudStream(
            path = "users/$uid/daily_caisse_counts",
            flow = remote.observeDailyCaisseCounts(uid)
        ) { items ->
            if (items.isNotEmpty() || !repository.hasPendingOfflineSync()) {
                repository.replaceDailyCaisseCountsFromCloud(items)
            }
        }
        collectCloudStream(
            path = "users/$uid/staff_users",
            flow = remote.observeStaffUsers(uid)
        ) { items ->
            if (items.isNotEmpty()) repository.replaceUsersFromCloud(items)
        }
        collectCloudStream(
            path = "users/$uid/role_policies",
            flow = remote.observeRolePolicies(uid)
        ) { items ->
            if (items.isNotEmpty()) repository.replaceRolePoliciesFromCloud(items)
        }
        collectCloudStream(
            path = "users/$uid/notifications",
            flow = remote.observeNotifications(uid)
        ) { items ->
            if (items.isNotEmpty() || !repository.hasPendingOfflineSync()) {
                repository.replaceNotificationsFromCloud(items)
            }
            val ctx = appContext
            if (ctx != null && items.isNotEmpty()) {
                val activeId = repository.currentUserIdFlow.firstOrNull()
                val activeStaff = repository.getUsersSnapshot().find { it.id == activeId && it.active }
                val role = activeStaff?.role ?: uiState.value.currentUser?.role
                val isAdminOrMgr = role == null || role == "admin" || role == "gerant"
                SaleSystemNotifier.processCloudNotifications(
                    context = ctx,
                    notifications = items,
                    isAdminOrManager = isAdminOrMgr
                )
            }
        }
        collectCloudStream(
            path = "users/$uid/store_info/default",
            flow = remote.observeStoreInfo(uid)
        ) { info ->
            if (info != null) repository.saveStoreInfo(info)
        }
        collectCloudStream(
            path = "users/$uid/app_settings/default",
            flow = remote.observeAppSettings(uid)
        ) { settings ->
            if (settings != null) repository.saveSettings(settings)
        }
    }

    private suspend fun syncBootstrapAndFlush(
        remote: FirestorePosRepository,
        uid: String,
        notifyUser: Boolean
    ) {
        if (!networkOnlineState.value) {
            navState.update {
                it.copy(
                    isCloudSynced = false,
                    isSyncingCloud = false,
                    cloudSyncError = "Pas de connexion Internet — cache local actif",
                    statusMessage = if (notifyUser) "Pas de connexion Internet — vérifiez le Wi-Fi / 4G" else it.statusMessage
                )
            }
            return
        }
        bootstrapMutex.withLock {
            navState.update { it.copy(isSyncingCloud = true, cloudSyncError = null) }
            try {
                // Verify authenticated server access first before bulk seed operations
                remote.ensureUserAccount(uid, userEmail, userDisplayName)
                if (repository.hasPendingOfflineSync()) {
                    remote.mergeMissingOfflineData(
                        userId = uid,
                        products = repository.getProductsSnapshot(),
                        customers = repository.getCustomersSnapshot(),
                        suppliers = repository.getSuppliersSnapshot(),
                        invoices = repository.getInvoicesSnapshot(),
                        caisseMovements = repository.getCaisseSnapshot(),
                        supplierMovements = repository.getSupplierMovementsSnapshot(),
                        dailyCaisseCounts = repository.getDailyCaisseCountsSnapshot(),
                        notifications = repository.getNotificationsSnapshot()
                    )
                    repository.setPendingOfflineSync(false)
                }
                if (!initialCloudSeedCompleted) {
                    remote.seedProductsIfEmpty(
                        userId = uid,
                        products = repository.getProductsSnapshot()
                    )
                    remote.seedDefaultsIfEmpty(
                        userId = uid,
                        staffUsers = repository.getUsersSnapshot(),
                        customers = repository.getCustomersSnapshot(),
                        suppliers = repository.getSuppliersSnapshot(),
                        caisseMovements = repository.getCaisseSnapshot(),
                        storeInfo = repository.getStoreInfoSnapshot(),
                        settings = repository.getSettingsSnapshot(),
                        rolePolicies = repository.getRolePoliciesSnapshot().ifEmpty { PosSeeds.DEFAULT_ROLE_POLICIES }
                    )
                    remote.seedOfflineHistoryIfEmpty(
                        userId = uid,
                        invoices = repository.getInvoicesSnapshot(),
                        supplierMovements = repository.getSupplierMovementsSnapshot(),
                        dailyCaisseCounts = repository.getDailyCaisseCountsSnapshot(),
                        notifications = repository.getNotificationsSnapshot()
                    )
                    initialCloudSeedCompleted = true
                }
                val flushed = withTimeoutOrNull(15_000L) {
                    remote.waitForPendingWrites()
                    true
                } ?: false
                if (flushed) {
                    navState.update {
                        it.copy(
                            isCloudSynced = true,
                            cloudSyncError = null,
                            statusMessage = if (notifyUser) "Cloud Firestore synchronisé en temps réel" else it.statusMessage
                        )
                    }
                } else {
                    navState.update {
                        it.copy(
                            isCloudSynced = false,
                            cloudSyncError = "Délai Cloud dépassé — vérifiez votre connexion Internet",
                            statusMessage = if (notifyUser) "Délai Cloud dépassé — vérifiez Internet" else it.statusMessage
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val root = e.unwrapFirestoreException() ?: e
                Log.w(TAG, "Cloud bootstrap warning", root)
                navState.update {
                    it.copy(
                        isCloudSynced = false,
                        cloudSyncError = root.message ?: "Synchronisation cloud en attente",
                        statusMessage = if (notifyUser) "Sync cloud en attente : ${root.message ?: "vérifiez internet"}" else it.statusMessage
                    )
                }
            } finally {
                navState.update { it.copy(isSyncingCloud = false) }
            }
        }
    }

    fun retryCloudSync() {
        val remote = firestoreRepo
        val uid = currentUserId
        if (remote == null || uid.isNullOrBlank()) {
            showMessage("Connectez un compte Google pour activer la sync Cloud.")
            return
        }
        viewModelScope.launch {
            syncBootstrapAndFlush(remote, uid, notifyUser = true)
        }
    }

    private fun <T> collectCloudStream(
        path: String,
        flow: Flow<T>,
        onEachValue: suspend (T) -> Unit
    ) {
        viewModelScope.launch {
            flow
                .retryWhen { cause, attempt ->
                    val firestoreError = cause.unwrapFirestoreException()
                    if (firestoreError == null && cause is CancellationException) return@retryWhen false
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                        firestoreError?.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                    ) {
                        return@retryWhen false
                    }
                    val rootError = firestoreError ?: cause
                    handleFirestoreError(rootError, OperationType.LIST, path)
                    navState.update {
                        it.copy(
                            isCloudSynced = false,
                            cloudSyncError = "Reconnexion temps réel... (${rootError.message ?: "hors ligne"})"
                        )
                    }
                    val backoffMs = (1000L * (1L shl attempt.coerceAtMost(4).toInt())).coerceAtMost(15_000L)
                    delay(backoffMs)
                    true
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    handleFirestoreError(rootError, OperationType.LIST, path)
                    navState.update {
                        it.copy(
                            isCloudSynced = false,
                            cloudSyncError = rootError.message ?: "Erreur de synchronisation cloud"
                        )
                    }
                }
                .collect { value ->
                    onEachValue(value)
                    val cur = navState.value
                    if (!cur.isSyncingCloud && cur.cloudSyncError?.startsWith("Reconnexion temps réel") == true) {
                        navState.update { it.copy(isCloudSynced = true, cloudSyncError = null) }
                    }
                }
        }
    }

    private fun launchCloudSync(
        operationType: OperationType,
        pathSuffix: String,
        trackOfflineMutation: Boolean = true,
        block: suspend (FirestorePosRepository, String) -> Unit
    ) {
        val remote = firestoreRepo
        val uid = currentUserId?.takeIf { it.isNotBlank() }
        if (remote == null || uid == null) {
            if (trackOfflineMutation) {
                viewModelScope.launch { repository.setPendingOfflineSync(true) }
            }
            return
        }
        viewModelScope.launch {
            navState.update { it.copy(pendingCloudWrites = it.pendingCloudWrites + 1) }
            try {
                runCloudSync(remote, uid, operationType, pathSuffix, block)
            } finally {
                navState.update { it.copy(pendingCloudWrites = (it.pendingCloudWrites - 1).coerceAtLeast(0)) }
            }
        }
    }

    private suspend fun runCloudSync(
        remote: FirestorePosRepository,
        uid: String,
        operationType: OperationType,
        pathSuffix: String,
        block: suspend (FirestorePosRepository, String) -> Unit
    ) {
        val fullPath = if (pathSuffix.isBlank()) "users/$uid" else "users/$uid/$pathSuffix"
        try {
            block(remote, uid)
            navState.update {
                if (it.cloudSyncError != null) it.copy(isCloudSynced = true, cloudSyncError = null) else it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val root = e.unwrapFirestoreException() ?: e
            handleFirestoreError(root, operationType, fullPath)
            navState.update {
                it.copy(cloudSyncError = root.message ?: "Erreur sync cloud")
            }
        }
    }

    private val coreDataFlow = combine(
        repository.productsFlow,
        repository.invoicesFlow,
        repository.customersFlow,
        repository.suppliersFlow,
        repository.supplierMovementsFlow
    ) { products, invoices, customers, suppliers, supMoves ->
        CoreBundle(products, invoices, customers, suppliers, supMoves)
    }

    private val opsDataFlow = combine(
        repository.caisseFlow,
        repository.dailyCaisseCountsFlow,
        repository.usersFlow,
        repository.notificationsFlow,
        repository.currentUserIdFlow
    ) { caisse, dailyCounts, users, notifications, currentUserId ->
        OpsBundle(caisse, dailyCounts, users, notifications, currentUserId)
    }

    private val autoDispatchFlagsFlow = combine(
        repository.autoEmailPdfFlow,
        repository.autoWhatsappPhotoFlow
    ) { autoEmailPdf, autoWhatsappPhoto ->
        autoEmailPdf to autoWhatsappPhoto
    }

    private val sessionDataFlow = combine(
        repository.cartFlow,
        repository.freeLinesFlow,
        repository.storeInfoFlow,
        repository.settingsFlow,
        autoDispatchFlagsFlow
    ) { cart, freeLines, storeInfo, settings, (autoEmailPdf, autoWhatsappPhoto) ->
        SessionBundle(cart, freeLines, storeInfo, settings, autoEmailPdf, autoWhatsappPhoto)
    }

    private val statusAndNavFlow = combine(
        repository.rolePoliciesFlow,
        navState,
        networkOnlineState
    ) { rolePolicies, nav, isNetworkAvailable ->
        Triple(rolePolicies, nav, isNetworkAvailable)
    }

    val uiState: StateFlow<PosUiState> = combine(
        coreDataFlow,
        opsDataFlow,
        sessionDataFlow,
        statusAndNavFlow
    ) { core, ops, session, (rolePolicies, nav, isNetworkAvailable) ->
        val resolvedPolicies = rolePolicies.ifEmpty { PosSeeds.DEFAULT_ROLE_POLICIES }
        val matchedUser = ops.users.find { it.id == ops.currentUserId && it.active }
            ?: ops.users.firstOrNull { it.active }
        val resolvedPerms = PosSeeds.resolveEffectivePermissions(matchedUser, resolvedPolicies)
        val resolvedTab = if (matchedUser != null && !resolvedPerms.allowedTabs.contains(nav.activeTab)) {
            resolvedPerms.allowedTabs.firstOrNull() ?: "pos"
        } else {
            nav.activeTab
        }
        val liveCalcTarget = nav.calculatorTarget?.let { target ->
            val targetProd = target.product
            if (targetProd != null && !target.isOneTimeProduct) {
                val refreshed = core.products.find { it.id == targetProd.id }
                if (refreshed != null && refreshed != targetProd) target.copy(product = refreshed) else target
            } else target
        }
        val effectiveCloudSynced = !currentUserId.isNullOrBlank() &&
            firestoreRepo != null &&
            isNetworkAvailable &&
            nav.isCloudSynced &&
            nav.cloudSyncError == null
        PosUiState(
            isLoading = !nav.isReady,
            products = core.products,
            invoices = core.invoices,
            customers = core.customers,
            suppliers = core.suppliers,
            supplierMovements = core.supMoves,
            caisseMovements = ops.caisse,
            dailyCaisseCounts = ops.dailyCounts,
            users = ops.users,
            rolePolicies = resolvedPolicies,
            notifications = ops.notifications,
            cart = session.cart,
            freeLines = session.freeLines,
            storeInfo = session.storeInfo,
            settings = session.settings,
            currentUser = matchedUser,
            isLocked = nav.isLocked || ops.currentUserId.isNullOrBlank(),
            pinError = nav.pinError,
            activeTab = resolvedTab,
            calculatorTarget = liveCalcTarget,
            previewInvoice = nav.previewInvoice,
            showNotificationsSheet = nav.showNotificationsSheet,
            editingInvoice = nav.editingInvoice,
            statusMessage = nav.statusMessage,
            firebaseUserEmail = userEmail,
            isCloudSynced = effectiveCloudSynced,
            isSyncingCloud = nav.isSyncingCloud,
            isNetworkAvailable = isNetworkAvailable,
            pendingCloudWrites = nav.pendingCloudWrites,
            cloudSyncError = nav.cloudSyncError,
            autoEmailPdf = session.autoEmailPdf,
            autoWhatsappPhoto = session.autoWhatsappPhoto,
            depotWatchEnabled = nav.depotWatchEnabled
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PosUiState(firebaseUserEmail = userEmail)
        )

    fun attemptUnlock(user: StaffUser, enteredPin: String) {
        if (enteredPin == user.pin) {
            viewModelScope.launch {
                repository.setCurrentUserId(user.id)
                val perms = PosSeeds.resolveEffectivePermissions(user, uiState.value.rolePolicies)
                val firstTab = if (perms.allowedTabs.contains("pos")) "pos" else perms.allowedTabs.firstOrNull() ?: "pos"
                navState.value = navState.value.copy(
                    isLocked = false,
                    pinError = null,
                    activeTab = firstTab
                )
            }
        } else {
            navState.value = navState.value.copy(pinError = "Code PIN incorrect pour ${user.name}")
        }
    }

    fun lockSession() {
        navState.value = navState.value.copy(isLocked = true, pinError = null)
    }

    fun clearPinError() {
        if (navState.value.pinError != null) {
            navState.value = navState.value.copy(pinError = null)
        }
    }

    fun selectTab(tab: String) {
        val perms = uiState.value.effectivePermissions
        if (tab !in perms.allowedTabs) {
            showMessage("Accès non autorisé à ce module")
            return
        }
        navState.value = navState.value.copy(activeTab = tab)
    }

    fun openStandaloneCalculator() {
        navState.value = navState.value.copy(calculatorTarget = CalculatorTarget())
    }

    fun openOneTimeProductCalculator() {
        navState.value = navState.value.copy(
            calculatorTarget = CalculatorTarget(isOneTimeProduct = true)
        )
    }

    fun openProductCalculator(product: TileProduct) {
        navState.value = navState.value.copy(calculatorTarget = CalculatorTarget(product = product))
    }

    fun openCartItemCalculator(item: CartItem) {
        val isOneTime = item.productId.startsWith("onetime_")
        val product = uiState.value.products.find { it.id == item.productId } ?: TileProduct(
            id = item.productId,
            name = item.name,
            usine = item.usine,
            dimension = item.dimension,
            m2Carton = item.m2Carton,
            pcs = item.pcs,
            prix = item.prixUnitaire,
            prixAchat = item.prixAchat,
            stock = 0,
            color = item.color,
            colorName = item.colorName,
            image = item.image,
            pricingUnit = item.pricingUnit,
            prixPiece = item.prixPiece
        )
        navState.value = navState.value.copy(
            calculatorTarget = CalculatorTarget(
                product = product,
                editingCartItem = item,
                isOneTimeProduct = isOneTime
            )
        )
    }

    fun closeCalculator() {
        navState.value = navState.value.copy(calculatorTarget = null)
    }

    fun openInvoicePreview(invoice: Invoice) {
        navState.value = navState.value.copy(previewInvoice = invoice)
    }

    fun closeInvoicePreview() {
        navState.value = navState.value.copy(previewInvoice = null)
    }

    fun markInvoiceWhatsappSent(invoiceId: String) {
        viewModelScope.launch {
            repository.markInvoiceWhatsappSent(invoiceId)
            navState.value.previewInvoice?.let { current ->
                if (current.id == invoiceId && !current.whatsappSentBeforePrint) {
                    navState.value = navState.value.copy(
                        previewInvoice = current.copy(whatsappSentBeforePrint = true)
                    )
                }
            }
            launchCloudSync(OperationType.UPDATE, "invoices/$invoiceId") { remote, uid ->
                remote.markInvoiceWhatsappSent(uid, invoiceId)
            }
        }
    }

    fun setInvoiceHidePrices(invoiceId: String, hide: Boolean) {
        if (!uiState.value.effectivePermissions.canHideInvoicePrices) {
            showMessage("Option masquage prix réservée à Admin / Manager")
            return
        }
        viewModelScope.launch {
            repository.updateInvoiceHidePrices(invoiceId, hide)
            navState.value.previewInvoice?.let { current ->
                if (current.id == invoiceId && current.hidePrices != hide) {
                    navState.value = navState.value.copy(
                        previewInvoice = current.copy(hidePrices = hide)
                    )
                }
            }
            launchCloudSync(OperationType.UPDATE, "invoices/$invoiceId") { remote, uid ->
                remote.updateInvoiceHidePrices(uid, invoiceId, hide)
            }
        }
    }

    fun openNotificationsSheet() {
        navState.value = navState.value.copy(showNotificationsSheet = true)
    }

    fun openInvoiceById(invoiceId: String) {
        if (invoiceId.isBlank()) {
            openNotificationsSheet()
            return
        }
        val match = uiState.value.invoices.find { it.id == invoiceId }
        if (match != null) {
            navState.update {
                it.copy(
                    showNotificationsSheet = false,
                    previewInvoice = match
                )
            }
        } else {
            navState.update { it.copy(showNotificationsSheet = true) }
        }
    }

    fun setDepotWatchEnabled(enabled: Boolean) {
        val ctx = appContext
        if (ctx != null) {
            SaleSystemNotifier.setDepotWatchEnabled(ctx, enabled)
            val role = uiState.value.currentUser?.role
            val isAdminOrMgr = role == null || role == "admin" || role == "gerant"
            DepotAlertMonitorService.syncServiceState(
                context = ctx,
                isSignedIn = !currentUserId.isNullOrBlank(),
                isAdminOrManager = isAdminOrMgr
            )
        }
        navState.update {
            it.copy(
                depotWatchEnabled = enabled,
                statusMessage = if (enabled) {
                    "Veille Dépôt Temps Réel activée (notifications hors-app)"
                } else {
                    "Veille persistante désactivée"
                }
            )
        }
    }

    fun sendTestSystemNotification() {
        val ctx = appContext
        if (ctx == null) {
            showMessage("Contexte Android indisponible pour le test notification.")
            return
        }
        val sent = SaleSystemNotifier.sendTestAdminNotification(ctx)
        if (sent) {
            showMessage("Notification test envoyée dans la barre Android")
        } else {
            showMessage("Autorisez les notifications Android dans Réglages pour recevoir les alertes hors-app.")
        }
    }

    fun closeNotificationsSheet() {
        navState.value = navState.value.copy(showNotificationsSheet = false)
        viewModelScope.launch {
            val unreadIds = repository.getUnreadNotificationIds()
            repository.markAllNotificationsRead()
            if (unreadIds.isNotEmpty()) {
                launchCloudSync(OperationType.UPDATE, "notifications", trackOfflineMutation = false) { remote, uid ->
                    remote.markAllNotificationsRead(uid, unreadIds)
                }
            }
        }
    }

    fun clearStatusMessage() {
        navState.value = navState.value.copy(statusMessage = null)
    }

    private fun showMessage(msg: String) {
        navState.value = navState.value.copy(statusMessage = msg)
    }

    // Cart Operations
    fun confirmCalculatorToCart(
        product: TileProduct,
        cartons: Int,
        pieces: Int,
        m2Total: Double,
        prixPerM2: Double,
        bain: String,
        calibre: String,
        editingItemId: String?,
        prixPiece: Double = 0.0
    ) {
        val state = uiState.value
        val perms = state.effectivePermissions
        val isOneTime = product.id.startsWith("onetime_")
        val catalogProduct = if (!isOneTime) state.products.find { it.id == product.id } else null
        val effectivePrixPerM2 = if (!perms.canModifyPrices && catalogProduct != null) {
            catalogProduct.prix
        } else {
            prixPerM2
        }
        val effectivePrixPiece = if (!perms.canModifyPrices && catalogProduct != null) {
            if (pieces > 0) {
                if (catalogProduct.prixPiece > 0.0) catalogProduct.prixPiece
                else kotlin.math.round(catalogProduct.prix * (catalogProduct.m2Carton / catalogProduct.pcs.coerceAtLeast(1)))
            } else 0.0
        } else {
            prixPiece
        }
        viewModelScope.launch {
            val currentCart = uiState.value.cart.toMutableList()
            val (calcM2, calcTotal) = TileCalculatorMath.calculateCartItemTotals(
                cartons = cartons,
                pieces = pieces,
                m2PerCarton = product.m2Carton,
                pcsPerCarton = product.pcs,
                prixPerM2 = effectivePrixPerM2,
                prixParPiece = effectivePrixPiece
            )
            val cleanM2 = if (calcM2 > 0) calcM2 else round2(m2Total)
            if (editingItemId != null) {
                val idx = currentCart.indexOfFirst { it.id == editingItemId }
                if (idx >= 0) {
                    currentCart[idx] = currentCart[idx].copy(
                        name = product.name,
                        usine = product.usine,
                        dimension = product.dimension,
                        m2Carton = product.m2Carton,
                        pcs = product.pcs,
                        cartons = cartons,
                        pieces = pieces,
                        m2Total = cleanM2,
                        prixUnitaire = effectivePrixPerM2,
                        prixPiece = effectivePrixPiece,
                        sousTotal = calcTotal,
                        bain = bain,
                        calibre = calibre,
                        pricingUnit = product.pricingUnit
                    )
                }
            } else {
                currentCart.add(
                    CartItem(
                        id = "line_${UUID.randomUUID().toString().take(8)}",
                        productId = product.id,
                        name = product.name,
                        usine = product.usine,
                        dimension = product.dimension,
                        m2Carton = product.m2Carton,
                        pcs = product.pcs,
                        prixUnitaire = effectivePrixPerM2,
                        prixAchat = product.prixAchat,
                        cartons = cartons,
                        pieces = pieces,
                        m2Total = cleanM2,
                        sousTotal = calcTotal,
                        bain = bain,
                        calibre = calibre,
                        color = product.color,
                        colorName = product.colorName,
                        image = product.image,
                        prixPiece = effectivePrixPiece,
                        pricingUnit = product.pricingUnit
                    )
                )
            }
            repository.saveCart(currentCart)
            navState.value = navState.value.copy(calculatorTarget = null)
        }
    }

    fun updateCartItemDirect(
        itemId: String,
        name: String,
        cartons: Int,
        pieces: Int,
        m2Carton: Double,
        pcs: Int,
        prixUnitaire: Double,
        prixPiece: Double = 0.0
    ) {
        val perms = uiState.value.effectivePermissions
        viewModelScope.launch {
            val safeM2Carton = m2Carton.coerceAtLeast(0.01)
            val safePcs = pcs.coerceAtLeast(1)
            val cleanCartons = cartons.coerceAtLeast(0)
            val cleanPieces = pieces.coerceAtLeast(0)
            val cleanPrix = prixUnitaire.coerceAtLeast(0.0)
            val cleanPrixPiece = prixPiece.coerceAtLeast(0.0)
            val currentCart = uiState.value.cart.mapNotNull { item ->
                if (item.id != itemId) item
                else if (cleanCartons == 0 && cleanPieces == 0) null
                else {
                    val isOneTime = item.productId.startsWith("onetime_")
                    val finalPrix = if (!perms.canModifyPrices && !isOneTime) item.prixUnitaire else cleanPrix
                    val finalPrixPiece = if (!perms.canModifyPrices && !isOneTime) item.prixPiece else cleanPrixPiece
                    val (nextM2, nextTotal) = TileCalculatorMath.calculateCartItemTotals(
                        cartons = cleanCartons,
                        pieces = cleanPieces,
                        m2PerCarton = safeM2Carton,
                        pcsPerCarton = safePcs,
                        prixPerM2 = finalPrix,
                        prixParPiece = finalPrixPiece
                    )
                    item.copy(
                        name = name.ifBlank { item.name },
                        m2Carton = safeM2Carton,
                        pcs = safePcs,
                        cartons = cleanCartons,
                        pieces = cleanPieces,
                        m2Total = nextM2,
                        prixUnitaire = finalPrix,
                        prixPiece = finalPrixPiece,
                        sousTotal = nextTotal
                    )
                }
            }
            repository.saveCart(currentCart)
        }
    }

    fun adjustCartItemCartons(itemId: String, delta: Int) {
        viewModelScope.launch {
            val currentCart = uiState.value.cart.mapNotNull { item ->
                if (item.id != itemId) item
                else {
                    val nextCartons = (item.cartons + delta).coerceAtLeast(0)
                    if (nextCartons == 0 && item.pieces == 0) null
                    else {
                        val (nextM2, nextTotal) = TileCalculatorMath.calculateCartItemTotals(
                            cartons = nextCartons,
                            pieces = item.pieces,
                            m2PerCarton = item.m2Carton,
                            pcsPerCarton = item.pcs,
                            prixPerM2 = item.prixUnitaire,
                            prixParPiece = item.prixPiece
                        )
                        item.copy(
                            cartons = nextCartons,
                            m2Total = nextM2,
                            sousTotal = nextTotal
                        )
                    }
                }
            }
            repository.saveCart(currentCart)
        }
    }

    fun removeCartItem(itemId: String) {
        viewModelScope.launch {
            repository.saveCart(uiState.value.cart.filterNot { it.id == itemId })
        }
    }

    fun addFreeLine(line: FreeLineItem) {
        viewModelScope.launch {
            repository.saveFreeLines(uiState.value.freeLines + line)
            navState.value = navState.value.copy(calculatorTarget = null)
        }
    }

    fun updateFreeLine(updatedLine: FreeLineItem) {
        viewModelScope.launch {
            val cleanQty = updatedLine.quantite.coerceAtLeast(0.0)
            val cleanPrix = updatedLine.prixUnitaire.coerceAtLeast(0.0)
            val nextLines = uiState.value.freeLines.map { existing ->
                if (existing.id == updatedLine.id) {
                    updatedLine.copy(
                        quantite = cleanQty,
                        prixUnitaire = cleanPrix,
                        sousTotal = round2(cleanQty * cleanPrix)
                    )
                } else {
                    existing
                }
            }
            repository.saveFreeLines(nextLines)
        }
    }

    fun adjustFreeLineQuantity(lineId: String, delta: Double) {
        viewModelScope.launch {
            val nextLines = uiState.value.freeLines.mapNotNull { line ->
                if (line.id != lineId) line
                else {
                    val nextQty = round2((line.quantite + delta).coerceAtLeast(0.0))
                    if (nextQty <= 0.0) null
                    else line.copy(
                        quantite = nextQty,
                        sousTotal = round2(nextQty * line.prixUnitaire)
                    )
                }
            }
            repository.saveFreeLines(nextLines)
        }
    }

    fun removeFreeLine(lineId: String) {
        viewModelScope.launch {
            repository.saveFreeLines(uiState.value.freeLines.filterNot { it.id == lineId })
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            repository.clearCartAndFreeLines()
            navState.value = navState.value.copy(editingInvoice = null)
        }
    }

    // Checkout & Invoices
    fun finalizeSale(
        client: InvoiceClient,
        remise: Double,
        montantPaye: Double,
        modePaiement: String,
        notes: String
    ) {
        val state = uiState.value
        val articles = state.cart
        val freeLines = state.freeLines
        if (articles.isEmpty() && freeLines.isEmpty()) return

        val perms = state.effectivePermissions
        val editing = state.editingInvoice
        val totalBrut = round2(articles.sumOf { it.sousTotal } + freeLines.sumOf { it.sousTotal })
        val requestedRemise = if (perms.canModifyPrices) remise else (editing?.remise ?: 0.0)
        val cleanRemise = requestedRemise.coerceIn(0.0, totalBrut)
        val totalNet = round2((totalBrut - cleanRemise).coerceAtLeast(0.0))
        val cleanPaye = if (modePaiement == "Crédit") 0.0 else montantPaye.coerceIn(0.0, totalNet)
        val reste = round2((totalNet - cleanPaye).coerceAtLeast(0.0))
        val statut = when {
            reste <= 0.01 -> "Payé"
            cleanPaye > 0.0 -> "Partiel"
            else -> "Crédit"
        }
        val sellerName = state.settings.deviceSellerName.trim().ifBlank { state.currentUser?.name ?: "Vendeur" }
        val sellerPhone = state.settings.deviceSellerPhone.trim().ifBlank { state.currentUser?.phone ?: "" }
        val invoiceMagasin = if (state.settings.activeStore != PosSeeds.DEFAULT_SETTINGS.activeStore) {
            state.settings.activeStore
        } else {
            state.currentUser?.magasin?.takeIf { it.isNotBlank() } ?: state.settings.activeStore
        }
        val invoice = if (editing != null) {
            editing.copy(
                vendeur = editing.vendeur.ifBlank { sellerName },
                vendeurTel = editing.vendeurTel.ifBlank { sellerPhone },
                client = client,
                articles = articles,
                lignesLibres = freeLines,
                totalBrut = totalBrut,
                remise = cleanRemise,
                totalNet = totalNet,
                montantPaye = cleanPaye,
                resteAPayer = reste,
                modePaiement = modePaiement,
                statut = statut,
                notes = notes
            )
        } else {
            val dateCode = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
            val seq = (state.invoices.size + 1).toString().padStart(3, '0')
            Invoice(
                id = "inv_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                reference = "CRM-$dateCode-$seq",
                date = nowIso(),
                vendeur = sellerName,
                magasin = invoiceMagasin,
                client = client,
                articles = articles,
                lignesLibres = freeLines,
                totalBrut = totalBrut,
                remise = cleanRemise,
                totalNet = totalNet,
                montantPaye = cleanPaye,
                resteAPayer = reste,
                modePaiement = modePaiement,
                statut = statut,
                notes = notes,
                vendeurTel = sellerPhone,
                hidePrices = false,
                whatsappSentBeforePrint = false
            )
        }

        val fallbackById = state.products.associateBy { it.id }
        viewModelScope.launch {
            val actorName = state.currentUser?.name ?: sellerName
            val commit = if (editing != null) {
                repository.updateExistingInvoice(editing, invoice, actorName)
            } else {
                repository.saveNewInvoice(invoice)
            }
            val createdIds = listOf(commit.notification.id) + commit.extraNotifications.map { it.id }
            appContext?.let { SaleSystemNotifier.recordLocalNotificationIds(it, createdIds) }
            navState.update {
                it.copy(
                    editingInvoice = null,
                    previewInvoice = invoice,
                    statusMessage = if (editing != null) "Facture #${invoice.reference} modifiée & Direction alertée" else "Facture #${invoice.reference} enregistrée"
                )
            }
            launchCloudSync(OperationType.WRITE, "invoices/${commit.invoice.id}") { remote, uid ->
                remote.commitSaleBatch(
                    userId = uid,
                    invoice = commit.invoice,
                    stockDeltas = commit.stockDeltas,
                    updatedProductStocks = commit.updatedProductStocks,
                    caisseMovement = commit.caisseMovement,
                    updatedCustomer = commit.updatedCustomer,
                    notifications = listOf(commit.notification) + commit.extraNotifications,
                    fallbackProductsById = fallbackById,
                    isEdit = editing != null
                )
            }
        }
    }

    fun startEditingInvoice(invoice: Invoice) {
        if (!uiState.value.effectivePermissions.canEditInvoices) {
            showMessage("Modification facture non autorisée")
            return
        }
        viewModelScope.launch {
            repository.saveCart(invoice.articles)
            repository.saveFreeLines(invoice.lignesLibres)
            navState.value = navState.value.copy(
                editingInvoice = invoice,
                activeTab = "pos",
                statusMessage = "Modification de #${invoice.reference}"
            )
        }
    }

    fun deleteInvoice(invoiceId: String) {
        if (!uiState.value.effectivePermissions.canDeleteInvoices) {
            showMessage("Suppression facture refusée : permission requise")
            return
        }
        val actor = uiState.value.currentUser?.name ?: "Admin"
        viewModelScope.launch {
            val commit = repository.deleteInvoiceWithReconcile(invoiceId, actor)
            commit.notification?.let { n ->
                appContext?.let { SaleSystemNotifier.recordLocalNotificationIds(it, listOf(n.id)) }
            }
            showMessage("Facture supprimée & stock restitué")
            launchCloudSync(OperationType.DELETE, "invoices/$invoiceId") { remote, uid ->
                remote.commitDeleteInvoiceBatch(
                    userId = uid,
                    invoiceId = invoiceId,
                    restoredStockDeltas = commit.restoredStockDeltas,
                    updatedCustomer = commit.updatedCustomer,
                    notification = commit.notification
                )
            }
        }
    }

    // Stock & Dispo Dépôt
    fun requestDepotAvailability(product: TileProduct) {
        val state = uiState.value
        val actor = state.currentUser?.name ?: "Vendeur"
        val site = state.settings.activeStore
        viewModelScope.launch {
            val notif = repository.recordDepotDispoRequest(product, actor, site)
            appContext?.let { SaleSystemNotifier.recordLocalNotificationIds(it, listOf(notif.id)) }
            showMessage("Dispo Dépôt envoyée (${product.name})")
            launchCloudSync(OperationType.CREATE, "notifications/${notif.id}") { remote, uid ->
                remote.createNotification(uid, notif)
            }
        }
    }

    fun adjustStock(productId: String, delta: Int) {
        val state = uiState.value
        if (!state.effectivePermissions.canModifyStock) {
            showMessage("Stock verrouillé en lecture seule pour ce profil")
            return
        }
        val actor = state.currentUser?.name ?: "Admin"
        val site = state.settings.activeStore
        val before = state.products.find { it.id == productId }
        viewModelScope.launch {
            val newStock = repository.adjustProductStock(productId, delta)
            if (newStock != null) {
                val updatedProd = before?.copy(stock = newStock)
                val stockNotif = updatedProd?.let {
                    repository.recordStockNotification(
                        product = it,
                        detail = "Stock modifié par $actor (${if (delta >= 0) "+$delta" else "$delta"} ctn) -> Nouveau stock: $newStock ctn",
                        actorName = actor,
                        site = site,
                        isAlert = newStock <= 5
                    )
                }
                stockNotif?.let { n ->
                    appContext?.let { SaleSystemNotifier.recordLocalNotificationIds(it, listOf(n.id)) }
                }
                launchCloudSync(OperationType.UPDATE, "products/$productId") { remote, uid ->
                    remote.adjustProductStockDelta(uid, productId, delta, updatedProd)
                    stockNotif?.let { remote.createNotification(uid, it) }
                }
            }
        }
    }

    fun setStock(productId: String, stock: Int) {
        val state = uiState.value
        if (!state.effectivePermissions.canModifyStock) {
            showMessage("Stock verrouillé en lecture seule pour ce profil")
            return
        }
        val actor = state.currentUser?.name ?: "Admin"
        val site = state.settings.activeStore
        val before = state.products.find { it.id == productId }
        viewModelScope.launch {
            val newStock = repository.setProductStock(productId, stock)
            val stockNotif = before?.copy(stock = newStock)?.let {
                repository.recordStockNotification(
                    product = it,
                    detail = "Inventaire ajusté par $actor (${before.stock} -> $newStock ctn)",
                    actorName = actor,
                    site = site,
                    isAlert = newStock <= 5
                )
            }
            stockNotif?.let { n ->
                appContext?.let { SaleSystemNotifier.recordLocalNotificationIds(it, listOf(n.id)) }
            }
            launchCloudSync(OperationType.UPDATE, "products/$productId") { remote, uid ->
                remote.updateProductStock(uid, productId, newStock)
                stockNotif?.let { remote.createNotification(uid, it) }
            }
        }
    }

    fun saveProduct(product: TileProduct) {
        val state = uiState.value
        if (!state.effectivePermissions.canModifyStock) {
            showMessage("Modification catalogue et tarifs réservée à l'Admin / Manager")
            return
        }
        val actor = state.currentUser?.name ?: "Admin"
        val site = state.settings.activeStore
        val existed = state.products.any { it.id == product.id }
        viewModelScope.launch {
            repository.upsertProduct(product)
            val stockNotif = repository.recordStockNotification(
                product = product,
                detail = "${if (existed) "Article modifié" else "Nouvel article"} par $actor — Prix ${product.prix.toInt()} DA/m², Stock ${product.stock} ctn",
                actorName = actor,
                site = site,
                isAlert = product.stock <= 5
            )
            appContext?.let { SaleSystemNotifier.recordLocalNotificationIds(it, listOf(stockNotif.id)) }
            showMessage("Fiche produit enregistrée")
            launchCloudSync(OperationType.WRITE, "products/${product.id}") { remote, uid ->
                remote.upsertProduct(uid, product)
                remote.createNotification(uid, stockNotif)
            }
        }
    }

    // Customers
    fun saveCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.upsertCustomer(customer)
            showMessage("Client enregistré")
            launchCloudSync(OperationType.WRITE, "customers/${customer.id}") { remote, uid ->
                remote.upsertCustomer(uid, customer)
            }
        }
    }

    fun payCustomerDebt(customerId: String, amount: Double) {
        val author = uiState.value.currentUser?.name ?: "Gérant"
        viewModelScope.launch {
            val res = repository.payCustomerDebt(customerId, amount, author)
            showMessage("Remboursement encaissé en caisse")
            if (res != null) {
                val (cust, mov) = res
                launchCloudSync(OperationType.WRITE, "customers/$customerId") { remote, uid ->
                    remote.upsertCustomer(uid, cust)
                    remote.createCaisseMovement(uid, mov)
                }
            }
        }
    }

    // Suppliers
    fun saveSupplier(supplier: Supplier) {
        viewModelScope.launch {
            repository.upsertSupplier(supplier)
            showMessage("Fournisseur enregistré")
            launchCloudSync(OperationType.WRITE, "suppliers/${supplier.id}") { remote, uid ->
                remote.upsertSupplier(uid, supplier)
            }
        }
    }

    fun recordSupplierVersement(supplierId: String, amount: Double, motif: String, ref: String?) {
        val author = uiState.value.currentUser?.name ?: "Gérant"
        viewModelScope.launch {
            val res = repository.recordSupplierVersement(supplierId, amount, motif, ref, author)
            showMessage("Versement usine validé & déduit de la caisse")
            if (res != null) {
                val (sup, supMov, caisseMov) = res
                launchCloudSync(OperationType.WRITE, "suppliers/$supplierId") { remote, uid ->
                    remote.upsertSupplier(uid, sup)
                    remote.createSupplierMovement(uid, supMov)
                    remote.createCaisseMovement(uid, caisseMov)
                }
            }
        }
    }

    fun recordSupplierInvoice(supplierId: String, amount: Double, motif: String, ref: String?) {
        val author = uiState.value.currentUser?.name ?: "Gérant"
        viewModelScope.launch {
            val res = repository.recordSupplierInvoice(supplierId, amount, motif, ref, author)
            showMessage("Facture réception ajoutée à la dette usine")
            if (res != null) {
                val (sup, supMov) = res
                launchCloudSync(OperationType.WRITE, "suppliers/$supplierId") { remote, uid ->
                    remote.upsertSupplier(uid, sup)
                    remote.createSupplierMovement(uid, supMov)
                }
            }
        }
    }

    // Caisse
    fun addCaisseMovement(type: String, motif: String, montant: Double) {
        val author = uiState.value.currentUser?.name ?: "Gérant"
        val mov = CaisseMovement(
            id = "caisse_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
            date = nowIso(),
            type = type,
            motif = motif,
            montant = montant,
            auteur = author
        )
        viewModelScope.launch {
            repository.addCaisseMovement(mov)
            showMessage(if (type == "entree") "Entrée de caisse ajoutée" else "Dépense / Sortie enregistrée")
            launchCloudSync(OperationType.CREATE, "caisse_movements/${mov.id}") { remote, uid ->
                remote.createCaisseMovement(uid, mov)
            }
        }
    }

    fun saveDailyCaisseCount(
        fondOuverture: Double,
        ventesEspecesJour: Double,
        entreesDiversJour: Double,
        sortiesJour: Double,
        theorique: Double,
        compteReel: Double,
        notes: String
    ) {
        val state = uiState.value
        val count = DailyCaisseCount(
            id = "count_${System.currentTimeMillis()}",
            date = nowIso(),
            magasin = state.settings.activeStore,
            auteur = state.currentUser?.name ?: "Gérant",
            fondOuverture = fondOuverture,
            ventesEspecesJour = ventesEspecesJour,
            entreesDiversJour = entreesDiversJour,
            sortiesJour = sortiesJour,
            theorique = theorique,
            compteReel = compteReel,
            ecart = round2(compteReel - theorique),
            notes = notes
        )
        viewModelScope.launch {
            repository.saveDailyCaisseCount(count)
            showMessage("Pointage caisse fin de journée sauvegardé")
            launchCloudSync(OperationType.CREATE, "daily_caisse_counts/${count.id}") { remote, uid ->
                remote.createDailyCaisseCount(uid, count)
            }
        }
    }

    // Users & Role Policies
    fun saveUser(user: StaffUser) {
        if (!uiState.value.effectivePermissions.canManageUsers) {
            showMessage("Gestion des comptes réservée à l'Admin")
            return
        }
        val normalizedRole = if (user.role in PosSeeds.VALID_ROLES) user.role else "vendeur"
        val cleanUser = if (normalizedRole == "admin") {
            user.copy(
                role = "admin",
                allowedTabs = PosSeeds.ALL_TABS,
                canEditInvoices = true,
                canDeleteInvoices = true,
                canModifyStock = true,
                canViewSuppliers = true,
                canViewCaisse = true,
                canManageUsers = true,
                canViewPrixAchat = true,
                canModifyPrices = true
            )
        } else {
            user.copy(role = normalizedRole)
        }
        viewModelScope.launch {
            repository.upsertUser(cleanUser)
            showMessage("Profil ${cleanUser.name} enregistré")
            launchCloudSync(OperationType.WRITE, "staff_users/${cleanUser.id}") { remote, uid ->
                remote.upsertStaffUser(uid, cleanUser)
            }
        }
    }

    fun deleteUser(userId: String) {
        if (!uiState.value.effectivePermissions.canManageUsers) {
            showMessage("Suppression de profil réservée à l'Admin")
            return
        }
        viewModelScope.launch {
            repository.deleteUser(userId)
            showMessage("Profil supprimé")
            launchCloudSync(OperationType.DELETE, "staff_users/$userId") { remote, uid ->
                remote.deleteStaffUser(uid, userId)
            }
        }
    }

    fun saveRolePolicy(policy: RolePolicy) {
        if (!uiState.value.effectivePermissions.canManageUsers) {
            showMessage("Configuration des rôles réservée à l'Admin")
            return
        }
        val roleId = if (policy.roleId in PosSeeds.VALID_ROLES) policy.roleId else return
        val sanitized = if (roleId == "admin") {
            PosSeeds.defaultPolicyForRole("admin")
        } else {
            val cleanTabs = (policy.allowedTabs.filter { it in PosSeeds.ALL_TABS } + listOf("pos", "calculator")).distinct()
            policy.copy(roleId = roleId, allowedTabs = cleanTabs)
        }
        viewModelScope.launch {
            repository.upsertRolePolicy(sanitized)
            showMessage("Permissions rôle « ${sanitized.displayName} » enregistrées")
            launchCloudSync(OperationType.WRITE, "role_policies/${sanitized.roleId}") { remote, uid ->
                remote.upsertRolePolicy(uid, sanitized)
            }
        }
    }

    fun saveStoreInfo(info: StoreInfo) {
        viewModelScope.launch {
            repository.saveStoreInfo(info)
            showMessage("Coordonnées magasin sauvegardées")
            launchCloudSync(OperationType.WRITE, "store_info/default") { remote, uid ->
                remote.saveStoreInfo(uid, info)
            }
        }
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
            launchCloudSync(OperationType.WRITE, "app_settings/default") { remote, uid ->
                remote.saveAppSettings(uid, settings)
            }
        }
    }

    fun setAutoEmailPdf(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoEmailPdf(enabled)
            showMessage(
                if (enabled) "Envoi PDF Gmail auto activé" else "Envoi PDF Gmail auto désactivé"
            )
        }
    }

    fun setAutoWhatsappPhoto(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoWhatsappPhoto(enabled)
            showMessage(
                if (enabled) "Envoi Photo WhatsApp Bureau auto activé" else "Envoi Photo WhatsApp Bureau auto désactivé"
            )
        }
    }

    fun resetCatalog() {
        if (!uiState.value.effectivePermissions.canModifyStock) {
            showMessage("Réinitialisation du stock refusée")
            return
        }
        viewModelScope.launch {
            val seeded = repository.resetCatalogToDefault()
            showMessage("Catalogue usines réinitialisé")
            launchCloudSync(OperationType.WRITE, "products") { remote, uid ->
                remote.resetProductsCatalog(uid, seeded)
            }
        }
    }

    fun exportAllDataJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportAllDataJson()
            onResult(json)
        }
    }

    fun importAllDataJson(json: String) {
        viewModelScope.launch {
            val ok = repository.importAllDataJson(json)
            showMessage(if (ok) "Sauvegarde restaurée avec succès" else "Erreur : fichier JSON invalide")
            if (ok) {
                launchCloudSync(OperationType.WRITE, "") { remote, uid ->
                    repository.getProductsSnapshot().let { remote.resetProductsCatalog(uid, it) }
                    repository.getCustomersSnapshot().forEach { remote.upsertCustomer(uid, it) }
                    repository.getSuppliersSnapshot().forEach { remote.upsertSupplier(uid, it) }
                    repository.getRolePoliciesSnapshot().forEach { remote.upsertRolePolicy(uid, it) }
                    repository.getUsersSnapshot().forEach { remote.upsertStaffUser(uid, it) }
                    remote.saveStoreInfo(uid, repository.getStoreInfoSnapshot())
                    remote.saveAppSettings(uid, repository.getSettingsSnapshot())
                }
            }
        }
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private data class CoreBundle(
        val products: List<TileProduct>,
        val invoices: List<Invoice>,
        val customers: List<Customer>,
        val suppliers: List<Supplier>,
        val supMoves: List<SupplierMovement>
    )

    private data class OpsBundle(
        val caisse: List<CaisseMovement>,
        val dailyCounts: List<DailyCaisseCount>,
        val users: List<StaffUser>,
        val notifications: List<DepotNotification>,
        val currentUserId: String?
    )

    private data class SessionBundle(
        val cart: List<CartItem>,
        val freeLines: List<FreeLineItem>,
        val storeInfo: StoreInfo,
        val settings: AppSettings,
        val autoEmailPdf: Boolean,
        val autoWhatsappPhoto: Boolean
    )

    private companion object {
        const val TAG = "PosVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
