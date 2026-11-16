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
    private val startUrl = "https://kartoons.to/home"

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
                if (clickCount % 8 == 0) {
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
        }

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

        webView.loadUrl(startUrl)
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
        val topNativeHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 0; background: #000000; display: flex; justify-content: center; align-items: center; overflow: hidden; }
                </style>
            </head>
            <body>
                <script async="async" data-cfasync="false" src="https://bancadeltempoidea.org/21/0e17b8fb5a802120ada324c402203f33"></script>
                <div id="container-0e17b8fb5a802120ada324c402203f33"></div>
            </body>
            </html>
        """.trimIndent()
        topNativeAdWebView.loadDataWithBaseURL("https://bancadeltempoidea.org", topNativeHtml, "text/html", "UTF-8", null)
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
                <script data-cfasync="false" src="https://bancadeltempoidea.org/14/32991f0b3df4a1990f96fc484219f10e"></script>
                <script data-cfasync="false" src="https://aarems.org/1/f4073d5400ca2987d76c23eecdb2a464"></script>
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
