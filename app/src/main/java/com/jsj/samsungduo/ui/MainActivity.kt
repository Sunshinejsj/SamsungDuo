package com.jsj.samsungduo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The module's settings screen.
 *
 * <p>Reachable both from the launcher (through the activity alias) and from the
 * LSPosed manager, which launches the activity advertising
 * {@code de.robv.android.xposed.category.MODULE_SETTINGS}.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { SettingsRepository(applicationContext) }
            MiuixTheme(controller = remember { ThemeController() }) {
                SettingsScreen(repository)
            }
        }
    }
}
