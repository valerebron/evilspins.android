#!/usr/bin/env bash
# EvilSpins Dev Android - Vite dev server + Capacitor app
# Usage: pnpm dev

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

log() { echo -e "${BLUE}[dev]${NC} $*"; }
ok() { echo -e "${GREEN}[✓]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err() { echo -e "${RED}[✗]${NC} $*"; }

export ANDROID_HOME=/home/user/Android/Sdk
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

PORT=3000

# 1. Detect local IP
detect_ip() {
  ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1
}

LOCAL_IP=$(detect_ip)
[[ -z "$LOCAL_IP" ]] && { err "No local IP detected"; exit 1; }
ok "Local IP: $LOCAL_IP"

# 2. Cleanup
for p in 3000 5173; do
  fuser -k ${p}/tcp 2>/dev/null || true
  adb reverse --remove tcp:$p 2>/dev/null || true
done
sleep 1

# 3. Force stop app
log "Force stopping app..."
adb shell am force-stop com.evilspins.app 2>/dev/null || true

# 4. Update capacitor.config.json with live reload URL
CAP_CONFIG="capacitor.config.json"
TMP=$(mktemp)
cat "$CAP_CONFIG" | jq --arg ip "$LOCAL_IP" --arg port "$PORT" '
  .server = {url: "http://\($ip):\($port)", cleartext: true}
' > "$TMP" && mv "$TMP" "$CAP_CONFIG"
ok "capacitor.config.json → server.url = http://$LOCAL_IP:$PORT"

# 5. Start Vite dev server (bound to 0.0.0.0, port 3000)
log "Starting Vite dev server on 0.0.0.0:$PORT..."
VITE_PORT=$PORT pnpm devite -- --host 0.0.0.0 --port $PORT --strictPort &
VITE_PID=$!

# 6. Forward port to device
log "Setting up adb reverse tcp:$PORT..."
sleep 2
adb reverse tcp:$PORT tcp:$PORT
ok "Port forwarded"

# 7. Sync & launch app (no live reload, just points to server.url)
log "Syncing & launching app..."
npx cap sync android --no-sync 2>/dev/null || true
adb shell am start -n com.evilspins.app/.MainActivity

# 8. Launch scrcpy
log "Starting scrcpy..."
scrcpy &
SCRCPY_PID=$!

ok "HMR active at http://$LOCAL_IP:$PORT"
log "Press Ctrl+C to stop."

cleanup() {
  log "Shutting down..."
  kill $VITE_PID 2>/dev/null || true
  kill $SCRCPY_PID 2>/dev/null || true
  adb reverse --remove tcp:$PORT 2>/dev/null || true
  exit 0
}
trap cleanup INT TERM EXIT

wait $VITE_PID