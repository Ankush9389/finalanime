package com.moonflix.adblock

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var topNativeAdWebView: WebView
    private lateinit var nativeAdWebView: WebView

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var pageLoadCount = 0
    private var clickCount = 0

    private val adRefreshHandler = Handler(Looper.getMainLooper())
    private val adRefreshIntervalMs = 45_000L
    private val adRefreshRunnable = object : Runnable {
        override fun run() {
            if (customView == null && !isFinishing && !isDestroyed) {
                topNativeAdWebView.reload()
                nativeAdWebView.reload()
            }
            adRefreshHandler.postDelayed(this, adRefreshIntervalMs)
        }
    }

    private val smartlinkUrls = listOf(
        "https://asiafilm.org/4/ad35604b5b60c239237ee41c3e8f402a",
        "https://directyp.org/4/bae193dc8298ba66cab50b57861444bd"
    )
    private var smartlinkIndex = 0
    private val moviesUrl = "https://moonflix.website/browse"
    private val animeUrl = "https://kartoons.to/home"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        mainContent = findViewById(R.id.mainContent)
        topNativeAdWebView = findViewById(R.id.topNativeAdWebView)
        nativeAdWebView = findViewById(R.id.nativeAdWebView)

        setupTopNativeAd()
        setupBottomAds()
        adRefreshHandler.postDelayed(adRefreshRunnable, adRefreshIntervalMs)

        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                clickCount++
                if (clickCount % 16 == 0) {
                    openSmartlink()
                }
            }
            false
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                if (AdBlocker.isAdUrl(url)) {
                    return AdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString()
                return !AdBlocker.isAllowedNavigation(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                view?.evaluateJavascript(AdBlocker.COSMETIC_ADBLOCK_JS, null)

                pageLoadCount++
                if (pageLoadCount == 2 || pageLoadCount % 4 == 0) {
                    openSmartlink()
                }

                // Inject Social Bar & Popunder into main WebView so they render full-screen and trigger on taps
                val socialAndPopunderJs = """
                    (function() {
                        if (!document.getElementById('adsterra-social-bar')) {
                            var s1 = document.createElement('script');
                            s1.id = 'adsterra-social-bar';
                            s1.setAttribute('data-cfasync', 'false');
                            s1.src = 'https://bancadeltempoidea.org/14/32991f0b3df4a1990f96fc484219f10e';
                            document.body.appendChild(s1);
                        }
                        if (!document.getElementById('adsterra-popunder')) {
                            var s2 = document.createElement('script');
                            s2.id = 'adsterra-popunder';
                            s2.setAttribute('data-cfasync', 'false');
                            s2.src = 'https://aarems.org/1/f4073d5400ca2987d76c23eecdb2a464';
                            document.body.appendChild(s2);
                        }
                    })();
                """.trimIndent()
                view?.evaluateJavascript(socialAndPopunderJs, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback

                fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                fullscreenContainer.visibility = View.VISIBLE
                mainContent.visibility = View.GONE
                progressBar.visibility = View.GONE

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setFullscreen(true)
            }

            override fun onHideCustomView() {
                fullscreenContainer.removeAllViews()
                fullscreenContainer.visibility = View.GONE
                mainContent.visibility = View.VISIBLE

                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                setFullscreen(false)
            }
        }

        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        loadHomePage()
    }

    private fun loadHomePage() {
        val homeHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                    body {
                        background: linear-gradient(160deg, #070b19 0%, #0f172a 60%, #111827 100%);
                        color: #f8fafc;
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        align-items: center;
                        padding: 24px 18px;
                    }
                    .brand {
                        text-align: center;
                        margin-bottom: 28px;
                    }
                    .brand h1 {
                        font-size: 32px;
                        font-weight: 800;
                        letter-spacing: 0.5px;
                    }
                    .brand h1 span.moon { color: #f8f5ec; }
                    .brand h1 span.flix { color: #eab308; }
                    .brand p {
                        color: #94a3b8;
                        font-size: 14px;
                        margin-top: 6px;
                    }
                    .cards {
                        width: 100%;
                        max-width: 400px;
                        display: flex;
                        flex-direction: column;
                        gap: 16px;
                    }
                    .card {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 20px;
                        border-radius: 16px;
                        text-decoration: none;
                        color: #ffffff;
                        border: 1px solid rgba(255,255,255,0.1);
                        box-shadow: 0 10px 25px rgba(0,0,0,0.4);
                        transition: transform 0.15s ease;
                    }
                    .card:active { transform: scale(0.98); }
                    .card-movies {
                        background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
                        border-left: 4px solid #eab308;
                    }
                    .card-anime {
                        background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%);
                        border-left: 4px solid #38bdf8;
                    }
                    .card-info h2 {
                        font-size: 20px;
                        font-weight: 700;
                        margin-bottom: 4px;
                    }
                    .card-info p {
                        font-size: 13px;
                        color: #cbd5e1;
                    }
                    .badge {
                        background: rgba(234, 179, 8, 0.18);
                        color: #facc15;
                        padding: 8px 14px;
                        border-radius: 999px;
                        font-size: 13px;
                        font-weight: 700;
                    }
                    .badge-anime {
                        background: rgba(56, 189, 248, 0.18);
                        color: #38bdf8;
                    }
                </style>
            </head>
            <body>
                <div class="brand">
                    <h1><span class="moon">Moon</span><span class="flix">Flix</span></h1>
                    <p>Choose what you want to watch</p>
                </div>
                <div class="cards">
                    <a class="card card-movies" href="$moviesUrl">
                        <div class="card-info">
                            <h2>🎬 Movies & TV Shows</h2>
                            <p>Stream latest movies &amp; series</p>
                        </div>
                        <span class="badge">Watch</span>
                    </a>
                    <a class="card card-anime" href="$animeUrl">
                        <div class="card-info">
                            <h2>⚡ Anime World</h2>
                            <p>Watch trending anime &amp; episodes</p>
                        </div>
                        <span class="badge badge-anime">Watch</span>
                    </a>
                </div>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://moonflix.website", homeHtml, "text/html", "UTF-8", null)
    }

    private fun setFullscreen(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureAdWebView(adWebView: WebView) {
        adWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(adWebView, true)
        adWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
                return true
            }
        }
    }

    private fun setupTopNativeAd() {
        configureAdWebView(topNativeAdWebView)
        topNativeAdWebView.settings.useWideViewPort = false
        topNativeAdWebView.settings.loadWithOverviewMode = false
        val topNativeHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    html, body { margin: 0; padding: 0; width: 100%; background: #000000; }
                    #container-b45a39ce7653b015bcb56101c755adf1 { width: 100%; }
                </style>
            </head>
            <body>
                <script async="async" data-cfasync="false" src="https://bauval.org/21/b45a39ce7653b015bcb56101c755adf1"></script>
                <div id="container-b45a39ce7653b015bcb56101c755adf1"></div>
            </body>
            </html>
        """.trimIndent()
        topNativeAdWebView.loadDataWithBaseURL("https://bauval.org", topNativeHtml, "text/html", "UTF-8", null)
    }

    private fun setupBottomAds() {
        configureAdWebView(nativeAdWebView)
        val bottomAdHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 0; background: #000000; display: flex; flex-direction: column; justify-content: center; align-items: center; overflow: hidden; }
                </style>
            </head>
            <body>
                <script>
                  atOptions = {
                    'key' : '0b13196d21c4ac29c86fefbd10837490',
                    'format' : 'iframe',
                    'height' : 50,
                    'width' : 320,
                    'params' : {}
                  };
                </script>
                <script src="https://bancadeltempoidea.org/22/0b13196d21c4ac29c86fefbd10837490"></script>
            </body>
            </html>
        """.trimIndent()
        nativeAdWebView.loadDataWithBaseURL("https://bancadeltempoidea.org", bottomAdHtml, "text/html", "UTF-8", null)
    }

    private fun openSmartlink() {
        try {
            val url = smartlinkUrls[smartlinkIndex % smartlinkUrls.size]
            smartlinkIndex++
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        adRefreshHandler.removeCallbacks(adRefreshRunnable)
        super.onDestroy()
    }
}
