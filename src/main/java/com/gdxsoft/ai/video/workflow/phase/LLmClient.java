package com.gdxsoft.ai.video.workflow.phase;

import java.io.IOException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.request.IRequestAI;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.RequestAIFactory;
import com.gdxsoft.ai.request.RequestDataFactory;

/**
 * 简单的 LLM 调用客户端 — 封装 provider 初始化和非流式调用。
 *
 * <p>用于工作流引擎各阶段中的 LLM 调用（分镜拆解等），不依赖 ChatManagerBase。
 *
 * @since 1.4.0
 */
public class LLmClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(LLmClient.class);

    private final String providerName;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private double temperature = 0.3;
    private double topP = 0.9;

    public LLmClient(String providerName, String apiUrl, String apiKey, String model) {
        this.providerName = providerName;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public LLmClient temperature(double v) { this.temperature = v; return this; }
    public LLmClient topP(double v) { this.topP = v; return this; }

    /**
     * 发送 prompt 并返回响应文本。
     *
     * @param systemPrompt  系统提示词（可为 null）
     * @param userPrompt    用户提示词
     * @param responseFormat 响应格式（"json_object" / "text"，null=text）
     * @return LLM 响应文本
     */
    public String chat(String systemPrompt, String userPrompt, String responseFormat)
            throws IOException, InterruptedException {

        IRequestAI provider = RequestAIFactory.createRequestAI(providerName);
        provider.initUrlAndKey(apiUrl, apiKey);

        IRequestData reqData = RequestDataFactory.createRequestData(providerName);
        reqData.model(model).stream(false).temperature(temperature).topP(topP);

        if (responseFormat != null && !"text".equals(responseFormat)) {
            reqData.responseFormat(responseFormat);
        }

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            reqData.addMessage(systemPrompt, "system");
        }
        reqData.addMessage(userPrompt, "user");

        LOGGER.info("LLM 调用: provider={}, model={}, userPromptLen={}",
                providerName, model, userPrompt.length());

        try {
            String resp = provider.doPost(reqData);
            JSONObject usage = provider.getTokensUsage();
            if (usage != null) {
                LOGGER.info("LLM usage: total={}, completion={}, prompt={}",
                        usage.optInt("total_tokens"), usage.optInt("completion_tokens"),
                        usage.optInt("prompt_tokens"));
            }
            return resp;
        } catch (java.net.URISyntaxException e) {
            throw new IOException("LLM URL 格式错误: " + e.getMessage(), e);
        }
    }

    /**
     * 便捷方法：发送单条 prompt（无 system 提示词）。
     */
    public String chat(String prompt, String responseFormat) throws IOException, InterruptedException {
        return chat(null, prompt, responseFormat);
    }

    public String getProviderName() { return providerName; }
    public String getModel() { return model; }
}
