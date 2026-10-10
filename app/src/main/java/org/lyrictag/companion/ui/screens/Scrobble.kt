package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lyrictag.companion.db.AppDatabase
import org.lyrictag.companion.scrobble.LastFm
import org.lyrictag.companion.scrobble.ListenBrainz
import org.lyrictag.companion.scrobble.ScrobbleManager

class ScrobbleViewModel(private val db: AppDatabase) : ViewModel() {

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    init {
        viewModelScope.launch {
            db.scrobbles().observePending().collect { _pending.value = it }
        }
    }

    fun validateLb(context: android.content.Context, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val ok = ListenBrainz.validate(token.trim())
            if (ok) {
                ScrobbleManager.prefs(context).edit().putString("lb_token", token.trim()).apply()
                _notice.value = "ListenBrainz connected."
            } else {
                _notice.value = "Token rejected — check it and retry."
            }
            _busy.value = false
        }
    }

    fun connectLastFm(
        context: android.content.Context,
        key: String, secret: String, user: String, password: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val sk = LastFm.mobileSession(key.trim(), secret.trim(), user.trim(), password)
            if (sk != null) {
                // Password is deliberately NOT stored — only the session key.
                ScrobbleManager.prefs(context).edit()
                    .putString("lf_key", key.trim())
                    .putString("lf_secret", secret.trim())
                    .putString("lf_user", user.trim())
                    .putString("lf_session", sk)
                    .apply()
                _notice.value = "Last.fm connected as $user."
            } else {
                _notice.value = "Last.fm login failed — check key, user and password."
            }
            _busy.value = false
        }
    }

    fun disconnect(context: android.content.Context) {
        ScrobbleManager.prefs(context).edit()
            .remove("lb_token").remove("lf_key").remove("lf_secret")
            .remove("lf_user").remove("lf_session").apply()
        viewModelScope.launch {
            withContext(Dispatchers.Main) { _notice.value = "Scrobbling accounts removed." }
        }
    }

    fun clearNotice() { _notice.value = null }
}

@Composable
fun ScrobbleScreen(vm: ScrobbleViewModel, enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val pending by vm.pending.collectAsState()
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()

    var lbToken by remember { mutableStateOf("") }
    var lfKey by remember { mutableStateOf("") }
    var lfSecret by remember { mutableStateOf("") }
    var lfUser by remember { mutableStateOf("") }
    var lfPass by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val p = ScrobbleManager.prefs(context)
        if (p.getString("lb_token", "").isNullOrBlank().not()) lbToken = "••••••"
        lfKey = p.getString("lf_key", "").orEmpty()
        lfSecret = if (p.getString("lf_secret", "").isNullOrBlank()) "" else "••••••"
        lfUser = p.getString("lf_user", "").orEmpty()
    }

    notice?.let { n ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.clearNotice() },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.clearNotice() }) { Text("OK") }
            },
            text = { Text(n) },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scrobbling", style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Scrobble Poweramp playback", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
        Text(
            "Uses Poweramp's playback events. A track scrobbles after 30s and half its length " +
                "(or 4 minutes). Offline plays queue with their real timestamps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pending > 0) Text("$pending queued", style = MaterialTheme.typography.bodyMedium)

        Text("Playback tracking", style = MaterialTheme.typography.titleMedium)
        // Re-checked on every resume (user may grant access in Settings).
        var listenerOn by remember { mutableStateOf(false) }
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    listenerOn = androidx.core.app.NotificationManagerCompat
                        .getEnabledListenerPackages(context)
                        .contains(context.packageName)
                }
            }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs) }
        }
        Text(
            if (listenerOn) "Notification access: on — playback is followed directly."
            else "Notification access: off — tracking relies on Poweramp's broadcasts, " +
                "which some builds don't send. Grant it for reliable scrobbling.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!listenerOn) {
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings
                                    .ACTION_NOTIFICATION_LISTENER_SETTINGS
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Throwable) {
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Grant notification access") }
        }

        Text("ListenBrainz", style = MaterialTheme.typography.titleMedium)
        Text(
            "Free, open, no API key needed — paste your user token " +
                "(listenbrainz.org → Settings).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = lbToken, onValueChange = { lbToken = it },
            label = { Text("User token") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.validateLb(context, lbToken) },
            enabled = !busy && lbToken.isNotBlank() && lbToken != "••••••",
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect ListenBrainz") }

        Text("Last.fm", style = MaterialTheme.typography.titleMedium)
        Text(
            "Needs your own free API key (last.fm/api/account/create). " +
                "Your password is used once to log in and never stored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(value = lfKey, onValueChange = { lfKey = it },
            label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = lfSecret, onValueChange = { lfSecret = it },
            label = { Text("Shared secret") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = lfUser, onValueChange = { lfUser = it },
            label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = lfPass, onValueChange = { lfPass = it },
            label = { Text("Password (used once)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { vm.connectLastFm(context, lfKey, lfSecret, lfUser, lfPass); lfPass = "" },
            enabled = !busy && lfKey.isNotBlank() && lfUser.isNotBlank() && lfPass.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect Last.fm") }

        OutlinedButton(onClick = { vm.disconnect(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Remove scrobbling accounts")
        }
    }
}
