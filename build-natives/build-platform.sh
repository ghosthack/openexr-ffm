#!/bin/sh
# Build OpenEXRCore and its permissively licensed dependencies from source,
# then stage the relocatable runtime closure for a Maven classifier jar.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLATFORM="${1:?usage: build-platform.sh <macos-arm64|linux-x64|windows-x64> [source]}"
OPENEXR_VERSION=3.4.14
IMATH_VERSION=3.2.2
SRC="${2:-}"
[ -n "$SRC" ] || SRC="$ROOT/build/openexr-$OPENEXR_VERSION"
PREFIX="$ROOT/build/prefix-$PLATFORM"
BUILD_DIR="$ROOT/build/cmake-$PLATFORM"
DEST="$ROOT/natives/src/main/resources/natives/$PLATFORM"

[ -f "$SRC/CMakeLists.txt" ] || {
  echo "OpenEXR source not found at $SRC" >&2
  echo "Fetch v$OPENEXR_VERSION into build/openexr-$OPENEXR_VERSION first" >&2
  exit 1
}

case "$PLATFORM" in
  macos-arm64)
    PROCESSORS="$(sysctl -n hw.ncpu)"
    set -- \
      -DCMAKE_OSX_ARCHITECTURES=arm64 \
      -DCMAKE_INSTALL_RPATH=@loader_path \
      -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON
    ;;
  linux-x64)
    PROCESSORS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)"
    set -- \
      '-DCMAKE_INSTALL_RPATH=$ORIGIN' \
      -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON
    ;;
  windows-x64)
    PROCESSORS="${NUMBER_OF_PROCESSORS:-2}"
    set -- '-DCMAKE_SHARED_LINKER_FLAGS=-static-libgcc -static-libstdc++'
    ;;
  *)
    echo "unsupported platform: $PLATFORM" >&2
    exit 1
    ;;
esac

cmake -S "$SRC" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_TESTING=OFF \
  -DOPENEXR_BUILD_TOOLS=OFF \
  -DOPENEXR_INSTALL_TOOLS=OFF \
  -DOPENEXR_BUILD_EXAMPLES=OFF \
  -DOPENEXR_BUILD_PYTHON=OFF \
  -DOPENEXR_INSTALL_DOCS=OFF \
  -DOPENEXR_FORCE_INTERNAL_DEFLATE=ON \
  -DOPENEXR_FORCE_INTERNAL_OPENJPH=ON \
  -DOPENEXR_FORCE_INTERNAL_IMATH=ON \
  "$@"
cmake --build "$BUILD_DIR" --parallel "$PROCESSORS"
cmake --install "$BUILD_DIR"

rm -rf "$DEST"
mkdir -p "$DEST"
: > "$DEST/manifest.txt"

stage_one() {
  source_file="$1"
  name="$(basename "$source_file")"
  cp "$source_file" "$DEST/$name"
  chmod 644 "$DEST/$name"
  echo "$name" >> "$DEST/manifest.txt"
}

case "$PLATFORM" in
  macos-arm64)
    IMATH_LIB="$(find "$PREFIX/lib" -type f -name 'libImath-3_2*.dylib' | sort | tail -1)"
    CORE_LIB="$(find "$PREFIX/lib" -type f -name 'libOpenEXRCore-3_4*.dylib' | sort | tail -1)"
    [ -n "$IMATH_LIB" ] && [ -n "$CORE_LIB" ] || { echo "built dylibs not found" >&2; exit 1; }
    stage_one "$IMATH_LIB"
    stage_one "$CORE_LIB"
    for file in "$DEST"/*.dylib; do
      if otool -L "$file" | tail -n +2 | grep -vE '@loader_path/|@rpath/|/usr/lib/|/System/Library/' >/dev/null; then
        otool -L "$file" >&2
        echo "non-relocatable dependency in $file" >&2
        exit 1
      fi
    done
    ;;
  linux-x64)
    IMATH_LIB="$(find "$PREFIX/lib" "$PREFIX/lib64" -type f -name 'libImath-3_2.so.*' 2>/dev/null | sort | tail -1)"
    CORE_LIB="$(find "$PREFIX/lib" "$PREFIX/lib64" -type f -name 'libOpenEXRCore-3_4.so.*' 2>/dev/null | sort | tail -1)"
    [ -n "$IMATH_LIB" ] && [ -n "$CORE_LIB" ] || { echo "built shared objects not found" >&2; exit 1; }
    stage_one "$IMATH_LIB"
    stage_one "$CORE_LIB"
    for file in "$DEST"/*.so.*; do
      patchelf --set-rpath '$ORIGIN' "$file"
      if readelf -d "$file" | grep -E 'RPATH|RUNPATH' | grep -v '\$ORIGIN' >/dev/null; then
        echo "non-relocatable rpath in $file" >&2
        exit 1
      fi
    done
    ;;
  windows-x64)
    for runtime in libgcc_s_seh-1.dll libstdc++-6.dll libwinpthread-1.dll; do
      runtime_path="$(command -v "$runtime" 2>/dev/null || true)"
      [ -z "$runtime_path" ] || stage_one "$runtime_path"
    done
    IMATH_LIB="$(find "$PREFIX/bin" -type f -iname '*Imath*.dll' | sort | tail -1)"
    CORE_LIB="$(find "$PREFIX/bin" -type f -iname '*OpenEXRCore*.dll' | sort | tail -1)"
    [ -n "$IMATH_LIB" ] && [ -n "$CORE_LIB" ] || { echo "built DLLs not found" >&2; exit 1; }
    stage_one "$IMATH_LIB"
    stage_one "$CORE_LIB"
    ;;
esac

cp "$SRC/LICENSE.md" "$DEST/LICENSE.OpenEXR"
cp "$SRC/PATENTS" "$DEST/PATENTS.OpenEXR"
cp "$SRC/external/deflate/COPYING" "$DEST/LICENSE.libdeflate"
cp "$SRC/external/OpenJPH/LICENSE" "$DEST/LICENSE.OpenJPH"
IMATH_LICENSE="$(find "$BUILD_DIR/_deps" -maxdepth 3 -type f -iname 'LICENSE*' -path '*imath*' | head -1)"
[ -n "$IMATH_LICENSE" ] || { echo "Imath license not found" >&2; exit 1; }
cp "$IMATH_LICENSE" "$DEST/LICENSE.Imath"

{
  echo "OpenEXR $OPENEXR_VERSION / Imath $IMATH_VERSION ($PLATFORM), built from unmodified source"
  echo "OpenEXR source: https://github.com/AcademySoftwareFoundation/openexr/archive/refs/tags/v$OPENEXR_VERSION.tar.gz"
  echo "Imath source: https://github.com/AcademySoftwareFoundation/Imath/tree/v$IMATH_VERSION"
  echo "License: BSD-3-Clause (see LICENSE.OpenEXR, PATENTS.OpenEXR, and LICENSE.Imath)"
  echo "Statically bundled: libdeflate (MIT, see LICENSE.libdeflate) and OpenJPH (BSD-2-Clause, see LICENSE.OpenJPH)"
  echo "CMake: shared libraries; tests, tools, examples, Python disabled; internal libdeflate/OpenJPH/Imath enabled"
} > "$DEST/BUILD-INFO.txt"

echo "Staged $(wc -l < "$DEST/manifest.txt" | tr -d ' ') libraries into $DEST"
