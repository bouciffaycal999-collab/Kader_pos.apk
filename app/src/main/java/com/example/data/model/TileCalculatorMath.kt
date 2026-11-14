package com.example.data.model

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class CalcInputMode {
    SURFACE,
    DIMENSIONS,
    CARTONS_PIECES
}

data class TileCalculationResult(
    val requestedM2: Double,
    val marginPercent: Int,
    val targetM2WithMargin: Double,
    val cartons: Int,
    val loosePieces: Int,
    val deliveredM2: Double,
    val surplusM2: Double,
    val totalDa: Double,
    val m2PerCarton: Double,
    val pcsPerCarton: Int,
    val m2PerPiece: Double,
    val prixParPiece: Double = 0.0,
    val piecesTotalDa: Double = 0.0
)

enum class TileDimensionUnit(val suffix: String) {
    CM("cm"),
    METER("m")
}

data class TileFormatPreset(
    val label: String,
    val lengthCm: Double,
    val widthCm: Double,
    val piecesPerCarton: Int
)

data class TileBoxCalculationResult(
    val tileAreaM2: Double,
    val m2PerCarton: Double,
    val piecesPerCarton: Int,
    val requiredAreaM2: Double,
    val marginPercent: Int,
    val targetAreaWithMarginM2: Double,
    val cartonsNeeded: Int,
    val deliveredAreaM2: Double,
    val surplusAreaM2: Double,
    val totalPieces: Int
)

data class RoomTileCalculationResult(
    val roomLengthM: Double,
    val roomWidthM: Double,
    val totalAreaM2: Double,
    val marginPercent: Int,
    val targetAreaWithMarginM2: Double,
    val tileAreaM2: Double,
    val tilesNeeded: Int,
    val m2PerCarton: Double,
    val cartonsNeeded: Int,
    val deliveredAreaM2: Double,
    val surplusAreaM2: Double
)

object TileCalculatorMath {
    val COLISAGE_PRESETS = listOf(1.44, 1.08, 1.20, 1.42, 1.50, 1.62)
    val MARGIN_PRESETS = listOf(0, 5, 10, 15)
    val TILE_FORMAT_PRESETS = listOf(
        TileFormatPreset("60×60", 60.0, 60.0, 4),
        TileFormatPreset("45×45", 45.0, 45.0, 7),
        TileFormatPreset("30×60", 30.0, 60.0, 6),
        TileFormatPreset("80×80", 80.0, 80.0, 3),
        TileFormatPreset("120×60", 120.0, 60.0, 2)
    )

    private fun safeM2(m2PerCarton: Double): Double = max(0.01, m2PerCarton)
    private fun safePcs(pcsPerCarton: Int): Int = max(1, pcsPerCarton)
    private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
    private fun round4(value: Double): Double = (value * 10000.0).roundToInt() / 10000.0

    fun calculateRoomTilePlan(
        roomLengthM: Double,
        roomWidthM: Double,
        tileLength: Double,
        tileWidth: Double,
        tileUnit: TileDimensionUnit,
        m2PerCarton: Double,
        marginPercent: Int = 0
    ): RoomTileCalculationResult {
        val cleanRoomL = roomLengthM.coerceAtLeast(0.0)
        val cleanRoomW = roomWidthM.coerceAtLeast(0.0)
        val cleanTileL = tileLength.coerceAtLeast(0.0)
        val cleanTileW = tileWidth.coerceAtLeast(0.0)
        val cleanM2PerCarton = m2PerCarton.coerceAtLeast(0.0)
        val cleanMargin = marginPercent.coerceIn(0, 50)

        val totalArea = cleanRoomL * cleanRoomW
        val targetArea = totalArea * (1.0 + cleanMargin / 100.0)

        val tileLM = if (tileUnit == TileDimensionUnit.CM) cleanTileL / 100.0 else cleanTileL
        val tileWM = if (tileUnit == TileDimensionUnit.CM) cleanTileW / 100.0 else cleanTileW
        val singleTileArea = tileLM * tileWM

        val tilesNeeded = if (singleTileArea <= 0.00001 || targetArea <= 0.00001) {
            0
        } else {
            ceil((targetArea / singleTileArea) - 1e-9).toInt().coerceAtLeast(1)
        }

        val cartonsNeeded = if (cleanM2PerCarton <= 0.00001 || targetArea <= 0.00001) {
            0
        } else {
            ceil((targetArea / cleanM2PerCarton) - 1e-9).toInt().coerceAtLeast(1)
        }

        val deliveredArea = cartonsNeeded * cleanM2PerCarton
        val surplusArea = (deliveredArea - totalArea).coerceAtLeast(0.0)

        return RoomTileCalculationResult(
            roomLengthM = round2(cleanRoomL),
            roomWidthM = round2(cleanRoomW),
            totalAreaM2 = round2(totalArea),
            marginPercent = cleanMargin,
            targetAreaWithMarginM2 = round2(targetArea),
            tileAreaM2 = round4(singleTileArea),
            tilesNeeded = tilesNeeded,
            m2PerCarton = round4(cleanM2PerCarton),
            cartonsNeeded = cartonsNeeded,
            deliveredAreaM2 = round2(deliveredArea),
            surplusAreaM2 = round2(surplusArea)
        )
    }

