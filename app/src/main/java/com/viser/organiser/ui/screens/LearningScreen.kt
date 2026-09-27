package com.viser.organiser.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.viser.organiser.Nav
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.Divider
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.OutlineButton
import com.viser.organiser.ui.PrimaryButton
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.timeHm
import com.viser.organiser.watch.Learning
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun LearningScreen(nav: Nav) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    // refresh the live list while learning
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Learning.read(ctx).toByteArray()) } }
                .onSuccess { Toast.makeText(ctx, "Log saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val svc = remember(tick) { Learning.serviceEnabled(ctx) }
    val on = remember(tick) { Learning.isOn(ctx) }
    val until = remember(tick) { Learning.onUntil(ctx) }
    val events = remember(tick) { Learning.eventCount(ctx) }
    val marks = remember(tick) { Learning.markCount(ctx) }
    val recent = remember(tick) {
        Learning.read(ctx).lines().filter { it.isNotBlank() && !it.startsWith("#") }.takeLast(40).reversed()
    }

    Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Ic.ChevronLeft, "Back") { nav.pop() }
            Spacer(Modifier.width(14.dp))
            Text("Learning mode", style = T.serif(30, 600, (-0.01).em))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Records which app and which screen is open while you pay, so the \"Did you just pay…?\" popup can be tuned to your phone. " +
                    "It never records text, amounts or PINs, and turns itself off after 3 hours.",
                style = T.sans(14, 400, color = C.Muted).copy(lineHeight = 20.sp),
            )

            Step(1, "Turn on the Organiser accessibility service", svc,
                if (svc) "On" else "Settings → Accessibility → Organiser → On. If it's greyed out, do the tip below first.") {
                open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            if (!svc && Build.VERSION.SDK_INT >= 33) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF6EDCF)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Greyed out? Android blocks this for apps installed from a file.", style = T.sans(13, 700))
                    Text("Open App info → tap ⋮ (top right) → Allow restricted settings → confirm. Then come back and do step 1.", style = T.sans(13, 400).copy(lineHeight = 19.sp))
                    OutlineButton("Open App info", height = 40.dp) {
                        open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                    }
                }
            }

            if (svc) {
                OutlineButton("Preview the “Did you just pay…?” popup", Modifier.fillMaxWidth(), height = 48.dp) {
                    val s = com.viser.organiser.watch.PayWatchService.instance
                    if (s == null) Toast.makeText(ctx, "Service is starting — try again in a second", Toast.LENGTH_SHORT).show()
                    else s.preview()
                }
            }

            Step(2, "Start learning", on, if (on) "On until ${timeHm(until)}" else "Needs step 1") {}
            if (on) {
                OutlineButton("Stop learning", Modifier.fillMaxWidth(), height = 48.dp) { Learning.stop(ctx); tick++ }
            } else {
                PrimaryButton("Start learning (3 hours)", enabled = svc, height = 52.dp) { Learning.start(ctx); tick++ }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Kicker("3 · Make a few real payments")
                listOf(
                    "Order on Swiggy or Zomato and pay with PhonePe / GPay",
                    "Scan a shop QR with GPay or PhonePe",
                    "Pay by card inside an app (Amazon, Flipkart)",
                    "Start one payment and cancel it at the PIN screen",
                ).forEach { Text("•  $it", style = T.sans(13, 500).copy(lineHeight = 19.sp)) }
                Text("Right after each one, pull down notifications and tap Paid ✓ or Cancelled / failed.", style = T.sans(13, 600).copy(lineHeight = 19.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Mark: Paid ✓", Modifier.weight(1f), height = 40.dp) { Learning.mark(ctx, "paid"); tick++ }
                    OutlineButton("Mark: Cancelled", Modifier.weight(1f), height = 40.dp) { Learning.mark(ctx, "cancelled"); tick++ }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("4 · Send me the log · $events events · $marks marks")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Share log", Modifier.weight(1f), enabled = events > 0, height = 48.dp) { share(ctx) }
                    OutlineButton("Save as file", Modifier.weight(1f), height = 48.dp) {
                        saveLauncher.launch("organiser-learning-${LocalDate.now()}.txt")
                    }
                }
                Text("Clear log", style = T.sans(13, 600, color = C.Late), modifier = Modifier.clickable { Learning.clear(ctx); tick++ }.padding(vertical = 4.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kicker("Latest events")
                if (recent.isEmpty()) Text("Nothing recorded yet.", style = T.sans(13, 400, color = C.Muted))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, C.Line, RoundedCornerShape(14.dp))
                        .horizontalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    recent.forEach { l ->
                        val parts = l.split(" | ")
                        val time = parts.getOrNull(0)?.substringAfter(' ')?.take(8).orEmpty()
                        val kind = parts.getOrNull(1).orEmpty()
                        val app = parts.getOrNull(2).orEmpty()
                        val scr = parts.getOrNull(3).orEmpty().substringAfterLast('.')
                        Text(
                            "$time  ${kind.padEnd(4)}  ${Learning.knownApps[app] ?: app}  $scr",
                            style = T.sans(11, if (kind == "MARK") 700 else 400, color = if (kind == "MARK") C.Good else C.Ink).copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1, overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) C.Good else C.Ink), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else n.toString(), style = T.sans(13, 700, color = Color.White))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.sans(15, 600))
            Text(sub, style = T.sans(12, 400, color = C.Muted).copy(lineHeight = 17.sp))
        }
    }
}

private fun open(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

private fun share(ctx: Context) {
    val f = Learning.logFile(ctx)
    val out = java.io.File(ctx.cacheDir, "shared").apply { mkdirs() }.let { java.io.File(it, "organiser-learning-${LocalDate.now()}.txt") }
    f.copyTo(out, overwrite = true)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "Organiser learning log").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Send learning log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
