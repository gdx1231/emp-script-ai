package com.gdxsoft.ai.video;

/**
 * A single video generation request.
 *
 * @since 1.3.0
 */
public class VideoRequest {
    private final VideoOptions options;

    public VideoRequest(VideoOptions options) {
        if (options == null) throw new IllegalArgumentException("options is required");
        if (options.getPrompt() == null || options.getPrompt().isEmpty())
            throw new IllegalArgumentException("prompt is required");
        this.options = options;
    }

    public VideoOptions getOptions() { return options; }
}
