package cool.cmg.sna

import android.content.Context
import android.view.WindowManager

data class DisplayConfig(
    val width: Int,
    val height: Int,
    val densityDpi: Int
){
    companion object {
        fun get(context: Context): DisplayConfig {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val bounds = windowManager.maximumWindowMetrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            val densityDpi = context.resources.configuration.densityDpi
            return DisplayConfig(width,height,densityDpi)
        }
    }
}