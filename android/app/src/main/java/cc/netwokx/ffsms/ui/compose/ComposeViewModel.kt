package cc.netwokx.ffsms.ui.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.db.GroupWithCount
import cc.netwokx.ffsms.data.repo.PendingCampaign
import cc.netwokx.ffsms.data.settings.AppSettings
import cc.netwokx.ffsms.domain.sms.SegmentCalculator
import cc.netwokx.ffsms.domain.sms.SegmentInfo
import cc.netwokx.ffsms.domain.sms.TextSanitizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComposeUiState(
    val groups: List<GroupWithCount> = emptyList(),
    val selectedGroupId: Long? = null,
    val text: String = "",
    val info: SegmentInfo = SegmentCalculator.calculate(""),
    val recipientCount: Int = 0,
    val settings: AppSettings? = null,
    /** Segmente, die eine Bereinigung sparen wuerde. 0 = nichts zu holen. */
    val savingBySanitize: Int = 0,
    val pending: PendingCampaign? = null,
    val message: String? = null,
) {
    val totalSegments: Int get() = info.segments * recipientCount

    val selectedGroup: GroupWithCount?
        get() = groups.firstOrNull { it.group.id == selectedGroupId }

    val overWarnThreshold: Boolean
        get() = settings?.let { totalSegments >= it.warnThresholdSegments } ?: false

    /** Ueber der harten Obergrenze - der Worker wuerde abbrechen. */
    val overHardLimit: Boolean
        get() = settings?.let { totalSegments > it.maxSegmentsPerCampaign } ?: false

    val canSend: Boolean
        get() = text.isNotBlank() && recipientCount > 0 && !overHardLimit
}

class ComposeViewModel(app: Application) : AndroidViewModel(app) {

    private val groupRepo = ServiceLocator.groups(app)
    private val campaignRepo = ServiceLocator.campaigns(app)
    private val settingsRepo = ServiceLocator.settings(app)

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            groupRepo.observeGroups().collect { groups ->
                _state.update { current ->
                    // Bei genau einer Gruppe direkt vorauswaehlen - der
                    // haeufigste Fall bei einer kleinen Feuerwehr.
                    val selected = current.selectedGroupId
                        ?: groups.singleOrNull()?.group?.id
                    current.copy(groups = groups, selectedGroupId = selected)
                }
                refreshRecipientCount()
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
    }

    /** Wird bei JEDEM Tastendruck aufgerufen - die Berechnung ist billig genug. */
    fun onTextChanged(text: String) {
        val info = SegmentCalculator.calculate(text)
        val sanitized = if (info.offenders.isEmpty()) {
            0
        } else {
            val after = SegmentCalculator.calculate(TextSanitizer.sanitize(text).text)
            (info.segments - after.segments).coerceAtLeast(0)
        }
        _state.update { it.copy(text = text, info = info, savingBySanitize = sanitized) }
    }

    fun onGroupSelected(groupId: Long) {
        _state.update { it.copy(selectedGroupId = groupId) }
        refreshRecipientCount()
    }

    /**
     * Bereinigt den Text und meldet zurueck, was passiert ist.
     * Die Anzeige springt dadurch sichtbar von UCS-2 auf GSM-7.
     */
    fun onSanitize() {
        val result = TextSanitizer.sanitize(_state.value.text)
        if (!result.changed) {
            _state.update { it.copy(message = "cleaned_nothing") }
            return
        }
        onTextChanged(result.text)
        _state.update {
            it.copy(message = "cleaned:${result.replaced.size}:${result.removed.size}")
        }
    }

    /**
     * Oeffnet den Bestaetigungsdialog.
     *
     * HIER entsteht die Campaign-UUID - nicht beim Klick auf "Senden". Ein
     * Doppeltipp auf den Senden-Button im Dialog verwendet damit zweimal
     * dieselbe UUID und laeuft in die Idempotenzsperre des WorkManagers.
     */
    fun onSendRequested() {
        val current = _state.value
        val group = current.selectedGroup ?: return
        if (current.text.isBlank()) return

        viewModelScope.launch {
            val recipients = groupRepo.validRecipients(group.group.id)
            if (recipients.isEmpty()) {
                _state.update { it.copy(message = "no_recipients") }
                return@launch
            }

            val info = SegmentCalculator.calculate(current.text)
            _state.update {
                it.copy(
                    pending = PendingCampaign(
                        campaignId = campaignRepo.newCampaignId(),
                        groupId = group.group.id,
                        groupName = group.group.name,
                        text = current.text,
                        recipients = recipients.map { r ->
                            PendingCampaign.Recipient(r.msisdn, r.displayName)
                        },
                        info = info,
                    ),
                )
            }
        }
    }

    fun onConfirmDismissed() {
        // Die verworfene UUID wird nicht wiederverwendet: ein erneuter Anlauf
        // ist eine neue Aussendung und bekommt eine neue Kennung.
        _state.update { it.copy(pending = null) }
    }

    fun onConfirmed() {
        val pending = _state.value.pending ?: return
        // Dialog sofort schliessen, damit ein zweiter Tap ins Leere geht.
        _state.update { it.copy(pending = null) }
        viewModelScope.launch {
            campaignRepo.confirmAndEnqueue(pending)
            _state.update { it.copy(text = "", info = SegmentCalculator.calculate(""), message = "queued") }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun refreshRecipientCount() {
        val groupId = _state.value.selectedGroupId
        viewModelScope.launch {
            val count = groupId?.let { groupRepo.countValidRecipients(it) } ?: 0
            _state.update { it.copy(recipientCount = count) }
        }
    }
}
