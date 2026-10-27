package com.lumen.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

val Bg = Color(0xFF0B0F1A)
val Surface = Color(0xFF151B2B)
val SurfaceSel = Color(0xFF1E2A44)
val FocusBg = Color(0xFF35508A)
val Accent = Color(0xFF3B82F6)
val TextMain = Color(0xFFE6EAF2)
val TextDim = Color(0xFF8A94A8)
val Gold = Color(0xFFFFC857)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Surface,
            onBackground = TextMain, onSurface = TextMain,
        ),
        content = content,
    )
}

/** Elemento base focalizzabile col telecomando: evidenzia il focus, OK = click, Menu/pressione lunga = onLongClick. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    selected: Boolean = false,
    focusScale: Float = 1.03f,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    // lo stato di focus arriva dall'interactionSource del clickable: è la fonte più affidabile
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else if (focused) focusScale else 1f, tween(90), label = "focusScale")
    var m = modifier
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    Box(
        m
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
            .onKeyEvent {
                if (onLongClick != null && it.key == Key.Menu) {
                    if (it.type == KeyEventType.KeyUp) onLongClick()
                    true
                } else {
                    false
                }
            }
            .clip(shape)
            .background(
                when {
                    pressed -> Accent
                    focused -> FocusBg
                    selected -> SurfaceSel
                    else -> Surface
                },
            )
            .border(if (focused) 4.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .focusProperties { canFocus = true }
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) { content(focused) }
}

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    fr: FocusRequester? = null,
    strong: Boolean = false,
) {
    // strong: testo sempre ben leggibile e bordo visibile anche senza focus (dialoghi)
    val base = if (strong) modifier.border(2.dp, Color(0xFF5A6A8C), RoundedCornerShape(10.dp)) else modifier
    TvBox(onClick = onClick, modifier = base, focusRequester = fr, selected = primary || strong) { f ->
        Text(
            text,
            color = if (f || primary || strong) Color.White else TextDim,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/**
 * Campo di testo per telecomando: OK sul riquadro apre la tastiera, Invio/Indietro la chiude.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    var editing by remember { mutableStateOf(false) }
    val innerFr = remember { FocusRequester() }
    val outerFr = focusRequester ?: remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { innerFr.requestFocus() }
            keyboard?.show()
        }
    }

    TvBox(onClick = { editing = true }, modifier = modifier, focusRequester = outerFr) { focused ->
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(label, color = if (focused || editing) Accent else TextDim, fontSize = 13.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TextMain, fontSize = 20.sp),
                cursorBrush = SolidColor(Accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    imeAction = imeAction,
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(onAny = {
                    editing = false
                    keyboard?.hide()
                    runCatching { outerFr.requestFocus() }
                    onImeAction()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(innerFr)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged { if (!it.isFocused && editing) editing = false },
            )
        }
    }
}

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier, ratio: Float = 2f / 3f) {
    val ctx = LocalContext.current
    Box(modifier.aspectRatio(ratio).background(Surface)) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).size(320, 480).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Surface),
                error = ColorPainter(Surface),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = TextDim, fontSize = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

/** I contenitori scorrevoli non devono prendere il focus: lo prendono solo i pulsanti al loro interno. */
fun Modifier.noFocus(): Modifier = this.focusProperties { canFocus = false }


/** Barra di avanzamento dell'aggiornamento: riempimento animato con un riflesso che scorre. */
@Composable
fun UpdateBar(label: String, fraction: Float, modifier: Modifier = Modifier) {
    val shown by androidx.compose.animation.core.animateFloatAsState(
        fraction.coerceIn(0f, 1f), androidx.compose.animation.core.tween(350), label = "upd",
    )
    val sweep by androidx.compose.animation.core.rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "sweep",
    )
    val done = fraction >= 1f
    Column(
        modifier
            .width(380.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color(0xE6101828))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextMain, fontSize = 13.sp, maxLines = 1)
            Text("${(shown * 100).toInt()}%", color = if (done) Gold else TextDim, fontSize = 13.sp)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(androidx.compose.ui.graphics.Color(0x33FFFFFF)),
        ) {
            Box(
                Modifier.fillMaxWidth(shown).fillMaxHeight()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Accent, Violet)))
                    .drawWithContent {
                        drawContent()
                        if (!done) {
                            val w = size.width * 0.35f
                            val x = (size.width + w) * sweep - w
                            drawRect(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color(0x66FFFFFF), androidx.compose.ui.graphics.Color.Transparent),
                                    startX = x, endX = x + w,
                                ),
                            )
                        }
                    },
            )
        }
    }
}
