package com.juying.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.juying.tv.ui.MainViewModel
import com.juying.tv.ui.detail.DetailScreen
import com.juying.tv.ui.home.HomeScreen
import com.juying.tv.ui.home.MineScreen
import com.juying.tv.ui.live.LiveScreen
import com.juying.tv.ui.search.SearchScreen
import com.juying.tv.ui.settings.SettingsScreen
import com.juying.tv.ui.theme.JyTheme
import com.juying.tv.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JyTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "tabs") {
                    composable("tabs") { TabsScreen(nav) }
                    composable("search") { SearchScreen(nav) }
                    composable("detail/{vodId}") { back ->
                        DetailScreen(nav, back.arguments?.getString("vodId") ?: "")
                    }
                    composable("settings") { SettingsScreen(nav) }
                }
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun TabsScreen(rootNav: NavHostController) {
    val tabs = listOf(
        TabItem("home", "首页", Icons.Default.Home),
        TabItem("live", "直播", Icons.Default.PlayArrow),
        TabItem("mine", "我的", Icons.Default.Person),
    )
    var selected by remember { mutableStateOf("home") }
    val mainVm: MainViewModel = viewModel()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = selected == t.route,
                        onClick = { selected = t.route },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                "home" -> HomeScreen(rootNav, mainVm)
                "live" -> LiveScreen(rootNav, mainVm)
                "mine" -> MineScreen(rootNav)
            }
        }
    }
}
