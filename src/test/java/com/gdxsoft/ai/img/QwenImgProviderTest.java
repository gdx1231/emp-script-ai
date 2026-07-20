package com.gdxsoft.ai.img;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.img.providers.qwen.QwenImgProvider;

/**
 * Test Qwen (通义万相) image generation using DASHSCOPE_API_KEY from environment.
 * <p>
 * Qwen now requires <b>async mode</b> (default) — the provider submits a task
 * and polls for completion.
 *
 * <p>Usage:
 * <pre>
 * export DASHSCOPE_API_KEY=sk-xxxxxxxx
 *
 * # Run all tests (unit + integration)
 * mvn test -pl . -Dtest=QwenImgProviderTest
 *
 * # Run only unit tests (no API key needed)
 * mvn test -pl . -Dtest="QwenImgProviderTest#buildRequestBodyShape+parseResponseShape"
 * </pre>
 *
 * @since 1.2.0
 */
class QwenImgProviderTest {

    private static final String ENV_KEY = "DASHSCOPE_API_KEY";

    // ==================== Integration tests (need API key) ====================

    @Test
    @Tag("integration")
    @DisplayName("Generate single image via async mode")
    void generateSingleImage() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);
        // asyncMode=true is default since Qwen deprecated sync API

        ImgRequest req = new ImgRequest(
                new ImgOptions("一只可爱的橘猫坐在窗台上，阳光洒在它身上")
                        .model("wanx2.1-t2i-turbo")
                        .size("1024*1024")
                        .n(1));

        System.out.println(provider.curl(req));
        System.out.println("(async mode, waiting for task completion...)");

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp, "response should not be null");
        assertFalse(resp.getImages().isEmpty(), "should have at least one image");

        ImgResponse.GeneratedImage img = resp.getFirstImage();
        assertNotNull(img, "first image should not be null");
        assertNotNull(img.getUrl(), "image URL should not be null");

        System.out.println("=== Generated Image ===");
        System.out.println("URL: " + img.getUrl());
        System.out.println("Model: " + resp.getModel());
        if (resp.getUsage() != null) {
            System.out.println("Usage: " + resp.getUsage().toString(2));
        }
        System.out.println("=== Done ===");
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate image with negative prompt")
    void generateWithNegativePrompt() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一幅美丽的山水画，青山绿水，云雾缭绕")
                        .model("wanx2.1-t2i-turbo")
                        .size("1024*1024")
                        .n(1)
                        .negativePrompt("模糊, 低质量, 变形, 文字, 水印"));

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());
        System.out.println("=== Negative Prompt Image ===");
        System.out.println("URL: " + resp.getFirstImage().getUrl());
        System.out.println("=== Done ===");
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate with seed for reproducibility")
    void generateWithSeed() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        long seed = 42L;
        ImgOptions opts = new ImgOptions("一个红色的苹果放在木桌上")
                .model("wanx2.1-t2i-turbo")
                .size("1024*1024")
                .n(1)
                .seed(seed);

        // Same seed + same prompt = reproducible result
        ImgResponse r1 = provider.generate(new ImgRequest(opts));
        ImgResponse r2 = provider.generate(new ImgRequest(opts));

        assertNotNull(r1.getFirstImage());
        assertNotNull(r2.getFirstImage());
        System.out.println("=== Seed Reproducibility ===");
        System.out.println("Image 1: " + r1.getFirstImage().getUrl());
        System.out.println("Image 2: " + r2.getFirstImage().getUrl());
        System.out.println("=== Done ===");
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate multiple images at once")
    void generateMultipleImages() throws Exception {
        String apiKey = requireApiKey();

        QwenImgProvider provider = new QwenImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一只小狗在草地上玩耍")
                        .model("wanx2.1-t2i-turbo")
                        .size("1024*1024")
                        .n(2));

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp);
        assertEquals(2, resp.getImages().size(),
                "should generate exactly 2 images");

        for (int i = 0; i < resp.getImages().size(); i++) {
            System.out.println("Image " + (i + 1) + ": " +
                    resp.getImages().get(i).getUrl());
        }
    }

    @Test
    void buildRequestBodyShape() {
        QwenImgProvider provider = new QwenImgProvider();

        ImgOptions opts = new ImgOptions("test prompt")
                .model("wanx2.1-t2i-turbo")
                .size("1024x1024")
                .n(1)
                .negativePrompt("bad")
                .seed(100L)
                .steps(50);

        org.json.JSONObject body = provider.buildRequestBody(opts);

        // Verify top-level fields
        assertEquals("wanx2.1-t2i-turbo", body.getString("model"));

        // Verify input block
        org.json.JSONObject input = body.getJSONObject("input");
        assertEquals("test prompt", input.getString("prompt"));
        assertEquals("bad", input.getString("negative_prompt"));

        // Verify parameters block
        org.json.JSONObject params = body.getJSONObject("parameters");
        assertEquals("1024*1024", params.getString("size"),
                "size should use * separator");
        assertEquals(1, params.getInt("n"));
        assertEquals(100L, params.getLong("seed"));
        assertEquals(50, params.getInt("steps"));

        System.out.println("=== Request Body Shape ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("Image-to-image with ref_image, ref_strength, ref_mode")
    void buildImageToImageBody() {
        QwenImgProvider provider = new QwenImgProvider();

        ImgOptions opts = new ImgOptions("将这只猫变成赛博朋克风格")
                .model("wanx2.1-t2i-turbo")
                .size("1024x1024")
                .n(1)
                .refImageUrl("https://example.com/cat.png")
                .refStrength(0.8)
                .refMode("repaint");

        org.json.JSONObject body = provider.buildRequestBody(opts);

        // Verify input.ref_image
        org.json.JSONObject input = body.getJSONObject("input");
        assertEquals("https://example.com/cat.png", input.getString("ref_image"));
        assertEquals("将这只猫变成赛博朋克风格", input.getString("prompt"));

        // Verify parameters.ref_strength and ref_mode
        org.json.JSONObject params = body.getJSONObject("parameters");
        assertEquals(0.8, params.getDouble("ref_strength"), 0.01);
        assertEquals("repaint", params.getString("ref_mode"));

        System.out.println("=== Image-to-Image Body ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("Image-to-image with ref_mode=refonly (style transfer)")
    void buildStyleTransferBody() {
        QwenImgProvider provider = new QwenImgProvider();

        ImgOptions opts = new ImgOptions("一幅油画风格的风景")
                .refImageUrl("https://example.com/reference.jpg")
                .refStrength(0.7)
                .refMode("refonly");

        org.json.JSONObject body = provider.buildRequestBody(opts);

        assertEquals("refonly", body.getJSONObject("parameters").getString("ref_mode"));
        assertEquals(0.7, body.getJSONObject("parameters").getDouble("ref_strength"), 0.01);

        System.out.println("=== Style Transfer Body ===");
        System.out.println(body.toString(2));
    }

    @Test
    void parseResponseShape() {
        QwenImgProvider provider = new QwenImgProvider();

        org.json.JSONObject mockResp = new org.json.JSONObject();
        mockResp.put("model", "wanx2.1-t2i-turbo");
        mockResp.put("request_id", "req-12345");

        org.json.JSONObject output = new org.json.JSONObject();
        output.put("task_status", "SUCCEEDED");

        org.json.JSONArray results = new org.json.JSONArray();
        org.json.JSONObject r1 = new org.json.JSONObject();
        r1.put("url", "https://example.com/img1.png");
        results.put(r1);

        org.json.JSONObject r2 = new org.json.JSONObject();
        r2.put("url", "https://example.com/img2.png");
        results.put(r2);

        output.put("results", results);
        mockResp.put("output", output);

        org.json.JSONObject usage = new org.json.JSONObject();
        usage.put("image_count", 2);
        mockResp.put("usage", usage);

        ImgResponse resp = provider.parseResponse(mockResp,
                new ImgOptions("test"));

        assertEquals(2, resp.getImages().size());
        assertEquals("https://example.com/img1.png",
                resp.getImages().get(0).getUrl());
        assertEquals("https://example.com/img2.png",
                resp.getImages().get(1).getUrl());
        assertEquals("wanx2.1-t2i-turbo", resp.getModel());
        assertEquals(2, resp.getUsage().getInt("image_count"));
    }

    // ==================== Helper ====================

    /**
     * Get API key from env, or skip the test via assumption.
     */
    private static String requireApiKey() {
        String key = System.getenv(ENV_KEY);
        assumeTrue(key != null && !key.isBlank(),
                "DASHSCOPE_API_KEY not set in environment — skipping integration test");
        return key;
    }
}
