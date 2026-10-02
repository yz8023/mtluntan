package io.mtluntan.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局刷新总线。
 *
 * 解决两类「切了东西但界面没变」的问题（Java 版 v2.6 踩过的坑）：
 *  - 切账号后所有页面要重新拉数据 —— 旧的请求去重缓存也要一起失效
 *  - 底栏点当前 Tab 要刷新该页
 *
 * [generation] 每次账号变化 +1，页面把它当 key 用即可实现「切号自动重载」。
 */
object Refresh {
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private val _tabTick = MutableStateFlow(0)
    val tabTick: StateFlow<Int> = _tabTick.asStateFlow()

    /** 账号切换 / 登录登出后调用。 */
    fun bumpGeneration() {
        _generation.value = _generation.value + 1
    }

    /** 底栏再次点击当前 Tab 时调用。 */
    fun bumpTab() {
        _tabTick.value = _tabTick.value + 1
    }
}
