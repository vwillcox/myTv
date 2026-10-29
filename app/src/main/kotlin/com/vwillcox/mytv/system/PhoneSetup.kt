package com.vwillcox.mytv.system

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.vwillcox.mytv.App
import com.vwillcox.mytv.data.BackgroundMode
import com.vwillcox.mytv.data.LenientJson
import com.vwillcox.mytv.data.friendly
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * A small web server for doing from a phone or computer what is slow with a remote: pasting a TMDB key, sending an
 * APK to install and choosing a background photo. It runs only while its page in
 * settings is open, and every request must carry the random token in the QR code.
 */
class PhoneSetup(private val app: App, port: Int) : NanoHTTPD(port) {
    val token: String = SecureRandom().let { r -> (1..10).map { "abcdefghjkmnpqrstuvwxyz23456789"[r.nextInt(31)] }.joinToString("") }
    /** What the phone has done, newest last, for the TV to show. */
    val activity = MutableStateFlow(emptyList<String>())

    val address: String? get() = localAddress()?.let { "http://$it:$listeningPort/$token" }

    private fun note(text: String) { activity.value = (activity.value + text).takeLast(6) }

    private fun json(ok: Boolean, message: String) = newFixedLengthResponse(
        if (ok) Response.Status.OK else Response.Status.BAD_REQUEST, "application/json",
        buildJsonObject { put("ok", ok); put("message", message) }.toString(),
    )

    private fun state(): String {
        val c = app.store.value
        return buildJsonObject {
            put("device", android.os.Build.MODEL)
            // Whether a key is held, never the key itself.
            put("tmdb", c.popular.tmdbKey.isNotBlank())
            put("brand", c.look.brand)
        }.toString()
    }

    override fun serve(session: IHTTPSession): Response {
        val parts = session.uri.trim('/').split('/')
        if (parts.firstOrNull() != token) return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Scan the QR code shown on the TV.")
        val action = parts.getOrNull(1) ?: ""
        if (session.method == Method.GET) return when (action) {
            "" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", PhonePage)
            "state" -> newFixedLengthResponse(Response.Status.OK, "application/json", state())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        if (session.method != Method.POST) return json(false, "Unsupported")
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            when (action) {
                // Uploads arrive as multipart forms; NanoHTTPD has already saved the file and gives its path.
                "apk" -> {
                    val tmp = files["file"]?.let(::File) ?: return json(false, "No file came through")
                    val name = session.parameters["file"]?.firstOrNull() ?: "app.apk"
                    runBlocking { app.installer.install(tmp, name) }
                    note("Sent $name to install")
                    json(true, "Sent. Look at the TV: Android asks before installing a new app.")
                }
                "photo" -> {
                    val tmp = files["file"]?.let(::File) ?: return json(false, "No file came through")
                    val dest = File(app.filesDir, "background-${System.currentTimeMillis()}.img")
                    tmp.copyTo(dest, overwrite = true)
                    app.filesDir.listFiles { f -> f.name.startsWith("background-") && f != dest }?.forEach { it.delete() }
                    app.store.update { it.copy(look = it.look.copy(background = BackgroundMode.PHOTO, photo = dest.absolutePath)) }
                    note("Background photo changed")
                    json(true, "The TV's background is now that photo.")
                }
                else -> {
                    val body = LenientJson.parseToJsonElement(files["postData"] ?: "{}").jsonObject
                    fun str(key: String) = body[key]?.jsonPrimitive?.content?.trim().orEmpty()
                    when (action) {
                        "tmdb" -> {
                            val key = str("key")
                            app.store.update { it.copy(popular = it.popular.copy(tmdbKey = key, region = str("region").ifBlank { it.popular.region }.uppercase())) }
                            app.feed.refresh()
                            note(if (key.isEmpty()) "TMDB key removed" else "TMDB key saved")
                            json(true, if (key.isEmpty()) "Removed" else "Saved. Popular titles in your library are being worked out now.")
                        }
                        "apkurl" -> {
                            val url = str("url")
                            runBlocking { app.installer.installFrom(url) }
                            note("Downloaded an app to install")
                            json(true, "Downloaded. Look at the TV to confirm the install.")
                        }
                        "photourl" -> {
                            val url = str("url")
                            app.store.update { it.copy(look = it.look.copy(background = BackgroundMode.PHOTO, photo = url)) }
                            note("Background photo changed")
                            json(true, "Saved")
                        }
                        "brand" -> {
                            app.store.update { it.copy(look = it.look.copy(brand = str("brand"))) }
                            note("Name by the clock changed")
                            json(true, "Saved")
                        }
                        else -> json(false, "Unknown action")
                    }
                }
            }
        } catch (e: Exception) {
            json(false, e.friendly())
        }
    }