    fun calculateM2PerCartonAndCartons(
        tileLength: Double,
        tileWidth: Double,
        unit: TileDimensionUnit,
        piecesPerCarton: Int,
        requiredAreaM2: Double,
        marginPercent: Int = 0
    ): TileBoxCalculationResult {
        val cleanLength = tileLength.coerceAtLeast(0.0)
        val cleanWidth = tileWidth.coerceAtLeast(0.0)
        val cleanPcs = piecesPerCarton.coerceAtLeast(0)
        val cleanRequired = requiredAreaM2.coerceAtLeast(0.0)
        val cleanMargin = marginPercent.coerceIn(0, 50)

        val lengthM = if (unit == TileDimensionUnit.CM) cleanLength / 100.0 else cleanLength
        val widthM = if (unit == TileDimensionUnit.CM) cleanWidth / 100.0 else cleanWidth

        val singleTileAreaM2 = lengthM * widthM
        val exactM2PerCarton = singleTileAreaM2 * cleanPcs
        val targetArea = cleanRequired * (1.0 + cleanMargin / 100.0)

        val cartonsNeeded = if (exactM2PerCarton <= 0.00001 || targetArea <= 0.00001) {
            0
        } else {
            ceil((targetArea / exactM2PerCarton) - 1e-9).toInt().coerceAtLeast(1)
        }

        val deliveredArea = cartonsNeeded * exactM2PerCarton
        val surplusArea = (deliveredArea - cleanRequired).coerceAtLeast(0.0)

        return TileBoxCalculationResult(
            tileAreaM2 = round4(singleTileAreaM2),
            m2PerCarton = round4(exactM2PerCarton),
            piecesPerCarton = cleanPcs,
            requiredAreaM2 = round2(cleanRequired),
            marginPercent = cleanMargin,
            targetAreaWithMarginM2 = round2(targetArea),
            cartonsNeeded = cartonsNeeded,
            deliveredAreaM2 = round2(deliveredArea),
            surplusAreaM2 = round2(surplusArea),
            totalPieces = cartonsNeeded * cleanPcs
        )
    }

    fun calculateCartonsFromM2PerCarton(
        m2PerCarton: Double,
        piecesPerCarton: Int,
        requiredAreaM2: Double,
        marginPercent: Int = 0
    ): TileBoxCalculationResult {
        val cleanM2Ctn = m2PerCarton.coerceAtLeast(0.0)
        val cleanPcs = piecesPerCarton.coerceAtLeast(1)
        val cleanRequired = requiredAreaM2.coerceAtLeast(0.0)
        val cleanMargin = marginPercent.coerceIn(0, 50)

        val targetArea = cleanRequired * (1.0 + cleanMargin / 100.0)
        val cartonsNeeded = if (cleanM2Ctn <= 0.00001 || targetArea <= 0.00001) {
            0
        } else {
            ceil((targetArea / cleanM2Ctn) - 1e-9).toInt().coerceAtLeast(1)
        }
        val deliveredArea = cartonsNeeded * cleanM2Ctn
        val surplusArea = (deliveredArea - cleanRequired).coerceAtLeast(0.0)
        val tileArea = cleanM2Ctn / cleanPcs

        return TileBoxCalculationResult(
            tileAreaM2 = round4(tileArea),
            m2PerCarton = round4(cleanM2Ctn),
            piecesPerCarton = cleanPcs,
            requiredAreaM2 = round2(cleanRequired),
            marginPercent = cleanMargin,
            targetAreaWithMarginM2 = round2(targetArea),
            cartonsNeeded = cartonsNeeded,
            deliveredAreaM2 = round2(deliveredArea),
            surplusAreaM2 = round2(surplusArea),
            totalPieces = cartonsNeeded * cleanPcs
        )
    }

    fun calculateFromSurface(
        surfaceM2: Double,
        marginPercent: Int,
        m2PerCarton: Double,
        pcsPerCarton: Int,
        prixPerM2: Double
    ): TileCalculationResult {
        val cleanSurface = surfaceM2.coerceAtLeast(0.0)
        val safeM2Ctn = safeM2(m2PerCarton)
        val safePcsCtn = safePcs(pcsPerCarton)
        val m2Piece = safeM2Ctn / safePcsCtn
        val marginFactor = 1.0 + (marginPercent.coerceIn(0, 50) / 100.0)
        val targetM2 = cleanSurface * marginFactor
        val cartons = if (targetM2 <= 0.0001) {
            0
        } else {
            ceil((targetM2 / safeM2Ctn) - 1e-9).toInt().coerceAtLeast(1)
        }
        val delivered = round2(cartons * safeM2Ctn)
        val surplus = round2((delivered - cleanSurface).coerceAtLeast(0.0))
        val total = round2(delivered * prixPerM2.coerceAtLeast(0.0))

        return TileCalculationResult(
            requestedM2 = round2(cleanSurface),
            marginPercent = marginPercent.coerceIn(0, 50),
            targetM2WithMargin = round2(targetM2),
            cartons = cartons,
            loosePieces = 0,
            deliveredM2 = delivered,
            surplusM2 = surplus,
            totalDa = total,
            m2PerCarton = safeM2Ctn,
            pcsPerCarton = safePcsCtn,
            m2PerPiece = round2(m2Piece)
        )
    }

