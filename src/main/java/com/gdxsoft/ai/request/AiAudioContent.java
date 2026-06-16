package com.gdxsoft.ai.request;

/**
 * 音频内容片段。
 * <p>
 * 支持两种方式：URL 或 Base64 数据。
 *
 * @since 1.1.0
 */
public class AiAudioContent extends AiContent {
    private final String url;
    private final String mimeType;
    private final String base64Data;

    public AiAudioContent(String url, String mimeType, String base64Data) {
        super(AiContentType.AUDIO);
        this.url = url;
        this.mimeType = mimeType;
        this.base64Data = base64Data;
    }

    public String getUrl() { return url; }
    public String getMimeType() { return mimeType; }
    public String getBase64Data() { return base64Data; }
    public boolean isUrlMode() { return url != null && !url.isEmpty(); }
}
