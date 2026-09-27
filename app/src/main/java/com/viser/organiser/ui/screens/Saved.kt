package com.viser.organiser.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.viser.organiser.Nav
import com.viser.organiser.Screen
import com.viser.organiser.data.Item
import com.viser.organiser.data.ItemType
import com.viser.organiser.data.Section
import com.viser.organiser.ui.BottomNav
import com.viser.organiser.ui.BottomPanel
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.EmptyState
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.LabelPill
import com.viser.organiser.ui.LightField
import com.viser.organiser.ui.OutlineButton
import com.viser.organiser.ui.Tab
import com.viser.organiser.ui.colorOf
import com.viser.organiser.ui.repo
import com.viser.organiser.ui.sections
import com.viser.organiser.ui.state
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import kotlinx.coroutines.launch
import java.util.Locale

private const val ALL = "__all"
private const val PLACES_ONLY = "__places"

@Composable
fun SavedScreen(nav: Nav, focusSearch: Boolean) {
    val r = repo()
    val scope = rememberCoroutineScope()
    val items by remember { r.db.items().savedItems() }.state(emptyList())
    val secs by sections()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ALL) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var addSection by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    val q = query.trim().lowercase(Locale.ROOT)
    val shown = items.filter { i ->
        (filter == ALL || (filter == PLACES_ONLY && i.type == ItemType.PLACE) || i.sectionList.contains(filter)) &&
            (q.isEmpty() || listOf(i.title, i.body, i.url, i.domain, i.sections, i.area, i.status).any { it.lowercase(Locale.ROOT).contains(q) })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Saved", style = T.serif(32, 600, (-0.01).em), modifier = Modifier.weight(1f))
                CircleIconButton(Ic.Map, if (filter == PLACES_ONLY) "Show everything" else "Show places only") {
                    filter = if (filter == PLACES_ONLY) ALL else PLACES_ONLY
                }
                Spacer(Modifier.width(10.dp))
                CircleIconButton(if (grid) Ic.ListView else Ic.Grid, if (grid) "List view" else "Grid view") { grid = !grid }
            }
            LightField(query, { query = it }, "Search titles, links, labels, attributes", leading = Ic.Search, modifier = Modifier.focusRequester(focus))

            if (shown.isEmpty()) {
                EmptyState(
                    if (items.isEmpty()) "Nothing saved yet" else "No matches",
                    if (items.isEmpty()) "Share a link or Maps place into Organiser, or tap +." else "Try another word or section.",
                )
            } else if (grid) {
                LazyVerticalGrid(
                    GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(shown, key = { it.id }) { SavedCard(it, secs, compact = true) { nav.push(Screen.ItemDetail(it.id)) } }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(shown, key = { it.id }) { SavedCard(it, secs, compact = false) { nav.push(Screen.ItemDetail(it.id)) } }
                }
            }
        }

        BottomPanel {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterButton("ALL", filter == ALL, onLongPress = { manage = true }) { filter = ALL } }
                itemsIndexed(secs, key = { _, s -> s.name }) { idx, s ->
                    Box {
                        FilterButton(s.name.uppercase(Locale.ROOT), filter == s.name, onLongPress = { menuFor = s.name }) {
                            filter = if (filter == s.name) ALL else s.name
                        }
                        SectionMenu(
                            expanded = menuFor == s.name, index = idx, count = secs.size,
                            onDismiss = { menuFor = null },
                            onMove = { to -> scope.launch { r.moveSection(s.name, to) }; menuFor = null },
                            onDelete = { confirmDelete = s.name; menuFor = null },
                        )
                    }
                }
            }
            CircleIconButton(Ic.Sliders, "Manage sections") { manage = true }
        }
        BottomNav(Tab.SAVED, onTab = nav::tab, onCapture = { nav.push(Screen.Capture()) })
    }

    if (manage) {
        SectionsDialog(
            secs = secs,
            itemCount = { name -> items.count { name in it.sectionList } },
            onMove = { name, to -> scope.launch { r.moveSection(name, to) } },
            onDelete = { name -> confirmDelete = name },
            onAdd = { addSection = true },
            onDismiss = { manage = false },
        )
    }
    confirmDelete?.let { name ->
        val n = items.count { name in it.sectionList }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = C.Ground,
            title = { Text("Delete “$name”?", style = T.serif(22, 600)) },
            text = {
                Text(
                    if (n == 0) "No saved items use this section." else "$n saved item${if (n == 1) "" else "s"} will keep everything else and just lose this label. Nothing is deleted except the section.",
                    style = T.sans(14, 400, color = C.Muted),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { r.deleteSection(name) }
                    if (filter == name) filter = ALL
                    confirmDelete = null
                }) { Text("Delete section", style = T.sans(14, 700, color = C.Late)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", style = T.sans(14, 600, color = C.Muted)) } },
        )
    }

    if (addSection) {
        NameDialog("New section", "e.g. Books, Gifts, Trips", onDismiss = { addSection = false }) { name ->
            scope.launch { r.addSection(name) }
            addSection = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterButton(label: String, active: Boolean, onLongPress: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) C.Ink else Color.White)
            .border(1.dp, if (active) C.Ink else C.Line, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress, onLongClickLabel = "Move or delete")
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = T.sans(11, 700, 0.06.em, if (active) Color.White else C.Ink))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedCard(i: Item, secs: List<Section>, compact: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            TypeBadge(i.type)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(i.title.ifBlank { i.body.lineSequence().firstOrNull().orEmpty().ifBlank { i.domain } }, style = T.sans(15, 600), maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                val sub = subtitle(i)
                if (sub.isNotBlank()) Text(sub, style = T.sans(13, 400, color = C.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (i.pinned && !compact) Text("PINNED", style = T.sans(10, 700, 0.12.em, C.Muted), modifier = Modifier.padding(top = 3.dp))
        }
        if (i.sectionList.isNotEmpty() || i.status.isNotBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                i.sectionList.forEach { LabelPill(it, secs.colorOf(it)) }
                if (i.status.isNotBlank()) LabelPill(i.status, Color(0xFFB8860B))
            }
        }
        if (i.type == ItemType.PLACE && !compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Open in Maps", Modifier.weight(1f), height = 40.dp) { openMaps(ctx, i, navigate = false) }
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(C.Ink).clickable { openMaps(ctx, i, navigate = true) },
                    contentAlignment = Alignment.Center,
                ) { Text("Navigate", style = T.sans(13, 600, color = Color.White)) }
            }
        }
    }
}

