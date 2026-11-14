package com.example.data.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

data class WaRecipient(
    val name: String = "",
    val phone: String = ""
)

data class TileProduct(
    val id: String,
    val name: String,
    val usine: String,
    val dimension: String,
    val m2Carton: Double,
    val pcs: Int,
    val prix: Double,
    val prixAchat: Double,
    val stock: Int,
    val color: String = "#94a3b8",
    val colorName: String = "Gris Minéral",
    val image: String = "",
    val finition: String = "brillant",
    val choix: String = "1er Choix",
    val categorie: String = "sol",
    val description: String = "",
    val pricingUnit: String = "carton", // "carton" | "piece"
    val prixPiece: Double = 0.0
) {
    val isSoldByPiece: Boolean get() = pricingUnit == "piece"
}

data class CartItem(
    val id: String,
    val productId: String,
    val name: String,
    val usine: String,
    val dimension: String,
    val m2Carton: Double,
    val pcs: Int,
    val prixUnitaire: Double,
    val prixAchat: Double,
    val cartons: Int,
    val pieces: Int = 0,
    val m2Total: Double,
    val sousTotal: Double,
    val bain: String = "",
    val calibre: String = "",
    val color: String = "#94a3b8",
    val colorName: String = "",
    val image: String = "",
    val prixPiece: Double = 0.0,
    val pricingUnit: String = "carton"
) {
    val isSoldByPiece: Boolean get() = pricingUnit == "piece"
}

data class FreeLineItem(
    val id: String,
    val designation: String,
    val quantite: Double,
    val unite: String,
    val prixUnitaire: Double,
    val sousTotal: Double
)

data class InvoiceClient(
    val id: String? = null,
    val nom: String,
    val tel: String = "",
    val adresse: String = ""
)

data class Invoice(
    val id: String,
    val reference: String,
    val date: String,
    val vendeur: String,
    val magasin: String,
    val client: InvoiceClient,
    val articles: List<CartItem>,
    val lignesLibres: List<FreeLineItem>,
    val totalBrut: Double,
    val remise: Double,
    val totalNet: Double,
    val montantPaye: Double,
    val resteAPayer: Double,
    val modePaiement: String,
    val statut: String,
    val notes: String = "",
    val vendeurTel: String = "",
    val hidePrices: Boolean = false,
    val whatsappSentBeforePrint: Boolean = false
)

data class Customer(
    val id: String,
    val nom: String,
    val tel: String,
    val adresse: String,
    val dette: Double,
    val totalAchats: Double
)

data class Supplier(
    val id: String,
    val nom: String,
    val contact: String,
    val tel: String,
    val adresse: String,
    val usineRepresentee: String,
    val soldeDette: Double,
    val totalAchats: Double
)

data class SupplierMovement(
    val id: String,
    val supplierId: String,
    val date: String,
    val type: String, // "versement" | "achat"
    val montant: Double,
    val motif: String,
    val pieceJointeRef: String? = null,
    val auteur: String
)

data class CaisseMovement(
    val id: String,
    val date: String,
    val type: String, // "entree" | "sortie"
    val motif: String,
    val montant: Double,
    val auteur: String,
    val referenceFacture: String? = null
)

data class DailyCaisseCount(
    val id: String,
    val date: String,
    val magasin: String,
    val auteur: String,
    val fondOuverture: Double,
    val ventesEspecesJour: Double,
    val entreesDiversJour: Double,
    val sortiesJour: Double,
    val theorique: Double,
    val compteReel: Double,
    val ecart: Double,
    val notes: String = ""
)

data class StaffUser(
    val id: String,
    val name: String,
    val role: String, // "admin" | "gerant" | "vendeur"
    val pin: String,
    val magasin: String,
    val allowedTabs: List<String>,
    val canEditInvoices: Boolean,
    val canDeleteInvoices: Boolean,
    val canModifyStock: Boolean,
    val canViewSuppliers: Boolean,
    val canViewCaisse: Boolean,
    val canManageUsers: Boolean,
    val active: Boolean = true,
    val phone: String = "",
    val canViewPrixAchat: Boolean = (role == "admin" || role == "gerant"),
    val canModifyPrices: Boolean = (role == "admin" || role == "gerant")
)

