package cool.cmg.sna

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import cool.cmg.sna.capture.MediaProjectionService

class MainService : AccessibilityService() {
    companion object {
        var instance: MainService? = null
            private set

        fun isEnabled(context: Context): Boolean = context
            .getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                service.resolveInfo.serviceInfo.run {
                    packageName == context.packageName && name == MainService::class.java.name
                }
            }
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        super.onServiceConnected()
    }

    // 脚本运行时按音量下键关闭关闭
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            && event.action == KeyEvent.ACTION_DOWN
            && MediaProjectionService.INSTANCE != null
        ) {
            MediaProjectionService.INSTANCE?.stopSelf()
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}