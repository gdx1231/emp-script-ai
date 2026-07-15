package com.gdxsoft.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.HttpUtils.MultipartBody;
import com.gdxsoft.ai.HttpUtils.MultipartPart;

/**
 * Byte-exact tests for {@link HttpUtils#buildMultipart(List)}.
 */
class HttpUtilsMultipartTest {

    @Test
    void contentTypeIncludesBoundary() throws IOException {
        MultipartBody body = HttpUtils.buildMultipart(
                Collections.singletonList(MultipartPart.text("model", "whisper-1")),
                "TESTBOUNDARY");
        assertEquals("multipart/form-data; boundary=TESTBOUNDARY", body.contentType());
        assertEquals("TESTBOUNDARY", body.boundary());
    }

    @Test
    void textPartShape() throws IOException {
        MultipartPart p = MultipartPart.text("model", "whisper-1");
        MultipartBody body = HttpUtils.buildMultipart(Collections.singletonList(p), "BOUND");

        String expected =
                "--BOUND\r\n" +
                "Content-Disposition: form-data; name=\"model\"\r\n" +
                "\r\n" +
                "whisper-1\r\n" +
                "--BOUND--\r\n";
        assertEquals(expected, new String(body.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void filePartShape() throws IOException {
        byte[] payload = new byte[] { 1, 2, 3, 4, 5 };
        MultipartPart p = MultipartPart.file("file", payload, "clip.mp3", "audio/mpeg");
        MultipartBody body = HttpUtils.buildMultipart(Collections.singletonList(p), "BOUND");

        // Build the expected byte stream manually so the raw bytes survive in the data section
        ByteArrayOutputStream exp = new ByteArrayOutputStream();
        String crlf = "\r\n";
        exp.write(("--BOUND" + crlf).getBytes(StandardCharsets.UTF_8));
        exp.write("Content-Disposition: form-data; name=\"file\"; filename=\"clip.mp3\""
                .getBytes(StandardCharsets.UTF_8));
        exp.write(crlf.getBytes(StandardCharsets.UTF_8));
        exp.write("Content-Type: audio/mpeg".getBytes(StandardCharsets.UTF_8));
        exp.write(crlf.getBytes(StandardCharsets.UTF_8));
        exp.write(crlf.getBytes(StandardCharsets.UTF_8));
        exp.write(payload);
        exp.write(crlf.getBytes(StandardCharsets.UTF_8));
        exp.write(("--BOUND--" + crlf).getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(exp.toByteArray(), body.bytes());
    }

    @Test
    void multiplePartsInOrder() throws IOException {
        List<MultipartPart> parts = Arrays.asList(
                MultipartPart.text("model", "whisper-1"),
                MultipartPart.text("language", "en"),
                MultipartPart.file("file", new byte[] { 'A' }, "x.wav", "audio/wav"));
        MultipartBody body = HttpUtils.buildMultipart(parts, "B");

        String text = new String(body.bytes(), StandardCharsets.UTF_8);
        int posModel = text.indexOf("name=\"model\"");
        int posLang = text.indexOf("name=\"language\"");
        int posFile = text.indexOf("name=\"file\"");
        assertTrue(posModel >= 0 && posLang > posModel && posFile > posLang,
                "parts must appear in declared order");
    }

    @Test
    void randomBoundaryIsNonEmptyHex() {
        String b = HttpUtils.newMultipartBoundary();
        assertNotNull(b);
        assertFalse(b.isEmpty());
        assertTrue(b.matches("[0-9a-f]{32}"), "expected 32-char hex UUID-derived boundary, got: " + b);
    }

    @Test
    void filePartRequiresFilename() {
        assertThrows(IllegalArgumentException.class,
                () -> MultipartPart.file("file", new byte[0], null, "audio/mpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> MultipartPart.file("file", new byte[0], "", "audio/mpeg"));
    }

    @Test
    void buildRejectsEmptyBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpUtils.buildMultipart(Collections.emptyList(), ""));
    }
}