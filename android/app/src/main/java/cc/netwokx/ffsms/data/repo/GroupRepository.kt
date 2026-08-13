package cc.netwokx.ffsms.data.repo

import cc.netwokx.ffsms.data.db.GroupDao
import cc.netwokx.ffsms.data.db.GroupEntity
import cc.netwokx.ffsms.data.db.GroupWithCount
import cc.netwokx.ffsms.data.db.RecipientDao
import cc.netwokx.ffsms.data.db.RecipientEntity
import cc.netwokx.ffsms.domain.phone.PhoneNormalizer
import kotlinx.coroutines.flow.Flow

/** Rohe Eingabe fuer den Import: Nummer plus optionaler Anzeigename. */
data class RawContact(val number: String, val displayName: String? = null)

/**
 * Ergebnis eines Imports - die Zahlen werden dem Benutzer angezeigt.
 * "3 Duplikate uebersprungen" ist eine Anforderung, kein Debug-Output.
 */
data class ImportResult(
    val added: Int,
    val duplicates: Int,
    val invalid: List<InvalidNumber>,
) {
    data class InvalidNumber(val raw: String, val reason: String)

    val hasProblems: Boolean get() = duplicates > 0 || invalid.isNotEmpty()
}

class GroupRepository(
    private val groupDao: GroupDao,
    private val recipientDao: RecipientDao,
    private val normalizer: PhoneNormalizer = PhoneNormalizer(),
) {

    fun observeGroups(): Flow<List<GroupWithCount>> = groupDao.observeGroups()

    fun observeRecipients(groupId: Long): Flow<List<RecipientEntity>> =
        recipientDao.observeForGroup(groupId)

    suspend fun findGroup(id: Long): GroupEntity? = groupDao.findById(id)

    suspend fun createGroup(name: String): Long =
        groupDao.insert(GroupEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renameGroup(id: Long, name: String) = groupDao.rename(id, name.trim())

    suspend fun deleteGroup(id: Long) = groupDao.delete(id)

    suspend fun deleteRecipient(id: Long) = recipientDao.delete(id)

    suspend fun countValidRecipients(groupId: Long): Int = recipientDao.countValid(groupId)

    suspend fun validRecipients(groupId: Long): List<RecipientEntity> =
        recipientDao.validForGroup(groupId)

    /**
     * Importiert Kontakte in eine Gruppe.
     *
     * Ablauf je Eintrag:
     *  1. Normalisierung nach E.164 (Default-Region AT)
     *  2. Duplikatpruefung innerhalb DIESES Imports (dieselbe Nummer kann im
     *     Kontaktpicker mehrfach ausgewaehlt sein, etwa als "mobil" und "privat")
     *  3. Insert mit OnConflict IGNORE - der UNIQUE-Index faengt Duplikate ab,
     *     die bereits in der Gruppe stehen
     *
     * Ungueltige Nummern werden NICHT stillschweigend verworfen: sie landen
     * als valid = 0 in der Gruppe, werden in der Liste markiert und beim
     * Versand ausgeschlossen. So sieht der Betreiber, dass ein Kontakt
     * ueberhaupt vorhanden, aber nicht erreichbar ist.
     */
    suspend fun importContacts(groupId: Long, contacts: List<RawContact>): ImportResult {
        var added = 0
        var duplicates = 0
        val invalid = mutableListOf<ImportResult.InvalidNumber>()
        val seenInThisImport = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        for (contact in contacts) {
            val normalized = normalizer.normalize(contact.number)
            val key = normalized.storageValue

            if (!seenInThisImport.add(key)) {
                duplicates++
                continue
            }

            val rowId = recipientDao.insertIgnoringDuplicates(
                RecipientEntity(
                    groupId = groupId,
                    msisdn = key,
                    displayName = contact.displayName?.trim()?.takeIf { it.isNotEmpty() },
                    valid = normalized.isValid,
                    addedAt = now,
                ),
            )

            // Erst einfuegen, dann bewerten: eine Nummer, die schon in der
            // Gruppe steht, ist ein Duplikat - egal ob sie gueltig waere.
            when {
                rowId == -1L -> duplicates++
                normalized.isValid -> added++
                else -> invalid += ImportResult.InvalidNumber(
                    raw = contact.number.trim(),
                    reason = normalized.problem ?: "Unbekannter Fehler",
                )
            }
        }

        return ImportResult(added, duplicates, invalid)
    }
}
