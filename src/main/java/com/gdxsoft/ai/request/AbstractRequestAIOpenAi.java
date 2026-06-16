package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.easyweb.utils.UJSon;

/**
 * OpenAI 风格 AI 请求抽象基类。
 * <p>
 * 适用于所有使用 OpenAI Chat Completions 格式的 Provider：
 * <ul>
 *   <li>认证：{@code Authorization: Bearer {apiKey}}</li>
 *   <li>SSE 响应：{@code data: {"choices":[{"delta":{"content":"..."}}]}}</li>
 *   <li>结束标记：{@code data: [DONE]}</li>
 * </ul>
 * <p>
 * 子类只需提供默认 URL 和 ProviderType，HTTP 请求和流式处理全部由
 * {@link RequestAIBase} 提供。
 *
 * <h3>使用示例</h3>
 * <pre>
 * public class MyProviderRequestAI extends AbstractRequestAIOpenAi {
 *     public static final String DEFAULT_URL = "https://api.example.com/v1/chat/completions";
 *     public MyProviderRequestAI() {
 *         this.providerType = ProviderType.OPENAI_COMPAT;
 *     }
 *     {@literal @}Override
 *     public String getDefaultUrl() { return DEFAULT_URL; }
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public abstract class AbstractRequestAIOpenAi extends RequestAIBase {

    /**
     * 获取默认 API URL，由子类实现。
     *
     * @return 默认 API URL
     */
    public abstract String getDefaultUrl();

    /**
     * 创建请求 URL。
     * 优先使用用户自定义的 apiUrl，否则使用默认 URL。
     *
     * @param reqData 请求数据
     * @return 完整 URL
     */
    @Override
    public String createUrl(IRequestData reqData) {
        String apiUrl = super.getApiUrl();
        if (apiUrl != null && !apiUrl.isEmpty()) {
            return apiUrl;
        }
        return getDefaultUrl();
    }

    /**
     * 提取 OpenAI 兼容格式的 SSE JSON 数据。
     * <p>
     * 解析 {@code data: {"choices":[{"delta":{"content":"..."}}]}} 格式，
     * 提取 content 字段。
     *
     * @param line          原始数据行
     * @param skipDataPrefix 是否跳过 "data:" 前缀
     * @return 标准化的 JSON 对象
     */
    @Override
    public JSONObject extraceJson(String line, boolean skipDataPrefix) {
        String jsonData = skipDataPrefix ? line.trim() : extractDataPrefix(line);
        if (jsonData == null) {
            return UJSon.rstFalse("没有data:的数据行，" + line);
        }
        if (jsonData.isEmpty()) {
            return UJSon.rstFalse("data:无数据，" + line);
        }
        if ("[DONE]".equals(jsonData)) {
            return UJSon.rstTrue("流结束标记");
        }

        try {
            JSONObject json = new JSONObject(jsonData);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                // 处理 usage 信息（流结束时）
                if (json.has("usage")) {
                    JSONObject usage = json.getJSONObject("usage");
                    UJSon.rstSetTrue(usage, null);
                    this.setTokensUsage(usage);
                    return usage;
                }
                if (choice.has("delta")) {
                    JSONObject delta = choice.getJSONObject("delta");
                    UJSon.rstSetTrue(delta, null);
                    return delta;
                }
                if (choice.has("message")) {
                    JSONObject message = choice.getJSONObject("message");
                    UJSon.rstSetTrue(message, null);
                    return message;
                }
            }
            return UJSon.rstFalse("无效数据，choices 为空或无 delta/message，" + line);
        } catch (Exception e) {
            return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
        }
    }

    /**
     * 从 SSE 行中提取 "data:" 前缀后的内容。
     *
     * @param line 原始行
     * @return data: 后的内容，如果不是 data: 开头则返回 null
     */
    protected String extractDataPrefix(String line) {
        if (line == null) {
            return null;
        }
        if (!line.startsWith("data:")) {
            return null;
        }
        return line.substring(5).trim();
    }
}
