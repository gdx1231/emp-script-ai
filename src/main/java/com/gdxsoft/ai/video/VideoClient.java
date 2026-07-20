package com.gdxsoft.ai.video;

import java.io.IOException;

/**
 * High-level facade for video generation.
 *
 * <pre>{@code
 * VideoResponse r = VideoClient.of("kling_video")
 *     .apiKey("...")
 *     .generate("A cat walking through a futuristic city");
 * System.out.println(r.getFirstVideo().getUrl());
 * }</pre>
 *
 * @since 1.3.0
 */
public final class VideoClient {
    private final IVideoProvider provider;

    public VideoClient(IVideoProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    public static VideoClient of(String name) {
        return new VideoClient(VideoProviderFactory.create(name));
    }

    public static VideoClient of(IVideoProvider p) { return new VideoClient(p); }

    public VideoClient apiKey(String key) { provider.setApiKey(key); return this; }
    public VideoClient apiUrl(String url) { provider.setApiUrl(url); return this; }
    public VideoClient config(String k, String v) { provider.setConfig(k, v); return this; }

    public VideoResponse generate(String prompt) throws IOException, InterruptedException {
        return provider.generate(new VideoRequest(new VideoOptions(prompt)));
    }

    public VideoResponse generate(VideoOptions opts) throws IOException, InterruptedException {
        return provider.generate(new VideoRequest(opts));
    }

    public VideoResponse generate(VideoRequest req) throws IOException, InterruptedException {
        return provider.generate(req);
    }

    public IVideoProvider getProvider() { return provider; }
}
