package com.loc.benimdunyam.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Triple(BottomNavScreen.Home.route, "Ana Sayfa", Icons.Default.Home),
        Triple(BottomNavScreen.Journals.route, "Günlükler", Icons.Default.Book),
        Triple(BottomNavScreen.People.route, "İnsanlarım", Icons.Default.People)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Sadece ana sekme rotalarındayken alt menüyü göster
    if (currentRoute in items.map { it.first }) {
        NavigationBar {
            items.forEach { (route, title, icon) ->
                NavigationBarItem(
                    icon = { Icon(imageVector = icon, contentDescription = title) },
                    label = { Text(title) },
                    selected = currentRoute == route,
                    onClick = {
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(Screen.Main.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}