package com.gdxsoft.ai.request;

import org.json.JSONObject;

import com.gdxsoft.easyweb.utils.UJSon;

/**
 * Anthropic Messages API 风格 AI 请求抽象基类。
 * <p>
 * 适用于所有使用 Anthropic Messages API 格式的 Provider：
 * <ul>
 *   <li>认证：{@code x-api-key: {apiKey}} + {@code anthropic-version: 2023-06-01}</li>
 *   <li>SSE 事件：{@code event: content_block_delta} / {@code event: message_delta}</li>
 *   <li>内容提取：从 {@code delta.text} 获取文本</li>
 *   <li>Usage 提取：从 {@code message_delta.usage} 或 {@code message_start.message.usage} 获取</li>
 * </ul>
 * <p>
 * 子类只需提供默认 URL 和 ProviderType，HTTP 请求和 SSE 解析全部继承。
 *
 * <h3>使用示例</h3>
 * <pre>
 * public class MyAnthropicRequestAI extends AbstractRequestAIAnthropic {
 *     public static final String DEFAULT_URL = "https://api.example.com/v1/messages";
 *     public MyAnthropicRequestAI() {
 *         this.providerType = ProviderType.ANTHROPIC_COMPAT;
 *     }
 *     {@literal @}Override
 *     public String getDefaultUrl() { return DEFAULT_URL; }
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public abstract class AbstractRequestAIAnthropic extends RequestAIBase {

    /**
     * Anthropic API 版本
     */
    public static final String API_VERSION = "2023-06-01";

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
     * 提取 Anthropic SSE 格式的 JSON 数据。
     * <p>
     * 解析以下事件类型：
     * <ul>
     *   <li>{@code content_block_delta} — 实际文本内容，从 {@code delta.text} 提取</li>
     *   <li>{@code message_delta} — 包含 usage 信息</li>
     *   <li>{@code message_start} — 包含初始 usage 信息</li>
     * </ul>
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
            String type = json.optString("type", "");

            // content_block_delta: 实际的文本内容
            if ("content_block_delta".equals(type) && json.has("delta")) {
                JSONObject delta = json.getJSONObject("delta");
                if ("text_delta".equals(delta.optString("type")) && delta.has("text")) {
                    UJSon.rstSetTrue(delta, null);
                    return delta;
                }
            }

            // message_delta: 包含 usage 信息
            if ("message_delta".equals(type) && json.has("usage")) {
                JSONObject usage = json.getJSONObject("usage");
                UJSon.rstSetTrue(usage, null);
                this.setTokensUsage(usage);
                return usage;
            }

            // message_start: 包含初始 usage
            if ("message_start".equals(type) && json.has("message")) {
                JSONObject message = json.getJSONObject("message");
                if (message.has("usage")) {
                    JSONObject usage = message.getJSONObject("usage");
                    UJSon.rstSetTrue(usage, null);
                    this.setTokensUsage(usage);
                }
                return UJSon.rstTrue("message_start");
            }

            return UJSon.rstFalse("未处理的事件类型: " + type);
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
