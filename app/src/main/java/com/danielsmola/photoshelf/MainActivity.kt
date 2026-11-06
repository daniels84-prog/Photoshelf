package com.danielsmola.photoshelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danielsmola.photoshelf.ui.AlbumScreen
import com.danielsmola.photoshelf.ui.BusyDialog
import com.danielsmola.photoshelf.ui.HomeScreen
import com.danielsmola.photoshelf.ui.LocalSession
import com.danielsmola.photoshelf.ui.LoginScreen
import com.danielsmola.photoshelf.ui.PhotoShelfTheme
import com.danielsmola.photoshelf.ui.PickRootScreen
import com.danielsmola.photoshelf.ui.SettingsScreen
import com.danielsmola.photoshelf.ui.StartingScreen
import com.danielsmola.photoshelf.ui.ViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoShelfTheme {
                val vm: MainViewModel = viewModel()
                App(vm)
            }
        }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val stack by vm.stack.collectAsState()
    val session by vm.session.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.onFolderPicked(uri)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.launches.collect { intent ->
            runCatching { context.startActivity(intent) }
                .onFailure { vm.toast("No app on this phone can open that.") }
        }
    }
    LaunchedEffect(Unit) { vm.folderPicks.collect { runCatching { folderPicker.launch(null) } } }

    // Screen-level back handlers are composed later, so they take priority over this one.
    BackHandler(enabled = stack.size > 1) { vm.back() }

    CompositionLocalProvider(LocalSession provides session) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val last = stack.lastIndex
            stack.forEachIndexed { i, screen ->
                // The viewer draws over the screen it came from, which stays alive to keep its scroll position.
                val show = i == last || (i == last - 1 && stack[last] is Screen.Viewer)
                if (show) {
                    key(i, System.identityHashCode(screen)) { ScreenContent(vm, screen) }
                }
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
            )
        }
    }

    busy?.let { BusyDialog(it) { vm.cancelBusy() } }
}

@Composable
private fun ScreenContent(vm: MainViewModel, screen: Screen) {
    when (screen) {
        Screen.Starting -> StartingScreen()
        Screen.Login -> LoginScreen(vm)
        Screen.PickRoot -> PickRootScreen(vm)
        Screen.Home -> HomeScreen(vm)
        Screen.SettingsScreen -> SettingsScreen(vm)
        is Screen.Album -> AlbumScreen(vm, screen.info)
        is Screen.Viewer -> ViewerScreen(vm, screen)
    }
}
