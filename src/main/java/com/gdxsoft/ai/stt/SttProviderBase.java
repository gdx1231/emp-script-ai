package com.gdxsoft.ai.stt;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common scaffolding for STT providers — config map, defaults, apiUrl/apiKey state.
 * <p>
 * Concrete providers must implement {@link #transcribe(SttRequest)} and
 * {@link #curl(SttRequest)}. Transport is via {@code HttpUtils.createHttpClient()}.
 *
 * @since 1.1.0
 */
public abstract class SttProviderBase implements ISttProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(SttProviderBase.class);

    protected String apiUrl;
    protected String apiKey;
    protected final Map<String, String> extras = new HashMap<>();

    @Override
    public String getApiUrl() { return apiUrl; }
    @Override
    public void setApiUrl(String url) { this.apiUrl = url; }

    @Override
    public String getApiKey() { return apiKey; }
    @Override
    public void setApiKey(String key) { this.apiKey = key; }

    @Override
    public void setConfig(String key, String value) {
        if (key == null) return;
        if (value == null) extras.remove(key);
        else extras.put(key, value);
    }

    @Override
    public String getConfig(String key) {
        return key == null ? null : extras.get(key);
    }

    /**
     * Build a debug curl line. Sensitive headers should be masked.
     */
    protected StringBuilder curlHeader(StringBuilder sb, String name, String value, boolean isSensitive) {
        sb.append("-H '").append(name).append(": ");
        if (value == null) sb.append("'");
        else if (isSensitive) sb.append("****'");
        else sb.append(value.replace("'", "'\\''")).append("'");
        return sb;
    }
}