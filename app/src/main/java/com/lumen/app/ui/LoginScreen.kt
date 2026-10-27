package com.lumen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.app.AppViewModel
import androidx.compose.ui.res.stringResource
import com.lumen.app.R

@Composable
fun LoginScreen(vm: AppViewModel) {
    var server by remember { mutableStateOf(vm.server) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val serverFr = remember { FocusRequester() }
    val userFr = remember { FocusRequester() }
    val passFr = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { (if (server.isBlank()) serverFr else userFr).requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Logo(size = 40)
            Column(Modifier.height(8.dp)) {}
            TvTextField(
                value = server,
                onValueChange = { server = it },
                label = stringResource(R.string.login_server),
                imeAction = ImeAction.Next,
                onImeAction = { runCatching { userFr.requestFocus() } },
                focusRequester = serverFr,
                modifier = Modifier.width(520.dp),
            )
            TvTextField(
                value = user,
                onValueChange = { user = it },
                label = stringResource(R.string.login_user),
                imeAction = ImeAction.Next,
                onImeAction = { runCatching { passFr.requestFocus() } },
                focusRequester = userFr,
                modifier = Modifier.width(520.dp),
            )
            TvTextField(
                value = pass,
                onValueChange = { pass = it },
                label = stringResource(R.string.login_pass),
                password = true,
                imeAction = ImeAction.Done,
                onImeAction = { vm.login(server, user, pass) },
                focusRequester = passFr,
                modifier = Modifier.width(520.dp),
            )
            if (vm.busy) {
                CircularProgressIndicator(color = Accent)
            } else {
                Button(stringResource(R.string.login_button), onClick = { vm.login(server, user, pass) }, primary = true, modifier = Modifier.width(520.dp))
            }
            vm.loginError?.let { Text(it, color = Gold, fontSize = 16.sp) }
            Text("Lumen v${com.lumen.app.BuildConfig.VERSION_NAME}", color = TextDim, fontSize = 12.sp)
        }
    }
}
