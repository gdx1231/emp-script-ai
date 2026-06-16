package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.easyweb.utils.UJSon;

/**
 * Google Gemini API 风格 AI 请求抽象基类。
 * <p>
 * 适用于所有使用 Gemini GenerateContent 格式的 Provider：
 * <ul>
 *   <li>认证：{@code x-goog-api-key: {apiKey}}</li>
 *   <li>URL：{@code /v1beta/models/{model}:streamGenerateContent?alt=sse}（流式）</li>
 *   <li>SSE 响应：{@code data: {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}}</li>
 *   <li>Usage：从 {@code usageMetadata} 提取，映射为 OpenAI 格式</li>
 * </ul>
 * <p>
 * 子类只需提供默认 URL 基础（不含模型名）和 ProviderType。
 *
 * <h3>使用示例</h3>
 * <pre>
 * public class MyGeminiRequestAI extends AbstractRequestAIGemini {
 *     public static final String DEFAULT_URL_BASE = "https://generativelanguage.googleapis.com/v1beta";
 *     public MyGeminiRequestAI() {
 *         this.providerType = ProviderType.GEMINI;
 *     }
 *     {@literal @}Override
 *     public String getDefaultUrlBase() { return DEFAULT_URL_BASE; }
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public abstract class AbstractRequestAIGemini extends RequestAIBase {

    /**
     * 获取默认 URL 基础（不含模型名和冒号后缀），由子类实现。
     *
     * @return 默认 URL 基础，例如 "https://generativelanguage.googleapis.com/v1beta"
     */
    public abstract String getDefaultUrlBase();

    /**
     * 创建 Gemini 请求 URL。
     * <p>
     * 流式：{@code {baseUrl}/models/{model}:streamGenerateContent}
     * 非流式：{@code {baseUrl}/models/{model}:generateContent}
     *
     * @param reqData 请求数据
     * @return 完整 URL
     */
    @Override
    public String createUrl(IRequestData reqData) {
        String apiUrl = super.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = getDefaultUrlBase();
        }
        // 移除尾部斜杠
        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }
        apiUrl += "/models/" + reqData.getModel();
        if (reqData.isStream()) {
            return apiUrl + ":streamGenerateContent";
        } else {
            return apiUrl + ":generateContent";
        }
    }

    /**
     * 提取 Gemini SSE 格式的 JSON 数据。
     * <p>
     * 解析 {@code data: {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}} 格式，
     * 提取文本内容并映射为 OpenAI 兼容格式 {@code {"content": "...", "RST": true}}。
     * <p>
     * 同时从 {@code usageMetadata} 提取 token usage，映射为 OpenAI 格式：
     * <pre>
     * {
     *   "completion_tokens": candidatesTokenCount + thoughtsTokenCount,
     *   "prompt_tokens": promptTokenCount,
     *   "total_tokens": totalTokenCount,
     *   "reasoning_tokens": thoughtsTokenCount
     * }
     * </pre>
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

        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(jsonData);

            // 提取文本内容
            if (json.has("candidates")) {
                JSONArray candidates = json.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);
                    if (candidate.has("content")) {
                        JSONObject content = candidate.getJSONObject("content");
                        if (content.has("parts")) {
                            JSONArray parts = content.getJSONArray("parts");
                            if (parts.length() > 0) {
                                JSONObject part = parts.getJSONObject(0);
                                if (part.has("text")) {
                                    result.put("content", part.getString("text"));
                                }
                            }
                        }
                    }
                }
            }

            // 提取 usage 信息并映射为 OpenAI 格式
            if (json.has("usageMetadata")) {
                JSONObject usage = json.getJSONObject("usageMetadata");
                int candidatesTokenCount = usage.optInt("candidatesTokenCount");
                int promptTokenCount = usage.optInt("promptTokenCount");
                int totalTokenCount = usage.optInt("totalTokenCount");
                int thoughtsTokenCount = usage.optInt("thoughtsTokenCount");

                var existingUsage = super.getTokensUsage();
                if (existingUsage != null) {
                    existingUsage.put("completion_tokens",
                            existingUsage.optInt("completion_tokens", 0) + candidatesTokenCount + thoughtsTokenCount);
                    existingUsage.put("reasoning_tokens",
                            existingUsage.optInt("reasoning_tokens", 0) + thoughtsTokenCount);
                    existingUsage.put("total_tokens",
                            existingUsage.optInt("total_tokens", 0) + totalTokenCount);
                } else {
                    JSONObject usageOpenAi = new JSONObject();
                    usageOpenAi.put("completion_tokens", candidatesTokenCount + thoughtsTokenCount);
                    usageOpenAi.put("prompt_tokens", promptTokenCount);
                    usageOpenAi.put("total_tokens", totalTokenCount);
                    usageOpenAi.put("reasoning_tokens", thoughtsTokenCount);
                    UJSon.rstSetTrue(usageOpenAi, null);
                    super.setTokensUsage(usageOpenAi);
                }
            }

            if (result.has("content")) {
                UJSon.rstSetTrue(result, null);
                return result;
            }
            return UJSon.rstFalse("无效数据，没有找到文本内容，" + line);
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
