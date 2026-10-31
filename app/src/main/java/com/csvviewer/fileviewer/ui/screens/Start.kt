@file:OptIn(ExperimentalFoundationApi::class)

package com.csvviewer.fileviewer.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.ui.AdSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppLogo(size: Int = 88) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp)).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.TableChart, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val anim = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(600))
        delay(500)
        onDone()
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.scale(anim.value).alpha(anim.value)) { AppLogo(96) }
        Spacer(Modifier.height(20.dp))
        Text("CSV Reader", style = MaterialTheme.typography.headlineSmall)
        Text("File Viewer & Converter", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class OnbPage(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    OnbPage(Icons.Outlined.TableChart, "Open any CSV in seconds", "View CSV, Excel, JSON, PDF and text files in a clean, fast table with search and sorting."),
    OnbPage(Icons.Outlined.SwapHoriz, "Convert between formats", "Turn CSV into PDF, Excel, JSON or HTML — and Excel or JSON back into CSV."),
    OnbPage(Icons.Outlined.Assessment, "Edit, analyse and chart", "Edit cells, create new CSV files, get column statistics and draw charts from your data."),
)

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            if (state.currentPage < pages.size - 1) TextButton(onClick = onDone) { Text("Skip") }
            else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(state, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(180.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = cs.primary, modifier = Modifier.size(84.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(p.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(
                    Modifier.padding(3.dp).height(8.dp).width(if (i == state.currentPage) 22.dp else 8.dp)
                        .clip(CircleShape).background(if (i == state.currentPage) cs.primary else cs.outline)
                )
            }
        }
        Button(
            onClick = {
                if (state.currentPage < pages.size - 1) scope.launch { state.animateScrollToPage(state.currentPage + 1) } else onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (state.currentPage < pages.size - 1) "Next" else "Get started", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        AdSlot(vm.isPro)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun BigCheck(color: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(96.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.CheckCircle, null, tint = color, modifier = Modifier.size(56.dp))
    }
}