data class RolePolicy(
    val roleId: String, // "admin" | "gerant" | "vendeur"
    val displayName: String,
    val description: String = "",
    val canViewPrixAchat: Boolean = false,
    val canModifyPrices: Boolean = false,
    val canModifyStock: Boolean = false,
    val canEditInvoices: Boolean = true,
    val canDeleteInvoices: Boolean = false,
    val canViewSuppliers: Boolean = false,
    val canViewCaisse: Boolean = false,
    val canManageUsers: Boolean = false,
    val allowedTabs: List<String> = emptyList()
)

data class EffectivePermissions(
    val roleId: String,
    val canViewPrixAchat: Boolean,
    val canModifyPrices: Boolean,
    val canModifyStock: Boolean,
    val canEditInvoices: Boolean,
    val canDeleteInvoices: Boolean,
    val canViewSuppliers: Boolean,
    val canViewCaisse: Boolean,
    val canManageUsers: Boolean,
    val canHideInvoicePrices: Boolean,
    val allowedTabs: List<String>
)

data class StoreInfo(
    val nom: String,
    val nomArabe: String,
    val slogan: String,
    val adresse: String,
    val tel: String,
    val tel2: String,
    val email: String,
    val rc: String,
    val nif: String,
    val nis: String,
    val ai: String,
    val invoiceWaRecipients: List<WaRecipient> = PosSeeds.DEFAULT_INVOICE_WA_RECIPIENTS,
    val dispoWaRecipients: List<WaRecipient> = PosSeeds.DEFAULT_DISPO_WA_RECIPIENTS
)

data class AppSettings(
    val printFormat: String = "A4", // "A4" | "A5"
    val a5Density: String = "compact", // "ultra" | "compact" | "normal"
    val hideBainTeinteFacture: Boolean = true,
    val colorTheme: String = "ceramica_showroom",
    val touchFriendlyMode: Boolean = true,
    val activeStore: String = "Depot Remchi",
    val enableSoundAlerts: Boolean = true,
    val deviceSellerName: String = "",
    val deviceSellerPhone: String = ""
)

data class DepotNotification(
    val id: String,
    val invoiceId: String,
    val reference: String,
    val date: String,
    val vendeur: String,
    val magasin: String,
    val client: String,
    val clientTel: String,
    val totalNet: Double,
    val m2Total: Double,
    val cartonsTotal: Int,
    val articlesSummary: String,
    val read: Boolean = false
)

data class DiversPreset(
    val designation: String,
    val unite: String,
    val prix: Double
)

object PosSeeds {
    val ALL_TABS = listOf(
        "pos", "stock", "invoices", "dashboard",
        "customers", "suppliers", "caisse", "users", "settings", "calculator"
    )

    val MANAGER_TABS = ALL_TABS.filterNot { it == "users" }

    val SELLER_TABS = listOf("pos", "stock", "invoices", "customers", "calculator")

    val VALID_ROLES = listOf("admin", "gerant", "vendeur")

    val DEFAULT_ROLE_POLICIES = listOf(
        RolePolicy(
            roleId = "admin",
            displayName = "Admin",
            description = "Accès complet : stock, prix d'achat, marges, tarifs et gestion équipe",
            canViewPrixAchat = true,
            canModifyPrices = true,
            canModifyStock = true,
            canEditInvoices = true,
            canDeleteInvoices = true,
            canViewSuppliers = true,
            canViewCaisse = true,
            canManageUsers = true,
            allowedTabs = ALL_TABS
        ),
        RolePolicy(
            roleId = "gerant",
            displayName = "Manager (Owner & Gérant)",
            description = "Gestion commerciale : stock, prix d'achat, tarifs, caisse et usines",
            canViewPrixAchat = true,
            canModifyPrices = true,
            canModifyStock = true,
            canEditInvoices = true,
            canDeleteInvoices = true,
            canViewSuppliers = true,
            canViewCaisse = true,
            canManageUsers = false,
            allowedTabs = MANAGER_TABS
        ),
        RolePolicy(
            roleId = "vendeur",
            displayName = "Seller (Vendeur)",
            description = "Vente au comptoir & devis : prix d'achat masqué, tarifs et stock verrouillés",
            canViewPrixAchat = false,
            canModifyPrices = false,
            canModifyStock = false,
            canEditInvoices = true,
            canDeleteInvoices = false,
            canViewSuppliers = false,
            canViewCaisse = false,
            canManageUsers = false,
            allowedTabs = SELLER_TABS
        )
    )

