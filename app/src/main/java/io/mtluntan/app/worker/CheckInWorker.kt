package io.mtluntan.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.mtluntan.app.MTLuntanApp

/**
 * Periodically opens the sign plugin page to keep the current session alive
 * and refresh the check-in streak. Runs quietly in the background; the UI reads
 * the true online state from [io.mtluntan.app.data.repo.ForumRepository.doSignInWhenDue].
 */
class CheckInWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "mt_keepalive_checkin"
        private const val TAG = "CheckInWorker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as MTLuntanApp
        val auth = app.auth
        val forum = app.forum
        // Only act when a guest-less session exists and auto-sign is on.
        runCatching {
            if (auth.isLoggedIn()) {
                forum.doSignIn()
            }
        }.onFailure { Log.w(TAG, "bg sign failed: ${it.message}") }
        return Result.success()
    }
}

/** Helper used by the app to run an immediate sign attempt. */
suspend fun runCheckInNow(context: Context) {
    val app = context.applicationContext as MTLuntanApp
    val forum = app.forum
    runCatching {
        if (app.auth.isLoggedIn()) forum.doSignIn()
    }
}