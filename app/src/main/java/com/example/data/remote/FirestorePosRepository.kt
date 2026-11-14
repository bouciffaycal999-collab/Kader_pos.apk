package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.CaisseMovement
import com.example.data.model.Customer
import com.example.data.model.DailyCaisseCount
import com.example.data.model.DepotNotification
import com.example.data.model.Invoice
import com.example.data.model.PosSeeds
import com.example.data.model.RolePolicy
import com.example.data.model.StaffUser
import com.example.data.model.StoreInfo
import com.example.data.model.Supplier
import com.example.data.model.SupplierMovement
import com.example.data.model.TileProduct
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class FirestorePosRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(resolveDatabaseId(context.applicationContext))
    )

    companion object {
        fun resolveDatabaseId(context: Context): String {
            return runCatching {
                context.getString(R.string.firestore_database_id).trim().takeIf { it.isNotBlank() }
            }.getOrNull() ?: "(default)"
        }
    }

    private val createdAtCache = ConcurrentHashMap<String, Timestamp>()

    private fun userDoc(userId: String): DocumentReference =
        db.collection("users").document(userId)

    private fun col(userId: String, name: String) =
        userDoc(userId).collection(name)

    private fun cacheCreatedAt(path: String, ts: Timestamp?) {
        if (ts != null) {
            createdAtCache[path] = ts
        }
    }

    private suspend fun resolveCreatedAt(docRef: DocumentReference): Timestamp? {
        createdAtCache[docRef.path]?.let { return it }
        val snap = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            ?.takeIf { it.exists() }
            ?: runCatching { docRef.get().await() }.getOrNull()
            ?: return null
        if (!snap.exists()) return null
        val ts = snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        cacheCreatedAt(docRef.path, ts)
        return ts
    }

    suspend fun waitForPendingWrites() {
        db.waitForPendingWrites().await()
    }

    // --- Account bootstrap ---

    suspend fun ensureUserAccount(userId: String, email: String?, displayName: String?) {
        val docRef = userDoc(userId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val created = snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                cacheCreatedAt(docRef.path, created)
                docRef.update(
                    mapOf(
                        "email" to email,
                        "displayName" to displayName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    UserAccountDoc(
                        id = userId,
                        userId = userId,
                        email = email,
                        displayName = displayName,
                        createdAt = null,
                        updatedAt = null
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Real-time snapshot Flows (first-party Query.snapshots()) ---

    fun observeProducts(userId: String): Flow<List<TileProduct>> =
        col(userId, "products").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(TileProductDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }
        }.flowOn(Dispatchers.Default)

    fun observeInvoices(userId: String): Flow<List<Invoice>> =
        col(userId, "invoices").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(InvoiceDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedByDescending { it.date }
        }.flowOn(Dispatchers.Default)

    fun observeInvoiceById(userId: String, invoiceId: String): Flow<Invoice?> =
        col(userId, "invoices").document(invoiceId).snapshots().map { doc ->
            if (!doc.exists()) null
            else doc.toObject(InvoiceDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }
                ?.toDomain()
        }.flowOn(Dispatchers.Default)

    fun observeCustomers(userId: String): Flow<List<Customer>> =
        col(userId, "customers").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(CustomerDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedBy { it.nom }
        }.flowOn(Dispatchers.Default)

    fun observeSuppliers(userId: String): Flow<List<Supplier>> =
        col(userId, "suppliers").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(SupplierDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedBy { it.nom }
        }.flowOn(Dispatchers.Default)

    fun observeSupplierMovements(userId: String): Flow<List<SupplierMovement>> =
        col(userId, "supplier_movements").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(SupplierMovementDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedByDescending { it.date }
        }.flowOn(Dispatchers.Default)

    fun observeCaisseMovements(userId: String): Flow<List<CaisseMovement>> =
        col(userId, "caisse_movements").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(CaisseMovementDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedByDescending { it.date }
        }.flowOn(Dispatchers.Default)

    fun observeDailyCaisseCounts(userId: String): Flow<List<DailyCaisseCount>> =
        col(userId, "daily_caisse_counts").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(DailyCaisseCountDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedByDescending { it.date }
        }.flowOn(Dispatchers.Default)

    fun observeStaffUsers(userId: String): Flow<List<StaffUser>> =
        col(userId, "staff_users").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(StaffUserDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedBy { it.name }
        }.flowOn(Dispatchers.Default)

    fun observeRolePolicies(userId: String): Flow<List<RolePolicy>> =
        col(userId, "role_policies").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(RolePolicyDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedBy { policy ->
                when (policy.roleId) {
                    "admin" -> 0
                    "gerant" -> 1
                    else -> 2
                }
            }
        }.flowOn(Dispatchers.Default)

    fun observeNotifications(userId: String): Flow<List<DepotNotification>> =
        col(userId, "notifications").snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                val obj = doc.toObject(DepotNotificationDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }?.toDomain()
            }.sortedByDescending { it.date }
        }.flowOn(Dispatchers.Default)

    fun observeStoreInfo(userId: String): Flow<StoreInfo?> =
        col(userId, "store_info").document("default").snapshots().map { doc ->
            if (!doc.exists()) null
            else doc.toObject(StoreInfoDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }
                ?.toDomain()
        }.flowOn(Dispatchers.Default)

    fun observeAppSettings(userId: String): Flow<AppSettings?> =
        col(userId, "app_settings").document("default").snapshots().map { doc ->
            if (!doc.exists()) null
            else doc.toObject(AppSettingsDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.also { cacheCreatedAt(doc.reference.path, it.createdAt) }
                ?.toDomain()
        }.flowOn(Dispatchers.Default)

    // --- Initial workspace seed (only writes when remote collection is empty) ---

    suspend fun seedProductsIfEmpty(userId: String, products: List<TileProduct>) = withContext(Dispatchers.IO) {
        if (products.isEmpty()) return@withContext
        val productsCol = col(userId, "products")
        try {
            val existing = productsCol.limit(1).get().await()
            if (!existing.isEmpty) return@withContext
            products.chunked(250).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { product ->
                    val ref = productsCol.document(product.id)
                    batch.set(ref, TileProductDoc.fromDomain(product, createdAt = null, updatedAt = null))
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, productsCol.path)
            throw e
        }
    }

    suspend fun seedDefaultsIfEmpty(
        userId: String,
        staffUsers: List<StaffUser>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        caisseMovements: List<CaisseMovement>,
        storeInfo: StoreInfo,
        settings: AppSettings,
        rolePolicies: List<RolePolicy> = PosSeeds.DEFAULT_ROLE_POLICIES
    ) = withContext(Dispatchers.IO) {
        try {
            if (col(userId, "role_policies").limit(1).get().await().isEmpty && rolePolicies.isNotEmpty()) {
                val batch = db.batch()
                rolePolicies.forEach { policy ->
                    batch.set(col(userId, "role_policies").document(policy.roleId), RolePolicyDoc.fromDomain(policy))
                }
                batch.commit().await()
            }
            if (col(userId, "staff_users").limit(1).get().await().isEmpty && staffUsers.isNotEmpty()) {
                val batch = db.batch()
                staffUsers.forEach { u ->
                    batch.set(col(userId, "staff_users").document(u.id), StaffUserDoc.fromDomain(u))
                }
                batch.commit().await()
            }
            if (col(userId, "customers").limit(1).get().await().isEmpty && customers.isNotEmpty()) {
                val batch = db.batch()
                customers.forEach { c ->
                    batch.set(col(userId, "customers").document(c.id), CustomerDoc.fromDomain(c))
                }
                batch.commit().await()
            }
            if (col(userId, "suppliers").limit(1).get().await().isEmpty && suppliers.isNotEmpty()) {
                val batch = db.batch()
                suppliers.forEach { s ->
                    batch.set(col(userId, "suppliers").document(s.id), SupplierDoc.fromDomain(s))
                }
                batch.commit().await()
            }
            if (col(userId, "caisse_movements").limit(1).get().await().isEmpty && caisseMovements.isNotEmpty()) {
                val batch = db.batch()
                caisseMovements.forEach { m ->
                    batch.set(col(userId, "caisse_movements").document(m.id), CaisseMovementDoc.fromDomain(m))
                }
                batch.commit().await()
            }
            val storeDoc = col(userId, "store_info").document("default")
            if (!storeDoc.get().await().exists()) {
                storeDoc.set(StoreInfoDoc.fromDomain(storeInfo)).await()
            }
            val settingsDoc = col(userId, "app_settings").document("default")
            if (!settingsDoc.get().await().exists()) {
                settingsDoc.set(AppSettingsDoc.fromDomain(settings)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc(userId).path)
            throw e
        }
    }

    suspend fun seedOfflineHistoryIfEmpty(
        userId: String,
        invoices: List<Invoice>,
        supplierMovements: List<SupplierMovement>,
        dailyCaisseCounts: List<DailyCaisseCount>,
        notifications: List<DepotNotification>
    ) = withContext(Dispatchers.IO) {
        try {
            if (invoices.isNotEmpty() && col(userId, "invoices").limit(1).get().await().isEmpty) {
                invoices.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { inv ->
                        batch.set(col(userId, "invoices").document(inv.id), InvoiceDoc.fromDomain(inv))
                    }
                    batch.commit().await()
                }
            }
            if (supplierMovements.isNotEmpty() && col(userId, "supplier_movements").limit(1).get().await().isEmpty) {
                supplierMovements.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { mov ->
                        batch.set(col(userId, "supplier_movements").document(mov.id), SupplierMovementDoc.fromDomain(mov))
                    }
                    batch.commit().await()
                }
            }
            if (dailyCaisseCounts.isNotEmpty() && col(userId, "daily_caisse_counts").limit(1).get().await().isEmpty) {
                dailyCaisseCounts.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { cnt ->
                        batch.set(col(userId, "daily_caisse_counts").document(cnt.id), DailyCaisseCountDoc.fromDomain(cnt))
                    }
                    batch.commit().await()
                }
            }
            if (notifications.isNotEmpty() && col(userId, "notifications").limit(1).get().await().isEmpty) {
                notifications.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { n ->
                        batch.set(col(userId, "notifications").document(n.id), DepotNotificationDoc.fromDomain(n))
                    }
                    batch.commit().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc(userId).path)
            throw e
        }
    }

    suspend fun mergeMissingOfflineData(
        userId: String,
        products: List<TileProduct>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        invoices: List<Invoice>,
        caisseMovements: List<CaisseMovement>,
        supplierMovements: List<SupplierMovement>,
        dailyCaisseCounts: List<DailyCaisseCount>,
        notifications: List<DepotNotification>
    ) = withContext(Dispatchers.IO) {
        try {
            if (products.isNotEmpty()) {
                val remoteIds = col(userId, "products").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = products.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { p -> batch.set(col(userId, "products").document(p.id), TileProductDoc.fromDomain(p)) }
                    batch.commit().await()
                }
            }
            if (customers.isNotEmpty()) {
                val remoteIds = col(userId, "customers").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = customers.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { c -> batch.set(col(userId, "customers").document(c.id), CustomerDoc.fromDomain(c)) }
                    batch.commit().await()
                }
            }
            if (suppliers.isNotEmpty()) {
                val remoteIds = col(userId, "suppliers").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = suppliers.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { s -> batch.set(col(userId, "suppliers").document(s.id), SupplierDoc.fromDomain(s)) }
                    batch.commit().await()
                }
            }
            if (invoices.isNotEmpty()) {
                val remoteIds = col(userId, "invoices").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = invoices.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { inv -> batch.set(col(userId, "invoices").document(inv.id), InvoiceDoc.fromDomain(inv)) }
                    batch.commit().await()
                }
            }
            if (caisseMovements.isNotEmpty()) {
                val remoteIds = col(userId, "caisse_movements").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = caisseMovements.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { m -> batch.set(col(userId, "caisse_movements").document(m.id), CaisseMovementDoc.fromDomain(m)) }
                    batch.commit().await()
                }
            }
            if (supplierMovements.isNotEmpty()) {
                val remoteIds = col(userId, "supplier_movements").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = supplierMovements.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { sm -> batch.set(col(userId, "supplier_movements").document(sm.id), SupplierMovementDoc.fromDomain(sm)) }
                    batch.commit().await()
                }
            }
            if (dailyCaisseCounts.isNotEmpty()) {
                val remoteIds = col(userId, "daily_caisse_counts").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = dailyCaisseCounts.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { cnt -> batch.set(col(userId, "daily_caisse_counts").document(cnt.id), DailyCaisseCountDoc.fromDomain(cnt)) }
                    batch.commit().await()
                }
            }
            if (notifications.isNotEmpty()) {
                val remoteIds = col(userId, "notifications").get().await().documents.mapTo(HashSet()) { it.id }
                val missing = notifications.filter { it.id !in remoteIds }
                missing.chunked(250).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { n -> batch.set(col(userId, "notifications").document(n.id), DepotNotificationDoc.fromDomain(n)) }
                    batch.commit().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc(userId).path)
            throw e
        }
    }

    // --- Products ---

    suspend fun upsertProduct(userId: String, product: TileProduct) {
        val docRef = col(userId, "products").document(product.id)
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(TileProductDoc.fromDomain(product, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateProductStock(userId: String, productId: String, newStock: Int) {
        val docRef = col(userId, "products").document(productId)
        try {
            docRef.update(
                mapOf(
                    "stock" to newStock,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adjustProductStockDelta(
        userId: String,
        productId: String,
        delta: Int,
        fallbackProduct: TileProduct? = null
    ) {
        if (delta == 0) return
        val docRef = col(userId, "products").document(productId)
        try {
            docRef.update(
                mapOf(
                    "stock" to FieldValue.increment(delta.toLong()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val root = e.unwrapFirestoreException()
            if (root?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND && fallbackProduct != null) {
                upsertProduct(userId, fallbackProduct)
                return
            }
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun commitSaleBatch(
        userId: String,
        invoice: Invoice,
        stockDeltas: Map<String, Int>,
        updatedProductStocks: Map<String, Int>,
        caisseMovement: CaisseMovement?,
        updatedCustomer: Customer?,
        notifications: List<DepotNotification>,
        fallbackProductsById: Map<String, TileProduct> = emptyMap(),
        isEdit: Boolean = false
    ) {
        val invRef = col(userId, "invoices").document(invoice.id)
        try {
            val invCreatedAt = if (isEdit) resolveCreatedAt(invRef) else null
            val custRef = updatedCustomer?.let { col(userId, "customers").document(it.id) }
            val custCreatedAt = custRef?.let { resolveCreatedAt(it) }

            val batch = db.batch()
            batch.set(invRef, InvoiceDoc.fromDomain(invoice, createdAt = invCreatedAt, updatedAt = null))

            stockDeltas.forEach { (prodId, delta) ->
                if (delta != 0) {
                    val prodRef = col(userId, "products").document(prodId)
                    batch.update(
                        prodRef,
                        mapOf(
                            "stock" to FieldValue.increment(delta.toLong()),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            caisseMovement?.let { mov ->
                batch.set(col(userId, "caisse_movements").document(mov.id), CaisseMovementDoc.fromDomain(mov))
            }
            if (custRef != null && updatedCustomer != null) {
                batch.set(custRef, CustomerDoc.fromDomain(updatedCustomer, createdAt = custCreatedAt, updatedAt = null))
            }
            notifications.forEach { n ->
                batch.set(col(userId, "notifications").document(n.id), DepotNotificationDoc.fromDomain(n))
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val root = e.unwrapFirestoreException()
            if (root?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND) {
                // Fallback if a product sold was created offline and not yet in Firestore
                upsertInvoice(userId, invoice)
                stockDeltas.forEach { (prodId, delta) ->
                    val fallback = fallbackProductsById[prodId]?.let { prod ->
                        updatedProductStocks[prodId]?.let { newStk -> prod.copy(stock = newStk) } ?: prod
                    }
                    adjustProductStockDelta(userId, prodId, delta, fallback)
                }
                caisseMovement?.let { createCaisseMovement(userId, it) }
                updatedCustomer?.let { upsertCustomer(userId, it) }
                notifications.forEach { createNotification(userId, it) }
                return
            }
            handleFirestoreError(e, OperationType.WRITE, invRef.path)
            throw e
        }
    }

    suspend fun commitDeleteInvoiceBatch(
        userId: String,
        invoiceId: String,
        restoredStockDeltas: Map<String, Int>,
        updatedCustomer: Customer?,
        notification: DepotNotification?
    ) {
        val invRef = col(userId, "invoices").document(invoiceId)
        try {
            val custRef = updatedCustomer?.let { col(userId, "customers").document(it.id) }
            val custCreatedAt = custRef?.let { resolveCreatedAt(it) }

            val batch = db.batch()
            batch.delete(invRef)
            restoredStockDeltas.forEach { (prodId, delta) ->
                if (delta != 0) {
                    batch.update(
                        col(userId, "products").document(prodId),
                        mapOf(
                            "stock" to FieldValue.increment(delta.toLong()),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            if (custRef != null && updatedCustomer != null) {
                batch.set(custRef, CustomerDoc.fromDomain(updatedCustomer, createdAt = custCreatedAt, updatedAt = null))
            }
            notification?.let { n ->
                batch.set(col(userId, "notifications").document(n.id), DepotNotificationDoc.fromDomain(n))
            }
            batch.commit().await()
            createdAtCache.remove(invRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, invRef.path)
            throw e
        }
    }

    suspend fun resetProductsCatalog(userId: String, products: List<TileProduct>) {
        val productsCol = col(userId, "products")
        try {
            val existing = productsCol.get().await()
            existing.documents.chunked(250).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            createdAtCache.keys.removeIf { it.startsWith("${productsCol.path}/") }
            products.chunked(250).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { product ->
                    batch.set(productsCol.document(product.id), TileProductDoc.fromDomain(product))
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, productsCol.path)
            throw e
        }
    }

    // --- Invoices ---

    suspend fun upsertInvoice(userId: String, invoice: Invoice) {
        val docRef = col(userId, "invoices").document(invoice.id)
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(InvoiceDoc.fromDomain(invoice, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getInvoiceById(userId: String, invoiceId: String): Invoice? {
        val docRef = col(userId, "invoices").document(invoiceId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) null
            else snap.toObject(InvoiceDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.also { cacheCreatedAt(docRef.path, it.createdAt) }
                ?.toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun markInvoiceWhatsappSent(userId: String, invoiceId: String) {
        val docRef = col(userId, "invoices").document(invoiceId)
        try {
            docRef.update(
                mapOf(
                    "whatsappSentBeforePrint" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateInvoiceHidePrices(userId: String, invoiceId: String, hide: Boolean) {
        val docRef = col(userId, "invoices").document(invoiceId)
        try {
            docRef.update(
                mapOf(
                    "hidePrices" to hide,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteInvoice(userId: String, invoiceId: String) {
        val docRef = col(userId, "invoices").document(invoiceId)
        try {
            docRef.delete().await()
            createdAtCache.remove(docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Customers ---

    suspend fun upsertCustomer(userId: String, customer: Customer) {
        val docRef = col(userId, "customers").document(customer.id)
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(CustomerDoc.fromDomain(customer, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Suppliers & Movements ---

    suspend fun upsertSupplier(userId: String, supplier: Supplier) {
        val docRef = col(userId, "suppliers").document(supplier.id)
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(SupplierDoc.fromDomain(supplier, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun createSupplierMovement(userId: String, movement: SupplierMovement) {
        val docRef = col(userId, "supplier_movements").document(movement.id)
        try {
            docRef.set(SupplierMovementDoc.fromDomain(movement)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- Caisse & Daily Counts ---

    suspend fun createCaisseMovement(userId: String, movement: CaisseMovement) {
        val docRef = col(userId, "caisse_movements").document(movement.id)
        try {
            docRef.set(CaisseMovementDoc.fromDomain(movement)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun createDailyCaisseCount(userId: String, count: DailyCaisseCount) {
        val docRef = col(userId, "daily_caisse_counts").document(count.id)
        try {
            docRef.set(DailyCaisseCountDoc.fromDomain(count)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- Staff Users ---

    suspend fun upsertStaffUser(userId: String, staffUser: StaffUser) {
        val docRef = col(userId, "staff_users").document(staffUser.id)
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(StaffUserDoc.fromDomain(staffUser, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStaffUser(userId: String, staffId: String) {
        val docRef = col(userId, "staff_users").document(staffId)
        try {
            docRef.delete().await()
            createdAtCache.remove(docRef.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Role Policies ---

    suspend fun upsertRolePolicy(userId: String, policy: RolePolicy) {
        val docRef = col(userId, "role_policies").document(policy.roleId)
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(RolePolicyDoc.fromDomain(policy, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Notifications ---

    suspend fun createNotification(userId: String, notification: DepotNotification) {
        val docRef = col(userId, "notifications").document(notification.id)
        try {
            docRef.set(DepotNotificationDoc.fromDomain(notification)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun markAllNotificationsRead(userId: String, notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return
        val notifCol = col(userId, "notifications")
        try {
            notificationIds.chunked(250).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { id ->
                    batch.update(
                        notifCol.document(id),
                        mapOf(
                            "read" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, notifCol.path)
            throw e
        }
    }

    // --- Store Info & App Settings ---

    suspend fun saveStoreInfo(userId: String, info: StoreInfo) {
        val docRef = col(userId, "store_info").document("default")
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(StoreInfoDoc.fromDomain(info, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun saveAppSettings(userId: String, settings: AppSettings) {
        val docRef = col(userId, "app_settings").document("default")
        try {
            val createdAt = resolveCreatedAt(docRef)
            docRef.set(AppSettingsDoc.fromDomain(settings, createdAt = createdAt, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }
}