    fun defaultPolicyForRole(role: String): RolePolicy =
        DEFAULT_ROLE_POLICIES.find { it.roleId == role } ?: DEFAULT_ROLE_POLICIES.last()

    fun resolveEffectivePermissions(
        user: StaffUser?,
        policies: List<RolePolicy> = DEFAULT_ROLE_POLICIES
    ): EffectivePermissions {
        if (user == null) {
            val fallback = defaultPolicyForRole("vendeur")
            return EffectivePermissions(
                roleId = fallback.roleId,
                canViewPrixAchat = false,
                canModifyPrices = false,
                canModifyStock = false,
                canEditInvoices = false,
                canDeleteInvoices = false,
                canViewSuppliers = false,
                canViewCaisse = false,
                canManageUsers = false,
                canHideInvoicePrices = false,
                allowedTabs = listOf("pos", "calculator")
            )
        }
        val policy = policies.find { it.roleId == user.role } ?: defaultPolicyForRole(user.role)
        if (user.role == "admin") {
            return EffectivePermissions(
                roleId = "admin",
                canViewPrixAchat = true,
                canModifyPrices = true,
                canModifyStock = true,
                canEditInvoices = true,
                canDeleteInvoices = true,
                canViewSuppliers = true,
                canViewCaisse = true,
                canManageUsers = true,
                canHideInvoicePrices = true,
                allowedTabs = ALL_TABS
            )
        }
        val policyTabs = policy.allowedTabs.ifEmpty {
            if (user.role == "gerant") MANAGER_TABS else SELLER_TABS
        }
        val baseUserTabs = user.allowedTabs.ifEmpty { policyTabs }
        val resolvedTabs = (baseUserTabs.filter { it in policyTabs } + "calculator")
            .distinct()
            .ifEmpty { listOf("pos", "calculator") }
        val canViewCost = policy.canViewPrixAchat && user.canViewPrixAchat
        return EffectivePermissions(
            roleId = user.role,
            canViewPrixAchat = canViewCost,
            canModifyPrices = policy.canModifyPrices && user.canModifyPrices,
            canModifyStock = policy.canModifyStock && user.canModifyStock,
            canEditInvoices = policy.canEditInvoices && user.canEditInvoices,
            canDeleteInvoices = policy.canDeleteInvoices && user.canDeleteInvoices,
            canViewSuppliers = policy.canViewSuppliers && user.canViewSuppliers,
            canViewCaisse = policy.canViewCaisse && user.canViewCaisse,
            canManageUsers = policy.canManageUsers && user.canManageUsers,
            canHideInvoicePrices = canViewCost,
            allowedTabs = resolvedTabs
        )
    }

    val SITES = listOf("Depot Remchi", "Magasin Ihab", "Magasin Zohir")

    val DEFAULT_INVOICE_WA_RECIPIENTS = listOf(
        WaRecipient(name = "Bureau / Kader (0676754124)", phone = "0676754124")
    )

    val DEFAULT_DISPO_WA_RECIPIENTS = listOf(
        WaRecipient(name = "Bureau / Kader (0676754124)", phone = "0676754124")
    )

    val DEFAULT_STORE_INFO = StoreInfo(
        nom = "CERAMICA",
        nomArabe = "سيراميكا — بيع الخزف وبلاط الأرضيات",
        slogan = "Dalle de Sol, Faïence Murale, Parquet Céramique & Accessoires de Pose",
        adresse = "ZONE INDUSTRIELLE REMCHI المنطقة الصناعية",
        tel = "0676754124",
        tel2 = "0676754124",
        email = "",
        rc = "16/00-1284560B24",
        nif = "002416012845690",
        nis = "002416012800145",
        ai = "16041285098",
        invoiceWaRecipients = DEFAULT_INVOICE_WA_RECIPIENTS,
        dispoWaRecipients = DEFAULT_DISPO_WA_RECIPIENTS
    )

