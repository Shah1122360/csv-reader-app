package com.csvviewer.fileviewer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.ui.*

private val comparison = listOf(
    Triple("View CSV, PDF, Excel, text", "✓", "✓"),
    Triple("CSV → PDF / Excel / JSON, Excel → CSV", "✓", "✓"),
    Triple("Scanner, ZIP, compress, merge", "✓", "✓"),
    Triple("Ads", "Yes", "None"),
    Triple("CSV editor & create CSV", "Create only", "✓"),
    Triple("Analysis & charts", "Preview", "✓"),
    Triple("Batch convert", "—", "✓"),
)

private data class Perk(val icon: ImageVector, val title: String)

private val perks = listOf(
    Perk(Icons.Outlined.Block, "No ads"),
    Perk(Icons.Outlined.Edit, "Full CSV editor"),
    Perk(Icons.Outlined.BarChart, "All charts"),
    Perk(Icons.Outlined.Assessment, "Full analysis"),
    Perk(Icons.Outlined.FileCopy, "Batch convert"),
    Perk(Icons.Outlined.FlashOn, "Future Pro tools"),
)

private data class Plan(val id: String, val name: String, val period: String, val tag: String?)

private val plans = listOf(
    Plan("weekly", "Weekly", "per week", null),
    Plan("monthly", "Monthly", "per month", "Popular"),
    Plan("yearly", "Yearly", "per year", "Best value"),
)

@Composable
fun PremiumScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf("yearly") }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.Close, "Close") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { ctx.toast("Google Play Billing isn't connected in this debug build") }) { Text("Restore purchase") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Gold1, Gold2))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Text("CSV Reader ", style = MaterialTheme.typography.headlineSmall)
                        Text("Premium", style = MaterialTheme.typography.headlineSmall, color = Gold2)
                    }
                    Text("Edit, analyse and convert without limits — and without ads.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
                }
            }
            item {
                Text("Free vs Premium", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))) {
                    Row(Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(12.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text("Free", Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                        Text("Premium", Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = Gold2)
                    }
                    comparison.forEachIndexed { i, (f, free, pro) ->
                        if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                        Row(Modifier.fillMaxWidth().background(cs.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(free, Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = if (free == "✓") cs.primary else cs.onSurfaceVariant)
                            Text(pro, Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = cs.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Text("What you get", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    perks.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { p ->
                                Row(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(p.icon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(p.title, style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Plans and the button stay pinned so they're visible without scrolling
        Column(Modifier.fillMaxWidth().background(cs.background)) {
            HorizontalDivider(color = cs.outline)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plans.forEach { p -> PlanCard(p, selected == p.id, Modifier.weight(1f)) { selected = p.id } }
            }
            Button(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
            ) { Text("Continue with ${plans.first { it.id == selected }.name}", fontWeight = FontWeight.Bold) }
            Text(
                "Cancel anytime in Google Play › Subscriptions", style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Terms of service · Privacy policy", style = MaterialTheme.typography.bodySmall, color = cs.primary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 2.dp),
            )
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Debug build") },
        text = { Text("Google Play Billing isn't connected yet, so no payment is taken. Unlock Premium on this phone for testing?") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                vm.updatePro(true)
                nav.navigate(Routes.PREMIUM_SUCCESS) { popUpTo(Routes.PREMIUM) { inclusive = true } }
            }) { Text("Unlock for testing") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PlanCard(p: Plan, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.padding(top = 8.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) cs.primaryContainer else cs.surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outline, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(top = 16.dp, bottom = 10.dp, start = 6.dp, end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(p.name, style = MaterialTheme.typography.labelMedium)
            // Real price comes from Play Billing ProductDetails; never hard-coded
            Text("Play price", style = MaterialTheme.typography.titleSmall, color = if (selected) cs.primary else cs.onSurface)
            Text(p.period, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (p.tag != null) Text(
            p.tag, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp)
                .clip(RoundedCornerShape(8.dp)).background(if (selected) cs.primary else cs.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun PremiumSuccessScreen(nav: NavController) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BigCheck(Gold2)
        Spacer(Modifier.height(20.dp))
        Text("You're Premium!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Ads are gone and every Pro tool is unlocked.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start using Premium") }
    }
}
