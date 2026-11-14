package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.CartItem
import com.example.data.model.DepotNotification
import com.example.data.model.Invoice
import com.example.data.model.InvoiceClient
import com.example.data.model.PosSeeds
import com.example.data.model.TileCalculatorMath
import com.example.data.model.TileProduct
import com.example.data.repository.PosRepository
import com.example.util.SaleSystemNotifier
import com.example.ui.PosViewModel
import com.example.ui.components.FirestoreInvoiceSummaryCard
import com.example.ui.components.InvoicePdfGenerator
import com.example.ui.components.InvoicePreviewPrintSheet
import com.example.ui.components.PrinterFriendlyInvoiceSummary
import com.example.ui.components.SmartTileCalculatorSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PosScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StockScreen
import com.example.ui.theme.KaderPosTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CeramicaWorkflowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun resetFileProviderCache() {
        runCatching {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @Test
    fun `default 2-user model has PIN-free Vendeur and PIN-0408 Admin without calculator tab`() {
        val users = PosSeeds.DEFAULT_USERS
        assertEquals(2, users.size)
        assertTrue(users.none { it.name.contains("Patron", ignoreCase = true) || it.role.contains("Patron", ignoreCase = true) })

        val admin = users.first { it.role == "admin" }
        assertEquals("0408", admin.pin)
        assertTrue(admin.canViewPrixAchat)
        assertTrue(admin.canManageUsers)
        assertFalse("calculator" in PosSeeds.ALL_TABS)

        val seller = users.first { it.role == "vendeur" }
        assertEquals("", seller.pin)
        assertFalse("Vendeur must NEVER see prixAchat", seller.canViewPrixAchat)
        assertEquals(listOf("pos", "stock"), seller.allowedTabs)
        val sellerPerms = PosSeeds.resolveEffectivePermissions(seller, PosSeeds.DEFAULT_ROLE_POLICIES)
        assertTrue(sellerPerms.canAddProducts)
        assertFalse(sellerPerms.canViewPrixAchat)
        assertFalse(sellerPerms.canModifyPrices)
        assertFalse(sellerPerms.canModifyStock)
    }

    @Test
    fun `negative stock sale succeeds without clamping to zero`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PosRepository(context, db)
            val zeroStockProduct = TileProduct(
                id = "prod_neg_test",
                name = "120/60 CALACATTA GOLD",
                usine = "BATI",
                dimension = "120×60",
                m2Carton = 1.44,
                pcs = 2,
                prix = 3200.0,
                prixAchat = 2400.0,
                stock = 0,
                pricingUnit = "piece",
                prixPiece = 2300.0
            )
            repo.upsertProduct(zeroStockProduct)

            val line = CartItem(
                id = "line_neg_1",
                productId = zeroStockProduct.id,
                name = zeroStockProduct.name,
                usine = zeroStockProduct.usine,
                dimension = zeroStockProduct.dimension,
                m2Carton = zeroStockProduct.m2Carton,
                pcs = zeroStockProduct.pcs,
                prixUnitaire = zeroStockProduct.prix,
                prixAchat = zeroStockProduct.prixAchat,
                cartons = 3,
                pieces = 0,
                m2Total = 4.32,
                sousTotal = 13800.0,
                prixPiece = zeroStockProduct.prixPiece,
                pricingUnit = "piece"
            )
            val invoice = Invoice(
                id = "inv_neg_1",
                reference = "CE-NEG-001",
                date = "2026-11-13T10:00:00.000Z",
                vendeur = "Zohir",
                vendeurTel = "0550112233",
                magasin = "Magasin Zohir",
                client = InvoiceClient(nom = "Client Passager"),
                articles = listOf(line),
                lignesLibres = emptyList(),
                totalBrut = 13800.0,
                remise = 0.0,
                totalNet = 13800.0,
                montantPaye = 13800.0,
                resteAPayer = 0.0,
                modePaiement = "Espèces",
                statut = "Payé"
            )

            val commit = repo.saveNewInvoice(invoice)
            assertEquals(-3, commit.updatedProductStocks[zeroStockProduct.id])
            val reloaded = repo.getProductsSnapshot().first { it.id == zeroStockProduct.id }
            assertEquals(-3, reloaded.stock)
        } finally {
            db.close()
        }
    }

    @Test
    fun `piece mode pricing bills full pieces cleanly when pricingUnit is piece`() {
        // 120x60: 2 pcs/carton (1.44 m2/ctn -> 0.72 m2/piece), 5 pieces * 2300 DA/pc = 11500 DA
        val res = TileCalculatorMath.calculateFromPiecesOnly(
            pieces = 5,
            m2PerCarton = 1.44,
            pcsPerCarton = 2,
            prixParPiece = 2300.0,
            prixPerM2 = 3000.0
        )
        assertEquals(3.60, res.deliveredM2, 0.01)
        assertEquals(11500.0, res.totalDa, 0.01)
    }

    @Test
    fun `room MIGRATION_1_2 preserves rows and adds v2 columns with defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_${System.currentTimeMillis()}.db"
        val helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS products (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        usine TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        m2Carton REAL NOT NULL,
                        pcs INTEGER NOT NULL,
                        prix REAL NOT NULL,
                        prixAchat REAL NOT NULL,
                        stock INTEGER NOT NULL,
                        color TEXT NOT NULL,
                        colorName TEXT NOT NULL,
                        image TEXT NOT NULL,
                        finition TEXT NOT NULL,
                        choix TEXT NOT NULL,
                        categorie TEXT NOT NULL,
                        description TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS invoices (
                        id TEXT NOT NULL PRIMARY KEY,
                        reference TEXT NOT NULL,
                        date TEXT NOT NULL,
                        vendeur TEXT NOT NULL,
                        magasin TEXT NOT NULL,
                        clientJson TEXT NOT NULL,
                        articlesJson TEXT NOT NULL,
                        lignesLibresJson TEXT NOT NULL,
                        totalBrut REAL NOT NULL,
                        remise REAL NOT NULL,
                        totalNet REAL NOT NULL,
                        montantPaye REAL NOT NULL,
                        resteAPayer REAL NOT NULL,
                        modePaiement TEXT NOT NULL,
                        statut TEXT NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS staff_users (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        role TEXT NOT NULL,
                        pin TEXT NOT NULL,
                        magasin TEXT NOT NULL,
                        allowedTabsJson TEXT NOT NULL,
                        canEditInvoices INTEGER NOT NULL,
                        canDeleteInvoices INTEGER NOT NULL,
                        canModifyStock INTEGER NOT NULL,
                        canViewSuppliers INTEGER NOT NULL,
                        canViewCaisse INTEGER NOT NULL,
                        canManageUsers INTEGER NOT NULL,
                        active INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO products VALUES ('p1','Test','BATI','60x60',1.44,4,1200,900,10,'#fff','Blanc','','mat','1er','sol','Desc')")
                db.execSQL("INSERT INTO invoices VALUES ('i1','REF-1','2026-11-12','Zohir','Depot Remchi','{\"nom\":\"Client\"}','[]','[]',1000,0,1000,1000,0,'Espèces','Payé','')")
                db.execSQL("INSERT INTO staff_users VALUES ('u_zohir','Zohir','vendeur','0001','Magasin Zohir','[\"pos\"]',1,0,0,0,0,0,1)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val legacyDb = helper.writableDatabase
        legacyDb.close()

        val supportHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AppDatabase.MIGRATION_1_2.migrate(db)
                    }
                })
                .build()
        )
        try {
            val migrated = supportHelper.writableDatabase
            migrated.query("SELECT pricingUnit, prixPiece FROM products WHERE id='p1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("carton", c.getString(0))
                assertEquals(0.0, c.getDouble(1), 0.001)
            }
            migrated.query("SELECT vendeurTel, hidePrices, whatsappSentBeforePrint FROM invoices WHERE id='i1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("", c.getString(0))
                assertEquals(0, c.getInt(1))
                assertEquals(0, c.getInt(2))
            }
            migrated.query("SELECT phone FROM staff_users WHERE id='u_zohir'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("", c.getString(0))
            }
        } finally {
            supportHelper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `eco-ink PNG photo exports cleanly in standard and hidePrices modes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invoice = sampleInvoice(whatsappSent = false, hidePrices = false)

        val pngStd = InvoicePdfGenerator.generateAndSaveInvoicePhotoPng(
            context = context,
            invoice = invoice,
            storeInfo = PosSeeds.DEFAULT_STORE_INFO,
            hideBain = true,
            hidePrices = false
        )
        assertTrue(pngStd.exists())
        assertTrue(pngStd.length() > 1000L)
        val bmpStd = android.graphics.BitmapFactory.decodeFile(pngStd.absolutePath)
        assertTrue(bmpStd != null && bmpStd.width == 1190 && bmpStd.height >= 400)
        bmpStd?.recycle()

        val pngRev = InvoicePdfGenerator.generateAndSaveInvoicePhotoPng(
            context = context,
            invoice = invoice.copy(hidePrices = true),
            storeInfo = PosSeeds.DEFAULT_STORE_INFO,
            hideBain = true,
            hidePrices = true
        )
        assertTrue(pngRev.exists())
        assertTrue(pngRev.name.contains("_REV.png"))
        assertTrue(pngRev.length() > 1000L)
    }

    @Test
    fun `dashboard hides margin KPI card when user cannot view prixAchat`() {
        val inv = sampleInvoice(whatsappSent = true, hidePrices = false)
        composeTestRule.setContent {
            KaderPosTheme {
                DashboardScreen(
                    invoices = listOf(inv),
                    customers = PosSeeds.DEFAULT_CUSTOMERS,
                    canViewPrixAchat = false
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("kpi_margin_card").fetchSemanticsNodes().size)
    }

    @Test
    fun `invoice preview locks print and pdf buttons until WhatsApp photo sent`() {
        val invoiceLocked = sampleInvoice(whatsappSent = false, hidePrices = false)
        composeTestRule.setContent {
            KaderPosTheme {
                InvoicePreviewPrintSheet(
                    invoice = invoiceLocked,
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                    settings = AppSettings(),
                    canHideInvoicePrices = false,
                    onUpdateSettings = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("invoice_print_pdf_btn").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("invoice_open_pdf_btn").assertIsNotEnabled()
        assertEquals(0, composeTestRule.onAllNodesWithTag("toggle_hide_invoice_prices").fetchSemanticsNodes().size)
    }

    @Test
    fun `invoice preview unlocks print when WhatsApp sent and exposes hidePrices toggle for admins`() {
        val invoiceUnlocked = sampleInvoice(whatsappSent = true, hidePrices = false)
        composeTestRule.setContent {
            KaderPosTheme {
                InvoicePreviewPrintSheet(
                    invoice = invoiceUnlocked,
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                    settings = AppSettings(),
                    canHideInvoicePrices = true,
                    onUpdateSettings = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("invoice_print_pdf_btn").assertIsEnabled()
        composeTestRule.onNodeWithTag("invoice_open_pdf_btn").assertIsEnabled()
        composeTestRule.onNodeWithTag("toggle_hide_invoice_prices").assertExists()
        composeTestRule.onNodeWithText("Livraison Complète / تسليم كامل").assertExists()
        composeTestRule.onNodeWithText("Livraison Partielle / تسليم جزئي").assertExists()
    }

    @Test
    fun `room MIGRATION_2_3 adds role_policies table and explicit staff price flags`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v2_v3_${System.currentTimeMillis()}.db"
        val helper = object : SQLiteOpenHelper(context, dbName, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS staff_users (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        role TEXT NOT NULL,
                        pin TEXT NOT NULL,
                        magasin TEXT NOT NULL,
                        allowedTabsJson TEXT NOT NULL,
                        canEditInvoices INTEGER NOT NULL,
                        canDeleteInvoices INTEGER NOT NULL,
                        canModifyStock INTEGER NOT NULL,
                        canViewSuppliers INTEGER NOT NULL,
                        canViewCaisse INTEGER NOT NULL,
                        canManageUsers INTEGER NOT NULL,
                        active INTEGER NOT NULL,
                        phone TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO staff_users VALUES ('u_kader','Kader','admin','1234','Depot Remchi','[\"pos\"]',1,1,1,1,1,1,1,'0676754124')")
                db.execSQL("INSERT INTO staff_users VALUES ('u_walid','Walid','gerant','2024','Depot Remchi','[\"pos\"]',1,0,1,1,1,0,1,'0550')")
                db.execSQL("INSERT INTO staff_users VALUES ('u_zohir','Zohir','vendeur','0001','Magasin Zohir','[\"pos\"]',0,0,0,0,0,0,1,'0551')")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()

        val supportHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AppDatabase.MIGRATION_2_3.migrate(db)
                    }
                })
                .build()
        )
        try {
            val migrated = supportHelper.writableDatabase
            migrated.query("SELECT id, canViewPrixAchat, canModifyPrices FROM staff_users ORDER BY id").use { c ->
                val map = mutableMapOf<String, Pair<Int, Int>>()
                while (c.moveToNext()) {
                    map[c.getString(0)] = c.getInt(1) to c.getInt(2)
                }
                assertEquals(1 to 1, map["u_kader"])
                assertEquals(1 to 1, map["u_walid"])
                assertEquals(0 to 0, map["u_zohir"])
            }
            migrated.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='role_policies'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        } finally {
            supportHelper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `viewmodel RBAC restricts seller price overrides, stock edits, and admin user management`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PosRepository(context, db)
        repo.ensureSeeded()
        val vmStore = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            vmStore,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    PosViewModel(
                        repository = repo,
                        firestoreRepo = null,
                        currentUserId = null,
                        userEmail = null,
                        userDisplayName = "Kader"
                    ) as T
            }
        )[PosViewModel::class.java]
        val subJob = CoroutineScope(Dispatchers.IO).launch { vm.uiState.collect {} }

        suspend fun awaitState(predicate: (com.example.ui.PosUiState) -> Boolean): com.example.ui.PosUiState {
            return withTimeout(10_000L) {
                while (true) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    val current = vm.uiState.value
                    if (predicate(current)) return@withTimeout current
                    delay(15)
                }
                @Suppress("UNREACHABLE_CODE")
                vm.uiState.value
            }
        }

        try {
            val seededState = awaitState { !it.isLoading && it.products.isNotEmpty() && it.users.isNotEmpty() }
            val catalogProduct = seededState.products.first()
            val adminUser = seededState.users.first { it.role == "admin" }
            assertEquals("0408", adminUser.pin)

            // 1. Boots directly unlocked as PIN-free Vendeur with Vente + Stock add-product access only
            assertFalse("App must start unlocked in Vendeur mode without PIN", seededState.isLocked)
            assertEquals("u_vendeur", seededState.currentUser?.id)
            assertEquals("vendeur", seededState.currentUser?.role)
            assertFalse(seededState.effectivePermissions.canViewPrixAchat)
            assertFalse(seededState.effectivePermissions.canModifyPrices)
            assertFalse(seededState.effectivePermissions.canModifyStock)
            assertTrue(seededState.effectivePermissions.canAddProducts)
            assertFalse(seededState.effectivePermissions.canManageUsers)

            // Seller cannot edit existing catalog product prices
            vm.saveProduct(catalogProduct.copy(prix = 1.0))
            // Seller CAN add a NEW product to Stock, with prixAchat stripped to 0.0
            val sellerNewProd = TileProduct(
                id = "prod_seller_added_1",
                name = "90/90 TRAVERTIN ROYAL",
                usine = "NOUVELLE_USINE",
                dimension = "90×90",
                m2Carton = 1.62,
                pcs = 2,
                prix = 2900.0,
                prixAchat = 9999.0,
                stock = 0,
                finition = "lappato"
            )
            vm.saveProduct(sellerNewProd)

            // Seller calculator call attempting price override must be clamped to catalog price
            vm.confirmCalculatorToCart(
                product = catalogProduct,
                cartons = 2,
                pieces = 0,
                m2Total = catalogProduct.m2Carton * 2,
                prixPerM2 = 10.0,
                bain = "",
                calibre = "",
                editingItemId = null,
                prixPiece = 10.0
            )

            val afterSellerActions = awaitState { state ->
                state.cart.isNotEmpty() && state.products.any { it.id == "prod_seller_added_1" }
            }
            val reloadedCatalogProd = afterSellerActions.products.first { it.id == catalogProduct.id }
            assertEquals(catalogProduct.prix, reloadedCatalogProd.prix, 0.001)
            assertEquals(catalogProduct.prix, afterSellerActions.cart.first().prixUnitaire, 0.001)
            val savedNewProd = afterSellerActions.products.first { it.id == "prod_seller_added_1" }
            assertEquals(0.0, savedNewProd.prixAchat, 0.001)
            assertEquals("90×90", savedNewProd.dimension)
            assertEquals("lappato", savedNewProd.finition)

            // 2. Wrong Admin PIN fails, 0408 unlocks Admin, and Admin can change PIN anytime
            vm.lockSession()
            awaitState { it.isLocked }
            vm.attemptUnlock(adminUser, "9999")
            val badPinState = awaitState { it.pinError != null }
            assertTrue(badPinState.isLocked)

            vm.attemptUnlock(adminUser, "0408")
            val adminState = awaitState { !it.isLocked && it.currentUser?.role == "admin" }
            assertTrue(adminState.effectivePermissions.canViewPrixAchat)
            assertTrue(adminState.effectivePermissions.canModifyPrices)
            assertTrue(adminState.effectivePermissions.canModifyStock)
            assertTrue(adminState.effectivePermissions.canViewSuppliers)
            assertTrue(adminState.effectivePermissions.canViewCaisse)

            assertTrue(vm.changeAdminPin("2580"))
            awaitState { state -> state.users.firstOrNull { it.role == "admin" }?.pin == "2580" }

            // Re-verify old PIN 0408 no longer unlocks, new PIN 2580 works, and Vendeur still needs no PIN
            vm.lockSession()
            awaitState { it.isLocked }
            val updatedAdmin = uiUser(vm, "admin")
            vm.attemptUnlock(updatedAdmin, "0408")
            val rejectedOld = awaitState { it.pinError != null }
            assertTrue(rejectedOld.isLocked)

            vm.attemptUnlock(updatedAdmin, "2580")
            awaitState { !it.isLocked && it.currentUser?.role == "admin" }

            vm.lockSession()
            awaitState { it.isLocked }
            vm.switchToSellerMode()
            val quickSeller = awaitState { !it.isLocked && it.currentUser?.role == "vendeur" }
            assertEquals("u_vendeur", quickSeller.currentUser?.id)
        } finally {
            subJob.cancel()
            vmStore.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(50)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    private fun uiUser(vm: PosViewModel, role: String) =
        vm.uiState.value.users.first { it.role == role }

    @Test
    fun `calculator renders prominent top result card and locks catalog prices when canModifyPrices is false`() {
        val sampleTile = TileProduct(
            id = "p_120_60_calc_test",
            name = "120/60 MARBRE ONYX",
            usine = "BATI",
            dimension = "120×60",
            m2Carton = 1.44,
            pcs = 2,
            prix = 2800.0,
            prixAchat = 2100.0,
            stock = 12,
            pricingUnit = "piece",
            prixPiece = 2300.0
        )
        composeTestRule.setContent {
            KaderPosTheme {
                SmartTileCalculatorSheet(
                    product = sampleTile,
                    editingCartItem = null,
                    isOneTimeProduct = false,
                    canViewPrixAchat = false,
                    canModifyPrices = false,
                    onDismiss = {},
                    onConfirmProductCart = { _, _, _, _, _, _, _, _, _ -> },
                    onConfirmStandaloneFreeLine = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calc_result_hero_card").assertExists()
        composeTestRule.onNodeWithTag("calc_price_field").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("calc_piece_price_field").assertIsNotEnabled()
    }

    @Test
    fun `pos screen renders 3 dynamic filter lines and surfaces newly added dimensions usines and finitions`() {
        val products = listOf(
            TileProduct(
                id = "p_f1",
                name = "60/60 CLASSICO",
                usine = "BATI",
                dimension = "60×60",
                finition = "brillant",
                m2Carton = 1.44,
                pcs = 4,
                prix = 1800.0,
                prixAchat = 1200.0,
                stock = 0
            ),
            TileProduct(
                id = "p_f2",
                name = "90/90 TRAVERTIN",
                usine = "NEW_USINE",
                dimension = "90×90",
                finition = "lappato",
                m2Carton = 1.62,
                pcs = 2,
                prix = 3200.0,
                prixAchat = 2400.0,
                stock = 0
            )
        )
        composeTestRule.setContent {
            KaderPosTheme {
                PosScreen(
                    products = products,
                    cart = emptyList(),
                    freeLines = emptyList(),
                    customers = PosSeeds.DEFAULT_CUSTOMERS,
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                    editingInvoice = null,
                    canModifyPrices = false,
                    onOpenCalculatorForProduct = {},
                    onRequestDepotDispo = {},
                    onOpenOneTimeProduct = {},
                    onEditCartItem = {},
                    onUpdateCartItemDirect = { _, _, _, _, _, _, _, _ -> },
                    onAdjustCartItemCarton = { _, _ -> },
                    onRemoveCartItem = {},
                    onAddFreeLine = {},
                    onUpdateFreeLine = {},
                    onAdjustFreeLineQty = { _, _ -> },
                    onRemoveFreeLine = {},
                    onClearCart = {},
                    onFinalizeSale = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pos_filter_dim_Toutes").assertExists()
        composeTestRule.onNodeWithTag("pos_filter_dim_90×90").assertExists()
        composeTestRule.onNodeWithTag("pos_filter_usine_Toutes").assertExists()
        composeTestRule.onNodeWithTag("pos_filter_usine_NEW_USINE").assertExists()
        composeTestRule.onNodeWithTag("pos_filter_finition_Toutes").assertExists()
        composeTestRule.onNodeWithTag("pos_filter_finition_lappato").assertExists()

        // Clicking new dimension chip filters out 60x60 product
        composeTestRule.onNodeWithTag("pos_filter_dim_90×90").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("90/90 TRAVERTIN").assertExists()
    }

    @Test
    fun `stock screen lets Vendeur add new product without prixAchat while Admin sees prixAchat`() {
        val sampleProd = TileProduct(
            id = "stk_rbac_1",
            name = "60/60 CREMA",
            usine = "BATI",
            dimension = "60×60",
            finition = "mat",
            m2Carton = 1.44,
            pcs = 4,
            prix = 1850.0,
            prixAchat = 1350.0,
            stock = 0
        )
        composeTestRule.setContent {
            KaderPosTheme {
                StockScreen(
                    products = listOf(sampleProd),
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                    canModifyStock = false,
                    canAddProducts = true,
                    canViewPrixAchat = false,
                    onAdjustStock = { _, _ -> },
                    onSetStock = { _, _ -> },
                    onSaveProduct = {},
                    onRequestDepotDispo = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stock_add_model_btn").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("product_fiche_dialog").assertExists()
        composeTestRule.onNodeWithTag("fiche_finition_input").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("fiche_prix_achat_field").fetchSemanticsNodes().size)
    }

    @Test
    fun `offline finalizeSale opens preview immediately, flags pendingOfflineSync, and tracks live network state`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PosRepository(context, db)
        repo.ensureSeeded()
        repo.setPendingOfflineSync(false)

        val networkFlow = MutableStateFlow(false)
        val vmStore = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            vmStore,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    PosViewModel(
                        repository = repo,
                        firestoreRepo = null,
                        currentUserId = null,
                        userEmail = null,
                        userDisplayName = "Kader",
                        isNetworkConnectedFlow = networkFlow
                    ) as T
            }
        )[PosViewModel::class.java]
        val subJob = CoroutineScope(Dispatchers.IO).launch { vm.uiState.collect {} }

        suspend fun awaitState(predicate: (com.example.ui.PosUiState) -> Boolean): com.example.ui.PosUiState {
            return withTimeout(10_000L) {
                while (true) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    val current = vm.uiState.value
                    if (predicate(current)) return@withTimeout current
                    delay(15)
                }
                @Suppress("UNREACHABLE_CODE")
                vm.uiState.value
            }
        }

        try {
            val ready = awaitState { !it.isLoading && it.users.isNotEmpty() && !it.isNetworkAvailable }
            assertFalse(ready.isNetworkAvailable)
            assertFalse(ready.isCloudSynced)

            val admin = ready.users.first { it.role == "admin" }
            vm.attemptUnlock(admin, admin.pin)
            val unlocked = awaitState { !it.isLocked && it.currentUser?.role == "admin" }
            val prod = unlocked.products.first()

            vm.confirmCalculatorToCart(
                product = prod,
                cartons = 2,
                pieces = 0,
                m2Total = prod.m2Carton * 2,
                prixPerM2 = prod.prix,
                bain = "",
                calibre = "",
                editingItemId = null
            )
            awaitState { it.cart.isNotEmpty() }

            vm.finalizeSale(
                client = InvoiceClient(nom = "Client Local"),
                remise = 0.0,
                montantPaye = 1000.0,
                modePaiement = "Espèces",
                notes = ""
            )

            val afterSale = awaitState { state ->
                state.previewInvoice != null && state.invoices.any { it.client.nom == "Client Local" }
            }
            assertTrue(afterSale.previewInvoice != null)
            assertTrue(afterSale.invoices.any { it.client.nom == "Client Local" })

            networkFlow.value = true
            val reconnected = awaitState { it.isNetworkAvailable }
            assertTrue(reconnected.isNetworkAvailable)
            assertFalse(reconnected.isCloudSynced)
        } finally {
            subJob.cancel()
            vmStore.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(50)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    @Test
    fun `settings screen shows Admin PIN editor, dual WhatsApp numbers, and no online sync controls`() {
        composeTestRule.setContent {
            KaderPosTheme {
                SettingsScreen(
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                    settings = AppSettings(),
                    autoWhatsappPhoto = true,
                    isAdmin = true,
                    onChangeAdminPin = { true },
                    onSaveStoreInfo = {},
                    onSaveSettings = {},
                    onSetAutoWhatsappPhoto = {},
                    onResetCatalog = {},
                    onExportJson = {},
                    onImportJson = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_new_pin_input").assertExists()
        composeTestRule.onNodeWithTag("settings_save_pin_btn").assertExists()
        composeTestRule.onNodeWithTag("settings_wa_principal_input").assertExists()
        composeTestRule.onNodeWithTag("settings_wa_gerant_input").assertExists()
        composeTestRule.onNodeWithTag("settings_auto_wa_photo_switch").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("settings_cloud_sync_btn").fetchSemanticsNodes().size)
    }

    @Test
    fun `out-of-app system notifier alerts admin on remote seller sale and skips seller self-notification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val prefs = context.getSharedPreferences("ceramica_notifier_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        nm.cancelAll()

        val amineSaleNotif = DepotNotification(
            id = "notif_amine_remote_1",
            invoiceId = "inv_amine_1",
            reference = "[VENTE] CRM-AMINE-99",
            date = "2026-11-13T17:10:00.000Z",
            vendeur = "Amine",
            magasin = "Magasin Oran",
            client = "Client Tlemcen",
            clientTel = "0550123456",
            totalNet = 21600.0,
            m2Total = 14.4,
            cartonsTotal = 10,
            articlesSummary = "10 ctn 60/60 BATI LUXOR (14,40 m²)",
            read = false
        )

        // On Seller's own device: local ID was recorded and seller is not admin -> 0 system notifications
        SaleSystemNotifier.recordLocalNotificationIds(context, listOf(amineSaleNotif.id))
        val sellerPosted = SaleSystemNotifier.processCloudNotifications(
            context = context,
            notifications = listOf(amineSaleNotif),
            isAdminOrManager = false
        )
        assertTrue(sellerPosted.isEmpty())
        assertEquals(0, shadowNm.allNotifications.size)

        // Simulate Kader's Admin phone (separate device prefs, not recorded locally)
        prefs.edit().remove("locally_created_ids").commit()
        val adminPosted = SaleSystemNotifier.processCloudNotifications(
            context = context,
            notifications = listOf(amineSaleNotif),
            isAdminOrManager = true
        )
        assertEquals(1, adminPosted.size)
        assertEquals(1, shadowNm.allNotifications.size)

        val posted = shadowNm.allNotifications.first()
        assertEquals(SaleSystemNotifier.CHANNEL_SALES_ALERTS, posted.channelId)
        val title = posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        assertTrue(title.contains("Amine"))
        assertTrue(title.contains("Magasin Oran"))
        assertTrue(bigText.contains("60/60 BATI LUXOR"))

        // Second poll with same notification ID must NOT duplicate
        val secondPass = SaleSystemNotifier.processCloudNotifications(
            context = context,
            notifications = listOf(amineSaleNotif),
            isAdminOrManager = true
        )
        assertTrue(secondPass.isEmpty())
        assertEquals(1, shadowNm.allNotifications.size)
    }

    private fun sampleInvoice(whatsappSent: Boolean, hidePrices: Boolean) = Invoice(
        id = "inv_sample_1",
        reference = "CE-261113-001",
        date = "2026-11-13T10:00:00.000Z",
        vendeur = "Kader",
        vendeurTel = "0676754124",
        magasin = "Depot Remchi",
        client = InvoiceClient(nom = "Ahmed Benali", tel = "0550123456"),
        articles = listOf(
            CartItem(
                id = "a1",
                productId = "p1",
                name = "120/60 MARBRE ONYX",
                usine = "BATI",
                dimension = "120×60",
                m2Carton = 1.44,
                pcs = 2,
                prixUnitaire = 2800.0,
                prixAchat = 2100.0,
                cartons = 4,
                pieces = 0,
                m2Total = 5.76,
                sousTotal = 18400.0,
                prixPiece = 2300.0,
                pricingUnit = "piece"
            )
        ),
        lignesLibres = emptyList(),
        totalBrut = 18400.0,
        remise = 0.0,
        totalNet = 18400.0,
        montantPaye = 18400.0,
        resteAPayer = 0.0,
        modePaiement = "Espèces",
        statut = "Payé",
        hidePrices = hidePrices,
        whatsappSentBeforePrint = whatsappSent
    )

    @Test
    fun `reusable FirestoreInvoiceSummaryCard renders printer-friendly layout and reseller hidePrices mode`() {
        val stdInv = sampleInvoice(whatsappSent = true, hidePrices = false)
        composeTestRule.setContent {
            KaderPosTheme {
                FirestoreInvoiceSummaryCard(
                    userId = "",
                    invoiceId = stdInv.id,
                    firestoreRepo = null,
                    initialInvoice = stdInv,
                    initialStoreInfo = PosSeeds.DEFAULT_STORE_INFO,
                    onPrintClick = { _, _ -> },
                    onSharePhotoAndPdfClick = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("firestore_invoice_summary_card").assertExists()
        composeTestRule.onNodeWithTag("printer_friendly_invoice_summary").assertExists()
        composeTestRule.onNodeWithText("N° CE-261113-001").assertExists()
        composeTestRule.onNodeWithText("Vendeur / البائع: Kader").assertExists()
        composeTestRule.onNodeWithText("Livraison Complète / تسليم كامل").assertExists()
        composeTestRule.onNodeWithText("Livraison Partielle / تسليم جزئي").assertExists()
        composeTestRule.onNodeWithTag("summary_wa_photo_pdf_btn").assertExists()
        composeTestRule.onNodeWithTag("summary_print_btn").assertExists()
    }

    @Test
    fun `printer-friendly summary hides financial totals in revendeur hidePrices mode`() {
        val revInv = sampleInvoice(whatsappSent = true, hidePrices = true)
        composeTestRule.setContent {
            KaderPosTheme {
                PrinterFriendlyInvoiceSummary(
                    invoice = revInv,
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("printer_friendly_invoice_summary").assertExists()
        composeTestRule.onNodeWithText("BON DE LIVRAISON / وصل تسليم").assertExists()
        composeTestRule.onNodeWithText("Prix masqués (Revendeur)").assertExists()
    }

    @Test
    fun `shareInvoicePhotoAndPdfViaWhatsApp dispatches ACTION_SEND_MULTIPLE with both PNG and PDF URIs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invoice = sampleInvoice(whatsappSent = false, hidePrices = false)
        val png = InvoicePdfGenerator.generateAndSaveInvoicePhotoPng(
            context = context,
            invoice = invoice,
            storeInfo = PosSeeds.DEFAULT_STORE_INFO,
            hideBain = true,
            hidePrices = false
        )
        val pdfDir = java.io.File(context.filesDir, "invoices_pdf").apply { mkdirs() }
        val pdf = java.io.File(pdfDir, "Facture_${invoice.reference}.pdf").apply {
            writeText("%PDF-1.4\n%CERAMICA_TEST_PDF\n")
        }

        InvoicePdfGenerator.shareInvoicePhotoAndPdfViaWhatsApp(
            context = context,
            pngFile = png,
            pdfFile = pdf,
            phone213 = null,
            message = "Facture test",
            invoiceRef = invoice.reference
        )

        val nextIntent = Shadows.shadowOf(context as Application).nextStartedActivity
        assertTrue("Expected share intent to be launched", nextIntent != null)
        val actualIntent: Intent = if (nextIntent.action == Intent.ACTION_CHOOSER) {
            if (Build.VERSION.SDK_INT >= 33) {
                nextIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            } else {
                @Suppress("DEPRECATION")
                nextIntent.getParcelableExtra(Intent.EXTRA_INTENT)!!
            }
        } else {
            nextIntent
        }
        assertEquals(Intent.ACTION_SEND_MULTIPLE, actualIntent.action)
        val streams: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
            actualIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            actualIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        assertEquals(2, streams?.size)
        assertTrue(streams!![0].toString().endsWith(".png") || streams[1].toString().endsWith(".png"))
        assertTrue(streams[0].toString().endsWith(".pdf") || streams[1].toString().endsWith(".pdf"))
        assertEquals(2, actualIntent.clipData?.itemCount)
        assertTrue((actualIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun `stock screen Dispo Depot opens recipient picker and supports Grid and List views`() {
        val sampleProd = TileProduct(
            id = "stk_test_1",
            name = "60/60 PORCELAINE CREMA",
            usine = "BATI",
            dimension = "60×60",
            m2Carton = 1.44,
            pcs = 4,
            prix = 1850.0,
            prixAchat = 1350.0,
            stock = 12
        )
        var requestedDispoId: String? = null
        composeTestRule.setContent {
            KaderPosTheme {
                StockScreen(
                    products = listOf(sampleProd),
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                    canModifyStock = true,
                    canAddProducts = true,
                    canViewPrixAchat = true,
                    onAdjustStock = { _, _ -> },
                    onSetStock = { _, _ -> },
                    onSaveProduct = {},
                    onRequestDepotDispo = { requestedDispoId = it.id }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stock_catalog_list").assertExists()

        // Switch to Grid view and verify grid renders
        composeTestRule.onNodeWithTag("stock_view_grid_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stock_catalog_grid").assertExists()

        // Click Dispo Depot -> triggers callback AND opens WhatsApp recipient chooser dialog
        composeTestRule.onNodeWithTag("stock_dispo_btn_${sampleProd.id}").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(sampleProd.id, requestedDispoId)
        composeTestRule.onNodeWithTag("dispo_recipients_dialog").assertExists()
    }

    @Test
    fun `pos screen switches between Grid and List catalog views and opens Dispo Depot dialog`() {
        val sampleProd = TileProduct(
            id = "pos_test_1",
            name = "120/60 TRAVERTINO",
            usine = "BCRAM",
            dimension = "120×60",
            m2Carton = 1.44,
            pcs = 2,
            prix = 3100.0,
            prixAchat = 2400.0,
            stock = 8,
            pricingUnit = "piece",
            prixPiece = 2200.0
        )
        var requestedDispoId: String? = null
        composeTestRule.setContent {
            KaderPosTheme {
                PosScreen(
                    products = listOf(sampleProd),
                    cart = emptyList(),
                    freeLines = emptyList(),
                    customers = PosSeeds.DEFAULT_CUSTOMERS,
                    storeInfo = PosSeeds.DEFAULT_STORE_INFO,
                    editingInvoice = null,
                    canModifyPrices = true,
                    onOpenCalculatorForProduct = {},
                    onRequestDepotDispo = { requestedDispoId = it.id },
                    onOpenOneTimeProduct = {},
                    onEditCartItem = {},
                    onUpdateCartItemDirect = { _, _, _, _, _, _, _, _ -> },
                    onAdjustCartItemCarton = { _, _ -> },
                    onRemoveCartItem = {},
                    onAddFreeLine = {},
                    onUpdateFreeLine = {},
                    onAdjustFreeLineQty = { _, _ -> },
                    onRemoveFreeLine = {},
                    onClearCart = {},
                    onFinalizeSale = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pos_catalog_grid").assertExists()

        composeTestRule.onNodeWithTag("pos_view_list_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pos_catalog_list").assertExists()

        composeTestRule.onNodeWithTag("btn_dispo_${sampleProd.id}").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(sampleProd.id, requestedDispoId)
        composeTestRule.onNodeWithTag("dispo_recipients_dialog").assertExists()
    }

    @Test
    fun `invoice preview auto-dispatches PNG photo and PDF file to 0676754124 without text and unlocks printing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invoice = sampleInvoice(whatsappSent = false, hidePrices = false)
        var markedInvoiceId: String? = null
        InvoicePdfGenerator.pdfFileFactoryOverride = { ctx, inv, _, _, _, _ ->
            val dir = java.io.File(ctx.filesDir, "invoices_pdf").apply { mkdirs() }
            java.io.File(dir, "Facture_${inv.reference}.pdf").apply {
                writeText("%PDF-1.4\n%CERAMICA_AUTO_TEST\n")
            }
        }

        try {
            composeTestRule.setContent {
                KaderPosTheme {
                    InvoicePreviewPrintSheet(
                        invoice = invoice,
                        storeInfo = PosSeeds.DEFAULT_STORE_INFO.copy(gerantWaPhone = "0550112233"),
                        settings = AppSettings(),
                        autoWhatsappPhoto = true,
                        onMarkWhatsappSent = { markedInvoiceId = it },
                        onUpdateSettings = {},
                        onDismiss = {}
                    )
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5000) {
                Shadows.shadowOf(context as Application).peekNextStartedActivity() != null
            }

            val launchedIntent = Shadows.shadowOf(context as Application).nextStartedActivity
            assertTrue("Expected auto WhatsApp share intent", launchedIntent != null)
            val actualIntent: Intent = if (launchedIntent.action == Intent.ACTION_CHOOSER) {
                if (Build.VERSION.SDK_INT >= 33) {
                    launchedIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
                } else {
                    @Suppress("DEPRECATION")
                    launchedIntent.getParcelableExtra(Intent.EXTRA_INTENT)!!
                }
            } else {
                launchedIntent
            }

            assertEquals(Intent.ACTION_SEND_MULTIPLE, actualIntent.action)
            assertEquals("213676754124@s.whatsapp.net", actualIntent.getStringExtra("jid"))
            assertFalse("Expected NO text caption on invoice PNG+PDF dispatch", actualIntent.hasExtra(Intent.EXTRA_TEXT))
            val streams: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
                actualIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                actualIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            assertEquals(2, streams?.size)
            assertTrue(streams!!.any { it.toString().endsWith(".png") })
            assertTrue(streams.any { it.toString().endsWith(".pdf") })
            assertEquals(invoice.id, markedInvoiceId)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("invoice_wa_gerant_photo_btn").assertExists()
            composeTestRule.onNodeWithTag("invoice_print_pdf_btn").assertIsEnabled()
            composeTestRule.onNodeWithTag("invoice_open_pdf_btn").assertIsEnabled()
            composeTestRule.onNodeWithTag("invoice_gmail_pdf_btn").assertDoesNotExist()
        } finally {
            InvoicePdfGenerator.pdfFileFactoryOverride = null
        }
    }
}
