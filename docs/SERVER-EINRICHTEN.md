# Server einrichten

Schritt für Schritt von einem frischen Debian bis zum laufenden Backend mit
ntfy und Cloudflare Tunnel. Gedacht zum Abarbeiten von oben nach unten.

Am Ende laufen vier Container:

| Container | Aufgabe | Erreichbar |
|---|---|---|
| `db` | PostgreSQL | nur intern |
| `api` | das FF-SMS-Backend | `https://ffsms.networkx.cc` |
| `ntfy` | Push-Meldungen aufs Handy | `https://ntfy.networkx.cc` |
| `cloudflared` | Tunnel nach außen | — |

Kein Port muss im Router freigegeben werden. Der Tunnel baut die Verbindung
von innen nach außen auf.

---

## Schritt 0 — Root werden

Bei einer Minimal-Installation von Debian ist `sudo` oft **gar nicht
vorhanden**, und `usermod` liegt in `/usr/sbin`, das im Suchpfad normaler
Benutzer fehlt. Beides führt zu `command not found`. Deshalb zuerst:

```bash
su -
```

Das **`-` ist wichtig** — nur damit bekommst du Roots vollständigen Suchpfad.
Gefragt wird nach dem **Root-Passwort**, nicht nach deinem eigenen.

Dann `sudo` nachinstallieren und den eigenen Benutzer berechtigen (`ffsms`
durch den eigenen Benutzernamen ersetzen):

```bash
apt update
apt install -y sudo ca-certificates curl git
adduser ffsms sudo
```

Die folgenden Schritte laufen weiter als root — deshalb steht in Schritt 1
kein `sudo` davor.

> Ist bereits `sudo` vorhanden und der eigene Benutzer berechtigt, kann
> Schritt 0 übersprungen und den Befehlen in Schritt 1 ein `sudo`
> vorangestellt werden.

---

## Schritt 1 — Docker installieren

Debians eigene Docker-Pakete sind meist veraltet. Offizielles Repository
(als root, siehe Schritt 0):

```bash
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg \
  -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list

apt update
apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
```

Den eigenen Benutzer zur Docker-Gruppe hinzufügen, damit später kein `sudo`
nötig ist, und die Root-Sitzung verlassen:

```bash
usermod -aG docker ffsms
exit
```

**Jetzt einmal vollständig ab- und wieder anmelden** — die SSH-Sitzung
schließen und neu verbinden. Ohne das kennt die Shell die neue
Gruppenzugehörigkeit nicht. Prüfen:

```bash
docker run --rm hello-world
```

Erscheint „Hello from Docker!", ist alles bereit.

> **Läuft der Server als LXC-Container** (z. B. auf Proxmox), braucht der
> Container `nesting=1` in seinen Optionen, sonst startet der Docker-Daemon
> nicht. Das zeigt sich genau hier beim `hello-world`.

---

## Schritt 2 — Projekt holen

Der Code liegt im Branch `claude/ff-sms-verteiler-app-ifodkw`, **nicht** auf
`main`. Der Branch muss deshalb ausdrücklich angegeben werden — ein `git clone`
ohne `-b` holt den Standard-Branch und damit nur die README:

```bash
mkdir -p ~/ffsms && cd ~/ffsms
git clone -b claude/ff-sms-verteiler-app-ifodkw https://github.com/flaash88/ffsms.git .
cd backend
```

**Später auf den neuesten Stand bringen:**

```bash
cd ~/ffsms
git fetch origin
git checkout -B claude/ff-sms-verteiler-app-ifodkw origin/claude/ff-sms-verteiler-app-ifodkw
cd backend && docker compose --profile tunnel up -d --build
```

Die `.env` überlebt das, weil sie nicht versioniert ist. Kamen mit dem Update
neue Pflichtfelder dazu, bricht der Start mit einer Meldung ab, die das
fehlende Feld benennt — dann `.env.example` gegenprüfen.

---

## Schritt 3 — Geheimnisse erzeugen

