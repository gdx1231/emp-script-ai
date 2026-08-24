package com.gdxsoft.ai.tts;

import java.util.UUID;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.ChatManagerDb;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * TTS 语音合成日志记录器。
 * <p>
 * 将语音合成的文本、参数、curl、结果持久化到 AI_CHAT / AI_CHAT_MSG 表。
 * 数据库未配置时静默跳过（不影响合成主流程）。
 *
 * @since 1.1.0
 */
public class TtsChatLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(TtsChatLogger.class);

    private final ChatManagerDb db;
    private final RequestValue rv;
    private long aiId;
    private String requestId;
    private short noi = 1;

    private TtsChatLogger(ChatManagerDb db, RequestValue rv) {
        this.db = db;
        this.rv = rv;
    }

    /**
     * 创建日志记录器。
     *
     * @param rv           请求上下文
     * @param dbConfigName 数据库配置名称
     * @return 日志记录器实例；初始化失败返回 null
     */
    public static TtsChatLogger create(RequestValue rv, String dbConfigName) {
        try {
            ChatManagerDb db = new ChatManagerDb(rv, dbConfigName);
            return new TtsChatLogger(db, rv);
        } catch (Exception e) {
            LOGGER.warn("TtsChatLogger 初始化失败，日志记录已跳过: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 记录合成开始。
     *
     * @param provider provider 名称
     * @param model    模型 ID
     * @param text     合成文本
     * @param options  参数（可为 null）
     */
    public void logStart(String provider, String model, String text, JSONObject options) {
        try {
            String requestId = UUID.randomUUID().toString();
            this.requestId = requestId;
            rv.addOrUpdateValue("request_id", requestId);
            rv.addOrUpdateValue("p_ai_pid", 0);
            rv.addOrUpdateValue("AI_PROVIDER", provider);
            rv.addOrUpdateValue("AI_MODEL", model);
            rv.addOrUpdateValue("AI_THINKING", 0);
            rv.addOrUpdateValue("AI_STREAM", 0);
            rv.addOrUpdateValue("AIM_STEP", "tts");
            rv.addOrUpdateValue("MODE", "tts");
            rv.addOrUpdateValue("AI_MAX_TOKEN", 0);
            rv.addOrUpdateValue("AI_REF", "tts");
            rv.addOrUpdateValue("AI_REF_ID", requestId);

            aiId = db.createChat(rv);

            StringBuilder msg = new StringBuilder();
            msg.append("语音合成\n\n");
            msg.append(text);
            if (options != null && options.length() > 0) {
                msg.append("\n\n--- 参数 ---\n").append(options.toString(2));
            }

            db.addMessage(aiId, msg.toString(), "user", "tts",
                    null, null, null, noi, false, true);

        } catch (Exception e) {
            LOGGER.warn("记录 TTS 开始日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录 curl 请求命令。
     */
    public void logCurl(String curl) {
        try {
            if (aiId == 0 || curl == null || curl.isEmpty()) return;
            db.addMessage(aiId, curl, "curl", "tts",
                    null, null, null, noi, true, false);
        } catch (Exception e) {
            LOGGER.warn("记录 curl 日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录合成成功。
     *
     * @param response 合成响应
     */
    public void logSuccess(TtsResponse response) {
        try {
            if (aiId == 0) return;
            noi = db.getNextInteractionNumber(aiId);

            StringBuilder msg = new StringBuilder();
            msg.append("语音合成成功\n\n");
            if (response.getAudioUrl() != null) {
                msg.append("音频 URL: ").append(response.getAudioUrl()).append("\n");
            }
            if (response.getMimeType() != null) {
                msg.append("MIME: ").append(response.getMimeType()).append("\n");
            }
            if (response.getAudio() != null) {
                msg.append("音频大小: ").append(response.getAudio().length).append(" bytes\n");
            }
            if (response.getRaw() != null) {
                msg.append("\n--- 原始返回 ---\n").append(response.getRaw().toString(2)).append("\n");
            }

            db.addMessage(aiId, msg.toString(), "assistant", "tts",
                    null, null, null, noi, false, false);

        } catch (Exception e) {
            LOGGER.warn("记录 TTS 成功日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录合成失败。
     */
    public void logError(Throwable error) {
        try {
            if (aiId == 0) return;
            noi = db.getNextInteractionNumber(aiId);

            String msg = "语音合成失败: " + (error.getMessage() != null ? error.getMessage() : error.toString());
            db.addMessage(aiId, msg, "assistant", "tts",
                    null, null, null, noi, false, false);

        } catch (Exception e) {
            LOGGER.warn("记录 TTS 失败日志失败: {}", e.getMessage());
        }
    }

    /** @return AI_CHAT ID */
    public long getAiId() { return aiId; }

    /** @return 请求 ID */
    public String getRequestId() { return requestId; }
}
