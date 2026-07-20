package com.gdxsoft.ai.img;

/**
 * Identifier for an image generation provider implementation.
 * <p>
 * Distinct from {@code com.gdxsoft.ai.request.ProviderType} (chat) and
 * {@code com.gdxsoft.ai.stt.SttProviderType} (speech-to-text) because
 * image generation providers have different request/response shapes,
 * auth patterns, and no SSE/tool-calling semantics.
 *
 * @since 1.2.0
 */
public enum ImgProviderType {
    OPENAI("openai_img"),
    OPENAI_COMPAT("openai_compat_img"),
    STABILITY("stability_img"),
    QWEN("qwen_img"),
    DOUBAO("doubao_img"),
    GROK("grok_img");

    private final String name;

    ImgProviderType(String name) {
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
    public static ImgProviderType fromName(String n) {
        if (n == null) return null;
        String lower = n.trim().toLowerCase();
        for (ImgProviderType t : values()) {
            if (t.name.equalsIgnoreCase(lower)) return t;
        }
        return null;
    }
}
