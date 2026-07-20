package com.gdxsoft.ai.video;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.video.providers.jimeng.JimengVideoProvider;

/**
 * Test Jimeng / Seedance (即梦 / 豆包视频) via Volcengine Ark.
 * <p>
 * Uses {@code DOUBAO_API_KEY} (火山引擎 Ark API Key) from environment.
 *
 * <pre>
 * export DOUBAO_API_KEY=your-ark-key
 *
 * # Run all tests
 * mvn test -pl . -Dtest=JimengVideoProviderTest
 *
 * # Run only unit tests (no key needed)
 * mvn test -pl . -Dtest="JimengVideoProviderTest#buildTextOnlyBody+buildImageToVideoBody+buildWithAllParams+parseSuccessResponse+parseErrorResponse"
 * </pre>
 *
 * @since 1.3.0
 */
class JimengVideoProviderTest {

    private static final String ENV_KEY = "DOUBAO_API_KEY";

    // ==================== Unit tests ====================

    @Test
    @DisplayName("Text-only request body with content array")
    void buildTextOnlyBody() {
        JimengVideoProvider p = new JimengVideoProvider();

        VideoOptions opts = new VideoOptions("无人机快速穿越森林")
                .model("doubao-seedance-1-0-pro-250528")
                .duration(5);

        org.json.JSONObject body = p.buildRequestBody(opts);

        // Top-level
        assertEquals("doubao-seedance-1-0-pro-250528", body.getString("model"));

        // Content array
        org.json.JSONArray content = body.getJSONArray("content");
        assertEquals(1, content.length(), "text-only should have 1 content block");

        // Text block
        org.json.JSONObject textBlock = content.getJSONObject(0);
        assertEquals("text", textBlock.getString("type"));
        String text = textBlock.getString("text");
        assertTrue(text.contains("无人机快速穿越森林"), "should contain prompt");
        assertTrue(text.contains("--duration 5"), "should embed duration");
        assertTrue(text.contains("--camerafixed false"), "default camerafixed");
        assertTrue(text.contains("--watermark true"), "default watermark");

        System.out.println("=== Text-Only Body ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("Image-to-video body with content array")
    void buildImageToVideoBody() {
        JimengVideoProvider p = new JimengVideoProvider();
        p.setCameraFixed(false);
        p.setWatermark(true);

        VideoOptions opts = new VideoOptions("让画面中的人物转头微笑")
                .model("doubao-seedance-1-0-pro-250528")
                .duration(3)
                .refImageUrl("https://example.com/portrait.png");

        org.json.JSONObject body = p.buildRequestBody(opts);

        org.json.JSONArray content = body.getJSONArray("content");
        assertEquals(2, content.length(), "i2v should have text + image blocks");

        // Text block
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertTrue(content.getJSONObject(0).getString("text")
                .contains("--duration 3"));

        // Image block
        JSONObject imgBlock = content.getJSONObject(1);
        assertEquals("image_url", imgBlock.getString("type"));
        assertEquals("https://example.com/portrait.png",
                imgBlock.getJSONObject("image_url").getString("url"));

        System.out.println("=== Image-to-Video Body ===");
        System.out.println(body.toString(2));
    }

    @Test
    @DisplayName("All parameters embedded in text")
    void buildWithAllParams() {
        JimengVideoProvider p = new JimengVideoProvider();
        p.setCameraFixed(true);
        p.setWatermark(false);

        VideoOptions opts = new VideoOptions("海浪拍打岩石")
                .duration(8)
                .fps(24)
                .aspectRatio("16:9")
                .negativePrompt("blurry, distorted")
                .seed(999L);

        org.json.JSONObject body = p.buildRequestBody(opts);
        String text = body.getJSONArray("content").getJSONObject(0).getString("text");

        assertTrue(text.contains("--duration 8"));
        assertTrue(text.contains("--fps 24"));
        assertTrue(text.contains("--ar 16:9"));
        assertTrue(text.contains("--camerafixed true"));
        assertTrue(text.contains("--watermark false"));
        assertTrue(text.contains("--negative \"blurry, distorted\""));
        assertTrue(text.contains("--seed 999"));

        System.out.println("=== All Params ===");
        System.out.println(text);
    }

    @Test
    @DisplayName("Default camera and watermark settings")
    void buildWithDefaults() {
        JimengVideoProvider p = new JimengVideoProvider();

        // Defaults: cameraFixed=false, watermark=true
        VideoOptions opts = new VideoOptions("test");

        org.json.JSONObject body = p.buildRequestBody(opts);
        String text = body.getJSONArray("content").getJSONObject(0).getString("text");

        assertTrue(text.contains("--camerafixed false"));
        assertTrue(text.contains("--watermark true"));

        // Override
        p.setCameraFixed(true);
        p.setWatermark(false);

        body = p.buildRequestBody(opts);
        text = body.getJSONArray("content").getJSONObject(0).getString("text");

        assertTrue(text.contains("--camerafixed true"));
        assertTrue(text.contains("--watermark false"));
    }

    @Test
    @DisplayName("Parse successful video response")
    void parseSuccessResponse() {
        JimengVideoProvider p = new JimengVideoProvider();

        org.json.JSONObject resp = new org.json.JSONObject();
        resp.put("id", "task_abc123");
        resp.put("status", "succeeded");

        org.json.JSONObject data = new org.json.JSONObject();
        org.json.JSONArray results = new org.json.JSONArray();
        org.json.JSONObject video = new org.json.JSONObject();
        video.put("url", "https://ark-result.volces.com/video/v1.mp4");
        video.put("cover_url", "https://ark-result.volces.com/thumb/v1.jpg");
        video.put("duration", 5.0);
        video.put("resolution", "1920x1080");
        results.put(video);
        data.put("results", results);
        resp.put("data", data);

        VideoResponse r = p.parseResponse(resp,
                new VideoOptions("test"), "task_abc123");

        assertEquals("task_abc123", r.getTaskId());
        assertEquals(1, r.getVideos().size());
        assertEquals("https://ark-result.volces.com/video/v1.mp4",
                r.getFirstVideo().getUrl());
        assertEquals("https://ark-result.volces.com/thumb/v1.jpg",
                r.getFirstVideo().getCoverUrl());
        assertEquals(5.0, r.getFirstVideo().getDuration());
        assertEquals("1920x1080", r.getFirstVideo().getResolution());
    }

    @Test
    @DisplayName("Response with output.results shape")
    void parseOutputResultsShape() {
        JimengVideoProvider p = new JimengVideoProvider();

        org.json.JSONObject resp = new org.json.JSONObject();
        org.json.JSONObject output = new org.json.JSONObject();
        org.json.JSONArray results = new org.json.JSONArray();
        org.json.JSONObject video = new org.json.JSONObject();
        video.put("url", "https://example.com/video.mp4");
        results.put(video);
        output.put("results", results);
        resp.put("output", output);

        VideoResponse r = p.parseResponse(resp,
                new VideoOptions("test"), null);

        assertEquals(1, r.getVideos().size());
        assertEquals("https://example.com/video.mp4",
                r.getFirstVideo().getUrl());
    }

    @Test
    @DisplayName("Multiple videos in response")
    void parseMultipleVideos() {
        JimengVideoProvider p = new JimengVideoProvider();

        org.json.JSONObject resp = new org.json.JSONObject();
        org.json.JSONArray videos = new org.json.JSONArray();
        videos.put(new org.json.JSONObject().put("url", "https://a.mp4"));
        videos.put(new org.json.JSONObject().put("url", "https://b.mp4"));
        resp.put("videos", videos);

        VideoResponse r = p.parseResponse(resp,
                new VideoOptions("test"), null);

        assertEquals(2, r.getVideos().size());
        assertEquals("https://a.mp4", r.getVideos().get(0).getUrl());
        assertEquals("https://b.mp4", r.getVideos().get(1).getUrl());
    }

    // ==================== Integration tests ====================

    @Test
    @Tag("integration")
    @DisplayName("Text-to-video generation")
    void textToVideo() throws Exception {
        String key = requireApiKey();

        JimengVideoProvider p = new JimengVideoProvider();
        p.setApiKey(key);

        try {
            VideoResponse r = p.generate(new VideoRequest(
                    new VideoOptions("海浪拍打岩石的慢动作镜头")
                            .model("doubao-seedance-1-0-pro-250528")
                            .duration(5)));

            assertNotNull(r);
            assertFalse(r.getVideos().isEmpty(), "should have at least one video");
            assertNotNull(r.getFirstVideo().getUrl());

            System.out.println("=== Text-to-Video ===");
            System.out.println("URL: " + r.getFirstVideo().getUrl());
            System.out.println("Task: " + r.getTaskId());
            System.out.println("=== Done ===");
        } catch (IOException e) {
            skipIfAccessDenied(e);
        }
    }

    @Test
    @Tag("integration")
    @DisplayName("Image-to-video generation")
    void imageToVideo() throws Exception {
        String key = requireApiKey();

        JimengVideoProvider p = new JimengVideoProvider();
        p.setApiKey(key);
        p.setCameraFixed(false);

        try {
            VideoResponse r = p.generate(new VideoRequest(
                    new VideoOptions("无人机以极快速度穿越复杂障碍，带来沉浸式飞行体验")
                            .model("doubao-seedance-1-0-pro-250528")
                            .duration(5)
                            .refImageUrl("https://ark-project.tos-cn-beijing.volces.com/doc_image/seepro_i2v.png")));

            assertNotNull(r);
            assertFalse(r.getVideos().isEmpty());
            System.out.println("=== Image-to-Video ===");
            System.out.println("URL: " + r.getFirstVideo().getUrl());
            if (r.getFirstVideo().getCoverUrl() != null)
                System.out.println("Cover: " + r.getFirstVideo().getCoverUrl());
            System.out.println("=== Done ===");
        } catch (IOException e) {
            skipIfAccessDenied(e);
        }
    }

    @Test
    @Tag("integration")
    @DisplayName("Generate with custom camera settings")
    void customCameraWatermark() throws Exception {
        String key = requireApiKey();

        JimengVideoProvider p = new JimengVideoProvider();
        p.setApiKey(key);
        p.setCameraFixed(true);   // static camera
        p.setWatermark(false);    // no watermark

        try {
            VideoResponse r = p.generate(new VideoRequest(
                    new VideoOptions("一朵花慢慢绽放的延时摄影")
                            .model("doubao-seedance-1-0-pro-250528")
                            .duration(4)));

            assertNotNull(r);
            assertFalse(r.getVideos().isEmpty());
            System.out.println("=== Custom Settings ===");
            System.out.println("URL: " + r.getFirstVideo().getUrl());
            System.out.println("=== Done ===");
        } catch (IOException e) {
            skipIfAccessDenied(e);
        }
    }

    // ==================== Helper ====================

    /**
     * Skip test gracefully if the API key doesn't have access to this model.
     * This is a permission issue, not a code bug.
     */
    private static void skipIfAccessDenied(IOException e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("InvalidEndpointOrModel.NotFound")
                || msg.contains("does not exist or you do not have access"))) {
            System.out.println("SKIP: Model not accessible with this API key — " + msg);
            assumeTrue(false, "Model not accessible: " + msg);
        }
        throw new RuntimeException(e);
    }

    private static String requireApiKey() {
        String key = System.getenv(ENV_KEY);
        assumeTrue(key != null && !key.isBlank(),
                "DOUBAO_API_KEY not set — skipping integration test");
        return key;
    }
}
