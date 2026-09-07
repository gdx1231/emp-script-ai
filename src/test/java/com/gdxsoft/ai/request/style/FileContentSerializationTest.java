package com.gdxsoft.ai.request.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.request.AiContent;
import com.gdxsoft.ai.request.AiTextContent;

/**
 * 验证文件识别内容在各协议下的请求体序列化。
 */
class FileContentSerializationTest {

    @Test
    void openAiFileBase64UsesFilePart() {
        com.gdxsoft.ai.providers.openai.RequestData rd = new com.gdxsoft.ai.providers.openai.RequestData();
        rd.addUserMultiPart(new AiTextContent("总结文件"),
                AiContent.fileBase64("application/pdf", "AAAA", "report.pdf"));

        JSONObject part = firstOpenAiPart(rd, 1);
        assertEquals("file", part.getString("type"));
        JSONObject file = part.getJSONObject("file");
        assertEquals("data:application/pdf;base64,AAAA", file.getString("file_data"));
        assertEquals("report.pdf", file.getString("filename"));
    }

    @Test
    void openAiFileUrlDerivesFilename() {
        com.gdxsoft.ai.providers.openai.RequestData rd = new com.gdxsoft.ai.providers.openai.RequestData();
        rd.addUserMultiPart(AiContent.fileUrl("https://example.com/docs/manual.pdf?version=2"));

        JSONObject file = firstOpenAiPart(rd, 0).getJSONObject("file");
        assertEquals("https://example.com/docs/manual.pdf?version=2", file.getString("file_data"));
        assertEquals("manual.pdf", file.getString("filename"));
    }

    @Test
    void anthropicDocumentUrlUsesUrlSource() {
        com.gdxsoft.ai.providers.anthropic.RequestData rd =
                new com.gdxsoft.ai.providers.anthropic.RequestData();
        rd.addUserMultiPart(AiContent.fileUrl("https://example.com/report.pdf", "财务报告.pdf"));

        JSONObject part = firstAnthropicPart(rd);
        assertEquals("document", part.getString("type"));
        assertEquals("财务报告.pdf", part.getString("title"));
        JSONObject source = part.getJSONObject("source");
        assertEquals("url", source.getString("type"));
        assertEquals("https://example.com/report.pdf", source.getString("url"));
    }

    @Test
    void anthropicDocumentBase64UsesPdfDefault() {
        com.gdxsoft.ai.providers.anthropic.RequestData rd =
                new com.gdxsoft.ai.providers.anthropic.RequestData();
        rd.addUserMultiPart(AiContent.fileBase64(null, "AAAA"));

        JSONObject source = firstAnthropicPart(rd).getJSONObject("source");
        assertEquals("base64", source.getString("type"));
        assertEquals("application/pdf", source.getString("media_type"));
        assertEquals("AAAA", source.getString("data"));
    }

    @Test
    void anthropicImageUrlUsesUrlSource() {
        com.gdxsoft.ai.providers.anthropic.RequestData rd =
                new com.gdxsoft.ai.providers.anthropic.RequestData();
        rd.addUserMultiPart(AiContent.imageUrl("https://example.com/photo.png"));

        JSONObject source = firstAnthropicPart(rd).getJSONObject("source");
        assertEquals("url", source.getString("type"));
        assertEquals("https://example.com/photo.png", source.getString("url"));
    }

    @Test
    void geminiFileUrlUsesFileData() {
        com.gdxsoft.ai.providers.gemini.RequestData rd = new com.gdxsoft.ai.providers.gemini.RequestData();
        rd.addUserMultiPart(AiContent.fileUrl("https://example.com/report.pdf"));

        JSONObject part = firstGeminiPart(rd);
        assertEquals("https://example.com/report.pdf", part.getJSONObject("fileData").getString("fileUri"));
        assertEquals("application/pdf", part.getJSONObject("fileData").getString("mimeType"));
    }

    @Test
    void openAiFileNameFromUrlHandlesNonFileNameUrl() {
        com.gdxsoft.ai.providers.openai.RequestData rd = new com.gdxsoft.ai.providers.openai.RequestData();
        assertNull(rd.fileNameFromUrl("https://example.com/"));
        assertNull(rd.fileNameFromUrl("data:application/pdf;base64,AAAA"));
    }

    @Test
    void qwenAudioBase64UsesQwenDataContract() {
        com.gdxsoft.ai.providers.qwen.RequestData rd = new com.gdxsoft.ai.providers.qwen.RequestData();
        rd.addUserMultiPart(AiContent.audioBase64("audio/mpeg", "AAAA"));

        JSONObject part = rd.build().getJSONArray("messages")
                .getJSONObject(0).getJSONArray("content").getJSONObject(0);
        assertEquals("input_audio", part.getString("type"));
        assertEquals("data:audio/mpeg;base64,AAAA", part.getJSONObject("input_audio").getString("data"));
        assertEquals("mpeg", part.getJSONObject("input_audio").getString("format"));
    }

    private JSONObject firstOpenAiPart(com.gdxsoft.ai.providers.openai.RequestData rd, int index) {
        JSONArray content = rd.build().getJSONArray("messages")
                .getJSONObject(0).getJSONArray("content");
        return content.getJSONObject(index);
    }

    private JSONObject firstAnthropicPart(com.gdxsoft.ai.providers.anthropic.RequestData rd) {
        JSONArray content = rd.build().getJSONArray("messages")
                .getJSONObject(0).getJSONArray("content");
        return content.getJSONObject(0);
    }

    private JSONObject firstGeminiPart(com.gdxsoft.ai.providers.gemini.RequestData rd) {
        return rd.build().getJSONArray("contents")
                .getJSONObject(0).getJSONArray("parts").getJSONObject(0);
    }
}
