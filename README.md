# openexr-ffm

Java 22 Panama FFM bindings for the complete OpenEXRCore C API, with bundled
natives built from unmodified source. No JNI and no user-installed OpenEXR.

| Artifact | Contents | License |
|---|---|---|
| `io.github.ghosthack:openexr-ffm` | jextract stubs, runtime loader, initializer/error helpers | MIT |
| `io.github.ghosthack:openexr-ffm-natives` (`macos-arm64`, `windows-x64`, `linux-x64`) | OpenEXRCore 3.4.14 and Imath 3.2.2 shared libraries | BSD-3-Clause and other permissive licenses |

OpenEXR 3.4.14 is a security-focused release. The binding exposes all 164
public `exr_*` functions plus the public structs, non-variadic callbacks, enums, and
constants used for scanline, tiled, multipart, deep-data, metadata, custom I/O,
decode, and encode workflows.

OpenEXRCore's variadic `exr_stream_error_func_ptr_t` cannot be represented by
jextract and is passed as a raw `MemorySegment` in the generated custom-I/O
callback signatures. No exported `exr_*` function is omitted.

## Use

```xml
<dependency>
  <groupId>io.github.ghosthack</groupId>
  <artifactId>openexr-ffm</artifactId>
  <version>3.4.14-0.1.0</version>
</dependency>
<dependency>
  <groupId>io.github.ghosthack</groupId>
  <artifactId>openexr-ffm-natives</artifactId>
  <version>3.4.14-0.1.0</version>
  <classifier>macos-arm64</classifier> <!-- windows-x64 or linux-x64 -->
  <scope>runtime</scope>
</dependency>
```

On the module path, declare `requires openexr.ffm;` and run with
`--enable-native-access=openexr.ffm`. On the class path, use
`--enable-native-access=ALL-UNNAMED`.

```java
import io.github.ghosthack.openexrffm.OpenEXRSupport;
import io.github.ghosthack.openexrffm.openexr.OpenEXR;

try (var arena = java.lang.foreign.Arena.ofConfined()) {
    var major = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
    var minor = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
    var patch = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
    var extra = arena.allocate(java.lang.foreign.ValueLayout.ADDRESS);
    OpenEXR.exr_get_library_version(major, minor, patch, extra);
}
```

`OpenEXRSupport.defaultContextInitializer()` is the Java equivalent of
`EXR_DEFAULT_CONTEXT_INITIALIZER`; corresponding encode/decode pipeline helpers
and checked error conversion are provided as well. See `SmokeTest` for a full
2×2 float image write/compress/read/decompress round trip.

### Library resolution

1. `-Dopenexrffm.libdir=<dir>` or `OPENEXR_FFM_LIBDIR` selects a compatible
   existing OpenEXRCore/Imath installation;
2. otherwise the matching classifier jar is extracted once under
   `~/.cache/openexr-ffm/<artifact-version>-<platform>/`;
3. libraries already loaded by the host application are the final fallback.

### Generated array accessors

jextract 22 indexed accessors for array fields are relative to the containing
field rather than the struct. Prefer the field-slice form for nonzero-offset
arrays:

```java
MemorySegment pad = exr_context_initializer_t.pad(initializer);
byte first = pad.getAtIndex(ValueLayout.JAVA_BYTE, 0);
```

## Build

```sh
mkdir -p build
curl -sfL https://github.com/AcademySoftwareFoundation/openexr/archive/refs/tags/v3.4.14.tar.gz | tar xz -C build
build-natives/macos-arm64.sh
JEXTRACT=tools/jextract-22/bin/jextract jextract/gen-bindings.sh
mvn clean install
```

Run the matching native script on Linux x64 or in an MSYS2 MINGW64 shell on
Windows x64. Native resources are build outputs and are not committed. The
build forces the release-vendored libdeflate and OpenJPH into OpenEXRCore and
stages only its runtime closure with licenses and exact provenance.
