package com.mattlabs.websigndisplay

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.mattlabs.websigndisplay.databinding.ActivityFullscreenBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Main activity — displays the sign URL fullscreen in a WebView.
 *
 * Responsibilities:
 * - Keeps the screen on and hides all system UI (fullscreen kiosk mode)
 * - Loads the configured sign URL from SharedPreferences on every resume
 * - Monitors network connectivity every 20 seconds and switches cache mode on transition
 * - Optionally reloads the page on a user-configured interval (Auto Reload)
 * - Restarts itself if backgrounded while Aggressive Restart is enabled
 * - Recovers from crashes via RestartExceptionHandler
 *
 * Settings are accessed via:
 * - F2 key (keyboard)
 * - MENU key (Fire TV remote)
 * - Hidden touch button at the bottom of the screen (touchscreen devices)
 */
class FullscreenActivity : AppCompatActivity() {

    // -- Views --

    /** The WebView that displays the sign content. */
    private lateinit var signDisplayView: VideoEnabledWebView

    // -- State --

    /**
     * Navigation guard: true when FullscreenActivity has intentionally opened SettingsActivity.
     * Prevents Aggressive Restart from firing when the user navigates to Settings.
     * Set in showSettings(), cleared at the start of onResume().
     */
    private var menuConfig = false

    /** Current network state — true if the last connectivity check succeeded. */
    private var onlineStatus = true

    // -- Settings (loaded from SharedPreferences on each resume) --
    private var signURL = ""
    private var autoReStartToggle = false
    private var autoReloadToggle = false
    private var autoReloadInterval = 10 // minutes

    // -- Coroutines --

    /**
     * Coroutine scope tied to this Activity's lifecycle.
     * Both background jobs (network checker and auto reload) run inside this scope.
     * Cancelled in onDestroy() to stop all jobs cleanly.
     */
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Handle for the auto-reload coroutine job — cancelled and restarted when settings change. */
    private var autoReloadJob: Job? = null

    // -- Lifecycle --

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on — essential for an unattended sign running 24/7
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyFullscreen()

        // Install crash handler — catches uncaught exceptions and restarts the app
        Thread.setDefaultUncaughtExceptionHandler(RestartExceptionHandler(this))

        val binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        signDisplayView = binding.webview

        // Wire VideoEnabledWebChromeClient with the two views it manages:
        // - binding.webview: hidden when video goes fullscreen
        // - binding.videoFullscreenContainer: shown when video goes fullscreen
        val webChromeClient = VideoEnabledWebChromeClient(
            binding.webview,
            binding.videoFullscreenContainer
        )
        signDisplayView.setWebChromeClient(webChromeClient)
        signDisplayView.setWebViewClient(WebViewClient())

        // Configure WebView settings appropriate for sign display
        signDisplayView.settings.apply {
            javaScriptEnabled = true          // Required for most sign content and video detection
            domStorageEnabled = true          // Required for web apps that use localStorage
            useWideViewPort = true            // Render at desktop width
            loadWithOverviewMode = true       // Fit page width to screen
            allowFileAccess = true            // Allow local file access if needed
            mediaPlaybackRequiresUserGesture = false  // Allow video/audio to autoplay
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        signDisplayView.setInitialScale(90)

        // Hidden touch button — touchscreen users tap the bottom edge to open Settings
        val settingsButton: Button = binding.buttonSettings
        settingsButton.setOnClickListener { showSettings() }

        // Start network checker (runs every 20s for the lifetime of the Activity)
        startNetworkChecker()
    }

    override fun onResume() {
        super.onResume()
        // Clear the navigation guard — we're back in the main display
        menuConfig = false
        // Re-apply keep-screen-on and fullscreen in case they were cleared while paused
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyFullscreen()
        // Reload all settings and refresh the WebView
        loadConfig()
    }

