package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.CartItem
import com.example.data.model.Invoice
import com.example.data.model.StoreInfo
import com.example.data.model.formatDa
import com.example.data.model.formatM2
import java.io.File
import java.io.FileOutputStream

object InvoicePdfGenerator {

    private const val PDF_DIR_NAME = "invoices_pdf"
    private const val PHOTO_DIR_NAME = "invoices_photo"
    private const val LOGO_DIR_NAME = "store_assets"
    private const val LOGO_FILE_NAME = "store_logo.png"

    fun getStoreLogoFile(context: Context): File {
        val dir = File(context.filesDir, LOGO_DIR_NAME).apply { mkdirs() }
        return File(dir, LOGO_FILE_NAME)
    }

    fun loadStoreLogoBitmap(context: Context): Bitmap? {
        val file = getStoreLogoFile(context)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun saveStoreLogoFromUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
            val maxDim = 512f
            val scale = minOf(maxDim / decoded.width, maxDim / decoded.height, 1f)
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val resized = if (targetW != decoded.width || targetH != decoded.height) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else decoded
            val dest = getStoreLogoFile(context)
            FileOutputStream(dest).use { out ->
                resized.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            if (resized != decoded) resized.recycle()
            decoded.recycle()
            true
        }.getOrDefault(false)
    }

    fun deleteStoreLogo(context: Context): Boolean {
        val file = getStoreLogoFile(context)
        return if (file.exists()) file.delete() else false
    }

    @androidx.annotation.VisibleForTesting
    internal var pdfFileFactoryOverride: ((Context, Invoice, StoreInfo, String, Boolean, Boolean) -> File)? = null

