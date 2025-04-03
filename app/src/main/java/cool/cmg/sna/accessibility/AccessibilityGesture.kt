package cool.cmg.sna.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume


suspend fun AccessibilityService.performGesture(strokeDescription: StrokeDescription): Boolean =
    suspendCancellableCoroutine {
        val gestureDescription = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                it.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                it.resume(false)
            }
        }

        dispatchGesture(gestureDescription, callback, null)
    }

suspend fun AccessibilityService.click(x: Int, y: Int, times: Int = 1) {
    val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    val strokeDescription = StrokeDescription(path, 0, 100)
    repeat(times) {
        performGesture(strokeDescription)
    }
//    delay(300)
}