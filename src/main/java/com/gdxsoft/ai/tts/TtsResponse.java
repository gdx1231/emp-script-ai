package com.gdxsoft.ai.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;

/**
 * Result of a TTS synthesis.
 *
 * @since 1.1.0
 */
public class TtsResponse {
    private final byte[] audio;
    private final String mimeType;
    private final String audioUrl;
    private final JSONObject raw;

    public TtsResponse(byte[] audio, String mimeType, String audioUrl, JSONObject raw) {
        this.audio = audio;
        this.mimeType = mimeType;
        this.audioUrl = audioUrl;
        this.raw = raw;
    }

    /** 音频字节（可能为 null，如仅返回 URL 且未下载时）。 */
    public byte[] getAudio() { return audio; }

    /** 音频 MIME 类型（如 audio/mpeg、audio/wav）。 */
    public String getMimeType() { return mimeType; }

    /** 服务商返回的音频 URL（qwen 为 24 小时有效的 OSS 地址；可能为 null）。 */
    public String getAudioUrl() { return audioUrl; }

    /** 服务商返回的原始 JSON。 */
    public JSONObject getRaw() { return raw; }

    /** 保存音频到文件。 */
    public void save(Path path) throws IOException {
        if (audio == null) throw new IllegalStateException("no audio bytes");
        Files.write(path, audio);
    }

    /** 音频时长（秒），从 raw 响应提取；无 raw 时返回 -1。 */
    public double getDuration() {
        if (raw == null) return -1;
        return raw.optDouble("duration", -1);
    }

    /** 原始音频时长（秒，计费依据），从 raw 响应提取；无 raw 时返回 -1。 */
    public double getOriginalDuration() {
        if (raw == null) return -1;
        return raw.optDouble("original_duration", -1);
    }

    /** 字幕信息（doubao enable_subtitle=true 时返回），从 raw 响应提取。 */
    public JSONObject getSubtitle() {
        if (raw == null) return null;
        return raw.optJSONObject("subtitle");
    }
}
