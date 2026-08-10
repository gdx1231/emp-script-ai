package com.gdxsoft.ai.video;

/**
 * Identifier for a video generation provider.
 *
 * @since 1.3.0
 */
public enum VideoProviderType {
    KLING("kling_video"),
    JIMENG("jimeng_video"),
    DOUBAO("doubao_video"),
    QWEN("qwen_video"),
    MINIMAX("minimax_video"),
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

    /**
     * Resolve from {@code AI_PROVIDER_URL.AP_CODE} style codes (uppercase,
     * optional {@code _VIDEO} suffix). Examples: {@code "DOUBAO_VIDEO"} →
     * {@link #DOUBAO}; {@code "MINIMAX_VIDEO"} → {@link #MINIMAX};
     * {@code "DOUBAO"} → {@link #DOUBAO}. Returns {@code null} on no match.
     * <p>
     * Differs from {@link #fromName} which expects the lowercase {@code name}
     * field (e.g. {@code "doubao_video"}); AP_CODE style needs the
     * {@code _VIDEO} suffix stripped and case-insensitive enum-name match.
     */
    public static VideoProviderType fromApCode(String apCode) {
        if (apCode == null) return null;
        String upper = apCode.trim().toUpperCase();
        for (VideoProviderType t : values()) {
            // Accept "DOUBAO" or "DOUBAO_VIDEO" both.
            if (upper.equals(t.name()) || upper.equals(t.name() + "_VIDEO")) {
                return t;
            }
        }
        return null;
    }
}
