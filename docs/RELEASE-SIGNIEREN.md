# Release-APK signieren und Updates ermöglichen

Diese Anleitung erklärt Schritt für Schritt, was ein Keystore ist, warum er
gebraucht wird und wie die Update-Kette aufgesetzt wird.

---

## Warum überhaupt?

Jedes Android-APK ist digital signiert. Die Signatur beantwortet die Frage:
**„Stammt dieses Update von demselben, der die installierte App gebaut hat?"**
Android erlaubt ein Update nur dann, wenn die Signatur übereinstimmt. Sonst
könnte jeder eine manipulierte Version über eine bestehende App schieben.

Der **Keystore** ist die Datei, die den privaten Schlüssel dafür enthält. Er
ist nichts weiter als eine Datei mit einem Passwort davor.

Bisher wurde ein **Debug-APK** verwendet. Das ist mit einem Wegwerf-Schlüssel
signiert, den Android automatisch erzeugt — geeignet zum Ausprobieren,
untauglich für Updates: der Schlüssel ist auf jedem Rechner ein anderer.

**Daraus folgt eine unangenehme, aber unvermeidbare Konsequenz:** Der Wechsel
vom Debug- auf das Release-APK erfordert einmal **Deinstallieren und neu
installieren**. Dabei gehen Verteiler und Verlauf verloren. Deshalb dieser
Schritt, **bevor** die echten Empfängerlisten eingepflegt werden — danach wäre
es echte Arbeit.

> **Die Keystore-Datei und ihr Passwort sind unersetzlich.** Gehen sie
> verloren, kann nie wieder ein Update über die installierte App gelegt
> werden — auf allen Geräten wäre Deinstallieren und Neuinstallieren nötig,
> inklusive Datenverlust. Beides gehört in den Passwortmanager der Feuerwehr
> und **nicht nur** auf den Laptop, der gerade zufällig da war.

---

## Schritt 1 — Keystore erzeugen

Auf deinem Rechner (nicht auf dem Server), im Projektverzeichnis:

```bash
cd android

keytool -genkeypair -v \
  -keystore ff-sms-tool.jks \
  -alias ffsms \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype JKS
```

`keytool` gehört zum JDK. Fehlt es: `sudo apt install default-jdk`.

Es folgen mehrere Fragen:

| Frage | Antwort |
|---|---|
| *Enter keystore password* | Ein Passwort ausdenken. **Merken.** Beim Tippen erscheint nichts — das ist normal. |
| *Re-enter new password* | Dasselbe nochmal |
| *What is your first and last name?* | z. B. `FF Kuehwiesen` |
| *Organizational unit / Organization* | z. B. `Feuerwehr` |
| *City / State / Country code* | Ort, Bundesland, `AT` |
| *Is CN=… correct?* | `ja` bzw. `yes` |

`-validity 10000` sind rund 27 Jahre. Läuft der Schlüssel ab, sind keine
Updates mehr möglich — deshalb großzügig.

Ergebnis: die Datei `android/ff-sms-tool.jks`. Sie steht in `.gitignore` und
darf **nicht** ins Git.

---

## Schritt 2 — Als GitHub-Secrets hinterlegen

Damit die CI das APK signieren kann, braucht sie den Keystore. Eine Binärdatei
lässt sich nicht direkt als Secret speichern, deshalb wird sie in Text
umgewandelt:

```bash
base64 -w0 ff-sms-tool.jks
```

Das ergibt eine sehr lange Zeile. **Komplett** kopieren (keine Zeilenumbrüche,
nichts abschneiden).

> Auf macOS heißt der Befehl `base64 -i ff-sms-tool.jks | tr -d '\n'`.

Dann im Browser:

**GitHub → Repository `flaash88/ffsms` → Settings → Secrets and variables →
Actions → New repository secret**

Vier Secrets anlegen:

| Name | Wert |
|---|---|
| `KEYSTORE_BASE64` | die lange Zeile von oben |
| `KEYSTORE_PASSWORD` | das Keystore-Passwort aus Schritt 1 |
| `KEY_ALIAS` | `ffsms` |
| `KEY_PASSWORD` | dasselbe Passwort wie `KEYSTORE_PASSWORD` |

> Zu `KEY_PASSWORD`: `keytool` fragt bei neueren Java-Versionen nur nach
> *einem* Passwort und verwendet es für beides. Falls du doch nach einem
> „key password" gefragt wurdest und ein anderes eingegeben hast, dann dieses
> hier eintragen.

Secrets sind nach dem Speichern nicht mehr lesbar — auch nicht für dich. Nur
überschreiben ist möglich. Deshalb: Passwort vorher sichern.

---

## Schritt 3 — Release-APK bauen lassen

Irgendeine Änderung unter `android/` pushen, oder den Workflow von Hand
starten:

**GitHub → Actions → Android → Run workflow**

Danach liegt am Lauf ein zusätzliches Artefakt **`ff-sms-tool-release`** mit
zwei Dateien:

