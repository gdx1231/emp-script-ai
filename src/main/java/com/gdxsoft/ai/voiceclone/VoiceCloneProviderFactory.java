package com.gdxsoft.ai.voiceclone;

/**
 * 声音克隆 provider 工厂。
 *
 * @since 1.1.0
 */
public final class VoiceCloneProviderFactory {

    private VoiceCloneProviderFactory() {}

    /**
     * 按枚举类型创建 provider 实例。
     *
     * @throws UnsupportedOperationException 未实现的 provider
     */
    public static IVoiceCloneProvider create(VoiceCloneProviderType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        return switch (type) {
            case DOUBAO -> new com.gdxsoft.ai.voiceclone.providers.doubao.DoubaoVoiceCloneProvider();
            case QWEN -> new com.gdxsoft.ai.voiceclone.providers.qwen.QwenVoiceCloneProvider();
            case MINIMAX -> new com.gdxsoft.ai.voiceclone.providers.minimax.MiniMaxVoiceCloneProvider();
        };
    }

    /**
     * 按名称创建 provider 实例（不区分大小写）。
     *
     * @throws IllegalArgumentException 未知 provider 名称
     */
    public static IVoiceCloneProvider create(String name) {
        VoiceCloneProviderType t = VoiceCloneProviderType.fromName(name);
        if (t == null) {
            throw new IllegalArgumentException("未知的声音克隆 provider: " + name);
        }
        return create(t);
    }

    /** 判断给定名称是否为已知 provider。 */
    public static boolean isSupported(String name) {
        return VoiceCloneProviderType.fromName(name) != null;
    }

    /** 所有已注册的 provider 名称列表。 */
    public static java.util.List<String> getSupportedProviders() {
        java.util.List<String> out = new java.util.ArrayList<>(VoiceCloneProviderType.values().length);
        for (VoiceCloneProviderType t : VoiceCloneProviderType.values()) out.add(t.getName());
        return out;
    }
}
