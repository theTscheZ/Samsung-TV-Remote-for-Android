package com.example.samsungremote

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val GAP = 8.dp
private val PAGE = 20.dp
private val SHAPE = RoundedCornerShape(18.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RemoteApp() }
    }
}

@Composable
private fun RemoteApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutine = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("shortcuts", 0) }
    var status by remember { mutableStateOf("Nicht verbunden") }
    var installed by remember { mutableStateOf(emptyList<TvApp>()) }
    val client = remember { TvClient(context, { status = it }, { installed = it }) }
    var ip by remember { mutableStateOf(client.host) }
    var mac by remember { mutableStateOf(client.getMac()) }
    var page by remember { mutableIntStateOf(0) }
    var discovered by remember { mutableStateOf(emptyList<String>()) }
    var scanning by remember { mutableStateOf(false) }
    var addApp by remember { mutableStateOf(false) }
    var sendText by remember { mutableStateOf(false) }
    var newAppName by remember { mutableStateOf("") }
    var newAppId by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(readApps(prefs.getString("apps", "").orEmpty())) }

    fun saveApps(apps: List<TvApp>) {
        pinned = apps
        val json = JSONArray()
        apps.forEach { json.put(JSONObject().put("name", it.name).put("id", it.id)) }
        prefs.edit().putString("apps", json.toString()).apply()
    }
    fun key(value: String) {
        if (!client.key(value)) status = "Bitte TV zuerst verbinden"
    }
    fun launch(app: TvApp) {
        if (!client.launch(app.id)) status = "App konnte nicht gestartet werden"
    }

    DisposableEffect(client) { onDispose { client.disconnect() } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF9FB7FF),
        background = Color(0xFF101116),
        surface = Color(0xFF1D2028)
    )) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(page == 0, { page = 0 },
                        icon = { Icon(Icons.Default.SettingsRemote, null) },
                        label = { Text("Remote") })
                    NavigationBarItem(page == 1, { page = 1 },
                        icon = { Icon(Icons.Default.Apps, null) },
                        label = { Text("Apps") })
                    NavigationBarItem(page == 2, { page = 2 },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Verbindung") })
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = PAGE, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TV Remote", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold)
                            Text(status, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            if (client.host.isNotBlank()) client.connect(client.host) else page = 2
                        }) { Icon(Icons.Default.Refresh, "Verbinden") }
                    }
                }

                when (page) {
                    0 -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                KeyButton("Aus", Modifier.weight(1f)) { key("KEY_POWEROFF") }
                                KeyButton("Ein", Modifier.weight(1f)) {
                                    coroutine.launch {
                                        status = if (client.wake()) "Aufwecksignal gesendet"
                                            else "MAC-Adresse unter Verbindung eintragen"
                                    }
                                }
                            }
                        }
                        item {
                            Panel {
                                Text("Navigation", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(GAP)
                                ) {
                                    KeyButton("↑", Modifier.width(82.dp)) { key("KEY_UP") }
                                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                        KeyButton("←", Modifier.width(82.dp)) { key("KEY_LEFT") }
                                        KeyButton("OK", Modifier.width(82.dp)) { key("KEY_ENTER") }
                                        KeyButton("→", Modifier.width(82.dp)) { key("KEY_RIGHT") }
                                    }
                                    KeyButton("↓", Modifier.width(82.dp)) { key("KEY_DOWN") }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                    KeyButton("Zurück", Modifier.weight(1f)) { key("KEY_RETURN") }
                                    KeyButton("Home", Modifier.weight(1f)) { key("KEY_HOME") }
                                    KeyButton("Quelle", Modifier.weight(1f)) { key("KEY_SOURCE") }
                                }
                            }
                        }
                        item {
                            Panel {
                                Text("Lautstärke & Sender", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                    KeyButton("Vol −", Modifier.weight(1f)) { key("KEY_VOLDOWN") }
                                    KeyButton("Stumm", Modifier.weight(1f)) { key("KEY_MUTE") }
                                    KeyButton("Vol +", Modifier.weight(1f)) { key("KEY_VOLUP") }
                                }
                                Spacer(Modifier.height(GAP))
                                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                    KeyButton("CH −", Modifier.weight(1f)) { key("KEY_CHDOWN") }
                                    KeyButton("CH +", Modifier.weight(1f)) { key("KEY_CHUP") }
                                }
                            }
                        }
                        item {
                            Panel {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Schnellzugriffe", Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = { page = 1 }) { Text("Bearbeiten") }
                                }
                                if (pinned.isEmpty()) {
                                    Text("Tippe auf +, um Apps anzupinnen.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Spacer(Modifier.height(12.dp))
                                    ShortcutGrid(pinned, installed, onLaunch = ::launch)
                                }
                                Spacer(Modifier.height(GAP))
                                OutlinedButton(
                                    onClick = { addApp = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("+ Schnellzugriff hinzufügen") }
                            }
                        }
                        item {
                            Panel {
                                Text("Weitere Tasten", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(GAP))
                                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                    KeyButton("Play", Modifier.weight(1f)) { key("KEY_PLAY") }
                                    KeyButton("Pause", Modifier.weight(1f)) { key("KEY_PAUSE") }
                                    KeyButton("Stop", Modifier.weight(1f)) { key("KEY_STOP") }
                                }
                                Spacer(Modifier.height(GAP))
                                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                    KeyButton("Guide", Modifier.weight(1f)) { key("KEY_GUIDE") }
                                    KeyButton("Menü", Modifier.weight(1f)) { key("KEY_MENU") }
                                    KeyButton("Text", Modifier.weight(1f)) { sendText = true }
                                }
                            }
                        }
                    }

                    1 -> {
                        item { Text("Installierte TV-Apps", style = MaterialTheme.typography.titleLarge) }
                        item {
                            Button(
                                onClick = {
                                    if (!client.requestApps()) status = "Bitte TV zuerst verbinden"
                                }, modifier = Modifier.fillMaxWidth()
                            ) { Text("App-Liste aktualisieren") }
                        }
                        if (installed.isEmpty()) item {
                            Text("Noch keine App-Liste verfügbar. Manche Samsung-Firmwareversionen " +
                                "unterstützen die Abfrage nicht; App-IDs lassen sich auch manuell eintragen.")
                        }
                        items(installed, key = { "installed:${it.id}" }) { app ->
                            Panel {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.name, fontWeight = FontWeight.SemiBold)
                                        Text(app.id, style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { launch(app) }) {
                                        Icon(Icons.Default.PlayArrow, "Öffnen")
                                    }
                                    IconButton(onClick = {
                                        saveApps((pinned + app).distinctBy { it.id })
                                    }) {
                                        Icon(if (pinned.any { it.id == app.id })
                                            Icons.Default.Check else Icons.Default.Add, "Anpinnen")
                                    }
                                }
                            }
                        }
                        item { Text("Meine Schnellzugriffe", style = MaterialTheme.typography.titleLarge) }
                        items(pinned, key = { "pinned:${it.id}" }) { app ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(app.name, Modifier.weight(1f))
                                IconButton(onClick = {
                                    saveApps(pinned.filterNot { it.id == app.id })
                                }) { Icon(Icons.Default.DeleteOutline, "Entfernen") }
                            }
                        }
                        item {
                            OutlinedButton(onClick = { addApp = true },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("+ App-ID manuell hinzufügen")
                            }
                        }
                    }

                    else -> {
                        item { Text("TV verbinden", style = MaterialTheme.typography.titleLarge) }
                        item {
                            Panel {
                                Text("Handy und TV müssen im selben Heimnetz sein.")
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = ip, onValueChange = { ip = it },
                                    label = { Text("IP-Adresse des TVs") },
                                    placeholder = { Text("192.168.1.100") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { client.connect(ip); mac = client.getMac() },
                                    modifier = Modifier.fillMaxWidth().height(54.dp)
                                ) { Text("Verbinden") }
                                Spacer(Modifier.height(GAP))
                                OutlinedButton(
                                    onClick = {
                                        coroutine.launch {
                                            scanning = true
                                            discovered = client.discover()
                                            scanning = false
                                        }
                                    }, enabled = !scanning, modifier = Modifier.fillMaxWidth()
                                ) { Text(if (scanning) "Suche läuft …" else "TV im WLAN suchen") }
                                discovered.forEach { found ->
                                    TextButton(onClick = {
                                        ip = found
                                        client.connect(found)
                                        mac = client.getMac()
                                    }) { Text("Gefunden: $found") }
                                }
                                Text("Beim ersten Verbinden die Freigabe auf dem TV bestätigen.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item {
                            Panel {
                                Text("Einschalten per Netzwerk",
                                    style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(GAP))
                                Text("MAC-Adresse des TVs für Wake-on-LAN. " +
                                    "Unterstützung hängt von Netzwerk und TV-Standby ab.")
                                Spacer(Modifier.height(GAP))
                                OutlinedTextField(
                                    value = mac, onValueChange = { mac = it },
                                    label = { Text("TV-MAC-Adresse") },
                                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(GAP))
                                Button(onClick = {
                                    client.setMac(mac)
                                    status = "MAC-Adresse gespeichert"
                                }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
                            }
                        }
                    }
                }
            }
        }

        if (addApp) {
            AlertDialog(
                onDismissRequest = { addApp = false },
                title = { Text("Schnellzugriff hinzufügen") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                        OutlinedTextField(value = newAppName,
                            onValueChange = { newAppName = it }, label = { Text("Name") })
                        OutlinedTextField(value = newAppId,
                            onValueChange = { newAppId = it }, label = { Text("TV-App-ID") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newAppName.isNotBlank() && newAppId.isNotBlank()) {
                            saveApps((pinned + TvApp(newAppName.trim(), newAppId.trim()))
                                .distinctBy { it.id })
                            newAppName = ""
                            newAppId = ""
                            addApp = false
                        }
                    }) { Text("Hinzufügen") }
                },
                dismissButton = {
                    TextButton(onClick = { addApp = false }) { Text("Abbrechen") }
                }
            )
        }

        if (sendText) {
            AlertDialog(
                onDismissRequest = { sendText = false },
                title = { Text("Text an TV senden") },
                text = {
                    OutlinedTextField(value = typed, onValueChange = { typed = it },
                        label = { Text("Text") })
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (!client.type(typed)) status = "Senden fehlgeschlagen"
                        sendText = false
                    }) { Text("Senden") }
                },
                dismissButton = {
                    TextButton(onClick = { sendText = false }) { Text("Abbrechen") }
                }
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = SHAPE,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun KeyButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        shape = SHAPE
    ) { Text(text, maxLines = 1) }
}

