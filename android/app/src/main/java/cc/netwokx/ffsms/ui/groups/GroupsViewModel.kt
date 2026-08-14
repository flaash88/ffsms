package cc.netwokx.ffsms.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.contacts.ContactsReader
import cc.netwokx.ffsms.data.contacts.DeviceContact
import cc.netwokx.ffsms.data.db.GroupWithCount
import cc.netwokx.ffsms.data.db.RecipientEntity
import cc.netwokx.ffsms.data.repo.ImportResult
import cc.netwokx.ffsms.data.repo.RawContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val groups: List<GroupWithCount> = emptyList(),
)

class GroupsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.groups(app)

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeGroups().collect { groups -> _state.update { it.copy(groups = groups) } }
        }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.createGroup(name) }
    }

    fun renameGroup(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameGroup(id, name) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { repo.deleteGroup(id) }
    }
}

data class GroupDetailUiState(
    val groupName: String = "",
    val recipients: List<RecipientEntity> = emptyList(),
    val contacts: List<DeviceContact> = emptyList(),
    val contactsLoading: Boolean = false,
    val lastImport: ImportResult? = null,
)

class GroupDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.groups(app)
    private val contactsReader = ContactsReader(app)

    private val _state = MutableStateFlow(GroupDetailUiState())
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    private var groupId: Long = -1

    fun bind(id: Long) {
        if (groupId == id) return
        groupId = id
        viewModelScope.launch {
            _state.update { it.copy(groupName = repo.findGroup(id)?.name.orEmpty()) }
        }
        viewModelScope.launch {
            repo.observeRecipients(id).collect { list -> _state.update { it.copy(recipients = list) } }
        }
    }

    fun loadContacts() {
        _state.update { it.copy(contactsLoading = true) }
        viewModelScope.launch {
            val contacts = runCatching { contactsReader.loadPhoneContacts() }.getOrDefault(emptyList())
            _state.update { it.copy(contacts = contacts, contactsLoading = false) }
        }
    }

    fun importContacts(selected: List<DeviceContact>) {
        viewModelScope.launch {
            val result = repo.importContacts(
                groupId,
                selected.map { RawContact(it.number, it.displayName) },
            )
            _state.update { it.copy(lastImport = result) }
        }
    }

    /**
     * Uebernimmt eine manuelle Eingabe. Mehrere Nummern duerfen durch Komma,
     * Semikolon oder Zeilenumbruch getrennt sein - das spart bei der
     * Ersteinrichtung viel Tipparbeit.
     */
    fun addManual(input: String, name: String?) {
        val numbers = input.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (numbers.isEmpty()) return

        viewModelScope.launch {
            val result = repo.importContacts(
                groupId,
                numbers.map { RawContact(it, name?.takeIf { _ -> numbers.size == 1 }) },
            )
            _state.update { it.copy(lastImport = result) }
        }
    }

    fun deleteRecipient(id: Long) {
        viewModelScope.launch { repo.deleteRecipient(id) }
    }

    fun importShown() = _state.update { it.copy(lastImport = null) }
}
