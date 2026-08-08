package io.github.ghosthack.openexrffm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenEXRLibsTest {
    @Test
    void detectsCurrentPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String expected = os.contains("mac") ? "macos-arm64"
                : os.contains("win") ? "windows-x64" : "linux-x64";
        assertEquals(expected, OpenEXRLibs.classifier());
    }
}
