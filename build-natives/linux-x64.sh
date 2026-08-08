#!/bin/sh
# Build and stage OpenEXRCore for 64-bit Linux.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT/build-natives/build-platform.sh" linux-x64 "${1:-}"

