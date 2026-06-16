package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Anthropic Messages API 风格请求数据抽象基类。
 * <p>
 * 适用于所有使用 Anthropic Messages API 格式的请求体：
 * <ul>
 *   <li>请求体结构：{@code {"model": "...", "messages": [...], "system": "...", "max_tokens": 4096, "tools": [...]}}</li>
 *   <li>system 消息使用独立字段（不在 messages 数组中）</li>
 *   <li>必须指定 max_tokens 参数</li>
 *   <li>工具格式：tools[] 使用 {name, description, input_schema}</li>
 *   <li>多模态：messages[].content 为数组，支持 text/image</li>
 * </ul>
 * <p>
 * 子类只需提供默认模型和 ProviderType，其余全部继承。
 *
 * <h3>使用示例</h3>
 * <pre>
 * public class MyAnthropicRequestData extends AbstractRequestDataAnthropic {
 *     public MyAnthropicRequestData() {
 *         super("claude-sonnet-4-20250514");
 *         this.providerType = ProviderType.ANTHROPIC_COMPAT;
 *     }
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public abstract class AbstractRequestDataAnthropic extends RequestDataBase {

    /** 默认 max_tokens 值 */
    private static final int DEFAULT_MAX_TOKENS = 4096;
    /** 工具列表 */
    protected JSONArray tools;
    /** 工具选择模式 */
    protected String toolChoice;

    /**
     * 构造函数。
     *
     * @param defaultModel 默认模型名称
     */
    protected AbstractRequestDataAnthropic(String defaultModel) {
        super(defaultModel);
    }

    /**
     * Anthropic 格式：system 消息使用独立 system 字段，不在 messages 数组中。
     */
    @Override
    public IRequestData systemMessage(String content) {
        parameters.put("system", content);
        return this;
    }

    /**
     * 设置最大 tokens（Anthropic 必须指定此参数）。
     */
    @Override
    public IRequestData maxTokens(int maxTokens) {
        parameters.put("max_tokens", maxTokens);
        return this;
    }

    /**
     * Anthropic 不支持顶层 thinking 参数（思考模式由模型本身支持）。
     */
    @Override
    public IRequestData thinking(boolean thinking) {
        return this;
    }

    /**
     * 设置工具列表（Anthropic 格式）。
     */
    @Override
    public IRequestData tools(AiTool... aiTools) {
        if (aiTools != null && aiTools.length > 0) {
            this.tools = AiTool.toAnthropicArray(aiTools);
        }
        return this;
    }

    /**
     * 设置工具选择模式：auto / any / tool。
     */
    @Override
    public IRequestData toolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
        return this;
    }

    /**
     * 添加工具调用结果消息（Anthropic 格式：role=user, content=[{type:tool_result,...}]）。
     */
    @Override
    public IRequestData addToolResult(String toolCallId, String content) {
        JSONArray contentArr = new JSONArray();
        JSONObject toolResult = new JSONObject();
        toolResult.put("type", "tool_result");
        toolResult.put("tool_use_id", toolCallId);

        JSONArray resultContent = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", content);
        resultContent.put(textPart);

        toolResult.put("content", resultContent);
        contentArr.put(toolResult);

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", contentArr);
        messages.put(message);
        return this;
    }

    /**
     * 添加用户多部分消息（Anthropic 格式：content 为数组）。
     * <p>
     * 支持文本、图片（Base64 或 URL）。
     */
    @Override
    public IRequestData addUserMultiPart(AiContent... contents) {
        JSONArray contentArr = new JSONArray();
        for (AiContent c : contents) {
            contentArr.put(toAnthropicContentPart(c));
        }

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", contentArr);
        messages.put(message);
        return this;
    }

    /**
     * 将 AiContent 转换为 Anthropic 格式的 content part。
     */
    protected JSONObject toAnthropicContentPart(AiContent content) {
        JSONObject part = new JSONObject();
        switch (content.getType()) {
            case TEXT:
                part.put("type", "text");
                part.put("text", ((AiTextContent) content).getText());
                break;
            case IMAGE:
                AiImageContent img = (AiImageContent) content;
                part.put("type", "image");
                JSONObject source = new JSONObject();
                if (img.isUrlMode()) {
                    // Anthropic 优先使用 base64，URL 模式自动转换提示
                    source.put("type", "base64");
                    source.put("media_type", img.getMimeType() != null ? img.getMimeType() : "image/png");
                    source.put("data", img.getUrl()); // URL 作为 data 传递（需要调用方先下载转 base64）
                } else {
                    source.put("type", "base64");
                    source.put("media_type", img.getMimeType());
                    source.put("data", img.getBase64Data());
                }
                part.put("source", source);
                break;
            case TOOL_RESULT:
                AiToolResult tr = (AiToolResult) content;
                part.put("type", "tool_result");
                part.put("tool_use_id", tr.getToolCallId());
                JSONArray textArr = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", tr.getContent());
                textArr.put(textPart);
                part.put("content", textArr);
                break;
            case AUDIO:
            case VIDEO:
                // Anthropic 不直接支持音频/视频 content part，降级为文本提示
                part.put("type", "text");
                part.put("text", "[多媒体: " + content.getType().getName() + " 不支持]");
                break;
            default:
                part.put("type", "text");
                part.put("text", content.toString());
        }
        return part;
    }

    /**
     * 构建 Anthropic Messages API 请求体。
     */
    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        requestData.put("model", this.model);
        requestData.put("messages", messages);

        // Anthropic 必须指定 max_tokens
        if (!requestData.has("max_tokens")) {
            requestData.put("max_tokens", DEFAULT_MAX_TOKENS);
        }

        // 添加工具
        if (tools != null && tools.length() > 0) {
            requestData.put("tools", tools);
            if (toolChoice != null) {
                requestData.put("tool_choice", parseAnthropicToolChoice(toolChoice));
            }
        }

        return requestData;
    }

    /**
     * 解析工具选择模式为 Anthropic 格式。
     */
    protected JSONObject parseAnthropicToolChoice(String toolChoice) {
        JSONObject result = new JSONObject();
        switch (toolChoice) {
            case "required":
                result.put("type", "any");
                break;
            case "none":
                // Anthropic 不支持 none，直接不传 tools
                break;
            default: // "auto" or specific tool
                if (toolChoice.startsWith("tool:")) {
                    result.put("type", "tool");
                    result.put("name", toolChoice.substring(5));
                } else {
                    result.put("type", "auto");
                }
        }
        return result;
    }
}
