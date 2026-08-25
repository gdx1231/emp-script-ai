package com.gdxsoft.ai.video.providers.doubao;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.video.VideoOptions;

/**
 * DoubaoVideoProvider 单元测试 -- Seedance 2.5 / 2.0 / 1.0 请求体构建。
 * <p>
 * 纯单元测试，不需要 API Key。
 *
 * <pre>
 * mvn test -Dtest=DoubaoVideoProviderTest
 * </pre>
 */
class DoubaoVideoProviderTest {

    // ==================== 默认模型 ====================

    @Test
    @DisplayName("默认模型为 Seedance 2.5")
    void defaultModel() {
        assertEquals("doubao-seedance-2-5-260814", DoubaoVideoProvider.DEFAULT_MODEL);

        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("一只小猫在月光下奔跑");
        JSONObject body = p.buildRequestBody(opts);
        assertEquals("doubao-seedance-2-5-260814", body.getString("model"));
    }

    // ==================== 文生视频 ====================

    @Test
    @DisplayName("文生视频：仅 prompt + 默认参数")
    void textToVideoDefault() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("一只小猫在月光下奔跑")
                .duration(5)
                .aspectRatio("16:9")
                .resolution("720p");

        JSONObject body = p.buildRequestBody(opts);

        assertEquals("doubao-seedance-2-5-260814", body.getString("model"));
        assertEquals(5, body.getInt("duration"));
        assertEquals("16:9", body.getString("ratio"));
        assertEquals("720p", body.getString("resolution"));

