package com.gdxsoft.ai.tts;

/**
 * Identifier for a text-to-speech provider implementation.
 *
 * @since 1.1.0
 */
public enum TtsProviderType {
    QWEN("qwen_tts"),
    DOUBAO("doubao_tts");

    private final String name;

    TtsProviderType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Resolve an enum constant from its lowercase identifier.
     *
     * @param n identifier (case-insensitive)
     * @return the matching enum, or {@code null} if unknown
     */
    public static TtsProviderType fromName(String n) {
        if (n == null) return null;
        String lower = n.trim().toLowerCase();
        for (TtsProviderType t : values()) {
            if (t.name.equalsIgnoreCase(lower)) return t;
        }
        return null;
    }
}
