#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$REPO_DIR/app/src/main/assets/bundled_components"

# SteamFlow hosts the same Proton/DXVK components plus both the original
# Adreno 740 RP6 driver and the patched Gen8 A830/A840 driver.
RELEASE_URL="${COMPONENTS_URL:-https://github.com/SteamFlowYT/wow-forever-gamenative/releases/download/v1.0.0}"

FILES=(
    proton-11.0-90624-arm64ec.wcp
    dxvk-2.4.1-wow-aarch64-test.wcp
    turnip-wow-scheduler-test.zip
    Turnip-V32-RP6sched-A8xx.zip
)

mkdir -p "$DEST"
for f in "${FILES[@]}"; do
    if [ -s "$DEST/$f" ]; then
        echo "have $f"
    elif [ -n "${1:-}" ] && [ -s "$1/$f" ]; then
        cp "$1/$f" "$DEST/$f"
        echo "copied $f"
    else
        curl -fL --retry 3 -o "$DEST/$f.part" "$RELEASE_URL/$f"
        mv "$DEST/$f.part" "$DEST/$f"
        echo "downloaded $f"
    fi
done

(cd "$DEST" && shasum -a 256 -c "$REPO_DIR/tools/components.sha256")
