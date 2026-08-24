package com.gdxsoft.ai.voiceclone;

import java.util.HashMap;
import java.util.Map;

/**
 * 声音克隆请求的可选参数。
 * <p>
 * 通用参数通过 {@link VoiceCloneRequest} 设置，
 * provider 特定参数通过 {@link #setExtra(String, String)} 传入。
 *
 * @since 1.1.0
 */
public class VoiceCloneOptions {

    /** provider 特定的额外参数 */
    private final Map<String, String> extras = new HashMap<>();

    /** 训练轮次（部分 provider 支持） */
    private Integer trainSteps;

    /** 音频预处理：是否启用降噪 */
    private Boolean denoise;

    /** 是否开启音频降噪（doubao enable_audio_denoise），音频样本噪声较大时建议开启。 */
    private Boolean enableAudioDenoise;

    /** 是否关闭音量归一化（doubao disable_volume_normalization），关闭后合成音量与原始音频更接近。 */
    private Boolean disableVolumeNormalization;

    public Map<String, String> getExtras() { return extras; }

    public VoiceCloneOptions setExtra(String key, String value) {
        if (key == null) return this;
        if (value == null) extras.remove(key);
        else extras.put(key, value);
        return this;
    }

    public String getExtra(String key) {
        return key == null ? null : extras.get(key);
    }

    public Integer getTrainSteps() { return trainSteps; }
    public VoiceCloneOptions setTrainSteps(Integer trainSteps) { this.trainSteps = trainSteps; return this; }

    public Boolean getDenoise() { return denoise; }
    public VoiceCloneOptions setDenoise(Boolean denoise) { this.denoise = denoise; return this; }

    public Boolean getEnableAudioDenoise() { return enableAudioDenoise; }
    public VoiceCloneOptions setEnableAudioDenoise(Boolean enableAudioDenoise) { this.enableAudioDenoise = enableAudioDenoise; return this; }

    public Boolean getDisableVolumeNormalization() { return disableVolumeNormalization; }
    public VoiceCloneOptions setDisableVolumeNormalization(Boolean disableVolumeNormalization) { this.disableVolumeNormalization = disableVolumeNormalization; return this; }
}