Drei Zufallswerte. **Jeden einzeln erzeugen und wegschreiben** — sie werden
gleich gebraucht:

```bash
echo "DB_PASSWORD  = $(openssl rand -hex 24)"
echo "API_KEY      = $(openssl rand -hex 24)"
echo "NTFY_PASS    = $(openssl rand -hex 12)"
```

Der `API_KEY` kommt später auch **in die App** (Einstellungen → API-Key).
Am besten gleich in den Passwortmanager der Feuerwehr.

---

## Schritt 4 — `.env` anlegen

```bash
cp .env.example .env
nano .env
```

Auszufüllen:

```ini
DB_USER=ffsms
DB_PASSWORD=<der Wert von oben>
DB_NAME=ffsms

# Format: kennung:schluessel
API_KEYS=ff-kuehwiesen-sms:<API_KEY von oben>

NTFY_BASE_URL=https://ntfy.networkx.cc
NTFY_URL=https://ntfy.networkx.cc/ff-sms
NTFY_TOKEN=          # bleibt vorerst leer, kommt in Schritt 6

TUNNEL_TOKEN=        # bleibt vorerst leer, kommt in Schritt 5

REPORT_TITLE=FF Kühwiesen
TZ_REPORT=Europe/Vienna
```

> Die Datei enthält alle Geheimnisse im Klartext. Sie steht in `.gitignore` und
> darf nicht ins Git. Rechte einschränken:
> ```bash
> chmod 600 .env
> ```

Update-Verzeichnis anlegen (bleibt zunächst leer):

```bash
mkdir -p updates
```

---

## Schritt 5 — Cloudflare Tunnel einrichten

Im **Cloudflare-Dashboard**, nicht auf dem Server:

1. **Zero Trust** → **Networks** → **Tunnels** → **Create a tunnel**
2. Typ **Cloudflared**, Name z. B. `ffsms-heim`
3. Auf der Installationsseite steht ein Befehl mit einem langen Token nach
   `--token`. **Nur diesen Token** kopieren (der Teil nach `--token `, beginnt
   meist mit `ey…`).
4. In die `.env` eintragen:
   ```ini
   TUNNEL_TOKEN=ey...
   ```
5. Im Dashboard unter **Public Hostnames** zwei Einträge anlegen:

   | Subdomain | Domain | Service |
   |---|---|---|
   | `ffsms` | `networkx.cc` | `HTTP` → `api:3000` |
   | `ntfy` | `networkx.cc` | `HTTP` → `ntfy:80` |

   `api` und `ntfy` sind die Container-Namen — der Tunnel läuft im selben
   Docker-Netz und erreicht sie darüber direkt. **Nicht** `localhost`
   eintragen: aus Sicht des cloudflared-Containers wäre das er selbst.

---

## Schritt 6 — Starten

```bash
docker compose --profile tunnel up -d --build
```

Der erste Start dauert ein paar Minuten (Images ziehen, Backend bauen).
Status prüfen:

```bash
docker compose ps
docker compose logs -f api
```

Erwartet: `FF-SMS-Backend laeuft auf Port 3000` und
`Wochenreport geplant: "0 20 * * 0" (Europe/Vienna)`.

### ntfy-Benutzer und Token anlegen

Die Instanz läuft mit `deny-all` — ohne Zugangsdaten kommt niemand an die
Meldungen. Jetzt einen Benutzer anlegen:

```bash
docker compose exec ntfy ntfy user add ff
# Passwort: der NTFY_PASS-Wert aus Schritt 3

docker compose exec ntfy ntfy access ff ff-sms rw
```

Token für das Backend erzeugen:

```bash
docker compose exec ntfy ntfy token add ff
```

Ausgegeben wird ein Token, das mit `tk_` beginnt. In die `.env`:

```ini
NTFY_TOKEN=tk_...
```

Und das Backend neu starten, damit es den Token liest:

```bash
docker compose up -d api
```

---

## Schritt 7 — Prüfen

