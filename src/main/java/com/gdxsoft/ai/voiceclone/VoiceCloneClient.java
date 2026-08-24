package com.gdxsoft.ai.voiceclone;

import java.io.IOException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.easyweb.script.RequestValue;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceCloneClient.class);

    private final IVoiceCloneProvider provider;
    private String dbConfigName;
    private RequestValue rv;

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

    /** 设置数据库日志配置（链式调用）。设置后 clone 操作会自动记录到 AI_CHAT / AI_CHAT_MSG。 */
    public VoiceCloneClient setDbConfig(String dbConfigName, RequestValue rv) {
        this.dbConfigName = dbConfigName;
        this.rv = rv;
        return this;
    }

    /**
     * 新建音色：传入音频样本，克隆后返回 speaker_id。
     */
    public VoiceCloneResponse clone(AudioSource audio) throws IOException, InterruptedException {
        return clone(new VoiceCloneRequest(audio));
    }

    /**
     * 克隆/升级音色（完整请求）。
     */
    public VoiceCloneResponse clone(VoiceCloneRequest request) throws IOException, InterruptedException {
        // 日志记录
        VoiceCloneChatLogger chatLogger = null;
        if (dbConfigName != null) {
            chatLogger = VoiceCloneChatLogger.create(rv, dbConfigName);
            if (chatLogger != null) {
                String targetModel = provider.getConfig("targetModel");
                if (targetModel == null) targetModel = provider.getConfig("model");
                if (targetModel == null) targetModel = "unknown";

                String audioUrl = provider.getConfig("audioUrl");
                JSONObject opts = new JSONObject();
                if (request.getSpeakerId() != null) opts.put("speakerId", request.getSpeakerId());
                if (request.getDemoText() != null) opts.put("demoText", request.getDemoText());
                if (request.getOptions() != null) {
                    for (var e : request.getOptions().getExtras().entrySet()) {
                        opts.put(e.getKey(), e.getValue());
                    }
                }

                chatLogger.logStart(provider.getProviderType().getName(), targetModel, audioUrl, opts);

                // 记录 curl
                try {
                    chatLogger.logCurl(provider.curl(request));
                } catch (Exception e) {
                    LOGGER.debug("生成 curl 命令失败: {}", e.getMessage());
                }
            }
        }

        try {
            VoiceCloneResponse response = provider.clone(request);

            // 记录成功
            if (chatLogger != null) {
                chatLogger.logSuccess(response);
            }

            // 记录到 AI_VOICE_CLONE 表
            if (dbConfigName != null && response.isSuccess()) {
                try {
                    VoiceCloneDb vcDb = new VoiceCloneDb(dbConfigName);
                    String audioUrl = provider.getConfig("audioUrl");
                    String prefix = provider.getConfig("prefix");
                    String targetModel = provider.getConfig("targetModel");
                    if (targetModel == null) targetModel = provider.getConfig("model");
                    Integer admId = rv != null ? (int) rv.getLong("g_ADM_ID.long") : null;
                    Integer usrId = rv != null ? (int) rv.getLong("G_WEB_USR_ID.long") : null;
                    Integer supId = rv != null ? (int) rv.getLong("g_SUP_ID.long") : null;
                    vcDb.save(response.getSpeakerId(), provider.getProviderType().getName(),
                            targetModel, prefix, audioUrl, null, admId, usrId, supId);
                } catch (Exception e) {
                    LOGGER.warn("保存 AI_VOICE_CLONE 记录失败: {}", e.getMessage());
                }
            }

            return response;
        } catch (IOException | InterruptedException e) {
            if (chatLogger != null) {
                chatLogger.logError(e);
            }
            throw e;
        } catch (Exception e) {
            if (chatLogger != null) {
                chatLogger.logError(e);
            }
            throw new IOException("声音克隆失败: " + e.getMessage(), e);
        }
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
