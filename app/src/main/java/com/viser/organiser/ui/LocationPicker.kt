package com.viser.organiser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viser.organiser.data.Item
import com.viser.organiser.data.ItemType
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.findUrl
import com.viser.organiser.util.parseLatLng
import com.viser.organiser.util.placeNameFromUrl

/** A to-do's location: a name plus (optionally) a Google Maps link and coordinates. */
data class PickedLocation(val name: String, val url: String, val lat: Double?, val lng: Double?) {
    companion object {
        fun of(i: Item): PickedLocation? =
            if (i.area.isBlank() && i.url.isBlank()) null else PickedLocation(i.area, i.url, i.lat, i.lng)
    }
}

/** Paste a Maps link (or a whole "shared from Maps" text), type a name, or pick one of your saved places. */
@Composable
fun LocationDialog(current: PickedLocation?, onDismiss: () -> Unit, onPick: (PickedLocation?) -> Unit) {
    val r = repo()
    val saved by remember { r.db.items().savedItems() }.state(emptyList())
    val places = saved.filter { it.type == ItemType.PLACE }
    var link by remember { mutableStateOf(current?.url.orEmpty()) }
    var name by remember { mutableStateOf(current?.name.orEmpty()) }

    fun onLink(v: String) {
        val u = findUrl(v)
        if (u != null) {
            link = u
            if (name.isBlank()) {
                val rest = v.replace(u, "").trim().lines().firstOrNull { it.isNotBlank() }?.trim()
                name = rest ?: placeNameFromUrl(u).orEmpty()
            }
        } else link = v
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text("Location", style = T.serif(22, 600)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LightField(link, { onLink(it) }, "Paste a Google Maps link", leading = Ic.Place,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
                LightField(name, { name = it }, "Place name, e.g. HDFC Koramangala")
                val ll = parseLatLng(link)
                if (ll != null) Text("Coordinates found · Navigate will start directions directly", style = T.sans(12, 500, color = C.Good))
                if (places.isNotEmpty()) {
                    Kicker("Or pick a saved place", modifier = Modifier.padding(top = 6.dp))
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                            .border(1.dp, C.Line, RoundedCornerShape(14.dp)).verticalScroll(rememberScrollState()),
                    ) {
                        places.forEachIndexed { idx, p ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onPick(PickedLocation(p.title, p.url, p.lat, p.lng))
                                }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Ic.Place, null, tint = C.Ink, modifier = Modifier.size(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, style = T.sans(14, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (p.area.isNotBlank()) Text(p.area, style = T.sans(12, 400, color = C.Muted), maxLines = 1)
                                }
                            }
                            if (idx < places.size - 1) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) { Divider() }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() || link.isNotBlank()) {
                    val ll = parseLatLng(link)
                    onPick(PickedLocation(name.trim().ifBlank { placeNameFromUrl(link) ?: "Location" }, link.trim(), ll?.first, ll?.second))
                }
            }) { Text("Save", style = T.sans(14, 700)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onPick(null) }) { Text("Remove", style = T.sans(14, 600, color = C.Late)) }
                TextButton(onClick = onDismiss) { Text("Cancel", style = T.sans(14, 600, color = C.Muted)) }
            }
        },
    )
}
