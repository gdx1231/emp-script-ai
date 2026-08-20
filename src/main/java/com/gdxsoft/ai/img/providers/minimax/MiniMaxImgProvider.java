package com.gdxsoft.ai.img.providers.minimax;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgProviderBase;
import com.gdxsoft.ai.img.ImgProviderType;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;
import com.gdxsoft.ai.img.ImgResponse.GeneratedImage;

/**
 * MiniMax 图片生成 API Provider（文生图 + 图生图）。
 * <p>
 * POST JSON 到 {@code https://api.minimaxi.com/v1/image_generation}，Bearer 鉴权，
 * 同步返回结果（url 有效期 24 小时）。两个模型共用同一端点：
 * <ul>
 *   <li>{@code image-01}（默认）- 支持自定义 {@code width}/{@code height}
 *       （[512,2048]、8 的倍数，同时设置）与 {@code 21:9} 宽高比</li>
 *   <li>{@code image-01-live} - 支持画风 {@code style}（漫画/元气/中世纪/水彩），
 *       仅支持 {@code aspect_ratio}</li>
 * </ul>
 * 通用参数映射：
 * <ul>
 *   <li>{@code size} - {@code "W:H"} 直接作为 aspect_ratio；{@code "WxH"} 时
 *       image-01 发送精确 width/height（非法值对齐到 8 的倍数并夹在 [512,2048]），
 *       image-01-live 按比例匹配支持的宽高比，匹配不上抛错</li>
 *   <li>{@code promptExtend} -> {@code prompt_optimizer}（提示词自动优化）</li>
 *   <li>{@code watermark} -> {@code aigc_watermark}</li>
 *   <li>{@code style} -> {@code style.style_type}（仅 image-01-live，权重用
 *       {@link #setStyleWeight(Double)} 设置，默认 0.8 由 API 控制）</li>
 *   <li>{@code refImageUrls}/{@code refImageUrl} -> {@code subject_reference}
 *       （人物主体参考，图生图；官方每次请求仅支持 1 张，多张时截取第一张）</li>
 *   <li>{@code responseFormat} 的 {@code b64_json} 自动转换为 API 要求的 {@code base64}</li>
 * </ul>
 */
public class MiniMaxImgProvider extends ImgProviderBase {
    public static final String DEFAULT_URL = "https://api.minimaxi.com/v1/image_generation";
    public static final String DEFAULT_MODEL = "image-01";
    /** 支持画风设置的模型 */
    public static final String MODEL_LIVE = "image-01-live";

    /** 支持的宽高比（21:9 仅 image-01） */
    private static final Set<String> SUPPORTED_RATIOS =
            Set.of("1:1", "16:9", "4:3", "3:2", "2:3", "3:4", "9:16", "21:9");
    /** image-01-live 支持的画风类型 */
    private static final Set<String> SUPPORTED_STYLE_TYPES = Set.of("漫画", "元气", "中世纪", "水彩");

    /** 画风权重 (image-01-live)，null 表示使用 API 默认值 0.8 */
    private Double styleWeight;

