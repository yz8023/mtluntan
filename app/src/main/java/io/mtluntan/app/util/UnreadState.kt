package io.mtluntan.app.util

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 未读消息状态（跨页面共享）。
 *
 * 消息页 / 私信页 / 后台轮询都会更新这里，底栏「消息」Tab 读它显示角标；
 * 带上账号名是为了切号时立刻归零，不会出现「B 账号看到 A 账号的红点」。
 */
object UnreadState {

    data class Snapshot(val account: String? = null, val notices: Int = 0, val pms: Int = 0) {
        val total: Int get() = notices + pms
    }

    private val state = MutableStateFlow(Snapshot())
    val flow = state

    fun update(account: String?, notices: Int, pms: Int) {
        state.value = Snapshot(account = account, notices = notices.coerceAtLeast(0), pms = pms.coerceAtLeast(0))
    }

    /** 切号 / 退出登录时清空。 */
    fun clear(account: String? = null) {
        val current = state.value
        if (account == null || current.account == null || current.account != account) {
            state.value = Snapshot(account = account)
        }
    }

    fun markAllRead() {
        state.value = state.value.copy(notices = 0, pms = 0)
    }
}
