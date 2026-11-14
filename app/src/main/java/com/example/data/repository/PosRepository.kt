package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.AppKvEntity
import com.example.data.local.PosJsonMapper
import com.example.data.local.PosJsonMapper.toEntity
import com.example.data.local.PosJsonMapper.toModel
import com.example.data.model.AppSettings
import com.example.data.model.CaisseMovement
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.DailyCaisseCount
import com.example.data.model.DepotNotification
import com.example.data.model.FreeLineItem
import com.example.data.model.Invoice
import com.example.data.model.PosSeeds
import com.example.data.model.RolePolicy
import com.example.data.model.StaffUser
import com.example.data.model.StoreInfo
import com.example.data.model.Supplier
import com.example.data.model.SupplierMovement
import com.example.data.model.TileProduct
import com.example.data.model.formatM2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SaleCommitResult(
    val invoice: Invoice,
    val caisseMovement: CaisseMovement?,
    val updatedCustomer: Customer?,
    val notification: DepotNotification,
    val updatedProductStocks: Map<String, Int>,
    val extraNotifications: List<DepotNotification> = emptyList(),
    val stockDeltas: Map<String, Int> = emptyMap()
)

data class DeleteInvoiceCommitResult(
    val restoredProductStocks: Map<String, Int>,
    val restoredStockDeltas: Map<String, Int>,
    val updatedCustomer: Customer?,
    val notification: DepotNotification?
)

class PosRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val dao = db.posDao()

    companion object {
        private const val KV_CART = "active_cart"
        private const val KV_FREE_LINES = "free_lines"
        private const val KV_STORE_INFO = "store_info"
        private const val KV_SETTINGS = "app_settings"
        private const val KV_CURRENT_USER_ID = "current_user_id"
        private const val KV_AUTO_EMAIL_PDF = "auto_email_pdf"
        private const val KV_PENDING_OFFLINE_SYNC = "pending_offline_sync"
    }

    val productsFlow: Flow<List<TileProduct>> =
        dao.observeProducts().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val invoicesFlow: Flow<List<Invoice>> =
        dao.observeInvoices().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val customersFlow: Flow<List<Customer>> =
        dao.observeCustomers().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val suppliersFlow: Flow<List<Supplier>> =
        dao.observeSuppliers().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val supplierMovementsFlow: Flow<List<SupplierMovement>> =
        dao.observeSupplierMovements().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val caisseFlow: Flow<List<CaisseMovement>> =
        dao.observeCaisseMovements().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val dailyCaisseCountsFlow: Flow<List<DailyCaisseCount>> =
        dao.observeDailyCaisseCounts().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val usersFlow: Flow<List<StaffUser>> =
        dao.observeUsers().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val rolePoliciesFlow: Flow<List<RolePolicy>> =
        dao.observeRolePolicies().map { list ->
            list.map { it.toModel() }.sortedBy { policy ->
                when (policy.roleId) {
                    "admin" -> 0
                    "gerant" -> 1
                    else -> 2
                }
            }
        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    val notificationsFlow: Flow<List<DepotNotification>> =
        dao.observeNotifications().map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val cartFlow: Flow<List<CartItem>> =
        dao.observeKv(KV_CART).map { kv ->
            kv?.let { PosJsonMapper.jsonToCartItems(it.jsonValue) } ?: emptyList()
        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    val freeLinesFlow: Flow<List<FreeLineItem>> =
        dao.observeKv(KV_FREE_LINES).map { kv ->
            kv?.let { PosJsonMapper.jsonToFreeLines(it.jsonValue) } ?: emptyList()
        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    val storeInfoFlow: Flow<StoreInfo> =
        dao.observeKv(KV_STORE_INFO).map { kv ->
            kv?.let { PosJsonMapper.jsonToStoreInfo(it.jsonValue, PosSeeds.DEFAULT_STORE_INFO) }
                ?: PosSeeds.DEFAULT_STORE_INFO
        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    val settingsFlow: Flow<AppSettings> =
        dao.observeKv(KV_SETTINGS).map { kv ->
            kv?.let { PosJsonMapper.jsonToSettings(it.jsonValue, PosSeeds.DEFAULT_SETTINGS) }
                ?: PosSeeds.DEFAULT_SETTINGS
        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    val currentUserIdFlow: Flow<String?> =
        dao.observeKv(KV_CURRENT_USER_ID).map { it?.jsonValue }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    val autoEmailPdfFlow: Flow<Boolean> =
        dao.observeKv(KV_AUTO_EMAIL_PDF).map { it?.jsonValue == "true" }
            .flowOn(Dispatchers.Default).distinctUntilChanged()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.countProducts() == 0) {
            val seededProducts = loadCatalogFromAssets()
            if (seededProducts.isNotEmpty()) {
                dao.insertProducts(seededProducts.map { it.toEntity() })
            }
        }
        if (dao.countUsers() == 0) {
            dao.insertUsers(PosSeeds.DEFAULT_USERS.map { it.toEntity() })
        }
        if (dao.countRolePolicies() == 0) {
            dao.insertRolePolicies(PosSeeds.DEFAULT_ROLE_POLICIES.map { it.toEntity() })
        }
        if (dao.countCustomers() == 0) {
            dao.insertCustomers(PosSeeds.DEFAULT_CUSTOMERS.map { it.toEntity() })
        }
        if (dao.countSuppliers() == 0) {
            dao.insertSuppliers(PosSeeds.DEFAULT_SUPPLIERS.map { it.toEntity() })
        }
        if (dao.countCaisse() == 0) {
            dao.insertCaisseMovement(
                CaisseMovement(
                    id = "c_init",
                    date = nowIso(),
                    type = "entree",
                    motif = "Fond de caisse ouverture",
                    montant = 50000.0,
                    auteur = "Walid"
                ).toEntity()
            )
        }
        if (dao.getKv(KV_STORE_INFO) == null) {
            dao.putKv(AppKvEntity(KV_STORE_INFO, PosJsonMapper.storeInfoToJson(PosSeeds.DEFAULT_STORE_INFO)))
        }
        if (dao.getKv(KV_SETTINGS) == null) {
            dao.putKv(AppKvEntity(KV_SETTINGS, PosJsonMapper.settingsToJson(PosSeeds.DEFAULT_SETTINGS)))
        }
    }

    private fun loadCatalogFromAssets(): List<TileProduct> {
        return runCatching {
            val raw = context.assets.open("catalog_seed.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    val usine = o.optString("usine")
                    val dimension = o.optString("dimension")
                    val choix = o.optString("choix", "1er Choix")
                    val desc = o.optString("description").ifBlank {
                        "$name - Qualité garantie usine $usine, $dimension, $choix."
                    }
                    add(
                        TileProduct(
                            id = o.optString("id"),
                            name = name,
                            usine = usine,
                            dimension = dimension,
                            m2Carton = o.optDouble("m2_carton", 1.0),
                            pcs = o.optInt("pcs", 1),
                            prix = o.optDouble("prix", 0.0),
                            prixAchat = o.optDouble("prixAchat", 0.0),
                            stock = o.optInt("stock", 0),
                            color = o.optString("color", "#94a3b8"),
                            colorName = o.optString("colorName", "Gris Minéral"),
                            image = o.optString("image", ""),
                            finition = o.optString("finition", "brillant"),
                            choix = choix,
                            categorie = o.optString("categorie", "sol"),
                            description = desc,
                            pricingUnit = o.optString("pricingUnit", "carton").ifBlank { "carton" },
                            prixPiece = o.optDouble("prixPiece", 0.0)
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    // Cloud Snapshot & Sync Helpers
    suspend fun getProductsSnapshot(): List<TileProduct> = withContext(Dispatchers.IO) {
        dao.getProductsSnapshot().map { it.toModel() }
    }
    suspend fun getUsersSnapshot(): List<StaffUser> = withContext(Dispatchers.IO) {
        dao.getUsersSnapshot().map { it.toModel() }
    }
    suspend fun getRolePoliciesSnapshot(): List<RolePolicy> = withContext(Dispatchers.IO) {
        dao.getRolePoliciesSnapshot().map { it.toModel() }
    }
    suspend fun getCustomersSnapshot(): List<Customer> = withContext(Dispatchers.IO) {
        dao.getCustomersSnapshot().map { it.toModel() }
    }
    suspend fun getSuppliersSnapshot(): List<Supplier> = withContext(Dispatchers.IO) {
        dao.getSuppliersSnapshot().map { it.toModel() }
    }
    suspend fun getCaisseSnapshot(): List<CaisseMovement> = withContext(Dispatchers.IO) {
        dao.getCaisseSnapshot().map { it.toModel() }
    }
    suspend fun getInvoicesSnapshot(): List<Invoice> = withContext(Dispatchers.IO) {
        dao.getInvoicesSnapshot().map { it.toModel() }
    }
    suspend fun getSupplierMovementsSnapshot(): List<SupplierMovement> = withContext(Dispatchers.IO) {
        dao.getSupplierMovementsSnapshot().map { it.toModel() }
    }
    suspend fun getDailyCaisseCountsSnapshot(): List<DailyCaisseCount> = withContext(Dispatchers.IO) {
        dao.getDailyCaisseCountsSnapshot().map { it.toModel() }
    }
    suspend fun getNotificationsSnapshot(): List<DepotNotification> = withContext(Dispatchers.IO) {
        dao.getNotificationsSnapshot().map { it.toModel() }
    }
    suspend fun getStoreInfoSnapshot(): StoreInfo = withContext(Dispatchers.IO) {
        dao.getKv(KV_STORE_INFO)?.let {
            PosJsonMapper.jsonToStoreInfo(it.jsonValue, PosSeeds.DEFAULT_STORE_INFO)
        } ?: PosSeeds.DEFAULT_STORE_INFO
    }
    suspend fun getSettingsSnapshot(): AppSettings = withContext(Dispatchers.IO) {
        dao.getKv(KV_SETTINGS)?.let {
            PosJsonMapper.jsonToSettings(it.jsonValue, PosSeeds.DEFAULT_SETTINGS)
        } ?: PosSeeds.DEFAULT_SETTINGS
    }

    suspend fun getUnreadNotificationIds(): List<String> = withContext(Dispatchers.IO) {
        dao.getNotificationsSnapshot().filter { !it.read }.map { it.id }
    }

    suspend fun replaceProductsFromCloud(items: List<TileProduct>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getProductsSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearProducts()
            dao.insertProducts(next)
        }
    }

    suspend fun replaceInvoicesFromCloud(items: List<Invoice>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getInvoicesSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearInvoices()
            dao.insertInvoices(next)
        }
    }

    suspend fun replaceCustomersFromCloud(items: List<Customer>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getCustomersSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearCustomers()
            dao.insertCustomers(next)
        }
    }

    suspend fun replaceSuppliersFromCloud(items: List<Supplier>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getSuppliersSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearSuppliers()
            dao.insertSuppliers(next)
        }
    }

    suspend fun replaceSupplierMovementsFromCloud(items: List<SupplierMovement>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getSupplierMovementsSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearSupplierMovements()
            dao.insertSupplierMovements(next)
        }
    }

    suspend fun replaceCaisseFromCloud(items: List<CaisseMovement>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getCaisseSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearCaisse()
            dao.insertCaisseMovements(next)
        }
    }

    suspend fun replaceDailyCaisseCountsFromCloud(items: List<DailyCaisseCount>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getDailyCaisseCountsSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearDailyCaisseCounts()
            dao.insertDailyCaisseCounts(next)
        }
    }

    suspend fun replaceUsersFromCloud(items: List<StaffUser>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getUsersSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearUsers()
            dao.insertUsers(next)
        }
    }

    suspend fun replaceRolePoliciesFromCloud(items: List<RolePolicy>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getRolePoliciesSnapshot()
        if (existing.size == next.size && existing.associateBy { it.roleId } == next.associateBy { it.roleId }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearRolePolicies()
            dao.insertRolePolicies(next)
        }
    }

    suspend fun replaceNotificationsFromCloud(items: List<DepotNotification>) = withContext(Dispatchers.IO) {
        val next = items.map { it.toEntity() }
        val existing = dao.getNotificationsSnapshot()
        if (existing.size == next.size && existing.associateBy { it.id } == next.associateBy { it.id }) {
            return@withContext
        }
        db.withTransaction {
            dao.clearNotifications()
            dao.insertNotifications(next)
        }
    }

    suspend fun resetCatalogToDefault(): List<TileProduct> = withContext(Dispatchers.IO) {
        val seeded = loadCatalogFromAssets()
        db.withTransaction {
            dao.clearProducts()
            dao.insertProducts(seeded.map { it.toEntity() })
        }
        seeded
    }

    // Cart & Free Lines
    suspend fun saveCart(items: List<CartItem>) = withContext(Dispatchers.IO) {
        dao.putKv(AppKvEntity(KV_CART, PosJsonMapper.cartItemsToJson(items)))
    }

    suspend fun saveFreeLines(lines: List<FreeLineItem>) = withContext(Dispatchers.IO) {
        dao.putKv(AppKvEntity(KV_FREE_LINES, PosJsonMapper.freeLinesToJson(lines)))
    }

    suspend fun clearCartAndFreeLines() = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.putKv(AppKvEntity(KV_CART, "[]"))
            dao.putKv(AppKvEntity(KV_FREE_LINES, "[]"))
        }
    }

    // Products
    suspend fun adjustProductStock(productId: String, delta: Int): Int? = withContext(Dispatchers.IO) {
        dao.adjustProductStock(productId, delta)
        dao.getProductsSnapshot().find { it.id == productId }?.stock
    }

    suspend fun setProductStock(productId: String, stock: Int): Int = withContext(Dispatchers.IO) {
        dao.setProductStock(productId, stock)
        stock
    }

    suspend fun upsertProduct(product: TileProduct) = withContext(Dispatchers.IO) {
        dao.upsertProduct(product.toEntity())
    }

    suspend fun deleteProduct(productId: String) = withContext(Dispatchers.IO) {
        dao.deleteProductById(productId)
    }

    // Invoices
    suspend fun saveNewInvoice(invoice: Invoice): SaleCommitResult = withContext(Dispatchers.IO) {
        var createdCaisse: CaisseMovement? = null
        var updatedCustomer: Customer? = null
        val updatedStocks = mutableMapOf<String, Int>()
        val extraNotifications = mutableListOf<DepotNotification>()
        lateinit var createdNotification: DepotNotification

        val stockDeltas = mutableMapOf<String, Int>()
        db.withTransaction {
            dao.upsertInvoice(invoice.toEntity())
            invoice.articles.forEach { item ->
                val deduct = itemStockCartons(item)
                if (!item.productId.startsWith("onetime_") && deduct > 0) {
                    dao.adjustProductStock(item.productId, -deduct)
                    stockDeltas[item.productId] = (stockDeltas[item.productId] ?: 0) - deduct
                }
            }
            val currentProducts = dao.getProductsSnapshot()
            invoice.articles.forEach { item ->
                if (!item.productId.startsWith("onetime_")) {
                    currentProducts.find { it.id == item.productId }?.let { p ->
                        updatedStocks[p.id] = p.stock
                        if (p.stock <= 5) {
                            val alertNotif = buildLowStockAlert(
                                product = p.toModel(),
                                actor = invoice.vendeur,
                                site = invoice.magasin
                            )
                            dao.insertNotification(alertNotif.toEntity())
                            extraNotifications.add(alertNotif)
                        }
                    }
                }
            }
            if (invoice.montantPaye > 0) {
                val caisse = CaisseMovement(
                    id = "caisse_${System.currentTimeMillis()}_${shortId()}",
                    date = nowIso(),
                    type = "entree",
                    motif = "Vente #${invoice.reference} (${invoice.magasin}) - ${invoice.client.nom}",
                    montant = invoice.montantPaye,
                    auteur = invoice.vendeur,
                    referenceFacture = invoice.reference
                )
                dao.insertCaisseMovement(caisse.toEntity())
                createdCaisse = caisse
            }
            val clientId = invoice.client.id
            if (!clientId.isNullOrBlank()) {
                val existing = dao.getCustomersSnapshot().find { it.id == clientId }
                if (existing != null) {
                    val unpaid = (invoice.totalNet - invoice.montantPaye).coerceAtLeast(0.0)
                    val nextCustEntity = existing.copy(
                        dette = existing.dette + unpaid,
                        totalAchats = existing.totalAchats + invoice.totalNet
                    )
                    dao.upsertCustomer(nextCustEntity)
                    updatedCustomer = nextCustEntity.toModel()
                }
            }
            val m2Total = invoice.articles.sumOf { it.m2Total }
            val cartonsTotal = invoice.articles.sumOf { it.cartons }
            val summary = invoice.articles.joinToString(", ") {
                val pcsTag = if (it.pieces > 0) "+${it.pieces}p" else ""
                "${it.cartons} ctn$pcsTag ${it.name} (${formatM2(it.m2Total)}m²)"
            }.ifBlank {
                invoice.lignesLibres.joinToString(", ") { "${it.quantite} ${it.unite} ${it.designation}" }
            }.take(980)
            val notif = DepotNotification(
                id = "notif_${System.currentTimeMillis()}_${shortId()}",
                invoiceId = sanitizeRuleId(invoice.id, "inv_${shortId()}"),
                reference = "[VENTE] ${invoice.reference}".take(60),
                date = nowIso(),
                vendeur = invoice.vendeur.take(80),
                magasin = invoice.magasin.take(80),
                client = invoice.client.nom.take(120),
                clientTel = invoice.client.tel.take(40),
                totalNet = invoice.totalNet.coerceAtLeast(0.0),
                m2Total = m2Total.coerceAtLeast(0.0),
                cartonsTotal = cartonsTotal.coerceAtLeast(0),
                articlesSummary = summary,
                read = false
            )
            dao.insertNotification(notif.toEntity())
            createdNotification = notif
            dao.putKv(AppKvEntity(KV_CART, "[]"))
            dao.putKv(AppKvEntity(KV_FREE_LINES, "[]"))
        }
        SaleCommitResult(
            invoice = invoice,
            caisseMovement = createdCaisse,
            updatedCustomer = updatedCustomer,
            notification = createdNotification,
            updatedProductStocks = updatedStocks,
            extraNotifications = extraNotifications,
            stockDeltas = stockDeltas
        )
    }

    suspend fun updateExistingInvoice(
        previousInvoice: Invoice,
        updatedInvoice: Invoice,
        editorName: String
    ): SaleCommitResult = withContext(Dispatchers.IO) {
        var caisseDiff: CaisseMovement? = null
        var updatedCustomer: Customer? = null
        val updatedStocks = mutableMapOf<String, Int>()
        val extraNotifications = mutableListOf<DepotNotification>()
        lateinit var modifNotification: DepotNotification

        val stockDeltas = mutableMapOf<String, Int>()
        db.withTransaction {
            val prevQty = mutableMapOf<String, Int>()
            previousInvoice.articles.forEach { item ->
                val qty = itemStockCartons(item)
                if (!item.productId.startsWith("onetime_") && qty > 0) {
                    prevQty[item.productId] = (prevQty[item.productId] ?: 0) + qty
                }
            }
            val nextQty = mutableMapOf<String, Int>()
            updatedInvoice.articles.forEach { item ->
                val qty = itemStockCartons(item)
                if (!item.productId.startsWith("onetime_") && qty > 0) {
                    nextQty[item.productId] = (nextQty[item.productId] ?: 0) + qty
                }
            }
            val allProdIds = (prevQty.keys + nextQty.keys).distinct()
            allProdIds.forEach { pid ->
                val delta = (prevQty[pid] ?: 0) - (nextQty[pid] ?: 0)
                if (delta != 0) {
                    dao.adjustProductStock(pid, delta)
                    stockDeltas[pid] = delta
                }
            }
            val currentProducts = dao.getProductsSnapshot()
            allProdIds.forEach { pid ->
                currentProducts.find { it.id == pid }?.let { p ->
                    updatedStocks[p.id] = p.stock
                    if (p.stock <= 5 && ((prevQty[pid] ?: 0) < (nextQty[pid] ?: 0))) {
                        val alertNotif = buildLowStockAlert(
                            product = p.toModel(),
                            actor = editorName,
                            site = updatedInvoice.magasin
                        )
                        dao.insertNotification(alertNotif.toEntity())
                        extraNotifications.add(alertNotif)
                    }
                }
            }

            val payDelta = updatedInvoice.montantPaye - previousInvoice.montantPaye
            if (kotlin.math.abs(payDelta) >= 1.0) {
                val mov = CaisseMovement(
                    id = "caisse_${System.currentTimeMillis()}_${shortId()}",
                    date = nowIso(),
                    type = if (payDelta > 0) "entree" else "sortie",
                    motif = "Ajustement facture #${updatedInvoice.reference} par $editorName",
                    montant = kotlin.math.abs(payDelta),
                    auteur = editorName,
                    referenceFacture = updatedInvoice.reference
                )
                dao.insertCaisseMovement(mov.toEntity())
                caisseDiff = mov
            }

            val custId = updatedInvoice.client.id
            if (!custId.isNullOrBlank() && custId == previousInvoice.client.id) {
                val existing = dao.getCustomersSnapshot().find { it.id == custId }
                if (existing != null) {
                    val prevUnpaid = (previousInvoice.totalNet - previousInvoice.montantPaye).coerceAtLeast(0.0)
                    val nextUnpaid = (updatedInvoice.totalNet - updatedInvoice.montantPaye).coerceAtLeast(0.0)
                    val nextCust = existing.copy(
                        dette = (existing.dette + (nextUnpaid - prevUnpaid)).coerceAtLeast(0.0),
                        totalAchats = (existing.totalAchats + (updatedInvoice.totalNet - previousInvoice.totalNet)).coerceAtLeast(0.0)
                    )
                    dao.upsertCustomer(nextCust)
                    updatedCustomer = nextCust.toModel()
                }
            }

            dao.upsertInvoice(updatedInvoice.toEntity())
            val m2Total = updatedInvoice.articles.sumOf { it.m2Total }
            val cartonsTotal = updatedInvoice.articles.sumOf { it.cartons }
            val itemsStr = updatedInvoice.articles.joinToString(", ") {
                val pcsTag = if (it.pieces > 0) "+${it.pieces}p" else ""
                "${it.cartons} ctn$pcsTag ${it.name}"
            }.ifBlank { "Lignes diverses" }
            val diffNote = "Modifiée par $editorName (Ancien: ${previousInvoice.totalNet.toInt()} DA -> Nouveau: ${updatedInvoice.totalNet.toInt()} DA) • $itemsStr"

            modifNotification = DepotNotification(
                id = "notif_${System.currentTimeMillis()}_${shortId()}",
                invoiceId = sanitizeRuleId(updatedInvoice.id, "inv_${shortId()}"),
                reference = "[MODIF] ${updatedInvoice.reference}".take(60),
                date = nowIso(),
                vendeur = editorName.take(80),
                magasin = updatedInvoice.magasin.take(80),
                client = updatedInvoice.client.nom.take(120),
                clientTel = updatedInvoice.client.tel.take(40),
                totalNet = updatedInvoice.totalNet.coerceAtLeast(0.0),
                m2Total = m2Total.coerceAtLeast(0.0),
                cartonsTotal = cartonsTotal.coerceAtLeast(0),
                articlesSummary = diffNote.take(980),
                read = false
            )
            dao.insertNotification(modifNotification.toEntity())
            dao.putKv(AppKvEntity(KV_CART, "[]"))
            dao.putKv(AppKvEntity(KV_FREE_LINES, "[]"))
        }

        SaleCommitResult(
            invoice = updatedInvoice,
            caisseMovement = caisseDiff,
            updatedCustomer = updatedCustomer,
            notification = modifNotification,
            updatedProductStocks = updatedStocks,
            extraNotifications = extraNotifications,
            stockDeltas = stockDeltas
        )
    }

    suspend fun deleteInvoiceWithReconcile(
        invoiceId: String,
        actorName: String
    ): DeleteInvoiceCommitResult = withContext(Dispatchers.IO) {
        val restoredStocks = mutableMapOf<String, Int>()
        val restoredDeltas = mutableMapOf<String, Int>()
        var updatedCustomer: Customer? = null
        var notif: DepotNotification? = null

        db.withTransaction {
            val inv = dao.getInvoiceById(invoiceId)?.toModel()
            if (inv != null) {
                inv.articles.forEach { item ->
                    val qty = itemStockCartons(item)
                    if (!item.productId.startsWith("onetime_") && qty > 0) {
                        dao.adjustProductStock(item.productId, qty)
                        restoredDeltas[item.productId] = (restoredDeltas[item.productId] ?: 0) + qty
                    }
                }
                val products = dao.getProductsSnapshot()
                inv.articles.forEach { item ->
                    if (!item.productId.startsWith("onetime_")) {
                        products.find { it.id == item.productId }?.let { p ->
                            restoredStocks[p.id] = p.stock
                        }
                    }
                }
                val custId = inv.client.id
                if (!custId.isNullOrBlank()) {
                    val cust = dao.getCustomersSnapshot().find { it.id == custId }
                    if (cust != null) {
                        val unpaid = (inv.totalNet - inv.montantPaye).coerceAtLeast(0.0)
                        val nextCust = cust.copy(
                            dette = (cust.dette - unpaid).coerceAtLeast(0.0),
                            totalAchats = (cust.totalAchats - inv.totalNet).coerceAtLeast(0.0)
                        )
                        dao.upsertCustomer(nextCust)
                        updatedCustomer = nextCust.toModel()
                    }
                }
                val created = DepotNotification(
                    id = "notif_${System.currentTimeMillis()}_${shortId()}",
                    invoiceId = sanitizeRuleId(inv.id, "del_${shortId()}"),
                    reference = "[SUPPR] ${inv.reference}".take(60),
                    date = nowIso(),
                    vendeur = actorName.take(80),
                    magasin = inv.magasin.take(80),
                    client = inv.client.nom.take(120),
                    clientTel = inv.client.tel.take(40),
                    totalNet = inv.totalNet.coerceAtLeast(0.0),
                    m2Total = inv.articles.sumOf { it.m2Total }.coerceAtLeast(0.0),
                    cartonsTotal = inv.articles.sumOf { it.cartons }.coerceAtLeast(0),
                    articlesSummary = "Facture #${inv.reference} supprimée par $actorName (Stock restitué)".take(980),
                    read = false
                )
                dao.insertNotification(created.toEntity())
                notif = created
            }
            dao.deleteInvoiceById(invoiceId)
        }
        DeleteInvoiceCommitResult(
            restoredProductStocks = restoredStocks,
            restoredStockDeltas = restoredDeltas,
            updatedCustomer = updatedCustomer,
            notification = notif
        )
    }

    suspend fun recordStockNotification(
        product: TileProduct,
        detail: String,
        actorName: String,
        site: String,
        isAlert: Boolean = false
    ): DepotNotification = withContext(Dispatchers.IO) {
        val tag = if (isAlert || product.stock <= 5) "[ALERTE]" else "[STOCK]"
        val notif = DepotNotification(
            id = "notif_${System.currentTimeMillis()}_${shortId()}",
            invoiceId = sanitizeRuleId(product.id, "prod_${shortId()}"),
            reference = "$tag ${product.name}".take(60),
            date = nowIso(),
            vendeur = actorName.take(80),
            magasin = site.take(80),
            client = "${product.usine} • ${product.dimension}".take(120),
            clientTel = "",
            totalNet = product.prix.coerceAtLeast(0.0),
            m2Total = (product.stock.coerceAtLeast(0) * product.m2Carton).coerceAtLeast(0.0),
            cartonsTotal = product.stock.coerceAtLeast(0),
            articlesSummary = detail.take(980),
            read = false
        )
        dao.insertNotification(notif.toEntity())
        notif
    }

    suspend fun recordDepotDispoRequest(
        product: TileProduct,
        actorName: String,
        site: String
    ): DepotNotification = withContext(Dispatchers.IO) {
        val m2Stock = (product.stock.coerceAtLeast(0) * product.m2Carton).coerceAtLeast(0.0)
        val notif = DepotNotification(
            id = "notif_${System.currentTimeMillis()}_${shortId()}",
            invoiceId = sanitizeRuleId(product.id, "dispo_${shortId()}"),
            reference = "[DISPO] ${product.name}".take(60),
            date = nowIso(),
            vendeur = actorName.take(80),
            magasin = site.take(80),
            client = "Demande Dispo Dépôt • ${product.usine}".take(120),
            clientTel = "",
            totalNet = product.prix.coerceAtLeast(0.0),
            m2Total = m2Stock,
            cartonsTotal = product.stock.coerceAtLeast(0),
            articlesSummary = "Vérification dépôt demandée par $actorName ($site) pour ${product.name} (${product.dimension}) — Stock actuel: ${product.stock} ctn (${formatM2(m2Stock)} m²)".take(980),
            read = false
        )
        dao.insertNotification(notif.toEntity())
        notif
    }

    private fun buildLowStockAlert(
        product: TileProduct,
        actor: String,
        site: String
    ): DepotNotification {
        val statusText = if (product.stock <= 0) "RUPTURE DE STOCK (${product.stock} ctn)" else "STOCK CRITIQUE (${product.stock} ctn restants)"
        return DepotNotification(
            id = "notif_${System.currentTimeMillis()}_${shortId()}",
            invoiceId = sanitizeRuleId(product.id, "alert_${shortId()}"),
            reference = "[ALERTE] ${product.name}".take(60),
            date = nowIso(),
            vendeur = actor.take(80),
            magasin = site.take(80),
            client = "${product.usine} • ${product.dimension}".take(120),
            clientTel = "",
            totalNet = product.prix.coerceAtLeast(0.0),
            m2Total = (product.stock.coerceAtLeast(0) * product.m2Carton).coerceAtLeast(0.0),
            cartonsTotal = product.stock.coerceAtLeast(0),
            articlesSummary = "$statusText — ${product.name} (${product.usine})".take(980),
            read = false
        )
    }

    private fun itemStockCartons(item: CartItem): Int {
        if (item.cartons > 0) return item.cartons
        if (item.pieces > 0) {
            return kotlin.math.ceil(item.pieces.toDouble() / item.pcs.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        }
        return 0
    }

    suspend fun markInvoiceWhatsappSent(invoiceId: String) = withContext(Dispatchers.IO) {
        dao.markInvoiceWhatsappSent(invoiceId)
    }

    suspend fun updateInvoiceHidePrices(invoiceId: String, hide: Boolean) = withContext(Dispatchers.IO) {
        dao.updateInvoiceHidePrices(invoiceId, hide)
    }

    private fun sanitizeRuleId(raw: String, fallback: String): String {
        val clean = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return clean.ifBlank { fallback }
    }

    // Notifications
    suspend fun markAllNotificationsRead() = withContext(Dispatchers.IO) {
        dao.markAllNotificationsRead()
    }

    // Customers
    suspend fun upsertCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        dao.upsertCustomer(customer.toEntity())
    }

    suspend fun payCustomerDebt(
        customerId: String,
        amount: Double,
        author: String
    ): Pair<Customer, CaisseMovement>? = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext null
        var updatedCust: Customer? = null
        var caisseMov: CaisseMovement? = null
        db.withTransaction {
            val cust = dao.getCustomersSnapshot().find { it.id == customerId } ?: return@withTransaction
            val nextCust = cust.copy(dette = (cust.dette - amount).coerceAtLeast(0.0))
            dao.upsertCustomer(nextCust)
            val mov = CaisseMovement(
                id = "caisse_${System.currentTimeMillis()}_${shortId()}",
                date = nowIso(),
                type = "entree",
                motif = "Remboursement crédit client - ${cust.nom}",
                montant = amount,
                auteur = author
            )
            dao.insertCaisseMovement(mov.toEntity())
            updatedCust = nextCust.toModel()
            caisseMov = mov
        }
        val c = updatedCust
        val m = caisseMov
        if (c != null && m != null) Pair(c, m) else null
    }

    // Suppliers
    suspend fun upsertSupplier(supplier: Supplier) = withContext(Dispatchers.IO) {
        dao.upsertSupplier(supplier.toEntity())
    }

    suspend fun recordSupplierVersement(
        supplierId: String,
        amount: Double,
        motif: String,
        ref: String?,
        author: String
    ): Triple<Supplier, SupplierMovement, CaisseMovement>? = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext null
        var updatedSup: Supplier? = null
        var supMov: SupplierMovement? = null
        var caisseMov: CaisseMovement? = null
        db.withTransaction {
            val sup = dao.getSuppliersSnapshot().find { it.id == supplierId } ?: return@withTransaction
            val nextSup = sup.copy(soldeDette = (sup.soldeDette - amount).coerceAtLeast(0.0))
            dao.upsertSupplier(nextSup)
            val mov = SupplierMovement(
                id = "sup_mov_${System.currentTimeMillis()}_${shortId()}",
                supplierId = supplierId,
                date = nowIso(),
                type = "versement",
                montant = amount,
                motif = motif.ifBlank { "Versement / Règlement dette usine" },
                pieceJointeRef = ref?.takeIf { it.isNotBlank() },
                auteur = author
            )
            dao.insertSupplierMovement(mov.toEntity())
            val cMov = CaisseMovement(
                id = "caisse_${System.currentTimeMillis()}_${shortId()}",
                date = nowIso(),
                type = "sortie",
                motif = "Règlement usine ${sup.nom} : $motif",
                montant = amount,
                auteur = author
            )
            dao.insertCaisseMovement(cMov.toEntity())
            updatedSup = nextSup.toModel()
            supMov = mov
            caisseMov = cMov
        }
        val s = updatedSup
        val sm = supMov
        val cm = caisseMov
        if (s != null && sm != null && cm != null) Triple(s, sm, cm) else null
    }

    suspend fun recordSupplierInvoice(
        supplierId: String,
        amount: Double,
        motif: String,
        ref: String?,
        author: String
    ): Pair<Supplier, SupplierMovement>? = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext null
        var updatedSup: Supplier? = null
        var supMov: SupplierMovement? = null
        db.withTransaction {
            val sup = dao.getSuppliersSnapshot().find { it.id == supplierId } ?: return@withTransaction
            val nextSup = sup.copy(
                soldeDette = sup.soldeDette + amount,
                totalAchats = sup.totalAchats + amount
            )
            dao.upsertSupplier(nextSup)
            val mov = SupplierMovement(
                id = "sup_mov_${System.currentTimeMillis()}_${shortId()}",
                supplierId = supplierId,
                date = nowIso(),
                type = "achat",
                montant = amount,
                motif = motif.ifBlank { "Réception Marchandise" },
                pieceJointeRef = ref?.takeIf { it.isNotBlank() },
                auteur = author
            )
            dao.insertSupplierMovement(mov.toEntity())
            updatedSup = nextSup.toModel()
            supMov = mov
        }
        val s = updatedSup
        val sm = supMov
        if (s != null && sm != null) Pair(s, sm) else null
    }

    // Caisse
    suspend fun addCaisseMovement(movement: CaisseMovement) = withContext(Dispatchers.IO) {
        dao.insertCaisseMovement(movement.toEntity())
    }

    suspend fun saveDailyCaisseCount(count: DailyCaisseCount) = withContext(Dispatchers.IO) {
        dao.insertDailyCaisseCount(count.toEntity())
    }

    // Users & Session
    suspend fun setCurrentUserId(userId: String?) = withContext(Dispatchers.IO) {
        dao.putKv(AppKvEntity(KV_CURRENT_USER_ID, userId ?: ""))
    }

    suspend fun upsertUser(user: StaffUser) = withContext(Dispatchers.IO) {
        dao.upsertUser(user.toEntity())
    }

    suspend fun deleteUser(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteUserById(userId)
    }

    suspend fun upsertRolePolicy(policy: RolePolicy) = withContext(Dispatchers.IO) {
        dao.upsertRolePolicy(policy.toEntity())
    }

    // Store Info & Settings
    suspend fun saveStoreInfo(info: StoreInfo) = withContext(Dispatchers.IO) {
        val json = PosJsonMapper.storeInfoToJson(info)
        if (dao.getKv(KV_STORE_INFO)?.jsonValue != json) {
            dao.putKv(AppKvEntity(KV_STORE_INFO, json))
        }
    }

    suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        val json = PosJsonMapper.settingsToJson(settings)
        if (dao.getKv(KV_SETTINGS)?.jsonValue != json) {
            dao.putKv(AppKvEntity(KV_SETTINGS, json))
        }
    }

    suspend fun setAutoEmailPdf(enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.putKv(AppKvEntity(KV_AUTO_EMAIL_PDF, if (enabled) "true" else "false"))
    }

    suspend fun hasPendingOfflineSync(): Boolean = withContext(Dispatchers.IO) {
        dao.getKv(KV_PENDING_OFFLINE_SYNC)?.jsonValue == "true"
    }

    suspend fun setPendingOfflineSync(pending: Boolean) = withContext(Dispatchers.IO) {
        val nextVal = if (pending) "true" else "false"
        if (dao.getKv(KV_PENDING_OFFLINE_SYNC)?.jsonValue != nextVal) {
            dao.putKv(AppKvEntity(KV_PENDING_OFFLINE_SYNC, nextVal))
        }
    }

    // Backup Export / Import JSON
    suspend fun exportAllDataJson(): String = withContext(Dispatchers.IO) {
        val storeInfo = dao.getKv(KV_STORE_INFO)?.let {
            PosJsonMapper.jsonToStoreInfo(it.jsonValue, PosSeeds.DEFAULT_STORE_INFO)
        } ?: PosSeeds.DEFAULT_STORE_INFO
        val settings = dao.getKv(KV_SETTINGS)?.let {
            PosJsonMapper.jsonToSettings(it.jsonValue, PosSeeds.DEFAULT_SETTINGS)
        } ?: PosSeeds.DEFAULT_SETTINGS

        val productsArr = JSONArray().apply {
            dao.getProductsSnapshot().map { it.toModel() }.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("usine", p.usine)
                    put("dimension", p.dimension)
                    put("m2_carton", p.m2Carton)
                    put("pcs", p.pcs)
                    put("prix", p.prix)
                    put("prixAchat", p.prixAchat)
                    put("stock", p.stock)
                    put("color", p.color)
                    put("colorName", p.colorName)
                    put("image", p.image)
                    put("finition", p.finition)
                    put("choix", p.choix)
                    put("categorie", p.categorie)
                    put("description", p.description)
                    put("pricingUnit", p.pricingUnit)
                    put("prixPiece", p.prixPiece)
                })
            }
        }

        val invoicesArr = JSONArray().apply {
            dao.getInvoicesSnapshot().forEach { inv ->
                put(JSONObject().apply {
                    put("id", inv.id)
                    put("reference", inv.reference)
                    put("date", inv.date)
                    put("vendeur", inv.vendeur)
                    put("vendeurTel", inv.vendeurTel)
                    put("magasin", inv.magasin)
                    put("client", JSONObject(inv.clientJson))
                    put("articles", JSONArray(inv.articlesJson))
                    put("lignesLibres", JSONArray(inv.lignesLibresJson))
                    put("totalBrut", inv.totalBrut)
                    put("remise", inv.remise)
                    put("totalNet", inv.totalNet)
                    put("montantPaye", inv.montantPaye)
                    put("resteAPayer", inv.resteAPayer)
                    put("modePaiement", inv.modePaiement)
                    put("statut", inv.statut)
                    put("notes", inv.notes)
                    put("hidePrices", inv.hidePrices)
                    put("whatsappSentBeforePrint", inv.whatsappSentBeforePrint)
                })
            }
        }

        val customersArr = JSONArray().apply {
            dao.getCustomersSnapshot().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("nom", c.nom)
                    put("tel", c.tel)
                    put("adresse", c.adresse)
                    put("dette", c.dette)
                    put("totalAchats", c.totalAchats)
                })
            }
        }

        val suppliersArr = JSONArray().apply {
            dao.getSuppliersSnapshot().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("nom", s.nom)
                    put("contact", s.contact)
                    put("tel", s.tel)
                    put("adresse", s.adresse)
                    put("usineRepresentee", s.usineRepresentee)
                    put("soldeDette", s.soldeDette)
                    put("totalAchats", s.totalAchats)
                })
            }
        }

        val caisseArr = JSONArray().apply {
            dao.getCaisseSnapshot().forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("date", m.date)
                    put("type", m.type)
                    put("motif", m.motif)
                    put("montant", m.montant)
                    put("auteur", m.auteur)
                    put("referenceFacture", m.referenceFacture ?: JSONObject.NULL)
                })
            }
        }

        val usersArr = JSONArray().apply {
            dao.getUsersSnapshot().forEach { u ->
                put(JSONObject().apply {
                    put("id", u.id)
                    put("name", u.name)
                    put("role", u.role)
                    put("pin", u.pin)
                    put("magasin", u.magasin)
                    put("allowedTabs", JSONArray(u.allowedTabsJson))
                    put("canEditInvoices", u.canEditInvoices)
                    put("canDeleteInvoices", u.canDeleteInvoices)
                    put("canModifyStock", u.canModifyStock)
                    put("canViewSuppliers", u.canViewSuppliers)
                    put("canViewCaisse", u.canViewCaisse)
                    put("canManageUsers", u.canManageUsers)
                    put("active", u.active)
                    put("phone", u.phone)
                    put("canViewPrixAchat", u.canViewPrixAchat)
                    put("canModifyPrices", u.canModifyPrices)
                })
            }
        }

        val rolePoliciesArr = JSONArray().apply {
            dao.getRolePoliciesSnapshot().forEach { rp ->
                put(JSONObject().apply {
                    put("roleId", rp.roleId)
                    put("displayName", rp.displayName)
                    put("description", rp.description)
                    put("canViewPrixAchat", rp.canViewPrixAchat)
                    put("canModifyPrices", rp.canModifyPrices)
                    put("canModifyStock", rp.canModifyStock)
                    put("canEditInvoices", rp.canEditInvoices)
                    put("canDeleteInvoices", rp.canDeleteInvoices)
                    put("canViewSuppliers", rp.canViewSuppliers)
                    put("canViewCaisse", rp.canViewCaisse)
                    put("canManageUsers", rp.canManageUsers)
                    put("allowedTabs", JSONArray(rp.allowedTabsJson))
                })
            }
        }

        JSONObject().apply {
            put("exportDate", nowIso())
            put("storeInfo", JSONObject(PosJsonMapper.storeInfoToJson(storeInfo)))
            put("settings", JSONObject(PosJsonMapper.settingsToJson(settings)))
            put("products", productsArr)
            put("invoices", invoicesArr)
            put("customers", customersArr)
            put("suppliers", suppliersArr)
            put("caisse", caisseArr)
            put("users", usersArr)
            put("rolePolicies", rolePoliciesArr)
        }.toString(2)
    }

    suspend fun importAllDataJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(jsonString)
            db.withTransaction {
                root.optJSONObject("storeInfo")?.let {
                    dao.putKv(AppKvEntity(KV_STORE_INFO, it.toString()))
                }
                root.optJSONObject("settings")?.let {
                    dao.putKv(AppKvEntity(KV_SETTINGS, it.toString()))
                }
                root.optJSONArray("products")?.let { arr ->
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            add(
                                TileProduct(
                                    id = o.optString("id"),
                                    name = o.optString("name"),
                                    usine = o.optString("usine"),
                                    dimension = o.optString("dimension"),
                                    m2Carton = o.optDouble("m2_carton", o.optDouble("m2Carton", 1.0)),
                                    pcs = o.optInt("pcs", 1),
                                    prix = o.optDouble("prix", 0.0),
                                    prixAchat = o.optDouble("prixAchat", 0.0),
                                    stock = o.optInt("stock", 0),
                                    color = o.optString("color", "#94a3b8"),
                                    colorName = o.optString("colorName", ""),
                                    image = o.optString("image", ""),
                                    finition = o.optString("finition", "brillant"),
                                    choix = o.optString("choix", "1er Choix"),
                                    categorie = o.optString("categorie", "sol"),
                                    description = o.optString("description", ""),
                                    pricingUnit = o.optString("pricingUnit", "carton").ifBlank { "carton" },
                                    prixPiece = o.optDouble("prixPiece", 0.0)
                                ).toEntity()
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        dao.clearProducts()
                        dao.insertProducts(list)
                    }
                }
                root.optJSONArray("customers")?.let { arr ->
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            add(
                                Customer(
                                    id = o.optString("id"),
                                    nom = o.optString("nom"),
                                    tel = o.optString("tel"),
                                    adresse = o.optString("adresse"),
                                    dette = o.optDouble("dette", 0.0),
                                    totalAchats = o.optDouble("totalAchats", 0.0)
                                ).toEntity()
                            )
                        }
                    }
                    dao.clearCustomers()
                    dao.insertCustomers(list)
                }
                root.optJSONArray("suppliers")?.let { arr ->
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            add(
                                Supplier(
                                    id = o.optString("id"),
                                    nom = o.optString("nom"),
                                    contact = o.optString("contact"),
                                    tel = o.optString("tel"),
                                    adresse = o.optString("adresse"),
                                    usineRepresentee = o.optString("usineRepresentee"),
                                    soldeDette = o.optDouble("soldeDette", 0.0),
                                    totalAchats = o.optDouble("totalAchats", 0.0)
                                ).toEntity()
                            )
                        }
                    }
                    dao.clearSuppliers()
                    dao.insertSuppliers(list)
                }
                root.optJSONArray("caisse")?.let { arr ->
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            add(
                                CaisseMovement(
                                    id = o.optString("id"),
                                    date = o.optString("date"),
                                    type = o.optString("type"),
                                    motif = o.optString("motif"),
                                    montant = o.optDouble("montant", 0.0),
                                    auteur = o.optString("auteur"),
                                    referenceFacture = if (o.isNull("referenceFacture")) null else o.optString("referenceFacture")
                                ).toEntity()
                            )
                        }
                    }
                    dao.clearCaisse()
                    dao.insertCaisseMovements(list)
                }
                root.optJSONArray("invoices")?.let { arr ->
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val clientObj = o.optJSONObject("client") ?: JSONObject()
                            val articlesArr = o.optJSONArray("articles") ?: JSONArray()
                            val freeArr = o.optJSONArray("lignesLibres") ?: JSONArray()
                            val client = PosJsonMapper.jsonToClient(clientObj.toString())
                            val articles = PosJsonMapper.jsonToCartItems(articlesArr.toString())
                            val free = PosJsonMapper.jsonToFreeLines(freeArr.toString())
                            add(
                                Invoice(
                                    id = o.optString("id"),
                                    reference = o.optString("reference"),
                                    date = o.optString("date"),
                                    vendeur = o.optString("vendeur"),
                                    magasin = o.optString("magasin", "Depot Remchi"),
                                    client = client,
                                    articles = articles,
                                    lignesLibres = free,
                                    totalBrut = o.optDouble("totalBrut", 0.0),
                                    remise = o.optDouble("remise", 0.0),
                                    totalNet = o.optDouble("totalNet", 0.0),
                                    montantPaye = o.optDouble("montantPaye", 0.0),
                                    resteAPayer = o.optDouble("resteAPayer", 0.0),
                                    modePaiement = o.optString("modePaiement", "Espèces"),
                                    statut = o.optString("statut", "Payé"),
                                    notes = o.optString("notes", ""),
                                    vendeurTel = o.optString("vendeurTel", ""),
                                    hidePrices = o.optBoolean("hidePrices", false),
                                    whatsappSentBeforePrint = o.optBoolean("whatsappSentBeforePrint", false)
                                ).toEntity()
                            )
                        }
                    }
                    dao.clearInvoices()
                    dao.insertInvoices(list)
                }
                root.optJSONArray("users")?.let { arr ->
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val tabsArr = o.optJSONArray("allowedTabs") ?: JSONArray()
                            val tabs = List(tabsArr.length()) { tabsArr.optString(it) }.filter { it.isNotBlank() }
                            val role = o.optString("role", "vendeur")
                            val isPrivileged = role == "admin" || role == "gerant"
                            add(
                                StaffUser(
                                    id = o.optString("id"),
                                    name = o.optString("name"),
                                    role = role,
                                    pin = o.optString("pin", "0000"),
                                    magasin = o.optString("magasin", "Depot Remchi"),
                                    allowedTabs = tabs,
                                    canEditInvoices = o.optBoolean("canEditInvoices", false),
                                    canDeleteInvoices = o.optBoolean("canDeleteInvoices", false),
                                    canModifyStock = o.optBoolean("canModifyStock", false),
                                    canViewSuppliers = o.optBoolean("canViewSuppliers", false),
                                    canViewCaisse = o.optBoolean("canViewCaisse", false),
                                    canManageUsers = o.optBoolean("canManageUsers", false),
                                    active = o.optBoolean("active", true),
                                    phone = o.optString("phone", ""),
                                    canViewPrixAchat = o.optBoolean("canViewPrixAchat", isPrivileged),
                                    canModifyPrices = o.optBoolean("canModifyPrices", isPrivileged)
                                ).toEntity()
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        dao.clearUsers()
                        dao.insertUsers(list)
                    }
                }
                root.optJSONArray("rolePolicies")?.let { arr ->
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val tabsArr = o.optJSONArray("allowedTabs") ?: JSONArray()
                            val tabs = List(tabsArr.length()) { tabsArr.optString(it) }.filter { it.isNotBlank() }
                            add(
                                RolePolicy(
                                    roleId = o.optString("roleId", "vendeur"),
                                    displayName = o.optString("displayName"),
                                    description = o.optString("description"),
                                    canViewPrixAchat = o.optBoolean("canViewPrixAchat", false),
                                    canModifyPrices = o.optBoolean("canModifyPrices", false),
                                    canModifyStock = o.optBoolean("canModifyStock", false),
                                    canEditInvoices = o.optBoolean("canEditInvoices", true),
                                    canDeleteInvoices = o.optBoolean("canDeleteInvoices", false),
                                    canViewSuppliers = o.optBoolean("canViewSuppliers", false),
                                    canViewCaisse = o.optBoolean("canViewCaisse", false),
                                    canManageUsers = o.optBoolean("canManageUsers", false),
                                    allowedTabs = tabs
                                ).toEntity()
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        dao.clearRolePolicies()
                        dao.insertRolePolicies(list)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun shortId(): String = UUID.randomUUID().toString().take(6)
}
