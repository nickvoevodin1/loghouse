package com.loghouse.calendar

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_ID = "id"
        // the calendar ships inside the apk, so it opens with no network at all
        private const val PAGE = "file:///android_asset/index.html"
    }

    private lateinit var web: WebView
    private var pendingRoute: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // hand over anything the widget asked for, once the page is alive
                EventStore.takePending(this@MainActivity)
                    .split(",").filter { it.isNotBlank() }
                    .forEach { call("window.__widgetToggle && window.__widgetToggle('$it')") }
                pendingRoute?.let { route(it) }
                pendingRoute = null
                call("window.__widgetPush && window.__widgetPush()")
            }
        }
        web.addJavascriptInterface(Bridge(), "LogHouseWidget")
        web.loadUrl(PAGE)

        pendingRoute = routeOf(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeOf(intent)?.let { route(it) }
    }

    private fun routeOf(intent: Intent?): String? {
        val r = intent?.getStringExtra(EXTRA_ROUTE) ?: return null
        val id = intent.getStringExtra(EXTRA_ID)
        return if (id.isNullOrBlank()) r else "$r=$id"
    }

    private fun route(value: String) = call("window.__widgetRoute && window.__widgetRoute('$value')")

    private fun call(js: String) = web.post { web.evaluateJavascript(js, null) }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    /** What the web app is allowed to ask of the widget. */
    inner class Bridge {
        @JavascriptInterface
        fun setEvents(json: String) {
            EventStore.save(this@MainActivity, json)
            WidgetProvider.refresh(this@MainActivity)
        }

        @JavascriptInterface
        fun setDay(dow: String, date: String) {
            EventStore.saveDay(this@MainActivity, dow, date)
            WidgetProvider.refresh(this@MainActivity)
        }

        @JavascriptInterface
        fun isPresent(): Boolean = true
    }
}