fun subtitle(i: Item): String = when (i.type) {
    // Links show what the owner wrote about them; the domain only when there are no details.
    ItemType.LINK -> i.body.replace('\n', ' ').trim().take(140).ifBlank { i.domain }
    ItemType.PLACE -> listOf(i.area, i.body.lineSequence().firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { if (i.lat != null) "%.4f, %.4f".format(i.lat, i.lng) else "Google Maps place" }
    else -> i.body.replace('\n', ' ').take(120).takeIf { i.title.isNotBlank() }.orEmpty()
}

@Composable
fun TypeBadge(type: String) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.Ink), contentAlignment = Alignment.Center) {
        val icon = when (type) {
            ItemType.PLACE -> Ic.Place
            ItemType.LINK -> Ic.Link
            ItemType.TODO -> Ic.CheckCircle
            else -> Ic.Note
        }
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Hands off to Google Maps (C-5); Maps uses its own connection. */
fun openMaps(ctx: Context, i: Item, navigate: Boolean) {
    val uri = when {
        navigate && i.lat != null && i.lng != null -> Uri.parse("google.navigation:q=${i.lat},${i.lng}")
        navigate -> Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(i.title.ifBlank { i.url }))
        i.url.isNotBlank() -> Uri.parse(i.url)
        i.lat != null && i.lng != null -> Uri.parse("geo:${i.lat},${i.lng}?q=${i.lat},${i.lng}(" + Uri.encode(i.title) + ")")
        else -> Uri.parse("geo:0,0?q=" + Uri.encode(i.title))
    }
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(Intent(intent).setPackage("com.google.android.apps.maps"))
    } catch (e: Exception) {
        try { ctx.startActivity(intent) } catch (e2: Exception) {
            Toast.makeText(ctx, "No maps app found", Toast.LENGTH_SHORT).show()
        }
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open this link", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun NameDialog(title: String, hint: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text(title, style = T.serif(22, 600)) },
        text = { LightField(text, { text = it }, hint) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("Save", style = T.sans(14, 700)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = T.sans(14, 600, color = C.Muted)) } },
    )
}

@Composable
private fun SectionMenu(expanded: Boolean, index: Int, count: Int, onDismiss: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = Color.White) {
        if (index > 0) {
            DropdownMenuItem(text = { Text("Move to front", style = T.sans(14, 500)) }, onClick = { onMove(0) })
            DropdownMenuItem(text = { Text("Move left", style = T.sans(14, 500)) }, onClick = { onMove(index - 1) })
        }
        if (index < count - 1) {
            DropdownMenuItem(text = { Text("Move right", style = T.sans(14, 500)) }, onClick = { onMove(index + 1) })
            DropdownMenuItem(text = { Text("Move to back", style = T.sans(14, 500)) }, onClick = { onMove(count - 1) })
        }
        DropdownMenuItem(text = { Text("Delete section", style = T.sans(14, 600, color = C.Late)) }, onClick = onDelete)
    }
}

/** Reorder (front ↔ back), delete and add sections in one place. */
@Composable
private fun SectionsDialog(
    secs: List<Section>,
    itemCount: (String) -> Int,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text("Sections", style = T.serif(22, 600)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("First in the list = first in the filter bar. Long-press a section in the bar for the same options.", style = T.sans(12, 400, color = C.Muted))
                Spacer(Modifier.height(8.dp))
                secs.forEachIndexed { idx, s ->
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(s.color)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = T.sans(15, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val n = itemCount(s.name)
                            Text("$n item${if (n == 1) "" else "s"}", style = T.sans(11, 400, color = C.Muted))
                        }
                        SmallIcon(Ic.ChevronUp, "Move ${s.name} up", enabled = idx > 0) { onMove(s.name, idx - 1) }
                        SmallIcon(Ic.ChevronDown, "Move ${s.name} down", enabled = idx < secs.size - 1) { onMove(s.name, idx + 1) }
                        SmallIcon(Ic.Trash, "Delete ${s.name}", tint = C.Late) { onDelete(s.name) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", style = T.sans(14, 700)) } },
        dismissButton = { TextButton(onClick = onAdd) { Text("+ New section", style = T.sans(14, 600)) } },
    )
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, tint: Color = C.Ink, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (enabled) tint else C.Sheet, modifier = Modifier.size(18.dp)) }
}
