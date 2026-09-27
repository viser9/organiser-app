package com.viser.organiser.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.viser.organiser.Nav
import com.viser.organiser.backup.Backup
import com.viser.organiser.reminders.ReminderScheduler
import com.viser.organiser.sms.SmsImporter
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.Divider
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.dateLong
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.launch
import java.time.LocalDate

fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun notificationsOk(ctx: Context) = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
fun smsOk(ctx: Context) = granted(ctx, Manifest.permission.RECEIVE_SMS) && granted(ctx, Manifest.permission.READ_SMS)
fun batteryOk(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) } // bump to re-read permission state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }

    var pwFor by remember { mutableStateOf<String?>(null) } // "export" | "import"
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        tick++
        if (res.values.all { it }) scope.launch {
            val n = SmsImporter.importInbox(ctx)
            Toast.makeText(ctx, "Found $n payment${if (n == 1) "" else "s"} in recent SMS", Toast.LENGTH_SHORT).show()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; pwFor = "export" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; pwFor = "import" }
    }

    val nOk = remember(tick) { notificationsOk(ctx) }
    val sOk = remember(tick) { smsOk(ctx) }
    val aOk = remember(tick) { ReminderScheduler.canExact(ctx) }
    val bOk = remember(tick) { batteryOk(ctx) }
    val last = remember(tick) { Backup.lastBackup(ctx) }

    Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Ic.ChevronLeft, "Back") { nav.pop() }
            Spacer(Modifier.width(14.dp))
            Text("Settings", style = T.serif(32, 600, (-0.01).em))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Group("Set up") {
                PermRow(Ic.Bell, "Notifications", "Reminders and payment prompts", nOk) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Divider()
                PermRow(Ic.Sms, "Read bank SMS", "SBI, HDFC, slice → expenses. Parsed on this phone only.", sOk) {
                    smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                }
                Divider()
                PermRow(Ic.Bell, "Exact reminder times", "Fire on the minute, even in Doze", aOk) {
                    if (Build.VERSION.SDK_INT >= 31) open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                }
                Divider()
                PermRow(Ic.Shield, "Ignore battery optimisation", "Stops the phone from delaying reminders and SMS", bOk) {
                    open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
                }
                Divider()
                ActionRow(Ic.Open, "App settings (autostart, background)", "On Xiaomi, Oppo, Vivo, OnePlus: allow autostart and no background limits") {
                    open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                }
            }

            Group("Payment detection") {
                ActionRow(Ic.Sliders, "Learning mode", if (com.viser.organiser.watch.Learning.isOn(ctx)) "On — recording app and screen changes" else "Record which screens appear while you pay, to tune the \"Did you just pay…?\" popup") {
                    nav.push(com.viser.organiser.Screen.Learning)
                }
            }

            Group("Expenses") {
                ActionRow(Ic.Sms, "Re-scan SMS inbox", "Look through the last 45 days again") {
                    if (!sOk) { Toast.makeText(ctx, "Allow SMS access first", Toast.LENGTH_SHORT).show(); return@ActionRow }
                    scope.launch {
                        SmsImporter.resetScan(ctx)
                        val n = SmsImporter.importInbox(ctx)
                        Toast.makeText(ctx, if (n == 0) "No new payments found" else "Found $n new payment${if (n == 1) "" else "s"} to confirm", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Group("Backup") {
                val stale = last == 0L || System.currentTimeMillis() - last > 7L * 24 * 3600_000
                Text(
                    if (last == 0L) "No backup yet. Everything lives only on this phone — save a backup file to Drive or Documents."
                    else "Last backup ${dateLong(last)}" + if (stale) " — over a week ago" else "",
                    style = T.sans(13, 500, color = if (stale) C.Late else C.Muted),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Divider()
                ActionRow(Ic.Download, "Export backup", "Password-protected file you save anywhere") {
                    exportLauncher.launch("organiser-backup-${LocalDate.now()}.orgbak")
                }
                Divider()
                ActionRow(Ic.Upload, "Restore from backup", "Merges a backup file into this phone") {
                    importLauncher.launch(arrayOf("*/*"))
                }
            }

            Group("Privacy") {
                Text(
                    "Organiser has no internet permission: nothing you save can leave this phone through the app. SMS are read on the phone and only the amount, merchant, bank and reference are kept.",
                    style = T.sans(13, 400, color = C.Muted).copy(lineHeight = 19.sp),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    val which = pwFor
    val uri = pendingUri
    if (which != null && uri != null) {
        PasswordDialog(if (which == "export") "Set a backup password" else "Backup password",
            if (which == "export") "You'll need it to restore. It can't be recovered." else "The password used when the backup was made.",
            onDismiss = { pwFor = null; pendingUri = null }) { pw ->
            pwFor = null; pendingUri = null
            scope.launch {
                try {
                    if (which == "export") {
                        Backup.export(ctx, uri, pw)
                        Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
                    } else {
                        val n = Backup.import(ctx, uri, pw)
                        Toast.makeText(ctx, "Restored $n records", Toast.LENGTH_SHORT).show()
                    }
                    tick++
                } catch (e: Exception) {
                    Toast.makeText(ctx, if (which == "import") "Wrong password or not a backup file" else "Couldn't save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

private fun open(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Kicker(title)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                .border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        ) { content() }
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, sub: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !ok, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = C.Ink, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.sans(15, 600))
            Text(sub, style = T.sans(12, 400, color = C.Muted))
        }
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(if (ok) C.Chip else C.Ink).padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text(if (ok) "ON" else "ALLOW", style = T.sans(11, 700, 0.08.em, if (ok) C.Good else Color.White)) }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = C.Ink, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.sans(15, 600))
            Text(sub, style = T.sans(12, 400, color = C.Muted))
        }
        Icon(Ic.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PasswordDialog(title: String, sub: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text(title, style = T.serif(22, 600)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(sub, style = T.sans(13, 400, color = C.Muted))
                BasicTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true,
                    textStyle = T.sans(15, 400), cursorBrush = SolidColor(C.Ink),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .border(1.dp, C.Line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (pw.length >= 4) onOk(pw) }) { Text("OK", style = T.sans(14, 700)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = T.sans(14, 600, color = C.Muted)) } },
    )
}
