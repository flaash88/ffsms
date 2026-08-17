package cc.netwokx.ffsms.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.db.CampaignEntity
import cc.netwokx.ffsms.data.db.RecipientStatusRow
import cc.netwokx.ffsms.export.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val campaigns: List<CampaignEntity> = emptyList(),
    val weekSegments: Int = 0,
    val monthSegments: Int = 0,
    /** Monatsvolumen des Tarifs. 0 = unbekannt, dann ohne Nenner anzeigen. */
    val monthLimit: Int = 0,
    val unsyncedCount: Int = 0,
    val exportResult: Boolean? = null,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.campaigns(app)

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeCampaigns().collect { list -> _state.update { it.copy(campaigns = list) } }
        }
        viewModelScope.launch {
            repo.observeWeekSegments().collect { v -> _state.update { it.copy(weekSegments = v) } }
        }
        viewModelScope.launch {
            repo.observeMonthSegments().collect { v -> _state.update { it.copy(monthSegments = v) } }
        }
        viewModelScope.launch {
            ServiceLocator.database(app).campaignDao().observeUnsyncedCount()
                .collect { v -> _state.update { it.copy(unsyncedCount = v) } }
        }
        // Die Monatszahl ohne ihren Nenner sagt nichts. "178" ist harmlos oder
        // knapp, je nachdem ob 500 oder 200 im Tarif sind.
        viewModelScope.launch {
            ServiceLocator.settings(app).settings.collect { s ->
                _state.update { it.copy(monthLimit = s.maxSegmentsPerMonth) }
            }
        }
    }

    fun suggestedFileName(): String = CsvExporter.suggestedFileName()

    /** Schreibt den Verlauf in das vom Benutzer gewaehlte Dokument. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = CsvExporter.export(repo.allCampaigns())
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Kein Ausgabestrom")
                }.isSuccess
            }
            _state.update { it.copy(exportResult = ok) }
        }
    }

    fun exportResultShown() = _state.update { it.copy(exportResult = null) }
}

data class CampaignDetailUiState(
    val campaign: CampaignEntity? = null,
    val recipients: List<RecipientStatusRow> = emptyList(),
    val retryStarted: String? = null,
)

class CampaignDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.campaigns(app)

    private val _state = MutableStateFlow(CampaignDetailUiState())
    val state: StateFlow<CampaignDetailUiState> = _state.asStateFlow()

    private var campaignId: String? = null

    fun bind(id: String) {
        if (campaignId == id) return
        campaignId = id
        viewModelScope.launch {
            repo.observeCampaign(id).collect { c -> _state.update { it.copy(campaign = c) } }
        }
        viewModelScope.launch {
            repo.observeRecipientStatus(id).collect { list -> _state.update { it.copy(recipients = list) } }
        }
    }

    /** Einzelner, ausdruecklich von Hand ausgeloester Neuversuch. */
    fun retry(msisdn: String) {
        val id = campaignId ?: return
        viewModelScope.launch {
            repo.retrySingle(id, msisdn)
            _state.update { it.copy(retryStarted = msisdn) }
        }
    }

    fun retryShown() = _state.update { it.copy(retryStarted = null) }
}
