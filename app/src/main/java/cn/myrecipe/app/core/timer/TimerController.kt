package cn.myrecipe.app.core.timer

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import cn.myrecipe.app.MainActivity
import cn.myrecipe.app.R
import cn.myrecipe.app.RecipeApplication
import cn.myrecipe.app.core.database.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun remainingMillis(timer: ActiveTimer, now: Long = SystemClock.elapsedRealtime()): Long = if (timer.state == "RUNNING") (timer.deadlineElapsed - now).coerceAtLeast(0) else timer.remainingMillis.coerceAtLeast(0)

class TimerController(private val context: Context, private val db: RecipeDatabase, private val gate: Mutex) {
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val dao = db.dao()
    private val alertService = Intent(context, TimerAlertService::class.java)
    init {
        notifications.createNotificationChannel(NotificationChannel("cooking_timer", "厨房计时到时提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "步骤计时结束时发出声音和振动"
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        })
        notifications.createNotificationChannel(NotificationChannel("timer_running", "运行中的厨房计时", NotificationManager.IMPORTANCE_LOW))
    }
    fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && notifications.areNotificationsEnabled() && notifications.getNotificationChannel("cooking_timer")?.importance != NotificationManager.IMPORTANCE_NONE
    fun permissionMessage(): String? = when { !exactAllowed() -> "请允许精确闹钟，后台计时提醒当前不可用"; !notificationsAllowed() -> "请开启通知及到时提醒渠道，后台计时提醒当前不可用"; else -> null }
    private fun pending(token: String = ""): PendingIntent = PendingIntent.getBroadcast(context, 41, Intent(context, TimerReceiver::class.java).setAction("FINISH").putExtra("token", token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancelPlatformAlarm() {
        alarm.cancel(pending())
        context.stopService(alertService)
        context.getSystemService(Vibrator::class.java).cancel()
        notifications.cancel(41); notifications.cancel(42)
    }
    private fun schedule(timer: ActiveTimer) {
        check(permissionMessage() == null) { permissionMessage().orEmpty() }
        alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.deadlineElapsed, pending(timer.token))
        showRunning(timer)
    }
    private fun contentIntent(timer: ActiveTimer): PendingIntent = PendingIntent.getActivity(context, 42, Intent(context, MainActivity::class.java).putExtra("timerRecipe", timer.recipeId).putExtra("timerStep", timer.stepId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun showRunning(timer: ActiveTimer) {
        if (!notificationsAllowed()) return
        // Returning to the app must not re-inflate SystemUI's clock and restart its tick.
        if (notifications.activeNotifications.any { it.id == 41 && it.notification.extras.getString("timerToken") == timer.token }) return
        val running = timer.state == "RUNNING"
        val views = RemoteViews(context.packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.timer_recipe, timer.recipeName)
            setTextViewText(R.id.timer_status, if (running) "计时中" else "计时已暂停")
            setViewVisibility(R.id.timer_countdown, if (running) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.timer_remaining, if (running) View.GONE else View.VISIBLE)
            // SystemUI owns the live countdown, including after the app process is reclaimed.
            setChronometer(R.id.timer_countdown, timer.deadlineElapsed, null, running)
            setChronometerCountDown(R.id.timer_countdown, true)
            val seconds = (remainingMillis(timer) + 999) / 1000
            setTextViewText(R.id.timer_remaining, if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60))
        }
        val notification = Notification.Builder(context, "timer_running").setSmallIcon(R.drawable.ic_timer).setContentTitle(timer.recipeName)
            .setContentText(if (running) "计时中" else "计时已暂停")
            .setContentIntent(contentIntent(timer)).setOngoing(true).setOnlyAlertOnce(true)
            .addExtras(Bundle().apply { putString("timerToken", timer.token) })
            .setShowWhen(false).setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views).setCustomBigContentView(views)
        notifications.notify(41, notification.build())
    }
    suspend fun start(recipe: Recipe, step: RecipeStep, seconds: Long, replace: Boolean = false) = gate.withLock {
        require(seconds in 1..604800) { "计时须在 1 秒至 7 天之间" }
        check(permissionMessage() == null) { permissionMessage().orEmpty() }
        val old = dao.timer(); check(replace || old == null || old.state in listOf("FINISHED", "INTERRUPTED")) { "已有计时器，请确认替换" }
        val timer = ActiveTimer(recipeId = recipe.id, stepId = step.id, recipeName = recipe.name, stepTitle = step.content.take(60), durationSeconds = seconds, deadlineElapsed = SystemClock.elapsedRealtime() + seconds * 1000, bootCount = bootCount())
        cancelPlatformAlarm(); dao.putTimer(timer)
        try { schedule(timer) } catch (e: Exception) { dao.putTimer(timer.copy(state = "INTERRUPTED", remainingMillis = seconds * 1000)); throw e }
    }
    suspend fun pause(expectedToken: String? = null) = gate.withLock {
        dao.timer()?.takeIf { it.state == "RUNNING" && (expectedToken == null || it.token == expectedToken) }?.let {
            val paused = it.copy(state = "PAUSED", remainingMillis = remainingMillis(it), token = newId())
            cancelPlatformAlarm(); dao.putTimer(paused); showRunning(paused)
        }
    }
    suspend fun resume(expectedToken: String? = null) = gate.withLock {
        dao.timer()?.takeIf { it.state == "PAUSED" && (expectedToken == null || it.token == expectedToken) }?.let {
            check(permissionMessage() == null) { permissionMessage().orEmpty() }
            val running = it.copy(state = "RUNNING", deadlineElapsed = SystemClock.elapsedRealtime() + it.remainingMillis.coerceAtLeast(1000), bootCount = bootCount(), token = newId())
            dao.putTimer(running)
            try { schedule(running) } catch (e: Exception) { dao.putTimer(it); throw e }
        }
    }
    suspend fun change(seconds: Long) = gate.withLock {
        require(seconds in 1..604800) { "计时须在 1 秒至 7 天之间" }
        dao.timer()?.takeIf { it.state in listOf("RUNNING", "PAUSED") }?.let {
            if (it.state == "RUNNING") check(permissionMessage() == null) { permissionMessage().orEmpty() }
            val changed = it.copy(durationSeconds = seconds, deadlineElapsed = SystemClock.elapsedRealtime() + seconds * 1000, remainingMillis = seconds * 1000, token = newId())
            cancelPlatformAlarm(); dao.putTimer(changed)
            try { if (changed.state == "RUNNING") schedule(changed) else showRunning(changed) }
            catch (e: Exception) { dao.putTimer(changed.copy(state = "INTERRUPTED")); throw e }
        }
    }
    suspend fun cancel() = gate.withLock { cancelPlatformAlarm(); dao.clearTimer() }
    suspend fun acknowledge(token: String) = gate.withLock {
        if (dao.timer()?.let { it.state == "FINISHED" && it.token == token } == true) {
            dao.clearTimer(); cancelPlatformAlarm()
        }
    }
    suspend fun withFinishedTimer(token: String?, action: (ActiveTimer) -> Unit): Boolean = gate.withLock {
        val timer = dao.timer()?.takeIf { it.state == "FINISHED" && it.bootCount == bootCount() && (token == null || token == it.token) } ?: return@withLock false
        action(timer)
        true
    }
    fun finishedNotification(token: String, recipeName: String, recipeId: String, stepId: String): Notification {
        val confirm = PendingIntent.getBroadcast(context, 43, Intent(context, TimerReceiver::class.java).setAction("ACKNOWLEDGE")
            .setData("cove-recipe://timer/$token".toUri()).putExtra("token", token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 42, Intent(context, MainActivity::class.java).putExtra("timerRecipe", recipeId).putExtra("timerStep", stepId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, "cooking_timer").setSmallIcon(R.drawable.ic_timer).setContentTitle(recipeName)
            .setContentText("计时结束").setContentIntent(open).setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "确认", confirm).build()).build()
    }
    suspend fun finish(token: String) = gate.withLock {
        dao.timer()?.takeIf { it.token == token && it.state == "RUNNING" && it.bootCount == bootCount() && remainingMillis(it) == 0L }?.let {
            dao.putTimer(it.copy(state = "FINISHED", remainingMillis = 0)); notifications.cancel(41)
            if (notificationsAllowed()) context.startForegroundService(Intent(context, TimerAlertService::class.java)
                .putExtra("token", it.token).putExtra("recipeName", it.recipeName).putExtra("recipeId", it.recipeId).putExtra("stepId", it.stepId))
        }
    }
    suspend fun interruptOnBoot() = gate.withLock {
        dao.timer()?.takeIf { it.state in listOf("RUNNING", "PAUSED") }?.let { dao.putTimer(it.copy(state = "INTERRUPTED", remainingMillis = 0)) }
        cancelPlatformAlarm()
    }
    suspend fun reconcile() {
        val timer = gate.withLock { dao.timer() } ?: return
        if (timer.bootCount != bootCount()) interruptOnBoot()
        else if (timer.state == "RUNNING" && remainingMillis(timer) == 0L) finish(timer.token)
        else if (timer.state in listOf("RUNNING", "PAUSED")) gate.withLock {
            val current = dao.timer() ?: return@withLock
            if (current.state == "RUNNING") {
                if (permissionMessage() == null) schedule(current)
                else { cancelPlatformAlarm(); dao.putTimer(current.copy(state = "INTERRUPTED", remainingMillis = remainingMillis(current))) }
            } else if (current.state == "PAUSED") showRunning(current)
        }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync(); val app = context.applicationContext as RecipeApplication
        app.scope.launch {
            try {
                val token = intent.getStringExtra("token") ?: return@launch
                when (intent.action) { "FINISH" -> app.container.timer.finish(token); "PAUSE" -> app.container.timer.pause(token); "RESUME" -> app.container.timer.resume(token); "ACKNOWLEDGE" -> app.container.timer.acknowledge(token) }
            } catch (e: Exception) { android.util.Log.e("MyRecipeTimer", "计时操作失败", e) }
            finally { pendingResult.finish() }
        }
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync(); val app = context.applicationContext as RecipeApplication
        app.scope.launch { try { app.container.timer.interruptOnBoot() } finally { result.finish() } }
    }
}
