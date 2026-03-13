package com.mattlabs.websigndisplay

import android.media.MediaPlayer
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout

/**
 * WebChromeClient subclass that enables fullscreen HTML5 video playback in a WebView.
 *
 * When a video requests fullscreen:
 * - [activityNonVideoView] is hidden
 * - The video is added to [activityVideoView] and displayed fullscreen
 *
 * When fullscreen ends (video finishes, user navigates back, or JS fires the ended event):
 * - [activityVideoView] is hidden and cleared
 * - [activityNonVideoView] is shown again
 *
 * Important: [VideoEnabledWebView.setWebChromeClient] must be called before any page load.
 *
 * Original Java implementation by Cristian Perez (http://cpr.name).
 * Converted to Kotlin for WebSignDisplay v2.
 */
class VideoEnabledWebChromeClient(
    /** The view containing all non-video content (typically the WebView). Hidden during fullscreen video. */
    private val activityNonVideoView: View,
    /** The ViewGroup that will host the fullscreen video. Typically fills the whole layout. */
    private val activityVideoView: ViewGroup,
    /** Optional loading indicator shown while video buffers (legacy API <11 only). */
    private val loadingView: View? = null,
    /**
     * The owning VideoEnabledWebView — enables HTML5 video-ended detection.
     * Note: The page should contain only one `<video>` element; the JS injection targets
     * `getElementsByTagName('video')[0]` and will not monitor additional video elements.
     */
    private val webView: VideoEnabledWebView? = null
) : WebChromeClient(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    /** Callback interface for external listeners that need to know when fullscreen toggles. */
    fun interface ToggledFullscreenCallback {
        fun toggledFullscreen(fullscreen: Boolean)
    }

    /** True while a video is displayed in fullscreen using a custom view. */
    var isVideoFullscreen: Boolean = false
        private set

    private var videoViewContainer: FrameLayout? = null
    private var videoViewCallback: CustomViewCallback? = null
    private var toggledFullscreenCallback: ToggledFullscreenCallback? = null

    /** Register a callback to be notified when fullscreen state changes. */
    fun setOnToggledFullscreen(callback: ToggledFullscreenCallback) {
        toggledFullscreenCallback = callback
    }

    /**
     * Called by the WebView when a video requests fullscreen. Hides the sign content view,
     * adds the video container to [activityVideoView], and wires up end-of-video detection
     * via MediaPlayer listeners (legacy) or injected JavaScript (modern).
     */
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (view !is FrameLayout) return

        val focusedChild = view.focusedChild

        isVideoFullscreen = true
        videoViewContainer = view
        videoViewCallback = callback

        // Hide the sign content and show the fullscreen video container
        activityNonVideoView.visibility = View.INVISIBLE
        activityVideoView.addView(
            videoViewContainer,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        activityVideoView.visibility = View.VISIBLE

        if (focusedChild is android.widget.VideoView) {
            // Legacy VideoView path (typically API < 11)
            focusedChild.setOnPreparedListener(this)
            focusedChild.setOnCompletionListener(this)
            focusedChild.setOnErrorListener(this)
        } else if (webView != null && webView.settings.javaScriptEnabled && focusedChild is SurfaceView) {
            // Modern path: inject JS to detect the video ended event and notify the
            // _VideoEnabledWebView JS interface so onHideCustomView() can be called.
            // Note: only works reliably when focusedChild is a SurfaceView, not TextureView.
            val js = buildString {
                append("javascript:")
                append("var _ytrp_html5_video_last;")
                append("var _ytrp_html5_video = document.getElementsByTagName('video')[0];")
                append("if (_ytrp_html5_video != undefined && _ytrp_html5_video != _ytrp_html5_video_last) {")
                append("_ytrp_html5_video_last = _ytrp_html5_video;")
                append("function _ytrp_html5_video_ended() {")
                append("_VideoEnabledWebView.notifyVideoEnd();")  // Must match JS interface name in VideoEnabledWebView
                append("}")
                append("_ytrp_html5_video.addEventListener('ended', _ytrp_html5_video_ended);")
                append("}")
            }
            webView.loadUrl(js)
        }

        toggledFullscreenCallback?.toggledFullscreen(true)
    }

    /**
     * Deprecated three-argument variant (removed in API 18). Delegates directly to
     * [onShowCustomView] with two arguments; the orientation hint is ignored.
     */
    @Deprecated("Deprecated in API 18", ReplaceWith("onShowCustomView(view, callback)"))
    override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) {
        onShowCustomView(view, callback)
    }

    /**
     * Restores the sign content view and tears down the fullscreen video container.
     * May be called manually (e.g. on back press or JS video-ended event) because the
     * system does not always invoke it automatically.
     */
    override fun onHideCustomView() {
        // This method must be manually called on video end in all cases because
        // it is not always called automatically by the system.
        if (!isVideoFullscreen) return

        // Restore the sign content view and tear down the fullscreen video container
        activityVideoView.visibility = View.INVISIBLE
        activityVideoView.removeView(videoViewContainer)
        activityNonVideoView.visibility = View.VISIBLE

        // Call back the CustomViewCallback — skip for Chromium WebView (API 19+)
        // because calling it on Chromium causes a crash.
        videoViewCallback?.takeIf { !it.javaClass.name.contains(".chromium.") }
            ?.onCustomViewHidden()

        isVideoFullscreen = false
        videoViewContainer = null
        videoViewCallback = null

        toggledFullscreenCallback?.toggledFullscreen(false)
    }

    /**
     * Returns the optional loading view to display while a video is buffering.
     * Falls back to the default WebChromeClient behaviour if no loading view was provided.
     */
    override fun getVideoLoadingProgressView(): View? {
        loadingView?.visibility = View.VISIBLE
        return loadingView ?: super.getVideoLoadingProgressView()
    }

    // MediaPlayer callbacks — only invoked for legacy android.widget.VideoView (API < 11)

    /**
     * Called when the legacy VideoView is ready to play. Hides the loading indicator
     * so the video is visible without obstruction.
     */
    override fun onPrepared(mp: MediaPlayer) {
        // Video is ready to play — hide the loading indicator
        loadingView?.visibility = View.GONE
    }

    /**
     * Called when the legacy VideoView finishes playing. Exits fullscreen by delegating
     * to [onHideCustomView] to restore the sign content view.
     */
    override fun onCompletion(mp: MediaPlayer) {
        // Video finished — exit fullscreen
        onHideCustomView()
    }

    /**
     * Called when a playback error occurs on the legacy VideoView. Returns false so that
     * [onCompletion] is subsequently fired, which exits fullscreen cleanly.
     */
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        // Return false so onCompletion() is called, which exits fullscreen cleanly
        return false
    }

    /**
     * Must be called from the Activity's onBackPressed() when video fullscreen is active.
     * @return true if the back press was consumed (fullscreen was active), false otherwise.
     */
    fun onBackPressed(): Boolean {
        return if (isVideoFullscreen) {
            onHideCustomView()
            true
        } else {
            false
        }
    }
}
