#!/usr/bin/env bash
#
# Bringt den Stack auf den neuesten Stand.
#
# Der Neustart von cloudflared ist kein Beiwerk: "docker compose up -d" erzeugt
# geaenderte Container neu, und dabei wechselt ihre IP im Docker-Netz.
# cloudflared haelt offene Verbindungen zur alten Adresse und antwortet dann
# mit 502, obwohl das Backend laeuft. Wer den Neustart vergisst, sucht den
# Fehler an der falschen Stelle.
#
# Die Reihenfolge ist dabei entscheidend: erst warten, bis das Backend
# antwortet, DANN den Tunnel neu starten. Andersherum verbindet sich
# cloudflared mit einem Backend, das noch startet - und genau daraus entsteht
# der 502, den der Neustart eigentlich verhindern soll.
set -euo pipefail

cd "$(dirname "$0")"

docker compose --profile tunnel up -d --build

echo
echo "Warte auf das Backend ..."
backend_up=false
for _ in $(seq 1 45); do
    if curl -sf -o /dev/null localhost:3000/api/v1/health/live; then
        echo "Backend lokal erreichbar."
        backend_up=true
        break
    fi
    sleep 2
done

if [ "$backend_up" = false ]; then
    echo
    echo "Das Backend antwortet lokal nicht. Der Tunnel ist NICHT die Ursache."
    echo "Die letzten Zeilen aus dem Log:"
    echo
    docker compose logs api --tail=30
    exit 1
fi

# Nur pruefen, wenn ueberhaupt ein Tunnel laeuft.
if docker compose ps --services --status running | grep -q '^cloudflared$'; then
    docker compose restart cloudflared

    url="$(grep -E '^NTFY_BASE_URL=' .env | cut -d= -f2- | tr -d '"' | sed 's|ntfy\.|ffsms.|')"
    if [ -n "$url" ]; then
        # Der Tunnel braucht nach dem Neustart ein paar Sekunden, bis die
        # Verbindungen zu Cloudflare wieder stehen. Ein einzelner Versuch
        # direkt danach meldet fast immer 502 - und schickt damit auf die
        # Suche nach einem Fehler, der sich von selbst erledigt haette.
        code=000
        for _ in $(seq 1 15); do
            code="$(curl -s -o /dev/null -w '%{http_code}' "$url/api/v1/health/live" || true)"
            [ "$code" = "200" ] && break
            sleep 2
        done
        echo "Tunnel antwortet mit HTTP $code"
        if [ "$code" != "200" ]; then
            echo "  -> Backend laeuft lokal, der Tunnel kommt aber nicht durch."
            echo "     Pruefen: docker compose logs cloudflared --tail=30"
        fi
    fi
fi

docker compose ps
