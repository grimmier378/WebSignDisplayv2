package com.mattlabs.websigndisplay

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

/**
 * Displays the app configuration screen.
 *
 * Exposes five settings via a PreferenceFragment backed by root_preferences.xml:
 * - Sign URL
 * - Auto Start on Boot
 * - Aggressive Restart
 * - Auto Reload Page
 * - Reload Interval (minutes)
 *
 * The Save button returns to FullscreenActivity. Back navigation also works via the action bar.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // Show the status bar in Settings so the user can see system info
        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)

        // Add the preferences fragment if this is a fresh creation (not a rotation restore)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }

        // Show back arrow in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Save button: return to the main display
        val saveButton: Button = findViewById(R.id.button_Save)
        saveButton.setOnClickListener {
            startActivity(
                Intent(this, FullscreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            finish()
        }
    }

    /**
     * Preference fragment that inflates root_preferences.xml.
     *
     * Also performs runtime configuration that cannot be done in XML:
     * - Sets numeric input type on the reload interval field
     * - Validates that the reload interval is a positive integer
     */
    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            configureReloadIntervalPreference()
        }

        /**
         * Configures the autoReloadInterval EditTextPreference:
         * - Restricts the keyboard to numeric input
         * - Validates that the entered value is a positive integer (>= 1)
         * - Rejects invalid input with a toast rather than silently ignoring it
         */
        private fun configureReloadIntervalPreference() {
            val intervalPref = findPreference<EditTextPreference>("autoReloadInterval") ?: return

            // Show a numeric keyboard for this field
            intervalPref.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }

            intervalPref.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue.toString().toIntOrNull()
                if (value == null || value < 1) {
                    Toast.makeText(
                        requireContext(),
                        "Reload interval must be 1 minute or greater",
                        Toast.LENGTH_SHORT
                    ).show()
                    false // Reject the change
                } else {
                    true // Accept the change
                }
            }
        }
    }
}
