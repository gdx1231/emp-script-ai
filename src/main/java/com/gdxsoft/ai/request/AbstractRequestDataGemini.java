package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Google Gemini API 风格请求数据抽象基类。
 * <p>
 * 适用于所有使用 Gemini GenerateContent 格式的请求体：
 * <ul>
 *   <li>请求体结构：{@code {"contents": [{"role": "...", "parts": [...]}], "generationConfig": {...}, "tools": [...]}}</li>
 *   <li>角色映射：system→user, assistant→model, user→user, tool→function</li>
 *   <li>参数放入 generationConfig，不支持 stream/thinking 等 OpenAI 参数</li>
 *   <li>工具：tools 使用 {functionDeclarations:[{name,description,parameters}]}</li>
 *   <li>多模态：parts 支持 text/inlineData(fileData)</li>
 * </ul>
 * <p>
 * 子类只需提供默认模型和 ProviderType，角色映射和 build() 全部继承。
 *
 * <h3>使用示例</h3>
 * <pre>
 * public class MyGeminiRequestData extends AbstractRequestDataGemini {
 *     public MyGeminiRequestData() {
 *         super("gemini-2.5-flash");
 *         this.providerType = ProviderType.GEMINI;
 *     }
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public abstract class AbstractRequestDataGemini extends RequestDataBase {

    /** 工具列表 */
    protected JSONArray tools;

    /**
     * 构造函数。
     *
     * @param defaultModel 默认模型名称
     */
    protected AbstractRequestDataGemini(String defaultModel) {
        super(defaultModel);
    }

    /**
     * Gemini API 角色映射。
     */
    @Override
    public IRequestData addMessage(String content, String role) {
        String geminiRole = mapRole(role);
        JSONObject message = new JSONObject();
        message.put("role", geminiRole);
        message.put("content", content);
        messages.put(message);
        return this;
    }

    /**
     * 将 OpenAI 角色映射为 Gemini 角色。
     */
    protected String mapRole(String role) {
        if ("system".equals(role)) {
            return "user";
        } else if ("assistant".equals(role)) {
            return "model";
        } else if ("tool".equals(role)) {
            return "function";
        }
        return role;
    }

    /**
     * 设置工具列表（Gemini 格式）。
     */
    @Override
    public IRequestData tools(AiTool... aiTools) {
        if (aiTools != null && aiTools.length > 0) {
            this.tools = AiTool.toGeminiArray(aiTools);
        }
        return this;
    }

    /**
     * 添加工具调用结果消息（Gemini 格式：role=function, parts=[{functionResponse}]）。
     */
    @Override
    public IRequestData addToolResult(String toolCallId, String content) {
        JSONObject contentItem = new JSONObject();
        contentItem.put("role", "function");

        JSONArray parts = new JSONArray();
        JSONObject functionResponse = new JSONObject();
        functionResponse.put("name", toolCallId);

        JSONObject response = new JSONObject();
        response.put("content", content);
        functionResponse.put("response", response);

        JSONObject part = new JSONObject();
        part.put("functionResponse", functionResponse);
        parts.put(part);

        contentItem.put("parts", parts);
        messages.put(contentItem);
        return this;
    }

    /**
     * 添加用户多部分消息（Gemini 格式：parts 数组）。
     * <p>
     * 支持文本、图片（inlineData/fileData）、音频、视频。
     */
    @Override
    public IRequestData addUserMultiPart(AiContent... contents) {
        JSONObject contentItem = new JSONObject();
        contentItem.put("role", "user");

        JSONArray parts = new JSONArray();
        for (AiContent c : contents) {
            parts.put(toGeminiPart(c));
        }

        contentItem.put("parts", parts);
        messages.put(contentItem);
        return this;
    }

    /**
     * 将 AiContent 转换为 Gemini 格式的 part。
     */
    protected JSONObject toGeminiPart(AiContent content) {
        JSONObject part = new JSONObject();
        switch (content.getType()) {
            case TEXT:
                part.put("text", ((AiTextContent) content).getText());
                break;
            case IMAGE:
                AiImageContent img = (AiImageContent) content;
                if (img.isUrlMode()) {
                    // fileData 模式（URL）
                    JSONObject fileData = new JSONObject();
                    fileData.put("fileUri", img.getUrl());
                    if (img.getMimeType() != null) {
                        fileData.put("mimeType", img.getMimeType());
                    }
                    part.put("fileData", fileData);
                } else {
                    // inlineData 模式（Base64）
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", img.getMimeType());
                    inlineData.put("data", img.getBase64Data());
                    part.put("inlineData", inlineData);
                }
                break;
            case AUDIO:
                AiAudioContent audio = (AiAudioContent) content;
                if (audio.isUrlMode()) {
                    JSONObject fileData = new JSONObject();
                    fileData.put("fileUri", audio.getUrl());
                    fileData.put("mimeType", audio.getMimeType() != null ? audio.getMimeType() : "audio/mpeg");
                    part.put("fileData", fileData);
                } else {
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", audio.getMimeType());
                    inlineData.put("data", audio.getBase64Data());
                    part.put("inlineData", inlineData);
                }
                break;
            case VIDEO:
                AiVideoContent video = (AiVideoContent) content;
                if (video.isUrlMode()) {
                    JSONObject fileData = new JSONObject();
                    fileData.put("fileUri", video.getUrl());
                    fileData.put("mimeType", "video/mp4");
                    part.put("fileData", fileData);
                } else {
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", "video/mp4");
                    inlineData.put("data", video.getBase64Data());
                    part.put("inlineData", inlineData);
                }
                break;
            case TOOL_RESULT:
                AiToolResult tr = (AiToolResult) content;
                JSONObject functionResponse = new JSONObject();
                functionResponse.put("name", tr.getToolCallId());
                JSONObject response = new JSONObject();
                response.put("content", tr.getContent());
                functionResponse.put("response", response);
                part.put("functionResponse", functionResponse);
                break;
            default:
                part.put("text", content.toString());
        }
        return part;
    }

    /**
     * 构建 Gemini GenerateContent API 请求体。
     */
    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject();
        JSONArray contents = new JSONArray();

        for (int i = 0; i < this.messages.length(); i++) {
            JSONObject message = this.messages.getJSONObject(i);
            String role = message.getString("role");
            String content = message.getString("content");

            if ("assistant".equals(role)) {
                role = "model";
            }

            JSONObject contentItem = new JSONObject();
            contentItem.put("role", role);

            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", content);
            parts.put(part);

            contentItem.put("parts", parts);
            contents.put(contentItem);
        }

        requestData.put("contents", contents);

        // 添加 generationConfig 参数
        if (this.parameters.length() > 0) {
            JSONObject clone = new JSONObject(this.parameters.toString());
            removeUnsupportedParams(clone);
            requestData.put("generationConfig", clone);
        }

        // 添加工具
        if (tools != null && tools.length() > 0) {
            requestData.put("tools", tools);
        }

        return requestData;
    }

    /**
     * 移除 Gemini generationConfig 不支持的参数。
     */
    protected void removeUnsupportedParams(JSONObject params) {
        if (params.has("stream")) {
            params.remove("stream");
        }
        if (params.has("thinking")) {
            params.remove("thinking");
        }
        if (params.has("stream_options")) {
            params.remove("stream_options");
        }
        if (params.has("response_format")) {
            params.remove("response_format");
        }
    }
}
