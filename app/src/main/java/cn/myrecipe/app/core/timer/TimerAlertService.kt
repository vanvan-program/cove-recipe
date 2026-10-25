package cn.myrecipe.app.core.timer

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import cn.myrecipe.app.RecipeApplication
import kotlinx.coroutines.launch

/** An exact alarm starts this service; the user acknowledges its persistent vibration. */
class TimerAlertService : Service() {
    private val app get() = application as RecipeApplication
    private val vibrator get() = getSystemService(Vibrator::class.java)
    private var ringingToken: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra("token")
        val timer = app.container.timer
        startForeground(42, timer.finishedNotification(token.orEmpty(), intent?.getStringExtra("recipeName") ?: "厨房计时",
            intent?.getStringExtra("recipeId").orEmpty(), intent?.getStringExtra("stepId").orEmpty()))
        app.scope.launch {
            val valid = timer.withFinishedTimer(token) { finished ->
                startForeground(42, timer.finishedNotification(finished.token, finished.recipeName, finished.recipeId, finished.stepId))
                if (ringingToken != finished.token) {
                    ringingToken = finished.token
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                }
            }
            if (!valid) stopSelf(startId)
        }
        return START_STICKY
    }
    override fun onDestroy() {
        vibrator.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
