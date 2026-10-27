package com.lumen.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.lumen.app.data.AccountInfo
import com.lumen.app.ui.Button
import com.lumen.app.ui.Gold
import com.lumen.app.ui.Surface
import com.lumen.app.ui.TextMain
import com.lumen.app.ui.DetailScreens
import com.lumen.app.ui.HomeScreen
import com.lumen.app.ui.LivePlayerScreen
import com.lumen.app.ui.LoginScreen
import com.lumen.app.ui.Bg
import com.lumen.app.ui.IntroScreen
import com.lumen.app.ui.TvTheme
import com.lumen.app.ui.VodPlayerScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val dpad = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> true
            else -> false
        }
        if (dpad && !vm.anyFocus && vm.screens.last() is Screen.Home) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) vm.focusKick++
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TvTheme {
                // testi più grandi per la lettura da lontano (regolabile nelle impostazioni)
                val d = LocalDensity.current
                val scale = floatArrayOf(1f, 1.15f, 1.3f)[vm.textScale]
                CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * scale)) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val top = vm.screens.last()
    var introDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5500)
        introDone = true
    }
    BackHandler(enabled = vm.screens.size > 1) { vm.back() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(vm.focusKick) { if (vm.focusKick > 0) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }
    LaunchedEffect(introDone) {
        if (introDone) {
            repeat(10) {
                delay(300)
                if (!vm.anyFocus && vm.screens.last() is Screen.Home) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
            }
        }
    }
    var showExit by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity
    BackHandler(enabled = vm.screens.size <= 1 && introDone && !showExit) { showExit = true }

    Box(Modifier.fillMaxSize().background(Bg).onFocusChanged { vm.anyFocus = it.hasFocus }) {
        when {
            !introDone || top is Screen.Splash -> IntroScreen(vm.expiryText())
            else -> AppContent(vm, top)
        }
        val notice = vm.renewalNotice
        if (notice != null && introDone && top !is Screen.Splash) RenewalDialog(notice) { vm.dismissRenewal() }

        val upd = vm.update
        androidx.compose.animation.AnimatedVisibility(
            visible = upd != null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 24.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
        ) {
            // durante la dissolvenza upd è già null: si tiene l'ultimo valore
            var last by remember { mutableStateOf("" to 1f) }
            if (upd != null) last = upd
            com.lumen.app.ui.UpdateBar(last.first, last.second)
        }
        if (showExit) ExitDialog(onNo = { showExit = false }, onYes = { activity?.finishAndRemoveTask() })
    }
}

@Composable
private fun RenewalDialog(text: String, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).clip(RoundedCornerShape(16.dp)).background(Surface).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.renewal_title), color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text, color = TextMain, fontSize = 17.sp, textAlign = TextAlign.Center)
            Button("OK", onClick = onClose, primary = true, fr = fr, modifier = Modifier.width(200.dp))
        }
    }
}

@Composable
private fun ExitDialog(onNo: () -> Unit, onYes: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onNo() }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).clip(RoundedCornerShape(16.dp)).background(Surface).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.exit_title), color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.exit_msg), color = TextMain, fontSize = 17.sp, textAlign = TextAlign.Center)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(stringResource(R.string.yes), onClick = onYes, modifier = Modifier.width(200.dp))
                Button(stringResource(R.string.no), onClick = onNo, primary = true, fr = fr, modifier = Modifier.width(200.dp))
            }
        }
    }
}

@Composable
private fun AppContent(vm: AppViewModel, top: Screen) {
    Box(Modifier.fillMaxSize()) {
        when (top) {
            is Screen.Splash -> {}
            is Screen.Login -> LoginScreen(vm)
            else -> {
                // La Home resta sotto agli altri schermi: così posizione e focus si ritrovano al ritorno.
                HomeScreen(vm, active = top is Screen.Home)
                when (top) {
                    is Screen.MovieDetail -> DetailScreens.Movie(vm, top.movie)
                    is Screen.SeriesDetail -> DetailScreens.Series(vm, top.series)
                    is Screen.PlayLive -> LivePlayerScreen(vm, top)
                    is Screen.PlayVod -> VodPlayerScreen(vm, top)
                    is Screen.Help -> com.lumen.app.ui.HelpScreen()
                    else -> {}
                }
            }
        }
    }
}
