package com.viser.organiser

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.viser.organiser.data.Repo
import com.viser.organiser.reminders.Notifier
import com.viser.organiser.reminders.ReminderScheduler
import com.viser.organiser.sms.SmsImporter
import com.viser.organiser.ui.Tab
import com.viser.organiser.ui.screens.CaptureScreen
import com.viser.organiser.ui.screens.HomeScreen
import com.viser.organiser.ui.screens.ItemScreen
import com.viser.organiser.ui.screens.MoneyScreen
import com.viser.organiser.ui.screens.ReviewScreen
import com.viser.organiser.ui.screens.SavedScreen
import com.viser.organiser.ui.screens.SettingsScreen
import com.viser.organiser.ui.screens.TasksScreen
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.OrganiserTheme
import com.viser.organiser.util.Shared
import com.viser.organiser.util.classifyShared
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        val repo = Repo.get(this)
        repo.scope.launch {
            repo.seed()
            ReminderScheduler.rescheduleAll(this@App)
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data class Saved(val focusSearch: Boolean = false) : Screen()
    data object Tasks : Screen()
    data object Money : Screen()
    data class Capture(val type: String? = null, val shared: Shared? = null) : Screen()
    data class Review(val startId: String? = null) : Screen()
    data object Settings : Screen()
    data class ItemDetail(val id: String) : Screen()
}

/** Tiny back-stack navigator; tabs replace the stack root. */
class Nav(start: Screen) {
    val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun replace(s: Screen) { stack.clear(); stack.add(s) }
    fun tab(t: Tab) = replace(
        when (t) {
            Tab.HOME -> Screen.Home
            Tab.SAVED -> Screen.Saved()
            Tab.TODOS -> Screen.Tasks
            Tab.MONEY -> Screen.Money
        },
    )
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_ID = "id"
    }

    private val nav = Nav(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            OrganiserTheme {
                AppRoot(nav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-scan the SMS inbox on open, in case the phone killed the receiver (risk mitigation).
        lifecycleScope.launch { SmsImporter.importInbox(this@MainActivity) }
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
            if (text.isNotBlank()) {
                val s = classifyShared(text, subject)
                nav.push(Screen.Capture(type = s.kind, shared = s))
            }
            return
        }
        val id = i.getStringExtra(EXTRA_ID)
        when (i.getStringExtra(EXTRA_ROUTE)) {
            "item" -> if (id != null) { nav.replace(Screen.Tasks); nav.push(Screen.ItemDetail(id)) }
            "review" -> { nav.replace(Screen.Money); nav.push(Screen.Review(id)) }
            "money" -> nav.replace(Screen.Money)
        }
        i.removeExtra(EXTRA_ROUTE)
    }
}

@Composable
fun AppRoot(nav: Nav) {
    BackHandler(enabled = nav.stack.size > 1 || nav.current != Screen.Home) {
        if (!nav.pop()) nav.replace(Screen.Home)
    }
    val bg = when (nav.current) {
        is Screen.Capture -> C.Nav
        else -> C.Ground
    }
    Box(Modifier.fillMaxSize().background(bg)) {
        when (val s = nav.current) {
            Screen.Home -> HomeScreen(nav)
            is Screen.Saved -> SavedScreen(nav, s.focusSearch)
            Screen.Tasks -> TasksScreen(nav)
            Screen.Money -> MoneyScreen(nav)
            is Screen.Capture -> CaptureScreen(nav, s.type, s.shared)
            is Screen.Review -> ReviewScreen(nav, s.startId)
            Screen.Settings -> SettingsScreen(nav)
            is Screen.ItemDetail -> ItemScreen(nav, s.id)
        }
    }
}
