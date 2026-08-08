#!/bin/sh
# Generate Java 22 FFM stubs for every symbol declared by OpenEXRCore's public
# openexr*.h headers, excluding the one variadic callback jextract cannot model.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JX="${JEXTRACT:-$ROOT/tools/jextract-22/bin/jextract}"
PREFIX="${OPENEXR_PREFIX:-$ROOT/build/prefix-macos-arm64}"
OUT="$ROOT/core/src/main/java"
PKG="io.github.ghosthack.openexrffm.openexr"
GEN_DIR="$OUT/$(echo "$PKG" | tr . /)"

[ -x "$JX" ] || { echo "jextract not found at $JX" >&2; exit 1; }
[ -f "$PREFIX/include/OpenEXR/openexr.h" ] || {
  echo "OpenEXR headers not found under $PREFIX; build macOS natives first" >&2
  exit 1
}

TMP_DIR="$(mktemp -d /tmp/openexr-ffm-jextract.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM
DUMP="$TMP_DIR/includes.txt"
DUMMY="$TMP_DIR/dummy"
mkdir -p "$DUMMY"

# Ask jextract for its symbol inventory, then retain only declarations from
# OpenEXRCore's public openexr*.h headers. This keeps the binding complete as
# patch releases add APIs without leaking libc/pthread platform layouts.
"$JX" --output "$DUMMY" --dump-includes "$DUMP" -t ignored \
  -I "$PREFIX/include/OpenEXR" \
  -I "$PREFIX/include/Imath" \
  "$ROOT/jextract/openexr_api.h" >/dev/null

awk '
  /^#### Extracted from: / { keep = index($0, "/OpenEXR/openexr") > 0 }
  keep && /^--include-/ {
    sub(/  *# header:.*/, "")
    if ($0 !~ /_priv_exr_context_t/ && $0 !~ /exr_stream_error_func_ptr_t/) print
  }
' "$DUMP" > "$TMP_DIR/options.txt"

rm -rf "$GEN_DIR"
mkdir -p "$GEN_DIR"
# The option inventory is exactly two whitespace-free arguments per line.
# shellcheck disable=SC2046
"$JX" --output "$OUT" -t "$PKG" --header-class-name OpenEXR \
  -I "$PREFIX/include/OpenEXR" \
  -I "$PREFIX/include/Imath" \
  -l ":$PREFIX/lib/libOpenEXRCore.dylib" \
  $(cat "$TMP_DIR/options.txt") \
  "$ROOT/jextract/openexr_api.h"

perl -0777 -pi -e \
  's/static final SymbolLookup SYMBOL_LOOKUP = .*?;/static final SymbolLookup SYMBOL_LOOKUP = io.github.ghosthack.openexrffm.OpenEXRLibs.lookup();/s' \
  "$GEN_DIR/OpenEXR.java"

if grep -rn "$PREFIX" "$GEN_DIR" >/dev/null; then
  echo "build-prefix paths survived in generated stubs" >&2
  exit 1
fi
grep -q 'OpenEXRLibs.lookup()' "$GEN_DIR/OpenEXR.java" || {
  echo "runtime lookup patch did not apply" >&2
  exit 1
}
echo "Generated $(find "$GEN_DIR" -type f | wc -l | tr -d ' ') files into $GEN_DIR"

