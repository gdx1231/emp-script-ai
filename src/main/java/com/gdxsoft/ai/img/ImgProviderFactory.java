package com.gdxsoft.ai.img;

import com.gdxsoft.ai.ChatManagerI18nConstants;

/**
 * Factory for {@link IImgProvider} instances.
 *
 * @since 1.2.0
 */
public final class ImgProviderFactory {

    private ImgProviderFactory() {}

    /**
     * Create a provider by enum type.
     *
     * @throws UnsupportedOperationException for providers not yet implemented
     */
    public static IImgProvider create(ImgProviderType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        return switch (type) {
            case OPENAI        -> new com.gdxsoft.ai.img.providers.openai.OpenAiImgProvider();
            case OPENAI_COMPAT -> new com.gdxsoft.ai.img.providers.openaiCompat.OpenAiCompatImgProvider();
            case STABILITY     -> new com.gdxsoft.ai.img.providers.stability.StabilityImgProvider();
            case QWEN          -> new com.gdxsoft.ai.img.providers.qwen.QwenImgProvider();
            case DOUBAO        -> new com.gdxsoft.ai.img.providers.doubao.DoubaoImgProvider();
            case GROK          -> new com.gdxsoft.ai.img.providers.grok.GrokImgProvider();
        };
    }

    /**
     * Create a provider by name (case-insensitive {@link ImgProviderType#getName()}).
     *
     * @throws IllegalArgumentException if name does not resolve
     */
    public static IImgProvider create(String name) {
        ImgProviderType t = ImgProviderType.fromName(name);
        if (t == null) {
            throw new IllegalArgumentException(
                    ChatManagerI18nConstants.getText("ERROR_IMG_PROVIDER_NOT_FOUND", false, name));
        }
        return create(t);
    }

    /** True if the given name resolves to a known provider. */
    public static boolean isSupported(String name) {
        return ImgProviderType.fromName(name) != null;
    }

    /** All registered provider identifiers. */
    public static java.util.List<String> getSupportedProviders() {
        java.util.List<String> out = new java.util.ArrayList<>(ImgProviderType.values().length);
        for (ImgProviderType t : ImgProviderType.values()) out.add(t.getName());
        return out;
    }
}
