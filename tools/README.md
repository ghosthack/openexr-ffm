# tools/

`jextract/gen-bindings.sh` expects the Java 22 jextract distribution at
`tools/jextract-22`, or an executable supplied through `JEXTRACT`.

For Apple Silicon:

```sh
curl -sfL https://download.java.net/java/early_access/jextract/22/6/openjdk-22-jextract+6-47_macos-aarch64_bin.tar.gz | tar xz -C tools/
```

jextract 22 emits code for the finalized FFM API, so the generated source runs
on JDK 22 and newer.
