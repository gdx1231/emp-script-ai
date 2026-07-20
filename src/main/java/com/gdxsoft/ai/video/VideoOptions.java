package com.gdxsoft.ai.video;

/**
 * Provider-agnostic options for a video generation request.
 * Each provider maps these to its own request shape.
 *
 * @since 1.3.0
 */
public class VideoOptions {
    private String model;
    private String prompt;
    private String negativePrompt;
    private Integer duration;        // seconds
    private Integer fps;             // frames per second
    private String resolution;       // "1080p", "720p", "1024x576"
    private String aspectRatio;      // "16:9", "9:16", "1:1"
    private Long seed;
    private Integer cfgScale;        // guidance scale 1-20
    private String cameraMovement;   // "static", "zoom_in", "pan_left", etc.
    private String refImageUrl;      // image-to-video reference

    public VideoOptions() {}

    public VideoOptions(String prompt) { this.prompt = prompt; }

    // ---- Fluent getters/setters ----

    public String getModel() { return model; }
    public VideoOptions model(String v) { this.model = v; return this; }
    public VideoOptions setModel(String v) { this.model = v; return this; }

    public String getPrompt() { return prompt; }
    public VideoOptions prompt(String v) { this.prompt = v; return this; }
    public VideoOptions setPrompt(String v) { this.prompt = v; return this; }

    public String getNegativePrompt() { return negativePrompt; }
    public VideoOptions negativePrompt(String v) { this.negativePrompt = v; return this; }
    public VideoOptions setNegativePrompt(String v) { this.negativePrompt = v; return this; }

    public Integer getDuration() { return duration; }
    public VideoOptions duration(Integer v) { this.duration = v; return this; }
    public VideoOptions setDuration(Integer v) { this.duration = v; return this; }

    public Integer getFps() { return fps; }
    public VideoOptions fps(Integer v) { this.fps = v; return this; }
    public VideoOptions setFps(Integer v) { this.fps = v; return this; }

    public String getResolution() { return resolution; }
    public VideoOptions resolution(String v) { this.resolution = v; return this; }
    public VideoOptions setResolution(String v) { this.resolution = v; return this; }

    public String getAspectRatio() { return aspectRatio; }
    public VideoOptions aspectRatio(String v) { this.aspectRatio = v; return this; }
    public VideoOptions setAspectRatio(String v) { this.aspectRatio = v; return this; }

    public Long getSeed() { return seed; }
    public VideoOptions seed(Long v) { this.seed = v; return this; }
    public VideoOptions setSeed(Long v) { this.seed = v; return this; }

    public Integer getCfgScale() { return cfgScale; }
    public VideoOptions cfgScale(Integer v) { this.cfgScale = v; return this; }
    public VideoOptions setCfgScale(Integer v) { this.cfgScale = v; return this; }

    public String getCameraMovement() { return cameraMovement; }
    public VideoOptions cameraMovement(String v) { this.cameraMovement = v; return this; }
    public VideoOptions setCameraMovement(String v) { this.cameraMovement = v; return this; }

    public String getRefImageUrl() { return refImageUrl; }
    public VideoOptions refImageUrl(String v) { this.refImageUrl = v; return this; }
    public VideoOptions setRefImageUrl(String v) { this.refImageUrl = v; return this; }
}
