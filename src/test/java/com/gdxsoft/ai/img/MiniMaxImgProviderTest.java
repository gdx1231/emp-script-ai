package com.gdxsoft.ai.img;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.img.providers.minimax.MiniMaxImgProvider;

class MiniMaxImgProviderTest {
    @Test
    void buildRequestBodyShape() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        JSONObject body = provider.buildRequestBody(new ImgOptions("一只橘猫"));
        assertEquals("image-01", body.getString("model"));
        assertEquals("一只橘猫", body.getString("prompt"));
        assertEquals(1, body.getInt("n"));
        assertEquals("url", body.getString("response_format"));
        // 默认 size 1024x1024 -> 精确 width/height
        assertEquals(1024, body.getInt("width"));
        assertEquals(1024, body.getInt("height"));
        assertFalse(body.has("aspect_ratio"));
    }

    @Test
    void mapsUniversalFields() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        ImgOptions opts = new ImgOptions("一只橘猫")
                .responseFormat("b64_json")
                .seed(42L)
                .n(3)
                .promptExtend(true)
                .watermark(true);
        JSONObject body = provider.buildRequestBody(opts);
        assertEquals("base64", body.getString("response_format"));
        assertEquals(42L, body.getLong("seed"));
        assertEquals(3, body.getInt("n"));
        assertTrue(body.getBoolean("prompt_optimizer"));
        assertTrue(body.getBoolean("aigc_watermark"));
    }

    @Test
    void convertsSizeToAspectRatio() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        assertEquals("16:9", provider.buildRequestBody(
                new ImgOptions("p").size("16:9")).getString("aspect_ratio"));
        // WxH 约分匹配（1280x720 -> 16:9），image-01-live 走 aspect_ratio
        assertEquals("16:9", provider.buildRequestBody(
                new ImgOptions("p").model("image-01-live").size("1280x720"))
                .getString("aspect_ratio"));
        // 交叉相乘匹配：7:3 归一化为 21:9
        assertEquals("21:9", provider.buildRequestBody(
                new ImgOptions("p").size("7:3")).getString("aspect_ratio"));
        assertEquals("4:3", provider.buildRequestBody(
                new ImgOptions("p").model("image-01-live").size("1152*864"))
                .getString("aspect_ratio"));
    }

    @Test
    void customSizeSendsWidthHeight() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        JSONObject body = provider.buildRequestBody(new ImgOptions("p").size("2048x1024"));
        assertEquals(2048, body.getInt("width"));
        assertEquals(1024, body.getInt("height"));
        assertFalse(body.has("aspect_ratio"));

        // 非法值对齐到 8 的倍数并夹在 [512,2048]
        JSONObject snapped = provider.buildRequestBody(new ImgOptions("p").size("1000x700"));
        assertEquals(1000, snapped.getInt("width"));
        assertEquals(704, snapped.getInt("height"));
        JSONObject clamped = provider.buildRequestBody(new ImgOptions("p").size("4096x100"));
        assertEquals(2048, clamped.getInt("width"));
        assertEquals(512, clamped.getInt("height"));
    }

    @Test
    void liveRejectsCustomSize() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").model("image-01-live").size("2048x1024")));
        // 21:9 仅 image-01
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").model("image-01-live").size("21:9")));
    }

    @Test
    void buildsStyleForLiveModel() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        provider.setStyleWeight(0.5);
        JSONObject body = provider.buildRequestBody(new ImgOptions("p")
                .model("image-01-live").size("16:9").style("漫画"));
        JSONObject style = body.getJSONObject("style");
        assertEquals("漫画", style.getString("style_type"));
        assertEquals(0.5, style.getDouble("style_weight"));

        // image-01 不支持 style，忽略
        assertFalse(provider.buildRequestBody(new ImgOptions("p").style("漫画")).has("style"));
        // 非法画风 / 非法权重
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").model("image-01-live").style("赛博朋克")));
        MiniMaxImgProvider badWeight = new MiniMaxImgProvider();
        badWeight.setStyleWeight(1.5);
        assertThrows(IllegalArgumentException.class, () -> badWeight.buildRequestBody(
                new ImgOptions("p").model("image-01-live").style("水彩")));
    }

    @Test
    void buildsSubjectReferenceFromRefImages() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        // 官方每次请求仅支持 1 张参考图，多张时截取第一张
        ImgOptions opts = new ImgOptions("让人物站在图书馆窗边")
                .refImageUrls(List.of("https://example.com/a.jpg", "https://example.com/b.jpg"));
        JSONArray refs = provider.buildRequestBody(opts).getJSONArray("subject_reference");
        assertEquals(1, refs.length());
        assertEquals("character", refs.getJSONObject(0).getString("type"));
        assertEquals("https://example.com/a.jpg", refs.getJSONObject(0).getString("image_file"));

        // 单数 refImageUrl 同样映射
        JSONArray single = provider.buildRequestBody(
                new ImgOptions("p").refImageUrl("https://example.com/c.jpg"))
                .getJSONArray("subject_reference");
        assertEquals(1, single.length());
    }

    @Test
    void validatesParams() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").model("image-02")));
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").n(10)));
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").size("5:4")));
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").size("2K")));
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new ImgOptions("p").prompt("a".repeat(1501))));
    }

    @Test
    void parsesUrlAndBase64Responses() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        JSONObject urlRoot = new JSONObject("""
                {"id":"t1","data":{"image_urls":["https://example.com/1.png","https://example.com/2.png"]},
                 "metadata":{"success_count":2,"failed_count":0},
                 "base_resp":{"status_code":0,"status_msg":"success"}}
                """);
        ImgResponse urlResp = provider.parseResponse(urlRoot);
        assertEquals(2, urlResp.getImages().size());
        assertTrue(urlResp.getFirstImage().isUrl());
        assertEquals("https://example.com/1.png", urlResp.getFirstImage().getUrl());

        JSONObject b64Root = new JSONObject("""
                {"data":{"image_base64":["aGVsbG8="]},"base_resp":{"status_code":0}}
                """);
        ImgResponse b64Resp = provider.parseResponse(b64Root);
        assertTrue(b64Resp.getFirstImage().isBase64());
        assertEquals("aGVsbG8=", b64Resp.getFirstImage().getB64Json());
    }

    @Test
    void parsesErrorResponse() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        JSONObject quota = new JSONObject("""
                {"base_resp":{"status_code":1008,"status_msg":"账号余额不足"}}
                """);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> provider.parseResponse(quota));
        assertTrue(e.getMessage().contains("1008"));

        // 全部图片被内容安全拦截
        JSONObject blocked = new JSONObject("""
                {"data":{},"metadata":{"success_count":0,"failed_count":3},
                 "base_resp":{"status_code":0}}
                """);
        assertThrows(IllegalStateException.class, () -> provider.parseResponse(blocked));
    }

    @Test
    void rendersCurlWithMaskedKey() {
        MiniMaxImgProvider provider = new MiniMaxImgProvider();
        provider.setApiKey("secret-key");
        String curl = provider.curl(new ImgRequest(new ImgOptions("一只橘猫")));
        assertTrue(curl.contains("https://api.minimaxi.com/v1/image_generation"));
        assertTrue(curl.contains("Authorization: Bearer ****"));
        assertFalse(curl.contains("secret-key"));
    }

    @Test
    void factoryCreatesProvider() {
        IImgProvider provider = ImgProviderFactory.create("minimax_img");
        assertInstanceOf(MiniMaxImgProvider.class, provider);
        assertEquals(ImgProviderType.MINIMAX, provider.getProviderType());
        assertTrue(ImgProviderFactory.isSupported("minimax_img"));
        assertTrue(ImgProviderFactory.getSupportedProviders().contains("minimax_img"));
    }
}
