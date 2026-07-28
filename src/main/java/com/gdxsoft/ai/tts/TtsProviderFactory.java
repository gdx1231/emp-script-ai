package com.gdxsoft.ai.tts;

import com.gdxsoft.ai.ChatManagerI18nConstants;

/**
 * Factory for {@link ITtsProvider} instances.
 *
 * @since 1.1.0
 */
public final class TtsProviderFactory {

    private TtsProviderFactory() {}

    /**
     * Create a provider by enum type.
     */
    public static ITtsProvider create(TtsProviderType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        return switch (type) {
            case QWEN   -> new com.gdxsoft.ai.tts.providers.qwen.QwenTtsProvider();
            case DOUBAO -> new com.gdxsoft.ai.tts.providers.doubao.DoubaoTtsProvider();
        };
    }

    /**
     * Create a provider by name (case-insensitive {@link TtsProviderType#getName()}).
     *
     * @throws IllegalArgumentException if name does not resolve
     */
    public static ITtsProvider create(String name) {
        TtsProviderType t = TtsProviderType.fromName(name);
        if (t == null) {
            throw new IllegalArgumentException(
                    ChatManagerI18nConstants.getText("ERROR_TTS_PROVIDER_NOT_FOUND", false, name));
        }
        return create(t);
    }

    /** True if the given name resolves to a known provider. */
    public static boolean isSupported(String name) {
        return TtsProviderType.fromName(name) != null;
    }

    /** All registered provider identifiers. */
    public static java.util.List<String> getSupportedProviders() {
        java.util.List<String> out = new java.util.ArrayList<>(TtsProviderType.values().length);
        for (TtsProviderType t : TtsProviderType.values()) out.add(t.getName());
        return out;
    }
}
