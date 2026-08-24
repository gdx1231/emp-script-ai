package com.gdxsoft.ai.tts;

/**
 * Provider-agnostic options for a TTS request.
 * <p>
 * Each provider maps these to its own request shape（qwen: model/voice/language_type；
 * doubao: voice_type/encoding/speed_ratio）。
 *
 * @since 1.1.0
 */
public class TtsOptions {
    private String model;
    private String voice;
    private String format = "mp3";
    private Double speed;
    private String languageType;
    private Integer sampleRate;
    private String instruction;

    public TtsOptions() {}

    public String getModel() { return model; }
    public TtsOptions withModel(String model) { this.model = model; return this; }
    public TtsOptions setModel(String model) { this.model = model; return this; }

    public String getVoice() { return voice; }
    public TtsOptions withVoice(String voice) { this.voice = voice; return this; }
    public TtsOptions setVoice(String voice) { this.voice = voice; return this; }

    /** 音频编码格式（如 mp3、wav），doubao 生效；qwen 固定返回 wav。 */
    public String getFormat() { return format; }
    public TtsOptions withFormat(String format) { this.format = format; return this; }
    public TtsOptions setFormat(String format) { this.format = format; return this; }

    /** 语速倍率（doubao speed_ratio），null 表示服务端默认。 */
    public Double getSpeed() { return speed; }
    public TtsOptions withSpeed(Double speed) { this.speed = speed; return this; }
    public TtsOptions setSpeed(Double speed) { this.speed = speed; return this; }

    /** 语种（qwen language_type，如 Chinese / English / Auto），null 表示 Auto。 */
    public String getLanguageType() { return languageType; }
    public TtsOptions withLanguageType(String languageType) { this.languageType = languageType; return this; }
    public TtsOptions setLanguageType(String languageType) { this.languageType = languageType; return this; }

    /** 采样率（如 24000、16000），null 表示服务端默认。 */
    public Integer getSampleRate() { return sampleRate; }
    public TtsOptions withSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; return this; }
    public TtsOptions setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; return this; }

    /** 指令控制文本（CosyVoice 用 instruction，Qwen-TTS Instruct 用 instructions）。 */
    public String getInstruction() { return instruction; }
    public TtsOptions withInstruction(String instruction) { this.instruction = instruction; return this; }
    public TtsOptions setInstruction(String instruction) { this.instruction = instruction; return this; }

    /** 音量倍率（doubao loudness_rate），范围 [-50, 100]，null 表示不调整。 */
    private Integer loudnessRate;
    public Integer getLoudnessRate() { return loudnessRate; }
    public TtsOptions withLoudnessRate(Integer loudnessRate) { this.loudnessRate = loudnessRate; return this; }
    public TtsOptions setLoudnessRate(Integer loudnessRate) { this.loudnessRate = loudnessRate; return this; }

    /** 音调偏移（doubao pitch_rate），范围 [-12, 12]，null 表示不调整。 */
    private Integer pitchRate;
    public Integer getPitchRate() { return pitchRate; }
    public TtsOptions withPitchRate(Integer pitchRate) { this.pitchRate = pitchRate; return this; }
    public TtsOptions setPitchRate(Integer pitchRate) { this.pitchRate = pitchRate; return this; }

    /** 启用字幕/时间轴（doubao enable_subtitle），null 表示不启用。 */
    private Boolean enableSubtitle;
    public Boolean getEnableSubtitle() { return enableSubtitle; }
    public TtsOptions withEnableSubtitle(Boolean enableSubtitle) { this.enableSubtitle = enableSubtitle; return this; }
    public TtsOptions setEnableSubtitle(Boolean enableSubtitle) { this.enableSubtitle = enableSubtitle; return this; }

    /** 参考音频 URL 列表（doubao references），用于参考音频生成模式。 */
    private String[] refAudioUrls;
    public String[] getRefAudioUrls() { return refAudioUrls; }
    public TtsOptions withRefAudioUrls(String... refAudioUrls) { this.refAudioUrls = refAudioUrls; return this; }
    public TtsOptions setRefAudioUrls(String[] refAudioUrls) { this.refAudioUrls = refAudioUrls; return this; }

    /** 参考图片 URL（doubao references），用于参考图片生成模式。 */
    private String refImageUrl;
    public String getRefImageUrl() { return refImageUrl; }
    public TtsOptions withRefImageUrl(String refImageUrl) { this.refImageUrl = refImageUrl; return this; }
    public TtsOptions setRefImageUrl(String refImageUrl) { this.refImageUrl = refImageUrl; return this; }
}