**Backend von außen:**

```bash
curl -s https://ffsms.networkx.cc/api/v1/health/live
# {"status":"ok"}

curl -s -H "X-API-Key: <API_KEY>" https://ffsms.networkx.cc/api/v1/health
# {"status":"ok","database":"up",...}
```

Kommt bei der zweiten Zeile `401`, stimmt der Schlüssel nicht mit `API_KEYS`
überein.

**ntfy:**

```bash
curl -H "Authorization: Bearer <NTFY_TOKEN>" \
     -H "Title: Test" -d "Funktioniert" \
     https://ntfy.networkx.cc/ff-sms
```

**Auf dem Handy:** die [ntfy-App](https://f-droid.org/packages/io.heckel.ntfy/)
installieren, unter *Einstellungen → Benutzerkonten* den Server
`https://ntfy.networkx.cc` mit Benutzer `ff` und dem Passwort hinterlegen,
dann das Topic `ff-sms` abonnieren. Der Test von oben muss ankommen.

---

## Schritt 8 — App verbinden

In der App unter **Einstellungen**:

| Feld | Wert |
|---|---|
| Backend-URL | `https://ffsms.networkx.cc/` |
| API-Key | der `API_KEY` aus Schritt 3 |
| Geräte-Kennung | `ff-kuehwiesen-sms` (muss zum `API_KEYS`-Eintrag passen) |

**Verbindung testen** antippen. Kommt „Verbindung in Ordnung", passt alles.
Danach **Verbrauchszahlen übertragen** einschalten.

---

## Betrieb

```bash
# Logs
docker compose logs -f api

# Neustart nach .env-Änderung
docker compose up -d

# Auf neuen Stand bringen
cd ~/ffsms && git fetch origin \
  && git checkout -B claude/ff-sms-verteiler-app-ifodkw origin/claude/ff-sms-verteiler-app-ifodkw \
  && cd backend && docker compose --profile tunnel up -d --build

# Datenbank sichern (regelmäßig! z. B. per cron)
docker compose exec -T db pg_dump -U ffsms ffsms | gzip > ~/ffsms-$(date +%F).sql.gz
```

Wochenreport von Hand auslösen, ohne bis Sonntag zu warten — dazu die
Alarmschwelle kurz herunterdrehen und eine Test-Kampagne einliefern, oder
schlicht warten und die Logs beobachten.

---

## Fehlersuche

| Symptom | Ursache |
|---|---|
| `curl` von außen antwortet nicht | Tunnel läuft nicht: `docker compose logs cloudflared` |
| `502` von Cloudflare | Public Hostname zeigt auf `localhost` statt auf `api` bzw. `ntfy` |
| `service "ntfy" is not running` | Lokale Kopie ist aelter als der Commit mit ntfy - siehe Schritt 2, "auf den neuesten Stand bringen" |
| Nach `git clone` liegt nur die README da | Ohne `-b claude/...` wird der Standard-Branch geholt, auf dem kein Code liegt |
| `sudo: command not found` | Minimal-Installation ohne sudo - siehe Schritt 0 |
| `usermod: command not found` | `/usr/sbin` fehlt im Suchpfad: `su -` mit Bindestrich verwenden |
| `docker: permission denied` | Nach `usermod -aG docker` nicht neu angemeldet |
| Docker-Daemon startet nicht (LXC) | Container braucht `nesting=1` |
| `api` startet nicht, Log nennt `API_KEYS` | `.env` fehlt oder der Eintrag hat nicht das Format `kennung:schluessel` |
| Meldung kommt nicht am Handy an | Token fehlt in `.env`, oder der Benutzer hat keine Rechte auf dem Topic (`ntfy access ff ff-sms rw`) |
| `403` beim Upload aus der App | Geräte-Kennung in der App weicht von der in `API_KEYS` ab |
| ntfy-Links im Handy zeigen ins Leere | `NTFY_BASE_URL` stimmt nicht mit dem Hostnamen im Tunnel überein |
