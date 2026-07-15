package com.gdxsoft.ai.stt;

/**
 * Identifier for a speech-to-text provider implementation.
 * <p>
 * Distinct from {@code com.gdxsoft.ai.request.ProviderType} because STT providers
 * have different auth headers, request shapes (multipart / JSON / raw bytes), and
 * no SSE/token-usage semantics.
 *
 * @since 1.1.0
 */
public enum SttProviderType {
    OPENAI("openai_stt"),
    OPENAI_COMPAT("openai_compat_stt"),
    AZURE("azure_stt"),
    GOOGLE("google_stt"),
    LOCAL("local_stt");

    private final String name;

    SttProviderType(String name) {
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
    public static SttProviderType fromName(String n) {
        if (n == null) return null;
        String lower = n.trim().toLowerCase();
        for (SttProviderType t : values()) {
            if (t.name.equalsIgnoreCase(lower)) return t;
        }
        return null;
    }
}