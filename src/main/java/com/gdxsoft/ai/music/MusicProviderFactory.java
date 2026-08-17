package com.gdxsoft.ai.music;

import com.gdxsoft.ai.ChatManagerI18nConstants;

/** 音乐生成 Provider 工厂。 */
public final class MusicProviderFactory {
    private MusicProviderFactory() {}

    public static IMusicProvider create(MusicProviderType type) {
        if (type == null) throw new IllegalArgumentException("type is null");
        return switch (type) {
            case MINIMAX -> new com.gdxsoft.ai.music.providers.minimax.MiniMaxMusicProvider();
        };
    }

    public static IMusicProvider create(String name) {
        MusicProviderType type = MusicProviderType.fromName(name);
        if (type == null) {
            throw new IllegalArgumentException(
                    ChatManagerI18nConstants.getText("ERROR_IMG_PROVIDER_NOT_FOUND", false, name));
        }
        return create(type);
    }
}
