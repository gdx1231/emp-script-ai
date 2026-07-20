package com.gdxsoft.ai.img;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

/**
 * Unified result of an image generation request.
 * <p>
 * All providers return instances of this class so callers never deal with
 * provider-specific response shapes.
 *
 * @since 1.2.0
 */
public class ImgResponse {
    private final List<GeneratedImage> images;
    private final Long created;
    private final String revisedPrompt;
    private final String model;
    private final JSONObject usage;
    private final JSONObject raw;

    public ImgResponse(List<GeneratedImage> images, Long created, String revisedPrompt,
                       String model, JSONObject usage, JSONObject raw) {
        this.images = images == null ? Collections.emptyList()
                : Collections.unmodifiableList(images);
        this.created = created;
        this.revisedPrompt = revisedPrompt;
        this.model = model;
        this.usage = usage;
        this.raw = raw;
    }

    /** List of generated images (never null). */
    public List<GeneratedImage> getImages() { return images; }

    /** First generated image, or null if empty. */
    public GeneratedImage getFirstImage() {
        return images.isEmpty() ? null : images.get(0);
    }

    /** UNIX timestamp (seconds) when the images were created. */
    public Long getCreated() { return created; }

    /** Revised prompt returned by the model (e.g. DALL-E 3). */
    public String getRevisedPrompt() { return revisedPrompt; }

    /** Model identifier used for this generation. */
    public String getModel() { return model; }

    /** Token usage (if provided by the API), or null. */
    public JSONObject getUsage() { return usage; }

    /** Original provider response for debugging. */
    public JSONObject getRaw() { return raw; }

    // ==== Memory-safe file I/O ====

    /**
     * Save all images to the given directory.
     * <ul>
     *   <li>URL images are downloaded (streamed, no full buffering)</li>
     *   <li>Base64 images are decoded and written (streamed via pipe, no full buffering)</li>
     * </ul>
     * @param dir target directory (will be created if not exists)
     * @return list of saved file paths, one per image
     */
    public List<Path> saveAll(Path dir) throws IOException {
        return saveAll(dir, "img_");
    }

    /**
     * Save all images with a custom filename prefix.
     * Files are named {@code prefix_0.png}, {@code prefix_1.png}, etc.
     */
    public List<Path> saveAll(Path dir, String prefix) throws IOException {
        Files.createDirectories(dir);
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            Path file = dir.resolve(prefix + i + ".png");
            images.get(i).writeToFile(file);
            paths.add(file);
        }
        return paths;
    }

    /**
     * A single generated image — either a URL or base64-encoded data.
     */
    public static class GeneratedImage {
        private final String url;
        private String b64Json;
        private final String revisedPrompt;

        public GeneratedImage(String url, String b64Json, String revisedPrompt) {
            this.url = url;
            this.b64Json = b64Json;
            this.revisedPrompt = revisedPrompt;
        }

        /** Publicly accessible image URL (may be null if base64 mode). */
        public String getUrl() { return url; }

        /** Base64-encoded image data (may be null if URL mode). */
        public String getB64Json() { return b64Json; }

        /** Per-image revised prompt, if available. */
        public String getRevisedPrompt() { return revisedPrompt; }

        /** True if this image has a URL (not base64). */
        public boolean isUrl() { return url != null; }

        /** True if this image is base64-encoded. */
        public boolean isBase64() { return b64Json != null; }

        // ==== Memory-safe file I/O ====

        /**
         * Write the image to a file. Automatically handles URL download
         * and base64 decoding. Uses streaming to avoid OOM.
         *
         * @param file target file path
         * @return the file path (same as input)
         */
        public Path writeToFile(Path file) throws IOException {
            if (isUrl()) {
                return downloadToFile(file);
            }
            if (isBase64()) {
                return decodeToFile(file);
            }
            throw new IOException("No image data (neither URL nor base64)");
        }

        /**
         * Write to a temp file. The file will be deleted on JVM exit.
         */
        public Path writeToTempFile() throws IOException {
            Path tmp = Files.createTempFile("img_gen_", ".png");
            writeToFile(tmp);
            tmp.toFile().deleteOnExit();
            return tmp;
        }

        /**
         * Write to a file and release the in-memory base64 string to free heap.
         * After this call, {@link #getB64Json()} returns null.
         */
        public Path writeToFileAndRelease(Path file) throws IOException {
            Path p = writeToFile(file);
            this.b64Json = null; // free memory
            return p;
        }

        /**
         * Release the base64 string from memory without writing.
         * Useful after you've already saved or don't need the data.
         */
        public void release() {
            this.b64Json = null;
        }

        private Path downloadToFile(Path file) throws IOException {
            try (InputStream in = URI.create(url).toURL().openStream();
                 OutputStream out = Files.newOutputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            return file;
        }

        private Path decodeToFile(Path file) throws IOException {
            // Use piped streams to avoid holding the full decoded image in memory
            try (InputStream in = Base64.getDecoder().wrap(
                    new java.io.ByteArrayInputStream(b64Json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                 OutputStream out = Files.newOutputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            return file;
        }
    }
}
