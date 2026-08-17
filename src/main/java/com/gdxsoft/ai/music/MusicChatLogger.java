package com.gdxsoft.ai.music;

import java.util.UUID;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.ChatManagerDb;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 音乐与歌词创作日志记录器，持久化到 AI_CHAT / AI_CHAT_MSG。
 */
public class MusicChatLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(MusicChatLogger.class);

    private final ChatManagerDb db;
    private final RequestValue rv;
    private long aiId;
    private String requestId;
    private short noi = 1;
    private String step = "music_generate";

    private MusicChatLogger(ChatManagerDb db, RequestValue rv) {
        this.db = db;
        this.rv = rv;
    }

    public static MusicChatLogger create(String dbConfigName) {
        try {
            RequestValue requestValue = new RequestValue();
            return new MusicChatLogger(new ChatManagerDb(requestValue, dbConfigName), requestValue);
        } catch (Exception e) {
            LOGGER.warn("MusicChatLogger 初始化失败，日志记录已跳过: {}", e.getMessage());
            return null;
        }
    }

    public static MusicChatLogger create(RequestValue rv, String dbConfigName) {
        try {
            return new MusicChatLogger(new ChatManagerDb(rv, dbConfigName), rv);
        } catch (Exception e) {
            LOGGER.warn("MusicChatLogger 初始化失败，日志记录已跳过: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从已有 AI_CHAT 会话恢复日志记录器（后台 Worker 轮询完成后继续写入同一会话）。
     *
     * @param aiId         提交时创建的 AI_CHAT 会话 ID
     * @param dbConfigName 数据库配置名称
     * @return 日志记录器实例；恢复失败返回 null
     */
    public static MusicChatLogger restore(long aiId, String dbConfigName) {
        try {
            RequestValue newRv = new RequestValue();
            ChatManagerDb db = new ChatManagerDb(newRv, dbConfigName);
            MusicChatLogger logger = new MusicChatLogger(db, newRv);
            logger.aiId = aiId;
            return logger;
        } catch (Exception e) {
            LOGGER.warn("MusicChatLogger 恢复失败: {}", e.getMessage());
            return null;
        }
    }

    public void logStart(String provider, String model, String mode, String step,
            String prompt, JSONObject params) {
        try {
            requestId = UUID.randomUUID().toString();
            rv.addOrUpdateValue("request_id", requestId);
            rv.addOrUpdateValue("p_ai_pid", 0);
            rv.addOrUpdateValue("AI_PROVIDER", provider);
            rv.addOrUpdateValue("AI_MODEL", model);
            rv.addOrUpdateValue("AI_THINKING", 0);
            rv.addOrUpdateValue("AI_STREAM", 0);
            rv.addOrUpdateValue("AIM_STEP", step);
            rv.addOrUpdateValue("MODE", mode);
            rv.addOrUpdateValue("AI_MAX_TOKEN", 0);
            this.step = step;
            aiId = db.createChat(rv);

            StringBuilder message = new StringBuilder(prompt == null ? "" : prompt);
            if (params != null && params.length() > 0) {
                message.append("\n\n--- 参数 ---\n").append(params.toString(2));
            }
            db.addMessage(aiId, message.toString(), "user", step,
                    null, null, null, noi, false, true);
        } catch (Exception e) {
            LOGGER.warn("记录音乐任务开始日志失败: {}", e.getMessage());
        }
    }

    public void logCurl(String curl) {
        try {
            if (aiId == 0 || curl == null || curl.isBlank()) return;
            db.addMessage(aiId, curl, "curl", step, null, null, null, noi, true, false);
        } catch (Exception e) {
            LOGGER.warn("记录音乐 curl 日志失败: {}", e.getMessage());
        }
    }

    public void logLyricsSuccess(MusicLyricsResponse response) {
        try {
            if (aiId == 0 || response == null) return;
            JSONObject message = new JSONObject();
            message.put("song_title", response.getSongTitle());
            message.put("style_tags", response.getStyleTags());
            message.put("lyrics", response.getLyrics());
            db.addMessage(aiId, message.toString(2), "assistant", "music_lyrics",
                    null, null, null, noi, false, false);
        } catch (Exception e) {
            LOGGER.warn("记录歌词生成成功日志失败: {}", e.getMessage());
        }
    }

    public void logPreprocessSuccess(MusicCoverPreprocessResponse response) {
        try {
            if (aiId == 0 || response == null) return;
            JSONObject message = new JSONObject();
            message.put("cover_feature_id", response.getCoverFeatureId());
            message.put("formatted_lyrics", response.getFormattedLyrics());
            message.put("structure_result", response.getStructureResult());
            message.put("audio_duration", response.getAudioDuration());
            message.put("trace_id", response.getTraceId());
            db.addMessage(aiId, message.toString(2), "assistant", "music_cover_preprocess",
                    null, null, null, noi, false, false);
        } catch (Exception e) {
            LOGGER.warn("记录翻唱预处理成功日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录 API 原始返回结果（异步任务的创建/查询返回）。
     *
     * @param label 标签（如 "创建任务返回"、"查询结果返回"）
     * @param raw   原始 JSON 响应，null 时跳过
     */
    public void logRawResponse(String label, JSONObject raw) {
        try {
            if (aiId == 0 || raw == null) return;
            // 原始返回可能含 hex 音频，脱敏后再入库，避免日志膨胀
            String msg = (label != null ? "【" + label + "】\n" : "") + sanitizeAudio(raw).toString(2);
            db.addMessage(aiId, msg, "assistant", step,
                    null, null, null, noi, true, false);
        } catch (Exception e) {
            LOGGER.warn("记录原始返回日志失败: {}", e.getMessage());
        }
    }

    public void logMusicSuccess(MusicResponse response) {
        try {
            if (aiId == 0 || response == null) return;
            JSONObject message = new JSONObject();
            if (response.getAudioUrl() != null) message.put("audio_url", response.getAudioUrl());
            if (response.getAudioHex() != null) {
                message.put("audio_hex_length", response.getAudioHex().length());
                message.put("audio_bytes_length", response.getAudioBytes().length);
            }
            message.put("status", response.getStatus());
            message.put("trace_id", response.getTraceId());
            message.put("extra_info", response.getExtraInfo());
            message.put("raw", sanitizeAudio(response.getRaw()));
            db.addMessage(aiId, message.toString(2), "assistant", "music_generate",
                    null, null, null, noi, false, false);
        } catch (Exception e) {
            LOGGER.warn("记录音乐生成成功日志失败: {}", e.getMessage());
        }
    }

    public void logError(Throwable error) {
        try {
            if (aiId == 0) return;
            String message = "音乐创作失败: " + (error.getMessage() == null ? error.toString() : error.getMessage());
            db.addMessage(aiId, message, "assistant", step, null, null, null, noi, false, false);
        } catch (Exception e) {
            LOGGER.warn("记录音乐任务失败日志失败: {}", e.getMessage());
        }
    }

    public long getAiId() { return aiId; }
    public String getRequestId() { return requestId; }

    private static JSONObject sanitizeAudio(JSONObject raw) {
        if (raw == null) return null;
        JSONObject copy = new JSONObject(raw.toString());
        JSONObject data = copy.optJSONObject("data");
        if (data != null && data.has("audio") && !data.optString("audio").startsWith("http")) {
            String audio = data.optString("audio");
            data.put("audio", "<hex:" + audio.length() + " chars>");
        }
        return copy;
    }
}
