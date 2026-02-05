package jp.espresso3389.kugutz.ui

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import jp.espresso3389.kugutz.service.AgentService

class WebAppBridge(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun showServiceDialog() {
        // no-op: status dialog removed
    }

    @JavascriptInterface
    fun startPythonWorker() {
        handler.post {
            val intent = Intent(activity, AgentService::class.java)
            intent.action = AgentService.ACTION_START_PYTHON
            activity.startForegroundService(intent)
        }
    }

    @JavascriptInterface
    fun restartPythonWorker() {
        handler.post {
            val intent = Intent(activity, AgentService::class.java)
            intent.action = AgentService.ACTION_RESTART_PYTHON
            activity.startForegroundService(intent)
        }
    }

    @JavascriptInterface
    fun stopPythonWorker() {
        handler.post {
            val intent = Intent(activity, AgentService::class.java)
            intent.action = AgentService.ACTION_STOP_PYTHON
            activity.startForegroundService(intent)
        }
    }

    @JavascriptInterface
    fun resetUiToDefaults() {
        handler.post {
            val extractor = jp.espresso3389.kugutz.service.AssetExtractor(activity)
            extractor.resetUiAssets()
            if (activity is MainActivity) {
                activity.reloadUi()
            }
        }
    }

    @JavascriptInterface
    fun getWifiIp(): String {
        return try {
            val cm = activity.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager
            val network = cm?.activeNetwork ?: return ""
            val caps = cm.getNetworkCapabilities(network) ?: return ""
            if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                return ""
            }
            val linkProps = cm.getLinkProperties(network) ?: return ""
            val addr = linkProps.linkAddresses
                .mapNotNull { it.address }
                .firstOrNull { it is java.net.Inet4Address }
            addr?.hostAddress ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
