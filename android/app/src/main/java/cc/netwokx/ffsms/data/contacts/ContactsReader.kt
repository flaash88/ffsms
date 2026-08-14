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
)

/**
 * Liest Telefonnummern aus dem Adressbuch.
 *
 * Bewusst ein eigener Auswahldialog statt ACTION_PICK: der System-Picker gibt
 * immer nur EINEN Kontakt zurueck. Fuer einen Verteiler mit 30 Personen waeren
 * das 30 Durchlaeufe. Der eigene Dialog erlaubt echte Mehrfachauswahl.
 *
 * Gelesen wird nur beim Oeffnen des Dialogs, es wird nichts zwischengespeichert
 * und nichts synchronisiert.
 */
class ContactsReader(private val context: Context) {

    suspend fun loadPhoneContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
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
            null,
            null,
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
        result
    }
}
