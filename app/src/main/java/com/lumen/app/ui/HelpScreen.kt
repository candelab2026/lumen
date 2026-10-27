package com.lumen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.app.R

private val SECTIONS = listOf(
    R.string.help1_t to R.string.help1_b,
    R.string.help2_t to R.string.help2_b,
    R.string.help3_t to R.string.help3_b,
    R.string.help4_t to R.string.help4_b,
    R.string.help5_t to R.string.help5_b,
    R.string.help6_t to R.string.help6_b,
    R.string.help7_t to R.string.help7_b,
)

/** Guida breve dell'app: ogni sezione è selezionabile, così il telecomando scorre la pagina. */
@Composable
fun HelpScreen() {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 48.dp, vertical = 24.dp)) {
        Text(stringResource(R.string.help_title), color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.help_hint), color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(SECTIONS) { i, (title, body) ->
                TvBox(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().let { if (i == 0) it.focusRequester0(first) else it },
                    focusScale = 1.01f,
                ) { f ->
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(title), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(body), color = if (f) TextMain else TextDim, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

private fun Modifier.focusRequester0(fr: FocusRequester) = this.then(Modifier.focusRequester(fr))
