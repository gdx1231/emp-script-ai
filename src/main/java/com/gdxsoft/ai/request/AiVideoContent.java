package com.gdxsoft.ai.request;

/**
 * 视频内容片段。
 * <p>
 * 支持两种方式：URL 或 Base64 数据。
 *
 * @since 1.1.0
 */
public class AiVideoContent extends AiContent {
    private final String url;
    private final String base64Data;

    public AiVideoContent(String url, String base64Data) {
        super(AiContentType.VIDEO);
        this.url = url;
        this.base64Data = base64Data;
    }

    public String getUrl() { return url; }
    public String getBase64Data() { return base64Data; }
    public boolean isUrlMode() { return url != null && !url.isEmpty(); }
}
