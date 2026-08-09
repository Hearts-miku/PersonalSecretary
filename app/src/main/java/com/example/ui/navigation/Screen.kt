package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "主页", Icons.Default.EditNote)
    object Timeline : Screen("timeline", "时间线", Icons.Default.CalendarMonth)
    object TodoList : Screen("todo", "待办", Icons.Default.FormatListNumbered)
    object Profile : Screen("profile", "个人中心", Icons.Default.Person)
    object Resume : Screen("resume", "我的简历", Icons.Default.Description)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Timeline,
    Screen.TodoList,
    Screen.Profile,
    Screen.Settings
)
