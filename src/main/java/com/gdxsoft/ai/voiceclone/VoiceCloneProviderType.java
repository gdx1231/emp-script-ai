package com.gdxsoft.ai.voiceclone;

/**
 * 声音克隆 provider 枚举。
 *
 * @since 1.1.0
 */
public enum VoiceCloneProviderType {
    DOUBAO("doubao_voice_clone"),
    QWEN("qwen_voice_clone"),
    MINIMAX("minimax_voice_clone");

    private final String name;

    VoiceCloneProviderType(String name) {
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
    public static VoiceCloneProviderType fromName(String n) {
        if (n == null) return null;
        String lower = n.trim().toLowerCase();
        for (VoiceCloneProviderType t : values()) {
            if (t.name.equalsIgnoreCase(lower)) return t;
        }
        return null;
    }
}