private fun readApps(raw: String): List<TvApp> = try {
    val json = JSONArray(raw)
    (0 until json.length()).mapNotNull { i ->
        json.optJSONObject(i)?.let { TvApp(it.optString("name"), it.optString("id")) }
            ?.takeIf { it.id.isNotBlank() }
    }
} catch (_: Exception) { emptyList() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutGrid(
    apps: List<TvApp>,
    installedApps: List<TvApp>,
    onLaunch: (TvApp) -> Unit
) {
    // FlowRow reflows automatically as the screen width changes.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.forEach { app ->
            ShortcutIcon(app, installedApps, onClick = { onLaunch(app) })
        }
    }
}

@Composable
private fun ShortcutIcon(
    app: TvApp,
    installedApps: List<TvApp>,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val brand = remember(app.name, app.id) { brandFor(app) }
    val tvIconBitmap = remember(app.id, installedApps) {
        try {
            val file = appIconFile(context, app.id)
            if (file.isFile) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            else null
        } catch (_: Exception) { null }
    }
    val fallbackLabel = app.name.trim().ifBlank { "App" }
    // Consistent readable fallback for unknown TV apps (not an anonymous grey initial).
    val fallbackColors = listOf(
        Color(0xFF244B79), Color(0xFF54418F),
        Color(0xFF236A60), Color(0xFF80522B)
    )
    val fallbackBackground = fallbackColors[(app.id.hashCode().toLong().and(0x7FFFFFFF) % fallbackColors.size).toInt()]
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(brand?.background ?: fallbackBackground)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${app.name} öffnen" },
        contentAlignment = Alignment.Center
    ) {
        when {
            brand?.iconRes != null -> {
                Icon(
                    painter = painterResource(brand.iconRes),
                    contentDescription = null,
                    tint = brand.foreground,
                    modifier = Modifier.size(40.dp)
                )
            }
            brand?.wordmark != null -> {
                Text(
                    text = brand.wordmark,
                    color = brand.foreground,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            tvIconBitmap != null -> {
                Image(
                    bitmap = tvIconBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(CircleShape).padding(5.dp)
                )
            }
            else -> {
                Text(
                    text = fallbackLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(5.dp)
                )
            }
        }
    }
}
