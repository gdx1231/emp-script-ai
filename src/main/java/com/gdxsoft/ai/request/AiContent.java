package com.gdxsoft.ai.request;

/**
 * AI 消息内容片段基类。
 * <p>
 * 支持多模态消息：文本、图片、音频、视频、工具调用、工具结果等。
 * 每个子类代表一种内容类型。
 *
 * @since 1.1.0
 */
public abstract class AiContent {

    private final AiContentType type;

    protected AiContent(AiContentType type) {
        this.type = type;
    }

    /**
     * 获取内容类型。
     */
    public AiContentType getType() {
        return type;
    }

    /**
     * 创建纯文本内容。
     */
    public static AiTextContent text(String text) {
        return new AiTextContent(text);
    }

    /**
     * 创建图片内容（URL）。
     */
    public static AiImageContent imageUrl(String url) {
        return new AiImageContent(url, null, null);
    }

    /**
     * 创建图片内容（Base64 数据）。
     *
     * @param mimeType   MIME 类型，如 "image/png"、"image/jpeg"
     * @param base64Data Base64 编码的图片数据（不含 data: 前缀）
     */
    public static AiImageContent imageBase64(String mimeType, String base64Data) {
        return new AiImageContent(null, mimeType, base64Data);
    }

    /**
     * 创建音频内容（URL）。
     */
    public static AiAudioContent audioUrl(String url) {
        return new AiAudioContent(url, null, null);
    }

    /**
     * 创建音频内容（Base64 数据）。
     *
     * @param mimeType   MIME 类型，如 "audio/mpeg"、"audio/wav"
     * @param base64Data Base64 编码的音频数据
     */
    public static AiAudioContent audioBase64(String mimeType, String base64Data) {
        return new AiAudioContent(null, mimeType, base64Data);
    }

    /**
     * 创建视频内容（URL）。
     */
    public static AiVideoContent videoUrl(String url) {
        return new AiVideoContent(url, null);
    }

    /**
     * 创建视频内容（Base64 数据）。
     *
     * @param mimeType   MIME 类型，如 "video/mp4"
     * @param base64Data Base64 编码的视频数据
     */
    public static AiVideoContent videoBase64(String mimeType, String base64Data) {
        return new AiVideoContent(null, base64Data);
    }

    /**
     * 创建工具调用内容（AI 请求调用工具）。
     */
    public static AiToolCall toolUse(String id, String name, String arguments) {
        return new AiToolCall(id, name, arguments);
    }

    /**
     * 创建工具结果内容（工具执行后的返回内容）。
     */
    public static AiToolResult toolResult(String toolCallId, String content) {
        return new AiToolResult(toolCallId, content);
    }
}
