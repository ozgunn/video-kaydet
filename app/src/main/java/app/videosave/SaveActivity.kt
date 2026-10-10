package app.videosave

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.InputStream
import java.net.URL

class SaveActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kopyalama ana iş parçacığında: arayüzü olmayan bir Activity de ANR'ye takılabilir.
        Thread {
            val message = runCatching { save() }.getOrElse {
                ErrorLog.write(this, it)
                val base = if (it is ClassCastException || it is ClassNotFoundException) {
                    getString(R.string.err_share)
                } else {
                    it.message ?: getString(R.string.err_generic)
                }
                "$base\n\n${getString(R.string.log_hint)}"
            }
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    private fun save(): String {
        val (videos, text) = payloads()
        var saved = 0
        for (uri in videos) {
            if (save(displayName(uri), contentResolver.getType(uri) ?: "video/mp4") {
                    contentResolver.openInputStream(uri)!!
                }) saved++
        }
        if (saved > 0) {
            val failed = videos.size - saved
            return when {
                failed == 0 && saved == 1 -> getString(R.string.saved_one)
                failed == 0 -> getString(R.string.saved_many, saved)
                else -> getString(R.string.saved_partial, saved, failed)
            }
        }

        // Dosya gelmediyse paylaşılan metindeki gönderi bağlantısını çöz.
        val raw = URL_RE.find(text)?.groupValues?.get(0) ?: throw Exception(getString(R.string.err_nothing))
        val resolver = Resolver(cookie())
        val video = resolver.resolve(resolver.expand(raw))
        if (!save("video_${System.currentTimeMillis()}.mp4", "video/mp4") { URL(video).openStream() }) {
            throw Exception(getString(R.string.err_generic))
        }
        return getString(R.string.saved_one)
    }

    private fun save(name: String, mime: String, open: () -> InputStream): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            open().use { input -> contentResolver.openOutputStream(target)!!.use { output -> input.copyTo(output) } }
            // IS_PENDING=1 satırı gizliyor; baytlar indikten sonra kaldır.
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(target, values, null, null)
            true
        } catch (e: Exception) {
            contentResolver.delete(target, null, null) // yarım dosya kalmasın
            false
        }
    }

    /** Video URI'lerini ve metin taşıyanları (gönderi bağlantısı, `.url` dosyası) ayır. */
    private fun payloads(): Pair<List<Uri>, String> {
        val videos = mutableListOf<Uri>()
        val text = StringBuilder()
        for (value in sharedValues()) {
            val uri = value as? Uri
            if (uri == null) {
                when (value) {
                    is CharSequence -> text.append(value).append('\n')
                    is Array<*> -> value.forEach { text.append(it ?: "").append('\n') }
                    is List<*> -> value.forEach { text.append(it ?: "").append('\n') }
                }
                continue
            }
            val type = contentResolver.getType(uri).orEmpty()
            if (type.startsWith("text/") || type.contains("shortcut") ||
                displayName(uri).endsWith(".url", ignoreCase = true)
            ) {
                contentResolver.openInputStream(uri)?.use {
                    text.append(it.readBytes().toString(Charsets.UTF_8)).append('\n')
                }
            } else {
                videos += uri
            }
        }
        return videos to text.toString()
    }

    /**
     * Gönderi adresini bazı sürümler EXTRA_TEXT yerine EXTRA_STREAM'a **metin** olarak
     * koyuyor. `getParcelableExtra(name, Uri::class.java)` bunu gördüğünde
     * "Value ... cannot be converted" ClassCastException fırlatıyor, o yüzden
     * extras'ı tip kontrolü yapmadan okuyup ne geldiğine göre ayrıştırıyoruz.
     */
    private fun sharedValues(): List<Any?> {
        val extras = intent?.extras ?: return emptyList()
        val out = mutableListOf<Any?>()
        out += untyped(extras, Intent.EXTRA_TEXT)
        when (val stream = untyped(extras, Intent.EXTRA_STREAM)) {
            null -> Unit
            is List<*> -> out.addAll(stream)
            is Array<*> -> out.addAll(stream)
            else -> out += stream
        }
        return out
    }

    private fun untyped(extras: android.os.Bundle, key: String): Any? = when {
        Build.VERSION.SDK_INT >= 33 -> extras.get(key)
        else -> runCatching { @Suppress("DEPRECATION") extras.getParcelable<android.os.Parcelable>(key) }.getOrNull()
            ?: runCatching { @Suppress("DEPRECATION") extras.getString(key) }.getOrNull()
    }

    private fun cookie(): String? {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val session = prefs.getString("sessionid", "").orEmpty()
        val csrf = prefs.getString("csrftoken", "").orEmpty()
        return if (session.isBlank()) null else "sessionid=$session; csrftoken=$csrf"
    }

    private fun displayName(uri: Uri): String {
        val name = contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
        return name?.takeIf { it.isNotBlank() } ?: "video_${System.currentTimeMillis()}.mp4"
    }

    private companion object {
        const val PREFS = "auth"
        val URL_RE = Regex("https?://\\S+")
    }
}