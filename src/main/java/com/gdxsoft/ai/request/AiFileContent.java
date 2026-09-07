package com.gdxsoft.ai.request;

/**
 * AI 消息中的通用文件内容片段。
 * <p>
 * 可用于大模型识别 PDF、Word、Excel、文本等文件。文件可由 URL 提供，也可直接携带
 * Base64 数据。{@code filename} 主要用于帮助模型和上游服务商识别文件类型。
 *
 * @since 1.1.0
 */
public class AiFileContent extends AiContent {
    private final String url;
    private final String mimeType;
    private final String base64Data;
    private final String filename;

    /**
     * 创建文件内容。
     *
     * @param url        文件 URL；使用 Base64 时为 null
     * @param mimeType   MIME 类型，如 "application/pdf"；可空
     * @param base64Data Base64 文件数据；使用 URL 时为 null
     * @param filename   文件名，如 "report.pdf"；可空
     */
    public AiFileContent(String url, String mimeType, String base64Data, String filename) {
        super(AiContentType.FILE);
        this.url = trimToNull(url);
        this.mimeType = trimToNull(mimeType);
        this.base64Data = trimToNull(base64Data);
        this.filename = trimToNull(filename);
    }

    /**
     * 获取文件 URL。
     */
    public String getUrl() {
        return url;
    }

    /**
     * 获取 MIME 类型。
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * 获取 Base64 文件数据。
     */
    public String getBase64Data() {
        return base64Data;
    }

    /**
     * 获取文件名。
     */
    public String getFilename() {
        return filename;
    }

    /**
     * 是否使用 URL 方式提供文件。
     */
    public boolean isUrlMode() {
        return url != null && !url.isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
