package io.mtluntan.app

import io.mtluntan.app.ai.AiConfigStore
import io.mtluntan.app.ai.AiRepository
import io.mtluntan.app.ai.AutoReplyEngine
import io.mtluntan.app.ai.AutoReplyScheduler
import io.mtluntan.app.data.db.AppDatabase
import io.mtluntan.app.data.local.AppSettings
import io.mtluntan.app.data.network.Net
import io.mtluntan.app.data.repo.AuthRepository
import io.mtluntan.app.data.repo.ForumRepository
import io.mtluntan.app.data.repo.LocalRepository
import io.mtluntan.app.data.repo.SessionGuard
import io.mtluntan.app.data.repo.SignInManager
import io.mtluntan.app.util.Notifier
import io.mtluntan.app.worker.BadgeScheduler
import io.mtluntan.app.worker.SignScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Application 入口：持有单例并接好后台任务。 */
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
    lateinit var sign: SignInManager
        private set
    lateinit var guard: SessionGuard
        private set
    lateinit var ai: AiRepository
        private set
    lateinit var aiConfig: AiConfigStore
        private set
    lateinit var autoReply: AutoReplyEngine
        private set

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    override fun onCreate() {
        super.onCreate()
        current = this
        settings = AppSettings(this)
        db = AppDatabase.get(this)
        net = Net.init(this)
        local = LocalRepository(db)
        auth = AuthRepository(db, net.cookieRepo, settings)
        forum = ForumRepository(net)
        sign = SignInManager(applicationContext, db, net.cookieRepo, settings, auth)
        guard = SessionGuard(net.cookieRepo)
        aiConfig = AiConfigStore(settings)
        ai = AiRepository(this)
        autoReply = AutoReplyEngine(this)

        Notifier.ensureChannel(this)
        setupWorkers()
        restoreActiveAccount()
    }

    private fun setupWorkers() {
        appScope.launch {
            val auto = settings.snapshotAutoSign()
            val (hour, minute) = settings.snapshotSignTime()
            if (auto) SignScheduler.schedule(this@MTLuntanApp, hour, minute) else SignScheduler.cancel(this@MTLuntanApp)

            val notify = settings.snapshotNotify()
            if (notify) BadgeScheduler.schedule(this@MTLuntanApp) else BadgeScheduler.cancel(this@MTLuntanApp)

            if (settings.snapshotAutoReply()) {
                AutoReplyScheduler.schedule(this@MTLuntanApp)
            } else {
                AutoReplyScheduler.cancel(this@MTLuntanApp)
            }
        }
    }

    /** 启动时恢复上次使用的账号（cookie jar + 前台会话）。 */
    private fun restoreActiveAccount() {
        appScope.launch {
            runCatching {
                val saved = settings.activeAccount.first()
                if (!saved.isNullOrEmpty()) auth.activate(saved)
            }
        }
    }

    companion object {
        @Volatile
        private var current: MTLuntanApp? = null

        fun of(context: android.content.Context): MTLuntanApp =
            context.applicationContext as MTLuntanApp

        /** 供仓库层在无 Context 时取用（可能为 null，调用方需判空）。 */
        fun instanceOrNull(): MTLuntanApp? = current
    }
}
