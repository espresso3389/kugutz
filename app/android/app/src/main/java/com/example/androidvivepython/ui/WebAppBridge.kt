package jp.espresso3389.kugutz.ui

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import jp.espresso3389.kugutz.perm.PermissionBroker
import java.net.HttpURLConnection
import java.net.URL

class WebAppBridge(private val activity: Activity) {
    private val broker = PermissionBroker(activity)
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun requestNativeConsent(requestId: String, tool: String, detail: String) {
        handler.post {
            broker.requestConsent(tool, detail) { approved ->
                val action = if (approved) "approve" else "deny"
                val url = URL("http://127.0.0.1:8765/permissions/$requestId/$action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                try {
                    conn.outputStream.use { it.write(ByteArray(0)) }
                    conn.inputStream.use { }
                } catch (_: Exception) {
                    // Ignore network errors; UI will retry via polling.
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    @JavascriptInterface
    fun showPythonServiceDialog() {
        handler.post {
            if (activity is MainActivity) {
                activity.showStatusDialog()
            }
        }
    }

    @JavascriptInterface
    fun restartPythonService() {
        handler.post {
            val intent = Intent(activity, jp.espresso3389.kugutz.service.AgentService::class.java)
            intent.action = jp.espresso3389.kugutz.service.AgentService.ACTION_RESTART_PYTHON
            activity.startForegroundService(intent)
        }
    }

    @JavascriptInterface
    fun stopPythonService() {
        handler.post {
            val intent = Intent(activity, jp.espresso3389.kugutz.service.AgentService::class.java)
            intent.action = jp.espresso3389.kugutz.service.AgentService.ACTION_STOP_PYTHON
            activity.startForegroundService(intent)
        }
    }

    @JavascriptInterface
    fun resetUiToDefaults() {
        handler.post {
            val extractor = jp.espresso3389.kugutz.service.AssetExtractor(activity)
            extractor.resetUiAssets()
        }
    }
}
