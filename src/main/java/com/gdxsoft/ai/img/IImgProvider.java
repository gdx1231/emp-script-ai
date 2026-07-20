package com.gdxsoft.ai.img;

import java.io.IOException;

/**
 * Contract for a single image generation provider.
 *
 * @since 1.2.0
 */
public interface IImgProvider {

    ImgProviderType getProviderType();

    /** Provider URL (full endpoint). */
    String getApiUrl();
    void setApiUrl(String url);

    /** API key. */
    String getApiKey();
    void setApiKey(String key);

    /**
     * Set a provider-specific configuration value
     * (e.g. {@code organization} for OpenAI).
     */
    void setConfig(String key, String value);
    String getConfig(String key);

    /**
     * Synchronous image generation.
     *
     * @throws IOException          on transport or HTTP errors
     * @throws InterruptedException on thread interruption
     */
    ImgResponse generate(ImgRequest request) throws IOException, InterruptedException;

    /**
     * Render the request as a {@code curl} command (for debugging / logging).
     * Sensitive headers (e.g. {@code Authorization}) should be redacted.
     */
    String curl(ImgRequest request);
}
