package com.gdxsoft.ai.img;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common scaffolding for image generation providers — config map, defaults,
 * apiUrl/apiKey state.
 * <p>
 * Providers are <b>thread-safe</b> — apiKey/apiUrl are volatile,
 * extras uses {@link ConcurrentHashMap}. A single provider instance can be
 * shared across threads.
 * <p>
 * Concrete providers must implement {@link #generate(ImgRequest)} and
 * {@link #curl(ImgRequest)}. Transport is via {@code HttpUtils.createHttpClient()}.
 *
 * @since 1.2.0
 */
public abstract class ImgProviderBase implements IImgProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ImgProviderBase.class);

    protected volatile String apiUrl;
    protected volatile String apiKey;
    protected final Map<String, String> extras = new ConcurrentHashMap<>();

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
     * Build a debug curl line header. Sensitive headers are masked.
     */
    protected StringBuilder curlHeader(StringBuilder sb, String name, String value, boolean isSensitive) {
        sb.append("-H '").append(name).append(": ");
        if (value == null) sb.append("'");
        else if (isSensitive) sb.append("****'");
        else sb.append(value.replace("'", "'\\''")).append("'");
        return sb;
    }
}
