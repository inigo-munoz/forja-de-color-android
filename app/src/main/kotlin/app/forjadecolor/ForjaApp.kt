package app.forjadecolor

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ForjaApp : Application() {
    lateinit var container: AppContainer
        @VisibleForTesting internal set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            try {
                container.removeOrphanPhotos()
            } catch (e: Exception) {
                Log.w("ForjaApp", "orphan photo cleanup failed; it runs again on the next start", e)
            }
        }
    }
}
