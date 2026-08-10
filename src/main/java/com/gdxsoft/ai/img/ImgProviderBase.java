package com.gdxsoft.ai.img;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    // ======================== Reference image resolution ========================

    /**
     * Resolve reference images from {@link ImgOptions}.
     * <p>
     * Prefers {@code refImageUrls} (plural), falls back to {@code refImageUrl} (singular).
     * Filters out {@code null} and empty entries. Returns {@code null} if no valid references.
     *
     * @param opts image options
     * @return list of valid image URLs, or {@code null} if none
     */
    protected static List<String> resolveRefImages(ImgOptions opts) {
        return resolveRefImages(opts, 0);
    }

    /**
     * Resolve reference images with optional truncation.
     * <p>
     * Same as {@link #resolveRefImages(ImgOptions)} but limits the result to at most
     * {@code maxCount} entries. Use {@code maxCount <= 0} for no limit.
     *
     * @param opts     image options
     * @param maxCount max number of images to return; &lt;= 0 means no limit
     * @return list of valid image URLs (size &lt;= maxCount if maxCount &gt; 0),
     *         or {@code null} if none
     */
    protected static List<String> resolveRefImages(ImgOptions opts, int maxCount) {
        if (opts == null) {
            return null;
        }
        List<String> urls = opts.getRefImageUrls();
        List<String> result = null;
        if (urls != null) {
            for (String u : urls) {
                if (u != null && !u.isEmpty()) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(u);
                    if (maxCount > 0 && result.size() >= maxCount) {
                        break;
                    }
                }
            }
        }
        if (result != null) {
            return result;
        }
        String single = opts.getRefImageUrl();
        if (single != null && !single.isEmpty()) {
            return Collections.singletonList(single);
        }
        return null;
    }
}
