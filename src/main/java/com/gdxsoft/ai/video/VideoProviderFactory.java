package com.gdxsoft.ai.video;

import com.gdxsoft.ai.ChatManagerI18nConstants;

/**
 * Factory for {@link IVideoProvider} instances.
 *
 * @since 1.3.0
 */
public final class VideoProviderFactory {

    private VideoProviderFactory() {}

    public static IVideoProvider create(VideoProviderType type) {
        if (type == null) throw new IllegalArgumentException("type is null");
        return switch (type) {
            case KLING  -> new com.gdxsoft.ai.video.providers.kling.KlingVideoProvider();
            case JIMENG -> new com.gdxsoft.ai.video.providers.jimeng.JimengVideoProvider();
            case QWEN   -> new com.gdxsoft.ai.video.providers.qwen.QwenVideoProvider();
            case OPENAI_COMPAT -> new com.gdxsoft.ai.video.providers.openaiCompat.OpenAiCompatVideoProvider();
        };
    }

    public static IVideoProvider create(String name) {
        VideoProviderType t = VideoProviderType.fromName(name);
        if (t == null) throw new IllegalArgumentException(
                ChatManagerI18nConstants.getText("ERROR_IMG_PROVIDER_NOT_FOUND", false, name));
        return create(t);
    }

    public static boolean isSupported(String name) {
        return VideoProviderType.fromName(name) != null;
    }

    public static java.util.List<String> getSupportedProviders() {
        java.util.List<String> out = new java.util.ArrayList<>(VideoProviderType.values().length);
        for (VideoProviderType t : VideoProviderType.values()) out.add(t.getName());
        return out;
    }
}
