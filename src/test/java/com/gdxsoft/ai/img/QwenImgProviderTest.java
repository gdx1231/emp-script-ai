package com.gdxsoft.ai.img;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.img.providers.qwen.QwenImgProvider;

/**
 * Test Qwen-image-3.0 multimodal-generation provider.
 *
 * <p>Usage:
 * <pre>
 * export DASHSCOPE_API_KEY=sk-xxxxxxxx
 *
 * # Run all tests
 * mvn test -pl . -Dtest=QwenImgProviderTest
 *
 * # Run only unit tests (no API key needed)
 * mvn test -pl . -Dtest="QwenImgProviderTest#buildT2iBody+buildI2iBody"
 * </pre>
 *
 * @since 1.2.0
 */
class QwenImgProviderTest {

    private static final String ENV_KEY = "DASHSCOPE_API_KEY";

    // ==================== Unit tests ====================

    @Test
    @DisplayName("T2I request body")
    void buildT2iBody() {
        QwenImgProvider provider = new QwenImgProvider();

        ImgOptions opts = new ImgOptions("一只可爱的橘猫坐在窗台上")
                .model("qwen-image-3.0-pro")
                .size("1024x1024")
                .n(1)
                .promptExtend(true)
                .negativePrompt("模糊, 低质量")
                .seed(42L);

        JSONObject body = provider.buildRequestBody(opts);

        assertEquals("qwen-image-3.0-pro", body.getString("model"));

        // input.messages
        JSONArray messages = body.getJSONObject("input").getJSONArray("messages");
        assertEquals(1, messages.length());
        JSONObject userMsg = messages.getJSONObject(0);
        assertEquals("user", userMsg.getString("role"));

        JSONArray content = userMsg.getJSONArray("content");
        assertEquals(1, content.length(), "T2I = 1 content item (text only)");
        assertEquals("一只可爱的橘猫坐在窗台上", content.getJSONObject(0).getString("text"));

        JSONObject params = body.getJSONObject("parameters");
        assertEquals("1024*1024", params.getString("size"));
        assertTrue(params.getBoolean("prompt_extend"));
        assertEquals("模糊, 低质量", params.getString("negative_prompt"));
        assertEquals(42L, params.getLong("seed"));

        System.out.println("=== T2I Body ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("I2I request body with multiple ref images")
    void buildI2iBody() {
        QwenImgProvider provider = new QwenImgProvider();

        ImgOptions opts = new ImgOptions("将背景换成海滩")
                .model("qwen-image-3.0-pro")
                .size("1024*1024")
                .n(1)
                .refImageUrls(Arrays.asList(
                        "https://example.com/ref1.png",
                        "https://example.com/ref2.png"))
                .promptExtendMode("direct")
                .watermark(false);

        JSONObject body = provider.buildRequestBody(opts);

        JSONArray messages = body.getJSONObject("input").getJSONArray("messages");
        JSONArray content = messages.getJSONObject(0).getJSONArray("content");
        assertEquals(3, content.length(), "2 refs + 1 text = 3 items");

        assertEquals("https://example.com/ref1.png", content.getJSONObject(0).getString("image"));
        assertEquals("https://example.com/ref2.png", content.getJSONObject(1).getString("image"));
        assertEquals("将背景换成海滩", content.getJSONObject(2).getString("text"));

        JSONObject params = body.getJSONObject("parameters");
        assertEquals("direct", params.getString("prompt_extend_mode"));
        assertFalse(params.getBoolean("watermark"));

        System.out.println("=== I2I Body ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("I2I with single refImageUrl fallback")
    void buildI2iSingleRefFallback() {
        QwenImgProvider provider = new QwenImgProvider();

        ImgOptions opts = new ImgOptions("改变风格为水彩画")
                .model("qwen-image-3.0")
                .refImageUrl("https://example.com/single.jpg");

        JSONObject body = provider.buildRequestBody(opts);

        JSONArray messages = body.getJSONObject("input").getJSONArray("messages");
        JSONArray content = messages.getJSONObject(0).getJSONArray("content");
        assertEquals(2, content.length(), "1 ref + 1 text = 2 items");
        assertEquals("https://example.com/single.jpg", content.getJSONObject(0).getString("image"));
        assertEquals("改变风格为水彩画", content.getJSONObject(1).getString("text"));
    }

    @Test
    @DisplayName("I2I with base64 image")
    void buildI2iBase64() {
        QwenImgProvider provider = new QwenImgProvider();

        String b64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

        ImgOptions opts = new ImgOptions("增强画质")
                .model("qwen-image-3.0-pro")
                .addRefImageUrl(b64);

        JSONObject body = provider.buildRequestBody(opts);

        JSONArray content = body.getJSONObject("input").getJSONArray("messages")
                .getJSONObject(0).getJSONArray("content");
        assertEquals(2, content.length());
        assertEquals(b64, content.getJSONObject(0).getString("image"));
        assertEquals("增强画质", content.getJSONObject(1).getString("text"));
    }

    @Test
    @DisplayName("T2I with agent prompt_extend_mode")
    void buildT2iAgentMode() {
        QwenImgProvider provider = new QwenImgProvider();

        ImgOptions opts = new ImgOptions("夕阳下的城市天际线")
                .model("qwen-image-3.0-pro")
                .promptExtend(true)
                .promptExtendMode("agent");

        JSONObject body = provider.buildRequestBody(opts);

        JSONObject params = body.getJSONObject("parameters");
        assertTrue(params.getBoolean("prompt_extend"));
        assertEquals("agent", params.getString("prompt_extend_mode"));

        JSONArray content = body.getJSONObject("input").getJSONArray("messages")
                .getJSONObject(0).getJSONArray("content");
        assertEquals(1, content.length(), "agent mode = T2I = text only");

        System.out.println("=== Agent Mode ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("Parse success response")
    void parseSuccessResponse() {
        QwenImgProvider provider = new QwenImgProvider();

        JSONObject mockResp = new JSONObject();
        mockResp.put("request_id", "req-001");

        JSONObject output = new JSONObject();
        JSONArray choices = new JSONArray();

        JSONObject choice1 = new JSONObject();
        choice1.put("finish_reason", "stop");
        JSONObject msg1 = new JSONObject();
        msg1.put("role", "assistant");
        JSONArray c1 = new JSONArray();
        c1.put(new JSONObject().put("image", "https://example.com/img1.png"));
        msg1.put("content", c1);
        choice1.put("message", msg1);
        choices.put(choice1);

        output.put("choices", choices);
        mockResp.put("output", output);

        JSONObject usage = new JSONObject();
        usage.put("output_height", 1024);
        usage.put("output_width", 1024);
        usage.put("input_image_count", 0);
        usage.put("output_image_count", 1);
        usage.put("output_image_type", "qima_output_1k");
        mockResp.put("usage", usage);

        ImgResponse resp = provider.parseResponse(mockResp,
                new ImgOptions("test").model("qwen-image-3.0-pro"));

        assertEquals(1, resp.getImages().size());
        assertEquals("https://example.com/img1.png", resp.getFirstImage().getUrl());
        assertEquals("qwen-image-3.0-pro", resp.getModel());

        JSONObject u = resp.getUsage();
        assertNotNull(u);
        assertEquals(1024, u.getInt("output_height"));
        assertEquals(1024, u.getInt("output_width"));
        assertEquals(0, u.getInt("input_image_count"));
        assertEquals("qima_output_1k", u.getString("output_image_type"));
    }

    @Test
    @DisplayName("Parse error response")
    void parseErrorResponse() {
        QwenImgProvider provider = new QwenImgProvider();

        JSONObject err = new JSONObject();
        err.put("request_id", "req-err");
        err.put("code", "InvalidApiKey");
        err.put("message", "Invalid API-key provided.");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                provider.parseResponse(err, new ImgOptions("test")));
        assertTrue(ex.getMessage().contains("InvalidApiKey"));
        assertTrue(ex.getMessage().contains("Invalid API-key provided."));
    }

    @Test
    @DisplayName("Default model is qwen-image-3.0-pro")
    void defaultModel() {
        assertEquals("qwen-image-3.0-pro", QwenImgProvider.DEFAULT_MODEL);
    }

    // ==================== Integration tests ====================

    @Test
    @Tag("integration")
    @DisplayName("T2I: generate single image")
    void t2iGenerate() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一只可爱的橘猫坐在窗台上，阳光洒在它身上")
                        .model("qwen-image-3.0-pro")
                        .size("1024*1024")
                        .n(1));

        System.out.println(provider.curl(req));

        ImgResponse resp = provider.generate(req);
        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());
        assertNotNull(resp.getFirstImage().getUrl());

        System.out.println("=== Generated ===");
        System.out.println("URL: " + resp.getFirstImage().getUrl());
        if (resp.getUsage() != null) {
            System.out.println("Usage: " + resp.getUsage().toString(2));
        }
    }

    @Test
    @Tag("integration")
    @DisplayName("T2I: with negative prompt and seed")
    void t2iWithParams() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一幅美丽的山水画，青山绿水，云雾缭绕")
                        .model("qwen-image-3.0-pro")
                        .size("1024*1024")
                        .n(1)
                        .negativePrompt("模糊, 低质量, 变形, 文字, 水印")
                        .seed(123L)
                        .promptExtend(true));

        ImgResponse resp = provider.generate(req);
        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());
        System.out.println("=== With Params ===");
        System.out.println("URL: " + resp.getFirstImage().getUrl());
    }

    @Test
    @Tag("integration")
    @DisplayName("T2I: with agent prompt_extend_mode")
    void t2iAgentMode() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("夕阳下的城市天际线")
                        .model("qwen-image-3.0-pro")
                        .size("1024*1024")
                        .n(1)
                        .promptExtend(true)
                        .promptExtendMode("agent"));

        ImgResponse resp = provider.generate(req);
        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());
        System.out.println("=== Agent Mode ===");
        System.out.println("URL: " + resp.getFirstImage().getUrl());
    }

    @Test
    @Tag("integration")
    @DisplayName("I2I: edit with reference image")
    void i2iGenerate() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        String refUrl = "https://alidocs.oss-cn-zhangjiakou.aliyuncs.com/res/yBRq1ZPYEaXdyOdv/img/33a80a19-7ac7-4c64-b0fa-7d685b7046a0.png";

        ImgRequest req = new ImgRequest(
                new ImgOptions("帮我生成一张充满高级感的都市风格女性写真，保留输入图片中人物的面部特征")
                        .model("qwen-image-3.0-pro")
                        .size("1024*1024")
                        .n(1)
                        .addRefImageUrl(refUrl)
                        .promptExtend(true));

        ImgResponse resp = provider.generate(req);
        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());
        System.out.println("=== I2I Generated ===");
        System.out.println("URL: " + resp.getFirstImage().getUrl());
        if (resp.getUsage() != null) {
            System.out.println("Usage: " + resp.getUsage().toString(2));
        }
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate multiple images")
    void generateMultiple() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一只小狗在草地上玩耍")
                        .model("qwen-image-3.0")
                        .size("1024*1024")
                        .n(2));

        ImgResponse resp = provider.generate(req);
        assertNotNull(resp);
        assertEquals(2, resp.getImages().size());

        for (int i = 0; i < resp.getImages().size(); i++) {
            System.out.println("Image " + (i + 1) + ": " +
                    resp.getImages().get(i).getUrl());
        }
    }

    @Test
    @DisplayName("prompt_extend defaults to true when not set")
    void promptExtendDefaultsTrue() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test prompt")
                .model("qwen-image-3.0-pro");

        JSONObject body = provider.buildRequestBody(opts);
        assertTrue(body.getJSONObject("parameters").getBoolean("prompt_extend"),
                "prompt_extend should default to true");
    }

    @Test
    @DisplayName("prompt_extend can be explicitly disabled")
    void promptExtendExplicitFalse() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test prompt")
                .model("qwen-image-3.0-pro")
                .promptExtend(false);

        JSONObject body = provider.buildRequestBody(opts);
        assertFalse(body.getJSONObject("parameters").getBoolean("prompt_extend"),
                "prompt_extend should be false when explicitly set");
    }

    // ==================== Validation tests ====================

    @Test
    @DisplayName("Reject negative seed")
    void rejectNegativeSeed() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test")
                .model("qwen-image-3.0-pro")
                .seed(-1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(opts));
        assertTrue(ex.getMessage().contains("seed"));
        assertTrue(ex.getMessage().contains("[0, 2147483647]"));
    }

    @Test
    @DisplayName("Reject seed > 2147483647")
    void rejectOversizedSeed() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test")
                .model("qwen-image-3.0-pro")
                .seed(3000000000L);

        assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(opts));
    }

    @Test
    @DisplayName("Accept seed = 0 (valid boundary)")
    void acceptZeroSeed() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test")
                .model("qwen-image-3.0-pro")
                .seed(0L);

        JSONObject body = provider.buildRequestBody(opts);
        assertEquals(0L, body.getJSONObject("parameters").getLong("seed"));
    }

    @Test
    @DisplayName("Reject invalid size format")
    void rejectInvalidSize() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test")
                .model("qwen-image-3.0-pro")
                .size("not-a-size");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(opts));
        assertTrue(ex.getMessage().contains("size"));
        assertTrue(ex.getMessage().contains("<width>*<height>"));
    }

    @Test
    @DisplayName("Skip empty size (not sent to API)")
    void skipEmptySize() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test")
                .model("qwen-image-3.0-pro")
                .size("");

        JSONObject body = provider.buildRequestBody(opts);
        assertFalse(body.getJSONObject("parameters").has("size"),
                "empty size should not be sent to API");
    }

    @Test
    @DisplayName("Accept size with uppercase X")
    void acceptUppercaseXSize() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test")
                .model("qwen-image-3.0-pro")
                .size("1024X768");

        JSONObject body = provider.buildRequestBody(opts);
        assertEquals("1024*768", body.getJSONObject("parameters").getString("size"));
    }

    @Test
    @DisplayName("Accept size with whitespace (trimmed)")
    void acceptTrimmedSize() {
        QwenImgProvider provider = new QwenImgProvider();
        ImgOptions opts = new ImgOptions("test")
                .model("qwen-image-3.0-pro")
                .size(" 1024*1024 ");

        JSONObject body = provider.buildRequestBody(opts);
        assertEquals("1024*1024", body.getJSONObject("parameters").getString("size"));
    }

    // ==================== Helper ====================

    private static String requireApiKey() {
        String key = System.getenv(ENV_KEY);
        assumeTrue(key != null && !key.isBlank(),
                "DASHSCOPE_API_KEY not set — skipping integration test");
        return key;
    }
}