    val DEFAULT_SETTINGS = AppSettings()

    val DEFAULT_USERS = listOf(
        StaffUser(
            id = "u_kader",
            name = "Kader",
            role = "admin",
            pin = "1234",
            magasin = "Depot Remchi",
            allowedTabs = ALL_TABS,
            canEditInvoices = true,
            canDeleteInvoices = true,
            canModifyStock = true,
            canViewSuppliers = true,
            canViewCaisse = true,
            canManageUsers = true,
            active = true,
            phone = "0676754124",
            canViewPrixAchat = true,
            canModifyPrices = true
        ),
        StaffUser(
            id = "u_walid",
            name = "Walid",
            role = "gerant",
            pin = "2222",
            magasin = "Depot Remchi",
            allowedTabs = MANAGER_TABS,
            canEditInvoices = true,
            canDeleteInvoices = true,
            canModifyStock = true,
            canViewSuppliers = true,
            canViewCaisse = true,
            canManageUsers = false,
            active = true,
            phone = "",
            canViewPrixAchat = true,
            canModifyPrices = true
        ),
        StaffUser(
            id = "u_toufik",
            name = "Toufik",
            role = "gerant",
            pin = "3333",
            magasin = "Depot Remchi",
            allowedTabs = MANAGER_TABS,
            canEditInvoices = true,
            canDeleteInvoices = true,
            canModifyStock = true,
            canViewSuppliers = true,
            canViewCaisse = true,
            canManageUsers = false,
            active = true,
            phone = "",
            canViewPrixAchat = true,
            canModifyPrices = true
        ),
        StaffUser(
            id = "u_ihab",
            name = "Ihab",
            role = "gerant",
            pin = "4444",
            magasin = "Magasin Ihab",
            allowedTabs = MANAGER_TABS,
            canEditInvoices = true,
            canDeleteInvoices = true,
            canModifyStock = true,
            canViewSuppliers = true,
            canViewCaisse = true,
            canManageUsers = false,
            active = true,
            phone = "",
            canViewPrixAchat = true,
            canModifyPrices = true
        ),
        StaffUser(
            id = "u_zohir",
            name = "Zohir",
            role = "vendeur",
            pin = "0001",
            magasin = "Magasin Zohir",
            allowedTabs = SELLER_TABS,
            canEditInvoices = true,
            canDeleteInvoices = false,
            canModifyStock = false,
            canViewSuppliers = false,
            canViewCaisse = false,
            canManageUsers = false,
            active = true,
            phone = "",
            canViewPrixAchat = false,
            canModifyPrices = false
        ),
        StaffUser(
            id = "u_celia",
            name = "Celia",
            role = "vendeur",
            pin = "0002",
            magasin = "Depot Remchi",
            allowedTabs = SELLER_TABS,
            canEditInvoices = true,
            canDeleteInvoices = false,
            canModifyStock = false,
            canViewSuppliers = false,
            canViewCaisse = false,
            canManageUsers = false,
            active = true,
            phone = "",
            canViewPrixAchat = false,
            canModifyPrices = false
        ),
        StaffUser(
            id = "u_alae",
            name = "Alae",
            role = "vendeur",
            pin = "0003",
            magasin = "Depot Remchi",
            allowedTabs = SELLER_TABS,
            canEditInvoices = true,
            canDeleteInvoices = false,
            canModifyStock = false,
            canViewSuppliers = false,
            canViewCaisse = false,
            canManageUsers = false,
            active = true,
            phone = "",
            canViewPrixAchat = false,
            canModifyPrices = false
        ),
        StaffUser(
            id = "u_amin",
            name = "Amine",
            role = "vendeur",
            pin = "0004",
            magasin = "Depot Remchi",
            allowedTabs = SELLER_TABS,
            canEditInvoices = true,
            canDeleteInvoices = false,
            canModifyStock = false,
            canViewSuppliers = false,
            canViewCaisse = false,
            canManageUsers = false,
            active = true,
            phone = "",
            canViewPrixAchat = false,
            canModifyPrices = false
        )
    )

