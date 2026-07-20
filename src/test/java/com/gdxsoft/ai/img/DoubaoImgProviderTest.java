package com.gdxsoft.ai.img;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.img.providers.doubao.DoubaoImgProvider;

/**
 * Test Doubao (豆包 / 火山引擎 Ark) image generation.
 * <p>
 * Uses {@code DOUBAO_API_KEY} (火山引擎 Ark API Key) from environment.
 *
 * <p>Usage:
 * <pre>
 * export DOUBAO_API_KEY=your-volcengine-ark-key
 *
 * # Run all tests (unit + integration)
 * mvn test -pl . -Dtest=DoubaoImgProviderTest
 *
 * # Run only unit tests (no API key needed)
 * mvn test -pl . -Dtest="DoubaoImgProviderTest#buildRequestBodyShape+parseResponseShape"
 * </pre>
 *
 * @since 1.2.0
 */
class DoubaoImgProviderTest {

    private static final String ENV_KEY = "DOUBAO_API_KEY";

    // ==================== Unit tests (no API key needed) ====================

    @Test
    @DisplayName("Request body includes Doubao-specific fields")
    void buildRequestBodyShape() {
        DoubaoImgProvider provider = new DoubaoImgProvider();

        ImgOptions opts = new ImgOptions("一只柴犬在草地上奔跑")
                .model("doubao-seedream-5-0-260128")
                .size("2K")
                .n(1)
                .responseFormat("url")
                .user("test-user-123");

        org.json.JSONObject body = provider.buildRequestBody(opts);

        // Standard fields
        assertEquals("doubao-seedream-5-0-260128", body.getString("model"));
        assertEquals("一只柴犬在草地上奔跑", body.getString("prompt"));
        assertEquals(1, body.getInt("n"));
        assertEquals("2K", body.getString("size"));
        assertEquals("url", body.getString("response_format"));
        assertEquals("test-user-123", body.getString("user"));

        // Doubao-specific fields
        assertEquals("disabled", body.getString("sequential_image_generation"),
                "default should be disabled");
        assertTrue(body.getBoolean("watermark"),
                "default watermark should be true");
        assertFalse(body.getBoolean("stream"),
                "default stream should be false");

        System.out.println("=== Request Body ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("Sequential mode + no watermark")
    void buildRequestBodyWithCustomSettings() {
        DoubaoImgProvider provider = new DoubaoImgProvider();
        provider.setSequentialImageGeneration("auto");
        provider.setWatermark(false);

        ImgOptions opts = new ImgOptions("test");
        org.json.JSONObject body = provider.buildRequestBody(opts);

        assertEquals("auto", body.getString("sequential_image_generation"));
        assertFalse(body.getBoolean("watermark"));

        System.out.println("=== Custom Settings ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("Parse OpenAI-shaped response")
    void parseResponseShape() {
        DoubaoImgProvider provider = new DoubaoImgProvider();

        org.json.JSONObject mockResp = new org.json.JSONObject();
        mockResp.put("created", 1752777600L);
        mockResp.put("model", "doubao-seedream-4.0-250828");

        org.json.JSONArray data = new org.json.JSONArray();
        org.json.JSONObject img1 = new org.json.JSONObject();
        img1.put("url", "https://ark-result.volces.com/img/a1.png");
        img1.put("size", "1440x2560");
        data.put(img1);

        org.json.JSONObject img2 = new org.json.JSONObject();
        img2.put("url", "https://ark-result.volces.com/img/a2.png");
        data.put(img2);

        mockResp.put("data", data);

        ImgResponse resp = provider.parseResponse(mockResp);

        assertNotNull(resp);
        assertEquals(2, resp.getImages().size());
        assertEquals("https://ark-result.volces.com/img/a1.png",
                resp.getImages().get(0).getUrl());
        assertEquals("https://ark-result.volces.com/img/a2.png",
                resp.getImages().get(1).getUrl());
        assertEquals(Long.valueOf(1752777600L), resp.getCreated());
        assertEquals("doubao-seedream-4.0-250828", resp.getModel());
        assertTrue(resp.getImages().get(0).isUrl());
        assertFalse(resp.getImages().get(0).isBase64());
    }

    @Test
    @DisplayName("Stream mode in request body")
    void buildRequestBodyWithStream() {
        DoubaoImgProvider provider = new DoubaoImgProvider();
        provider.setStream(true);

        ImgOptions opts = new ImgOptions("test");
        org.json.JSONObject body = provider.buildRequestBody(opts);

        assertTrue(body.getBoolean("stream"),
                "stream should be true when enabled");

        System.out.println("=== Stream Mode ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("Parse base64 response")
    void parseBase64Response() {
        DoubaoImgProvider provider = new DoubaoImgProvider();

        org.json.JSONObject mockResp = new org.json.JSONObject();
        mockResp.put("created", 1752777600L);

        org.json.JSONArray data = new org.json.JSONArray();
        org.json.JSONObject img = new org.json.JSONObject();
        img.put("b64_json", "aW1hZ2UgZGF0YQ==");
        data.put(img);

        mockResp.put("data", data);

        ImgResponse resp = provider.parseResponse(mockResp);

        assertEquals(1, resp.getImages().size());
        assertTrue(resp.getImages().get(0).isBase64());
        assertEquals("aW1hZ2UgZGF0YQ==", resp.getImages().get(0).getB64Json());
    }

    // ==================== Integration tests (need API key) ====================

    @Test
    @Tag("integration")
    @DisplayName("Generate single image")
    void generateSingleImage() throws Exception {
        String apiKey = requireApiKey();

        DoubaoImgProvider provider = new DoubaoImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一只可爱的柯基犬在海滩上奔跑，夕阳背景")
                        .model("doubao-seedream-5-0-260128")
                        .size("2K")
                        .n(1));

        System.out.println(provider.curl(req));

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp, "response should not be null");
        assertFalse(resp.getImages().isEmpty(), "should have at least one image");

        ImgResponse.GeneratedImage img = resp.getFirstImage();
        assertNotNull(img, "first image should not be null");
        assertNotNull(img.getUrl(), "image URL should not be null");

        System.out.println("=== Generated Image ===");
        System.out.println("URL: " + img.getUrl());
        System.out.println("Model: " + resp.getModel());
        System.out.println("Created: " + resp.getCreated());
        System.out.println("=== Done ===");
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate without watermark")
    void generateWithoutWatermark() throws Exception {
        String apiKey = requireApiKey();

        DoubaoImgProvider provider = new DoubaoImgProvider();
        provider.setApiKey(apiKey);
        provider.setWatermark(false);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一幅中国水墨画，山水之间有一座小桥")
                        .model("doubao-seedream-5-0-260128")
                        .size("2K"));

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());
        System.out.println("=== No Watermark ===");
        System.out.println("URL: " + resp.getFirstImage().getUrl());
        System.out.println("=== Done ===");
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate multiple images with sequential mode")
    void generateSequentialMultiple() throws Exception {
        String apiKey = requireApiKey();

        DoubaoImgProvider provider = new DoubaoImgProvider();
        provider.setApiKey(apiKey);
        provider.setSequentialImageGeneration("auto");

        ImgRequest req = new ImgRequest(
                new ImgOptions("一组不同季节的同一棵树的插画")
                        .model("doubao-seedream-5-0-260128")
                        .size("2K")
                        .n(4));

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp);
        assertEquals(4, resp.getImages().size(),
                "should generate exactly 4 images");

        for (int i = 0; i < resp.getImages().size(); i++) {
            System.out.println("Image " + (i + 1) + ": " +
                    resp.getImages().get(i).getUrl());
        }
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate with b64_json response format")
    void generateBase64Response() throws Exception {
        String apiKey = requireApiKey();

        DoubaoImgProvider provider = new DoubaoImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一个简单的几何图形，红色圆形")
                        .model("doubao-seedream-5-0-260128")
                        .size("2K")
                        .n(1)
                        .responseFormat("b64_json"));

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());

        ImgResponse.GeneratedImage img = resp.getFirstImage();
        assertTrue(img.isBase64(), "should be base64 encoded");
        assertNotNull(img.getB64Json(), "b64_json should not be null");
        assertTrue(img.getB64Json().length() > 100,
                "base64 data should be substantial");

        System.out.println("=== Base64 Image ===");
        System.out.println("Length: " + img.getB64Json().length() + " chars");
        System.out.println("First 50 chars: " + img.getB64Json().substring(0, 50) + "...");
        System.out.println("=== Done ===");
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate with 2K preset size")
    void generate2KSize() throws Exception {
        String apiKey = requireApiKey();

        DoubaoImgProvider provider = new DoubaoImgProvider();
        provider.setApiKey(apiKey);

        ImgRequest req = new ImgRequest(
                new ImgOptions("一片宁静的湖泊，倒映着雪山")
                        .model("doubao-seedream-5-0-260128")
                        .size("2K")
                        .n(1));

        ImgResponse resp = provider.generate(req);

        assertNotNull(resp);
        assertFalse(resp.getImages().isEmpty());
        System.out.println("=== 2K Image ===");
        System.out.println("URL: " + resp.getFirstImage().getUrl());
        System.out.println("=== Done ===");
    }

    // ==================== Helper ====================

    /**
     * Get API key from env, or skip the test via assumption.
     */
    private static String requireApiKey() {
        String key = System.getenv(ENV_KEY);
        assumeTrue(key != null && !key.isBlank(),
                "DOUBAO_API_KEY not set in environment — skipping integration test");
        return key;
    }
}
