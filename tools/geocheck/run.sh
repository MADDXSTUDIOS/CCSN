#!/bin/sh
# Compiles the real cable geometry (CableLanes, CableRender) against tiny Minecraft stubs, then
#   tools/geocheck/run.sh            proves no strand overlaps another or the tray steel
#   tools/geocheck/run.sh scenes OUT renders preview scenes to OUT/*.png (needs numpy + Pillow)
# Run from the repo root. Needs only a JDK.
set -e
OUT=build/geocheck
mkdir -p "$OUT"
javac -d "$OUT" $(find tools/geocheck/stubs -name '*.java') \
    src/main/java/uk/maddx/ccsn/network/CableLanes.java src/main/java/uk/maddx/ccsn/network/CableRender.java \
    tools/geocheck/Dump.java tools/geocheck/Check.java
if [ "$1" = scenes ]; then
    python3 tools/geocheck/scenes.py "$OUT" "${2:-$OUT}"
else
    java -cp "$OUT" Check
fi
