package com.example.data.remote

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.CaisseMovement
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.DepotNotification
import com.example.data.model.Invoice
import com.example.data.model.InvoiceClient
import com.example.data.model.PosSeeds
import com.example.data.model.Supplier
import com.example.data.model.SupplierMovement
import com.example.data.model.TileProduct
import com.example.data.repository.PosRepository
import com.example.ui.components.InvoicePdfGenerator
import com.example.ui.components.buildWhatsAppManagementMessage
import com.example.util.SaleSystemNotifier
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows
import java.util.UUID

class PosFirestoreRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ownerCanCreateUpdateObserveAndDeleteProductAndInvoice() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestorePosRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserAccount(aliceUid, ALICE_EMAIL, "Alice Owner")
        }

        val prodId = "p_test_${UUID.randomUUID().toString().take(8)}"
        val product = TileProduct(
            id = prodId,
            name = "60/60 BATI LUXOR TEST",
            usine = "BATI",
            dimension = "60×60",
            m2Carton = 1.44,
            pcs = 4,
            prix = 1200.0,
            prixAchat = 950.0,
            stock = 30,
            color = "#94a3b8",
            colorName = "Gris Minéral",
            finition = "rectifie",
            choix = "1er Choix",
            categorie = "sol",
            description = "Test tile"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertProduct(aliceUid, product) }
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.updateProductStock(aliceUid, prodId, 25) }

        val observedProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts(aliceUid).first { list -> list.any { it.id == prodId && it.stock == 25 } }
        }
        assertTrue(observedProducts.any { it.id == prodId && it.stock == 25 })

        val invId = "inv_${UUID.randomUUID().toString().take(8)}"
        val invoice = Invoice(
            id = invId,
            reference = "KC-TEST-001",
            date = "2026-11-12T10:00:00.000Z",
            vendeur = "Kader",
            magasin = "Depot Remchi",
            client = InvoiceClient(nom = "Ahmed Benali", tel = "0550123456", adresse = "Remchi"),
            articles = listOf(
                CartItem(
                    id = "line_1",
                    productId = prodId,
                    name = product.name,
                    usine = product.usine,
                    dimension = product.dimension,
                    m2Carton = 1.44,
                    pcs = 4,
                    prixUnitaire = 1200.0,
                    prixAchat = 950.0,
                    cartons = 5,
                    pieces = 0,
                    m2Total = 7.2,
                    sousTotal = 8640.0
                )
            ),
            lignesLibres = emptyList(),
            totalBrut = 8640.0,
            remise = 0.0,
            totalNet = 8640.0,
            montantPaye = 8640.0,
            resteAPayer = 0.0,
            modePaiement = "Espèces",
            statut = "Payé",
            notes = ""
        )
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertInvoice(aliceUid, invoice) }
        val loadedInv = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getInvoiceById(aliceUid, invId) }
        assertNotNull(loadedInv)
        assertEquals("KC-TEST-001", loadedInv?.reference)

        withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteInvoice(aliceUid, invId) }
    }

    @Test
    fun commitSaleBatchUpdatesStockAtomicallyAcrossRealTimeObserver() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestorePosRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserAccount(aliceUid, ALICE_EMAIL, "Alice Owner")
        }

        val prodId = "p_sync_${UUID.randomUUID().toString().take(8)}"
        val product = TileProduct(
            id = prodId,
            name = "120/60 REALTIME SYNC",
            usine = "CERAMICA",
            dimension = "120×60",
            m2Carton = 1.44,
            pcs = 2,
            prix = 1800.0,
            prixAchat = 1350.0,
            stock = 10,
            color = "#0f172a",
            colorName = "Noir",
            finition = "polie",
            choix = "1er Choix",
            categorie = "sol",
            description = "Sync test"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertProduct(aliceUid, product) }

        val stockChannel = Channel<Int>(Channel.UNLIMITED)
        val observerJob = launch(Dispatchers.IO) {
            repo.observeProducts(aliceUid).collect { list ->
                list.firstOrNull { it.id == prodId }?.let { stockChannel.trySend(it.stock) }
            }
        }

        try {
            suspend fun awaitStock(expected: Int) {
                withTimeout(FLOW_TIMEOUT_MS) {
                    while (true) {
                        val current = stockChannel.receive()
                        if (current == expected) break
                    }
                }
            }

            awaitStock(10)

            val invId1 = "inv_sync1_${UUID.randomUUID().toString().take(8)}"
            val inv1 = Invoice(
                id = invId1,
                reference = "KC-SYNC-001",
                date = "2026-11-12T11:00:00.000Z",
                vendeur = "Zohir",
                magasin = "Depot Remchi",
                client = InvoiceClient(nom = "Client A"),
                articles = listOf(
                    CartItem(
                        id = "l1",
                        productId = prodId,
                        name = product.name,
                        usine = product.usine,
                        dimension = product.dimension,
                        m2Carton = 1.44,
                        pcs = 2,
                        prixUnitaire = 1800.0,
                        prixAchat = 1350.0,
                        cartons = 6,
                        pieces = 0,
                        m2Total = 8.64,
                        sousTotal = 15552.0
                    )
                ),
                lignesLibres = emptyList(),
                totalBrut = 15552.0,
                remise = 0.0,
                totalNet = 15552.0,
                montantPaye = 15552.0,
                resteAPayer = 0.0,
                modePaiement = "Espèces",
                statut = "Payé"
            )
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.commitSaleBatch(
                    userId = aliceUid,
                    invoice = inv1,
                    stockDeltas = mapOf(prodId to -6),
                    updatedProductStocks = mapOf(prodId to 4),
                    caisseMovement = CaisseMovement(
                        id = "cm_${UUID.randomUUID().toString().take(8)}",
                        date = "2026-11-12T11:00:00.000Z",
                        type = "entree",
                        motif = "Vente KC-SYNC-001",
                        montant = 15552.0,
                        auteur = "Zohir",
                        referenceFacture = "KC-SYNC-001"
                    ),
                    updatedCustomer = null,
                    notifications = listOf(
                        DepotNotification(
                            id = "notif_${UUID.randomUUID().toString().take(8)}",
                            invoiceId = invId1,
                            reference = "KC-SYNC-001",
                            date = "2026-11-12T11:00:00.000Z",
                            vendeur = "Zohir",
                            magasin = "Depot Remchi",
                            client = "Client A",
                            clientTel = "",
                            totalNet = 15552.0,
                            m2Total = 8.64,
                            cartonsTotal = 6,
                            articlesSummary = "120/60 REALTIME SYNC (6 crt)"
                        )
                    )
                )
            }
            awaitStock(4)

            // Second sale on another device decrements into negative stock atomically
            val invId2 = "inv_sync2_${UUID.randomUUID().toString().take(8)}"
            val inv2 = inv1.copy(id = invId2, reference = "KC-SYNC-002", vendeur = "Celia")
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.commitSaleBatch(
                    userId = aliceUid,
                    invoice = inv2,
                    stockDeltas = mapOf(prodId to -8),
                    updatedProductStocks = mapOf(prodId to -4),
                    caisseMovement = null,
                    updatedCustomer = null,
                    notifications = emptyList()
                )
            }
            awaitStock(-4)

            // Deleting first invoice atomically restores +6 cartons across live listeners
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.commitDeleteInvoiceBatch(
                    userId = aliceUid,
                    invoiceId = invId1,
                    restoredStockDeltas = mapOf(prodId to 6),
                    updatedCustomer = null,
                    notification = null
                )
            }
            awaitStock(2)
        } finally {
            observerJob.cancel()
            stockChannel.close()
        }
    }

    @Test
    fun ownerCanWriteCustomerSupplierMovementAndSettings() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestorePosRepository(firestore)

        val custId = "cust_${UUID.randomUUID().toString().take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertCustomer(aliceUid, Customer(custId, "Client Test", "0550112233", "Remchi", 1000.0, 5000.0))
        }

        val supId = "sup_${UUID.randomUUID().toString().take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertSupplier(aliceUid, Supplier(supId, "BCRAM Test", "Contact", "041000000", "Oran", "BCRAM", 50000.0, 100000.0))
            repo.createSupplierMovement(
                aliceUid,
                SupplierMovement(
                    id = "mov_${UUID.randomUUID().toString().take(8)}",
                    supplierId = supId,
                    date = "2026-11-12T10:00:00.000Z",
                    type = "versement",
                    montant = 20000.0,
                    motif = "Acompte test",
                    pieceJointeRef = null,
                    auteur = "Kader"
                )
            )
            repo.saveStoreInfo(aliceUid, PosSeeds.DEFAULT_STORE_INFO)
            repo.saveAppSettings(aliceUid, AppSettings())
        }
    }

    @Test
    fun rolePoliciesAndStaffPermissionsSeedObserveAndUpdateOnEmulator() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestorePosRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserAccount(aliceUid, ALICE_EMAIL, "Alice Owner")
            repo.seedDefaultsIfEmpty(
                userId = aliceUid,
                staffUsers = PosSeeds.DEFAULT_USERS,
                customers = PosSeeds.DEFAULT_CUSTOMERS,
                suppliers = PosSeeds.DEFAULT_SUPPLIERS,
                caisseMovements = emptyList(),
                storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                settings = PosSeeds.DEFAULT_SETTINGS,
                rolePolicies = PosSeeds.DEFAULT_ROLE_POLICIES
            )
            // Normalize default vendeur policy in case a previous run left test state in the emulator
            repo.upsertRolePolicy(aliceUid, PosSeeds.defaultPolicyForRole("vendeur"))
        }

        val initialPolicies = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeRolePolicies(aliceUid).first { list ->
                list.size == 3 && list.any { it.roleId == "vendeur" && !it.canModifyPrices }
            }
        }
        val sellerPolicy = initialPolicies.first { it.roleId == "vendeur" }
        assertEquals(false, sellerPolicy.canViewPrixAchat)
        assertEquals(false, sellerPolicy.canModifyPrices)
        assertEquals(false, sellerPolicy.canModifyStock)

        val adminPolicy = initialPolicies.first { it.roleId == "admin" }
        assertTrue(adminPolicy.canViewPrixAchat)
        assertTrue(adminPolicy.canModifyPrices)
        assertTrue(adminPolicy.canModifyStock)
        assertTrue(adminPolicy.canManageUsers)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertRolePolicy(aliceUid, sellerPolicy.copy(canModifyPrices = true))
        }
        val updatedPolicies = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeRolePolicies(aliceUid).first { list ->
                list.any { it.roleId == "vendeur" && it.canModifyPrices }
            }
        }
        assertTrue(updatedPolicies.first { it.roleId == "vendeur" }.canModifyPrices)

        // Restore default vendeur policy
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertRolePolicy(aliceUid, PosSeeds.defaultPolicyForRole("vendeur"))
        }

        val initialStaff = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeStaffUsers(aliceUid).first { it.isNotEmpty() }
        }
        val sellerUser = initialStaff.first { it.role == "vendeur" }
        assertEquals(false, sellerUser.canViewPrixAchat)
        assertEquals(false, sellerUser.canModifyPrices)
        assertEquals(false, sellerUser.canModifyStock)
    }

    @Test
    fun crossUserAccessFailsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestorePosRepository(firestore)
        val invId = "inv_${UUID.randomUUID().toString().take(8)}"
        val invoice = Invoice(
            id = invId,
            reference = "KC-PRIV-001",
            date = "2026-11-12T10:00:00.000Z",
            vendeur = "Kader",
            magasin = "Depot Remchi",
            client = InvoiceClient(nom = "Private Client"),
            articles = emptyList(),
            lignesLibres = emptyList(),
            totalBrut = 1000.0,
            remise = 0.0,
            totalNet = 1000.0,
            montantPaye = 1000.0,
            resteAPayer = 0.0,
            modePaiement = "Espèces",
            statut = "Payé"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.upsertInvoice(aliceUid, invoice) }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FirestorePosRepository(firestore)
        var captured: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getInvoiceById(aliceUid, invId) }
        } catch (e: Exception) {
            captured = e.unwrapFirestoreException()
        }
        assertNotNull(captured)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, captured?.code)
    }

    @Test
    fun unauthenticatedObserveProductsFailsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestorePosRepository(firestore)
        var captured: FirebaseFirestoreException? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeProducts("alice_unauth_test").first() }
        } catch (e: Exception) {
            captured = e.unwrapFirestoreException()
        }
        assertNotNull(captured)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, captured?.code)
    }

    @Test
    fun mergeMissingOfflineDataUploadsLocalRecordsWithoutOverwritingRemoteDocs() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestorePosRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserAccount(aliceUid, ALICE_EMAIL, "Alice Owner")
        }

        val remoteId = "p_remote_${UUID.randomUUID().toString().take(8)}"
        val offlineId = "p_offline_${UUID.randomUUID().toString().take(8)}"
        val remoteProd = TileProduct(
            id = remoteId,
            name = "REMOTE KEEP PRODUCT",
            usine = "BATI",
            dimension = "60×60",
            m2Carton = 1.44,
            pcs = 4,
            prix = 1400.0,
            prixAchat = 1000.0,
            stock = 50
        )
        val offlineProd = TileProduct(
            id = offlineId,
            name = "OFFLINE CREATED PRODUCT",
            usine = "CERAMICA",
            dimension = "45×45",
            m2Carton = 1.62,
            pcs = 8,
            prix = 950.0,
            prixAchat = 700.0,
            stock = 12
        )
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertProduct(aliceUid, remoteProd) }

        val offlineInvId = "inv_off_${UUID.randomUUID().toString().take(8)}"
        val offlineInvoice = Invoice(
            id = offlineInvId,
            reference = "KC-OFF-001",
            date = "2026-11-13T09:00:00.000Z",
            vendeur = "Kader",
            magasin = "Depot Remchi",
            client = InvoiceClient(nom = "Client Offline"),
            articles = emptyList(),
            lignesLibres = emptyList(),
            totalBrut = 2500.0,
            remise = 0.0,
            totalNet = 2500.0,
            montantPaye = 2500.0,
            resteAPayer = 0.0,
            modePaiement = "Espèces",
            statut = "Payé"
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.mergeMissingOfflineData(
                userId = aliceUid,
                products = listOf(remoteProd.copy(stock = 10), offlineProd),
                customers = emptyList(),
                suppliers = emptyList(),
                invoices = listOf(offlineInvoice),
                caisseMovements = emptyList(),
                supplierMovements = emptyList(),
                dailyCaisseCounts = emptyList(),
                notifications = emptyList()
            )
            repo.waitForPendingWrites()
        }

        val observedProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts(aliceUid).first { list ->
                list.any { it.id == remoteId } && list.any { it.id == offlineId }
            }
        }
        assertEquals(50, observedProducts.first { it.id == remoteId }.stock)
        assertEquals(12, observedProducts.first { it.id == offlineId }.stock)

        val uploadedInv = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getInvoiceById(aliceUid, offlineInvId) }
        assertNotNull(uploadedInv)
        assertEquals("KC-OFF-001", uploadedInv?.reference)
    }

    @Test
    fun amineSaleInAnotherCityCirculatesProductStockInvoiceWhatsappAndOutOfAppNotificationToAdmin() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("ceramica_notifier_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

        val sharedAccountUid = signInTestUser(ALICE_EMAIL)
        val firestoreRepo = FirestorePosRepository(firestore)

        // Two completely separate local Room DBs: Device A (Amine in Oran) & Device B (Kader Admin at Depot Remchi)
        val dbAmine = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dbAdmin = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

        try {
            val repoAmine = PosRepository(context, dbAmine)
            val repoAdmin = PosRepository(context, dbAdmin)
            val prodId = "p_amine_circ_${UUID.randomUUID().toString().take(6)}"
            val product = TileProduct(
                id = prodId,
                name = "60/60 BATI LUXOR ORAN",
                usine = "BATI",
                dimension = "60×60",
                m2Carton = 1.44,
                pcs = 4,
                prix = 1200.0,
                prixAchat = 950.0,
                stock = 30
            )

            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestoreRepo.ensureUserAccount(sharedAccountUid, ALICE_EMAIL, "CERAMICA Workspace")
                firestoreRepo.upsertProduct(sharedAccountUid, product)
            }
            repoAmine.upsertProduct(product)
            repoAdmin.upsertProduct(product)

            // 1. Amine (Seller in Magasin Oran) makes a sale & sends WhatsApp invoice photo
            val invId = "inv_amine_${UUID.randomUUID().toString().take(8)}"
            val line = CartItem(
                id = "line_amine_1",
                productId = prodId,
                name = product.name,
                usine = product.usine,
                dimension = product.dimension,
                m2Carton = 1.44,
                pcs = 4,
                prixUnitaire = 1200.0,
                prixAchat = 950.0,
                cartons = 6,
                pieces = 0,
                m2Total = 8.64,
                sousTotal = 10368.0
            )
            val amineInvoice = Invoice(
                id = invId,
                reference = "CRM-AMINE-001",
                date = "2026-11-13T17:00:00.000Z",
                vendeur = "Amine",
                vendeurTel = "0550998877",
                magasin = "Magasin Oran",
                client = InvoiceClient(nom = "Karim Oran", tel = "0770112233", adresse = "Oran"),
                articles = listOf(line),
                lignesLibres = emptyList(),
                totalBrut = 10368.0,
                remise = 0.0,
                totalNet = 10368.0,
                montantPaye = 8000.0,
                resteAPayer = 2368.0,
                modePaiement = "Espèces",
                statut = "Partiel",
                whatsappSentBeforePrint = false
            )

            val commit = repoAmine.saveNewInvoice(amineInvoice)
            assertEquals(24, commit.updatedProductStocks[prodId])

            // Verify WhatsApp invoice photo & management message generated on Amine's device
            val pngFile = InvoicePdfGenerator.generateAndSaveInvoicePhotoPng(
                context = context,
                invoice = commit.invoice,
                storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                hideBain = true,
                hidePrices = false
            )
            assertTrue(pngFile.exists() && pngFile.length() > 1000L)
            val waCaption = buildWhatsAppManagementMessage(commit.invoice)
            assertTrue(waCaption.contains("Amine"))
            assertTrue(waCaption.contains("Magasin Oran"))
            assertTrue(waCaption.contains("60/60 BATI LUXOR ORAN"))
            assertTrue(waCaption.contains("0770112233"))

            // Sync Amine's sale batch + WhatsApp sent flag to Firestore
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestoreRepo.commitSaleBatch(
                    userId = sharedAccountUid,
                    invoice = commit.invoice,
                    stockDeltas = commit.stockDeltas,
                    updatedProductStocks = commit.updatedProductStocks,
                    caisseMovement = commit.caisseMovement,
                    updatedCustomer = commit.updatedCustomer,
                    notifications = listOf(commit.notification) + commit.extraNotifications,
                    fallbackProductsById = mapOf(prodId to product)
                )
                firestoreRepo.markInvoiceWhatsappSent(sharedAccountUid, invId)
            }

            // 2. Kader (Admin at Depot Remchi on Device B) listens in real time
            val syncedProducts = withTimeout(FLOW_TIMEOUT_MS) {
                firestoreRepo.observeProducts(sharedAccountUid).first { list ->
                    list.any { it.id == prodId && it.stock == 24 }
                }
            }
            assertEquals(24, syncedProducts.first { it.id == prodId }.stock)

            val syncedInvoices = withTimeout(FLOW_TIMEOUT_MS) {
                firestoreRepo.observeInvoices(sharedAccountUid).first { list ->
                    list.any { it.id == invId && it.whatsappSentBeforePrint }
                }
            }
            val adminInvoice = syncedInvoices.first { it.id == invId }
            assertEquals("Amine", adminInvoice.vendeur)
            assertEquals("Magasin Oran", adminInvoice.magasin)
            assertEquals(10368.0, adminInvoice.totalNet, 0.01)
            assertTrue(adminInvoice.whatsappSentBeforePrint)

            val syncedNotifications = withTimeout(FLOW_TIMEOUT_MS) {
                firestoreRepo.observeNotifications(sharedAccountUid).first { list ->
                    list.any { it.id == commit.notification.id }
                }
            }
            repoAdmin.replaceNotificationsFromCloud(syncedNotifications)
            assertFalse(repoAdmin.getNotificationsSnapshot().first { it.id == commit.notification.id }.read)

            // 3. Out-of-app system notification fires on Admin's phone when outside app UI
            val posted = SaleSystemNotifier.processCloudNotifications(
                context = context,
                notifications = syncedNotifications,
                isAdminOrManager = true
            )
            assertTrue(posted.any { it.id == commit.notification.id })

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val shadowNm = Shadows.shadowOf(nm)
            val androidNotifs = shadowNm.allNotifications
            assertTrue("Expected out-of-app Android notification for Admin", androidNotifs.isNotEmpty())
            val titleText = androidNotifs.last().extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            val bodyText = androidNotifs.last().extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
            assertTrue("Notification title should mention Amine & city: $titleText", titleText.contains("Amine") && titleText.contains("Magasin Oran"))
            assertTrue("Notification body should mention invoice & client: $bodyText", bodyText.contains("CRM-AMINE-001") && bodyText.contains("Karim Oran"))
        } finally {
            dbAmine.close()
            dbAdmin.close()
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.pos@test.com"
        const val BOB_EMAIL = "bob.pos@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
