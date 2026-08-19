package com.gdxsoft.ai.voicedesign;

import java.util.HashMap;
import java.util.Map;

/**
 * 声音设计请求的可选参数。
 * <p>
 * 通用参数通过 {@link VoiceDesignRequest} 设置，
 * provider 特定参数通过 {@link #setExtra(String, String)} 传入。
 *
 * @since 1.1.0
 */
public class VoiceDesignOptions {

    /** provider 特定的额外参数 */
    private final Map<String, String> extras = new HashMap<>();

    /** 目标合成模型（声音设计、语音合成要使用相同的模型） */
    private String targetModel;

    /** 音色名前缀（CosyVoice 的 {@code prefix} / Qwen-TTS 的 {@code preferred_name}） */
    private String prefix;

    /** 预览音频采样率（默认 24000） */
    private Integer sampleRate = 24000;

    /** 预览音频编码格式（默认 wav） */
    private String responseFormat = "wav";

    public Map<String, String> getExtras() { return extras; }

    public VoiceDesignOptions setExtra(String key, String value) {
        if (key == null) return this;
        if (value == null) extras.remove(key);
        else extras.put(key, value);
        return this;
    }

    public String getExtra(String key) {
        return key == null ? null : extras.get(key);
    }

    public String getTargetModel() { return targetModel; }
    public VoiceDesignOptions setTargetModel(String targetModel) { this.targetModel = targetModel; return this; }
    public VoiceDesignOptions withTargetModel(String targetModel) { this.targetModel = targetModel; return this; }

    public String getPrefix() { return prefix; }
    public VoiceDesignOptions setPrefix(String prefix) { this.prefix = prefix; return this; }
    public VoiceDesignOptions withPrefix(String prefix) { this.prefix = prefix; return this; }

    public Integer getSampleRate() { return sampleRate; }
    public VoiceDesignOptions setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; return this; }
    public VoiceDesignOptions withSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; return this; }

    public String getResponseFormat() { return responseFormat; }
    public VoiceDesignOptions setResponseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }
    public VoiceDesignOptions withResponseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }
}