    override fun onPause() {
        super.onPause()
        // Aggressive Restart: if enabled and the user did NOT navigate to Settings,
        // restart this Activity to keep the sign display in the foreground.
        // menuConfig = true means the user intentionally opened Settings, so we skip restart.
        if (autoReStartToggle && !menuConfig) {
            startActivity(
                Intent(this, FullscreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutine jobs to prevent memory leaks
        activityScope.cancel()
    }

    // -- Key Handling --

    /** Opens Settings on F2 (keyboard) or MENU (Fire TV remote). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Open Settings on F2 (keyboard) or MENU (Fire TV remote)
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_MENU -> {
                    showSettings()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // -- Configuration --

    /**
     * Reads all settings from SharedPreferences and loads the sign URL.
     * Called on every onResume() to pick up any changes made in SettingsActivity.
     *
     * If no URL is configured, opens Settings immediately.
     * URLs without a scheme are auto-prefixed with http://.
     */
    private fun loadConfig() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        autoReStartToggle = prefs.getBoolean("autoReStartToggle", false)
        autoReloadToggle = prefs.getBoolean("autoReloadToggle", false)
        // EditTextPreference always stores its value as a String, never Int.
        // toIntOrNull() safely handles empty or non-numeric saved values.
        autoReloadInterval = (prefs.getString("autoReloadInterval", "10")?.toIntOrNull() ?: 10).coerceAtLeast(1)
        signURL = prefs.getString("url", "") ?: ""

        if (signURL.isEmpty()) {
            // No URL saved yet — send user to Settings to configure one
            showSettings()
            return
        }

        // Ensure the URL has a scheme — default to http:// for plain domain entries
        if (!signURL.startsWith("http://") && !signURL.startsWith("https://")) {
            signURL = "http://$signURL"
        }

        // Run the blocking network check on IO dispatcher to avoid blocking the main thread.
        // All UI updates (loadUrl, cacheMode) happen back on Main after the check completes.
        activityScope.launch {
            if (isNetworkAvailable()) {
                // Online: load fresh from network.
                // LOAD_NO_CACHE avoids a known crash in some menu board web apps that occurs
                // around 29 minutes when cached content is served instead of fresh content.
                signDisplayView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                onlineStatus = true
            } else {
                // Offline: serve from cache so the sign still shows something
                signDisplayView.settings.cacheMode = WebSettings.LOAD_CACHE_ONLY
                onlineStatus = false
            }
            // Back on Main dispatcher here — safe to update the WebView
            signDisplayView.loadUrl(signURL)
            restartAutoReloadJob()
        }
    }

    // -- Network Monitoring --

    /**
     * Starts the network checker coroutine. Runs every 20 seconds and calls changeCache()
     * to switch between online and offline modes on connectivity transitions.
     *
     * Started once in onCreate() and runs until onDestroy() cancels the scope.
     */
    private fun startNetworkChecker() {
        activityScope.launch {
            while (isActive) {
                delay(20_000L) // Check every 20 seconds
                changeCache()
            }
        }
    }

    /**
     * Checks current network state and switches cache mode only on transitions.
     * Suspend function — the blocking ping runs on Dispatchers.IO internally.
     *
     * This is a delta check — it does nothing if connectivity is unchanged.
     * This prevents the page from reloading every 20 seconds when connectivity is stable.
     */
    private suspend fun changeCache() {
        // Use the already-normalised URL loaded by loadConfig() rather than re-reading prefs.
        // signURL is maintained by loadConfig() which is called on every onResume().
        val currentUrl = signURL

        val nowOnline = isNetworkAvailable() // suspend — runs on IO, returns to Main

        if (nowOnline && !onlineStatus) {
            // Transitioned: offline → online
            signDisplayView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            signDisplayView.loadUrl(currentUrl)
            onlineStatus = true
        } else if (!nowOnline && onlineStatus) {
            // Transitioned: online → offline
            signDisplayView.settings.cacheMode = WebSettings.LOAD_CACHE_ONLY
            signDisplayView.loadUrl(currentUrl)
            onlineStatus = false
        }
        // No state change — do nothing, avoid redundant reloads
    }

    /**
     * Checks internet connectivity by pinging Google's DNS server.
     * Runs the blocking ping on Dispatchers.IO to avoid blocking the main thread.
     * Returns true if the ping succeeds (online), false otherwise (offline).
     *
     * Note: The `-w` flag semantics (seconds vs milliseconds) vary across Android/Fire OS
     * builds. On Fire OS, `-w 100` behaves as expected (short timeout). The command is
     * identical to the v1 implementation which ran successfully in production.
     */
    private suspend fun isNetworkAvailable(): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 100 8.8.8.8")
            process.waitFor() == 0
        } catch (e: IOException) {
            false
        } catch (e: InterruptedException) {
            false
        } finally {
            // Always destroy the process to release file descriptors.
            // On a 24/7 kiosk calling this every 20 seconds, failing to do so
            // causes a slow file descriptor leak via accumulated zombie processes.
            process?.destroy()
        }
    }

    // -- Auto Reload --

    /**
     * Cancels any existing auto-reload job and starts a new one if the toggle is enabled.
     * Called from loadConfig() so interval changes in Settings take effect immediately.
     */
    private fun restartAutoReloadJob() {
        autoReloadJob?.cancel()
        if (autoReloadToggle) {
            autoReloadJob = activityScope.launch {
                while (isActive) {
                    delay(autoReloadInterval * 60_000L)
                    // Reload the web page content only — does NOT restart the Activity
                    signDisplayView.reload()
                }
            }
        }
    }

    // -- Navigation --

    /**
     * Opens SettingsActivity.
     *
     * Sets menuConfig = true to prevent Aggressive Restart from firing when this
     * Activity pauses. finish() is intentionally NOT called — FullscreenActivity
     * stays in the back stack so the menuConfig guard remains effective on return.
     */
    private fun showSettings() {
        menuConfig = true
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // -- Fullscreen --

    /**
     * Hides the system status bar and navigation bar for fullscreen kiosk display.
     *
     * Uses WindowInsetsController on API 30+ (Android 11 / Fire OS 8).
     * Falls back to the legacy systemUiVisibility flags on older API levels.
     */
    @Suppress("DEPRECATION")
    private fun applyFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }
}
