package com.gdxsoft.ai.voiceclone;

import org.json.JSONObject;

/**
 * 声音克隆响应。
 *
 * @since 1.1.0
 */
public class VoiceCloneResponse {

    /** 声音 ID（克隆成功后由 provider 返回） */
    private String speakerId;

    /** 状态（如 "success"、"processing"、"failed"） */
    private String status;

    /** 错误信息 */
    private String message;

    /** provider 原始响应 JSON */
    private JSONObject raw;

    public VoiceCloneResponse() {}

    public VoiceCloneResponse(String speakerId, String status, JSONObject raw) {
        this.speakerId = speakerId;
        this.status = status;
        this.raw = raw;
    }

    public String getSpeakerId() { return speakerId; }
    public VoiceCloneResponse setSpeakerId(String speakerId) { this.speakerId = speakerId; return this; }

    public String getStatus() { return status; }
    public VoiceCloneResponse setStatus(String status) { this.status = status; return this; }

    public String getMessage() { return message; }
    public VoiceCloneResponse setMessage(String message) { this.message = message; return this; }

    public JSONObject getRaw() { return raw; }
    public VoiceCloneResponse setRaw(JSONObject raw) { this.raw = raw; return this; }

    /** 是否成功 */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status) || (speakerId != null && !speakerId.isEmpty());
    }

    /** 快速构造成功响应 */
    public static VoiceCloneResponse success(String speakerId, JSONObject raw) {
        return new VoiceCloneResponse(speakerId, "success", raw);
    }

    /** 快速构造失败响应 */
    public static VoiceCloneResponse error(String message, JSONObject raw) {
        VoiceCloneResponse r = new VoiceCloneResponse();
        r.setStatus("failed");
        r.setMessage(message);
        r.setRaw(raw);
        return r;
    }

    /** 训练状态码（doubao）：0=NotFound, 1=Training, 2=Success, 3=Failed, 4=Active */
    public int getTrainingStatus() {
        if (raw == null) return -1;
        return raw.optInt("status", -1);
    }

    /** 是否训练成功（status=2 或 4） */
    public boolean isTrainingComplete() {
        int s = getTrainingStatus();
        return s == 2 || s == 4;
    }

    /** 剩余训练次数（doubao） */
    public int getAvailableTrainingTimes() {
        if (raw == null) return -1;
        return raw.optInt("available_training_times", -1);
    }

    /** 试听音频 URL（doubao，Success 时返回，1 小时有效） */
    public String getDemoAudioUrl() {
        if (raw == null) return null;
        // speaker_status 数组中第一个元素的 demo_audio
        org.json.JSONArray arr = raw.optJSONArray("speaker_status");
        if (arr != null && arr.length() > 0) {
            return arr.optJSONObject(0).optString("demo_audio", null);
        }
        return raw.optString("demo_audio", null);
    }
}
