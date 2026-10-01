package io.mtluntan.app.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global mutable session state: which account (or guest) is currently active.
 * Swapping the account swaps the persisted cookie jar used by all requests.
 */
object SessionHolder {
    private val _activeAccount = MutableStateFlow<String?>(null)
    private val _desktopMode = MutableStateFlow(false)

    val activeAccount: StateFlow<String?> = _activeAccount.asStateFlow()
    val desktopMode: StateFlow<Boolean> = _desktopMode.asStateFlow()

    fun setAccount(name: String?) {
        _activeAccount.value = name
    }

    fun setDesktopMode(enabled: Boolean) {
        _desktopMode.value = enabled
    }
}