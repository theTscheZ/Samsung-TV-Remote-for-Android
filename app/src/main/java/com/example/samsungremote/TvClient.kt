package com.example.samsungremote

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
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
    private var socket: WebSocket? = null
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

    fun connect(address: String) {
        val cleaned = address.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':')
        if (!isPrivateIp(cleaned)) { onStatus("Nur private IPv4-Adressen erlaubt"); return }
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
                onStatus("Warte auf TV-Freigabe")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    when (obj.optString("event")) {
                        "ms.channel.connect" -> {
                            val newToken = obj.optJSONObject("data")?.optString("token").orEmpty()
                            if (newToken.isNotBlank()) prefs.edit().putString("token_$cleaned", newToken).apply()
                            connected = true
                            onStatus("Verbunden")
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
                                if (id.isBlank()) null else TvApp(app.optString("name", id), id)
                            }
                            onApps(list.distinctBy { it.id }.sortedBy { it.name.lowercase() })
                        }
                    }
                } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                onStatus("Verbindung fehlgeschlagen: ${t.message ?: "Unbekannter Fehler"}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                onStatus("Nicht verbunden")
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

    fun disconnect() { socket?.close(1000, "Exit"); socket = null; connected = false }

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

    suspend fun discover(): List<String> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("tv-discovery")
        lock.setReferenceCounted(false)
        lock.acquire()
        try {
            val found = mutableSetOf<String>()
            DatagramSocket().use { socket ->
                socket.soTimeout = 800
                val search = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n").toByteArray()
                socket.send(DatagramPacket(search, search.size,
                    InetAddress.getByName("239.255.255.250"), 1900))
                val until = System.currentTimeMillis() + 3200
                while (System.currentTimeMillis() < until) {
                    val buffer = ByteArray(4096)
                    val reply = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(reply)
                        val body = String(buffer, 0, reply.length)
                        if (body.contains("Samsung", true) || body.contains("TV", true)) {
                            reply.address.hostAddress?.takeIf(::isPrivateIp)?.let(found::add)
                        }
                    } catch (_: java.net.SocketTimeoutException) { }
                }
            }
            found.sorted()
        } catch (_: Exception) { emptyList() }
        finally { if (lock.isHeld) lock.release() }
    }

    private fun isPrivateIp(address: String): Boolean {
        val numbers = address.split('.').mapNotNull(String::toIntOrNull)
        return numbers.size == 4 && numbers.all { it in 0..255 } &&
            (numbers[0] == 10 || numbers[0] == 192 && numbers[1] == 168 ||
                numbers[0] == 172 && numbers[1] in 16..31)
    }
}
