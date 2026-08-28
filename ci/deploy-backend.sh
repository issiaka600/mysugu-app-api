#!/usr/bin/env bash
set -euo pipefail
COMPOSE_DIR=/root/mysugu
BACKEND_DIR="$COMPOSE_DIR/backend"
JAR="$BACKEND_DIR/mysugu-app-api.jar"
NEW="$BACKEND_DIR/mysugu-app-api.jar.new"
HEALTH_URL="http://localhost:8083/swagger-ui/index.html"

[ -f "$NEW" ] || { echo "ERROR: no new jar at $NEW"; exit 1; }

TS=$(date +%s)
BAK="$BACKEND_DIR/mysugu-app-api.jar.bak.$TS"
cp -f "$JAR" "$BAK"
echo "Backed up current jar -> $BAK"

mv -f "$NEW" "$JAR"
echo "Swapped in new jar."

cd "$COMPOSE_DIR"
# Bound the remote Docker operation so a stuck daemon produces a useful failure
# instead of leaving the GitHub runner SSH session idle until it breaks.
if ! timeout --foreground 240 docker compose up -d --build backend; then
  echo "ERROR: Docker compose backend build/start failed or timed out"
  docker compose ps || true
  docker compose logs --tail=80 backend || true
  exit 1
fi

echo "Health-gating $HEALTH_URL ..."
ok=0
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" || echo 000)
  if [ "$code" = "200" ]; then ok=1; echo "Healthy (200) after $((i*3))s"; break; fi
  sleep 3
done

if [ "$ok" != "1" ]; then
  echo "HEALTH CHECK FAILED -- rolling back to $BAK"
  cp -f "$BAK" "$JAR"
  docker compose up -d --build backend
  echo "Rolled back to previous jar. DEPLOY FAILED."
  exit 2
fi

# keep the 3 newest backups, delete older
ls -1t "$BACKEND_DIR"/mysugu-app-api.jar.bak.* 2>/dev/null | tail -n +4 | xargs -r rm -f
echo "DEPLOY OK."
