package com.gdxsoft.ai.video.workflow.phase;

/**
 * 视频合成选项 — 对应 Phase4 compositing 阶段的 ffmpeg 参数。
 *
 * @since 1.4.0
 */
public class ComposeOptions {

    /** 统一分辨率，如 "1280x720"，null=保持原始 */
    private String resolution = "1280x720";

    /** 统一帧率，默认 30 */
    private int fps = 30;

    /** 视频编码器 */
    private String videoCodec = "libx264";

    /** 音频编码器 */
    private String audioCodec = "aac";

    /** 是否添加转场效果（默认 false） */
    private boolean transitions;

    /** 背景音乐文件路径（可选） */
    private String bgmPath;

    /** BGM 音量 0.0-1.0 */
    private double bgmVolume = 0.3;

    /** 字幕文件路径 (.srt)（可选） */
    private String subtitlePath;

    /** ffmpeg 可执行文件路径，默认 "ffmpeg"（从 PATH 查找） */
    private String ffmpegPath = "ffmpeg";

    /** 转换超时毫秒，默认 600000 (10min) */
    private long timeoutMs = 600000;

    // ===== Getters / Setters (fluent) =====

    public String getResolution() { return resolution; }
    public ComposeOptions resolution(String v) { this.resolution = v; return this; }
    public ComposeOptions setResolution(String v) { this.resolution = v; return this; }

    public int getFps() { return fps; }
    public ComposeOptions fps(int v) { this.fps = v; return this; }
    public ComposeOptions setFps(int v) { this.fps = v; return this; }

    public String getVideoCodec() { return videoCodec; }
    public ComposeOptions videoCodec(String v) { this.videoCodec = v; return this; }

    public String getAudioCodec() { return audioCodec; }
    public ComposeOptions audioCodec(String v) { this.audioCodec = v; return this; }

    public boolean isTransitions() { return transitions; }
    public ComposeOptions transitions(boolean v) { this.transitions = v; return this; }

    public String getBgmPath() { return bgmPath; }
    public ComposeOptions bgmPath(String v) { this.bgmPath = v; return this; }

    public double getBgmVolume() { return bgmVolume; }
    public ComposeOptions bgmVolume(double v) { this.bgmVolume = v; return this; }

    public String getSubtitlePath() { return subtitlePath; }
    public ComposeOptions subtitlePath(String v) { this.subtitlePath = v; return this; }

    public String getFfmpegPath() { return ffmpegPath; }
    public ComposeOptions ffmpegPath(String v) { this.ffmpegPath = v; return this; }

    public long getTimeoutMs() { return timeoutMs; }
    public ComposeOptions timeoutMs(long v) { this.timeoutMs = v; return this; }
}
