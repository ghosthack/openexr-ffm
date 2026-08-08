package io.github.ghosthack.openexrffm;

/** An error result reported by OpenEXRCore. */
public final class OpenEXRException extends RuntimeException {
    private final int code;

    public OpenEXRException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** Returns the native {@code exr_result_t}. */
    public int code() {
        return code;
    }
}

