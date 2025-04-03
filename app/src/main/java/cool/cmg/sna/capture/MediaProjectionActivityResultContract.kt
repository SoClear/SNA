package cool.cmg.sna.capture

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.contract.ActivityResultContract

class MediaProjectionActivityResultContract: ActivityResultContract<Unit, Intent?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val mediaProjectionManager = context.getSystemService(MediaProjectionManager::class.java)
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
        return if (resultCode == RESULT_OK) intent else null
    }
}