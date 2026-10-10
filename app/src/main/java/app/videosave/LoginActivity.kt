package app.videosave

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/** Instagram'a burada giriş yapılıyor; çerez WebView'in kendi deposundan okunuyor. */
class LoginActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val jar = CookieManager.getInstance().apply { setAcceptCookie(true) }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("https://www.instagram.com/accounts/login/")
        }
        val done = Button(this).apply {
            text = getString(R.string.login_done)
            setOnClickListener {
                store(jar.getCookie("https://www.instagram.com").orEmpty())
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(done)
        }
        setContentView(column)
    }

    private fun store(cookieHeader: String) {
        val jar = cookieHeader.split(';')
            .mapNotNull {
                val kv = it.trim().split('=', limit = 2)
                if (kv.size == 2) kv[0] to kv[1] else null
            }.toMap()
        val session = jar["sessionid"]
        if (session.isNullOrBlank()) {
            toast(getString(R.string.login_failed))
            return
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("sessionid", session)
            .putString("csrftoken", jar["csrftoken"].orEmpty())
            .apply()
        CookieManager.getInstance().flush()
        toast(getString(R.string.login_ok))
        finish()
    }

    private fun toast(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private companion object {
        const val PREFS = "auth"
    }
}