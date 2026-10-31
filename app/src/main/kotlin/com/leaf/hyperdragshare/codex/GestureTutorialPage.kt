package com.leaf.hyperdragshare.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GestureTutorialPage(onBack: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var received by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    var selectedWords by remember { mutableStateOf(setOf<String>()) }
    var targetBounds by remember { mutableStateOf(Rect.Zero) }
    var sampleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var overReceiver by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = "操作教程",
                largeTitle = "操作教程",
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = "分词并拖出文字", modifier = Modifier.weight(1f),
                    onClick = {
                        mode = 0; received = false; previewReady = false
                        selectedWords = emptySet(); dragOffset = Offset.Zero
                    })
                TextButton(text = "拖出图片", modifier = Modifier.weight(1f),
                    onClick = {
                        mode = 1; received = false; previewReady = false
                        selectedWords = emptySet(); dragOffset = Offset.Zero
                    })
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallTitle(if (mode == 0) "分词并拖出文字" else "拖出图片")
                    BasicComponent(
                        title = if (mode == 0) "先选词，再拖动预览把手" else "先确认图片预览，再按住移动",
                        summary = if (mode == 0) {
                            "长按后松手会打开分词面板。选中一个或多个词，抓住面板底部的文字预览并移动即可拖出。"
                        } else {
                            "长按图片达到两倍时长后松手，图片预览会保留。再次按住预览并移动即可拖出。"
                        },
                    )
                    BasicComponent(
                        title = "取消拖出",
                        summary = "待拖期间，在预览范围外轻点即可取消；拖动中离开预览不会取消。",
                    )
                }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallTitle("模拟接收区")
                    if (mode == 0 && !previewReady) {
                        Text("① 选择示例词语", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("今天", "适合", "散步").forEach { word ->
                                TextButton(
                                    text = if (word in selectedWords) "✓ $word" else word,
                                    onClick = {
                                        selectedWords = if (word in selectedWords) {
                                            selectedWords - word
                                        } else {
                                            selectedWords + word
                                        }
                                    },
                                )
                            }
                        }
                        TextButton(
                            text = "② 显示待拖预览",
                            enabled = selectedWords.isNotEmpty(),
                            onClick = { previewReady = true },
                        )
                    } else if (mode == 1 && !previewReady) {
                        TextButton(text = "确认图片并显示待拖预览",
                            onClick = { previewReady = true })
                    }
                    if (previewReady) {
                        Text(
                            text = "③ 按住下方预览并移动到接收区；也可模拟轻点预览外取消",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        TextButton(text = "模拟轻点预览外取消", onClick = {
                            previewReady = false
                            dragOffset = Offset.Zero
                            overReceiver = false
                        })
                    }
                    Text(
                        text = when {
                            received -> "示例已放入此处"
                            overReceiver -> "松手放入示例"
                            previewReady -> "把下面的待拖预览拖到这里"
                            else -> "先完成上方步骤"
                        },
                        modifier = Modifier.fillMaxWidth().height(92.dp)
                            .background(MiuixTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(12.dp)).onGloballyPositioned {
                                targetBounds = it.boundsInRoot()
                            }.padding(12.dp),
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (mode == 0) {
                            if (previewReady) selectedWords.sorted().joinToString(" ")
                                else "示例文字：今天适合去公园散步"
                        } else "示例图片：风景预览",
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .background(MiuixTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(12.dp))
                            .onGloballyPositioned { sampleCoordinates = it }
                            .graphicsLayer {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                            }
                            .pointerInput(mode, targetBounds, sampleCoordinates, previewReady) {
                                if (!previewReady) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { start ->
                                        received = false
                                        dragOffset = Offset.Zero
                                        dragOrigin = sampleCoordinates?.localToRoot(start) ?: Offset.Zero
                                    },
                                    onDragEnd = {
                                        received = overReceiver
                                        if (overReceiver) previewReady = false
                                        dragOffset = Offset.Zero
                                        overReceiver = false
                                    },
                                    onDragCancel = {
                                        dragOffset = Offset.Zero
                                        overReceiver = false
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val pointer = dragOrigin + dragOffset
                                    overReceiver = targetBounds.contains(pointer)
                                }
                            }
                            .padding(12.dp),
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (received) {
                        TextButton(text = "重置演示", onClick = { received = false })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
