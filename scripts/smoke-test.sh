#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$PROJECT_DIR/build/smoke-server"
USER_AGENT="GLITG-Core-smoke-test/1.0 (https://example.com/glitgcore)"
PAPER_VERSION="26.2"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }

"$PROJECT_DIR/gradlew" -q jar
rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR/plugins"
cp "$PROJECT_DIR/build/libs/glitg-core-1.0.0.jar" "$SERVER_DIR/plugins/"

BUILDS_RESPONSE="$(curl -fsSL -H "User-Agent: $USER_AGENT" "https://fill.papermc.io/v3/projects/paper/versions/$PAPER_VERSION/builds")"
PAPER_URL="$(python3 -c 'import json,sys; builds=json.load(sys.stdin); print(next((b["downloads"]["server:default"]["url"] for b in builds if b.get("channel")=="STABLE"), ""))' <<<"$BUILDS_RESPONSE")"
[[ -n "$PAPER_URL" ]] || { echo "No stable Paper $PAPER_VERSION build is available" >&2; exit 1; }
curl -fsSL -H "User-Agent: $USER_AGENT" "$PAPER_URL" -o "$SERVER_DIR/paper.jar"

printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nview-distance=2\nsimulation-distance=2\n' > "$SERVER_DIR/server.properties"
mkfifo "$SERVER_DIR/console.fifo"
exec 3<>"$SERVER_DIR/console.fifo"

(
  cd "$SERVER_DIR"
  java --enable-native-access=ALL-UNNAMED -Xms512M -Xmx1G -jar paper.jar --nogui <&3 >server.log 2>&1
) &
SERVER_PID=$!

READY=0
for _ in $(seq 1 120); do
  if grep -q 'Done (' "$SERVER_DIR/server.log" 2>/dev/null; then READY=1; break; fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then break; fi
  sleep 1
done

if [[ "$READY" -eq 1 ]]; then
  printf 'glitgcore version\nglitgcore status\nglitgcore reload\nkit clear\nkit join off\nkit load @a\nsaltar info missing\ndeathban typo\nstart 2\ndimension status end\nanonymousdeaths status\nstop\n' >&3
fi

for _ in $(seq 1 30); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done
if kill -0 "$SERVER_PID" 2>/dev/null; then kill "$SERVER_PID"; wait "$SERVER_PID" || true; fi
exec 3>&-

[[ "$READY" -eq 1 ]] || { echo "Paper did not become ready" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
grep -q 'GLITG Core 1.0.0 enabled for Paper 26.2' "$SERVER_DIR/server.log" || { echo "GLITG Core did not enable" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
grep -q 'Configuration reloaded' "$SERVER_DIR/server.log" || { echo "Transactional reload command failed" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
grep -q 'Kit operation complete' "$SERVER_DIR/server.log" || { echo "Console-safe kit commands failed" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
grep -q 'No matching altar' "$SERVER_DIR/server.log" || { echo "Console altar ID inspection failed" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
grep -q 'Unknown deathban operation' "$SERVER_DIR/server.log" || { echo "Death-ban operation validation failed" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
grep -q 'Invisible-player deaths are hidden until:' "$SERVER_DIR/server.log" || { echo "Independent timer commands did not execute" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
grep -q 'The End.*locked' "$SERVER_DIR/server.log" || { echo "Durable End scheduling did not execute" >&2; tail -120 "$SERVER_DIR/server.log" >&2; exit 1; }
if grep -Eiq '(Unhandled exception|Could not pass event|GLITG Core could not start safely|\[GLITG Core\].*(ERROR|SEVERE)|Exception in thread)' "$SERVER_DIR/server.log"; then
  echo "Exception detected in smoke-test log" >&2
  grep -Ein '(Unhandled exception|Could not pass event|GLITG Core could not start safely|\[GLITG Core\].*(ERROR|SEVERE)|Exception in thread)' "$SERVER_DIR/server.log" >&2
  exit 1
fi

echo "Paper $PAPER_VERSION smoke test passed. Log: $SERVER_DIR/server.log"
