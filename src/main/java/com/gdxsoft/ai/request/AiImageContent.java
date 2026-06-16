package com.gdxsoft.ai.request;

/**
 * 图片内容片段。
 * <p>
 * 支持两种方式：
 * <ul>
 *   <li>URL：直接传入图片地址</li>
 *   <li>Base64：传入 MIME 类型和 Base64 编码数据</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class AiImageContent extends AiContent {
    private final String url;
    private final String mimeType;
    private final String base64Data;

    public AiImageContent(String url, String mimeType, String base64Data) {
        super(AiContentType.IMAGE);
        this.url = url;
        this.mimeType = mimeType;
        this.base64Data = base64Data;
    }

    /** 图片 URL（Base64 模式为 null） */
    public String getUrl() {
        return url;
    }

    /** MIME 类型，如 "image/png"（URL 模式为 null） */
    public String getMimeType() {
        return mimeType;
    }

    /** Base64 编码数据（URL 模式为 null） */
    public String getBase64Data() {
        return base64Data;
    }

    /** 是否为 URL 模式 */
    public boolean isUrlMode() {
        return url != null && !url.isEmpty();
    }
}
