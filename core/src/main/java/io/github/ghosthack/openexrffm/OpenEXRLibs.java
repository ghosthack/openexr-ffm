package io.github.ghosthack.openexrffm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the OpenEXRCore shared library used by the generated bindings.
 *
 * <p>Resolution order is an explicit native directory, bundled platform
 * natives, and finally libraries already loaded by the host process. Set the
 * directory with {@code -Dopenexrffm.libdir=<dir>} or
 * {@code OPENEXR_FFM_LIBDIR}.</p>
 */
public final class OpenEXRLibs {
    private static final Arena LIBRARY_ARENA = Arena.ofAuto();
    private static volatile SymbolLookup lookup;

    private OpenEXRLibs() {}

    /** Returns the lazily initialized lookup chain. */
    public static SymbolLookup lookup() {
        SymbolLookup value = lookup;
        if (value == null) {
            synchronized (OpenEXRLibs.class) {
                value = lookup;
                if (value == null) {
                    lookup = value = resolve();
                }
            }
        }
        return value;
    }

    private static SymbolLookup resolve() {
        String override = System.getProperty(
                "openexrffm.libdir", System.getenv("OPENEXR_FFM_LIBDIR"));
        SymbolLookup resolved = override != null
                ? fromDirectory(Path.of(override))
                : fromBundle();
        SymbolLookup fallback = SymbolLookup.loaderLookup()
                .or(Linker.nativeLinker().defaultLookup());
        return resolved == null ? fallback : resolved.or(fallback);
    }

    private static SymbolLookup fromDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("openexrffm.libdir does not exist: " + directory);
        }
        List<Path> libraries = new ArrayList<>();
        try (var files = Files.list(directory)) {
            files.filter(OpenEXRLibs::isOpenEXRLibrary).forEach(libraries::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (libraries.isEmpty()) {
            throw new IllegalStateException("no OpenEXRCore shared library found in " + directory);
        }
        libraries.sort((a, b) -> Integer.compare(loadRank(a), loadRank(b)));
        return chain(libraries);
    }

    private static boolean isOpenEXRLibrary(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean shared = name.endsWith(".dylib") || name.endsWith(".dll") || name.contains(".so");
        return shared && (name.contains("imath") || name.contains("openexrcore"));
    }

    private static int loadRank(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.contains("imath") ? 0 : 1;
    }

    private static SymbolLookup fromBundle() {
        String classifier = classifier();
        String base = "natives/" + classifier + "/";
        List<String> manifest = readManifest(base);
        if (manifest == null) {
            return null;
        }
        Path cache = Path.of(System.getProperty("user.home"), ".cache", "openexr-ffm",
                Version.VERSION + "-" + classifier);
        try {
            Files.createDirectories(cache);
            List<Path> libraries = new ArrayList<>();
            for (String name : manifest) {
                libraries.add(extract(base + name, cache.resolve(name)));
            }
            return chain(libraries);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "failed to extract bundled OpenEXR natives to " + cache, e);
        }
    }

    private static List<String> readManifest(String base) {
        try (InputStream in = OpenEXRLibs.class.getClassLoader()
                .getResourceAsStream(base + "manifest.txt")) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path extract(String resource, Path target) throws IOException {
        try (InputStream in = OpenEXRLibs.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing classpath resource " + resource);
            }
            byte[] bytes = in.readAllBytes();
            if (Files.exists(target) && Files.size(target) == bytes.length) {
                return target;
            }
            Path temporary = Files.createTempFile(
                    target.getParent(), target.getFileName().toString(), ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }
    }

    private static SymbolLookup chain(List<Path> libraries) {
        SymbolLookup result = null;
        for (Path library : libraries) {
            SymbolLookup next = SymbolLookup.libraryLookup(
                    library.toAbsolutePath().toString(), LIBRARY_ARENA);
            result = result == null ? next : result.or(next);
        }
        return result;
    }

    static String classifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
        if (os.contains("mac") && arm64) return "macos-arm64";
        if (os.contains("win") && x64) return "windows-x64";
        if (os.contains("linux") && x64) return "linux-x64";
        throw new UnsupportedOperationException(
                "no bundled OpenEXR natives for " + os + " / " + arch
                        + "; set openexrffm.libdir to a compatible installation");
    }
}

