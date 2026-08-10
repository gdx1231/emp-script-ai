package com.gdxsoft.ai.img;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.json.JSONObject;

import com.gdxsoft.easyweb.script.RequestValue;

/**
 * High-level facade for image generation.
 * <p>
 * Typical use:
 * <pre>{@code
 * // URL mode — no OOM risk
 * ImgResponse r = ImgClient.of("openai_img")
 *     .apiKey("sk-...")
 *     .generate("A cute cat wearing a spacesuit");
 * System.out.println(r.getFirstImage().getUrl());
 *
 * // OOM-safe: stream directly to files
 * List&lt;Path&gt; files = ImgClient.of("doubao_img")
 *     .apiKey("...")
 *     .generateToFiles("A beautiful landscape", Path.of("/tmp/images"));
 * }</pre>
 *
 * @since 1.2.0
 */
public final class ImgClient {
    private final IImgProvider provider;
    /** 可选的聊天日志记录器，将图片生成任务记录到 AI_CHAT / AI_CHAT_MSG */
    private ImgChatLogger chatLogger;

    public ImgClient(IImgProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    /** Convenience factory — creates the provider and returns a client. */
    public static ImgClient of(String providerName) {
        return new ImgClient(ImgProviderFactory.create(providerName));
    }

    /** Convenience factory using an already-configured provider. */
    public static ImgClient of(IImgProvider provider) {
        return new ImgClient(provider);
    }

    /** Set the API key on the underlying provider (fluent). */
    public ImgClient apiKey(String key) {
        provider.setApiKey(key);
        return this;
    }

    /** Set the API URL on the underlying provider (fluent). */
    public ImgClient apiUrl(String url) {
        provider.setApiUrl(url);
        return this;
    }

    /** Set a provider-specific config value (fluent). */
    public ImgClient config(String key, String value) {
        provider.setConfig(key, value);
        return this;
    }

    /**
     * 设置聊天日志记录器（fluent）。
     * 设置后，generate 调用会自动记录到 AI_CHAT / AI_CHAT_MSG。
     *
     * @param logger 日志记录器，null 则不记录
     * @return this
     */
    public ImgClient setChatLogger(ImgChatLogger logger) {
        this.chatLogger = logger;
        return this;
    }

    /** @return 当前聊天日志记录器（可能为 null） */
    public ImgChatLogger getChatLogger() { return chatLogger; }

    /**
     * 设置请求上下文，自动从 rv 中读取 {@code ewa_db_config} 创建日志记录器。
     * <p>
     * 框架（ChatManagerBase）在调用 Action 前会将 dbConfigName 注入 rv，
     * 调用此方法后无需再手动 setChatLogger。
     *
     * @param rv 请求上下文
     * @return this
     */
    public ImgClient setRv(RequestValue rv) {
        if (rv != null && chatLogger == null) {
            String dbConfig = rv.getString("ewa_db_config");
            if (dbConfig != null && !dbConfig.isEmpty()) {
                this.chatLogger = ImgChatLogger.create(dbConfig);
            }
        }
        return this;
    }

    /** Generate an image from a prompt with default options. */
    public ImgResponse generate(String prompt) throws IOException, InterruptedException {
        return provider.generate(new ImgRequest(new ImgOptions(prompt)));
    }

    /** Generate with the supplied options. */
    public ImgResponse generate(ImgOptions options) throws IOException, InterruptedException {
        return provider.generate(new ImgRequest(options));
    }

    /** Generate with a fully-formed request. */
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        ImgOptions opts = request.getOptions();
        if (chatLogger != null) {
            chatLogger.logStart(provider.getProviderType().getName(),
                    opts.getModel(), opts.getPrompt(), buildOptsJson(opts));
        }
        try {
            ImgResponse resp = provider.generate(request);
            if (chatLogger != null) {
                chatLogger.logSuccess(resp);
            }
            return resp;
        } catch (Exception e) {
            if (chatLogger != null) {
                chatLogger.logError(e);
            }
            throw e;
        }
    }

    // ==== OOM-safe convenience methods ====

    /**
     * Generate and save directly to files (streaming, no full buffering in memory).
     * Uses URL response format to avoid base64 in memory.
     *
     * @param prompt image description
     * @param outputDir directory to save images
     * @return list of saved file paths
     */
    public List<Path> generateToFiles(String prompt, Path outputDir)
            throws IOException, InterruptedException {
        return generateToFiles(new ImgOptions(prompt).responseFormat("url"), outputDir);
    }

    /**
     * Generate with options and save directly to files.
     * Automatically uses URL format to avoid base64 OOM.
     */
    public List<Path> generateToFiles(ImgOptions options, Path outputDir)
            throws IOException, InterruptedException {
        if (!"b64_json".equals(options.getResponseFormat())) {
            options.responseFormat("url"); // URL mode avoids base64 in memory
        }
        ImgResponse resp = generate(options);
        List<Path> files = resp.saveAll(outputDir);
        // Release any base64 data
        for (ImgResponse.GeneratedImage img : resp.getImages()) {
            img.release();
        }
        return files;
    }

    public IImgProvider getProvider() { return provider; }

    private static JSONObject buildOptsJson(ImgOptions opts) {
        JSONObject j = new JSONObject();
        if (opts.getSize() != null) j.put("size", opts.getSize());
        if (opts.getQuality() != null) j.put("quality", opts.getQuality());
        if (opts.getStyle() != null) j.put("style", opts.getStyle());
        if (opts.getN() != null) j.put("n", opts.getN());
        if (opts.getResponseFormat() != null) j.put("response_format", opts.getResponseFormat());
        if (opts.getNegativePrompt() != null) j.put("negative_prompt", opts.getNegativePrompt());
        if (opts.getSteps() != null) j.put("steps", opts.getSteps());
        if (opts.getSeed() != null) j.put("seed", opts.getSeed());
        if (opts.getRefImageUrl() != null) j.put("ref_image", opts.getRefImageUrl());
        if (opts.getRefStrength() != null) j.put("ref_strength", opts.getRefStrength());
        if (opts.getRefMode() != null) j.put("ref_mode", opts.getRefMode());
        return j.length() > 0 ? j : null;
    }
}
