package com.gdxsoft.ai.video;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.video.providers.kling.KlingVideoProvider;

/**
 * KlingVideoProvider 单元测试 -- Kling 3.0 API 请求体构建、端点路由、响应解析。
 * <p>
 * 纯单元测试，不需要 API Key。
 *
 * <pre>
 * mvn test -Dtest=KlingVideoProviderTest
 * </pre>
 */
class KlingVideoProviderTest {

    // ==================== 文生视频 ====================

    @Test
    @DisplayName("文生视频：仅 prompt + 默认参数")
    void textToVideoDefault() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("一只猫在赛博城市漫步")
                .model("kling-3.0")
                .resolution("720p")
                .aspectRatio("16:9")
                .duration(5);

        assertEquals(KlingVideoProvider.PATH_TEXT_TO_VIDEO, p.resolveEndpoint(opts));

        JSONObject body = p.buildRequestBody(opts);
        assertEquals("一只猫在赛博城市漫步", body.getString("prompt"));
        assertFalse(body.has("contents"), "文生视频不应有 contents");

        JSONObject settings = body.getJSONObject("settings");
        assertEquals("720p", settings.getString("resolution"));
        assertEquals("16:9", settings.getString("aspect_ratio"));
        assertEquals(5, settings.getInt("duration"));
        assertFalse(settings.has("audio"), "audio 未设置时应省略");
        assertFalse(settings.has("multi_shot"), "multi_shot 未设置时应省略");
    }

    @Test
    @DisplayName("默认模型为 kling-3.0")
    void defaultModel() {
        assertEquals("kling-3.0", KlingVideoProvider.DEFAULT_MODEL);

        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test");
        assertEquals(KlingVideoProvider.PATH_TEXT_TO_VIDEO, p.resolveEndpoint(opts));
    }

    // ==================== 图生视频 ====================

    @Test
    @DisplayName("图生视频：首帧 + 尾帧")
    void imageToVideoFirstAndLastFrame() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("女孩从微笑变为大笑")
                .firstFrameUrl("https://example.com/first.png")
                .lastFrameUrl("https://example.com/last.jpg")
                .duration(10);

        assertEquals(KlingVideoProvider.PATH_IMAGE_TO_VIDEO, p.resolveEndpoint(opts));

        JSONObject body = p.buildRequestBody(opts);
        JSONArray contents = body.getJSONArray("contents");
        assertEquals(3, contents.length());
        assertEquals("prompt", contents.getJSONObject(0).getString("type"));
        assertEquals("first_frame", contents.getJSONObject(1).getString("type"));
        assertEquals("https://example.com/first.png", contents.getJSONObject(1).getString("url"));
        assertEquals("last_frame", contents.getJSONObject(2).getString("type"));

        // 图生视频 settings 不含 aspect_ratio
        JSONObject settings = body.getJSONObject("settings");
        assertFalse(settings.has("aspect_ratio"), "图生视频不应有 aspect_ratio");
        assertEquals(10, settings.getInt("duration"));
    }

    @Test
    @DisplayName("图生视频：首帧 + 主体")
    void imageToVideoWithElement() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("@element_1 在雨中跳舞")
                .firstFrameUrl("https://example.com/first.png")
                .refElementIds("173");

        assertEquals(KlingVideoProvider.PATH_IMAGE_TO_VIDEO, p.resolveEndpoint(opts));

        JSONObject body = p.buildRequestBody(opts);
        JSONArray contents = body.getJSONArray("contents");
        assertEquals(3, contents.length());
        JSONObject elem = contents.getJSONObject(2);
        assertEquals("element", elem.getString("type"));
        assertEquals("173", elem.getString("element_id"));
        assertEquals("element_1", elem.getString("id"));
    }

    @Test
    @DisplayName("仅尾帧报错")
    void lastFrameOnlyThrows() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .lastFrameUrl("https://example.com/last.png");

        assertThrows(IllegalArgumentException.class, () -> p.resolveEndpoint(opts));
    }

    @Test
    @DisplayName("图生视频主体超过 3 个报错")
    void imageToVideoElementOverLimit() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .firstFrameUrl("https://example.com/first.png")
                .refElementIds("1", "2", "3", "4");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> p.buildRequestBody(opts));
        assertTrue(ex.getMessage().contains("最多支持 3 个主体"));
    }

    // ==================== Omni ====================

    @Test
    @DisplayName("Omni 显式模型：参考图 + 参考视频 + 待编辑视频 + 主体")
    void omniExplicitModel() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("@image_2 改变羽毛颜色")
                .model("kling-3.0-omni")
                .firstFrameUrl("https://example.com/first.png")
                .addRefImageUrl("https://example.com/ref.jpg")
                .addRefVideoUrl("https://example.com/feat.mp4")
                .baseVideoUrl("https://example.com/base.mp4")
                .addRefElementId("172")
                .resolution("1080p")
                .duration(5);

        assertEquals(KlingVideoProvider.PATH_OMNI_VIDEO, p.resolveEndpoint(opts));

        JSONObject body = p.buildRequestBody(opts);
        JSONArray contents = body.getJSONArray("contents");
        // prompt + first_frame + refer_image + feature_video + base_video + element = 6
        assertEquals(6, contents.length());

        assertEquals("prompt", contents.getJSONObject(0).getString("type"));
        assertEquals("first_frame", contents.getJSONObject(1).getString("type"));
        assertEquals("image_1", contents.getJSONObject(1).getString("id"));
        assertEquals("refer_image", contents.getJSONObject(2).getString("type"));
        assertEquals("image_2", contents.getJSONObject(2).getString("id"));
        assertEquals("feature_video", contents.getJSONObject(3).getString("type"));
        assertEquals("video_1", contents.getJSONObject(3).getString("id"));
        assertEquals("base_video", contents.getJSONObject(4).getString("type"));
        assertEquals("video_2", contents.getJSONObject(4).getString("id"));
        assertEquals("element", contents.getJSONObject(5).getString("type"));
        assertEquals("172", contents.getJSONObject(5).getString("element_id"));
        assertEquals("element_1", contents.getJSONObject(5).getString("id"));

        JSONObject settings = body.getJSONObject("settings");
        assertEquals("1080p", settings.getString("resolution"));
        assertEquals("1080p", settings.getString("resolution"));
    }

    @Test
    @DisplayName("默认模型 + 参考图自动路由到 Omni")
    void autoRouteToOmniWithRefImage() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("改变颜色")
                .addRefImageUrl("https://example.com/ref.jpg");

        assertEquals(KlingVideoProvider.PATH_OMNI_VIDEO, p.resolveEndpoint(opts));
    }

    @Test
    @DisplayName("默认模型 + 参考视频自动路由到 Omni")
    void autoRouteToOmniWithRefVideo() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("参考动作风格")
                .addRefVideoUrl("https://example.com/feat.mp4");

        assertEquals(KlingVideoProvider.PATH_OMNI_VIDEO, p.resolveEndpoint(opts));
    }

    @Test
    @DisplayName("默认模型 + 待编辑视频自动路由到 Omni")
    void autoRouteToOmniWithBaseVideo() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("编辑视频")
                .baseVideoUrl("https://example.com/base.mp4");

        assertEquals(KlingVideoProvider.PATH_OMNI_VIDEO, p.resolveEndpoint(opts));
    }

    // ==================== 动作控制 ====================

    @Test
    @DisplayName("动作控制：显式模型 + character_orientation")
    void motionControlExplicit() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("穿灰色 T 恤的女孩")
                .model("kling-3.0-motion")
                .characterOrientation("video")
                .refImageUrl("https://example.com/char.png")
                .refVideoUrl("https://example.com/dance.mp4")
                .resolution("1080p");

        assertEquals(KlingVideoProvider.PATH_MOTION_CONTROL, p.resolveEndpoint(opts));

        JSONObject body = p.buildRequestBody(opts);
        JSONArray contents = body.getJSONArray("contents");
        assertEquals(3, contents.length());
        assertEquals("prompt", contents.getJSONObject(0).getString("type"));
        assertEquals("image", contents.getJSONObject(1).getString("type"));
        assertEquals("video", contents.getJSONObject(2).getString("type"));

        JSONObject settings = body.getJSONObject("settings");
        assertEquals("video", settings.getString("character_orientation"));
        assertEquals("1080p", settings.getString("resolution"));
        assertFalse(settings.has("duration"), "动作控制不应有 duration");
        assertFalse(settings.has("aspect_ratio"), "动作控制不应有 aspect_ratio");
    }

    @Test
    @DisplayName("默认模型 + characterOrientation 自动路由到动作控制")
    void autoRouteToMotionWithOrientation() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .characterOrientation("image")
                .refImageUrl("https://example.com/char.png")
                .refVideoUrl("https://example.com/dance.mp4");

        assertEquals(KlingVideoProvider.PATH_MOTION_CONTROL, p.resolveEndpoint(opts));
    }

    @Test
    @DisplayName("动作控制缺少 characterOrientation 报错")
    void motionWithoutOrientationThrows() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("kling-3.0-motion");

        assertThrows(IllegalArgumentException.class, () -> p.resolveEndpoint(opts));
    }

    @Test
    @DisplayName("动作控制主体超过 1 个报错")
    void motionElementOverLimit() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .model("kling-3.0-motion")
                .characterOrientation("video")
                .refImageUrl("https://example.com/char.png")
                .refVideoUrl("https://example.com/dance.mp4")
                .refElementIds("1", "2");

        assertThrows(IllegalArgumentException.class, () -> p.buildRequestBody(opts));
    }

    // ==================== 模型校验 ====================

    @Test
    @DisplayName("未知模型报错")
    void unknownModelThrows() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test").model("kling-v1-6");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> p.buildRequestBody(opts));
        assertTrue(ex.getMessage().contains("不支持的可灵模型"));
        assertTrue(ex.getMessage().contains("kling-3.0"));
    }

    @Test
    @DisplayName("prompt 为空报错")
    void emptyPromptThrows() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("");

        assertThrows(IllegalArgumentException.class, () -> p.buildRequestBody(opts));
    }

    // ==================== audio 映射 ====================

    @Test
    @DisplayName("audio: generateAudio=true -> native")
    void audioNative() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test").generateAudio(true);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals("native", body.getJSONObject("settings").getString("audio"));
    }

    @Test
    @DisplayName("audio: generateAudio=false -> off")
    void audioOff() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test").generateAudio(false);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals("off", body.getJSONObject("settings").getString("audio"));
    }

    @Test
    @DisplayName("audio: keepSourceAudio=true -> original（优先于 generateAudio）")
    void audioOriginal() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test")
                .generateAudio(false)
                .keepSourceAudio(true);

        JSONObject body = p.buildRequestBody(opts);
        assertEquals("original", body.getJSONObject("settings").getString("audio"));
    }

    @Test
    @DisplayName("audio: 均未设置时省略")
    void audioOmitted() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test");

        JSONObject body = p.buildRequestBody(opts);
        assertFalse(body.getJSONObject("settings").has("audio"));
    }

    // ==================== multi_shot / watermark ====================

    @Test
    @DisplayName("multiShot=true -> settings.multi_shot")
    void multiShotEnabled() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("镜头 1, 5, 女孩走入画面;").multiShot(true);

        JSONObject body = p.buildRequestBody(opts);
        assertTrue(body.getJSONObject("settings").getBoolean("multi_shot"));
    }

    @Test
    @DisplayName("watermark=true -> options.watermark_info.enabled")
    void watermarkEnabled() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test").watermark(true);

        JSONObject body = p.buildRequestBody(opts);
        assertTrue(body.getJSONObject("options")
                .getJSONObject("watermark_info").getBoolean("enabled"));
    }

    @Test
    @DisplayName("watermark 未设置时不发送 options")
    void watermarkOmitted() {
        KlingVideoProvider p = new KlingVideoProvider();
        VideoOptions opts = new VideoOptions("test");

        JSONObject body = p.buildRequestBody(opts);
        assertFalse(body.has("options"));
    }

    // ==================== 响应解析 ====================

    @Test
    @DisplayName("parseTask 成功：解析 outputs 中的视频")
    void parseTaskSuccess() {
        KlingVideoProvider p = new KlingVideoProvider();

        JSONObject task = new JSONObject();
        task.put("id", "893605946402811985");
        task.put("status", "succeeded");

        JSONArray outputs = new JSONArray();
        JSONObject video = new JSONObject();
        video.put("type", "video");
        video.put("url", "https://example.com/result.mp4");
        video.put("watermark_url", "https://example.com/result_wm.mp4");
        video.put("duration", "10");
        outputs.put(video);
        task.put("outputs", outputs);

        JSONArray billing = new JSONArray();
        JSONObject bill = new JSONObject();
        bill.put("charge_type", "cash");
        bill.put("amount", "1.5");
        billing.put(bill);
        task.put("billing", billing);

        VideoOptions opts = new VideoOptions("test").model("kling-3.0");
        VideoResponse resp = p.parseTask(task, opts, "893605946402811985");

        assertEquals("893605946402811985", resp.getTaskId());
        assertEquals(1, resp.getVideos().size());
        // watermark=false 默认取 url
        assertEquals("https://example.com/result.mp4", resp.getFirstVideo().getUrl());
        assertEquals(10.0, resp.getFirstVideo().getDuration());
        assertNotNull(resp.getUsage());
        assertEquals(1, resp.getUsage().getJSONArray("billing").length());
    }

    @Test
    @DisplayName("parseTask 水印偏好：watermark=true 时取 watermark_url")
    void parseTaskPreferWatermark() {
        KlingVideoProvider p = new KlingVideoProvider();

        JSONObject task = new JSONObject();
        task.put("status", "succeeded");
        JSONArray outputs = new JSONArray();
        JSONObject video = new JSONObject();
        video.put("type", "video");
        video.put("url", "https://example.com/result.mp4");
        video.put("watermark_url", "https://example.com/result_wm.mp4");
        outputs.put(video);
        task.put("outputs", outputs);

        VideoOptions opts = new VideoOptions("test").watermark(true);
        VideoResponse resp = p.parseTask(task, opts, "task-1");

        assertEquals("https://example.com/result_wm.mp4", resp.getFirstVideo().getUrl());
    }

    @Test
    @DisplayName("parseTask 跳过非 video 类型的 output")
    void parseTaskSkipsNonVideo() {
        KlingVideoProvider p = new KlingVideoProvider();

        JSONObject task = new JSONObject();
        task.put("status", "succeeded");
        JSONArray outputs = new JSONArray();

        JSONObject image = new JSONObject();
        image.put("type", "image");
        image.put("url", "https://example.com/cover.png");
        outputs.put(image);

        JSONObject video = new JSONObject();
        video.put("type", "video");
        video.put("url", "https://example.com/result.mp4");
        outputs.put(video);

        task.put("outputs", outputs);

        VideoResponse resp = p.parseTask(task, new VideoOptions("test"), "task-1");
        assertEquals(1, resp.getVideos().size());
        assertEquals("https://example.com/result.mp4", resp.getFirstVideo().getUrl());
    }

    // ==================== curl ====================

    @Test
    @DisplayName("curl 包含端点 URL 和轮询地址")
    void curlContainsEndpointAndPollUrl() {
        KlingVideoProvider p = new KlingVideoProvider();
        p.setApiKey("test-key");
        VideoOptions opts = new VideoOptions("test");

        String curl = p.curl(new VideoRequest(opts));
        assertTrue(curl.contains("/text-to-video/kling-3.0"));
        assertTrue(curl.contains("/tasks?task_ids={task_id}"));
        assertTrue(curl.contains("Authorization: Bearer ****"));
    }
}
