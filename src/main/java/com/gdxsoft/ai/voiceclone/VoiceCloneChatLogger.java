package com.gdxsoft.ai.voiceclone;

import java.util.UUID;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.ChatManagerDb;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 声音复刻日志记录器。
 * <p>
 * 将声音复刻任务的参数、curl、结果持久化到 AI_CHAT / AI_CHAT_MSG 表，
 * 便于在聊天历史中回溯声音复刻记录。
 * <p>
 * 数据库未配置时静默跳过（不影响复刻主流程）。
 *
 * @since 1.1.0
 */
public class VoiceCloneChatLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceCloneChatLogger.class);

    private final ChatManagerDb db;
    private final RequestValue rv;
    private long aiId;
    private String requestId;
    private short noi = 1;

    private VoiceCloneChatLogger(ChatManagerDb db, RequestValue rv) {
        this.db = db;
        this.rv = rv;
    }

    /**
     * 创建日志记录器。
     *
     * @param rv           请求上下文（含用户信息）
     * @param dbConfigName 数据库配置名称
     * @return 日志记录器实例；初始化失败返回 null
     */
    public static VoiceCloneChatLogger create(RequestValue rv, String dbConfigName) {
        try {
            ChatManagerDb db = new ChatManagerDb(rv, dbConfigName);
            return new VoiceCloneChatLogger(db, rv);
        } catch (Exception e) {
            LOGGER.warn("VoiceCloneChatLogger 初始化失败，日志记录已跳过: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 记录复刻开始：创建 AI_CHAT 会话 + 用户消息（参数）。
     *
     * @param provider    provider 名称
     * @param targetModel 目标合成模型
     * @param audioUrl    源音频 URL
     * @param options     额外参数（可为 null）
     */
    public void logStart(String provider, String targetModel, String audioUrl, JSONObject options) {
        try {
            String requestId = UUID.randomUUID().toString();
            this.requestId = requestId;
            rv.addOrUpdateValue("request_id", requestId);
            rv.addOrUpdateValue("p_ai_pid", 0);
            rv.addOrUpdateValue("AI_PROVIDER", provider);
            rv.addOrUpdateValue("AI_MODEL", targetModel);
            rv.addOrUpdateValue("AI_THINKING", 0);
            rv.addOrUpdateValue("AI_STREAM", 0);
            rv.addOrUpdateValue("AIM_STEP", "voice_clone");
            rv.addOrUpdateValue("MODE", "voice_clone");
            rv.addOrUpdateValue("AI_MAX_TOKEN", 0);
            rv.addOrUpdateValue("AI_REF", "voice_clone");
            rv.addOrUpdateValue("AI_REF_ID", requestId);

            aiId = db.createChat(rv);

            StringBuilder msg = new StringBuilder();
            msg.append("声音复刻\n\n");
            msg.append("目标模型: ").append(targetModel).append("\n");
            if (audioUrl != null) {
                msg.append("音频来源: ").append(audioUrl).append("\n");
            }
            if (options != null && options.length() > 0) {
                msg.append("\n--- 参数 ---\n").append(options.toString(2));
            }

            db.addMessage(aiId, msg.toString(), "user", "voice_clone",
                    null, null, null, noi, false, true);

        } catch (Exception e) {
            LOGGER.warn("记录声音复刻开始日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录 curl 请求命令。
     */
    public void logCurl(String curl) {
        try {
            if (aiId == 0 || curl == null || curl.isEmpty()) return;
            db.addMessage(aiId, curl, "curl", "voice_clone",
                    null, null, null, noi, true, false);
        } catch (Exception e) {
            LOGGER.warn("记录 curl 日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录复刻成功。
     *
     * @param response 复刻响应
     */
    public void logSuccess(VoiceCloneResponse response) {
        try {
            if (aiId == 0) return;
            noi = db.getNextInteractionNumber(aiId);

            StringBuilder msg = new StringBuilder();
            msg.append("声音复刻成功\n\n");
            msg.append("音色 ID: ").append(response.getSpeakerId()).append("\n");
            if (response.getStatus() != null) {
                msg.append("状态: ").append(response.getStatus()).append("\n");
            }
            if (response.getMessage() != null) {
                msg.append("信息: ").append(response.getMessage()).append("\n");
            }
            if (response.getRaw() != null) {
                msg.append("\n--- 原始返回 ---\n").append(response.getRaw().toString(2)).append("\n");
            }

            db.addMessage(aiId, msg.toString(), "assistant", "voice_clone",
                    null, null, null, noi, false, false);

        } catch (Exception e) {
            LOGGER.warn("记录声音复刻成功日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录复刻失败。
     */
    public void logError(Throwable error) {
        try {
            if (aiId == 0) return;
            noi = db.getNextInteractionNumber(aiId);

            String msg = "声音复刻失败: " + (error.getMessage() != null ? error.getMessage() : error.toString());
            db.addMessage(aiId, msg, "assistant", "voice_clone",
                    null, null, null, noi, false, false);

        } catch (Exception e) {
            LOGGER.warn("记录声音复刻失败日志失败: {}", e.getMessage());
        }
    }

    /** @return 创建的 AI_CHAT ID */
    public long getAiId() { return aiId; }

    /** @return 请求 ID */
    public String getRequestId() { return requestId; }
}
