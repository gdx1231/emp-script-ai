package com.gdxsoft.ai.img;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/**
 * Provider-agnostic options for an image generation request.
 * <p>
 * Each provider maps these to its own request shape (JSON body / multipart, etc.).
 *
 * @since 1.2.0
 */
public class ImgOptions {
    private String model = "dall-e-3";
    private String prompt;
    private String size = "1024x1024";
    private String quality;
    private String style;
    private Integer n = 1;
    private String responseFormat = "url"; // "url" or "b64_json"
    private String negativePrompt;
    private Integer steps;
    private Long seed;
    private String user;

    // ==== qwen-image-3.0 新增字段 ====

    /** 提示词智能改写 (qwen-image-3.0)。默认 true（由 API 端控制）。 */
    private Boolean promptExtend;

    /** 提示词改写方式: "direct" (DPE) 或 "agent" (APE, 仅T2I)。 */
    private String promptExtendMode;

    /** 是否添加水印 (qwen-image-3.0)。默认 false。 */
    private Boolean watermark;

    /**
     * 多张参考图 URL 列表 (qwen-image-3.0 I2I 支持 1-3 张)。
     * wanx 2.7 支持 0-9 张，传递完整列表；其它 provider 按各自能力截取。
     */
    private List<String> refImageUrls;

    // ==== wanx 2.7 新增字段 ====

    /**
     * 交互式编辑的边界框列表 (wanx 2.7)。
     * <p>
     * 外层长度 = 输入图片数量；每个元素 = 该图的边界框列表（最多 2 个）。
     * 坐标格式 {@code [x1, y1, x2, y2]}，原图像素绝对坐标，左上角为 (0,0)。
     * 对无框选的图传空列表 {@code []}。
     */
    private List<List<List<Integer>>> bboxList;

    /**
     * 是否启用组图输出模式 (wanx 2.7)。默认 false。
     * <p>
     * 开启后 n 表示最大生成张数（1-12），实际数量由模型决定。
     */
    private Boolean enableSequential;

    /**
     * 是否开启思考模式 (wanx 2.7)。默认 true（由 API 端控制）。
     * <p>
     * 仅在关闭组图模式且无图片输入时生效。
     */
    private Boolean thinkingMode;

    /**
     * 自定义颜色主题 (wanx 2.7)，每个元素是 {@code {"hex": "#XXXXXX", "ratio": "XX.XX%"}}。
     * 3-10 种颜色，所有 ratio 之和必须为 100.00%。
     * 仅在关闭组图模式时可用。
     */
    private List<JSONObject> colorPalette;

    // ==== doubao seedream 5.0 新增字段 ====

    /**
     * 组图生成模式 (doubao seedream 5.0)。
     * <p>
     * 可选值："auto"（启用组图生成）。
     * 设置后模型会生成多张连贯图片。
     */
    private String sequentialImageGeneration;

    /**
     * 组图生成选项 (doubao seedream 5.0)。
     * <p>
     * 当前支持的选项：
     * <ul>
     *   <li>{@code max_images}: 最大生成图片数量（Integer）</li>
     * </ul>
     */
    private JSONObject sequentialImageGenerationOptions;

    /**
     * 输出图片格式 (doubao seedream 5.0)。
     * <p>
     * 可选值："png"（默认）、"jpeg"、"webp" 等。
     */
    private String outputFormat;

    public ImgOptions() {}

    public ImgOptions(String prompt) {
        this.prompt = prompt;
    }

    // ---- Getters / Setters (fluent) ----

    public String getModel() { return model; }
    public ImgOptions model(String model) { this.model = model; return this; }
    public ImgOptions setModel(String model) { this.model = model; return this; }

    public String getPrompt() { return prompt; }
    public ImgOptions prompt(String prompt) { this.prompt = prompt; return this; }
    public ImgOptions setPrompt(String prompt) { this.prompt = prompt; return this; }

    public String getSize() { return size; }
    public ImgOptions size(String size) { this.size = size; return this; }
    public ImgOptions setSize(String size) { this.size = size; return this; }

    /** e.g. "standard" or "hd" (OpenAI) */
    public String getQuality() { return quality; }
    public ImgOptions quality(String quality) { this.quality = quality; return this; }
    public ImgOptions setQuality(String quality) { this.quality = quality; return this; }