- `app-release.apk` — das signierte APK
- `release.json` — Versionsangaben für den Update-Kanal

Erscheint das Artefakt nicht, hat die CI kein `KEYSTORE_BASE64` gefunden. Im
Job-Log steht dann `Kein KEYSTORE_BASE64-Secret gesetzt - Release wird
uebersprungen.`

---

## Schritt 4 — Auf die Geräte bringen

**Einmalig, mit Datenverlust** (deshalb jetzt und nicht später):

1. Auf jedem Gerät die alte App **deinstallieren** — Einstellungen → Apps →
   FF-SMS-Tool → Deinstallieren.
2. `app-release.apk` installieren. Am saubersten per USB, weil dabei Androids
   Sperre „Eingeschränkte Einstellungen" gar nicht erst entsteht:
   ```bash
   adb install -r app-release.apk
   ```
   Alternativ per Dateimanager — dann greift die Sperre und muss wie in der
   README beschrieben aufgehoben werden.
3. In der App Backend-URL, API-Key und Geräte-Kennung eintragen, Verteiler neu
   anlegen.

Ab jetzt laufen alle weiteren Updates ohne Deinstallieren.

---

## Schritt 5 — Update-Kanal befüllen

Damit die App etwas zum Finden hat:

```bash
scp app-release.apk release.json dein-server:~/ffsms/backend/updates/
```

Prüfen, ob der Server es sieht:

```bash
curl -s -H "X-API-Key: <API_KEY>" \
  https://ffsms.networkx.cc/api/v1/update/manifest
```

Erwartet wird etwas wie:

```json
{
  "version_code": 1,
  "version_name": "1.0.0",
  "size_bytes": 17515636,
  "sha256": "6c7e23...",
  "notes": null,
  "released_at": "2026-08-14T07:14:32.000Z"
}
```

In der App zeigt **Einstellungen → App-Version → Nach Update suchen** jetzt
„Die App ist aktuell" — richtig so, weil installierte und angebotene Version
identisch sind.

---

## Ab jetzt: ein Update ausrollen

**1. Versionsnummer erhöhen** in `android/app/build.gradle.kts`:

```kotlin
versionCode = 2          // MUSS steigen, sonst passiert nichts
versionName = "1.0.1"    // nur zur Anzeige
```

`versionCode` ist die Zahl, die die App vergleicht. Wird sie vergessen, meldet
die App weiterhin „aktuell", egal was auf dem Server liegt.

**2. Pushen.** Die CI baut und legt `ff-sms-tool-release` bereit.

**3. Artefakt herunterladen, entpacken, auf den Server kopieren:**

```bash
scp app-release.apk release.json dein-server:~/ffsms/backend/updates/
```

Kein Neustart nötig — das Backend liest die Dateien bei jeder Abfrage frisch.

**4. Optional einen Hinweis für die Kollegen ergänzen** — direkt auf dem
Server:

```bash
nano ~/ffsms/backend/updates/release.json
```

```json
{ "version_code": 2, "version_name": "1.0.1", "notes": "Zählt jetzt auch Umlaute richtig." }
```

**5. Fertig.** Binnen eines Tages bekommt jedes Gerät die Meldung „Update
verfügbar". Der Kollege tippt auf **Einstellungen → App-Version →
Herunterladen und installieren**, Android fragt einmal nach, fertig.

Wer nicht warten will, tippt in der App auf **Nach Update suchen**.

---

## Was dabei nicht geht

**Vollautomatisch ohne jeden Tastendruck.** Android verlangt bei jeder App
außerhalb des Play Store die Bestätigung des Benutzers. Umgehen ließe sich das
nur, wenn die App Geräteeigentümer eines zentral verwalteten Geräts wäre — das
setzt ein zurückgesetztes Handy und eine MDM-Einrichtung voraus und steht in
keinem Verhältnis zu zwei Feuerwehrhandys.

**Der Punkt ist trotzdem erreicht:** Du musst nicht mehr zum Kollegen fahren.

---

## Fehlersuche

| Symptom | Ursache |
|---|---|
| Kein `ff-sms-tool-release`-Artefakt | `KEYSTORE_BASE64` fehlt oder ist abgeschnitten |
| CI-Fehler „keystore password was incorrect" | `KEYSTORE_PASSWORD` bzw. `KEY_PASSWORD` stimmt nicht |
| Android bricht Installation mit Signaturfehler ab | Auf dem Gerät liegt noch das Debug-APK — einmal deinstallieren |
| App meldet „Die App ist aktuell" trotz neuem APK | `versionCode` nicht erhöht, oder die alte `release.json` liegt noch auf dem Server |
| „Prüfsumme stimmt nicht" | APK unvollständig übertragen — `scp` wiederholen |
| HTTP 404 beim Prüfen | `app-release.apk` oder `release.json` fehlt in `~/ffsms/backend/updates/` |
