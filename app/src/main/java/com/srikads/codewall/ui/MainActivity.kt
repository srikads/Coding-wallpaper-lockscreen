package com.srikads.codewall.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.srikads.codewall.config.ConfigStore
import com.srikads.codewall.core.WallConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CodeWallTheme { App() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    PREVIEW("Preview", Icons.Filled.Code),
    FIELDS("Fields", Icons.Filled.Tune),
    STYLE("Style", Icons.Filled.Palette),
    TASKS("Tasks", Icons.Filled.Checklist),
}

/** Holds the editable config and persists every change immediately. */
class ConfigState(private val store: ConfigStore) {
    var config by mutableStateOf(store.config())
        private set

    fun update(block: (WallConfig) -> WallConfig) {
        config = block(config)
        store.saveConfig(config)
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val store = remember { ConfigStore(context) }
    val state = remember { ConfigState(store) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (Tab.entries[tab]) {
            Tab.PREVIEW -> PreviewScreen(modifier)
            Tab.FIELDS -> FieldsScreen(state, modifier)
            Tab.STYLE -> StyleScreen(state, modifier)
            Tab.TASKS -> TasksScreen(store, modifier)
        }
    }
}
