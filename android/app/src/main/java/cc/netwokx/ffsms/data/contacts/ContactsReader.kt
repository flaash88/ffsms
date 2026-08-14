package cc.netwokx.ffsms.data.contacts

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val contactId: String,
    val displayName: String,
    val number: String,
    val label: String?,
) {
    /**
     * Eindeutiger Schluessel fuer Listen.
     *
     * Muss wirklich eindeutig sein: Compose wirft beim Scrollen eine Exception,
     * wenn zwei Eintraege denselben Schluessel haben. Genau das passiert, wenn
     * derselbe Kontakt ueber zwei Konten synchronisiert wird - Google und SIM
     * liefern dann dieselbe Nummer zweimal.
     */
    val key: String get() = "$contactId|$number"
}

/** Eine Kontaktgruppe (in Google Kontakte heissen sie "Labels"). */
data class DeviceContactGroup(
    val id: Long,
    val title: String,
    val accountName: String?,
    val memberCount: Int,
)

/**
 * Liest Telefonnummern und Kontaktgruppen aus dem Adressbuch.
 *
 * Bewusst ein eigener Auswahldialog statt ACTION_PICK: der System-Picker gibt
 * immer nur EINEN Kontakt zurueck. Fuer einen Verteiler mit 30 Personen waeren
 * das 30 Durchlaeufe.
 *
 * Gelesen wird nur auf ausdrueckliche Anforderung, es wird nichts
 * zwischengespeichert und nichts an das Backend uebertragen.
 */
class ContactsReader(private val context: Context) {

    suspend fun loadPhoneContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        queryPhones(selection = null, args = null)
    }

    /**
     * Kontaktgruppen, die als Verteiler taugen.
     *
     * Herausgefiltert werden Systemgruppen ("My Contacts", "Starred in
     * Android"), leere Gruppen und geloeschte. Was uebrig bleibt, sind die
     * selbst angelegten Labels - genau das, was in Google Kontakte gepflegt
     * wird.
     */
    suspend fun loadContactGroups(): List<DeviceContactGroup> = withContext(Dispatchers.IO) {
        val counts = memberCountsByGroup()

        val projection = arrayOf(
            ContactsContract.Groups._ID,
            ContactsContract.Groups.TITLE,
            ContactsContract.Groups.ACCOUNT_NAME,
            ContactsContract.Groups.AUTO_ADD,
            ContactsContract.Groups.FAVORITES,
        )

        val result = mutableListOf<DeviceContactGroup>()
        context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            projection,
            "${ContactsContract.Groups.DELETED} = 0",
            null,
            "${ContactsContract.Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
            val titleIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
            val accountIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups.ACCOUNT_NAME)
            val autoAddIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups.AUTO_ADD)
            val favIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups.FAVORITES)

            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIdx) ?: continue
                // Systemgruppen tragen einen technischen Titel und sind als
                // Verteiler wertlos.
                if (title.startsWith("System Group:")) continue
                if (cursor.getInt(autoAddIdx) == 1 || cursor.getInt(favIdx) == 1) continue

                val id = cursor.getLong(idIdx)
                val count = counts[id] ?: 0
                if (count == 0) continue

                result += DeviceContactGroup(
                    id = id,
                    title = title,
                    accountName = cursor.getString(accountIdx),
                    memberCount = count,
                )
            }
        }
        // Dieselbe Gruppe kann ueber mehrere Konten auftauchen.
        result.distinctBy { it.title to it.accountName }
    }

    /** Alle Telefonnummern der Mitglieder einer Kontaktgruppe. */
    suspend fun loadContactsInGroup(groupId: Long): List<DeviceContact> =
        withContext(Dispatchers.IO) {
            val contactIds = contactIdsInGroup(groupId)
            if (contactIds.isEmpty()) return@withContext emptyList()

            // IN-Liste statt Einzelabfragen: bei 40 Mitgliedern waeren das sonst
            // 40 Datenbankzugriffe.
            val placeholders = contactIds.joinToString(",") { "?" }
            queryPhones(
                selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($placeholders)",
                args = contactIds.map { it.toString() }.toTypedArray(),
            )
        }

    private fun contactIdsInGroup(groupId: Long): List<Long> {
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID),
            "${ContactsContract.Data.MIMETYPE} = ? AND " +
                "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
            arrayOf(
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                groupId.toString(),
            ),
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            while (cursor.moveToNext()) ids += cursor.getLong(idIdx)
        }
        return ids.toList()
    }

    private fun memberCountsByGroup(): Map<Long, Int> {
        val counts = mutableMapOf<Long, MutableSet<Long>>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,
                ContactsContract.Data.CONTACT_ID,
            ),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val groupIdx = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,
            )
            val contactIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            while (cursor.moveToNext()) {
                counts.getOrPut(cursor.getLong(groupIdx)) { mutableSetOf() }
                    .add(cursor.getLong(contactIdx))
            }
        }
        return counts.mapValues { it.value.size }
    }

    private fun queryPhones(selection: String?, args: Array<String>?): List<DeviceContact> {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )

        val result = mutableListOf<DeviceContact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIdx) ?: continue
                result += DeviceContact(
                    contactId = cursor.getString(idIdx).orEmpty(),
                    displayName = cursor.getString(nameIdx) ?: number,
                    number = number,
                    label = ContactsContract.CommonDataKinds.Phone
                        .getTypeLabel(context.resources, cursor.getInt(typeIdx), cursor.getString(labelIdx))
                        ?.toString(),
                )
            }
        }

        // Ist derselbe Kontakt ueber zwei Konten synchronisiert, liefert Android
        // dieselbe Nummer mehrfach. In der Liste waeren das doppelte Schluessel,
        // und Compose bricht dann beim Scrollen mit einer Exception ab.
        return result.distinctBy { it.key }
    }
}
