package com.viser.organiser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.viser.organiser.Nav
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.Divider
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.LightField
import com.viser.organiser.ui.OutlineButton
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.watch.PayApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PayAppsScreen(nav: Nav) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<PayApps.PayApp>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(version) { apps = withContext(Dispatchers.IO) { PayApps.refresh(ctx) } }

    Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Ic.ChevronLeft, "Back") { nav.pop() }
            Spacer(Modifier.width(14.dp))
            Text("Payment apps", style = T.serif(30, 600, (-0.01).em))
        }
        Column(Modifier.weight(1f).navigationBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Every app on this phone that can pay by UPI, plus bank and wallet apps. Organiser reads their payment notifications " +
                    "and asks \"Did you just pay…?\" after you use them. Shopping and chat apps that also take UPI only have their notifications read.",
                style = T.sans(13, 400, color = C.Muted).copy(lineHeight = 19.sp),
            )
            val list = apps
            if (list == null) {
                Text("Looking for payment apps…", style = T.sans(14, 500, color = C.Muted))
            } else {
                Kicker("${list.count { it.on }} of ${list.size} watched")
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, C.Line, RoundedCornerShape(16.dp)),
                ) {
                    items(list, key = { it.pkg }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { PayApps.setOn(ctx, a.pkg, !a.on); version++ }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(a.label, style = T.sans(15, 600, color = if (a.on) C.Ink else C.Faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    a.kind.label + if (a.dual) " · shopping/chat — notifications only" else " · pop-up + notifications",
                                    style = T.sans(12, 400, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = a.on,
                                onCheckedChange = { PayApps.setOn(ctx, a.pkg, it); version++ },
                                colors = SwitchDefaults.colors(checkedTrackColor = C.Ink, checkedThumbColor = Color.White),
                            )
                        }
                        Divider(C.LineSoft)
                    }
                }
                OutlineButton("+ Add an app it missed", Modifier.fillMaxWidth(), height = 48.dp) { adding = true }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (adding) {
        AddAppDialog(onDismiss = { adding = false }) { pkg ->
            PayApps.add(ctx, pkg); adding = false; version++
        }
    }
}

@Composable
private fun AddAppDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val have = withContext(Dispatchers.IO) { PayApps.all(ctx).map { it.pkg }.toSet() }
        all = withContext(Dispatchers.IO) { PayApps.launchableApps(ctx).filter { it.first !in have } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text("Add a payment app", style = T.serif(22, 600)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LightField(q, { q = it }, "Search apps", leading = Ic.Search)
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    all.filter { q.isBlank() || it.second.contains(q, true) }.forEach { (pkg, label) ->
                        Box(Modifier.fillMaxWidth().clickable { onPick(pkg) }.padding(vertical = 12.dp)) {
                            Text(label, style = T.sans(15, 500))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", style = T.sans(14, 600)) } },
    )
}
