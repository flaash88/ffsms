# FF-SMS-Tool

SMS-Verteiler für eine Freiwillige Feuerwehr, bestehend aus zwei Teilen:

| Teil | Verzeichnis | Zweck |
|---|---|---|
| **Android-App** | `android/` | Versendet eine SMS an eine Empfängergruppe. Verteilung als selbst signiertes APK per Sideload. |
| **Backend** | `backend/` | Nimmt reine Verbrauchszahlen entgegen, alarmiert sofort bei Auffälligkeiten und schickt sonntags einen Wochenreport per ntfy. |

Das Projekt ist um eine einzige Frage herum gebaut: **Wie verhindert man, dass
aus einer Aussendung an 30 Leute versehentlich 2100 verrechnete SMS werden —
und wie sieht man es sofort, wenn es doch passiert?**

---

> **Zum Einrichten:** [docs/SERVER-EINRICHTEN.md](docs/SERVER-EINRICHTEN.md)
> (Debian, Docker, ntfy, Cloudflare Tunnel) und
> [docs/RELEASE-SIGNIEREN.md](docs/RELEASE-SIGNIEREN.md) (Keystore, signiertes
> APK, Updates ausrollen). Beide sind Schritt-für-Schritt-Anleitungen zum
> Abarbeiten; diese README erklaert dahinter das Warum.

## Inhalt

