package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gdxsoft.ai.stt.AudioSource.Base64Source;
import com.gdxsoft.ai.stt.AudioSource.BytesSource;
import com.gdxsoft.ai.stt.AudioSource.FileSource;
import com.gdxsoft.ai.stt.AudioSource.StreamSource;
import com.gdxsoft.ai.stt.AudioSource.UrlSource;

class AudioSourceTest {

    @Test
    void fromBytes_roundTripsGetters(@TempDir Path tmp) throws IOException {
        byte[] data = { 1, 2, 3 };
        AudioSource s = AudioSource.fromBytes(data, "audio/mp3", "x.mp3");
        assertInstanceOf(BytesSource.class, s);
        assertEquals("audio/mp3", s.mimeType());
        assertEquals("x.mp3", s.filenameHint());
        assertArrayEquals(data, ((BytesSource) s).data());
        assertArrayEquals(data, s.materialize());
    }

    @Test
    void fromFile_readsBytes(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("hello.wav");
        Files.write(f, new byte[] { 'h', 'i' });
        AudioSource s = AudioSource.fromFile(f);
        assertInstanceOf(FileSource.class, s);
        assertEquals("hello.wav", s.filenameHint());
        assertArrayEquals(new byte[] { 'h', 'i' }, s.materialize());
        assertNotNull(s.mimeType(), "mime type should be probed (or defaulted)");
        assertTrue(s.mimeType().startsWith("audio/"));
    }

    @Test
    void fromBase64_decodes(@TempDir Path tmp) throws IOException {
        String b64 = java.util.Base64.getEncoder().encodeToString(new byte[] { 4, 5, 6 });
        AudioSource s = AudioSource.fromBase64(b64, "audio/wav", "x.wav");
        assertInstanceOf(Base64Source.class, s);
        assertEquals("x.wav", s.filenameHint());
        assertArrayEquals(new byte[] { 4, 5, 6 }, s.materialize());
    }

    @Test
    void fromUrl_defaultsFilenameFromUrlPath() {
        AudioSource s = AudioSource.fromUrl("https://example.com/path/clip.mp3", "audio/mpeg");
        assertInstanceOf(UrlSource.class, s);
        assertEquals("clip.mp3", s.filenameHint());
        assertEquals("audio/mpeg", s.mimeType());
    }

    @Test
    void fromStream_consumesAndCloses() throws IOException {
        AudioSource s = AudioSource.fromStream(
                new ByteArrayInputStream(new byte[] { 7, 8, 9 }), "audio/wav", "y.wav");
        assertInstanceOf(StreamSource.class, s);
        assertArrayEquals(new byte[] { 7, 8, 9 }, s.materialize());
    }

    @Test
    void fromBytes_requiresFilename() {
        assertThrows(IllegalArgumentException.class,
                () -> AudioSource.fromBytes(new byte[0], "audio/mp3", null));
        assertThrows(IllegalArgumentException.class,
                () -> AudioSource.fromBytes(new byte[0], "audio/mp3", ""));
    }
}