    fun calculateFromDimensions(
        lengthMeters: Double,
        widthMeters: Double,
        marginPercent: Int,
        m2PerCarton: Double,
        pcsPerCarton: Int,
        prixPerM2: Double
    ): TileCalculationResult {
        val area = lengthMeters.coerceAtLeast(0.0) * widthMeters.coerceAtLeast(0.0)
        return calculateFromSurface(
            surfaceM2 = area,
            marginPercent = marginPercent,
            m2PerCarton = m2PerCarton,
            pcsPerCarton = pcsPerCarton,
            prixPerM2 = prixPerM2
        )
    }

    @JvmOverloads
    fun calculateFromCartonsAndPieces(
        cartons: Int,
        loosePieces: Int,
        m2PerCarton: Double,
        pcsPerCarton: Int,
        prixPerM2: Double,
        prixParPiece: Double = 0.0
    ): TileCalculationResult {
        val safeM2Ctn = safeM2(m2PerCarton)
        val safePcsCtn = safePcs(pcsPerCarton)
        val cleanCartons = cartons.coerceAtLeast(0)
        val cleanPieces = loosePieces.coerceAtLeast(0)
        val cleanPrixM2 = prixPerM2.coerceAtLeast(0.0)
        val cleanPrixPiece = prixParPiece.coerceAtLeast(0.0)
        val m2Piece = safeM2Ctn / safePcsCtn
        val cartonsM2 = cleanCartons * safeM2Ctn
        val delivered = round2(cartonsM2 + (cleanPieces * m2Piece))
        val usePieceCost = cleanPieces > 0 && cleanPrixPiece > 0.0
        val piecesDa = if (usePieceCost) round2(cleanPieces * cleanPrixPiece) else round2(cleanPieces * m2Piece * cleanPrixM2)
        val total = if (usePieceCost) {
            round2((cartonsM2 * cleanPrixM2) + (cleanPieces * cleanPrixPiece))
        } else {
            round2(delivered * cleanPrixM2)
        }

        return TileCalculationResult(
            requestedM2 = delivered,
            marginPercent = 0,
            targetM2WithMargin = delivered,
            cartons = cleanCartons,
            loosePieces = cleanPieces,
            deliveredM2 = delivered,
            surplusM2 = 0.0,
            totalDa = total,
            m2PerCarton = safeM2Ctn,
            pcsPerCarton = safePcsCtn,
            m2PerPiece = round2(m2Piece),
            prixParPiece = cleanPrixPiece,
            piecesTotalDa = piecesDa
        )
    }

    fun calculateCartItemTotals(
        cartons: Int,
        pieces: Int,
        m2PerCarton: Double,
        pcsPerCarton: Int,
        prixPerM2: Double,
        prixParPiece: Double = 0.0
    ): Pair<Double, Double> {
        val res = calculateFromCartonsAndPieces(
            cartons = cartons,
            loosePieces = pieces,
            m2PerCarton = m2PerCarton,
            pcsPerCarton = pcsPerCarton,
            prixPerM2 = prixPerM2,
            prixParPiece = prixParPiece
        )
        return res.deliveredM2 to res.totalDa
    }

    fun calculateFromPiecesOnly(
        pieces: Int,
        m2PerCarton: Double,
        pcsPerCarton: Int,
        prixParPiece: Double,
        prixPerM2: Double = 0.0
    ): TileCalculationResult {
        val safeM2Ctn = safeM2(m2PerCarton)
        val safePcsCtn = safePcs(pcsPerCarton)
        val cleanPieces = pieces.coerceAtLeast(0)
        val m2Piece = safeM2Ctn / safePcsCtn
        val delivered = round2(cleanPieces * m2Piece)
        val effPiecePrice = if (prixParPiece > 0.0) {
            prixParPiece
        } else {
            prixPerM2.coerceAtLeast(0.0) * m2Piece
        }
        val piecesDa = round2(cleanPieces * effPiecePrice)
        return TileCalculationResult(
            requestedM2 = delivered,
            marginPercent = 0,
            targetM2WithMargin = delivered,
            cartons = 0,
            loosePieces = cleanPieces,
            deliveredM2 = delivered,
            surplusM2 = 0.0,
            totalDa = piecesDa,
            m2PerCarton = safeM2Ctn,
            pcsPerCarton = safePcsCtn,
            m2PerPiece = round2(m2Piece),
            prixParPiece = effPiecePrice,
            piecesTotalDa = piecesDa
        )
    }
}
