package app.videosave

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Toast kaybolduğu için hatanın tam metnini İndirilenler klasörüne yazar. */
object ErrorLog {

    const val FILE = "videokaydet-hata.txt"

    fun write(context: Context, error: Throwable) {
        val text = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            append("  ")
            appendLine(error.javaClass.name)
            appendLine(error.message ?: "(mesaj yok)")
            appendLine("--- stack ---")
            append(android.util.Log.getStackTraceString(error))
        }
        runCatching {
            val resolver = context.contentResolver
            // Aynı isim iki kez durmasın diye önce eski kaydı düşürüyoruz.
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(FILE),
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }
}