- [Warum diese Bauweise](#warum-diese-bauweise)
- [Schutz gegen Mehrfachversand](#schutz-gegen-mehrfachversand)
- [Datenschutz](#datenschutz)
- [Android-App bauen](#android-app-bauen)
- [Release-APK signieren](#release-apk-signieren)
- [APK herunterladen](#apk-herunterladen)
- [Sideload-Installation](#sideload-installation)
- [Backend-Deployment](#backend-deployment)
- [.env-Felder](#env-felder)
- [ntfy-Einrichtung](#ntfy-einrichtung)
- [API](#api)
- [Updates ausrollen](#updates-ausrollen)
- [Abgleich mit der Providerrechnung](#abgleich-mit-der-providerrechnung)
- [Tests](#tests)
- [Abweichungen von der Vorgabe](#abweichungen-von-der-vorgabe)

---

## Warum diese Bauweise

Ein Vorfall mit rund 2100 verrechneten SMS hat zwei plausible Ursachen:
**Doppelversand** oder **UCS-2-Encoding**. Beide werden hier gezielt adressiert.

**UCS-2.** Eine SMS fasst 160 Zeichen — aber nur, solange jedes Zeichen im
GSM-7-Alphabet steht. Ein einziges Zeichen außerhalb (typografischer
Apostroph `’`, Gedankenstrich `–`, Auslassungspunkte `…`, ein Emoji) kippt die
**gesamte** Nachricht auf UCS-2, und dann passen nur noch 70 Zeichen hinein,
bei mehrteiligen Nachrichten sogar nur 67. Aus 2 Segmenten werden so schnell 4.
Bei 30 Empfängern sind das 60 zusätzliche SMS für ein einziges Zeichen, das
das Handy beim Tippen automatisch eingesetzt hat.

Wichtig und häufig falsch angenommen: **ä ö ü ß Ä Ö Ü stehen im
GSM-7-Basisalphabet.** Sie kosten 1 Byte und lösen *kein* UCS-2 aus. Auch das
Euro-Zeichen ist unproblematisch — es kostet 2 Bytes, kippt die Nachricht aber
nicht.

Die App rechnet deshalb bei jedem Tastendruck mit und benennt die
verantwortlichen Zeichen im Klartext:

```
Encoding: UCS-2  ⚠ Sonderzeichen erkannt
248 Zeichen → 4 Segmente
× 15 Empfänger = 60 SMS
```

Ein Knopf „Text bereinigen" ersetzt die typografischen Zeichen durch ihre
GSM-7-Entsprechung und entfernt Emojis. Die Anzeige springt danach sichtbar auf
GSM-7 zurück, und die App sagt vorher, wie viele SMS das spart.

**Doppelversand.** Dazu der eigene Abschnitt weiter unten — das ist der Teil,
der wirklich zählt.

---

## Schutz gegen Mehrfachversand

Fünf Mechanismen, die unabhängig voneinander greifen. Keiner davon verlässt
sich darauf, dass ein anderer funktioniert.

**1. Die Campaign-UUID entsteht beim Öffnen des Bestätigungsdialogs.**
Nicht beim Klick auf „Senden". Ein Doppeltipp auf den Senden-Knopf verwendet
damit zweimal dieselbe UUID — und läuft in Sperre 2.

**2. `enqueueUniqueWork(campaignId, ExistingWorkPolicy.KEEP)`.**
Eine zweite Einreihung derselben Kampagne wird vom WorkManager verworfen.
Gesendet wird ausschließlich aus einem `OneTimeWorkRequest`, nie aus einer
Activity, einem ViewModel-Scope oder `onCreate`. Bildschirmdrehung und
App-Kill lösen deshalb keinen zweiten Versand aus.

**3. `UNIQUE(campaign_id, msisdn)` in der Tabelle `send_log`.**
Der Worker fügt die Zeile **vor** dem Absetzen der SMS ein. Liefert das Insert
`-1` zurück, wurde diese Nummer in dieser Kampagne bereits bedient und wird
übersprungen. Das hält auch dann, wenn der Prozess mitten im Versand
abgeschossen und der Worker vom System neu gestartet wird.

**4. Harte Obergrenzen.**
300 Segmente je Aussendung, 1000 Segmente pro Kalendertag. In den Einstellungen
anpassbar, aber nicht abschaltbar (Werte ≤ 0 werden auf 1 angehoben). Bei
Überschreitung bricht der Worker ab, protokolliert den Grund und meldet ihn ans
Backend. Es gibt **keinen** „trotzdem weiter"-Pfad im Code. Das Tageslimit wird
vor *jedem einzelnen Empfänger* neu geprüft, nicht nur einmal vorab — sonst
könnte eine parallel laufende zweite Kampagne daran vorbeirutschen.

**5. Kein automatischer Retry.**
Der Worker liefert nie `Result.retry()`. Der Statusreceiver sendet nie nach.
Fehlgeschlagene Empfänger werden markiert und können nur **einzeln und von
Hand** erneut angestoßen werden, abgesichert durch ein bedingtes UPDATE auf
Status und Versuchszähler. Ein automatischer Retry, der nach einem
Teilversand von vorne beginnt, ist die wahrscheinlichste Ursache des
2100er-Vorfalls.

Dazu kommt: Nummern werden beim Import per libphonenumber nach **E.164**
normalisiert. Ohne das würden `0664 1234567`, `+43 664 1234567` und
`0043-664-1234567` als drei verschiedene Empfänger gelten und dieselbe Person
dreimal eine SMS bekommen. Die Normalisierung ist Teil des Schutzes, nicht
Kosmetik.

Der Bestätigungsdialog nennt die Gesamtzahl dreimal — im Fließtext, in einer
Pflicht-Checkbox und auf dem Bestätigungsknopf:

```
Es werden 60 SMS verrechnet.

15 Empfänger × 4 Segmente je Nachricht (UCS2).

Verteiler: Aktivmannschaft

Achtung: Durch Sonderzeichen wird UCS-2 verwendet.
Mit „Text bereinigen" ließen sich 30 SMS sparen.

[ ] Ich habe die Zahl 60 geprüft
                              [ Abbrechen ] [ 60 SMS jetzt senden ]
```

Der Knopf ist erst aktiv, wenn die Checkbox gesetzt ist. Ein reines „OK" gibt
es nicht.

---

## Datenschutz

Der Betreiber des Geräts hat der Übertragung der Zahlen zugestimmt. Es geht um
Kostenkontrolle, nicht um Überwachung. Der Sync ist **standardmäßig
ausgeschaltet** und muss in den Einstellungen bewusst eingeschaltet werden.

**Übertragen wird:**

| Feld | Beispiel |
|---|---|
| Geräte-Kennung | `ff-kuehwiesen-sms` (frei gewählt, keine IMEI, keine Rufnummer) |
| Kennung der Aussendung | Zufalls-UUID, nur zur Doppelzählungsvermeidung |
| Zeitpunkt | `2026-08-13T18:22:00+02:00` |
| Anzahl Empfänger | `15` |
| Segmente je Nachricht / gesamt | `3` / `45` |
| Encoding | `GSM7` oder `UCS2` |
| Anzahl Fehlschläge | `0` |
| Abbruchgrund | `limit_daily` oder `null` |

**Niemals übertragen wird:** Nachrichtentext, Telefonnummern, Kontaktnamen,
Textfragmente, Hashes des Textes, Standortdaten, Gruppennamen.

Das ist nicht nur zugesagt, sondern technisch abgesichert:

- Das Upload-Modell (`CampaignUploadDto`) hat für diese Felder keine Slots —
  auch nicht optional, auch nicht auskommentiert.
- Das Backend läuft mit `whitelist: true, forbidNonWhitelisted: true`. Ein
  Datensatz mit einem unbekannten Feld wird **komplett mit HTTP 400 abgelehnt**,
  nicht still beschnitten. Selbst wenn eine künftige App-Version versehentlich
  Text mitschicken würde, landet er nicht in der Datenbank.
- `aborted_reason` ist auf `[a-z0-9_]` begrenzt, damit das Feld nicht als
  Umweg für freien Text taugt.
- Ein Integrationstest weist nach, dass Datensätze mit `message_text`,
  `recipients_msisdn` und `text_hash` abgelehnt werden.

Verteilerlisten, Nummern, Kontaktnamen und Nachrichtentexte bleiben
ausschließlich in der lokalen Datenbank der App. Cloud-Backup und
Geräte-zu-Geräte-Übertragung sind für die App abgeschaltet, damit diese Daten
nicht über einen Google-Backup-Umweg das Gerät verlassen.

Die App enthält kein Firebase, keine Analytics, kein Crash-Reporting und keine
Werbe-SDKs. Der Screen **Einstellungen → Datenschutz** listet all das in der
App selbst auf.

---

## Android-App bauen

**Voraussetzungen:** JDK 17, Android SDK mit API 34.

```bash
cd android
./gradlew testDebugUnitTest      # Unit-Tests (Segmentberechnung, CSV-Export)
./gradlew assembleDebug          # APK zum Ausprobieren
```

Das Debug-APK liegt danach unter
`android/app/build/outputs/apk/debug/app-debug.apk` und hat die Application-ID
`cc.netwokx.ffsms.debug` — es lässt sich also parallel zur echten App
installieren.

---

## Release-APK signieren

Die App wird **nicht über den Play Store** verteilt: Google gibt die
Berechtigung `SEND_SMS` nur noch Standard-SMS-Apps. Für die Verteilung als
selbst signiertes APK innerhalb der Feuerwehr ist das unproblematisch.

**1. Keystore einmalig erzeugen**

```bash
cd android
keytool -genkeypair -v \
  -keystore ff-sms-tool.jks \
  -alias ffsms \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype JKS
```

`keytool` fragt nach einem Passwort und ein paar Angaben zur Organisation.
Die Angaben sind frei wählbar; das Passwort ist wichtig.

> **Die `.jks`-Datei und ihr Passwort sicher aufbewahren** (z. B. im
> Passwortmanager der Feuerwehr, nicht nur auf dem Laptop, der das APK gebaut
> hat). Geht der Keystore verloren, lässt sich kein Update mehr über die
> installierte App legen — sie müsste deinstalliert werden, und dabei gehen
> die lokalen Verteilerlisten und der Verlauf verloren.

**2. `keystore.properties` anlegen**

```bash
cp keystore.properties.example keystore.properties
```

und ausfüllen:

```properties
storeFile=ff-sms-tool.jks
storePassword=DEIN_PASSWORT
keyAlias=ffsms
keyPassword=DEIN_PASSWORT
```

Die Datei steht in `.gitignore` und darf nicht eingecheckt werden.

**3. Bauen**

```bash
./gradlew assembleRelease
```

Ergebnis: `android/app/build/outputs/apk/release/app-release.apk`

Fehlt `keystore.properties`, baut Gradle trotzdem durch, das APK ist dann aber
unsigniert und nicht installierbar.

**4. Signatur prüfen (optional)**

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

---

## APK herunterladen

Am schnellsten über GitHub Actions — dort steht ein Android-SDK bereit, es
muss also lokal nichts installiert werden.

1. Im Repository auf **Actions** → Workflow **Android** → den obersten
   (grünen) Lauf des gewünschten Branches öffnen.
2. Ganz unten unter **Artifacts** liegt **`ff-sms-tool-debug-apk`**.
   Herunterladen und entpacken — GitHub packt Artefakte immer in ein ZIP.
3. Die enthaltene `app-debug.apk` auf das Gerät kopieren und wie unten
   beschrieben installieren.

Der Workflow läuft bei jedem Push auf `android/**` und lässt sich unter
**Actions → Android → Run workflow** auch von Hand starten.

> Das ist ein **Debug-APK**: mit Androids Debug-Schlüssel signiert, also sofort
> installierbar, aber nicht für den Dauerbetrieb gedacht. Die Application-ID
> endet auf `.debug`, es läuft daher parallel zu einer später selbst signierten
> Release-Version. Für das Gerät, das die echten Alarme versendet, das
> Release-APK nach der Anleitung oben bauen — nur so bleiben Updates über
> dieselbe Installation möglich, ohne dass Verteiler und Verlauf verlorengehen.
>
> GitHub löscht Artefakte standardmäßig nach 90 Tagen.

---

## Sideload-Installation

**Per USB vom Rechner:**

```bash
adb install -r app-release.apk
```

**Direkt am Gerät:**

1. APK auf das Gerät kopieren (USB, E-Mail an sich selbst, Nextcloud, …).
2. Datei im Dateimanager antippen.
3. Android meldet, dass Apps aus dieser Quelle nicht installiert werden dürfen
   → **Einstellungen** antippen → **Aus dieser Quelle zulassen** aktivieren
   (Android 8+: die Freigabe gilt pro App, also z. B. nur für den Dateimanager).
4. Zurück, **Installieren**.
5. Die Freigabe danach wieder abschalten — sie wird nur für die Installation
   gebraucht.

### „App wurde Zugriff verweigert" beim Erteilen der SMS-Berechtigung

Kommt beim Freigeben von **SMS** eine Meldung mit blauem Schild-Symbol und dem
Text *„App wurde Zugriff verweigert"*, ist das **nicht** die App und auch keine
Sperre des Herstellers, sondern Androids Schutz **„Eingeschränkte
Einstellungen"**. Ab Android 13 blockiert er besonders sensible Berechtigungen —
darunter SMS — für Apps, die über einen Dateimanager installiert wurden.

Freigeben:

1. **Einstellungen → Apps → FF-SMS-Tool**
2. Oben rechts das **Menü (drei Punkte ⋮)**
3. **„Eingeschränkte Einstellungen zulassen"**
4. Zurück zu **Berechtigungen → SMS → Zulassen**

Der Menüpunkt in Schritt 3 taucht nur auf, wenn Android die Sperre für diese App
tatsächlich gesetzt hat.

**Sauberer ist der Weg über USB**, weil die Sperre dabei gar nicht entsteht —
`adb` installiert sitzungsbasiert, und darauf zielt der Schutz nicht ab:

```bash
adb install -r app-debug.apk
```

Für das Gerät, das später die echten Alarme versendet, ist das der empfohlene
Weg.

**Beim ersten Start:**

1. **Verteiler** → Gruppe anlegen → Empfänger hinzufügen (aus den Kontakten
   oder von Hand). Beim ersten Kontaktzugriff fragt die App nach der
   Berechtigung; sie lässt sich ablehnen, dann funktioniert die manuelle
   Eingabe weiter.
2. **Einstellungen → Serverkonfiguration** → Backend-URL, API-Key und
   Geräte-Kennung eintragen, **Verbindung testen**, dann
   **Verbrauchszahlen übertragen** einschalten.
3. **Verfassen** → beim ersten Senden fragt die App nach `SEND_SMS`.
4. **Serverkonfiguration → PIN einrichten**, bevor das Gerät weitergegeben
   wird.

### Serverkonfiguration hinter einer PIN

Adresse, API-Key, Geräte-Kennung und der Übertragungsschalter liegen auf einem
**eigenen Bildschirm**, erreichbar über die erste Zeile in den Einstellungen.
Ist eine PIN gesetzt, kommt dort nur herein, wer sie kennt.

**In den Einstellungen bleiben offen:** Grenzwerte, App-Version samt
Update-Suche, Berechtigungen, Verbindung testen und Datenschutz. Ein Kollege
soll senden, eine fehlende Berechtigung nachreichen und prüfen können, ob der
Server erreichbar ist, ohne jemanden anzurufen.

Die eingestellte Adresse steht als Untertitel in der Zeile, obwohl der
Bildschirm dahinter gesperrt ist. Wer am Telefon gefragt wird, welcher Server
eingetragen ist, soll nachsehen können — und genau dieses Nachsehen ist der
Vorgang, bei dem sonst versehentlich etwas verstellt wird. Der Schlüssel steht
nicht dabei; er ist ein Geheimnis und kein Diagnosewert.

Warum ausgerechnet diese vier Werte: sie werden einmal eingerichtet und danach
nie wieder angefasst. Ein verstellter Grenzwert fällt beim nächsten Verfassen
auf. Eine verstellte Serveradresse fällt **gar nicht** auf — die App sendet
weiter SMS, nur die Verbrauchszahlen kommen nirgends mehr an. Bemerkt wird das
erst, wenn die Rechnung eine Zahl nennt, der man nichts mehr entgegenhalten
kann.

Die Freigabe gilt nur für den aktuellen Besuch: geht die App in den
Hintergrund, ist wieder zu. Geprüft wird beim Öffnen des Bildschirms, nicht
beim Antippen der Zeile — so bleibt die Sperre auch dann wirksam, wenn Android
den Bildschirm aus dem Backstack wiederherstellt.

> **Die PIN lässt sich nicht wiederherstellen.** Ist sie vergessen, hilft nur
> eine Neuinstallation, und dabei gehen Verteiler und Verlauf verloren. Sie
> gehört in den Passwortmanager der Feuerwehr. Der Dialog verlangt sie beim
> Einrichten zweimal — eine vertippte PIN fiele sonst erst auf, wenn sie
> gebraucht wird.

Gespeichert wird `salz:hash`, nicht die PIN. Kein Zähler für Fehlversuche und
keine Sperrzeit: die PIN hält niemanden auf, der die App zerlegen will, und
eine Sperre stünde genau dann im Weg, wenn unter Zeitdruck etwas
richtigzustellen ist.

---

## Backend-Deployment

Ein Reverse-Proxy wird nicht gebraucht; der Dienst läuft hinter Tailscale bzw.
einem Cloudflare Tunnel und bindet sich nur an `127.0.0.1:3000`.

```bash
cd backend
cp .env.example .env
$EDITOR .env                 # mindestens DB_PASSWORD, API_KEYS und NTFY_URL setzen
docker compose up -d --build
```

Prüfen:

```bash
curl -s localhost:3000/api/v1/health/live
# {"status":"ok"}

curl -s -H "X-API-Key: DEIN_KEY" localhost:3000/api/v1/health
# {"status":"ok","database":"up","version":"1.0.0"}
```

Logs und Update:

```bash
docker compose logs -f api
docker compose pull && docker compose up -d --build
```

Die Datenbank liegt im Docker-Volume `pgdata` und wird nach außen nicht
exponiert. Sicherung:

```bash
docker compose exec -T db pg_dump -U ffsms ffsms | gzip > ffsms-$(date +%F).sql.gz
```

Ohne Docker: `npm ci && npm run build && npm run start:prod`, mit einer
erreichbaren PostgreSQL-Instanz und denselben Umgebungsvariablen.

---

## .env-Felder

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `DB_USER` | – | Datenbankbenutzer, Standard `ffsms` |
| `DB_PASSWORD` | **ja** | Datenbankpasswort. Compose startet ohne nicht. |
| `DB_NAME` | – | Datenbankname, Standard `ffsms` |
| `API_KEYS` | **ja** | `kennung:schlüssel[,weitere:schlüssel]`. Mindestens 16 Zeichen je Schlüssel. |
| `NTFY_URL` | – | Vollständige Topic-URL. Fehlt sie, werden Meldungen nur ins Log geschrieben. |
| `NTFY_TOKEN` | – | Nur bei einer ntfy-Instanz mit Zugriffsschutz. |
| `ALERT_SEGMENTS_PER_CAMPAIGN` | – | Sofort-Alarm ab dieser Segmentzahl je Aussendung. Standard `150`. |
| `ALERT_SEGMENTS_PER_MONTH` | – | Sofort-Alarm ab dieser Monatssumme. Standard `1500`. |
| `TZ_REPORT` | – | Zeitzone für Reports und Monatsgrenzen. Standard `Europe/Vienna`. |
| `WEEKLY_REPORT_CRON` | – | Standard `0 20 * * 0` (sonntags 20:00 Ortszeit). |
| `REPORT_TITLE` | – | Klartextname in der Betreffzeile, z. B. `FF Kühwiesen`. |
| `DOWNLOAD_PASSWORD` | – | Passwort der Download-Seite `/download`. Leer = Seite abgeschaltet (404). |

`API_KEYS` verknüpft Schlüssel und Geräte-Kennung fest miteinander. Liefert ein
Gerät Daten unter einer anderen `device_id` ein, antwortet der Server mit 403.
Schlüssel erzeugen:

```bash
openssl rand -hex 24
```

Fehlt `API_KEYS`, **startet die Anwendung nicht**. Ein Backend, das mangels
Konfiguration versehentlich ohne Authentifizierung läuft, wäre schlimmer als
eines, das gar nicht startet.

---

## ntfy-Einrichtung

**1. Topic wählen.** Beim öffentlichen ntfy.sh gibt es keine Zugangskontrolle —
wer den Topic-Namen kennt, liest mit. Deshalb einen langen, zufälligen Namen:

```bash
echo "ff-sms-$(openssl rand -hex 8)"
# ff-sms-9c2a4f7e1b3d8a06
```

In die `.env`:

```
NTFY_URL=https://ntfy.sh/ff-sms-9c2a4f7e1b3d8a06
```

**2. App installieren.** ntfy gibt es für [Android](https://f-droid.org/packages/io.heckel.ntfy/)
und iOS. Dort denselben Topic abonnieren.

**3. Testen:**

```bash
curl -H "Title: Test" -d "Funktioniert" https://ntfy.sh/ff-sms-9c2a4f7e1b3d8a06
```

**Eigene Instanz mit Zugriffsschutz** (empfohlen, wenn ohnehin ein Server da
ist): Token in ntfy anlegen und zusätzlich `NTFY_TOKEN=tk_...` setzen. Das
Backend schickt ihn dann als `Authorization: Bearer`.

### Was gemeldet wird

**Sofort-Alarm** (Priority `high`), sobald eine der Bedingungen zutrifft:
`total_segments` über der Schwelle · Monatssumme über der Schwelle ·
`aborted_reason` gesetzt · `failed > 0`.

```
Titel: Aussendung ABGEBROCHEN (limit_daily)

Geraet: ff-kuehwiesen-sms
30 Empfaenger x 70 Segmente = 2100
Encoding: UCS2 (Sonderzeichen!)
Monat bisher: 2100 Segmente

- Abbruchgrund: limit_daily
- 2100 Segmente in einer Aussendung (Schwelle 150)
- Monat bisher 2100 Segmente (Schwelle 1500)
```

Der Titel benennt den Grund direkt — eine Push-Meldung wird auf dem
Sperrbildschirm gelesen, nicht aufgeklappt.

Ein **wiederholter Upload derselben Aussendung löst keinen zweiten Alarm aus.**
Das Gerät lädt nach einer Offline-Phase offene Einträge erneut hoch; daraus
darf kein Alarmgewitter werden.

**Wochenreport**, sonntags 20:00 Europe/Vienna:

```
Titel: FF Kuehwiesen - SMS-Woche 33

Aussendungen: 4
Empfaenger gesamt: 61
Segmente (verrechnet): 178
Encoding: 3x GSM-7, 1x UCS-2 (!)
Monat bisher: 512 Segmente
Vormonat gesamt: 634
```

Abbrüche und Fehlschläge stehen nur drin, wenn es welche gab — eine Zeile
„Abbrüche: 0" jede Woche würde nur abstumpfen.

> Umlaute werden in ntfy-Titeln zu `ae/oe/ue` umgeschrieben: HTTP-Header dürfen
> nur Latin-1 enthalten, sonst zerlegt es den Request.

---

## API

Alle Endpunkte erwarten den Header `X-API-Key`. Ausnahme: `/api/v1/health/live`.

### `POST /api/v1/campaigns`

```json
{
  "device_id": "ff-kuehwiesen-sms",
  "campaign_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "sent_at": "2026-08-13T18:22:00+02:00",
  "recipients": 15,
  "segments_per_msg": 3,
  "total_segments": 45,
  "encoding": "GSM7",
  "failed": 0,
  "aborted_reason": null
}
```

Antwort `200`:

```json
{ "campaign_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301", "duplicate": false }
```

`campaign_id` ist Primärschlüssel; wiederholte Uploads sind idempotent (Upsert).
`200` statt `201`, weil der Aufruf beliebig oft wiederholt werden darf.

Fehler: `400` bei Validierungsfehlern **oder unbekannten Feldern**, `401` bei
falschem Schlüssel, `403` wenn `device_id` nicht zum Schlüssel gehört.

### `GET /api/v1/stats?device_id=&from=&to=`

Ohne `from`/`to` wird der laufende Kalendermonat in `TZ_REPORT` genommen.

```json
{
  "device_id": "ff-kuehwiesen-sms",
  "from": "2026-07-31T22:00:00.000Z",
  "to": "2026-08-13T16:22:00.000Z",
  "campaigns": 4, "recipients": 61, "total_segments": 178,
  "failed": 0, "aborted": 0,
  "by_encoding": { "GSM7": 3, "UCS2": 1 }
}
```

### `POST /api/v1/reports/weekly`

Schickt den Wochenreport sofort per ntfy, ohne auf den Sonntag zu warten.
Kein Body, Antwort `{"sent":true}`.

```bash
curl -s -X POST -H "X-API-Key: DEIN_KEY" \
  https://ffsms.networkx.cc/api/v1/reports/weekly
```

Gedacht zum Ausprobieren und zum Nachholen, wenn der Server sonntags abend
aus war. Der Report bezieht sich auf die **laufende** Kalenderwoche — von
Hand ausgelöst zeigt er also den Stand von jetzt.

Die Alternative wäre, `WEEKLY_REPORT_CRON` kurz umzustellen und den Dienst
zweimal neu zu starten. Das birgt jedes Mal die Gefahr, die Umstellung
hinterher zu vergessen und dann jede Minute eine Push-Meldung zu bekommen —
was die Meldungen genau dann abstumpfen lässt, wenn sie zählen.

### `GET /api/v1/health`

Erreichbarkeit **und** Gültigkeit des Schlüssels. Der Endpunkt liegt bewusst
hinter der Authentifizierung: die App nutzt ihn für „Verbindung testen", und
ein Test, der auch mit falschem Schlüssel grün meldet, wäre wertlos. Für
Uptime-Monitoring ohne Schlüssel gibt es `GET /api/v1/health/live`.

### Sync-Verhalten der App

Aussendungen werden lokal in Room gequeut. Ein periodischer WorkManager-Job
(alle 30 Minuten, Constraint: Netzwerk) lädt offene Einträge hoch und markiert
sie als synchronisiert; nach jeder Aussendung wird zusätzlich sofort ein
Versuch angestoßen. **Das SMS-Handy darf beliebig lange offline sein — der
Versand hängt nie am Backend.** Ein Retry beim Upload ist ungefährlich, weil
nur Zahlen übertragen werden und der Server ein Upsert macht.

---

## Updates ausrollen

Damit ein Update nicht bei jedem Kollegen von Hand installiert werden muss,
liefert das Backend die jeweils aktuelle Version aus. Die App sieht einmal
täglich nach und meldet sich, wenn etwas bereitliegt.

**Zwei Dinge vorweg, die sich nicht wegkonfigurieren lassen:**

- **Android zeigt immer seinen Installationsdialog.** Der Kollege muss einmal
  auf „Installieren" tippen. Eine stille Installation setzt voraus, dass die App
  Geräteeigentümer eines zentral verwalteten Geräts ist — für ein einzelnes
  Feuerwehrhandy unverhältnismäßig. Hinfahren muss aber niemand mehr.
- **Die Signatur muss gleich bleiben.** Ein Update, das mit einem anderen
  Keystore signiert ist, lehnt Android ab. Wer heute ein Debug-APK installiert
  hat, muss für den Wechsel auf die Release-Signatur **einmal deinstallieren** —
  Verteiler und Verlauf sind dann weg. Deshalb: **vor dem ersten echten Einsatz
  auf das Release-APK wechseln**, nicht danach.

### Einmalige Einrichtung

**1. Keystore erzeugen** (siehe [Release-APK signieren](#release-apk-signieren)),
dann als GitHub-Secrets hinterlegen — *Settings → Secrets and variables →
Actions*:

| Secret | Wert |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 ff-sms-tool.jks` |
| `KEYSTORE_PASSWORD` | das Store-Passwort |
| `KEY_ALIAS` | `ffsms` |
| `KEY_PASSWORD` | das Key-Passwort |

Fehlen die Secrets, baut die CI weiterhin nur das Debug-APK — der Build schlägt
deswegen nicht fehl.

**2. Update-Verzeichnis auf dem Server anlegen:**

```bash
mkdir -p ~/ffsms/backend/updates
```

Es wird von `docker compose` als `/data/updates` **nur lesend** eingebunden.

**3. Backend neu starten**, damit der Mount greift:

```bash
cd ~/ffsms/backend && docker compose up -d
```

### Bei jedem Update

**1. `versionCode` erhöhen** in `android/app/build.gradle.kts` — das ist die
Zahl, die die App vergleicht. Ohne Erhöhung passiert nichts:

```kotlin
versionCode = 2
versionName = "1.0.1"
```

**2. Pushen.** Die CI baut das signierte Release-APK und legt das Artefakt
`ff-sms-tool-release` bereit (`app-release.apk` + `release.json`).

**3. Artefakt herunterladen, entpacken und auf den Server kopieren:**

```bash
scp app-release.apk release.json dein-server:~/ffsms/backend/updates/
```

Fertig. Ein Neustart des Backends ist nicht nötig — die Dateien werden bei
jeder Abfrage frisch gelesen.

**4. Optional Hinweistext ergänzen**, der dem Kollegen angezeigt wird:

```json
{ "version_code": 2, "version_name": "1.0.1", "notes": "Zählt jetzt auch Umlaute richtig." }
```

### Erstinstallation: die Download-Seite

Der Update-Kanal setzt eine bereits installierte App voraus — sie ist es ja,
die den API-Key mitschickt. Beim ersten Mal gibt es die noch nicht. Für diesen
Fall liefert dasselbe Backend unter **`/download`** eine Seite aus, die sich im
Browser mit einem Passwort öffnen lässt:

```
https://ffsms.networkx.cc/download
```

Benutzername beliebig, Passwort ist `DOWNLOAD_PASSWORD` aus der `.env`. Die
Seite zeigt Version, Größe, Datum und Prüfsumme des hinterlegten APK, dazu die
Installationsschritte inklusive des Play-Protect-Falls. Der Download heißt
`ff-sms-<version>.apk`, damit im Download-Ordner nicht drei Dateien namens
`app-release(2).apk` nebeneinander liegen.

Es ist dieselbe Datei aus demselben Verzeichnis — wer ein neues APK nach
`updates/` kopiert, hat damit automatisch auch die Download-Seite aktualisiert.

> **Ohne gesetztes `DOWNLOAD_PASSWORD` antwortet `/download` mit 404.** Ein
> vergessener Eintrag in der `.env` soll das APK nicht offen ins Netz stellen —
> „kein Passwort" darf nicht „kein Schutz" bedeuten.

### Auf dem Gerät

Der Kollege bekommt binnen eines Tages die Meldung „Update verfügbar" und
tippt in der App auf **Einstellungen → App-Version → Herunterladen und
installieren**. Prüfen lässt sich es dort auch jederzeit von Hand.

Beim ersten Mal fragt Android nach der Erlaubnis, Apps aus dieser Quelle zu
installieren. Danach nicht mehr.

### Wie es abgesichert ist

- Der Update-Endpunkt liegt **hinter dem API-Key**, wie alles andere auch.
- Das Backend berechnet den **SHA-256 selbst** aus der Datei auf der Platte,
  statt ihn aus `release.json` zu übernehmen. Eine halb hochgeladene Datei kann
  damit nicht als gültig ausgewiesen werden.
- Die App prüft die Prüfsumme **während des Schreibens** in die
  Installationssitzung und bricht vor dem Commit ab, wenn sie nicht passt. Ein
  abgebrochener Download wird dem System also gar nicht erst angeboten.
- Ein **Downgrade** wird ignoriert: verglichen wird nur, ob der `version_code`
  auf dem Server größer ist.
- **Automatisch heruntergeladen wird nichts.** Der Tagesjob meldet nur. Auf
  einem Alarmierungsgerät ist eine Installation zum falschen Zeitpunkt ein
  reales Risiko — wann aktualisiert wird, entscheidet der Mensch davor.

### Fehlersuche

| Symptom | Ursache |
|---|---|
| „Die App ist aktuell", obwohl neu hochgeladen | `versionCode` nicht erhöht, oder `release.json` zeigt noch die alte Zahl |
| HTTP 404 beim Prüfen | `app-release.apk` oder `release.json` fehlt im Update-Verzeichnis |
| „Prüfsumme stimmt nicht" | Datei unvollständig übertragen — `scp` wiederholen |
| Android bricht die Installation ab | Signatur weicht ab: das installierte APK stammt aus einem anderen Keystore (z. B. noch das Debug-APK) |
| `/download` antwortet 404 | `DOWNLOAD_PASSWORD` ist in der `.env` leer — die Seite ist dann bewusst abgeschaltet |
| `/download` fragt nicht nach dem Passwort | Änderung an der `.env` ohne `./update.sh` — der Container liest sie nur beim Start |

---

## Abgleich mit der Providerrechnung

Der Zweck der ganzen Übung: nachweisen können, wofür verrechnet wurde.

**1. CSV exportieren.** In der App: **Verlauf** → Download-Symbol oben rechts →
Speicherort wählen. Die Datei heißt `ff-sms-verlauf-JJJJ-MM-TT.csv`.

Format: Semikolon als Trennzeichen, CRLF, UTF-8 mit BOM — öffnet sich in einem
deutschsprachigen Excel ohne Zerlegung der Umlaute.

```csv
Datum;Uhrzeit;Gruppe;Empfaenger;Segmente je Nachricht;Segmente gesamt;Encoding;Status;Fehlgeschlagen;Abbruchgrund;Kampagne
13.08.2026;18:22:00;Aktivmannschaft;15;3;45;GSM7;abgeschlossen;0;;3f2504e0-...
20.08.2026;09:14:00;Aktivmannschaft;15;4;60;UCS2;abgeschlossen;0;;7b19cc31-...

Summe Segmente;;;;105
```

**2. Einzelverbindungsnachweis anfordern.** Bei österreichischen Anbietern über
das Kundenportal oder telefonisch. Nur der EVN listet die einzelnen Segmente;
die Sammelposition auf der Rechnung reicht für einen Abgleich nicht aus.

**3. Gegenüberstellen.**

- **Zeilenweise:** Jede Zeile im CSV entspricht `Empfaenger × Segmente je
  Nachricht` Einträgen im EVN, alle innerhalb weniger Minuten ab der genannten
  Uhrzeit. Bei 800 ms Pause pro Empfänger dauert eine Aussendung an 15 Leute
  rund 12 Sekunden; bei schlechtem Netz können die EVN-Zeitstempel etwas
  auseinanderlaufen.
- **Monatlich:** Die Summenzeile gegen die Gesamtzahl im EVN für denselben
  Zeitraum. Achtung auf die Monatsgrenze: eine Aussendung am Monatsletzten um
  23:30 gehört noch in den alten Monat. Die App und das Backend rechnen beide
  in Ortszeit, damit das aufgeht.

**4. Bei Abweichung:**

| Beobachtung | Deutung |
|---|---|
| EVN zeigt **mehr** Segmente als das CSV | Es wurde außerhalb der App gesendet (Standard-SMS-App auf demselben Gerät?), oder der Provider rundet anders. |
| EVN zeigt **dieselbe Nummer mehrfach** zum selben Zeitpunkt | Der Fall, den diese App verhindern soll. Die Spalte `Kampagne` zeigt, ob es eine oder zwei Aussendungen waren. In der Detailansicht steht der Status je Empfänger. |
| **Deutlich mehr Segmente als Zeichen erwarten lassen** | UCS-2. Spalte `Encoding` prüfen. |
| CSV zeigt **mehr** als der EVN | Fehlgeschlagene Empfänger (Spalte `Fehlgeschlagen`) werden nicht verrechnet. |

Die Spalte `Kampagne` enthält dieselbe UUID, die auch das Backend kennt — damit
lassen sich CSV, `GET /api/v1/stats` und der EVN auf denselben Vorgang beziehen.

Die Zahlen im Verlauf stammen aus dem **lokalen Sendeprotokoll**, nicht aus dem
Backend: gezählt wird, was das Modem tatsächlich abgesetzt hat, nicht was
geplant war. Eine abgebrochene Aussendung taucht deshalb nur mit ihrem
tatsächlichen Verbrauch auf, und die Wochen-/Monatsanzeige funktioniert auch,
wenn der Server steht.

---

## Tests

```bash
cd android  && ./gradlew testDebugUnitTest    # 38 Tests
cd backend  && npm test                       # 47 Tests
```

**Android (38).** Die Segmentberechnung ist bewusst frei von
Android-Abhängigkeiten, damit sich die Regeln in reinen JVM-Tests festnageln
lassen: 160/161/306/307 ASCII, Emoji-Surrogatpaare, Umlaute bleiben GSM-7, das
Euro-Zeichen zählt doppelt ohne zu kippen, leerer Text. Dazu die Fälle, in
denen Greedy-Packung und einfache Division auseinanderlaufen — ein
2-Einheiten-Zeichen darf nicht über eine Segmentgrenze zerrissen werden, sonst
weist die Anzeige ein Segment zu wenig aus. Weiter: Textbereinigung und
CSV-Export inklusive Quoting und Summenzeile.

**Backend (47).** Alarmbedingungen, Reportformat, Schlüsselkonfiguration,
Zeitzonengrenzen (Sommer-/Winterzeit, Jahreswechsel, 23:30 am Monatsletzten)
und ein HTTP-Integrationstest über die volle Nest-Pipeline, der
Authentifizierung, Idempotenz und die Abweisung von Text-, Nummern- und
Hash-Feldern nachweist.

---

## Abweichungen von der Vorgabe

**Kontaktauswahl.** Statt `ACTION_PICK` ein eigener Auswahldialog auf denselben
Kontaktdaten. Grund: Der System-Picker gibt immer nur *einen* Kontakt zurück —
für einen Verteiler mit 30 Personen wären das 30 Durchläufe. Die geforderte
Mehrfachauswahl gibt es damit tatsächlich, die Daten kommen aus derselben
Quelle und werden nicht zwischengespeichert.

**Segmentberechnung greedy statt per Division.** Die Vorgabe nennt 153 bzw. 67
Zeichen je Teil. Eine reine Division `ceil(gesamt / 153)` ist in Grenzfällen
falsch: bei `152 × "a" + "€" + 152 × "a"` (306 Septetts) ergibt die Division 2,
richtig sind 3 — das Euro-Zeichen braucht 2 Septetts und passt nicht mehr in
das eine freie Septett von Segment 1, das dadurch verfällt. Unterschätzte
Kosten wären hier der schlimmere Fehler, deshalb wird gepackt statt geteilt.
Beide Fälle sind getestet.

**`/api/v1/health` liegt hinter der Authentifizierung.** Sonst wäre
„Verbindung testen" in der App wertlos. Für Uptime-Monitoring gibt es
zusätzlich `/api/v1/health/live` ohne Schlüssel.

**Kein Hilt/Dagger.** Die App hat eine Handvoll Singletons; ein weiterer
Annotation-Processor macht den Build für eine Feuerwehr, die das APK
vielleicht in zwei Jahren neu bauen muss, nur zerbrechlicher. Stattdessen ein
schlanker `ServiceLocator`.

**`synchronize: true` in TypeORM.** Vertretbar, weil das Schema aus einer
einzigen Tabelle besteht und ausschließlich von dieser Anwendung verwaltet
wird. Kämen eine zweite Tabelle oder ein Feldwechsel dazu, wären Migrationen
fällig.

---

## Offene Punkte

Diese Werte waren in der Vorgabe nicht festgelegt und sind hier mit einem
Standard belegt — jeweils an genau einer Stelle änderbar:

| Punkt | Aktuell | Wo ändern |
|---|---|---|
| Package-Name | `cc.netwokx.ffsms` (`FF-SMS-Tool` ist als Java-Package nicht gültig) | `android/app/build.gradle.kts` |
| Geräte-Kennung | `ff-kuehwiesen-sms` | App → Einstellungen, bzw. `DEFAULT_DEVICE_ID` |
| ntfy-Topic | offen | `NTFY_URL` in `backend/.env` |
| Backend-URL | `https://ffsms.networkx.cc/` | App → Einstellungen, bzw. `DEFAULT_BACKEND_URL` |
| Warnschwelle Anzeige | 100 SMS | App → Einstellungen |