    val DEFAULT_CUSTOMERS = listOf(
        Customer("c1", "Ahmed Benali", "0550 12 34 56", "Remchi Centre", 45000.0, 320000.0),
        Customer("c2", "Youcef Carreleur Professionnel", "0661 88 99 00", "Tlemcen Mansourah", 18500.0, 480000.0),
        Customer("c3", "Mohamed Chérif", "0770 44 55 66", "Hennaya", 0.0, 145000.0),
        Customer("c4", "Khaled Berrached", "0555 90 80 70", "Ain Temouchent", 62000.0, 750000.0)
    )

    val DEFAULT_SUPPLIERS = listOf(
        Supplier(
            id = "f1",
            nom = "Sarl Usine BCRAM Céramique",
            contact = "Direction Commerciale",
            tel = "041 55 66 77",
            adresse = "Zone Industrielle Oran",
            usineRepresentee = "BCRAM",
            soldeDette = 320000.0,
            totalAchats = 1850000.0
        ),
        Supplier(
            id = "f2",
            nom = "NOVA Céramique Oran",
            contact = "M. Kaddour",
            tel = "0550 77 88 99",
            adresse = "Es-Sénia, Oran",
            usineRepresentee = "NOVA",
            soldeDette = 150000.0,
            totalAchats = 1200000.0
        ),
        Supplier(
            id = "f3",
            nom = "CEVICO Céramique Tlemcen",
            contact = "Service Vente Usine",
            tel = "043 22 33 44",
            adresse = "Zone Industrielle Remchi",
            usineRepresentee = "CEVICO",
            soldeDette = 0.0,
            totalAchats = 890000.0
        ),
        Supplier(
            id = "f4",
            nom = "Comptoir National Céramique NCM",
            contact = "M. Boumediene",
            tel = "0662 11 22 33",
            adresse = "Sidi Bel Abbès",
            usineRepresentee = "NCM",
            soldeDette = 85000.0,
            totalAchats = 650000.0
        ),
        Supplier(
            id = "f5",
            nom = "BATI Céramique Alger",
            contact = "Représentant Ouest",
            tel = "0771 33 44 55",
            adresse = "Rouiba, Alger",
            usineRepresentee = "BATI",
            soldeDette = 210000.0,
            totalAchats = 2400000.0
        )
    )

    val DIVERS_PRESETS = listOf(
        DiversPreset("Colle Céramique C1 (25kg)", "sac", 750.0),
        DiversPreset("Colle Porcelaine C2 Haute Adhérence (25kg)", "sac", 1250.0),
        DiversPreset("Joint de Carrelage Hydrofuge (5kg)", "sac", 450.0),
        DiversPreset("Croisillons 2mm (Paquet 200u)", "u", 600.0),
        DiversPreset("Baguette Aluminium Chrome 2.5m", "u", 850.0),
        DiversPreset("Forfait Livraison & Transport Camion", "forfait", 3000.0),
        DiversPreset("Main d'Œuvre / Manutention Déchargement", "forfait", 2000.0)
    )

    val WHATSAPP_MANAGEMENT = "213676754124"
    val WHATSAPP_DEPOT = "213676754124"
}

private val daSymbols = DecimalFormatSymbols(Locale.FRANCE).apply {
    groupingSeparator = ' '
    decimalSeparator = '.'
}
private val daIntFormat = DecimalFormat("#,##0", daSymbols)
private val m2Format = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))

fun formatDa(amount: Double): String {
    val rounded = amount.roundToLong()
    return "${daIntFormat.format(rounded)} DA"
}

fun formatNumber(amount: Double): String {
    val rounded = amount.roundToLong()
    return daIntFormat.format(rounded)
}

fun formatM2(m2: Double): String {
    val clean = (m2 * 100.0).let { kotlin.math.round(it) / 100.0 }
    return m2Format.format(clean)
}

fun toDzWhatsAppDigits(rawPhone: String): String? {
    val digits = rawPhone.filter { it.isDigit() }
    if (digits.isBlank()) return null
    return when {
        digits.startsWith("213") -> digits
        digits.startsWith("0") -> "213" + digits.drop(1)
        else -> "213$digits"
    }
}
