package io.mtluntan.app

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.mtluntan.app.data.db.AppDatabase
import io.mtluntan.app.data.local.AppSettings
import io.mtluntan.app.data.network.Net
import io.mtluntan.app.data.repo.AuthRepository
import io.mtluntan.app.data.repo.ForumRepository
import io.mtluntan.app.data.repo.LocalRepository
import io.mtluntan.app.worker.CheckInWorker
import java.util.concurrent.TimeUnit

/** Application entry: owns the singletons and wires background workers. */
class MTLuntanApp : android.app.Application() {

    lateinit var settings: AppSettings
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var net: Net
        private set
    lateinit var local: LocalRepository
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var forum: ForumRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        db = AppDatabase.get(this)
        net = Net.init(this)
        local = LocalRepository(db)
        auth = AuthRepository(db, net.cookieRepo, settings)
        forum = ForumRepository(net)

        setupWorkers()
    }

    private fun setupWorkers() {
        val checkInRequest = PeriodicWorkRequestBuilder<CheckInWorker>(
            12, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CheckInWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            checkInRequest,
        )
    }

    companion object {
        fun of(context: android.content.Context): MTLuntanApp =
            context.applicationContext as MTLuntanApp
    }
}