package app.videosave

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Kullanım notu + Instagram oturumu. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val status = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString("sessionid", "").orEmpty()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // setContentView ile verilen içerik başlık çubuğunun arkasına yerleşiyor.
            setPadding((24 * d).toInt(), (88 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.howto) + "\n\n" +
                    if (status.isBlank()) getString(R.string.no_session)
                    else getString(R.string.session_ok)
                textSize = 15f
            })
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.login)
                setOnClickListener { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) }
            })
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.test)
                setOnClickListener { test() }
            })
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun test() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val session = prefs.getString("sessionid", "").orEmpty()
        if (session.isBlank()) return toast(getString(R.string.login_first))
        val cookie = "sessionid=$session; csrftoken=${prefs.getString("csrftoken", "").orEmpty()}"
        toast(getString(R.string.testing))
        Thread {
            val result = runCatching { Resolver(cookie).probe() }
                .getOrElse {
                    ErrorLog.write(this, it)
                    it.message ?: getString(R.string.err_generic)
                }
            runOnUiThread { toast(result) }
        }.start()
    }

    private fun toast(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private companion object {
        const val PREFS = "auth"
    }
}