package com.gdxsoft.ai.video;

/**
 * Identifier for a video generation provider.
 *
 * @since 1.3.0
 */
public enum VideoProviderType {
    KLING("kling_video"),
    JIMENG("jimeng_video"),
    QWEN("qwen_video"),
    OPENAI_COMPAT("openai_compat_video");

    private final String name;

    VideoProviderType(String name) { this.name = name; }

    public String getName() { return name; }

    public static VideoProviderType fromName(String n) {
        if (n == null) return null;
        String lower = n.trim().toLowerCase();
        for (VideoProviderType t : values()) {
            if (t.name.equalsIgnoreCase(lower)) return t;
        }
        return null;
    }
}
