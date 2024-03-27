package com.lucas.automateddutyplanner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItems(val route: String, val icon: ImageVector, val label: String)

val BottomNavItemList: List<BottomNavItems> = listOf(
    BottomNavItems("home", Icons.Default.Home, "Home"),
    BottomNavItems("results", Icons.Default.Person, "Results"),
    BottomNavItems("settings", Icons.Default.Settings, "Settings")
)