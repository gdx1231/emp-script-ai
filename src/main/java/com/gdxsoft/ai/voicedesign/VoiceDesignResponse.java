package com.gdxsoft.ai.voicedesign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;

/**
 * 声音设计响应。
 * <p>
 * 创建成功后返回音色 ID（{@code output.voice} / {@code output.voice_id}）以及
 * 预览音频（{@code output.preview_audio.data}，Base64 编码的 WAV），
 * 建议试听确认效果后再用于语音合成。
 *
 * @since 1.1.0
 */
public class VoiceDesignResponse {

    /** 音色 ID（用于后续语音合成） */
    private String voiceId;

    /** 预览音频字节（可能为 null） */
    private byte[] previewAudio;

    /** 预览音频 MIME 类型（如 audio/wav） */
    private String previewMimeType;

    /** 状态（如 "success"、"failed"） */
    private String status;

    /** 错误信息或附加说明 */
    private String message;

    /** provider 原始响应 JSON */
    private JSONObject raw;

    public VoiceDesignResponse() {}

    public VoiceDesignResponse(String voiceId, String status, JSONObject raw) {
        this.voiceId = voiceId;
        this.status = status;
        this.raw = raw;
    }

    public String getVoiceId() { return voiceId; }
    public VoiceDesignResponse setVoiceId(String voiceId) { this.voiceId = voiceId; return this; }

    public byte[] getPreviewAudio() { return previewAudio; }
    public VoiceDesignResponse setPreviewAudio(byte[] previewAudio) { this.previewAudio = previewAudio; return this; }

    public String getPreviewMimeType() { return previewMimeType; }
    public VoiceDesignResponse setPreviewMimeType(String previewMimeType) { this.previewMimeType = previewMimeType; return this; }

    public String getStatus() { return status; }
    public VoiceDesignResponse setStatus(String status) { this.status = status; return this; }

    public String getMessage() { return message; }
    public VoiceDesignResponse setMessage(String message) { this.message = message; return this; }

    public JSONObject getRaw() { return raw; }
    public VoiceDesignResponse setRaw(JSONObject raw) { this.raw = raw; return this; }

    /** 是否成功 */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status) || (voiceId != null && !voiceId.isEmpty());
    }

    /** 保存预览音频到文件。 */
    public void savePreview(Path path) throws IOException {
        if (previewAudio == null) throw new IllegalStateException("no preview audio bytes");
        Files.write(path, previewAudio);
    }

    /** 快速构造成功响应 */
    public static VoiceDesignResponse success(String voiceId, JSONObject raw) {
        return new VoiceDesignResponse(voiceId, "success", raw);
    }

    /** 快速构造失败响应 */
    public static VoiceDesignResponse error(String message, JSONObject raw) {
        VoiceDesignResponse r = new VoiceDesignResponse();
        r.setStatus("failed");
        r.setMessage(message);
        r.setRaw(raw);
        return r;
    }
}