    fun generateAndSavePdf(
        context: Context,
        invoice: Invoice,
        storeInfo: StoreInfo,
        format: String = "A4",
        hideBain: Boolean = true,
        hidePrices: Boolean = invoice.hidePrices
    ): File {
        pdfFileFactoryOverride?.let { overrideFn ->
            return overrideFn(context, invoice, storeInfo, format, hideBain, hidePrices)
        }
        val dir = File(context.filesDir, PDF_DIR_NAME).apply { mkdirs() }
        val safeRef = invoice.reference.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { invoice.id }
        val suffix = if (hidePrices) "_REV" else ""
        val targetFile = File(dir, "Facture_${safeRef}$suffix.pdf")

        val isA5 = format.equals("A5", ignoreCase = true)
        val pageWidth = if (isA5) 420 else 595
        val pageHeight = if (isA5) 595 else 842
        val margin = if (isA5) 18f else 26f
        val logoBitmap = loadStoreLogoBitmap(context)

        val doc = PdfDocument()
        try {
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas

            val scale = if (isA5) 0.78f else 1f
            val footerReserve = 165f * scale

            var y = drawEcoInvoiceHeader(
                canvas = canvas,
                pageWidth = pageWidth.toFloat(),
                margin = margin,
                scale = scale,
                invoice = invoice,
                storeInfo = storeInfo,
                logoBitmap = logoBitmap,
                hidePrices = hidePrices
            )
            y = drawEcoTableHeader(canvas, pageWidth.toFloat(), margin, y, scale, hidePrices)

            fun ensureSpace(needed: Float) {
                if (y + needed > pageHeight - margin - footerReserve) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    y = drawEcoTableHeader(canvas, pageWidth.toFloat(), margin, margin, scale, hidePrices)
                }
            }

            invoice.articles.forEach { item ->
                val rowH = 34f * scale
                ensureSpace(rowH + 4f)
                y = drawEcoProductRow(canvas, pageWidth.toFloat(), margin, y, scale, item, hideBain, hidePrices)
            }

            invoice.lignesLibres.forEach { line ->
                val rowH = 24f * scale
                ensureSpace(rowH + 4f)
                y = drawEcoFreeLineRow(canvas, pageWidth.toFloat(), margin, y, scale, line.designation, "${formatM2(line.quantite)} ${line.unite}", line.prixUnitaire, line.sousTotal, hidePrices)
            }

            ensureSpace(footerReserve)
            drawEcoInvoiceFooter(
                canvas = canvas,
                pageWidth = pageWidth.toFloat(),
                margin = margin,
                yStart = y + (8f * scale),
                scale = scale,
                invoice = invoice,
                storeInfo = storeInfo,
                hidePrices = hidePrices
            )

            doc.finishPage(page)
            FileOutputStream(targetFile).use { out ->
                doc.writeTo(out)
            }
        } finally {
            runCatching { doc.close() }
            runCatching { logoBitmap?.recycle() }
        }
        return targetFile
    }

    fun generateAndSaveInvoicePhotoPng(
        context: Context,
        invoice: Invoice,
        storeInfo: StoreInfo,
        hideBain: Boolean = true,
        hidePrices: Boolean = invoice.hidePrices
    ): File {
        val dir = File(context.filesDir, PHOTO_DIR_NAME).apply { mkdirs() }
        val safeRef = invoice.reference.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { invoice.id }
        val suffix = if (hidePrices) "_REV" else ""
        val targetFile = File(dir, "Facture_${safeRef}$suffix.png")

        val scale = 2.0f
        val logicalWidth = 595f
        val margin = 24f
        val headerEstimate = 162f
        val rowsEstimate = (invoice.articles.size * 38f) + (invoice.lignesLibres.size * 26f)
        val footerEstimate = 215f
        val logicalHeight = (margin * 2 + headerEstimate + rowsEstimate + footerEstimate).coerceAtLeast(780f)

        val bmpW = (logicalWidth * scale).toInt()
        val bmpH = (logicalHeight * scale).toInt()
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)

        val logoBitmap = loadStoreLogoBitmap(context)
        var y = drawEcoInvoiceHeader(
            canvas = canvas,
            pageWidth = logicalWidth,
            margin = margin,
            scale = 1f,
            invoice = invoice,
            storeInfo = storeInfo,
            logoBitmap = logoBitmap,
            hidePrices = hidePrices
        )
        y = drawEcoTableHeader(canvas, logicalWidth, margin, y, 1f, hidePrices)

        invoice.articles.forEach { item ->
            y = drawEcoProductRow(canvas, logicalWidth, margin, y, 1f, item, hideBain, hidePrices)
        }
        invoice.lignesLibres.forEach { line ->
            y = drawEcoFreeLineRow(
                canvas = canvas,
                pageWidth = logicalWidth,
                margin = margin,
                yStart = y,
                scale = 1f,
                designation = line.designation,
                qtyLabel = "${formatM2(line.quantite)} ${line.unite}",
                pu = line.prixUnitaire,
                total = line.sousTotal,
                hidePrices = hidePrices
            )
        }

        val finalBottom = drawEcoInvoiceFooter(
            canvas = canvas,
            pageWidth = logicalWidth,
            margin = margin,
            yStart = y + 10f,
            scale = 1f,
            invoice = invoice,
            storeInfo = storeInfo,
            hidePrices = hidePrices
        )

        val usedPixelHeight = ((finalBottom + margin) * scale).toInt().coerceIn(400, bmpH)
        val finalBitmap = if (usedPixelHeight < bmpH - 12) {
            Bitmap.createBitmap(bitmap, 0, 0, bmpW, usedPixelHeight)
        } else bitmap

        FileOutputStream(targetFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        if (finalBitmap != bitmap) finalBitmap.recycle()
        bitmap.recycle()
        logoBitmap?.recycle()
        return targetFile
    }

    private fun drawEcoInvoiceHeader(
        canvas: Canvas,
        pageWidth: Float,
        margin: Float,
        scale: Float,
        invoice: Invoice,
        storeInfo: StoreInfo,
        logoBitmap: Bitmap?,
        hidePrices: Boolean
    ): Float {
        canvas.drawColor(Color.WHITE)
        val inkDark = Color.parseColor("#0F172A")
        val inkMuted = Color.parseColor("#475569")
        val inkBorder = Color.parseColor("#1E293B")
        val contentWidth = pageWidth - (margin * 2)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = inkBorder
            strokeWidth = 1.1f * scale
        }
        val lightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#94A3B8")
            strokeWidth = 0.8f * scale
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 17f * scale
            color = inkDark
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f * scale
            color = inkDark
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f * scale
            color = inkDark
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f * scale
            color = inkMuted
        }
        val rightBold = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightSmall = Paint(smallPaint).apply { textAlign = Paint.Align.RIGHT }

        // Top Eco-Ink Outlined Header Box (ZERO filled dark background)
        val headerH = 82f * scale
        canvas.drawRoundRect(
            RectF(margin, margin, pageWidth - margin, margin + headerH),
            6f * scale,
            6f * scale,
            borderPaint
        )

        var leftTextX = margin + (10f * scale)
        if (logoBitmap != null) {
            val logoBox = 54f * scale
            val ratio = minOf(logoBox / logoBitmap.width, logoBox / logoBitmap.height)
            val lw = logoBitmap.width * ratio
            val lh = logoBitmap.height * ratio
            val lx = margin + (10f * scale)
            val ly = margin + (headerH - lh) / 2f
            canvas.drawBitmap(logoBitmap, null, RectF(lx, ly, lx + lw, ly + lh), null)
            leftTextX = lx + lw + (10f * scale)
        }

        canvas.drawText(storeInfo.nom.uppercase(), leftTextX, margin + (20f * scale), brandPaint)
        if (storeInfo.nomArabe.isNotBlank()) {
            canvas.drawText(storeInfo.nomArabe.take(40), leftTextX, margin + (33f * scale), bodyPaint)
        }
        canvas.drawText(storeInfo.adresse.take(46), leftTextX, margin + (46f * scale), smallPaint)
        val telText = listOf(storeInfo.tel, storeInfo.tel2).filter { it.isNotBlank() }.joinToString(" / ")
        if (telText.isNotBlank()) {
            canvas.drawText("Tél: $telText", leftTextX, margin + (58f * scale), smallPaint)
        }
        val fiscalText = listOfNotNull(
            storeInfo.rc.takeIf { it.isNotBlank() }?.let { "RC: $it" },
            storeInfo.nif.takeIf { it.isNotBlank() }?.let { "NIF: $it" }
        ).joinToString(" • ")
        if (fiscalText.isNotBlank()) {
            canvas.drawText(fiscalText.take(48), leftTextX, margin + (70f * scale), smallPaint)
        }

        val rx = pageWidth - margin - (10f * scale)
        val titleLine = if (hidePrices) {
            "BON DE LIVRAISON / وصل تسليم"
        } else {
            "FACTURE • وصل تسليم / فاتورة"
        }
        canvas.drawText(titleLine, rx, margin + (19f * scale), rightBold)
        canvas.drawText("N° ${invoice.reference}", rx, margin + (33f * scale), rightBold.apply { textSize = 11.5f * scale })
        canvas.drawText("Date / التاريخ: ${invoice.date.take(10)}", rx, margin + (46f * scale), rightSmall)
        canvas.drawText("Site: ${invoice.magasin}", rx, margin + (58f * scale), rightSmall)
        val sellerPhonePart = if (invoice.vendeurTel.isNotBlank()) " • ${invoice.vendeurTel}" else ""
        canvas.drawText(
            "Vendeur / البائع: ${invoice.vendeur}$sellerPhonePart",
            rx,
            margin + (70f * scale),
            rightSmall
        )

        val y = margin + headerH + (8f * scale)
        val clientH = 32f * scale
        canvas.drawRoundRect(
            RectF(margin, y, pageWidth - margin, y + clientH),
            5f * scale,
            5f * scale,
            lightBorderPaint
        )
        canvas.drawText("Client / الزبون : ${invoice.client.nom}", margin + (10f * scale), y + (13f * scale), boldPaint)
        val clientMeta = listOf(invoice.client.tel, invoice.client.adresse).filter { it.isNotBlank() }.joinToString(" • ")
        if (clientMeta.isNotBlank()) {
            canvas.drawText(clientMeta.take(58), margin + (10f * scale), y + (25f * scale), smallPaint)
        }
        val statusText = if (hidePrices) {
            "COPIE REVENDEUR (SANS PRIX)"
        } else {
            "Statut / الحالة : ${invoice.statut.uppercase()}"
        }
        canvas.drawText(statusText, rx, y + (19f * scale), rightBold.apply { textSize = 9.5f * scale })
        return y + clientH + (8f * scale)
    }

    private fun drawEcoTableHeader(
        canvas: Canvas,
        pageWidth: Float,
        margin: Float,
        yStart: Float,
        scale: Float,
        hidePrices: Boolean
    ): Float {
        val contentWidth = pageWidth - (margin * 2)
        val h = 24f * scale
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 1.1f * scale
        }
        val thLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f * scale
            color = Color.parseColor("#0F172A")
        }
        val thRight = Paint(thLeft).apply { textAlign = Paint.Align.RIGHT }

        canvas.drawLine(margin, yStart, pageWidth - margin, yStart, borderPaint)
        canvas.drawLine(margin, yStart + h, pageWidth - margin, yStart + h, borderPaint)

        val textY = yStart + (h * 0.65f)
        canvas.drawText("[ ]", margin + (3f * scale), textY, thLeft)
        canvas.drawText("Désignation / التعيين", margin + (22f * scale), textY, thLeft)
        canvas.drawText("Qté & m² / الكمية", margin + contentWidth * 0.64f, textY, thRight)
        canvas.drawText(if (hidePrices) "---" else "P.U / س.و", margin + contentWidth * 0.81f, textY, thRight)
        canvas.drawText(if (hidePrices) "---" else "Total / المجموع", pageWidth - margin - (4f * scale), textY, thRight)
        return yStart + h + (3f * scale)
    }

    private fun drawEcoProductRow(
        canvas: Canvas,
        pageWidth: Float,
        margin: Float,
        yStart: Float,
        scale: Float,
        item: CartItem,
        hideBain: Boolean,
        hidePrices: Boolean
    ): Float {
        val contentWidth = pageWidth - (margin * 2)
        val rowH = 34f * scale
        val inkDark = Color.parseColor("#0F172A")
        val inkMuted = Color.parseColor("#475569")

        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = inkDark
            strokeWidth = 1f * scale
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f * scale
            color = inkDark
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.8f * scale
            color = inkDark
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.8f * scale
            color = inkMuted
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.7f * scale
        }
        val rightBody = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightSmall = Paint(smallPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }

        // Pen check square ahead of each product for partial delivery checking
        val boxSize = 10f * scale
        canvas.drawRect(
            margin + (3f * scale),
            yStart + (9f * scale),
            margin + (3f * scale) + boxSize,
            yStart + (9f * scale) + boxSize,
            checkPaint
        )

        val nameX = margin + (20f * scale)
        val designation = "${item.name} • ${item.usine} • ${item.dimension}"
        canvas.drawText(designation.take(44), nameX, yStart + (12f * scale), boldPaint)

        val unitSpec = if (item.isSoldByPiece) {
            "Vendu à la Pièce / بالقطعة (${item.pcs} pcs/crt)"
        } else {
            "${formatM2(item.m2Carton)} m²/Crt"
        }
        val extraSpec = buildString {
            append(unitSpec)
            if (!hideBain && item.bain.isNotBlank()) append(" • Bain ${item.bain}")
        }
        canvas.drawText(extraSpec.take(50), nameX, yStart + (24f * scale), smallPaint)

        val qtyRightX = margin + contentWidth * 0.64f
        val qtyLabel = when {
            item.isSoldByPiece && item.cartons > 0 -> "${item.cartons} ctn + ${item.pieces} pcs"
            item.isSoldByPiece -> "${item.pieces} pcs"
            item.pieces > 0 -> "${item.cartons} ctn + ${item.pieces} pcs"
            else -> "${item.cartons} ctn"
        }
        canvas.drawText(qtyLabel, qtyRightX, yStart + (12f * scale), rightBody)
        canvas.drawText("${formatM2(item.m2Total)} m²", qtyRightX, yStart + (24f * scale), rightSmall)

        val puRightX = margin + contentWidth * 0.81f
        val puLabel = when {
            hidePrices -> "---"
            item.isSoldByPiece -> "${(if (item.prixPiece > 0) item.prixPiece else item.prixUnitaire).toInt()} DA/p"
            item.pieces > 0 && item.prixPiece > 0 -> "${item.prixUnitaire.toInt()} / ${item.prixPiece.toInt()}p"
            else -> "${item.prixUnitaire.toInt()} DA/m²"
        }
        canvas.drawText(puLabel, puRightX, yStart + (17f * scale), rightBody)

        val totRightX = pageWidth - margin - (4f * scale)
        canvas.drawText(if (hidePrices) "---" else formatDa(item.sousTotal), totRightX, yStart + (17f * scale), rightBold)

        val endY = yStart + rowH
        canvas.drawLine(margin, endY, pageWidth - margin, endY, linePaint)
        return endY + (2f * scale)
    }

    private fun drawEcoFreeLineRow(
        canvas: Canvas,
        pageWidth: Float,
        margin: Float,
        yStart: Float,
        scale: Float,
        designation: String,
        qtyLabel: String,
        pu: Double,
        total: Double,
        hidePrices: Boolean
    ): Float {
        val contentWidth = pageWidth - (margin * 2)
        val rowH = 24f * scale
        val inkDark = Color.parseColor("#0F172A")
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = inkDark
            strokeWidth = 1f * scale
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.2f * scale
            color = inkDark
        }
        val rightBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.8f * scale
            color = inkDark
            textAlign = Paint.Align.RIGHT
        }
        val rightBold = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.7f * scale
        }

        val boxSize = 10f * scale
        canvas.drawRect(
            margin + (3f * scale),
            yStart + (6f * scale),
            margin + (3f * scale) + boxSize,
            yStart + (6f * scale) + boxSize,
            checkPaint
        )
        canvas.drawText(designation.take(40), margin + (20f * scale), yStart + (15f * scale), boldPaint)
        canvas.drawText(qtyLabel, margin + contentWidth * 0.64f, yStart + (15f * scale), rightBody)
        canvas.drawText(if (hidePrices) "---" else "${pu.toInt()} DA", margin + contentWidth * 0.81f, yStart + (15f * scale), rightBody)
        canvas.drawText(if (hidePrices) "---" else formatDa(total), pageWidth - margin - (4f * scale), yStart + (15f * scale), rightBold)

        val endY = yStart + rowH
        canvas.drawLine(margin, endY, pageWidth - margin, endY, linePaint)
        return endY + (2f * scale)
    }

    private fun drawEcoInvoiceFooter(
        canvas: Canvas,
        pageWidth: Float,
        margin: Float,
        yStart: Float,
        scale: Float,
        invoice: Invoice,
        storeInfo: StoreInfo,
        hidePrices: Boolean
    ): Float {
        val inkDark = Color.parseColor("#0F172A")
        val inkMuted = Color.parseColor("#475569")
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1E293B")
            strokeWidth = 1f * scale
        }
        val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#64748B")
            strokeWidth = 0.9f * scale
            pathEffect = DashPathEffect(floatArrayOf(4f * scale, 3f * scale), 0f)
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f * scale
            color = inkDark
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.8f * scale
            color = inkDark
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f * scale
            color = inkMuted
        }
        val rightBold = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightLarge = Paint(boldPaint).apply {
            textAlign = Paint.Align.RIGHT
            textSize = 12f * scale
        }

        // Delivery status & summary box (NO "Total Cartons" printed per user request!)
        val topBoxH = 74f * scale
        canvas.drawRoundRect(
            RectF(margin, yStart, pageWidth - margin, yStart + topBoxH),
            6f * scale,
            6f * scale,
            borderPaint
        )

        val totalM2 = invoice.articles.sumOf { it.m2Total }
        val lx = margin + (10f * scale)
        canvas.drawText("Surface totale / المساحة : ${formatM2(totalM2)} m²", lx, yStart + (16f * scale), boldPaint)

        // Two Delivery Checkboxes: Livraison Complète vs Livraison Partielle
        val cbSize = 10f * scale
        val cb1Y = yStart + (26f * scale)
        canvas.drawRect(lx, cb1Y, lx + cbSize, cb1Y + cbSize, borderPaint)
        canvas.drawText("Livraison Complète / تسليم كامل", lx + cbSize + (6f * scale), cb1Y + (8.5f * scale), bodyPaint)

        val cb2Y = yStart + (44f * scale)
        canvas.drawRect(lx, cb2Y, lx + cbSize, cb2Y + cbSize, borderPaint)
        canvas.drawText("Livraison Partielle / تسليم جزئي", lx + cbSize + (6f * scale), cb2Y + (8.5f * scale), bodyPaint)

        val sellerTelPart = if (invoice.vendeurTel.isNotBlank()) " (${invoice.vendeurTel})" else ""
        canvas.drawText(
            "Vendeur: ${invoice.vendeur}$sellerTelPart",
            lx,
            yStart + (66f * scale),
            smallPaint
        )

        val rx = pageWidth - margin - (10f * scale)
        if (hidePrices) {
            canvas.drawText("BON DE LIVRAISON", rx, yStart + (24f * scale), rightLarge)
            canvas.drawText("Prix masqués (Revendeur)", rx, yStart + (40f * scale), Paint(smallPaint).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("الأسعار مخفية", rx, yStart + (54f * scale), Paint(smallPaint).apply { textAlign = Paint.Align.RIGHT })
        } else {
            var ty = yStart + (16f * scale)
            if (invoice.remise > 0) {
                canvas.drawText("Remise / التخفيض : -${formatDa(invoice.remise)}", rx, ty, rightBold)
                ty += 14f * scale
            }
            canvas.drawText("TOTAL NET / الصافي : ${formatDa(invoice.totalNet)}", rx, ty, rightLarge)
            ty += 15f * scale
            canvas.drawText("Payé (${invoice.modePaiement}) : ${formatDa(invoice.montantPaye)}", rx, ty, rightBold)
            if (invoice.resteAPayer > 0.01) {
                ty += 14f * scale
                canvas.drawText("Reste / الباقي : ${formatDa(invoice.resteAPayer)}", rx, ty, rightBold)
            }
        }

        // Empty handwritten Notes box at bottom (intentionally open for pen notes)
        val notesY = yStart + topBoxH + (8f * scale)
        val notesH = 62f * scale
        canvas.drawRoundRect(
            RectF(margin, notesY, pageWidth - margin, notesY + notesH),
            6f * scale,
            6f * scale,
            dashedPaint
        )
        canvas.drawText("Notes / ملاحظات :", lx, notesY + (14f * scale), smallPaint)

        val bottomY = notesY + notesH + (12f * scale)
        if (storeInfo.slogan.isNotBlank()) {
            val centerPaint = Paint(smallPaint).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(storeInfo.slogan.take(70), pageWidth / 2f, bottomY, centerPaint)
            return bottomY + (8f * scale)
        }
        return bottomY
    }

    fun getFileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun getPdfUri(context: Context, file: File): Uri = getFileUri(context, file)

    fun openPdfViewer(context: Context, file: File) {
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Ouvrir la facture PDF"))
        }
    }

    fun sharePdfViaGmail(
        context: Context,
        file: File,
        invoice: Invoice,
        storeInfo: StoreInfo
    ) {
        val uri = getFileUri(context, file)
        val recipient = storeInfo.email.trim()
        val body = buildString {
            appendLine("Bonjour,")
            appendLine("Veuillez trouver ci-joint la facture PDF #${invoice.reference}.")
            appendLine("Magasin: ${invoice.magasin} | Vendeur: ${invoice.vendeur}")
            appendLine("Client: ${invoice.client.nom} ${if (invoice.client.tel.isNotBlank()) "(${invoice.client.tel})" else ""}")
            if (!invoice.hidePrices) {
                appendLine("Total Net: ${formatDa(invoice.totalNet)} | Payé: ${formatDa(invoice.montantPaye)} | Reste: ${formatDa(invoice.resteAPayer)}")
            }
            appendLine("-- ${storeInfo.nom}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (recipient.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            }
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Facture #${invoice.reference} - ${invoice.client.nom}"
            )
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val gmailIntent = Intent(intent).apply { setPackage("com.google.android.gm") }
        val resolvedGmail = gmailIntent.resolveActivity(context.packageManager)
        runCatching {
            if (resolvedGmail != null) {
                context.startActivity(gmailIntent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Envoyer PDF par Email"))
            }
        }
    }

    fun shareInvoicePhotoAndPdfViaWhatsApp(
        context: Context,
        pngFile: File,
        pdfFile: File,
        phone213: String?,
        message: String = "",
        invoiceRef: String = ""
    ): Boolean {
        val pngUri = getFileUri(context, pngFile)
        val pdfUri = getFileUri(context, pdfFile)
        val streamUris = arrayListOf(pngUri, pdfUri)
        val clipData = android.content.ClipData.newUri(context.contentResolver, "Facture $invoiceRef", pngUri).apply {
            addItem(android.content.ClipData.Item(pdfUri))
        }
        val multiIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "application/pdf"))
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, streamUris)
            if (message.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, message)
            }
            if (invoiceRef.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, "Facture #$invoiceRef")
            }
            if (!phone213.isNullOrBlank()) {
                putExtra("jid", "$phone213@s.whatsapp.net")
            }
            this.clipData = clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val waStandard = Intent(multiIntent).apply { setPackage("com.whatsapp") }
        val waBusiness = Intent(multiIntent).apply { setPackage("com.whatsapp.w4b") }
        return when {
            waStandard.resolveActivity(context.packageManager) != null -> {
                runCatching { context.startActivity(waStandard) }.isSuccess
            }
            waBusiness.resolveActivity(context.packageManager) != null -> {
                runCatching { context.startActivity(waBusiness) }.isSuccess
            }
            else -> {
                val chooser = Intent.createChooser(multiIntent, "Envoyer Photo + PDF Facture").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(chooser) }.isSuccess
            }
        }
    }

    fun shareInvoicePhotoViaWhatsApp(
        context: Context,
        pngFile: File,
        phone213: String?,
        message: String
    ): Boolean {
        val uri = getFileUri(context, pngFile)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (message.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, message)
            }
            if (!phone213.isNullOrBlank()) {
                putExtra("jid", "$phone213@s.whatsapp.net")
            }
            clipData = android.content.ClipData.newRawUri("Facture", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val waStandard = Intent(sendIntent).apply { setPackage("com.whatsapp") }
        val waBusiness = Intent(sendIntent).apply { setPackage("com.whatsapp.w4b") }
        return when {
            waStandard.resolveActivity(context.packageManager) != null -> {
                runCatching { context.startActivity(waStandard) }.isSuccess
            }
            waBusiness.resolveActivity(context.packageManager) != null -> {
                runCatching { context.startActivity(waBusiness) }.isSuccess
            }
            else -> {
                val chooser = Intent.createChooser(sendIntent, "Envoyer Photo Facture").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(chooser) }.isSuccess
            }
        }
    }

    fun sharePdfViaWhatsApp(
        context: Context,
        file: File,
        phone213: String?,
        message: String
    ) {
        val uri = getFileUri(context, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            if (!phone213.isNullOrBlank()) {
                putExtra("jid", "$phone213@s.whatsapp.net")
            }
            clipData = android.content.ClipData.newRawUri("Facture PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val waStandard = Intent(sendIntent).apply { setPackage("com.whatsapp") }
        val waBusiness = Intent(sendIntent).apply { setPackage("com.whatsapp.w4b") }
        when {
            waStandard.resolveActivity(context.packageManager) != null -> {
                runCatching { context.startActivity(waStandard) }
            }
            waBusiness.resolveActivity(context.packageManager) != null -> {
                runCatching { context.startActivity(waBusiness) }
            }
            else -> {
                openWhatsApp(context, phone213, message)
            }
        }
    }
}