    companion object {
        /** Starts on a memorable port when free, otherwise any port. */
        fun start(app: App): PhoneSetup? {
            for (port in listOf(8686, 8687, 0)) {
                val server = PhoneSetup(app, port)
                if (runCatching { server.start(SOCKET_READ_TIMEOUT, false) }.isSuccess) return server
            }
            return null
        }

        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        fun qr(text: String, size: Int = 512): Bitmap {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
            val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}

private val PhonePage = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>myTV setup</title>
<style>
:root{--bg:#101114;--card:#1d1f24;--text:#e8eaed;--dim:#9aa0a6;--accent:#8ab4f8}
@media (prefers-color-scheme: light){:root{--bg:#f4f5f7;--card:#fff;--text:#1f1f1f;--dim:#5f6368;--accent:#1a73e8}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.45 system-ui,sans-serif;padding:16px}
main{max-width:560px;margin:0 auto}h1{font-weight:500;font-size:26px;margin:8px 0 4px}p.lead{color:var(--dim);margin:0 0 20px}
section{background:var(--card);border-radius:16px;padding:16px 16px 18px;margin:0 0 14px}h2{font-size:17px;font-weight:600;margin:0 0 4px}
.hint{color:var(--dim);font-size:14px;margin:0 0 12px}label{display:block;font-size:14px;color:var(--dim);margin:10px 0 4px}
input{width:100%;padding:11px 12px;border-radius:10px;border:1px solid #5f636866;background:transparent;color:var(--text);font:inherit}
button{margin-top:12px;padding:10px 18px;border:0;border-radius:999px;background:var(--accent);color:#0b1d3a;font:inherit;font-weight:600}
.msg{margin-top:10px;font-size:14px;min-height:1em}.ok{color:#81c995}.bad{color:#f28b82}
</style></head><body><main>
<h1>myTV setup</h1><p class="lead" id="device">Sending to your TV</p>

<section><h2>Popular titles (TMDB)</h2><p class="hint" id="tmdbstate">A free key from themoviedb.org (Settings → API) lets the TV find what's popular this week and show the ones you own.</p>
<label>API key or read access token</label><input id="tmdbkey" autocomplete="off"><label>Country (two letters)</label><input id="region" value="GB" maxlength="2">
<button onclick="send('tmdb',{key:v('tmdbkey'),region:v('region')},'tmdbmsg')">Save</button><div class="msg" id="tmdbmsg"></div></section>

<section><h2>Install an app</h2><p class="hint">Send an APK from this phone or computer, or give a download address.</p>
<input id="apk" type="file" accept=".apk,application/vnd.android.package-archive"><button onclick="upload('apk','apk','apkmsg')">Send to TV</button>
<label>…or an address</label><input id="apkurl" placeholder="https://example.com/app.apk"><button onclick="send('apkurl',{url:v('apkurl')},'apkmsg')">Download on TV</button>
<div class="msg" id="apkmsg"></div></section>

<section><h2>Background photo</h2><p class="hint">Shown behind the home screen.</p>
<input id="photo" type="file" accept="image/*"><button onclick="upload('photo','photo','photomsg')">Use this photo</button>
<label>…or a picture's address</label><input id="photourl" placeholder="https://…"><button onclick="send('photourl',{url:v('photourl')},'photomsg')">Use it</button>
<div class="msg" id="photomsg"></div></section>

<section><h2>Name by the clock</h2><input id="brand"><button onclick="send('brand',{brand:v('brand')},'brandmsg')">Save</button><div class="msg" id="brandmsg"></div></section>
</main><script>
const base=location.pathname.replace(/\/$/,'');const v=id=>document.getElementById(id).value;
function show(id,r){const e=document.getElementById(id);e.textContent=r.message;e.className='msg '+(r.ok?'ok':'bad')}
async function send(a,body,id){show(id,{ok:true,message:'Working…'});try{const r=await fetch(base+'/'+a,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});show(id,await r.json());load()}catch(e){show(id,{ok:false,message:String(e)})}}
async function upload(a,input,id){const f=document.getElementById(input).files[0];if(!f){show(id,{ok:false,message:'Choose a file first'});return}
show(id,{ok:true,message:'Sending '+f.name+'…'});const fd=new FormData();fd.append('file',f,f.name);
try{const r=await fetch(base+'/'+a,{method:'POST',body:fd});show(id,await r.json())}catch(e){show(id,{ok:false,message:String(e)})}}
async function load(){const s=await (await fetch(base+'/state')).json();document.getElementById('device').textContent='Sending to '+s.device;
if(s.tmdb)document.getElementById('tmdbstate').textContent='A TMDB key is saved. Paste a new one to replace it, or save an empty box to remove it.';
document.getElementById('brand').value=s.brand}
load();
</script></body></html>
""".trimIndent()
