package com.example

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.TileProduct
import com.example.ui.screens.M2PerCartonCalculatorScreen
import com.example.ui.theme.KaderPosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class M2PerCartonCalculatorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun enteringAreaAndChangingTileSpecsAutomaticallyRecalculatesCartons() {
        val sampleProduct = TileProduct(
            id = "tile_1",
            name = "Marbre Royal",
            usine = "Ceramika",
            dimension = "60x120",
            m2Carton = 1.44,
            pcs = 2,
            prix = 2800.0,
            prixAchat = 2200.0,
            stock = 40
        )

        composeTestRule.setContent {
            KaderPosTheme {
                M2PerCartonCalculatorScreen(products = listOf(sampleProduct))
            }
        }

        // Initial defaults: 25 m², 60x60 cm, 4 pcs/ctn (1.44 m²/ctn) -> ceil(25 / 1.44) = 18 Cartons
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calc_result_cartons").performScrollTo().assertTextContains("18 Cartons")

        // Typing 30 m² updates cartons automatically without clicking any Calculate button -> ceil(30 / 1.44) = 21 Cartons
        composeTestRule.onNodeWithTag("required_area_input").performScrollTo().performTextReplacement("30")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calc_result_cartons").performScrollTo().assertTextContains("21 Cartons")

        // Switching to direct colisage mode and selecting 1.08 m²/ctn updates cartons -> ceil(30 / 1.08) = 28 Cartons
        composeTestRule.onNodeWithTag("spec_mode_colisage").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("direct_m2_per_carton_input").performScrollTo()
        composeTestRule.onNodeWithTag("colisage_preset_1.08").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calc_result_cartons").performScrollTo().assertTextContains("28 Cartons")

        // Adding +10% cutting margin updates cartons -> ceil(33 / 1.08) = 31 Cartons
        composeTestRule.onNodeWithTag("margin_chip_10").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calc_result_cartons").performScrollTo().assertTextContains("31 Cartons")

        // Switching to catalog tile mode uses sampleProduct (1.44 m²/ctn): 33 / 1.44 = 23 Cartons
        composeTestRule.onNodeWithTag("spec_mode_catalog").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calc_result_cartons").performScrollTo().assertTextContains("23 Cartons")
    }
}
