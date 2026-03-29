package com.me.chat.ai.up.admin

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.ConsoleMessage
import android.util.Log
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.me.chat.ai.up.admin.bridge.AppJsBridge
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var jsBridge: AppJsBridge

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        webView = WebView(this)
        setContentView(webView)

        setupWebView()

        jsBridge = AppJsBridge(this, webView)
        webView.addJavascriptInterface(jsBridge, "Android")

        webView.loadUrl("file:///android_asset/web/index.html")

        // Handle back navigation using the modern OnBackPressedDispatcher API
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // Allow file:// pages to make cross-origin XHR (needed when assets call external APIs)
            allowUniversalAccessFromFileURLs = true
            allowFileAccessFromFileURLs = true
            // Enable mixed content so HTTP APIs work when loaded from file://
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("WebView", "[${message.messageLevel()}] ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {

            /**
             * Intercept all HTTP(S) requests from the WebView.
             *
             * Requests to the configured API endpoints are proxied through
             * OkHttp running on the native side.  This eliminates the CORS
             * restrictions that a WebView loaded from `file://` would normally
             * impose on cross-origin fetches, while keeping full control over
             * request headers and bodies.
             */
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()

                // Only proxy actual HTTP/HTTPS API calls – let asset loads pass through
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return null
                }

                // Only intercept API-style requests (non-GET or JSON content type)
                val method = request.method ?: "GET"
                val isApiRequest = method.uppercase() != "GET" ||
                        request.requestHeaders?.get("Content-Type")?.contains("json") == true

                if (!isApiRequest) return null

                return try {
                    proxyRequest(request)
                } catch (e: Exception) {
                    Log.e("CorsProxy", "Proxy failed for $url", e)
                    null
                }
            }
        }
    }

    /**
     * Proxy a WebResourceRequest through OkHttp and return the response
     * with permissive CORS headers added.
     */
    private fun proxyRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val method = (request.method ?: "GET").uppercase()

        val reqBuilder = Request.Builder().url(url)

        // Forward original headers (except Host which OkHttp manages)
        request.requestHeaders?.forEach { (k, v) ->
            if (!k.equals("Host", ignoreCase = true)) {
                reqBuilder.addHeader(k, v)
            }
        }

        // Build body for non-GET methods
        val body = when (method) {
            "GET", "HEAD" -> null
            else -> {
                // The WebView does not provide the request body directly; the JS
                // side sends it via the Android.proxyPost() bridge method instead.
                // For plain GET intercepts this path is sufficient.
                "".toRequestBody(null)
            }
        }
        reqBuilder.method(method, body)

        val response = httpClient.newCall(reqBuilder.build()).execute()
        val responseBody = response.body?.bytes() ?: ByteArray(0)
        val contentType = response.body?.contentType()?.toString() ?: "application/json"

        val headers = mutableMapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, Authorization, X-Requested-With",
            "Cache-Control" to "no-cache"
        )

        return WebResourceResponse(
            contentType,
            "utf-8",
            response.code,
            response.message.ifEmpty { "OK" },
            headers,
            ByteArrayInputStream(responseBody)
        )
    }

    override fun onDestroy() {
        jsBridge.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
