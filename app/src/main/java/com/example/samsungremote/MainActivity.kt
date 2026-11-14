package com.example.samsungremote

import android.os.Bundle
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import android.graphics.BitmapFactory
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    var pendingDelete by remember { mutableStateOf<TvApp?>(null) }

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
                if (page != 0) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(status, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = {
                            if (client.host.isNotBlank()) client.connect(client.host) else page = 2
                        }) { Icon(Icons.Default.Refresh, "Verbinden") }
                    }
                }

                when (page) {
                    0 -> {
                        item {
                            CompactRemote(
                                status = status,
                                onReconnect = {
                                    if (client.host.isNotBlank()) client.connect(client.host)
                                    else page = 2
                                },
                                onPower = {
                                    if (client.connected) {
                                        // Samsung Tizen models do not all support KEY_POWEROFF.
                                        // KEY_POWER is the real remote's toggle key.
                                        if (!client.key("KEY_POWER")) status = "Ausschalten fehlgeschlagen"
                                    } else {
                                        coroutine.launch {
                                            status = if (client.wake()) "Einschaltsignal gesendet"
                                                else "TV-MAC-Adresse unter Verbindung hinterlegen"
                                        }
                                    }
                                },
                                onKey = ::key,
                                onPlayPause = { play ->
                                    if (client.key(if (play) "KEY_PLAY" else "KEY_PAUSE")) true
                                    else { status = "Bitte TV zuerst verbinden"; false }
                                }
                            )
                        }
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = SHAPE,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Schnellzugriffe", Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold)
                                        IconButton(onClick = { addApp = true }) {
                                            Icon(Icons.Default.Add, "Schnellzugriff hinzufügen")
                                        }
                                        TextButton(onClick = { page = 1 }) { Text("Apps") }
                                    }
                                    if (pinned.isNotEmpty()) {
                                        ShortcutGrid(pinned, installed,
                                            onLaunch = ::launch,
                                            onReorder = ::saveApps,
                                            onDelete = { pendingDelete = it })
                                    } else {
                                        Text("Mit + Apps hinzufügen",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
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

        pendingDelete?.let { app ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Schnellzugriff entfernen?") },
                text = { Text("${app.name} aus den Schnellzugriffen entfernen? Die App bleibt auf dem TV installiert.") },
                confirmButton = {
                    TextButton(onClick = {
                        saveApps(pinned.filterNot { it.id == app.id })
                        pendingDelete = null
                    }) { Text("Entfernen") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") }
                }
            )
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
private fun CompactRemote(
    status: String,
    onReconnect: () -> Unit,
    onPower: () -> Unit,
    onKey: (String) -> Unit,
    onPlayPause: (Boolean) -> Boolean
) {
    var paused by remember { mutableStateOf(false) }
    val buttonBackground = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // No large title; keep only the compact connection indicator.
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape)
                .background(if (status == "Verbunden") Color(0xFF45C69B) else Color(0xFFF2AE62)))
            Spacer(Modifier.width(8.dp))
            Text(status, Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onReconnect, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, "Neu verbinden", modifier = Modifier.size(20.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ControlButton(Icons.Default.Input, "Quelle", onClick = { onKey("KEY_SOURCE") })
            ControlButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück",
                onClick = { onKey("KEY_RETURN") })
            ControlButton(Icons.Default.Home, "Home",
                onClick = { onKey("KEY_HOME") })
            Spacer(Modifier.size(52.dp))
            ControlButton(Icons.Default.PowerSettingsNew, "Ein/Aus",
                background = Color(0xFF3D2838),
                tint = Color(0xFFFFAFC3),
                onClick = onPower)
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val padSize = (maxWidth - 134.dp).coerceIn(152.dp, 206.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(
                    Modifier.width(56.dp).height(padSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlButton(Icons.Default.Add, "Lauter", onClick = { onKey("KEY_VOLUP") })
                    ControlButton(Icons.Default.VolumeMute, "Stumm", onClick = { onKey("KEY_MUTE") })
                    ControlButton(Icons.Default.Remove, "Leiser", onClick = { onKey("KEY_VOLDOWN") })
                }
                Box(
                    Modifier.size(padSize).clip(CircleShape)
                        .background(buttonBackground)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { onKey("KEY_UP") },
                        modifier = Modifier.align(Alignment.TopCenter).size(54.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Hoch", modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { onKey("KEY_DOWN") },
                        modifier = Modifier.align(Alignment.BottomCenter).size(54.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Runter", modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { onKey("KEY_LEFT") },
                        modifier = Modifier.align(Alignment.CenterStart).size(54.dp)) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Links", modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { onKey("KEY_RIGHT") },
                        modifier = Modifier.align(Alignment.CenterEnd).size(54.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, "Rechts", modifier = Modifier.size(32.dp))
                    }
                    FilledTonalButton(
                        onClick = { onKey("KEY_ENTER") },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accent.copy(alpha = .26f))
                    ) { Text("OK", fontWeight = FontWeight.Bold) }
                }
                Column(
                    Modifier.width(56.dp).height(padSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlButton(Icons.Default.Add, "Sender hoch",
                        onClick = { onKey("KEY_CHUP") })
                    Icon(Icons.Default.Tv, contentDescription = "Sender",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp))
                    ControlButton(Icons.Default.Remove, "Sender runter",
                        onClick = { onKey("KEY_CHDOWN") })
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilledTonalButton(
                modifier = Modifier.height(50.dp).width(144.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    val nextPlay = paused
                    if (onPlayPause(nextPlay)) paused = !paused
                }
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.Pause, null)
            }
        }
    }
}

@Composable
private fun ControlButton(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).clip(CircleShape).background(background)
    ) {
        Icon(image, contentDescription = description, tint = tint,
            modifier = Modifier.size(26.dp))
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
    onLaunch: (TvApp) -> Unit,
    onReorder: (List<TvApp>) -> Unit,
    onDelete: (TvApp) -> Unit
) {
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartBounds by remember { mutableStateOf<Rect?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    fun finishDrag() {
        val source = draggingId
        val target = dropTarget
        if (source != null && target != null && source != target) {
            val from = apps.indexOfFirst { it.id == source }
            val to = apps.indexOfFirst { it.id == target }
            if (from >= 0 && to >= 0) {
                val reordered = apps.toMutableList()
                val moved = reordered.removeAt(from)
                reordered.add(to, moved)
                onReorder(reordered)
            }
        }
        draggingId = null
        dropTarget = null
        dragStartBounds = null
        dragOffset = Offset.Zero
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.forEach { app ->
            val isDragging = draggingId == app.id
            val isTarget = dropTarget == app.id && draggingId != app.id
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .zIndex(if (isDragging) 2f else 0f)
                    .onGloballyPositioned { bounds[app.id] = it.boundsInRoot() }
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.1f
                            scaleY = 1.1f
                            shadowElevation = 12.dp.toPx()
                        } else if (isTarget) {
                            scaleX = 0.88f
                            scaleY = 0.88f
                        }
                    }
                    .pointerInput(app.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = app.id
                                dragStartBounds = bounds[app.id]
                                dragOffset = Offset.Zero
                                dropTarget = null
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggingId == app.id) {
                                    dragOffset += dragAmount
                                    val center = dragStartBounds?.center?.plus(dragOffset)
                                    if (center != null) {
                                        val newTarget = apps.firstOrNull {
                                            it.id != app.id && bounds[it.id]?.contains(center) == true
                                        }?.id
                                        if (newTarget != null) dropTarget = newTarget
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragOffset.getDistance() < 12.dp.toPx()) onDelete(app)
                                finishDrag()
                            },
                            onDragCancel = { finishDrag() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                ShortcutIcon(app, installedApps, onClick = { if (draggingId == null) onLaunch(app) })
            }
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
                // Image doesn't tint raster logos: preserve original brand colors.
                Image(
                    painter = painterResource(brand.iconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(54.dp)
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
