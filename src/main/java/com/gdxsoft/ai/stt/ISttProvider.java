package com.gdxsoft.ai.stt;

import java.io.IOException;

/**
 * Contract for a single speech-to-text provider.
 *
 * @since 1.1.0
 */
public interface ISttProvider {

    SttProviderType getProviderType();

    /** Provider URL (full endpoint). */
    String getApiUrl();
    void setApiUrl(String url);

    /** API key (may be optional for some local providers). */
    String getApiKey();
    void setApiKey(String key);

    /**
     * Set a provider-specific configuration value (e.g. {@code region} for Azure,
     * {@code format} for Local).
     */
    void setConfig(String key, String value);
    String getConfig(String key);

    /**
     * Synchronous transcription.
     *
     * @throws IOException          on transport or HTTP errors
     * @throws InterruptedException on thread interruption
     */
    SttResponse transcribe(SttRequest request) throws IOException, InterruptedException;

    /**
     * Render the request as a {@code curl} command (for debugging / logging).
     * Sensitive headers (e.g. {@code Authorization}) should be redacted.
     */
    String curl(SttRequest request);
}