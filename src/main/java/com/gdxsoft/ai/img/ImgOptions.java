package com.gdxsoft.ai.img;

/**
 * Provider-agnostic options for an image generation request.
 * <p>
 * Each provider maps these to its own request shape (JSON body / multipart, etc.).
 *
 * @since 1.2.0
 */
public class ImgOptions {
    private String model = "dall-e-3";
    private String prompt;
    private String size = "1024x1024";
    private String quality;
    private String style;
    private Integer n = 1;
    private String responseFormat = "url"; // "url" or "b64_json"
    private String negativePrompt;
    private Integer steps;
    private Long seed;
    private String user;

    public ImgOptions() {}

    public ImgOptions(String prompt) {
        this.prompt = prompt;
    }

    // ---- Getters / Setters (fluent) ----

    public String getModel() { return model; }
    public ImgOptions model(String model) { this.model = model; return this; }
    public ImgOptions setModel(String model) { this.model = model; return this; }

    public String getPrompt() { return prompt; }
    public ImgOptions prompt(String prompt) { this.prompt = prompt; return this; }
    public ImgOptions setPrompt(String prompt) { this.prompt = prompt; return this; }

    public String getSize() { return size; }
    public ImgOptions size(String size) { this.size = size; return this; }
    public ImgOptions setSize(String size) { this.size = size; return this; }

    /** e.g. "standard" or "hd" (OpenAI) */
    public String getQuality() { return quality; }
    public ImgOptions quality(String quality) { this.quality = quality; return this; }
    public ImgOptions setQuality(String quality) { this.quality = quality; return this; }

    /** e.g. "vivid" or "natural" (OpenAI) */
    public String getStyle() { return style; }
    public ImgOptions style(String style) { this.style = style; return this; }
    public ImgOptions setStyle(String style) { this.style = style; return this; }

    public Integer getN() { return n; }
    public ImgOptions n(Integer n) { this.n = n; return this; }
    public ImgOptions setN(Integer n) { this.n = n; return this; }

    /** "url" or "b64_json" */
    public String getResponseFormat() { return responseFormat; }
    public ImgOptions responseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }
    public ImgOptions setResponseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }

    /** Negative prompt (supported by Stability AI, ignored by OpenAI) */
    public String getNegativePrompt() { return negativePrompt; }
    public ImgOptions negativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; return this; }
    public ImgOptions setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; return this; }

    /** Diffusion steps (supported by Stability AI) */
    public Integer getSteps() { return steps; }
    public ImgOptions steps(Integer steps) { this.steps = steps; return this; }
    public ImgOptions setSteps(Integer steps) { this.steps = steps; return this; }

    /** Seed for reproducible generation */
    public Long getSeed() { return seed; }
    public ImgOptions seed(Long seed) { this.seed = seed; return this; }
    public ImgOptions setSeed(Long seed) { this.seed = seed; return this; }

    /** End-user identifier for abuse monitoring */
    public String getUser() { return user; }
    public ImgOptions user(String user) { this.user = user; return this; }
    public ImgOptions setUser(String user) { this.user = user; return this; }

    // ==== Image-to-Image (垫图/参考图) ====

    /** Reference image URL for image-to-image generation. */
    private String refImageUrl;

    /** Reference image strength [0.0, 1.0]. Higher = more similar to reference. */
    private Double refStrength;

    /**
     * Reference mode:
     * <ul>
     *   <li>{@code "repaint"} — generate based on reference content (default)</li>
     *   <li>{@code "refonly"} — generate based on reference style only</li>
     * </ul>
     */
    private String refMode;

    /** Reference image URL. */
    public String getRefImageUrl() { return refImageUrl; }
    public ImgOptions refImageUrl(String url) { this.refImageUrl = url; return this; }
    public ImgOptions setRefImageUrl(String url) { this.refImageUrl = url; return this; }

    /** How strongly to follow the reference [0.0, 1.0]. */
    public Double getRefStrength() { return refStrength; }
    public ImgOptions refStrength(Double strength) { this.refStrength = strength; return this; }
    public ImgOptions setRefStrength(Double strength) { this.refStrength = strength; return this; }

    /**
     * Reference mode: {@code "repaint"} (content) or {@code "refonly"} (style).
     */
    public String getRefMode() { return refMode; }
    public ImgOptions refMode(String mode) { this.refMode = mode; return this; }
    public ImgOptions setRefMode(String mode) { this.refMode = mode; return this; }
}
