package com.gdxsoft.ai.voicedesign;

/**
 * 声音设计 provider 工厂。
 *
 * @since 1.1.0
 */
public final class VoiceDesignProviderFactory {

    private VoiceDesignProviderFactory() {}

    /**
     * 按枚举类型创建 provider 实例。
     *
     * @throws UnsupportedOperationException 未实现的 provider
     */
    public static IVoiceDesignProvider create(VoiceDesignProviderType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        return switch (type) {
            case QWEN -> new com.gdxsoft.ai.voicedesign.providers.qwen.QwenVoiceDesignProvider();
        };
    }

    /**
     * 按名称创建 provider 实例（不区分大小写）。
     *
     * @throws IllegalArgumentException 未知 provider 名称
     */
    public static IVoiceDesignProvider create(String name) {
        VoiceDesignProviderType t = VoiceDesignProviderType.fromName(name);
        if (t == null) {
            throw new IllegalArgumentException("未知的声音设计 provider: " + name);
        }
        return create(t);
    }

    /** 判断给定名称是否为已知 provider。 */
    public static boolean isSupported(String name) {
        return VoiceDesignProviderType.fromName(name) != null;
    }

    /** 所有已注册的 provider 名称列表。 */
    public static java.util.List<String> getSupportedProviders() {
        java.util.List<String> out = new java.util.ArrayList<>(VoiceDesignProviderType.values().length);
        for (VoiceDesignProviderType t : VoiceDesignProviderType.values()) out.add(t.getName());
        return out;
    }
}
