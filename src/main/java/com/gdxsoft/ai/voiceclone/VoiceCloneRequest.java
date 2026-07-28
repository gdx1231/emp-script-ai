package com.gdxsoft.ai.voiceclone;

import com.gdxsoft.ai.stt.AudioSource;

/**
 * 声音克隆请求。
 *
 * @since 1.1.0
 */
public class VoiceCloneRequest {

    /** 音频来源（文件、字节、base64 等） */
    private final AudioSource audio;

    /** 已有 speaker_id 表示升级音色，空/null 表示新建 */
    private String speakerId;

    /** 语言（1=中文，2=英文等，provider 特定） */
    private int language = 1;

    /** 音频格式（wav/mp3 等），为空时从 audio mimeType 推导 */
    private String audioFormat;

    /** 可选：试听文本 */
    private String demoText;

    /** 可选：降噪模型 ID */
    private String denoiseModelId;

    /** 额外 provider 特定参数 */
    private VoiceCloneOptions options;

    public VoiceCloneRequest(AudioSource audio) {
        if (audio == null) throw new IllegalArgumentException("audio is required");
        this.audio = audio;
    }

    public AudioSource getAudio() { return audio; }

    public String getSpeakerId() { return speakerId; }
    public VoiceCloneRequest setSpeakerId(String speakerId) { this.speakerId = speakerId; return this; }

    public int getLanguage() { return language; }
    public VoiceCloneRequest setLanguage(int language) { this.language = language; return this; }

    public String getAudioFormat() { return audioFormat; }
    public VoiceCloneRequest setAudioFormat(String audioFormat) { this.audioFormat = audioFormat; return this; }

    public String getDemoText() { return demoText; }
    public VoiceCloneRequest setDemoText(String demoText) { this.demoText = demoText; return this; }

    public String getDenoiseModelId() { return denoiseModelId; }
    public VoiceCloneRequest setDenoiseModelId(String denoiseModelId) { this.denoiseModelId = denoiseModelId; return this; }

    public VoiceCloneOptions getOptions() { return options; }
    public VoiceCloneRequest setOptions(VoiceCloneOptions options) { this.options = options; return this; }

    /** 是否为升级操作（已有 speaker_id） */
    public boolean isUpgrade() {
        return speakerId != null && !speakerId.isEmpty();
    }
}
