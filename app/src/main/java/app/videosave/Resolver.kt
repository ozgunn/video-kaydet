package app.videosave

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Paylaşılan gönderi bağlantısını doğrudan video adresine çevirir. */
class Resolver(private val cookie: String?) {

    fun resolve(url: String): String {
        val host = URL(url).host.lowercase()
        return when {
            host == "x.com" || host.endsWith(".x.com") -> twitter(url)
            host == "twitter.com" || host.endsWith(".twitter.com") -> twitter(url)
            host == "instagram.com" || host.endsWith(".instagram.com") -> instagram(url)
            else -> throw Exception("Desteklenmeyen bağlantı: $host")
        }
    }

    /** "t.co" kısaltması paylaşımlarda sık gelir, önce asıl adrese git. */
    fun expand(shortUrl: String): String {
        val conn = URL(shortUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        return try {
            val location = conn.getHeaderField("Location")
            when {
                location == null -> shortUrl
                location.startsWith("http") -> location
                else -> URL(URL(shortUrl), location).toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun twitter(url: String): String {
        val id = TWEET_ID.find(url)?.groupValues?.get(1)
            ?: throw Exception("Tweet bağlantısı çözülemedi.")
        // Syndication endpoint'i imza isteyen bir token bekliyor: id / 1e15 * PI
        val token = String.format("%.0f", id.toLong() / 1e15 * Math.PI)
        val body = get(
            "https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=en&token=$token",
            mapOf("User-Agent" to BROWSER_UA),
        ).second
        val video = JSONObject(body).optJSONObject("video") ?: throw Exception("Bu gönderide video yok.")
        val variants = video.optJSONArray("variants") ?: throw Exception("Bu gönderide video yok.")
        var best = ""
        var bestPixels = -1L
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            if (v.optString("type") != "video/mp4") continue
            val src = v.optString("src")
            val dim = VID_SIZE.find(src)
            val pixels = if (dim != null) dim.groupValues[1].toLong() * dim.groupValues[2].toLong() else 0L
            if (pixels >= bestPixels) { bestPixels = pixels; best = src }
        }
        return best.ifEmpty { throw Exception("Bu gönderide video yok.") }
    }

    /**
     * Instagram'ın `api/v1/media/shortcode/.../info/` ucu artık 404 veriyor; gönderi
     * sayfasını oturumla çekip içindeki video adresini alıyoruz.
     */
    private fun instagram(url: String): String {
        if (STORY.find(url) != null) throw Exception("Story'ler desteklenmiyor.")
        if (cookie.isNullOrBlank()) throw Exception("Instagram için önce uygulamadan giriş yap.")
        val (code, body) = get(
            url,
            mapOf(
                "User-Agent" to BROWSER_UA,
                "Accept" to "text/html,application/xhtml+xml",
                "Accept-Language" to "en-US,en;q=0.9",
                "Cookie" to cookie,
            ),
        )
        for (pattern in listOf(OG_VIDEO, VIDEO_VERSIONS, VIDEO_URL)) {
            val hit = pattern.find(body)?.groupValues?.get(1)
            if (!hit.isNullOrBlank()) return unescape(hit)
        }
        throw Exception(
            "Instagram video adresi vermedi (HTTP $code). Yanıtın başı: " + snippet(body)
        )
    }

    /** "t.co" dışı kısaltmalar ve çerez kontrolü için: oturum gerçekten geçerli mi? */
    fun probe(): String {
        val (code, body) = get(
            "https://www.instagram.com/api/v1/accounts/current_user/?edit=true",
            mapOf(
                "X-IG-App-ID" to IG_APP_ID,
                "User-Agent" to IG_UA,
                "Accept" to "*/*",
                "Cookie" to cookie.orEmpty(),
            ),
        )
        return when {
            code == 200 && body.trimStart().startsWith("{") -> "Oturum açık."
            code == 401 || code == 403 -> "Oturum geçersiz (HTTP $code)."
            else -> "HTTP $code: " + snippet(body)
        }
    }

    private fun unescape(url: String) =
        url.replace("\\/", "/").replace("\\u0026", "&").replace("\\u00253D", "=")

    private fun snippet(body: String) =
        body.take(200).replace(Regex("\\s+"), " ").trim()

    private fun get(url: String, headers: Map<String, String>): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT = 15000
        const val BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"
        const val IG_UA = "Instagram 219.0.0.12.117 Android"
        const val IG_APP_ID = "936619743392459"
        val TWEET_ID = Regex("(?:twitter\\.com|x\\.com)/(?:\\w+/status/|i/status/|\\w+/statuses/)(\\d+)")
        val VID_SIZE = Regex("/vid/(\\d+)x(\\d+)/")
        val STORY = Regex("instagram\\.com/stories/")
        val OG_VIDEO = Regex("""og:video[^>]{0,80}?content="([^"]+)"""")
        val VIDEO_VERSIONS = Regex(""""video_versions"\s*:\s*\[\s*\{[^}]*?"url"\s*:\s*"([^"]+)"""")
        val VIDEO_URL = Regex(""""video_url"\s*:\s*"([^"]+)"""")
    }
}