package com.example

import com.example.data.model.TileCalculatorMath
import com.example.data.model.TileDimensionUnit
import com.example.ui.components.applyKeypadInput
import com.example.ui.components.applyKeypadQuickDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class TileCalculatorMathTest {

    @Test
    fun surfaceExactCartonsAndSurplus() {
        // 20 m2 with 1.44 m2/ctn -> ceil(20 / 1.44) = 14 ctns -> 20.16 m2 delivered, 0.16 m2 surplus
        val res = TileCalculatorMath.calculateFromSurface(
            surfaceM2 = 20.0,
            marginPercent = 0,
            m2PerCarton = 1.44,
            pcsPerCarton = 4,
            prixPerM2 = 1000.0
        )
        assertEquals(14, res.cartons)
        assertEquals(20.16, res.deliveredM2, 0.001)
        assertEquals(0.16, res.surplusM2, 0.001)
        assertEquals(20160.0, res.totalDa, 0.01)
    }

    @Test
    fun surfaceWith10PercentMargin() {
        // 20 m2 + 10% = 22 m2 -> ceil(22 / 1.44) = 16 ctns -> 23.04 m2
        val res = TileCalculatorMath.calculateFromSurface(
            surfaceM2 = 20.0,
            marginPercent = 10,
            m2PerCarton = 1.44,
            pcsPerCarton = 4,
            prixPerM2 = 1500.0
        )
        assertEquals(22.0, res.targetM2WithMargin, 0.001)
        assertEquals(16, res.cartons)
        assertEquals(23.04, res.deliveredM2, 0.001)
        assertEquals(3.04, res.surplusM2, 0.001)
        assertEquals(34560.0, res.totalDa, 0.01)
    }

    @Test
    fun dimensionsModeCalculatesAreaAndCartons() {
        // 5m x 4m = 20 m2 + 5% = 21 m2 -> ceil(21 / 1.44) = 15 ctns = 21.6 m2
        val res = TileCalculatorMath.calculateFromDimensions(
            lengthMeters = 5.0,
            widthMeters = 4.0,
            marginPercent = 5,
            m2PerCarton = 1.44,
            pcsPerCarton = 4,
            prixPerM2 = 1200.0
        )
        assertEquals(20.0, res.requestedM2, 0.001)
        assertEquals(15, res.cartons)
        assertEquals(21.6, res.deliveredM2, 0.001)
    }

    @Test
    fun cartonsAndLoosePiecesCalculation() {
        // 10 ctns + 2 pcs at 1.44 m2/ctn (4 pcs/ctn -> 0.36 m2/pc) = 14.4 + 0.72 = 15.12 m2
        val res = TileCalculatorMath.calculateFromCartonsAndPieces(
            cartons = 10,
            loosePieces = 2,
            m2PerCarton = 1.44,
            pcsPerCarton = 4,
            prixPerM2 = 1000.0
        )
        assertEquals(10, res.cartons)
        assertEquals(2, res.loosePieces)
        assertEquals(15.12, res.deliveredM2, 0.001)
        assertEquals(15120.0, res.totalDa, 0.01)
    }

    @Test
    fun guardsAgainstZeroColisageOrPieces() {
        val res = TileCalculatorMath.calculateFromSurface(
            surfaceM2 = 5.0,
            marginPercent = 0,
            m2PerCarton = 0.0,
            pcsPerCarton = 0,
            prixPerM2 = 500.0
        )
        assertEquals(0.01, res.m2PerCarton, 0.0001)
        assertEquals(1, res.pcsPerCarton)
    }

    @Test
    fun nonImeKeypadBufferRules() {
        val (first, p1) = applyKeypadInput("20", "5", allowDecimal = true, isPristine = true)
        assertEquals("5", first)
        assertEquals(false, p1)

        val (dot, _) = applyKeypadInput(first, ".", allowDecimal = true, isPristine = p1)
        assertEquals("5.", dot)

        val (dec, _) = applyKeypadInput(dot, "2", allowDecimal = true, isPristine = false)
        assertEquals("5.2", dec)

        val (noSecondDot, _) = applyKeypadInput(dec, ".", allowDecimal = true, isPristine = false)
        assertEquals("5.2", noSecondDot)

        val bumped = applyKeypadQuickDelta("12", 5.0, allowDecimal = true)
        assertEquals("17", bumped)
    }

    @Test
    fun tileDimensionsCmAndPiecesToM2PerCartonAndCartons() {
        // 60x60 cm tile = 0.36 m2 * 4 pcs = 1.44 m2/carton; 25 m2 -> ceil(25 / 1.44) = 18 cartons
        val res = TileCalculatorMath.calculateM2PerCartonAndCartons(
            tileLength = 60.0,
            tileWidth = 60.0,
            unit = TileDimensionUnit.CM,
            piecesPerCarton = 4,
            requiredAreaM2 = 25.0,
            marginPercent = 0
        )
        assertEquals(0.36, res.tileAreaM2, 0.0001)
        assertEquals(1.44, res.m2PerCarton, 0.0001)
        assertEquals(18, res.cartonsNeeded)
        assertEquals(25.92, res.deliveredAreaM2, 0.01)
        assertEquals(0.92, res.surplusAreaM2, 0.01)
        assertEquals(72, res.totalPieces)
    }

    @Test
    fun tileDimensionsMetersWithWasteMargin() {
        // 0.30m x 0.60m = 0.18 m2 * 6 pcs = 1.08 m2/carton
        // 20 m2 + 10% margin = 22 m2 -> ceil(22 / 1.08) = 21 cartons = 22.68 m2
        val res = TileCalculatorMath.calculateM2PerCartonAndCartons(
            tileLength = 0.30,
            tileWidth = 0.60,
            unit = TileDimensionUnit.METER,
            piecesPerCarton = 6,
            requiredAreaM2 = 20.0,
            marginPercent = 10
        )
        assertEquals(0.18, res.tileAreaM2, 0.0001)
        assertEquals(1.08, res.m2PerCarton, 0.0001)
        assertEquals(22.0, res.targetAreaWithMarginM2, 0.01)
        assertEquals(21, res.cartonsNeeded)
        assertEquals(22.68, res.deliveredAreaM2, 0.01)
    }

    @Test
    fun tileDimensionsZeroInputsReturnZeroCartons() {
        val res = TileCalculatorMath.calculateM2PerCartonAndCartons(
            tileLength = 0.0,
            tileWidth = 60.0,
            unit = TileDimensionUnit.CM,
            piecesPerCarton = 4,
            requiredAreaM2 = 20.0
        )
        assertEquals(0.0, res.m2PerCarton, 0.0001)
        assertEquals(0, res.cartonsNeeded)
        assertEquals(0.0, res.deliveredAreaM2, 0.01)
    }

    @Test
    fun loosePiecesUseExplicitPieceCostWhenProvided() {
        // 2 cartons (2 * 1.44 m2 = 2.88 m2 @ 1000 DA/m2 = 2880 DA) + 3 loose pcs @ 400 DA/pc = 1200 DA -> total 4080 DA
        val res = TileCalculatorMath.calculateFromCartonsAndPieces(
            cartons = 2,
            loosePieces = 3,
            m2PerCarton = 1.44,
            pcsPerCarton = 4,
            prixPerM2 = 1000.0,
            prixParPiece = 400.0
        )
        assertEquals(3.96, res.deliveredM2, 0.001)
        assertEquals(1200.0, res.piecesTotalDa, 0.01)
        assertEquals(4080.0, res.totalDa, 0.01)

        val (m2Total, subTotal) = TileCalculatorMath.calculateCartItemTotals(
            cartons = 2,
            pieces = 3,
            m2PerCarton = 1.44,
            pcsPerCarton = 4,
            prixPerM2 = 1000.0,
            prixParPiece = 400.0
        )
        assertEquals(3.96, m2Total, 0.001)
        assertEquals(4080.0, subTotal, 0.01)
    }

    @Test
    fun directM2PerCartonToCartonsWithMargin() {
        // 30 m2 at 1.08 m2/ctn (6 pcs/ctn) with +10% margin = 33 m2 -> ceil(33 / 1.08) = 31 cartons
        val res = TileCalculatorMath.calculateCartonsFromM2PerCarton(
            m2PerCarton = 1.08,
            piecesPerCarton = 6,
            requiredAreaM2 = 30.0,
            marginPercent = 10
        )
        assertEquals(0.18, res.tileAreaM2, 0.0001)
        assertEquals(1.08, res.m2PerCarton, 0.0001)
        assertEquals(33.0, res.targetAreaWithMarginM2, 0.01)
        assertEquals(31, res.cartonsNeeded)
        assertEquals(33.48, res.deliveredAreaM2, 0.01)
        assertEquals(186, res.totalPieces)
    }

    @Test
    fun roomTilePlanCalculatesAreaTileCountAndCartonsWithConfigurableCoverage() {
        // 5m x 4m = 20 m2, 60x60 cm tile (0.36 m2/tile -> 56 tiles), custom 1.44 m2/carton -> ceil(20 / 1.44) = 14 cartons
        val res1 = TileCalculatorMath.calculateRoomTilePlan(
            roomLengthM = 5.0,
            roomWidthM = 4.0,
            tileLength = 60.0,
            tileWidth = 60.0,
            tileUnit = TileDimensionUnit.CM,
            m2PerCarton = 1.44,
            marginPercent = 0
        )
        assertEquals(20.0, res1.totalAreaM2, 0.01)
        assertEquals(0.36, res1.tileAreaM2, 0.0001)
        assertEquals(56, res1.tilesNeeded)
        assertEquals(14, res1.cartonsNeeded)
        assertEquals(20.16, res1.deliveredAreaM2, 0.01)
        assertEquals(0.16, res1.surplusAreaM2, 0.01)

        // Configurable 1.08 m2/carton with +10% waste (22 m2) and 0.3m x 0.6m tile (0.18 m2/tile) -> 123 tiles, ceil(22 / 1.08) = 21 cartons
        val res2 = TileCalculatorMath.calculateRoomTilePlan(
            roomLengthM = 5.0,
            roomWidthM = 4.0,
            tileLength = 0.30,
            tileWidth = 0.60,
            tileUnit = TileDimensionUnit.METER,
            m2PerCarton = 1.08,
            marginPercent = 10
        )
        assertEquals(20.0, res2.totalAreaM2, 0.01)
        assertEquals(22.0, res2.targetAreaWithMarginM2, 0.01)
        assertEquals(123, res2.tilesNeeded)
        assertEquals(21, res2.cartonsNeeded)
        assertEquals(22.68, res2.deliveredAreaM2, 0.01)
    }
}
