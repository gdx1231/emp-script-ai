package com.gdxsoft.ai.img;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.img.providers.qwen.QwenImgWanxProvider;

/**
 * Test Qwen Wanx 2.7 image provider.
 *
 * <p>Unit tests cover request body shape, response parsing, mode/region configuration.
 * Integration tests (Tag "integration") require {@code DASHSCOPE_API_KEY} env var.
 *
 * <p>Usage:
 * <pre>
 * # Unit tests only
 * mvn test -pl . -Dtest="QwenImgWanxProviderTest"
 *
 * # Include integration tests (needs valid API key)
 * export DASHSCOPE_API_KEY=sk-xxxxxxxx
 * mvn test -pl . -Dtest="QwenImgWanxProviderTest" -Dgroups=integration
 * </pre>
 *
 * @since 1.2.0
 */
class QwenImgWanxProviderTest {

    private static final String ENV_KEY = "DASHSCOPE_API_KEY";

    // ==================== Constants ====================

    @Test
    @DisplayName("Default model is wan2.7-image-pro")
    void defaultModel() {
        assertEquals("wan2.7-image-pro", QwenImgWanxProvider.DEFAULT_MODEL);
    }

    @Test
    @DisplayName("Provider type is WANX")
    void providerType() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        assertEquals(ImgProviderType.WANX, provider.getProviderType());
    }

    @Test
    @DisplayName("Default mode is sync")
    void defaultSyncMode() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        assertTrue(provider.isSyncMode());
    }

    // ==================== URL resolution ====================

    @Test
    @DisplayName("Default URL uses legacy dashscope host (no workspace)")
    void defaultUrlNoWorkspace() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        String curl = provider.curl(new ImgRequest(new ImgOptions("a")));
        // 同步模式默认应指向 multimodal-generation/generation
        assertTrue(curl.contains("/api/v1/services/aigc/multimodal-generation/generation"));
        assertFalse(curl.contains("X-DashScope-Async"));
    }

    @Test
    @DisplayName("Workspace URL for cn-beijing region")
    void workspaceUrlBeijing() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        provider.setWorkspaceId("my-ws-12345");
        provider.setRegion("cn-beijing");
        String curl = provider.curl(new ImgRequest(new ImgOptions("a")));
        assertTrue(curl.contains("https://my-ws-12345.cn-beijing.maas.aliyuncs.com"),
                "Workspace URL should use dedicated domain, got: " + curl);
    }

    @Test
    @DisplayName("Workspace URL for ap-southeast-1 region uses intl host")
    void workspaceUrlSingapore() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        provider.setWorkspaceId("my-ws-12345");
        provider.setRegion("ap-southeast-1");
        // 当未设 workspaceId 时 fallback 到 intl host
        provider.setWorkspaceId(null);
        String curl = provider.curl(new ImgRequest(new ImgOptions("a")));
        assertTrue(curl.contains("dashscope-intl.aliyuncs.com"),
                "Singapore region should use intl host, got: " + curl);
    }

    @Test
    @DisplayName("Async mode curl includes X-DashScope-Async header")
    void asyncModeCurlHeader() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        provider.setSyncMode(false);
        String curl = provider.curl(new ImgRequest(new ImgOptions("a")));
        assertTrue(curl.contains("/api/v1/services/aigc/image-generation/generation"),
                "Async should target image-generation/generation, got: " + curl);
        assertTrue(curl.contains("X-DashScope-Async: enable"),
                "Async should include X-DashScope-Async: enable header, got: " + curl);
    }

    // ==================== Request body: T2I ====================

    @Test
    @DisplayName("T2I body: messages with text only")
    void buildT2IBody() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        ImgOptions opts = new ImgOptions("一间有着精致窗户的花店")
                .model("wan2.7-image-pro")
                .size("2K")
                .n(1)
                .thinkingMode(true);
        JSONObject body = provider.buildRequestBody(opts);

        assertEquals("wan2.7-image-pro", body.getString("model"));

        JSONArray messages = body.getJSONObject("input").getJSONArray("messages");
        assertEquals(1, messages.length());
        JSONObject userMsg = messages.getJSONObject(0);
        assertEquals("user", userMsg.getString("role"));

        JSONArray content = userMsg.getJSONArray("content");
        assertEquals(1, content.length(), "T2I should have 1 text content");
        assertEquals("一间有着精致窗户的花店", content.getJSONObject(0).getString("text"));

        JSONObject params = body.getJSONObject("parameters");
        assertEquals("2K", params.getString("size"));
        assertEquals(1, params.getInt("n"));
        assertTrue(params.getBoolean("thinking_mode"));
        assertFalse(params.getBoolean("watermark")); // default
    }

    @Test
    @DisplayName("Size '1K' / '4K' pass through, '1024x1024' normalizes to '1024*1024'")
    void sizeNormalization() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();

        JSONObject body1 = provider.buildRequestBody(
                new ImgOptions("a").size("1K"));
        assertEquals("1K", body1.getJSONObject("parameters").getString("size"));

        JSONObject body4 = provider.buildRequestBody(
                new ImgOptions("a").size("4K").model("wan2.7-image-pro"));
        assertEquals("4K", body4.getJSONObject("parameters").getString("size"));

        JSONObject bodyPx = provider.buildRequestBody(
                new ImgOptions("a").size("1024x1024"));
        assertEquals("1024*1024", bodyPx.getJSONObject("parameters").getString("size"));
    }

    @Test
    @DisplayName("Invalid size throws IllegalArgumentException")
    void invalidSizeThrows() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(new ImgOptions("a").size("not-a-size")));
    }

    // ==================== Request body: I2I / image editing ====================

    @Test
    @DisplayName("I2I body: multi-image references (up to 9)")
    void buildI2IBody() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        ImgOptions opts = new ImgOptions("把图2的涂鸦喷绘在图1的汽车上")
                .model("wan2.7-image-pro")
                .size("2K")
                .n(1)
                .watermark(false)
                .refImageUrls(Arrays.asList(
                        "https://example.com/car.webp",
                        "https://example.com/paint.webp"));
        JSONObject body = provider.buildRequestBody(opts);

        JSONArray content = body.getJSONObject("input")
                .getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content");
        assertEquals(3, content.length());
        assertEquals("https://example.com/car.webp", content.getJSONObject(0).getString("image"));
        assertEquals("https://example.com/paint.webp", content.getJSONObject(1).getString("image"));
        assertEquals("把图2的涂鸦喷绘在图1的汽车上", content.getJSONObject(2).getString("text"));
        assertFalse(body.getJSONObject("parameters").getBoolean("watermark"));
    }

    @Test
    @DisplayName("Ref images truncated to 9 (wanx 2.7 limit)")
    void refImagesTruncatedTo9() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            urls.add("https://example.com/img" + i + ".png");
        }
        JSONObject body = provider.buildRequestBody(
                new ImgOptions("prompt").refImageUrls(urls));

        JSONArray content = body.getJSONObject("input")
                .getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content");
        // 9 张图 + 1 文本 = 10
        assertEquals(10, content.length());
    }

    @Test
    @DisplayName("Interactive editing: bbox_list included in parameters")
    void buildInteractiveEditingBody() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        List<List<List<Integer>>> bboxList = Arrays.asList(
                Arrays.asList(Arrays.asList(0, 0, 12, 12),
                              Arrays.asList(25, 25, 100, 100)),
                new ArrayList<>()); // 图 2 无框选

        ImgOptions opts = new ImgOptions("把图1的闹钟放在图2的框选位置")
                .model("wan2.7-image-pro")
                .refImageUrls(Arrays.asList(
                        "https://example.com/clock.png",
                        "https://example.com/scene.png"))
                .bboxList(bboxList)
                .size("2K");
        JSONObject body = provider.buildRequestBody(opts);

        JSONArray bboxOut = body.getJSONObject("parameters").getJSONArray("bbox_list");
        assertEquals(2, bboxOut.length());
        // 图 1: 2 个框
        JSONArray img1 = bboxOut.getJSONArray(0);
        assertEquals(2, img1.length());
        assertEquals(0, img1.getJSONArray(0).getInt(0));
        // 图 2: 0 个框
        assertEquals(0, bboxOut.getJSONArray(1).length());
    }

    // ==================== Request body: 组图 ====================

    @Test
    @DisplayName("Sequential mode: enable_sequential=true, n up to 12")
    void buildSequentialBody() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        ImgOptions opts = new ImgOptions("电影感组图，记录同一只流浪橘猫，特征必须前后一致。第一张：春天…")
                .model("wan2.7-image-pro")
                .enableSequential(true)
                .n(4)
                .size("2K");
        JSONObject body = provider.buildRequestBody(opts);

        JSONObject params = body.getJSONObject("parameters");
        assertTrue(params.getBoolean("enable_sequential"));
        assertEquals(4, params.getInt("n"));
        assertEquals("2K", params.getString("size"));
    }

    @Test
    @DisplayName("Color palette: list of {hex, ratio} objects in parameters")
    void buildColorPaletteBody() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        List<JSONObject> palette = new ArrayList<>();
        palette.add(new JSONObject().put("hex", "#C2D1E6").put("ratio", "50.00%"));
        palette.add(new JSONObject().put("hex", "#636574").put("ratio", "50.00%"));

        ImgOptions opts = new ImgOptions("冷色调风景")
                .model("wan2.7-image-pro")
                .colorPalette(palette)
                .size("2K");
        JSONObject body = provider.buildRequestBody(opts);

        JSONArray paletteOut = body.getJSONObject("parameters").getJSONArray("color_palette");
        assertEquals(2, paletteOut.length());
        assertEquals("#C2D1E6", paletteOut.getJSONObject(0).getString("hex"));
        assertEquals("50.00%", paletteOut.getJSONObject(0).getString("ratio"));
    }

    // ==================== Response parsing ====================

    @Test
    @DisplayName("Parse sync success response: choices[].message.content[].image")
    void parseSyncResponse() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        JSONObject root = new JSONObject()
                .put("request_id", "req-123")
                .put("model", "wan2.7-image-pro")
                .put("output", new JSONObject()
                        .put("finished", true)
                        .put("choices", new JSONArray()
                                .put(new JSONObject()
                                        .put("finish_reason", "stop")
                                        .put("message", new JSONObject()
                                                .put("role", "assistant")
                                                .put("content", new JSONArray()
                                                        .put(new JSONObject()
                                                                .put("type", "image")
                                                                .put("image",
                                                                        "https://dashscope.example.com/1.png?Expires=xxx")))))))
                .put("usage", new JSONObject()
                        .put("image_count", 1)
                        .put("size", "1024*1024")
                        .put("total_tokens", 100));

        ImgResponse resp = provider.parseResponse(root, new ImgOptions("test"));

        assertEquals(1, resp.getImages().size());
        assertEquals("https://dashscope.example.com/1.png?Expires=xxx",
                resp.getImages().get(0).getUrl());
        assertEquals("wan2.7-image-pro", resp.getModel());
        assertEquals(1, resp.getUsage().getInt("image_count"));
    }

    @Test
    @DisplayName("Parse async PENDING: returns empty image list (not an error)")
    void parseAsyncPending() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        JSONObject root = new JSONObject()
                .put("output", new JSONObject()
                        .put("task_id", "abc")
                        .put("task_status", "PENDING"));
        ImgResponse resp = provider.parseResponse(root, new ImgOptions("test"));
        assertEquals(0, resp.getImages().size());
    }

    @Test
    @DisplayName("Parse async FAILED: throws RuntimeException with status + code")
    void parseAsyncFailed() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        JSONObject root = new JSONObject()
                .put("output", new JSONObject()
                        .put("task_id", "abc")
                        .put("task_status", "FAILED")
                        .put("code", "InvalidParameter")
                        .put("message", "bad prompt"));
        Exception ex = assertThrows(RuntimeException.class,
                () -> provider.parseResponse(root, new ImgOptions("test")));
        assertTrue(ex.getMessage().contains("FAILED"));
        assertTrue(ex.getMessage().contains("InvalidParameter"));
    }

    @Test
    @DisplayName("Parse root-level error: throws RuntimeException with code+message")
    void parseRootError() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        JSONObject root = new JSONObject()
                .put("code", "InvalidApiKey")
                .put("message", "No API-key provided.");
        Exception ex = assertThrows(RuntimeException.class,
                () -> provider.parseResponse(root, new ImgOptions("test")));
        assertTrue(ex.getMessage().contains("InvalidApiKey"));
    }

    // ==================== Seed validation ====================

    @Test
    @DisplayName("Seed validated: 0..2147483647, out of range throws")
    void seedValidation() {
        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        assertDoesNotThrow(() -> provider.buildRequestBody(
                new ImgOptions("a").seed(0L)));
        assertDoesNotThrow(() -> provider.buildRequestBody(
                new ImgOptions("a").seed(2147483647L)));
        assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(new ImgOptions("a").seed(-1L)));
        assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(new ImgOptions("a").seed(2147483648L)));
    }

    // ==================== Integration (require DASHSCOPE_API_KEY) ====================

    private static String requireApiKey() {
        String key = System.getenv(ENV_KEY);
        org.junit.jupiter.api.Assumptions.assumeTrue(key != null && !key.isEmpty(),
                "Skip integration test: " + ENV_KEY + " not set");
        return key;
    }

    @Test
    @Tag("integration")
    @DisplayName("Sync T2I: wan2.7-image-pro with size 2K")
    void syncT2IIntegration() throws Exception {
        String apiKey = requireApiKey();

        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        provider.setApiKey(apiKey);
        provider.setSyncMode(true);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一间有着精致窗户的花店，漂亮的木质门，摆放着花朵")
                        .model("wan2.7-image-pro")
                        .size("2K")
                        .n(1)
                        .watermark(false));

        System.out.println("=== Sync T2I request ===");
        System.out.println(provider.curl(req));

        ImgResponse resp = provider.generate(req);
        assertNotNull(resp);
        System.out.println("=== Sync T2I response ===");
        System.out.println("images=" + resp.getImages().size()
                + ", model=" + resp.getModel());
        if (resp.getUsage() != null) {
            System.out.println("usage=" + resp.getUsage());
        }
        assertTrue(resp.getImages().size() >= 1, "Expected >= 1 image");
    }

    @Test
    @Tag("integration")
    @DisplayName("Async T2I: syncMode=false submits task and polls")
    void asyncT2IIntegration() throws Exception {
        String apiKey = requireApiKey();

        QwenImgWanxProvider provider = new QwenImgWanxProvider();
        provider.setApiKey(apiKey);
        provider.setSyncMode(false);

        ImgRequest req = new ImgRequest(
                new ImgOptions("夕阳下的城市天际线")
                        .model("wan2.7-image-pro")
                        .size("2K")
                        .n(1));

        System.out.println("=== Async T2I request ===");
        System.out.println(provider.curl(req));

        ImgResponse resp = provider.generate(req);
        assertNotNull(resp);
        assertTrue(resp.getImages().size() >= 1, "Async should eventually return >= 1 image");
    }
}
