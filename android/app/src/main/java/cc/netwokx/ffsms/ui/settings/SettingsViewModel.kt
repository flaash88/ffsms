package cc.netwokx.ffsms.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.remote.ApiClientFactory
import cc.netwokx.ffsms.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data class Ok(val status: String) : ConnectionTest
    data class Failed(val reason: String) : ConnectionTest
}

data class SettingsUiState(
    val settings: AppSettings? = null,
    val segmentsToday: Int = 0,
    val test: ConnectionTest = ConnectionTest.Idle,
    val saved: Boolean = false,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.settings(app)
    private val campaigns = ServiceLocator.campaigns(app)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            _state.update { it.copy(segmentsToday = campaigns.segmentsToday()) }
        }
    }

    fun setBackendUrl(v: String) = update { repo.setBackendUrl(v) }

    fun setApiKey(v: String) = update { repo.setApiKey(v) }

    fun setDeviceId(v: String) = update { repo.setDeviceId(v) }

    fun setSyncEnabled(v: Boolean) = update { repo.setSyncEnabled(v) }

    fun setWarnThreshold(v: Int) = update { repo.setWarnThreshold(v) }

    fun setMaxPerCampaign(v: Int) = update { repo.setMaxPerCampaign(v) }

    fun setMaxPerDay(v: Int) = update { repo.setMaxPerDay(v) }

    /**
     * Prueft Erreichbarkeit und API-Key gegen /api/v1/health.
     * Es werden dabei keine Verbrauchsdaten uebertragen.
     */
    fun testConnection() {
        val settings = _state.value.settings ?: return
        if (!settings.backendConfigured) {
            _state.update { it.copy(test = ConnectionTest.Failed("incomplete")) }
            return
        }
        _state.update { it.copy(test = ConnectionTest.Running) }
        viewModelScope.launch {
            val result = runCatching {
                ApiClientFactory.create(settings.backendUrl, settings.apiKey).health()
            }
            _state.update {
                it.copy(
                    test = result.fold(
                        onSuccess = { health -> ConnectionTest.Ok(health.status) },
                        onFailure = { e -> ConnectionTest.Failed(e.message ?: e::class.java.simpleName) },
                    ),
                )
            }
        }
    }

    fun testShown() = _state.update { it.copy(test = ConnectionTest.Idle) }

    fun savedShown() = _state.update { it.copy(saved = false) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            _state.update { it.copy(saved = true) }
        }
    }
}
