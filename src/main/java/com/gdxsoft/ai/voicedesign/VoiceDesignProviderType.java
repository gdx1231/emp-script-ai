package com.gdxsoft.ai.voicedesign;

/**
 * 声音设计 provider 枚举。
 * <p>
 * 声音设计（Voice Design）通过自然语言描述创建定制化音色，无需音频样本。
 *
 * @since 1.1.0
 */
public enum VoiceDesignProviderType {
    /** 阿里云百炼（DashScope）：CosyVoice / Qwen-Audio-TTS / Qwen-TTS 声音设计 */
    QWEN("qwen_voice_design");

    private final String name;

    VoiceDesignProviderType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * 按名称查找枚举（不区分大小写）。
     *
     * @param n 名称
     * @return 匹配的枚举，未知返回 {@code null}
     */
    public static VoiceDesignProviderType fromName(String n) {
        if (n == null) return null;
        String lower = n.trim().toLowerCase();
        for (VoiceDesignProviderType t : values()) {
            if (t.name.equalsIgnoreCase(lower)) return t;
        }
        return null;
    }
}
