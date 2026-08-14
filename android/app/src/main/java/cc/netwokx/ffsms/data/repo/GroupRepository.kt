package cc.netwokx.ffsms.data.repo

import cc.netwokx.ffsms.data.db.GroupDao
import cc.netwokx.ffsms.data.db.GroupEntity
import cc.netwokx.ffsms.data.db.GroupWithCount
import cc.netwokx.ffsms.data.db.RecipientDao
import cc.netwokx.ffsms.data.db.RecipientEntity
import cc.netwokx.ffsms.data.contacts.ContactsReader
import cc.netwokx.ffsms.data.contacts.DeviceContactGroup
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

/**
 * Ergebnis eines Abgleichs mit einer Kontaktgruppe.
 *
 * @param added neu hinzugekommene Empfaenger
 * @param missing Empfaenger, die nicht mehr in der Kontaktgruppe stehen
 * @param unchanged unveraendert uebernommene
 * @param skipped leer, wenn der Abgleich lief; sonst der Grund fuer den Abbruch
 */
data class ContactSyncResult(
    val added: Int,
    val missing: Int,
    val unchanged: Int,
    val skipped: String? = null,
) {
    val ran: Boolean get() = skipped == null
}

class GroupRepository(
    private val groupDao: GroupDao,
    private val recipientDao: RecipientDao,
    private val contactsReader: ContactsReader,
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

    // --- Kontaktgruppen ---------------------------------------------------

    suspend fun availableContactGroups(): List<DeviceContactGroup> =
        contactsReader.loadContactGroups()

    /** Verknuepft einen Verteiler mit einer Kontaktgruppe und gleicht sofort ab. */
    suspend fun linkContactGroup(
        groupId: Long,
        contactGroup: DeviceContactGroup,
        autoSync: Boolean,
    ): ContactSyncResult {
        groupDao.linkContactGroup(groupId, contactGroup.id, contactGroup.title, autoSync)
        return syncContactGroup(groupId)
    }

    suspend fun unlinkContactGroup(groupId: Long) {
        groupDao.linkContactGroup(groupId, null, null, false)
        // Die Empfaenger bleiben - sie wurden ja bewusst aufgenommen. Nur die
        // Verknuepfung geht weg.
        recipientDao.allForGroup(groupId)
            .filter { it.missingInContactGroup }
            .forEach { recipientDao.setMissing(it.id, false) }
    }

    suspend fun setAutoSync(groupId: Long, enabled: Boolean) =
        groupDao.setAutoSync(groupId, enabled)

    suspend fun countMissing(groupId: Long): Int = recipientDao.countMissing(groupId)

    /** Entfernt die als "nicht mehr in der Kontaktgruppe" markierten Empfaenger. */
    suspend fun removeMissing(groupId: Long) = recipientDao.deleteMissing(groupId)

    /**
     * Gleicht einen Verteiler mit seiner Kontaktgruppe ab.
     *
     * Neue Mitglieder kommen automatisch dazu - das ist der Zweck der Uebung.
     * Wer nicht mehr in der Kontaktgruppe steht, wird NUR MARKIERT und nicht
     * entfernt: ein Verteiler, der sich von allein leert, weil die
     * Kontakte-Synchronisation gerade klemmt oder ein Konto abgemeldet wurde,
     * waere bei einer Alarmierung der schlimmste denkbare Fehler. Das
     * Entfernen bleibt eine bewusste Entscheidung.
     *
     * Aus demselben Grund bricht der Abgleich ab, wenn die Kontaktgruppe
     * ueberhaupt keine Mitglieder mehr liefert - das ist fast immer ein
     * technisches Problem und keine echte Aenderung.
     */
    suspend fun syncContactGroup(groupId: Long): ContactSyncResult {
        val group = groupDao.findById(groupId)
            ?: return ContactSyncResult(0, 0, 0, "Verteiler nicht gefunden")
        val contactGroupId = group.contactGroupId
            ?: return ContactSyncResult(0, 0, 0, "Keine Kontaktgruppe verknuepft")

        val members = contactsReader.loadContactsInGroup(contactGroupId)
        val existing = recipientDao.allForGroup(groupId)

        if (members.isEmpty() && existing.isNotEmpty()) {
            return ContactSyncResult(
                0, 0, existing.size,
                "Kontaktgruppe lieferte keine Mitglieder - Abgleich uebersprungen",
            )
        }

        val normalizedMembers = members
            .map { it to normalizer.normalize(it.number) }
            .associate { (contact, normalized) -> normalized.storageValue to contact }

        var added = 0
        val now = System.currentTimeMillis()

        for ((msisdn, contact) in normalizedMembers) {
            val normalized = normalizer.normalize(contact.number)
            val rowId = recipientDao.insertIgnoringDuplicates(
                RecipientEntity(
                    groupId = groupId,
                    msisdn = msisdn,
                    displayName = contact.displayName,
                    valid = normalized.isValid,
                    addedAt = now,
                    fromContactGroup = true,
                ),
            )
            if (rowId != -1L) added++
        }

        var missing = 0
        for (recipient in existing) {
            val stillThere = normalizedMembers.containsKey(recipient.msisdn)
            if (!stillThere && recipient.fromContactGroup) {
                if (!recipient.missingInContactGroup) recipientDao.setMissing(recipient.id, true)
                missing++
            } else if (stillThere && recipient.missingInContactGroup) {
                // Wieder aufgetaucht - Markierung zuruecknehmen.
                recipientDao.setMissing(recipient.id, false)
            }
        }

        groupDao.markSynced(groupId, now)
        return ContactSyncResult(
            added = added,
            missing = missing,
            unchanged = normalizedMembers.size - added,
        )
    }

    /** Alle Verteiler mit eingeschaltetem Abgleich. Fuer den taeglichen Worker. */
    suspend fun autoSyncAll(): Map<String, ContactSyncResult> =
        groupDao.autoSyncGroups().associate { it.name to syncContactGroup(it.id) }

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
