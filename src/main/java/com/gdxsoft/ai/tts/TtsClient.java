package com.gdxsoft.ai.tts;

import java.io.IOException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.script.RequestValue;

/**
 * High-level facade for TTS synthesis.
 * <p>
 * Typical use:
 * <pre>{@code
 * TtsResponse r = TtsClient.of("qwen_tts")
 *     .synthesize("你好，世界");
 * r.save(Path.of("hello.wav"));
 * }</pre>
 *
 * @since 1.1.0
 */
public final class TtsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TtsClient.class);

    private final ITtsProvider provider;
    private String dbConfigName;
    private RequestValue rv;

    public TtsClient(ITtsProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    /** Convenience factory. */
    public static TtsClient of(String providerName) {
        return new TtsClient(TtsProviderFactory.create(providerName));
    }

    /** Convenience factory using an already-configured provider. */
    public static TtsClient of(ITtsProvider provider) {
        return new TtsClient(provider);
    }

    /** 设置数据库日志配置（链式调用）。设置后 synthesize 会自动记录到 AI_CHAT / AI_CHAT_MSG。 */
    public TtsClient setDbConfig(String dbConfigName, RequestValue rv) {
        this.dbConfigName = dbConfigName;
        this.rv = rv;
        return this;
    }

    /** Synthesize with default options. */
    public TtsResponse synthesize(String text) throws IOException, InterruptedException {
        return synthesize(new TtsRequest(text));
    }

    /** Synthesize with the supplied options. */
    public TtsResponse synthesize(String text, TtsOptions options) throws IOException, InterruptedException {
        return synthesize(new TtsRequest(text, options));
    }

    /** Synthesize with a fully-formed request. */
    public TtsResponse synthesize(TtsRequest request) throws IOException, InterruptedException {
        // 日志记录
        TtsChatLogger chatLogger = null;
        if (dbConfigName != null) {
            chatLogger = TtsChatLogger.create(rv, dbConfigName);
            if (chatLogger != null) {
                String model = request.getOptions().getModel();
                if (model == null || model.isEmpty()) model = provider.getConfig("model");
                if (model == null || model.isEmpty()) model = "unknown";

                JSONObject opts = new JSONObject();
                if (request.getOptions().getVoice() != null) opts.put("voice", request.getOptions().getVoice());
                if (request.getOptions().getFormat() != null) opts.put("format", request.getOptions().getFormat());
                if (request.getOptions().getSpeed() != null) opts.put("speed", request.getOptions().getSpeed());
                if (request.getOptions().getLanguageType() != null) opts.put("languageType", request.getOptions().getLanguageType());
                if (request.getOptions().getSampleRate() != null) opts.put("sampleRate", request.getOptions().getSampleRate());
                if (request.getOptions().getInstruction() != null) opts.put("instruction", request.getOptions().getInstruction());

                chatLogger.logStart(provider.getProviderType().getName(), model, request.getText(), opts);

                // 记录 curl
                try {
                    chatLogger.logCurl(provider.curl(request));
                } catch (Exception e) {
                    LOGGER.debug("生成 curl 命令失败: {}", e.getMessage());
                }
            }
        }

        try {
            // 记录请求的 curl 命令（含完整 key，未脱敏），便于排查问题
            if (LOGGER.isInfoEnabled()) {
                try {
                    LOGGER.info("TTS curl [{}]: {}", provider.getProviderType().getName(),
                            provider.curl(request));
                } catch (Exception e) {
                    LOGGER.warn("TTS curl render failed: {}", e.getMessage());
                }
            }
            TtsResponse response = provider.synthesize(request);

            // 记录成功
            if (chatLogger != null) {
                chatLogger.logSuccess(response);
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
            throw new IOException("TTS 合成失败: " + e.getMessage(), e);
        }
    }

    public ITtsProvider getProvider() { return provider; }
}
