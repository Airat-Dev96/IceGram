#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
command -v javac >/dev/null 2>&1 || { echo 'Install JDK 17 to run these tests.' >&2; exit 1; }
OUT=$(mktemp -d)
trap 'rm -f "$OUT/ImageCachePolicyTest.class" "$OUT/org/icegram/messenger/ImageCachePolicy.class"; rmdir "$OUT/org/icegram/messenger" "$OUT/org/icegram" "$OUT/org" "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" \
    "$ROOT/TMessagesProj/src/main/java/org/icegram/messenger/ImageCachePolicy.java" \
    "$ROOT/Tools/icegram/ImageCachePolicyTest.java"
java -cp "$OUT" ImageCachePolicyTest
