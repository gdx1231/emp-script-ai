package com.gdxsoft.ai.video;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.video.providers.qwen.QwenVideoProvider;

/**
 * QwenVideoProvider 单元测试 —— 聚焦 WAN 3.0 请求体构建。
 * <p>
 * 纯单元测试，不需要 API Key。
 *
 * <pre>
 * mvn test -pl . -Dtest=QwenVideoProviderTest
 * </pre>
 */
class QwenVideoProviderTest {

    // ==================== WAN 3.0 文生视频 ====================

    @Test
    @DisplayName("WAN 3.0 文生视频：仅 prompt + 默认参数")
    void wan3TextToVideo() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("一只小猫在月光下的屋顶上奔跑")
                .model("wan3.0-video")
                .resolution("480P")
                .aspectRatio("adaptive")
                .duration(5);

        JSONObject body = p.buildRequestBody(opts);

        assertEquals("wan3.0-video", body.getString("model"));

        // input
        JSONObject input = body.getJSONObject("input");
        assertEquals("一只小猫在月光下的屋顶上奔跑", input.getString("prompt"));
        assertFalse(input.has("media"), "纯文生视频不应有 media");

        // parameters
        JSONObject params = body.getJSONObject("parameters");
        assertEquals("480P", params.getString("resolution"));
        assertEquals("adaptive", params.getString("ratio"));
        assertEquals(5, params.getInt("duration"));
        assertTrue(params.getBoolean("audio"), "audio 默认应为 true");
        assertFalse(params.getBoolean("watermark"), "watermark 默认应为 false");
    }

    // ==================== WAN 3.0 首帧/首尾帧 ====================

    @Test
    @DisplayName("WAN 3.0 首帧生视频")
    void wan3FirstFrame() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("都市奇幻涂鸦角色活过来")
                .model("wan3.0-video")
                .firstFrameUrl("https://example.com/first.png")
                .duration(5);

        JSONObject body = p.buildRequestBody(opts);
        JSONArray media = body.getJSONObject("input").getJSONArray("media");

        assertEquals(1, media.length());
        assertEquals("first_frame", media.getJSONObject(0).getString("type"));
        assertEquals("https://example.com/first.png", media.getJSONObject(0).getString("url"));
    }

    @Test
    @DisplayName("WAN 3.0 首尾帧生视频")
    void wan3FirstAndLastFrame() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("女孩从微笑变为大笑")
                .model("wan3.0-video")
                .firstFrameUrl("https://example.com/first.png")
                .lastFrameUrl("https://example.com/last.jpg")
                .duration(5);

        JSONObject body = p.buildRequestBody(opts);
        JSONArray media = body.getJSONObject("input").getJSONArray("media");

        assertEquals(2, media.length());
        assertEquals("first_frame", media.getJSONObject(0).getString("type"));
        assertEquals("last_frame", media.getJSONObject(1).getString("type"));
    }

    // ==================== WAN 3.0 参考生视频 ====================

    @Test
    @DisplayName("WAN 3.0 参考生视频：多张参考图 + 参考视频")
    void wan3ReferenceMedia() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("视频1抱着图3在椅子上弹奏")
                .model("wan3.0-video")
                .refImageUrls("https://example.com/img1.jpg", "https://example.com/img2.jpg")
                .refVideoUrls("https://example.com/vid1.mp4")
                .duration(5);

        JSONObject body = p.buildRequestBody(opts);
        JSONArray media = body.getJSONObject("input").getJSONArray("media");

        assertEquals(3, media.length());
        assertEquals("reference_image", media.getJSONObject(0).getString("type"));
        assertEquals("reference_image", media.getJSONObject(1).getString("type"));
        assertEquals("reference_video", media.getJSONObject(2).getString("type"));
    }

    // ==================== WAN 3.0 file 类型 ====================

    @Test
    @DisplayName("WAN 3.0 file 类型：参考文件生视频")
    void wan3FileType() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("智能眼镜产品广告")
                .model("wan3.0-video")
                .fileUrl("https://example.com/glass.pptx")
                .resolution("480P")
                .duration(10);

        JSONObject body = p.buildRequestBody(opts);

        JSONArray media = body.getJSONObject("input").getJSONArray("media");
        assertEquals(1, media.length());
        assertEquals("file", media.getJSONObject(0).getString("type"));
        assertEquals("https://example.com/glass.pptx", media.getJSONObject(0).getString("url"));
    }

    // ==================== WAN 3.0 link 类型 ====================

    @Test
    @DisplayName("WAN 3.0 link 类型：网页链接生视频")
    void wan3LinkType() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("根据网页内容生成视频")
                .model("wan3.0-video")
                .linkUrl("https://example.com/article/test");

        JSONObject body = p.buildRequestBody(opts);

        JSONArray media = body.getJSONObject("input").getJSONArray("media");
        assertEquals(1, media.length());
        assertEquals("link", media.getJSONObject(0).getString("type"));
        assertEquals("https://example.com/article/test", media.getJSONObject(0).getString("url"));
    }

    // ==================== 互斥校验 ====================

    @Test
    @DisplayName("WAN 3.0 file + link 互斥")
    void wan3FileLinkMutualExclusive() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("wan3.0-video")
                .fileUrl("https://example.com/doc.pdf")
                .linkUrl("https://example.com/page");

        assertThrows(IllegalArgumentException.class, () -> p.buildRequestBody(opts));
    }

    @Test
    @DisplayName("WAN 3.0 file + first_frame 互斥")
    void wan3FileAndFirstFrameMutualExclusive() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("wan3.0-video")
                .fileUrl("https://example.com/doc.pdf")
                .firstFrameUrl("https://example.com/img.png");

        assertThrows(IllegalArgumentException.class, () -> p.buildRequestBody(opts));
    }

    @Test
    @DisplayName("WAN 3.0 link + reference_image 互斥")
    void wan3LinkAndRefImageMutualExclusive() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("wan3.0-video")
                .linkUrl("https://example.com/page")
                .addRefImageUrl("https://example.com/img.png");

        assertThrows(IllegalArgumentException.class, () -> p.buildRequestBody(opts));
    }

    // ==================== reference 数量上限 ====================

    @Test
    @DisplayName("WAN 3.0 reference_image 超过 10 张报错")
    void wan3RefImageOverLimit() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test").model("wan3.0-video");
        for (int i = 0; i < 11; i++) {
            opts.addRefImageUrl("https://example.com/img" + i + ".png");
        }

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> p.buildRequestBody(opts));
        assertTrue(ex.getMessage().contains("reference_image 最多 10 张"));
    }

    @Test
    @DisplayName("WAN 3.0 reference_video 超过 5 段报错")
    void wan3RefVideoOverLimit() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test").model("wan3.0-video");
        for (int i = 0; i < 6; i++) {
            opts.addRefVideoUrl("https://example.com/vid" + i + ".mp4");
        }

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> p.buildRequestBody(opts));
        assertTrue(ex.getMessage().contains("reference_video 最多 5 段"));
    }

    @Test
    @DisplayName("WAN 3.0 reference_audio 超过 5 段报错")
    void wan3RefAudioOverLimit() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test").model("wan3.0-video");
        for (int i = 0; i < 6; i++) {
            opts.addRefAudioUrl("https://example.com/audio" + i + ".mp3");
        }

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> p.buildRequestBody(opts));
        assertTrue(ex.getMessage().contains("reference_audio 最多 5 段"));
    }

    @Test
    @DisplayName("WAN 3.0 reference 数量恰好在上限内通过")
    void wan3RefAtLimit() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test").model("wan3.0-video");
        // 10 images + 5 videos + 5 audios = 恰好上限
        for (int i = 0; i < 10; i++) opts.addRefImageUrl("https://example.com/img" + i + ".png");
        for (int i = 0; i < 5; i++) opts.addRefVideoUrl("https://example.com/vid" + i + ".mp4");
        for (int i = 0; i < 5; i++) opts.addRefAudioUrl("https://example.com/audio" + i + ".mp3");

        JSONObject body = p.buildRequestBody(opts);
        JSONArray media = body.getJSONObject("input").getJSONArray("media");
        assertEquals(20, media.length(), "10 images + 5 videos + 5 audios = 20");
    }

    // ==================== audio 参数 ====================

    @Test
    @DisplayName("WAN 3.0 audio 默认 true")
    void wan3AudioDefault() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test").model("wan3.0-video");

        JSONObject body = p.buildRequestBody(opts);
        assertTrue(body.getJSONObject("parameters").getBoolean("audio"));
    }

    @Test
    @DisplayName("WAN 3.0 audio 可显式关闭")
    void wan3AudioExplicitFalse() {
        QwenVideoProvider p = new QwenVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("wan3.0-video")
                .generateAudio(false);

        JSONObject body = p.buildRequestBody(opts);
        assertFalse(body.getJSONObject("parameters").getBoolean("audio"));
    }

    // ==================== 响应解析 ====================

    @Test
    @DisplayName("WAN 3.0 成功响应解析")
    void wan3ParseSuccess() {
        QwenVideoProvider p = new QwenVideoProvider();

        JSONObject resp = new JSONObject();
        JSONObject output = new JSONObject();
        output.put("task_id", "task-123");
        output.put("task_status", "SUCCEEDED");
        output.put("video_url", "https://dashscope-result.oss.aliyuncs.com/video.mp4");
        output.put("orig_prompt", "一只小猫");
        resp.put("output", output);

        JSONObject usage = new JSONObject();
        usage.put("video_count", 1);
        usage.put("duration", 5.0);
        usage.put("fps", 30);
        usage.put("SR", 720);
        usage.put("ratio", "16:9");
        resp.put("usage", usage);

        VideoOptions opts = new VideoOptions("一只小猫").model("wan3.0-video");
        VideoResponse r = p.parseSuccess(resp, opts, "task-123");

        assertEquals("task-123", r.getTaskId());
        assertEquals(1, r.getVideos().size());
        assertEquals("https://dashscope-result.oss.aliyuncs.com/video.mp4",
                r.getFirstVideo().getUrl());
        assertEquals(5.0, r.getFirstVideo().getDuration());
        assertEquals("720P", r.getFirstVideo().getResolution());
        assertNotNull(r.getUsage());
        assertEquals(1, r.getUsage().getInt("video_count"));
    }

    // ==================== 默认模型 ====================

    @Test
    @DisplayName("默认模型为 wan3.0-video")
    void defaultModel() {
        QwenVideoProvider p = new QwenVideoProvider();
        assertEquals("wan3.0-video", QwenVideoProvider.DEFAULT_MODEL);

        // 不设 model 时使用默认
        VideoOptions opts = new VideoOptions("test");
        JSONObject body = p.buildRequestBody(opts);
        assertEquals("wan3.0-video", body.getString("model"));
    }

    // ==================== curl 输出 ====================

    @Test
    @DisplayName("curl 包含 X-DashScope-Async header")
    void curlContainsAsyncHeader() {
        QwenVideoProvider p = new QwenVideoProvider();
        p.setApiKey("test-key");
        VideoOptions opts = new VideoOptions("test").model("wan3.0-video");

        String curl = p.curl(new VideoRequest(opts));
        assertTrue(curl.contains("X-DashScope-Async: enable"));
        assertTrue(curl.contains("wan3.0-video"));
    }
}