        JSONArray content = body.getJSONArray("content");
        assertEquals(1, content.length());
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("一只小猫在月光下奔跑", content.getJSONObject(0).getString("text"));
    }

    // ==================== 首帧 / 首尾帧 ====================

    @Test
    @DisplayName("首帧生视频")
    void firstFrame() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("都市奇幻涂鸦角色活过来")
                .firstFrameUrl("https://example.com/first.png")
                .duration(5);

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");

        assertEquals(2, content.length());
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertEquals("first_frame", content.getJSONObject(1).getString("role"));
        assertEquals("https://example.com/first.png",
                content.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test
    @DisplayName("首尾帧生视频")
    void firstAndLastFrame() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("女孩从微笑变为大笑")
                .firstFrameUrl("https://example.com/first.png")
                .lastFrameUrl("https://example.com/last.jpg")
                .duration(5);

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");

        assertEquals(3, content.length());
        assertEquals("first_frame", content.getJSONObject(1).getString("role"));
        assertEquals("last_frame", content.getJSONObject(2).getString("role"));
    }

    // ==================== 多模态参考 ====================

    @Test
    @DisplayName("多模态参考：参考图 + 参考视频 + 参考音频")
    void multiModalReference() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("视频1抱着图3在椅子上弹奏")
                .refImageUrls("https://example.com/img1.jpg", "https://example.com/img2.jpg")
                .refVideoUrls("https://example.com/vid1.mp4")
                .refAudioUrls("https://example.com/audio1.mp3")
                .duration(5);

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");

        // text + 2 images + 1 video + 1 audio = 5
        assertEquals(5, content.length());
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("reference_image", content.getJSONObject(1).getString("role"));
        assertEquals("reference_image", content.getJSONObject(2).getString("role"));
        assertEquals("reference_video", content.getJSONObject(3).getString("role"));
        assertEquals("reference_audio", content.getJSONObject(4).getString("role"));
    }

    @Test
    @DisplayName("Seedance 2.5：30 张参考图不报错")
    void seedance25_refImageLimit() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-5-260814");
        for (int i = 0; i < 30; i++) {
            opts.addRefImageUrl("https://example.com/img" + i + ".png");
        }

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");
        // text + 30 images = 31
        assertEquals(31, content.length());
    }

    @Test
    @DisplayName("Seedance 2.5：10 段参考视频不报错")
    void seedance25_refVideoLimit() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-5-260814");
        for (int i = 0; i < 10; i++) {
            opts.addRefVideoUrl("https://example.com/vid" + i + ".mp4");
        }

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");
        // text + 10 videos = 11
        assertEquals(11, content.length());
    }

    @Test
    @DisplayName("Seedance 2.5：10 段参考音频不报错")
    void seedance25_refAudioLimit() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-5-260814");
        for (int i = 0; i < 10; i++) {
            opts.addRefAudioUrl("https://example.com/audio" + i + ".mp3");
        }

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");
        // text + 10 audios = 11
        assertEquals(11, content.length());
    }

    // ==================== Seedance 2.5 新字段 ====================

    @Test
    @DisplayName("Seedance 2.5：output_format=mov")
    void seedance25_outputFormat() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-5-260814")
                .outputFormat("mov");

        JSONObject body = p.buildRequestBody(opts);
        assertEquals("mov", body.getString("output_format"));
    }

    @Test
    @DisplayName("Seedance 2.5：omni_reference_task_type=edit")
    void seedance25_omniTaskType() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("改变视频色调为暖色")
                .model("doubao-seedance-2-5-260814")
                .omniReferenceTaskType("edit")
                .refVideoUrl("https://example.com/video.mp4")
                .aspectRatio("adaptive")
                .duration(-1);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals("edit", body.getString("omni_reference_task_type"));
        assertEquals("adaptive", body.getString("ratio"));
        assertEquals(-1, body.getInt("duration"));
    }

    @Test
    @DisplayName("Seedance 2.5：priority 优先级")
    void seedance25_priority() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-5-260814")
                .priority(5);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals(5, body.getInt("priority"));
    }

    @Test
    @DisplayName("Seedance 2.5：execution_expires_after 任务超时")
    void seedance25_executionExpiresAfter() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-5-260814")
                .executionExpiresAfter(7200);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals(7200, body.getInt("execution_expires_after"));
    }

    @Test
    @DisplayName("Seedance 2.0：priority 优先级")
    void seedance20_priority() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-0-260128")
                .priority(3);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals(3, body.getInt("priority"));
    }

    @Test
    @DisplayName("Seedance 1.0：frames 帧数")
    void seedance10_frames() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-1-0-pro-241215")
                .frames(57);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals(57, body.getInt("frames"));
    }

    @Test
    @DisplayName("Seedance 2.5 不发送 frames 字段")
    void seedance25_noFrames() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-2-5-260814")
                .frames(57);

        JSONObject body = p.buildRequestBody(opts);
        assertFalse(body.has("frames"), "Seedance 2.5 不应发送 frames 字段");
    }

    @Test
    @DisplayName("Seedance 1.0 不发送 priority 字段")
    void seedance10_noPriority() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-1-0-pro-241215")
                .priority(5);

        JSONObject body = p.buildRequestBody(opts);
        assertFalse(body.has("priority"), "Seedance 1.0 不应发送 priority 字段");
    }

    // ==================== 音频 / 水印 / 尾帧 ====================

    @Test
    @DisplayName("generate_audio=true")
    void generateAudioTrue() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test").generateAudio(true);

        JSONObject body = p.buildRequestBody(opts);
        assertTrue(body.getBoolean("generate_audio"));
    }

    @Test
    @DisplayName("generate_audio=false")
    void generateAudioFalse() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test").generateAudio(false);

        JSONObject body = p.buildRequestBody(opts);
        assertFalse(body.getBoolean("generate_audio"));
    }

    @Test
    @DisplayName("watermark=true")
    void watermarkTrue() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test").watermark(true);

        JSONObject body = p.buildRequestBody(opts);
        assertTrue(body.getBoolean("watermark"));
    }

    @Test
    @DisplayName("return_last_frame=true")
    void returnLastFrame() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test").returnLastFrame(true);

        JSONObject body = p.buildRequestBody(opts);
        assertTrue(body.getBoolean("return_last_frame"));
    }

    // ==================== 联网搜索 ====================

    @Test
    @DisplayName("enableWebSearch=true 添加 web_search 工具")
    void enableWebSearch() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("2026 年春节联欢晚会").enableWebSearch(true);

        JSONObject body = p.buildRequestBody(opts);
        assertTrue(body.has("tools"));
        JSONArray tools = body.getJSONArray("tools");
        assertEquals(1, tools.length());
        assertEquals("web_search", tools.getJSONObject(0).getString("type"));
    }

    // ==================== 离线推理 ====================

    @Test
    @DisplayName("service_tier=flex（离线推理）")
    void serviceTierFlex() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("doubao-seedance-1-5-pro-251215")
                .serviceTier("flex");

        JSONObject body = p.buildRequestBody(opts);
        assertEquals("flex", body.getString("service_tier"));
    }

    // ==================== 响应解析 ====================

    @Test
    @DisplayName("成功响应解析")
    void parseSuccess() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();

        JSONObject resp = new JSONObject();
        resp.put("id", "task-123");
        resp.put("status", "succeeded");

        JSONObject content = new JSONObject();
        content.put("video_url", "https://example.com/video.mp4");
        content.put("last_frame_url", "https://example.com/last_frame.png");
        resp.put("content", content);

        JSONObject usage = new JSONObject();
        usage.put("completion_tokens", 123);
        resp.put("usage", usage);

        VideoOptions opts = new VideoOptions("test");
        var result = p.parseResponse(resp, opts, "task-123");

        assertEquals("task-123", result.getTaskId());
        assertEquals(1, result.getVideos().size());
        assertEquals("https://example.com/video.mp4", result.getFirstVideo().getUrl());
        assertEquals("https://example.com/last_frame.png", result.getLastFrameUrl());
    }

    // ==================== curl ====================

    @Test
    @DisplayName("curl 输出包含必要信息")
    void curlOutput() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        p.setApiKey("test-key");
        VideoOptions opts = new VideoOptions("test");

        String curl = p.curl(new com.gdxsoft.ai.video.VideoRequest(opts));
        assertTrue(curl.contains("ark.cn-beijing.volces.com"));
        assertTrue(curl.contains("Authorization: Bearer"));
        assertTrue(curl.contains("doubao-seedance-2-5-260814"));
    }

    // ==================== 私域素材库 asset:// URI ====================

    @Test
    @DisplayName("asset:// URI 作为参考图传入")
    void assetUriAsRefImage() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("图片1中的女孩在微笑")
                .refImageUrl("asset://asset-20260318035710-abc");

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");

        assertEquals(2, content.length());
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertEquals("asset://asset-20260318035710-abc",
                content.getJSONObject(1).getJSONObject("image_url").getString("url"));
        assertEquals("reference_image", content.getJSONObject(1).getString("role"));
    }

    @Test
    @DisplayName("asset:// URI 作为首帧传入")
    void assetUriAsFirstFrame() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("女孩开始说话")
                .firstFrameUrl("asset://asset-20260318035710-abc");

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");

        assertEquals(2, content.length());
        assertEquals("first_frame", content.getJSONObject(1).getString("role"));
        assertEquals("asset://asset-20260318035710-abc",
                content.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test
    @DisplayName("混合 asset:// 和普通 URL")
    void mixedAssetAndNormalUrl() {
        DoubaoVideoProvider p = new DoubaoVideoProvider();
        VideoOptions opts = new VideoOptions("图片1和图片2中的人对话")
                .refImageUrls("asset://asset-abc", "https://example.com/img.jpg");

        JSONObject body = p.buildRequestBody(opts);
        JSONArray content = body.getJSONArray("content");

        assertEquals(3, content.length());
        assertEquals("asset://asset-abc",
                content.getJSONObject(1).getJSONObject("image_url").getString("url"));
        assertEquals("https://example.com/img.jpg",
                content.getJSONObject(2).getJSONObject("image_url").getString("url"));
    }
}
