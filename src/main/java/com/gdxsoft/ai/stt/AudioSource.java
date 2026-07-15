package com.gdxsoft.ai.stt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Audio input for an STT request.
 * <p>
 * Use the static factories — implementations are records ({@link FileSource},
 * {@link BytesSource}, {@link Base64Source}, {@link UrlSource}, {@link StreamSource}).
 *
 * @since 1.1.0
 */
public sealed interface AudioSource
        permits AudioSource.FileSource,
                AudioSource.BytesSource,
                AudioSource.Base64Source,
                AudioSource.UrlSource,
                AudioSource.StreamSource {

    /** MIME type (e.g. {@code audio/mpeg}, {@code audio/wav}). May be {@code null} for {@link UrlSource}. */
    String mimeType();

    /**
     * Filename hint for multipart uploads. May be {@code null} for {@link UrlSource} or {@link StreamSource}
     * when not supplied; providers will fall back to a default.
     */
    String filenameHint();

    /**
     * Read all bytes (used by providers that don't accept remote URLs or streaming bodies).
     * <p>
     * For {@link UrlSource} this fetches the URL once and caches; for {@link StreamSource} it
     * reads the stream fully into memory and closes it.
     *
     * @return raw audio bytes
     * @throws IOException on read failure
     */
    byte[] materialize() throws IOException;

    // ------------------------------------------------------------
    // Static factories
    // ------------------------------------------------------------

    static AudioSource fromFile(Path p) throws IOException {
        String mt = Files.probeContentType(p);
        if (mt == null) mt = "audio/mpeg";
        return new FileSource(p, mt, p.getFileName().toString());
    }

    static AudioSource fromBytes(byte[] data, String mimeType, String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("filename is required");
        }
        return new BytesSource(data == null ? new byte[0] : data, mimeType, filename);
    }

    static AudioSource fromBase64(String b64, String mimeType, String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("filename is required");
        }
        return new Base64Source(b64, mimeType, filename);
    }

    static AudioSource fromUrl(String url, String mimeType) {
        return new UrlSource(url, mimeType);
    }

    static AudioSource fromStream(InputStream in, String mimeType, String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("filename is required");
        }
        return new StreamSource(in, mimeType, filename);
    }

    // ------------------------------------------------------------
    // Implementations
    // ------------------------------------------------------------

    record FileSource(Path path, String mimeType, String filenameHint) implements AudioSource {
        @Override
        public byte[] materialize() throws IOException {
            return Files.readAllBytes(path);
        }
    }

    record BytesSource(byte[] data, String mimeType, String filenameHint) implements AudioSource {
        @Override
        public byte[] materialize() {
            return data;
        }
    }

    record Base64Source(String base64, String mimeType, String filenameHint) implements AudioSource {
        @Override
        public byte[] materialize() {
            return java.util.Base64.getDecoder().decode(base64);
        }
    }

    record UrlSource(String url, String mimeType) implements AudioSource {
        @Override
        public String filenameHint() {
            int slash = url.lastIndexOf('/');
            return slash >= 0 ? url.substring(slash + 1) : url;
        }

        @Override
        public byte[] materialize() throws IOException {
            try (InputStream in = java.net.URI.create(url).toURL().openStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        }
    }

    record StreamSource(InputStream in, String mimeType, String filenameHint) implements AudioSource {
        @Override
        public byte[] materialize() throws IOException {
            try (InputStream input = in) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = input.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        }
    }
}