package com.example

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.RoomTileCalculationResult
import com.example.ui.components.TileCalculator
import com.example.ui.screens.M2PerCartonCalculatorScreen
import com.example.ui.theme.KaderPosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TileCalculatorComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun roomDimensionsTileSizeAndConfigurableM2PerCartonAutoCalculateAreaAndCartons() {
        var addedResult: RoomTileCalculationResult? = null

        composeTestRule.setContent {
            KaderPosTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TileCalculator(
                        onAddToCart = { addedResult = it }
                    )
                }
            }
        }

        // Initial defaults: 5m x 4m = 20 m2, 60x60 cm (0.36 m2/tile -> 56 pcs), 1.44 m2/ctn -> 14 Cartons
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tile_calc_total_area").performScrollTo().assertTextContains("20 m²")
        composeTestRule.onNodeWithTag("tile_calc_cartons_needed").performScrollTo().assertTextContains("14 Cartons")
        composeTestRule.onNodeWithTag("tile_calc_tiles_needed").performScrollTo().assertTextContains("56 pcs")

        // Change room dimensions to 6m x 5m = 30 m2 -> ceil(30 / 1.44) = 21 Cartons
        composeTestRule.onNodeWithTag("tile_calc_room_length_input").performScrollTo().performTextReplacement("6")
        composeTestRule.onNodeWithTag("tile_calc_room_width_input").performScrollTo().performTextReplacement("5")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tile_calc_total_area").performScrollTo().assertTextContains("30 m²")
        composeTestRule.onNodeWithTag("tile_calc_cartons_needed").performScrollTo().assertTextContains("21 Cartons")

        // Configure tile size to 30x60 cm (0.18 m2/tile -> 167 pcs) and configurable m2/carton to 1.08 -> ceil(30 / 1.08) = 28 Cartons
        composeTestRule.onNodeWithTag("tile_calc_tile_length_input").performScrollTo().performTextReplacement("30")
        composeTestRule.onNodeWithTag("tile_calc_tile_width_input").performScrollTo().performTextReplacement("60")
        composeTestRule.onNodeWithTag("tile_calc_m2_per_carton_input").performScrollTo().performTextReplacement("1.08")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tile_calc_tiles_needed").performScrollTo().assertTextContains("167 pcs")
        composeTestRule.onNodeWithTag("tile_calc_cartons_needed").performScrollTo().assertTextContains("28 Cartons")

        // Add +10% cutting margin: 30 * 1.10 = 33 m2 -> ceil(33 / 1.08) = 31 Cartons
        composeTestRule.onNodeWithTag("tile_calc_margin_chip_10").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tile_calc_cartons_needed").performScrollTo().assertTextContains("31 Cartons")

        // Trigger add to cart callback
        composeTestRule.onNodeWithTag("tile_calc_send_to_pos_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertNotNull(addedResult)
        assertEquals(30.0, addedResult!!.totalAreaM2, 0.01)
        assertEquals(31, addedResult!!.cartonsNeeded)
        assertEquals(1.08, addedResult!!.m2PerCarton, 0.001)
    }

    @Test
    fun calculatorScreenSwitchesToRoomTileCalculatorComponent() {
        composeTestRule.setContent {
            KaderPosTheme {
                M2PerCartonCalculatorScreen()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calc_top_mode_room").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tile_calc_component").assertExists()
        composeTestRule.onNodeWithTag("tile_calc_total_area").performScrollTo().assertTextContains("20 m²")
    }
}
