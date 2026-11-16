package app.forjadecolor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.forjadecolor.platform.fileAuthority
import app.forjadecolor.platform.parseIncomingShare
import app.forjadecolor.ui.navigation.AppNavHost
import app.forjadecolor.ui.theme.ForgeTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as ForjaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark: keep light system-bar icons even when the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation) still holds the launch intent: it was already handled.
        if (savedInstanceState == null) container.incomingShares.offer(parseIncomingShare(intent, fileAuthority(this)))
        setContent {
            ForgeTheme {
                AppNavHost(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        container.incomingShares.offer(parseIncomingShare(intent, fileAuthority(this)))
    }
}
