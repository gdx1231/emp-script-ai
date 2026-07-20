package com.gdxsoft.ai.img;

/**
 * A single image generation request, pairing options with provider configuration.
 *
 * @since 1.2.0
 */
public class ImgRequest {
    private final ImgOptions options;

    public ImgRequest(ImgOptions options) {
        if (options == null) throw new IllegalArgumentException("options is required");
        if (options.getPrompt() == null || options.getPrompt().isEmpty()) {
            throw new IllegalArgumentException("prompt is required in ImgOptions");
        }
        this.options = options;
    }

    public ImgOptions getOptions() { return options; }
}
