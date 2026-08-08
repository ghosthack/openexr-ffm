package io.github.ghosthack.openexrffm;

import io.github.ghosthack.openexrffm.openexr.OpenEXR;
import io.github.ghosthack.openexrffm.openexr.exr_attr_box2i_t;
import io.github.ghosthack.openexrffm.openexr.exr_attr_v2i_t;
import io.github.ghosthack.openexrffm.openexr.exr_chunk_info_t;
import io.github.ghosthack.openexrffm.openexr.exr_coding_channel_info_t;
import io.github.ghosthack.openexrffm.openexr.exr_decode_pipeline_t;
import io.github.ghosthack.openexrffm.openexr.exr_encode_pipeline_t;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.ghosthack.openexrffm.OpenEXRSupport.check;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests against the bundled native libraries. */
class SmokeTest {

    @Test
    void bundledNativesReportPinnedVersion() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment major = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment minor = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment patch = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment extra = arena.allocate(ValueLayout.ADDRESS);
            OpenEXR.exr_get_library_version(major, minor, patch, extra);
            assertEquals(3, major.get(ValueLayout.JAVA_INT, 0));
            assertEquals(4, minor.get(ValueLayout.JAVA_INT, 0));
            assertEquals(14, patch.get(ValueLayout.JAVA_INT, 0));
        }
    }

    @Test
    void exposesNativeErrorsAsJavaExceptions() {
        OpenEXRException error = assertThrows(OpenEXRException.class,
                () -> check(OpenEXR.EXR_ERR_INVALID_ARGUMENT()));
        assertEquals(OpenEXR.EXR_ERR_INVALID_ARGUMENT(), error.code());
        assertTrue(error.getMessage().contains("INVALID_ARGUMENT"));
    }

    @Test
    void writesAndReadsFloatPixels() throws Exception {
        Path image = Files.createTempFile("openexr-ffm-", ".exr");
        try {
            writeImage(image);
            assertTrue(Files.size(image) > 100, "an EXR file was written");
            readAndVerifyImage(image);
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void writeImage(Path image) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment contextPointer = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment initializer = OpenEXRSupport.defaultContextInitializer(arena);
            check(OpenEXR.exr_start_write(contextPointer, arena.allocateFrom(image.toString()),
                    OpenEXR.EXR_WRITE_FILE_DIRECTLY(), initializer));
            MemorySegment context = contextPointer.get(ValueLayout.ADDRESS, 0);
            try {
                MemorySegment partIndex = arena.allocate(ValueLayout.JAVA_INT);
                check(OpenEXR.exr_add_part(context, arena.allocateFrom("beauty"),
                        OpenEXR.EXR_STORAGE_SCANLINE(), partIndex));
                assertEquals(0, partIndex.get(ValueLayout.JAVA_INT, 0));

                check(OpenEXR.exr_initialize_required_attr_simple(
                        context, 0, 2, 2, OpenEXR.EXR_COMPRESSION_ZIP()));
                check(OpenEXR.exr_add_channel(context, 0, arena.allocateFrom("R"),
                        OpenEXR.EXR_PIXEL_FLOAT(), OpenEXR.EXR_PERCEPTUALLY_LINEAR(), 1, 1));
                check(OpenEXR.exr_write_header(context));

                MemorySegment chunk = arena.allocate(exr_chunk_info_t.layout());
                check(OpenEXR.exr_write_scanline_chunk_info(context, 0, 0, chunk));
                assertEquals(2, exr_chunk_info_t.width(chunk));
                assertEquals(2, exr_chunk_info_t.height(chunk));

                MemorySegment pipeline = OpenEXRSupport.defaultEncodePipeline(arena);
                check(OpenEXR.exr_encoding_initialize(context, 0, chunk, pipeline));
                try {
                    assertEquals(1, exr_encode_pipeline_t.channel_count(pipeline));
                    MemorySegment channel = exr_encode_pipeline_t.channels(pipeline)
                            .reinterpret(exr_coding_channel_info_t.layout().byteSize());
                    MemorySegment pixels = arena.allocate(4L * ValueLayout.JAVA_FLOAT.byteSize(),
                            ValueLayout.JAVA_FLOAT.byteAlignment());
                    pixels.setAtIndex(ValueLayout.JAVA_FLOAT, 0, 0.25f);
                    pixels.setAtIndex(ValueLayout.JAVA_FLOAT, 1, 1.5f);
                    pixels.setAtIndex(ValueLayout.JAVA_FLOAT, 2, -2.0f);
                    pixels.setAtIndex(ValueLayout.JAVA_FLOAT, 3, 65504.0f);
                    configureChannel(channel, pixels, 2);
                    check(OpenEXR.exr_encoding_choose_default_routines(context, 0, pipeline));
                    check(OpenEXR.exr_encoding_run(context, 0, pipeline));
                } finally {
                    check(OpenEXR.exr_encoding_destroy(context, pipeline));
                }
            } finally {
                check(OpenEXR.exr_finish(contextPointer));
            }
        }
    }

    private static void readAndVerifyImage(Path image) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment contextPointer = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment initializer = OpenEXRSupport.defaultContextInitializer(arena);
            check(OpenEXR.exr_start_read(
                    contextPointer, arena.allocateFrom(image.toString()), initializer));
            MemorySegment context = contextPointer.get(ValueLayout.ADDRESS, 0);
            try {
                MemorySegment partCount = arena.allocate(ValueLayout.JAVA_INT);
                check(OpenEXR.exr_get_count(context, partCount));
                assertEquals(1, partCount.get(ValueLayout.JAVA_INT, 0));

                MemorySegment dataWindow = arena.allocate(exr_attr_box2i_t.layout());
                check(OpenEXR.exr_get_data_window(context, 0, dataWindow));
                assertEquals(0, exr_attr_v2i_t.x(exr_attr_box2i_t.min(dataWindow)));
                assertEquals(0, exr_attr_v2i_t.y(exr_attr_box2i_t.min(dataWindow)));
                assertEquals(1, exr_attr_v2i_t.x(exr_attr_box2i_t.max(dataWindow)));
                assertEquals(1, exr_attr_v2i_t.y(exr_attr_box2i_t.max(dataWindow)));

                MemorySegment chunk = arena.allocate(exr_chunk_info_t.layout());
                check(OpenEXR.exr_read_scanline_chunk_info(context, 0, 0, chunk));
                MemorySegment pipeline = OpenEXRSupport.defaultDecodePipeline(arena);
                check(OpenEXR.exr_decoding_initialize(context, 0, chunk, pipeline));
                try {
                    assertEquals(1, exr_decode_pipeline_t.channel_count(pipeline));
                    MemorySegment channel = exr_decode_pipeline_t.channels(pipeline)
                            .reinterpret(exr_coding_channel_info_t.layout().byteSize());
                    assertEquals("R", exr_coding_channel_info_t.channel_name(channel)
                            .reinterpret(256).getString(0));
                    MemorySegment pixels = arena.allocate(4L * ValueLayout.JAVA_FLOAT.byteSize(),
                            ValueLayout.JAVA_FLOAT.byteAlignment());
                    configureChannel(channel, pixels, 2);
                    check(OpenEXR.exr_decoding_choose_default_routines(context, 0, pipeline));
                    check(OpenEXR.exr_decoding_run(context, 0, pipeline));

                    assertEquals(0.25f, pixels.getAtIndex(ValueLayout.JAVA_FLOAT, 0));
                    assertEquals(1.5f, pixels.getAtIndex(ValueLayout.JAVA_FLOAT, 1));
                    assertEquals(-2.0f, pixels.getAtIndex(ValueLayout.JAVA_FLOAT, 2));
                    assertEquals(65504.0f, pixels.getAtIndex(ValueLayout.JAVA_FLOAT, 3));
                } finally {
                    check(OpenEXR.exr_decoding_destroy(context, pipeline));
                }
            } finally {
                check(OpenEXR.exr_finish(contextPointer));
            }
        }
    }

    private static void configureChannel(
            MemorySegment channel, MemorySegment pixels, int width) {
        exr_coding_channel_info_t.decode_to_ptr(channel, pixels);
        exr_coding_channel_info_t.user_bytes_per_element(channel, (short) Float.BYTES);
        exr_coding_channel_info_t.user_data_type(channel, (short) OpenEXR.EXR_PIXEL_FLOAT());
        exr_coding_channel_info_t.user_pixel_stride(channel, Float.BYTES);
        exr_coding_channel_info_t.user_line_stride(channel, width * Float.BYTES);
    }
}

