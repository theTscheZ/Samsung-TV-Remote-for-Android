package com.example.samsungremote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import android.net.wifi.WifiManager
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.Socket
import java.net.InetSocketAddress
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class TvApp(val name: String, val id: String)

class TvClient(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onApps: (List<TvApp>) -> Unit
) {
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) { prefs.edit().putString("host", value).apply() }
    @Volatile private var socket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile var connecting = false
        private set
    @Volatile var lastConnectAttempt = 0L
        private set
    @Volatile var connected = false
        private set

    // Samsung uses its own self-signed TLS certificate. Restrict connections to private IPv4.
    private val localClient: OkHttpClient by lazy {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        }
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trust), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { candidate, _ -> candidate == host }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized fun reconnect() { if (host.isNotBlank()) connect(host, force = true) }

    @Synchronized fun connect(address: String, force: Boolean = false) {
        val cleaned = address.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':')
        if (!isPrivateIp(cleaned)) { onStatus("Nur private IPv4-Adressen erlaubt"); return }
        if (!force && cleaned == host && (connected || connecting && System.currentTimeMillis() - lastConnectAttempt < 15_000)) return
        connecting = true
        lastConnectAttempt = System.currentTimeMillis()
        socket?.close(1000, "Reconnect")
        connected = false
        host = cleaned
        onStatus("Verbinde … am TV bestätigen")
        val name = Base64.encodeToString("Android TV Remote".toByteArray(), Base64.NO_WRAP)
        val token = prefs.getString("token_$cleaned", "").orEmpty()
        val url = "wss://$cleaned:8002/api/v2/channels/samsung.remote.control?name=$name" +
            if (token.isNotBlank()) "&token=$token" else ""
        socket = localClient.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket === socket) mainHandler.post { onStatus("Warte auf TV-Freigabe") }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    when (obj.optString("event")) {
                        "ms.channel.connect" -> {
                            val newToken = obj.optJSONObject("data")?.optString("token").orEmpty()
                            if (newToken.isNotBlank()) prefs.edit().putString("token_$cleaned", newToken).apply()
                            if (webSocket !== socket) return
                            connecting = false
                            connected = true
                            mainHandler.post { onStatus("Verbunden") }
                            requestApps()
                        }
                        "ed.installedApp.get" -> {
                            val data = obj.opt("data")
                            val array = when (data) {
                                is JSONArray -> data
                                is JSONObject -> data.optJSONArray("data") ?: data.optJSONArray("apps") ?: JSONArray()
                                else -> JSONArray()
                            }
                            val list = (0 until array.length()).mapNotNull { index ->
                                val app = array.optJSONObject(index) ?: return@mapNotNull null
                                val id = app.optString("appId", app.optString("id"))
                                if (id.isBlank()) null else {
                                    val icon = app.optString("icon")
                                    if (icon.isNotBlank()) saveAppIcon(context, id, icon)
                                    TvApp(app.optString("name", id), id)
                                }
                            }
                            mainHandler.post { if (webSocket === socket) onApps(list.distinctBy { it.id }.sortedBy { it.name.lowercase() }) }
                        }
                    }
                } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== socket) return
                connecting = false
                connected = false
                mainHandler.post { onStatus("Verbindung fehlgeschlagen (${cleaned}:8002): ${t.message ?: "Unbekannter Fehler"}") }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== socket) return
                connecting = false
                connected = false
                mainHandler.post { onStatus("Nicht verbunden") }
            }
        })
    }

    private fun send(value: JSONObject): Boolean =
        if (connected) socket?.send(value.toString()) ?: false else false

    fun key(code: String): Boolean = send(JSONObject().put("method", "ms.remote.control")
        .put("params", JSONObject().put("Cmd", "Click").put("DataOfCmd", code)
            .put("Option", "false").put("TypeOfRemote", "SendRemoteKey")))

    fun requestApps(): Boolean = send(JSONObject().put("method", "ms.channel.emit")
        .put("params", JSONObject().put("event", "ed.installedApp.get").put("to", "host")))

    fun launch(appId: String): Boolean = send(JSONObject().put("method", "ms.channel.emit")
        .put("params", JSONObject().put("event", "ed.apps.launch").put("to", "host")
            .put("data", JSONObject().put("action_type", "NATIVE_LAUNCH")
                .put("appId", appId).put("metaTag", ""))))

    fun type(value: String): Boolean = send(JSONObject().put("method", "ms.remote.control")
        .put("params", JSONObject()
            .put("Cmd", Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP))
            .put("DataOfCmd", "base64")
            .put("TypeOfRemote", "SendInputString")))

    @Synchronized fun disconnect() {
        val previous = socket
        socket = null
        connecting = false
        connected = false
        previous?.close(1000, "Exit")
    }

    fun getMac(): String = prefs.getString("mac_$host", "").orEmpty()
    fun setMac(mac: String) { prefs.edit().putString("mac_$host", mac).apply() }

    suspend fun wake(): Boolean = withContext(Dispatchers.IO) {
        val mac = getMac().replace(":", "").replace("-", "")
        if (!mac.matches(Regex("[0-9a-fA-F]{12}"))) return@withContext false
        try {
            val bytes = mac.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val magic = ByteArray(102) { index ->
                if (index < 6) 0xff.toByte() else bytes[(index - 6) % 6]
            }
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val packet = DatagramPacket(magic, magic.size,
                    InetAddress.getByName("255.255.255.255"), 9)
                repeat(3) { socket.send(packet) }
            }
            true
        } catch (_: Exception) { false }
    }

    /**
     * Use SSDP first, then a bounded local /24 TCP probe on port 8002.
     * SSDP is frequently filtered by routers or ignored by sleeping TVs.
     * The scan runs only when the user explicitly presses Search.
     */
    suspend fun discover(): List<String> = withContext(Dispatchers.IO) {
        val found = mutableSetOf<String>()
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("tv-discovery")
        lock.setReferenceCounted(false)
        try {
            lock.acquire()
            DatagramSocket().use { probe ->
                probe.soTimeout = 500
                val search = ("M-SEARCH * HTTP/1.1\\r\\nHOST: 239.255.255.250:1900\\r\\n" +
                    "MAN: \\"ssdp:discover\\"\\r\\nMX: 2\\r\\nST: ssdp:all\\r\\n\\r\\n")
                    .toByteArray()
                probe.send(DatagramPacket(search, search.size,
                    InetAddress.getByName("239.255.255.250"), 1900))
                val until = System.currentTimeMillis() + 2400
                while (System.currentTimeMillis() < until) {
                    val buffer = ByteArray(4096)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        probe.receive(packet)
                        val body = String(buffer, 0, packet.length)
                        if (body.contains("Samsung", true) ||
                            body.contains("samsung.com", true)) {
                            packet.address.hostAddress?.takeIf(::isPrivateIp)?.let(found::add)
                        }
                    } catch (_: java.net.SocketTimeoutException) {}
                }
            }
        } catch (_: Exception) {
            // The subnet probe below still runs when multicast is unavailable.
        } finally {
            if (lock.isHeld) lock.release()
        }

        val current = wifiIpv4Address()
        if (current != null && isPrivateIp(current)) {
            val prefix = current.substringBeforeLast('.') + "."
            val semaphore = Semaphore(32)
            val candidates = coroutineScope {
                (1..254).map { last ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val address = prefix + last
                            if (address == current) return@withPermit null
                            try {
                                Socket().use { sock ->
                                    sock.connect(InetSocketAddress(address, 8002), 230)
                                    address
                                }
                            } catch (_: Exception) { null }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            found.addAll(candidates)
        }
        found.sorted()
    }

    private fun wifiIpv4Address(): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (network in cm.allNetworks) {
                val capabilities = cm.getNetworkCapabilities(network) ?: continue
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val properties = cm.getLinkProperties(network) ?: continue
                val address = properties.linkAddresses.map { it.address }
                    .firstOrNull { it is java.net.Inet4Address }?.hostAddress
                if (address != null) return address
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isPrivateIp(address: String): Boolean {
        val numbers = address.split('.').mapNotNull(String::toIntOrNull)
        return numbers.size == 4 && numbers.all { it in 0..255 } &&
            (numbers[0] == 10 || numbers[0] == 192 && numbers[1] == 168 ||
                numbers[0] == 172 && numbers[1] in 16..31)
    }
}

/**
 * Samsung's installed-app response can include the image as a base64-encoded icon.
 * Keep images in internal storage, rather than putting large blobs in preferences.
 * A missing/unsupported icon is intentionally handled by the UI fallback.
 */
internal fun appIconFile(context: Context, appId: String): File {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(appId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return File(File(context.filesDir, "app-icons"), "$digest.png")
}

private fun saveAppIcon(context: Context, appId: String, encodedIcon: String) {
    try {
        val value = encodedIcon.substringAfter("base64,", encodedIcon)
        if (value.length > 2_800_000) return
        val bytes = Base64.decode(value, Base64.DEFAULT)
        if (bytes.isEmpty() || bytes.size > 2_000_000) return
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth !in 1..2048 || options.outHeight !in 1..2048) return
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val destination = appIconFile(context, appId)
        destination.parentFile?.mkdirs()
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    } catch (_: Exception) { /* Preserve shortcut even if icon data is unavailable */ }
}
