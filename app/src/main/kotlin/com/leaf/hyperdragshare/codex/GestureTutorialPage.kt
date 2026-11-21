package com.leaf.hyperdragshare.codex

import android.view.ViewConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GestureTutorialPage(settings: DragShareSettings, onBack: () -> Unit) {
    var imageSample by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var received by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(Rect.Zero) }
    var over by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var handleOrigin by remember { mutableStateOf(Offset.Zero) }
    val preventScroll = remember { RequestDisallowInterceptTouchEvent() }
    val slop = ViewConfiguration.get(LocalContext.current).scaledTouchSlop.toFloat()
    val single = settings.resolvePressConfirmationTimeoutMillis()
    val double = settings.resolveTwoFingerPressConfirmationTimeoutMillis()
    val press = remember(imageSample, single, double, slop) {
        TutorialPress(single, double, slop,
            onPreview = { preventScroll(true); preview = true; words = false; received = false },
            onWords = { received = false; selected = emptySet(); words = true },
            onMove = { x, y -> over = target.contains(origin + Offset(x, y)) },
            onFinish = { dropped ->
                received = dropped && over
                preview = false
                over = false
                preventScroll(false)
            })
    }
    DisposableEffect(press) { onDispose { press.stop() } }
    Scaffold(topBar = {
        TopAppBar(title = "操作教程", largeTitle = "操作教程",
            navigationIcon = { BackNavigationIcon(onClick = onBack) })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column {
                    BasicComponent(title = "单指拖拽", summary = "按住文字或图片，看到预览就拖动。未拖就松手会取消。")
                    BasicComponent(title = "双指分词", summary = "两指按到设定时长即打开分词。按在图片上会先识字。")
                }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        SmallTitle("手势练习")
                        TextButton(text = if (imageSample) "换成文字" else "换成图片", onClick = {
                            press.stop()
                            imageSample = !imageSample
                            words = false
                            selected = emptySet()
                            received = false
                        })
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 88.dp)
                        .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                        .onGloballyPositioned { origin = it.positionInRoot() }
                        .pointerInteropFilter(preventScroll, press::touch), contentAlignment = Alignment.Center) {
                        if (imageSample) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(48.dp))
                                Text(if (preview) "图片预览" else "示例图片 · 今天适合散步")
                            }
                        } else {
                            Text(if (preview) "文字预览 · 今天适合散步" else "按住这行示例文字：今天适合散步")
                        }
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .onGloballyPositioned { target = it.boundsInRoot() }
                        .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(if (received && imageSample && !words) "已放入示例图片"
                            else if (received) "已放入示例文字" else if (over) "松手放入" else "拖到这里")
                    }
                    if (words) {
                        if (imageSample) Text("示例图片的文字")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("今天", "适合", "散步").forEach { word ->
                                TextButton(text = if (word in selected) "✓ $word" else word, onClick = {
                                    selected = if (word in selected) selected - word else selected + word
                                })
                            }
                        }
                        if (selected.isNotEmpty()) {
                            Text("拖出所选文字 · " + listOf("今天", "适合", "散步").filter { it in selected }.joinToString(""),
                                modifier = Modifier.fillMaxWidth().onGloballyPositioned { handleOrigin = it.positionInRoot() }
                                    .pointerInput(Unit) {
                                        var point = Offset.Zero
                                        detectDragGestures(onDragStart = { point = handleOrigin + it; received = false },
                                            onDragEnd = { received = over; over = false },
                                            onDragCancel = { over = false }) { change, delta ->
                                            change.consume()
                                            point += delta
                                            over = target.contains(point)
                                        }
                                    }.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}
