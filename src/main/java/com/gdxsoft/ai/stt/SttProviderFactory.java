package com.gdxsoft.ai.stt;

import com.gdxsoft.ai.ChatManagerI18nConstants;

/**
 * Factory for {@link ISttProvider} instances.
 *
 * @since 1.1.0
 */
public final class SttProviderFactory {

    private SttProviderFactory() {}

    /**
     * Create a provider by enum type.
     *
     * @throws UnsupportedOperationException for providers not yet implemented in this version
     */
    public static ISttProvider create(SttProviderType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        return switch (type) {
            case OPENAI        -> new com.gdxsoft.ai.stt.providers.openai.OpenAiSttProvider();
            case OPENAI_COMPAT -> new com.gdxsoft.ai.stt.providers.openaiCompat.OpenAiCompatSttProvider();
            case AZURE         -> new com.gdxsoft.ai.stt.providers.azure.AzureSttProvider();
            case GOOGLE        -> new com.gdxsoft.ai.stt.providers.google.GoogleSttProvider();
            case LOCAL         -> new com.gdxsoft.ai.stt.providers.local.LocalSttProvider();
        };
    }

    /**
     * Create a provider by name (case-insensitive {@link SttProviderType#getName()}).
     *
     * @throws IllegalArgumentException if name does not resolve
     */
    public static ISttProvider create(String name) {
        SttProviderType t = SttProviderType.fromName(name);
        if (t == null) {
            throw new IllegalArgumentException(
                    ChatManagerI18nConstants.getText("ERROR_STT_PROVIDER_NOT_FOUND", false, name));
        }
        return create(t);
    }

    /** True if the given name resolves to a known provider. */
    public static boolean isSupported(String name) {
        return SttProviderType.fromName(name) != null;
    }

    /** All registered provider identifiers. */
    public static java.util.List<String> getSupportedProviders() {
        java.util.List<String> out = new java.util.ArrayList<>(SttProviderType.values().length);
        for (SttProviderType t : SttProviderType.values()) out.add(t.getName());
        return out;
    }
}