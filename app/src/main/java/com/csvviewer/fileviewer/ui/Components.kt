@file:OptIn(ExperimentalMaterial3Api::class)

package com.csvviewer.fileviewer.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csvviewer.fileviewer.data.FileItem
import com.csvviewer.fileviewer.data.FileKind
import com.csvviewer.fileviewer.data.FileUtil

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

@Composable
fun kindColor(kind: FileKind): Color {
    val t = LocalTints.current
    return when (kind) {
        FileKind.CSV, FileKind.TSV -> t.csv
        FileKind.EXCEL -> t.excel
        FileKind.PDF -> t.pdf
        FileKind.JSON -> t.json
        FileKind.TEXT -> t.text
        FileKind.IMAGE -> t.image
        FileKind.ZIP -> t.zip
        FileKind.OTHER -> t.other
    }
}

fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.CSV, FileKind.TSV -> Icons.Outlined.TableChart
    FileKind.EXCEL -> Icons.Outlined.GridOn
    FileKind.PDF -> Icons.Outlined.PictureAsPdf
    FileKind.JSON -> Icons.Outlined.Code
    FileKind.TEXT -> Icons.Outlined.Description
    FileKind.IMAGE -> Icons.Outlined.Image
    FileKind.ZIP -> Icons.Outlined.Archive
    FileKind.OTHER -> Icons.Outlined.InsertDriveFile
}

@Composable
fun FileIconBox(kind: FileKind, size: Dp = 44.dp) {
    val c = kindColor(kind)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kindIcon(kind), null, tint = c, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Gold "PRO" chip shown in the Home header for free users. */
@Composable
fun ProChip(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Gold1, Gold2)))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("PRO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Small badge placed in the corner of locked tool cards. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Gold1, Gold2)))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(9.dp))
        Spacer(Modifier.width(2.dp))
        Text("PRO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 10.sp)
    }
}

/**
 * Placeholder for an ad unit. No ad SDK is included in this build;
 * swap the body for an AdMob banner / native ad when it is added.
 */
@Composable
fun AdSlot(isPro: Boolean, banner: Boolean = false, modifier: Modifier = Modifier) {
    if (isPro) return
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(if (banner) 50.dp else 96.dp)
            .then(if (banner) Modifier else Modifier.clip(RoundedCornerShape(12.dp)))
            .background(cs.surfaceVariant)
            .border(BorderStroke(1.dp, cs.outline), if (banner) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ad", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant,
                modifier = Modifier.border(1.dp, cs.onSurfaceVariant, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (banner) "Banner ad placement" else "Native ad placement", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun FileRow(
    item: FileItem,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIconBox(item.kind)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, null, tint = Gold1, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                listOf(item.kind.label, FileUtil.size(item.size), FileUtil.time(item.lastOpened)).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
        else if (onFavorite != null || onShare != null || onTrash != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onFavorite != null) DropdownMenuItem(
                        text = { Text(if (item.favorite) "Remove from favorites" else "Add to favorites") },
                        leadingIcon = { Icon(if (item.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                        onClick = { menu = false; onFavorite() },
                    )
                    if (onShare != null) DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; onShare() })
                    if (onInfo != null) DropdownMenuItem(text = { Text("File info") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; onInfo() })
                    if (onTrash != null) DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onTrash() })
                }
            }
        }
    }
}

@Composable
fun ToolTile(icon: ImageVector, title: String, pro: Boolean, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .height(112.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = 26.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (pro) ProBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
fun ToolRow(icon: ImageVector, title: String, subtitle: String, tint: Color, pro: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (pro) ProBadge()
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun LoadingBox(text: String = "Loading…") {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Card16(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
