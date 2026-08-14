#!/usr/bin/env bash
#
# Bringt den Stack auf den neuesten Stand.
#
# Der Neustart von cloudflared am Ende ist kein Beiwerk: "docker compose up -d"
# erzeugt geaenderte Container neu, und dabei wechselt ihre IP im Docker-Netz.
# cloudflared haelt offene Verbindungen zur alten Adresse und antwortet dann
# mit 502, obwohl das Backend laeuft. Wer den Neustart vergisst, sucht den
# Fehler an der falschen Stelle.
set -euo pipefail

cd "$(dirname "$0")"

docker compose --profile tunnel up -d --build
docker compose restart cloudflared

echo
echo "Warte auf das Backend ..."
for i in $(seq 1 30); do
    if curl -sf -o /dev/null localhost:3000/api/v1/health/live; then
        echo "Backend lokal erreichbar."
        break
    fi
    sleep 2
done

# Nur pruefen, wenn ueberhaupt ein Tunnel laeuft.
if docker compose ps --services --status running | grep -q '^cloudflared$'; then
    url="$(grep -E '^NTFY_BASE_URL=' .env | cut -d= -f2- | tr -d '"' | sed 's|ntfy\.|ffsms.|')"
    if [ -n "$url" ]; then
        code="$(curl -s -o /dev/null -w '%{http_code}' "$url/api/v1/health/live" || true)"
        echo "Tunnel antwortet mit HTTP $code"
        [ "$code" = "200" ] || echo "  -> Bei 502: nochmal 'docker compose restart cloudflared' versuchen."
    fi
fi

docker compose ps
