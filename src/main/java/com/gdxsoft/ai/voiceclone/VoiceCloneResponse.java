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
}