    /** e.g. "vivid" or "natural" (OpenAI) */
    public String getStyle() { return style; }
    public ImgOptions style(String style) { this.style = style; return this; }
    public ImgOptions setStyle(String style) { this.style = style; return this; }

    public Integer getN() { return n; }
    public ImgOptions n(Integer n) { this.n = n; return this; }
    public ImgOptions setN(Integer n) { this.n = n; return this; }

    /** "url" or "b64_json" */
    public String getResponseFormat() { return responseFormat; }
    public ImgOptions responseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }
    public ImgOptions setResponseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }

    /** Negative prompt (supported by Stability AI, ignored by OpenAI) */
    public String getNegativePrompt() { return negativePrompt; }
    public ImgOptions negativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; return this; }
    public ImgOptions setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; return this; }

    /** Diffusion steps (supported by Stability AI) */
    public Integer getSteps() { return steps; }
    public ImgOptions steps(Integer steps) { this.steps = steps; return this; }
    public ImgOptions setSteps(Integer steps) { this.steps = steps; return this; }

    /** Seed for reproducible generation */
    public Long getSeed() { return seed; }
    public ImgOptions seed(Long seed) { this.seed = seed; return this; }
    public ImgOptions setSeed(Long seed) { this.seed = seed; return this; }

    /** End-user identifier for abuse monitoring */
    public String getUser() { return user; }
    public ImgOptions user(String user) { this.user = user; return this; }
    public ImgOptions setUser(String user) { this.user = user; return this; }

    // ==== Image-to-Image (垫图/参考图) ====

    /** Reference image URL for image-to-image generation. */
    private String refImageUrl;

    /** Reference image strength [0.0, 1.0]. Higher = more similar to reference. */
    private Double refStrength;

    /**
     * Reference mode:
     * <ul>
     *   <li>{@code "repaint"} — generate based on reference content (default)</li>
     *   <li>{@code "refonly"} — generate based on reference style only</li>
     * </ul>
     */
    private String refMode;

    /** Reference image URL. */
    public String getRefImageUrl() { return refImageUrl; }
    public ImgOptions refImageUrl(String url) { this.refImageUrl = url; return this; }
    public ImgOptions setRefImageUrl(String url) { this.refImageUrl = url; return this; }

    /** How strongly to follow the reference [0.0, 1.0]. */
    public Double getRefStrength() { return refStrength; }
    public ImgOptions refStrength(Double strength) { this.refStrength = strength; return this; }
    public ImgOptions setRefStrength(Double strength) { this.refStrength = strength; return this; }

    /**
     * Reference mode: {@code "repaint"} (content) or {@code "refonly"} (style).
     */
    public String getRefMode() { return refMode; }
    public ImgOptions refMode(String mode) { this.refMode = mode; return this; }
    public ImgOptions setRefMode(String mode) { this.refMode = mode; return this; }

    // ==== qwen-image-3.0 新增字段 getter/setter ====

    /** 提示词智能改写 (qwen-image-3.0)。null 表示不传此参数（由 API 默认处理）。 */
    public Boolean getPromptExtend() { return promptExtend; }
    public ImgOptions promptExtend(Boolean v) { this.promptExtend = v; return this; }
    public ImgOptions setPromptExtend(Boolean v) { this.promptExtend = v; return this; }

    /** 提示词改写方式: "direct" (DPE) 或 "agent" (APE, 仅T2I)。 */
    public String getPromptExtendMode() { return promptExtendMode; }
    public ImgOptions promptExtendMode(String v) { this.promptExtendMode = v; return this; }
    public ImgOptions setPromptExtendMode(String v) { this.promptExtendMode = v; return this; }

    /** 是否添加水印 (qwen-image-3.0)。null 表示不传。 */
    public Boolean getWatermark() { return watermark; }
    public ImgOptions watermark(Boolean v) { this.watermark = v; return this; }
    public ImgOptions setWatermark(Boolean v) { this.watermark = v; return this; }

    /**
     * 多张参考图 URL 列表 (qwen-image-3.0 I2I 支持 1-3 张)。
     * wanx 模式下仅使用第一张作为 ref_image。
     */
    public List<String> getRefImageUrls() { return refImageUrls; }
    public ImgOptions refImageUrls(List<String> urls) { this.refImageUrls = urls; return this; }
    public ImgOptions setRefImageUrls(List<String> urls) { this.refImageUrls = urls; return this; }

    /** 便捷方法：添加一张参考图到列表。 */
    public ImgOptions addRefImageUrl(String url) {
        if (this.refImageUrls == null) this.refImageUrls = new ArrayList<>();
        this.refImageUrls.add(url);
        return this;
    }

    // ==== wanx 2.7 新增字段 getter/setter ====

    /**
     * 交互式编辑的边界框列表 (wanx 2.7)。
     * <p>
     * 外层长度 = 输入图片数量；每个元素 = 该图的边界框列表（最多 2 个）。
     * 坐标格式 {@code [x1, y1, x2, y2]}，原图像素绝对坐标，左上角为 (0,0)。
     * 对无框选的图传空列表 {@code []}。
     */
    public List<List<List<Integer>>> getBboxList() { return bboxList; }
    public ImgOptions bboxList(List<List<List<Integer>>> v) { this.bboxList = v; return this; }
    public ImgOptions setBboxList(List<List<List<Integer>>> v) { this.bboxList = v; return this; }

    /**
     * 是否启用组图输出模式 (wanx 2.7)。null 表示不传此参数。
     * <p>
     * 开启后 n 表示最大生成张数（1-12），实际数量由模型决定。
     */
    public Boolean getEnableSequential() { return enableSequential; }
    public ImgOptions enableSequential(Boolean v) { this.enableSequential = v; return this; }
    public ImgOptions setEnableSequential(Boolean v) { this.enableSequential = v; return this; }

    /**
     * 是否开启思考模式 (wanx 2.7)。null 表示不传此参数（API 端默认 true）。
     * <p>
     * 仅在关闭组图模式且无图片输入时生效。
     */
    public Boolean getThinkingMode() { return thinkingMode; }
    public ImgOptions thinkingMode(Boolean v) { this.thinkingMode = v; return this; }
    public ImgOptions setThinkingMode(Boolean v) { this.thinkingMode = v; return this; }

    /**
     * 自定义颜色主题 (wanx 2.7)，每个元素是 {@code {"hex": "#XXXXXX", "ratio": "XX.XX%"}}。
     * 3-10 种颜色，所有 ratio 之和必须为 100.00%。
     * 仅在关闭组图模式时可用。
     */
    public List<JSONObject> getColorPalette() { return colorPalette; }
    public ImgOptions colorPalette(List<JSONObject> v) { this.colorPalette = v; return this; }
    public ImgOptions setColorPalette(List<JSONObject> v) { this.colorPalette = v; return this; }

    // ==== doubao seedream 5.0 新增字段 getter/setter ====

    /**
     * 组图生成模式 (doubao seedream 5.0)。
     * <p>
     * 可选值："auto"（启用组图生成）。设置后模型会生成多张连贯图片。
     */
    public String getSequentialImageGeneration() { return sequentialImageGeneration; }
    public ImgOptions sequentialImageGeneration(String v) { this.sequentialImageGeneration = v; return this; }
    public ImgOptions setSequentialImageGeneration(String v) { this.sequentialImageGeneration = v; return this; }

    /**
     * 组图生成选项 (doubao seedream 5.0)。
     * <p>
     * 当前支持的选项：{@code max_images}（Integer，最大生成图片数量）。
     */
    public JSONObject getSequentialImageGenerationOptions() { return sequentialImageGenerationOptions; }
    public ImgOptions sequentialImageGenerationOptions(JSONObject v) { this.sequentialImageGenerationOptions = v; return this; }
    public ImgOptions setSequentialImageGenerationOptions(JSONObject v) { this.sequentialImageGenerationOptions = v; return this; }

    /**
     * 便捷方法：设置组图生成的最大图片数量。
     *
     * @param maxImages 最大图片数量
     * @return this
     */
    public ImgOptions maxSequentialImages(int maxImages) {
        if (this.sequentialImageGenerationOptions == null) {
            this.sequentialImageGenerationOptions = new JSONObject();
        }
        this.sequentialImageGenerationOptions.put("max_images", maxImages);
        if (this.sequentialImageGeneration == null) {
            this.sequentialImageGeneration = "auto";
        }
        return this;
    }

    /**
     * 输出图片格式 (doubao seedream 5.0)。
     * <p>
     * 可选值："png"（默认）、"jpeg"、"webp" 等。
     */
    public String getOutputFormat() { return outputFormat; }
    public ImgOptions outputFormat(String v) { this.outputFormat = v; return this; }
    public ImgOptions setOutputFormat(String v) { this.outputFormat = v; return this; }
}
