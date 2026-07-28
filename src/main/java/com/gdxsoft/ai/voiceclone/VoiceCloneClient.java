package com.gdxsoft.ai.voiceclone;

import java.io.IOException;

import com.gdxsoft.ai.stt.AudioSource;

/**
 * 声音克隆高层入口。
 * <p>
 * 典型用法：
 * <pre>{@code
 * VoiceCloneResponse r = VoiceCloneClient.of("doubao_voice_clone")
 *     .setApiKey("your-api-key")
 *     .clone(AudioSource.fromFile(Path.of("sample.wav")));
 * System.out.println("Speaker ID: " + r.getSpeakerId());
 * }</pre>
 *
 * @since 1.1.0
 */
public final class VoiceCloneClient {
    private final IVoiceCloneProvider provider;

    public VoiceCloneClient(IVoiceCloneProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    /** 按 provider 名称创建。 */
    public static VoiceCloneClient of(String providerName) {
        return new VoiceCloneClient(VoiceCloneProviderFactory.create(providerName));
    }

    /** 使用已配置的 provider 创建。 */
    public static VoiceCloneClient of(IVoiceCloneProvider provider) {
        return new VoiceCloneClient(provider);
    }

    /** 设置 API Key（链式调用）。 */
    public VoiceCloneClient setApiKey(String key) {
        provider.setApiKey(key);
        return this;
    }

    /** 设置 API URL（链式调用）。 */
    public VoiceCloneClient setApiUrl(String url) {
        provider.setApiUrl(url);
        return this;
    }

    /** 设置 provider 特定配置（链式调用）。 */
    public VoiceCloneClient setConfig(String key, String value) {
        provider.setConfig(key, value);
        return this;
    }

    /**
     * 新建音色：传入音频样本，克隆后返回 speaker_id。
     */
    public VoiceCloneResponse clone(AudioSource audio) throws IOException, InterruptedException {
        return provider.clone(new VoiceCloneRequest(audio));
    }

    /**
     * 克隆/升级音色（完整请求）。
     */
    public VoiceCloneResponse clone(VoiceCloneRequest request) throws IOException, InterruptedException {
        return provider.clone(request);
    }

    /**
     * 查询音色状态。
     */
    public VoiceCloneResponse query(String speakerId) throws IOException, InterruptedException {
        return provider.query(speakerId);
    }

    /** 获取底层 provider（用于高级配置）。 */
    public IVoiceCloneProvider getProvider() { return provider; }
}
