package cc.netwokx.ffsms.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.remote.ApiClientFactory
import cc.netwokx.ffsms.data.settings.AppSettings
import cc.netwokx.ffsms.update.AvailableUpdate
import cc.netwokx.ffsms.update.UpdateChecker
import cc.netwokx.ffsms.update.UpdateResult
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

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data object Downloading : UpdateState
    data class Failed(val reason: String) : UpdateState
}

data class SettingsUiState(
    val settings: AppSettings? = null,
    val segmentsToday: Int = 0,
    val test: ConnectionTest = ConnectionTest.Idle,
    val saved: Boolean = false,
    val update: UpdateState = UpdateState.Idle,
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

    /**
     * Sucht von Hand nach einem Update.
     *
     * Der taegliche Worker meldet nur; heruntergeladen und installiert wird
     * erst, wenn hier ausdruecklich darauf getippt wird. Auf einem
     * Alarmierungsgeraet soll niemand ueberrascht werden.
     */
    fun checkForUpdate() {
        _state.update { it.copy(update = UpdateState.Checking) }
        viewModelScope.launch {
            val result = UpdateChecker(getApplication()).check()
            _state.update {
                it.copy(
                    update = when (result) {
                        is UpdateResult.Available -> UpdateState.Available(result.manifest)
                        UpdateResult.UpToDate -> UpdateState.UpToDate
                        is UpdateResult.Failed -> UpdateState.Failed(result.reason)
                    },
                )
            }
        }
    }

    fun installUpdate(update: AvailableUpdate) {
        _state.update { it.copy(update = UpdateState.Downloading) }
        viewModelScope.launch {
            val error = UpdateChecker(getApplication()).download(update)
            _state.update {
                // Bei Erfolg uebernimmt jetzt Androids Installationsdialog.
                it.copy(update = if (error == null) UpdateState.Idle else UpdateState.Failed(error))
            }
        }
    }

    fun updateShown() = _state.update { it.copy(update = UpdateState.Idle) }

    fun savedShown() = _state.update { it.copy(saved = false) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            _state.update { it.copy(saved = true) }
        }
    }
}
