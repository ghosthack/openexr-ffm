package io.github.ghosthack.openexrffm;

import io.github.ghosthack.openexrffm.openexr.OpenEXR;
import io.github.ghosthack.openexrffm.openexr.exr_context_initializer_t;
import io.github.ghosthack.openexrffm.openexr.exr_decode_pipeline_t;
import io.github.ghosthack.openexrffm.openexr.exr_encode_pipeline_t;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/** Java equivalents of OpenEXRCore initializer macros and error helpers. */
public final class OpenEXRSupport {
    private OpenEXRSupport() {}

    /** Allocates and fills {@code EXR_DEFAULT_CONTEXT_INITIALIZER}. */
    public static MemorySegment defaultContextInitializer(Arena arena) {
        MemorySegment value = arena.allocate(exr_context_initializer_t.layout());
        exr_context_initializer_t.size(value, exr_context_initializer_t.layout().byteSize());
        exr_context_initializer_t.zip_level(value, -2);
        exr_context_initializer_t.dwa_quality(value, -1.0f);
        return value;
    }

    /** Allocates and fills {@code EXR_DECODE_PIPELINE_INITIALIZER}. */
    public static MemorySegment defaultDecodePipeline(Arena arena) {
        MemorySegment value = arena.allocate(exr_decode_pipeline_t.layout());
        exr_decode_pipeline_t.decode_flags(value, (short) 0);
        exr_decode_pipeline_t.pipe_size(value, exr_decode_pipeline_t.layout().byteSize());
        return value;
    }

    /** Allocates and fills {@code EXR_ENCODE_PIPELINE_INITIALIZER}. */
    public static MemorySegment defaultEncodePipeline(Arena arena) {
        MemorySegment value = arena.allocate(exr_encode_pipeline_t.layout());
        exr_encode_pipeline_t.encode_flags(value, (short) 0);
        exr_encode_pipeline_t.pipe_size(value, exr_encode_pipeline_t.layout().byteSize());
        return value;
    }

    /** Throws {@link OpenEXRException} unless {@code result} is success. */
    public static void check(int result) {
        if (result != OpenEXR.EXR_ERR_SUCCESS()) {
            throw new OpenEXRException(result, errorMessage(result));
        }
    }

    /** Returns the native symbolic code and default message. */
    public static String errorMessage(int result) {
        String code = nativeString(OpenEXR.exr_get_error_code_as_string(result));
        String detail = nativeString(OpenEXR.exr_get_default_error_message(result));
        return code + ": " + detail;
    }

    private static String nativeString(MemorySegment pointer) {
        return pointer.equals(MemorySegment.NULL) ? "unknown" : pointer.reinterpret(4096).getString(0);
    }
}
