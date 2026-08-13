package cc.netwokx.ffsms.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.ui.compose.ComposeScreen
import cc.netwokx.ffsms.ui.groups.GroupDetailScreen
import cc.netwokx.ffsms.ui.groups.GroupsScreen
import cc.netwokx.ffsms.ui.history.CampaignDetailScreen
import cc.netwokx.ffsms.ui.history.HistoryScreen
import cc.netwokx.ffsms.ui.privacy.PrivacyScreen
import cc.netwokx.ffsms.ui.settings.SettingsScreen
import cc.netwokx.ffsms.ui.theme.FfSmsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FfSmsTheme {
                FfSmsApp()
            }
        }
    }
}

private sealed class Dest(val route: String) {
    data object Compose : Dest("compose")
    data object Groups : Dest("groups")
    data object History : Dest("history")
    data object Settings : Dest("settings")
    data object Privacy : Dest("privacy")
    data object GroupDetail : Dest("groups/{groupId}") {
        fun of(groupId: Long) = "groups/$groupId"
    }
    data object CampaignDetail : Dest("history/{campaignId}") {
        fun of(campaignId: String) = "history/$campaignId"
    }
}

private data class TabItem(val dest: Dest, val labelRes: Int, val icon: ImageVector)

private val TABS = listOf(
    TabItem(Dest.Compose, R.string.nav_compose, Icons.Default.Edit),
    TabItem(Dest.Groups, R.string.nav_groups, Icons.Default.Group),
    TabItem(Dest.History, R.string.nav_history, Icons.Default.History),
    TabItem(Dest.Settings, R.string.nav_settings, Icons.Default.Settings),
)

@Composable
private fun FfSmsApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    val selected = currentRoute?.hierarchy?.any { it.route == tab.dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(tab.dest.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Compose.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Compose.route) {
                ComposeScreen(onOpenGroups = { navController.navigateToTab(Dest.Groups.route) })
            }
            composable(Dest.Groups.route) {
                GroupsScreen(onOpenGroup = { navController.navigate(Dest.GroupDetail.of(it)) })
            }
            composable(Dest.GroupDetail.route) { entry ->
                val groupId = entry.arguments?.getString("groupId")?.toLongOrNull()
                if (groupId == null) {
                    navController.popBackStack()
                } else {
                    GroupDetailScreen(groupId = groupId, onBack = { navController.popBackStack() })
                }
            }
            composable(Dest.History.route) {
                HistoryScreen(onOpenCampaign = { navController.navigate(Dest.CampaignDetail.of(it)) })
            }
            composable(Dest.CampaignDetail.route) { entry ->
                val campaignId = entry.arguments?.getString("campaignId")
                if (campaignId == null) {
                    navController.popBackStack()
                } else {
                    CampaignDetailScreen(campaignId = campaignId, onBack = { navController.popBackStack() })
                }
            }
            composable(Dest.Settings.route) {
                SettingsScreen(onOpenPrivacy = { navController.navigate(Dest.Privacy.route) })
            }
            composable(Dest.Privacy.route) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
