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
}
