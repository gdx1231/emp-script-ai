package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * OpenAI 风格请求数据抽象基类。
 * <p>
 * 适用于所有使用 OpenAI Chat Completions 格式的请求体：
 * <ul>
 *   <li>请求体结构：{@code {"model": "...", "messages": [...], ...}}</li>
 *   <li>角色：system / user / assistant / tool</li>
 *   <li>参数：temperature, top_p, stream, stream_options 等</li>
 *   <li>工具：tools[] + tool_choice</li>
 *   <li>多模态：content 数组（text/image_url/input_audio/video_url）</li>
 * </ul>
 * <p>
 * 子类只需提供默认模型和 ProviderType，其余全部继承。
 * 如需自定义 thinking 参数格式（如 Qwen 的 object 格式、OpenRouter 的 reasoning），
 * 覆写 {@link #thinking(boolean)} 即可。
 *
 * <h3>使用示例</h3>
 * <pre>
 * public class MyProviderRequestData extends AbstractRequestDataOpenAi {
 *     public MyProviderRequestData() {
 *         super("my-model-name");
 *         this.providerType = ProviderType.OPENAI_COMPAT;
 *     }
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public abstract class AbstractRequestDataOpenAi extends RequestDataBase {

    /** 工具列表 */
    protected JSONArray tools;
    /** 工具选择模式 */
    protected String toolChoice;

    /**
     * 构造函数。
     *
     * @param defaultModel 默认模型名称
     */
    protected AbstractRequestDataOpenAi(String defaultModel) {
        super(defaultModel);
    }

    /**
     * 设置工具列表。
     */
    @Override
    public IRequestData tools(AiTool... aiTools) {
        if (aiTools != null && aiTools.length > 0) {
            this.tools = AiTool.toOpenAiArray(aiTools);
        }
        return this;
    }

    /**
     * 设置工具选择模式：auto / required / none。
     */
    @Override
    public IRequestData toolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
        return this;
    }

    /**
     * 添加工具调用结果消息（OpenAI 格式：role=tool, tool_call_id=...）。
     */
    @Override
    public IRequestData addToolResult(String toolCallId, String content) {
        JSONObject message = new JSONObject();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", content);
        messages.put(message);
        return this;
    }

    /**
     * 添加用户多部分消息（OpenAI 格式：content 为数组）。
     * <p>
     * 支持文本、图片（URL/Base64）、音频、视频。
     */
    @Override
    public IRequestData addUserMultiPart(AiContent... contents) {
        JSONObject message = new JSONObject();
        message.put("role", "user");

        if (contents.length == 1 && contents[0] instanceof AiTextContent) {
            message.put("content", ((AiTextContent) contents[0]).getText());
        } else {
            JSONArray contentArr = new JSONArray();
            for (AiContent c : contents) {
                contentArr.put(toOpenAiContentPart(c));
            }
            message.put("content", contentArr);
        }

        messages.put(message);
        return this;
    }

    /**
     * 将 AiContent 转换为 OpenAI 格式的 content part。
     */
    protected JSONObject toOpenAiContentPart(AiContent content) {
        JSONObject part = new JSONObject();
        switch (content.getType()) {
            case TEXT:
                part.put("type", "text");
                part.put("text", ((AiTextContent) content).getText());
                break;
            case IMAGE:
                AiImageContent img = (AiImageContent) content;
                part.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                if (img.isUrlMode()) {
                    imageUrl.put("url", img.getUrl());
                } else {
                    imageUrl.put("url", "data:" + img.getMimeType() + ";base64," + img.getBase64Data());
                }
                part.put("image_url", imageUrl);
                break;
            case AUDIO:
                AiAudioContent audio = (AiAudioContent) content;
                part.put("type", "input_audio");
                JSONObject audioData = new JSONObject();
                if (audio.isUrlMode()) {
                    audioData.put("url", audio.getUrl());
                } else {
                    audioData.put("data", audio.getBase64Data());
                    audioData.put("format", audio.getMimeType() != null ? audio.getMimeType().split("/")[1] : "wav");
                }
                part.put("input_audio", audioData);
                break;
            case VIDEO:
                AiVideoContent video = (AiVideoContent) content;
                part.put("type", "video_url");
                JSONObject videoUrl = new JSONObject();
                if (video.isUrlMode()) {
                    videoUrl.put("url", video.getUrl());
                } else {
                    videoUrl.put("url", "data:video/mp4;base64," + video.getBase64Data());
                }
                part.put("video_url", videoUrl);
                break;
            case TOOL_RESULT:
                AiToolResult tr = (AiToolResult) content;
                part.put("type", "text");
                part.put("text", "[Tool Result: " + tr.getToolCallId() + "] " + tr.getContent());
                break;
            default:
                part.put("type", "text");
                part.put("text", content.toString());
        }
        return part;
    }

    /**
     * 构建 OpenAI Chat Completions 标准格式请求体。
     */
    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        requestData.put("model", this.model);
        requestData.put("messages", messages);

        if (tools != null && tools.length() > 0) {
            requestData.put("tools", tools);
            if (toolChoice != null) {
                requestData.put("tool_choice", toolChoice);
            }
        }

        return requestData;
    }
}
