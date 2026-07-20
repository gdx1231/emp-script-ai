package com.gdxsoft.ai.img;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * High-level facade for image generation.
 * <p>
 * Typical use:
 * <pre>{@code
 * // URL mode — no OOM risk
 * ImgResponse r = ImgClient.of("openai_img")
 *     .apiKey("sk-...")
 *     .generate("A cute cat wearing a spacesuit");
 * System.out.println(r.getFirstImage().getUrl());
 *
 * // OOM-safe: stream directly to files
 * List&lt;Path&gt; files = ImgClient.of("doubao_img")
 *     .apiKey("...")
 *     .generateToFiles("A beautiful landscape", Path.of("/tmp/images"));
 * }</pre>
 *
 * @since 1.2.0
 */
public final class ImgClient {
    private final IImgProvider provider;

    public ImgClient(IImgProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    /** Convenience factory — creates the provider and returns a client. */
    public static ImgClient of(String providerName) {
        return new ImgClient(ImgProviderFactory.create(providerName));
    }

    /** Convenience factory using an already-configured provider. */
    public static ImgClient of(IImgProvider provider) {
        return new ImgClient(provider);
    }

    /** Set the API key on the underlying provider (fluent). */
    public ImgClient apiKey(String key) {
        provider.setApiKey(key);
        return this;
    }

    /** Set the API URL on the underlying provider (fluent). */
    public ImgClient apiUrl(String url) {
        provider.setApiUrl(url);
        return this;
    }

    /** Set a provider-specific config value (fluent). */
    public ImgClient config(String key, String value) {
        provider.setConfig(key, value);
        return this;
    }

    /** Generate an image from a prompt with default options. */
    public ImgResponse generate(String prompt) throws IOException, InterruptedException {
        return provider.generate(new ImgRequest(new ImgOptions(prompt)));
    }

    /** Generate with the supplied options. */
    public ImgResponse generate(ImgOptions options) throws IOException, InterruptedException {
        return provider.generate(new ImgRequest(options));
    }

    /** Generate with a fully-formed request. */
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        return provider.generate(request);
    }

    // ==== OOM-safe convenience methods ====

    /**
     * Generate and save directly to files (streaming, no full buffering in memory).
     * Uses URL response format to avoid base64 in memory.
     *
     * @param prompt image description
     * @param outputDir directory to save images
     * @return list of saved file paths
     */
    public List<Path> generateToFiles(String prompt, Path outputDir)
            throws IOException, InterruptedException {
        return generateToFiles(new ImgOptions(prompt).responseFormat("url"), outputDir);
    }

    /**
     * Generate with options and save directly to files.
     * Automatically uses URL format to avoid base64 OOM.
     */
    public List<Path> generateToFiles(ImgOptions options, Path outputDir)
            throws IOException, InterruptedException {
        if (!"b64_json".equals(options.getResponseFormat())) {
            options.responseFormat("url"); // URL mode avoids base64 in memory
        }
        ImgResponse resp = generate(options);
        List<Path> files = resp.saveAll(outputDir);
        // Release any base64 data
        for (ImgResponse.GeneratedImage img : resp.getImages()) {
            img.release();
        }
        return files;
    }

    public IImgProvider getProvider() { return provider; }
}
