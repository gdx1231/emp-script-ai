package com.gdxsoft.ai.voicedesign;

/**
 * 声音设计请求。
 * <p>
 * 通过 {@code voice_prompt}（自然语言声音描述）创建音色，无需音频样本。
 *
 * @since 1.1.0
 */
public class VoiceDesignRequest {

    /** 声音描述（必填，中文或英文，CosyVoice ≤500 字符，Qwen-TTS ≤2048 字符） */
    private final String voicePrompt;

    /** 预览音频朗读的文本（可选） */
    private String previewText;

    /** 可选参数（目标模型、音色名前缀、采样率等） */
    private VoiceDesignOptions options;

    public VoiceDesignRequest(String voicePrompt) {
        this(voicePrompt, new VoiceDesignOptions());
    }

    public VoiceDesignRequest(String voicePrompt, VoiceDesignOptions options) {
        if (voicePrompt == null || voicePrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("voicePrompt is required");
        }
        if (options == null) {
            throw new IllegalArgumentException("options is required");
        }
        this.voicePrompt = voicePrompt.trim();
        this.options = options;
    }

    public String getVoicePrompt() { return voicePrompt; }

    public String getPreviewText() { return previewText; }
    public VoiceDesignRequest setPreviewText(String previewText) { this.previewText = previewText; return this; }

    public VoiceDesignOptions getOptions() { return options; }
    public VoiceDesignRequest setOptions(VoiceDesignOptions options) {
        if (options == null) throw new IllegalArgumentException("options is required");
        this.options = options;
        return this;
    }
}
