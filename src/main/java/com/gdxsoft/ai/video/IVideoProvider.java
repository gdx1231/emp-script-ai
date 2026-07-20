package com.gdxsoft.ai.video;

import java.io.IOException;

/**
 * Contract for a single video generation provider.
 *
 * @since 1.3.0
 */
public interface IVideoProvider {

    VideoProviderType getProviderType();

    String getApiUrl();
    void setApiUrl(String url);

    String getApiKey();
    void setApiKey(String key);

    void setConfig(String key, String value);
    String getConfig(String key);

    /**
     * Generate video. Video generation is async — the provider
     * submits a task and polls for completion.
     */
    VideoResponse generate(VideoRequest request) throws IOException, InterruptedException;

    /** Debug curl representation. */
    String curl(VideoRequest request);
}
