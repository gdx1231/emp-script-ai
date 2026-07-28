package com.gdxsoft.ai.tts;

import java.io.IOException;

/**
 * Contract for a single text-to-speech provider.
 *
 * @since 1.1.0
 */
public interface ITtsProvider {

    TtsProviderType getProviderType();

    /** Provider URL (full endpoint). */
    String getApiUrl();
    void setApiUrl(String url);

    /** API key / access token. */
    String getApiKey();
    void setApiKey(String key);

    /**
     * Set a provider-specific configuration value (e.g. {@code appid} / {@code cluster}
     * for Doubao, {@code model} / {@code voice} overrides).
     */
    void setConfig(String key, String value);
    String getConfig(String key);

    /**
     * Synchronous synthesis.
     *
     * @throws IOException          on transport or HTTP errors
     * @throws InterruptedException on thread interruption
     */
    TtsResponse synthesize(TtsRequest request) throws IOException, InterruptedException;

    /**
     * Render the request as a {@code curl} command (for debugging / logging).
     * Sensitive headers (e.g. {@code Authorization}) should be redacted.
     */
    String curl(TtsRequest request);
}
