package com.gdxsoft.ai.providers.qwen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Qwen Files API 客户端解析与地址推导测试。
 */
class QwenFileClientTest {

    @Test
    void resolvesFilesUrlFromChatCompletionsUrl() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/files",
                QwenFileClient.resolveFilesUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"));
        assertEquals("https://api.example.com/openai/v1/files",
                QwenFileClient.resolveFilesUrl("https://api.example.com/openai/v1"));
        assertEquals("https://api.example.com/openai/v1/files",
                QwenFileClient.resolveFilesUrl("https://api.example.com/openai/v1/files/"));
    }

    @Test
    void parsesUploadResponse() {
        QwenFileClient.QwenFileUpload upload = QwenFileClient.parse(
                "{\"id\":\"file-fe-123\",\"filename\":\"a.pdf\",\"purpose\":\"file-extract\",\"bytes\":8}");

        assertEquals("file-fe-123", upload.id());
        assertEquals("a.pdf", upload.filename());
        assertEquals("file-extract", upload.purpose());
        assertEquals(8, upload.bytes());
        assertEquals("fileid://file-fe-123", QwenFileClient.toFileId(upload.id()));
        assertTrue(QwenFileClient.isFileId("FILEID://file-fe-123"));
    }

    @Test
    void parseRejectsMissingFileId() {
        assertThrows(IllegalArgumentException.class, () -> QwenFileClient.parse("{}"));
    }

}
