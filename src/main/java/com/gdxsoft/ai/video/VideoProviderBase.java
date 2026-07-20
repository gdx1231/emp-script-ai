package com.gdxsoft.ai.video;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.HttpUtils;

/**
 * Common scaffolding for video generation providers.
 * Thread-safe via volatile fields and ConcurrentHashMap.
 *
 * @since 1.3.0
 */
public abstract class VideoProviderBase implements IVideoProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(VideoProviderBase.class);

    protected volatile String apiUrl;
    protected volatile String apiKey;
    protected final Map<String, String> extras = new ConcurrentHashMap<>();

    /** Max polling attempts for async tasks. */
    protected int maxPollCount = 120;
    /** Delay between polls in ms. */
    protected long pollDelayMs = 3000;

    @Override public String getApiUrl() { return apiUrl; }
    @Override public void setApiUrl(String url) { this.apiUrl = url; }

    @Override public String getApiKey() { return apiKey; }
    @Override public void setApiKey(String key) { this.apiKey = key; }

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

    /** Max poll attempts (default 120). */
    public void setMaxPollCount(int v) { this.maxPollCount = v; }
    public int getMaxPollCount() { return maxPollCount; }

    /** Poll delay in ms (default 3000). */
    public void setPollDelayMs(long v) { this.pollDelayMs = v; }
    public long getPollDelayMs() { return pollDelayMs; }

    /**
     * Derive a task-query URL from the base apiUrl.
     */
    protected String deriveTaskUrl(String taskId, String path) {
        try {
            URI uri = URI.create(apiUrl);
            String base = uri.getPort() > 0
                    ? uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort()
                    : uri.getScheme() + "://" + uri.getHost();
            return base + path + taskId;
        } catch (Exception e) {
            return apiUrl + "/" + taskId;
        }
    }
}