    public MiniMaxImgProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.MINIMAX; }

    /** 设置画风权重 (image-01-live)，取值 (0, 1]；null 使用 API 默认值 0.8。 */
    public void setStyleWeight(Double styleWeight) { this.styleWeight = styleWeight; }
    public Double getStyleWeight() { return styleWeight; }

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        JSONObject body = buildRequestBody(request.getOptions());
        return parseResponse(postJson(apiUrl, body));
    }

    @Override
    public String curl(ImgRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        return "curl -X POST '" + apiUrl + "' \\\n"
                + "  -H 'Authorization: Bearer ****' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }

    /** 构造 API 请求体，公开给单元测试复用。 */
    public JSONObject buildRequestBody(ImgOptions opts) {
        String model = normalizeModel(opts.getModel());
        boolean live = MODEL_LIVE.equals(model);
        if (!DEFAULT_MODEL.equals(model) && !live) {
            throw new IllegalArgumentException("unsupported image model: " + model);
        }
        if (opts.getPrompt() == null || opts.getPrompt().length() > 1500) {
            throw new IllegalArgumentException("prompt is required and max length is 1500");
        }
        int n = opts.getN() == null ? 1 : opts.getN();
        if (n < 1 || n > 9) {
            throw new IllegalArgumentException("n must be between 1 and 9");
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("prompt", opts.getPrompt());
        body.put("n", n);
        body.put("response_format", toMiniMaxResponseFormat(opts.getResponseFormat()));
        if (opts.getSeed() != null) body.put("seed", opts.getSeed());
        if (opts.getPromptExtend() != null) body.put("prompt_optimizer", opts.getPromptExtend());
        if (opts.getWatermark() != null) body.put("aigc_watermark", opts.getWatermark());

        applySize(body, opts.getSize(), live);

        if (opts.getStyle() != null && !opts.getStyle().isBlank() && live) {
            if (!SUPPORTED_STYLE_TYPES.contains(opts.getStyle())) {
                throw new IllegalArgumentException("unsupported style_type: " + opts.getStyle());
            }
            if (styleWeight != null && (styleWeight <= 0 || styleWeight > 1)) {
                throw new IllegalArgumentException("styleWeight must be in (0, 1]");
            }
            JSONObject style = new JSONObject();
            style.put("style_type", opts.getStyle());
            if (styleWeight != null) style.put("style_weight", styleWeight);
            body.put("style", style);
        }
        // image-01 不支持 style，静默忽略（与其它 provider 忽略不支持参数一致）

        // 官方指南：每次请求仅支持 1 张参考图，多张时截取第一张（与 qwen/wanx 截取约定一致）
        List<String> refUrls = resolveRefImages(opts, 1);
        if (refUrls != null && !refUrls.isEmpty()) {
            JSONArray refs = new JSONArray();
            for (String url : refUrls) {
                refs.put(new JSONObject().put("type", "character").put("image_file", url));
            }
            body.put("subject_reference", refs);
        }
        return body;
    }

    /** 解析 API 响应，公开给单元测试复用。 */
    public ImgResponse parseResponse(JSONObject root) {
        JSONObject base = root.optJSONObject("base_resp");
        int status = base != null ? base.optInt("status_code", -1) : root.optInt("status_code", -1);
        if (status != 0) {
            String message = base != null ? base.optString("status_msg", "MiniMax image error")
                    : "MiniMax image error";
            throw new IllegalStateException("MiniMax image error " + status + ": " + message);
        }
        JSONObject data = root.optJSONObject("data");
        List<GeneratedImage> images = new ArrayList<>();
        if (data != null) {
            JSONArray urls = data.optJSONArray("image_urls");
            if (urls != null) {
                for (int i = 0; i < urls.length(); i++) {
                    images.add(new GeneratedImage(urls.optString(i), null, null));
                }
            }
            JSONArray b64s = data.optJSONArray("image_base64");
            if (b64s != null) {
                for (int i = 0; i < b64s.length(); i++) {
                    images.add(new GeneratedImage(null, b64s.optString(i), null));
                }
            }
        }
        JSONObject metadata = root.optJSONObject("metadata");
        int failedCount = metadata != null ? metadata.optInt("failed_count", 0) : 0;
        if (failedCount > 0) {
            LOGGER.warn("MiniMax 图片生成有 {} 张因内容安全检查未返回", failedCount);
        }
        if (images.isEmpty()) {
            throw new IllegalStateException(
                    "MiniMax image response has no images (failed_count=" + failedCount + ")");
        }
        return new ImgResponse(images, null, null, null, null, root);
    }

    private JSONObject postJson(String url, JSONObject body) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("MiniMax image generation requires an API key");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HttpUtils.createHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return new JSONObject(response.body());
    }

    /**
     * 把通用 size 映射到 MiniMax 的 aspect_ratio / width+height。
     * <ul>
     *   <li>{@code "W:H"} - 作为 aspect_ratio（须在支持列表内）</li>
     *   <li>{@code "WxH"} + image-01 - 发送 width/height（非法值对齐到 8 的倍数
     *       并夹在 [512,2048]）</li>
     *   <li>{@code "WxH"} + image-01-live - 按比例匹配支持的宽高比，匹配不上抛错
     *       （live 不支持 width/height）</li>
     * </ul>
     */
    private static void applySize(JSONObject body, String size, boolean live) {
        if (size == null || size.isBlank()) {
            return; // API 默认 1:1
        }
        String trimmed = size.trim();
        if (trimmed.contains(":")) {
            String ratio = matchAspectRatio(trimmed);
            if (ratio == null) {
                throw new IllegalArgumentException(
                        "unsupported aspect ratio: " + trimmed + ", supported: " + SUPPORTED_RATIOS);
            }
            requireRatioAllowedForModel(ratio, live);
            body.put("aspect_ratio", ratio);
            return;
        }
        String[] parts = trimmed.split("[xX*]");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "size must be '<width>x<height>' or '<w>:<h>', got: '" + size + "'");
        }
        int w;
        int h;
        try {
            w = Integer.parseInt(parts[0].trim());
            h = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "size must be '<width>x<height>' or '<w>:<h>', got: '" + size + "'");
        }
        if (live) {
            String ratio = matchAspectRatio(w, h);
            if (ratio == null) {
                throw new IllegalArgumentException("image-01-live only supports aspect_ratio ("
                        + SUPPORTED_RATIOS + "), custom size '" + size + "' requires image-01");
            }
            requireRatioAllowedForModel(ratio, live);
            body.put("aspect_ratio", ratio);
            return;
        }
        body.put("width", snapTo8(w));
        body.put("height", snapTo8(h));
    }

    /** "W:H" 与支持列表按比例匹配（非字符串相等）。 */
    private static String matchAspectRatio(String ratio) {
        String[] parts = ratio.split(":");
        if (parts.length != 2) return null;
        try {
            return matchAspectRatio(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** WxH 与支持列表按交叉相乘比较，命中返回标准比例（如 1344x576 -> 21:9）。 */
    private static String matchAspectRatio(int w, int h) {
        for (String r : SUPPORTED_RATIOS) {
            int idx = r.indexOf(':');
            int rw = Integer.parseInt(r.substring(0, idx));
            int rh = Integer.parseInt(r.substring(idx + 1));
            if (w * rh == h * rw) return r;
        }
        return null;
    }

    /** 21:9 仅 image-01 支持。 */
    private static void requireRatioAllowedForModel(String ratio, boolean live) {
        if (live && "21:9".equals(ratio)) {
            throw new IllegalArgumentException("aspect_ratio 21:9 is only supported by image-01");
        }
    }

    /** 对齐到 8 的倍数并夹在 [512, 2048]（MiniMax width/height 约束）。 */
    private static int snapTo8(int v) {
        int snapped = (v + 4) / 8 * 8;
        return Math.max(512, Math.min(2048, snapped));
    }

    /** 解析模型名：{@code ImgOptions} 的跨供应商默认值 {@code dall-e-3} 视为未设置。 */
    private static String normalizeModel(String model) {
        if (model == null || model.isEmpty() || "dall-e-3".equals(model)) {
            return DEFAULT_MODEL;
        }
        return model;
    }

    /** b64_json / base64 -> base64，其余 -> url。 */
    private static String toMiniMaxResponseFormat(String responseFormat) {
        if ("b64_json".equals(responseFormat) || "base64".equals(responseFormat)) {
            return "base64";
        }
        return "url";
    }
}
