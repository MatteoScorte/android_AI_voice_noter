package com.transcriber.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.transcriber.app.api.CanvaApiClient
import kotlinx.coroutines.flow.first
import com.transcriber.app.data.InboxRepository
import com.transcriber.app.data.SettingsRepository
import com.transcriber.app.ui.theme.DarkBackground
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled — app will check before recording */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        // Only process the launch intent on FIRST creation.
        // savedInstanceState != null means the Activity is being re-created due to a
        // configuration change (rotation, permission dialog, system kill-restore, etc.)
        // In those cases this.intent still holds the original WhatsApp ACTION_SEND intent,
        // so calling handleIncomingIntent here would import the same file a second (or third)
        // time.  onNewIntent() already handles shares that arrive while the app is running.
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBackground
            ) {
                TranscriberApp()
            }
        }
    }

    // Called when the activity is already running (singleTop) and a new share arrives
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * If the launching intent is an ACTION_SEND with an audio URI, copy the file
     * into the app's inbox so it appears in the Audio Inbox tab.
     *
     * After processing we clear the action on the stored intent via setIntent() so
     * that any unexpected re-delivery path (e.g. onNewIntent called twice by some
     * launchers, or future code that calls handleIncomingIntent again) is idempotent.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        // Canva OAuth callback
        val deepLinkUri = intent?.data
        if (intent?.action == Intent.ACTION_VIEW &&
            deepLinkUri?.scheme == "com.transcriber.app" && deepLinkUri.host == "canva"
        ) {
            val code = deepLinkUri.getQueryParameter("code") ?: return
            setIntent(Intent())
            lifecycleScope.launch {
                val clientId = SettingsRepository(this@MainActivity).canvaClientId.first()
                CanvaApiClient(this@MainActivity).handleOAuthCallback(code, clientId)
            }
            return
        }

        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("audio/") != true) return

        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        uri?.let { incomingUri ->
            // Consume the intent so it cannot be re-processed by a subsequent
            // onCreate/onNewIntent call for the same share action.
            setIntent(Intent())

            lifecycleScope.launch {
                InboxRepository(this@MainActivity).importFromUri(this@MainActivity, incomingUri)
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
