package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation(collectorViewModel: CollectorViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                viewModel = collectorViewModel,
                onLoginSuccess = { operatorName, operatorId ->
                    collectorViewModel.operatorName = operatorName
                    collectorViewModel.operatorId = operatorId
                    navController.navigate("inventory_selection")
                }
            )
        }
        composable("inventory_selection") {
            InventorySelectionScreen(
                viewModel = collectorViewModel,
                onInventorySelected = { inventoryId ->
                    collectorViewModel.selectInventory(inventoryId)
                    navController.navigate("section_selection")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("section_selection") {
            SectionSelectionScreen(
                viewModel = collectorViewModel,
                onSectionSelected = { sectorId, sectionCode ->
                    collectorViewModel.sectorId = sectorId
                    collectorViewModel.currentSectionCode = sectionCode
                    navController.navigate("collector")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("collector") {
            CollectorScreen(
                viewModel = collectorViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
