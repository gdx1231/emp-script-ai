package com.gdxsoft.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 验证 mode prompt 中 SQL/API 图片 URL 内容的解析。
 */
class ChatManagerImagePromptTest {

    @Test
    void extractSingleUrl() throws Exception {
        assertEquals(List.of("https://example.com/a.png"),
                ChatManagerBase.extractImageUrls("https://example.com/a.png"));
    }

    @Test
    void extractSqlLineUrls() throws Exception {
        String content = "https://example.com/a.png\nhttps://example.com/b.jpg";
        assertEquals(List.of("https://example.com/a.png", "https://example.com/b.jpg"),
                ChatManagerBase.extractImageUrls(content));
    }

    @Test
    void extractCommaSeparatedUrls() throws Exception {
        assertEquals(List.of("https://example.com/a.png", "https://example.com/b.jpg"),
                ChatManagerBase.extractImageUrls("https://example.com/a.png,https://example.com/b.jpg"));
    }

    @Test
    void extractDataUrlWithoutSplittingBase64() throws Exception {
        String url = "data:image/png;base64,AAAA,BBBB";
        assertEquals(List.of(url), ChatManagerBase.extractImageUrls(url));
    }

    @Test
    void extractJsonUrlObjects() throws Exception {
        String content = "[{\"IMAGE_URL\":\"https://example.com/a.png\"},"
                + "{\"IMG\":\"https://example.com/b.jpg\"}]";
        assertEquals(List.of("https://example.com/a.png", "https://example.com/b.jpg"),
                ChatManagerBase.extractImageUrls(content));
    }

    @Test
    void removeDuplicateUrls() throws Exception {
        String content = "https://example.com/a.png\nhttps://example.com/a.png";
        assertEquals(List.of("https://example.com/a.png"), ChatManagerBase.extractImageUrls(content));
    }
}
