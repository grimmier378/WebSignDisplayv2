package com.mattlabs.websigndisplay

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * WebView subclass designed for use with [VideoEnabledWebChromeClient].
 *
 * Adds a JavaScript interface (_VideoEnabledWebView) before each page load so
 * HTML5 video end events can be detected and reported back to the chrome client,
 * allowing fullscreen video to exit cleanly when a video finishes playing.
 *
 * Important:
 * - JavaScript must remain enabled (do not call settings.javaScriptEnabled = false).
 * - [setWebChromeClient] must be called before any loadData/loadUrl call.
 *
 * Original Java implementation by Cristian Perez (http://cpr.name).
 * Converted to Kotlin for WebSignDisplay v2.
 */
class VideoEnabledWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : WebView(context, attrs, defStyle) {

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
    }

    /** Tracks whether the JS interface has already been added (only needs to happen once). */
    private var addedJavascriptInterface = false

    /** Reference to the chrome client — used to trigger onHideCustomView() on video end. */
    private var videoEnabledWebChromeClient: VideoEnabledWebChromeClient? = null

    /**
     * JavaScript interface exposed to web pages as window._VideoEnabledWebView.
     * The chrome client injects JS that calls notifyVideoEnd() when a video finishes.
     */
    private inner class JavascriptInterface {
        @android.webkit.JavascriptInterface
        fun notifyVideoEnd() {
            // This runs on a background thread — post to main thread before touching UI
            mainHandler.post {
                videoEnabledWebChromeClient?.onHideCustomView()
            }
        }
    }

    /** Returns true if a video is currently being shown in fullscreen. */
    val isVideoFullscreen: Boolean
        get() = videoEnabledWebChromeClient?.isVideoFullscreen == true

    /**
     * Sets the WebChromeClient. If the client is a [VideoEnabledWebChromeClient],
     * it is stored for fullscreen video coordination.
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun setWebChromeClient(client: WebChromeClient?) {
        settings.javaScriptEnabled = true
        if (client is VideoEnabledWebChromeClient) {
            videoEnabledWebChromeClient = client
        }
        super.setWebChromeClient(client)
    }

    /**
     * Ensures the JS interface is registered before loading inline HTML content,
     * then delegates to the standard WebView implementation.
     */
    override fun loadData(data: String, mimeType: String?, encoding: String?) {
        addJavascriptInterface()
        super.loadData(data, mimeType, encoding)
    }

    /**
     * Ensures the JS interface is registered before loading inline HTML content with a base URL,
     * then delegates to the standard WebView implementation.
     */
    override fun loadDataWithBaseURL(
        baseUrl: String?, data: String, mimeType: String?,
        encoding: String?, historyUrl: String?
    ) {
        addJavascriptInterface()
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    /**
     * Ensures the JS interface is registered before navigating to the given URL,
     * then delegates to the standard WebView implementation.
     */
    override fun loadUrl(url: String) {
        addJavascriptInterface()
        super.loadUrl(url)
    }

    /**
     * Ensures the JS interface is registered before navigating to the given URL with custom
     * HTTP headers, then delegates to the standard WebView implementation.
     */
    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        addJavascriptInterface()
        super.loadUrl(url, additionalHttpHeaders)
    }

    /**
     * Adds the JS interface to the WebView the first time a page is loaded.
     * Must be called before loadUrl/loadData — adding it after load has no effect.
     */
    private fun addJavascriptInterface() {
        if (!addedJavascriptInterface) {
            // Interface name _VideoEnabledWebView must match the name referenced in
            // VideoEnabledWebChromeClient's injected JavaScript.
            addJavascriptInterface(JavascriptInterface(), "_VideoEnabledWebView")
            addedJavascriptInterface = true
        }
    }
}
