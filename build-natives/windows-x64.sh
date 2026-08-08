#!/bin/sh
# Build and stage OpenEXRCore from an MSYS2 MINGW64 shell.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT/build-natives/build-platform.sh" windows-x64 "${1:-}"

