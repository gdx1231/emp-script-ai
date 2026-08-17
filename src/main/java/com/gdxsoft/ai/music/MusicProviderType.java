package com.gdxsoft.ai.music;

/** 音乐生成 Provider 类型。 */
public enum MusicProviderType {
    MINIMAX("minimax_music");

    private final String name;

    MusicProviderType(String name) { this.name = name; }

    public String getName() { return name; }

    public static MusicProviderType fromName(String name) {
        if (name == null) return null;
        String value = name.trim().toLowerCase();
        for (MusicProviderType type : values()) {
            if (type.name.equals(value)) return type;
        }
        return null;
    }
}
