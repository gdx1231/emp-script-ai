package com.gdxsoft.ai.video;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 视频生成请求的通用参数。
 * <p>
 * 与 provider 无关，各 provider 自行将字段映射到自己的请求格式。
 * 支持 fluent 链式调用：
 * <pre>{@code
 * new VideoOptions("一只猫在赛博城市漫步")
 *     .duration(5)
 *     .aspectRatio("16:9")
 *     .generateAudio(true)
 * }</pre>
 *
 * @since 1.3.0
 */
public class VideoOptions {

    /** 模型 ID（如 doubao-seedance-2-0-260128），null 时使用 provider 默认模型 */
    private String model;

    /** 文本提示词（必填） */
    private String prompt;

    /** 负向提示词，描述不希望出现的内容 */
    private String negativePrompt;

    /** 视频时长（秒），如 5、10、15 */
    private Integer duration;

    /** 帧率（fps） */
    private Integer fps;

    /** 输出分辨率，如 "480p"、"720p"、"1080p"、"4k" */
    private String resolution;

    /** 输出宽高比，如 "16:9"、"9:16"、"1:1"、"adaptive" */
    private String aspectRatio;

    /** 随机种子，用于复现结果 */
    private Long seed;

    /** 引导系数（CFG scale），控制提示词的影响力，范围 1-20 */
    private Integer cfgScale;

    /** 运镜方式，如 "static"、"zoom_in"、"pan_left" 等 */
    private String cameraMovement;

    /** 单张参考图片 URL（图生视频），兼容字段；多张时优先使用 {@link #refImageUrls} */
    private String refImageUrl;

    /** 严格首帧图片 URL（Seedance first_frame role）：视频第一帧与该图一致，优先于 refImageUrls */
    private String firstFrameUrl;

    /** 严格尾帧图片 URL（Seedance last_frame role）：视频最后一帧与该图一致 */
    private String lastFrameUrl;

    /** 单个参考视频 URL，兼容字段；多个时优先使用 {@link #refVideoUrls} */
    private String refVideoUrl;

    /** 单个参考音频 URL，兼容字段；多个时优先使用 {@link #refAudioUrls} */
    private String refAudioUrl;

    /** 是否生成有声视频（Seedance 2.0） */
    private Boolean generateAudio;

    /** 推理服务层级，"flex" 表示离线推理（Seedance 2.0） */
    private String serviceTier;

    /** 是否返回视频尾帧图（Seedance 2.0），结果通过 VideoResponse.getLastFrameUrl() 获取 */
    private Boolean returnLastFrame;

    /** 是否启用联网搜索（Seedance 2.0），仅纯文本输入时有效 */
    private Boolean enableWebSearch;

    /** 是否添加水印（Seedance 2.0）。null 表示沿用 provider instance 默认值 */
    private Boolean watermark;

    /** 多张参考图片 URL 列表（最多 9 张），用于多模态参考 */
    private List<String> refImageUrls;

    /** 多个参考视频 URL 列表（最多 3 个），用于视频延长/编辑 */
    private List<String> refVideoUrls;

    /** 多个参考音频 URL 列表（最多 3 个） */
    private List<String> refAudioUrls;

    /** 文件 URL（WAN 3.0 type=file），支持 docx/xlsx/pptx/pdf/txt 等，最多 1 个，不可与 link 同时使用 */
    private String fileUrl;

    /** 网页链接 URL（WAN 3.0 type=link），公开网页，最多 1 个，不可与 file 同时使用 */
    private String linkUrl;

    /** 是否生成多镜头视频（Kling 3.0，prompt 用“镜头 n, m, words;”格式描述分镜），null 沿用服务端默认 */
    private Boolean multiShot;

    /** 人物朝向参考（Kling 3.0 动作控制）："image" 与形象参考图一致 / "video" 与动作参考视频一致 */
    private String characterOrientation;

    /** 主体库主体 ID 列表（Kling 3.0 contents element），prompt 中通过自动生成的 @element_N 引用 */
    private List<String> refElementIds;

    /** 待编辑视频 URL（Kling 3.0 Omni base_video）：编辑该视频而非参考其风格 */
    private String baseVideoUrl;

    /** 是否保留参考视频原声（Kling 3.0 audio=original），优先于 generateAudio */
    private Boolean keepSourceAudio;

    /** 输出视频格式（Seedance 2.5）："mp4"（通用）/ "mov"（专业色彩精度，适用于后期加工） */
    private String outputFormat;

    /** 全模态参考任务类型（Seedance 2.5）："auto" / "reference" / "edit" / "extend" */
    private String omniReferenceTaskType;

    /** 执行优先级（Seedance 2.5 / 2.0），0-9，数值越大优先级越高 */
    private Integer priority;

    /** 任务超时阈值（秒，Seedance 2.5 / 2.0），范围 [3600, 259200]，默认 172800（48 小时） */
    private Integer executionExpiresAfter;

    /** 视频帧数（Seedance 1.0 系列），范围 [29, 289]，与 duration 二选一，frames 优先级更高 */
    private Integer frames;

    public VideoOptions() {}

    /**
     * 以提示词构造。
     *
     * @param prompt 文本提示词
     */
    public VideoOptions(String prompt) { this.prompt = prompt; }

    // ==================== 模型 ====================

    /** @return 模型 ID，null 表示使用 provider 默认值 */
    public String getModel() { return model; }

    /** 设置模型 ID（fluent） */
    public VideoOptions model(String v) { this.model = v; return this; }

    /** 设置模型 ID（setter） */
    public VideoOptions setModel(String v) { this.model = v; return this; }

    // ==================== 提示词 ====================

    /** @return 文本提示词 */
    public String getPrompt() { return prompt; }

    /** 设置文本提示词（fluent） */
    public VideoOptions prompt(String v) { this.prompt = v; return this; }

    /** 设置文本提示词（setter） */
    public VideoOptions setPrompt(String v) { this.prompt = v; return this; }

    /** @return 负向提示词 */
    public String getNegativePrompt() { return negativePrompt; }

    /** 设置负向提示词（fluent） */
    public VideoOptions negativePrompt(String v) { this.negativePrompt = v; return this; }

    /** 设置负向提示词（setter） */
    public VideoOptions setNegativePrompt(String v) { this.negativePrompt = v; return this; }

    // ==================== 输出规格 ====================

    /** @return 视频时长（秒） */
    public Integer getDuration() { return duration; }

    /** 设置视频时长（fluent） */
    public VideoOptions duration(Integer v) { this.duration = v; return this; }

    /** 设置视频时长（setter） */
    public VideoOptions setDuration(Integer v) { this.duration = v; return this; }

    /** @return 帧率（fps） */
    public Integer getFps() { return fps; }

    /** 设置帧率（fluent） */
    public VideoOptions fps(Integer v) { this.fps = v; return this; }

    /** 设置帧率（setter） */
    public VideoOptions setFps(Integer v) { this.fps = v; return this; }

    /** @return 输出分辨率，如 "480p"、"720p"、"1080p"、"4k" */
    public String getResolution() { return resolution; }

    /** 设置输出分辨率（fluent）。4K 仅 Seedance 2.0 支持，输出 H.265/HEVC 10bit */
    public VideoOptions resolution(String v) { this.resolution = v; return this; }

    /** 设置输出分辨率（setter） */
    public VideoOptions setResolution(String v) { this.resolution = v; return this; }

    /** @return 输出宽高比，如 "16:9"、"9:16"、"1:1"、"adaptive" */
    public String getAspectRatio() { return aspectRatio; }

    /** 设置输出宽高比（fluent）。"adaptive" 表示自适应输入图片宽高比 */
    public VideoOptions aspectRatio(String v) { this.aspectRatio = v; return this; }

    /** 设置输出宽高比（setter） */
    public VideoOptions setAspectRatio(String v) { this.aspectRatio = v; return this; }

    // ==================== 生成控制 ====================

    /** @return 随机种子 */
    public Long getSeed() { return seed; }

    /** 设置随机种子（fluent） */
    public VideoOptions seed(Long v) { this.seed = v; return this; }

    /** 设置随机种子（setter） */
    public VideoOptions setSeed(Long v) { this.seed = v; return this; }

    /** @return 引导系数（CFG scale），范围 1-20 */
    public Integer getCfgScale() { return cfgScale; }

    /** 设置引导系数（fluent） */
    public VideoOptions cfgScale(Integer v) { this.cfgScale = v; return this; }

    /** 设置引导系数（setter） */
    public VideoOptions setCfgScale(Integer v) { this.cfgScale = v; return this; }

    /** @return 运镜方式 */
    public String getCameraMovement() { return cameraMovement; }

    /** 设置运镜方式（fluent） */
    public VideoOptions cameraMovement(String v) { this.cameraMovement = v; return this; }

    /** 设置运镜方式（setter） */
    public VideoOptions setCameraMovement(String v) { this.cameraMovement = v; return this; }

    // ==================== 单个参考素材（兼容） ====================

    /** @return 单张参考图片 URL */
    public String getRefImageUrl() { return refImageUrl; }

    /** 设置单张参考图片 URL（fluent）。多张图片请使用 {@link #refImageUrls(String...)} */
    public VideoOptions refImageUrl(String v) { this.refImageUrl = v; return this; }

    /** 设置单张参考图片 URL（setter） */
    public VideoOptions setRefImageUrl(String v) { this.refImageUrl = v; return this; }

    /** @return 严格首帧图片 URL */
    public String getFirstFrameUrl() { return firstFrameUrl; }

    /** 设置严格首帧图片 URL（fluent）：视频第一帧与该图一致 */
    public VideoOptions firstFrameUrl(String v) { this.firstFrameUrl = v; return this; }

    /** 设置严格首帧图片 URL（setter） */
    public VideoOptions setFirstFrameUrl(String v) { this.firstFrameUrl = v; return this; }

    /** @return 严格尾帧图片 URL */
    public String getLastFrameUrl() { return lastFrameUrl; }

    /** 设置严格尾帧图片 URL（fluent）：视频最后一帧与该图一致 */
    public VideoOptions lastFrameUrl(String v) { this.lastFrameUrl = v; return this; }

    /** 设置严格尾帧图片 URL（setter） */
    public VideoOptions setLastFrameUrl(String v) { this.lastFrameUrl = v; return this; }

    /** @return 单个参考视频 URL */
    public String getRefVideoUrl() { return refVideoUrl; }

    /** 设置单个参考视频 URL（fluent）。多个视频请使用 {@link #refVideoUrls(String...)} */
    public VideoOptions refVideoUrl(String v) { this.refVideoUrl = v; return this; }

    /** 设置单个参考视频 URL（setter） */
    public VideoOptions setRefVideoUrl(String v) { this.refVideoUrl = v; return this; }

    /** @return 单个参考音频 URL */
    public String getRefAudioUrl() { return refAudioUrl; }

    /** 设置单个参考音频 URL（fluent）。多个音频请使用 {@link #refAudioUrls(String...)} */
    public VideoOptions refAudioUrl(String v) { this.refAudioUrl = v; return this; }

    /** 设置单个参考音频 URL（setter） */
    public VideoOptions setRefAudioUrl(String v) { this.refAudioUrl = v; return this; }

    // ==================== Seedance 2.0 扩展 ====================

    /** @return 是否生成有声视频 */
    public Boolean getGenerateAudio() { return generateAudio; }

    /** 设置是否生成有声视频（fluent） */
    public VideoOptions generateAudio(Boolean v) { this.generateAudio = v; return this; }

    /** 设置是否生成有声视频（setter） */
    public VideoOptions setGenerateAudio(Boolean v) { this.generateAudio = v; return this; }

    /** @return 推理服务层级，"flex" 表示离线推理 */
    public String getServiceTier() { return serviceTier; }

    /** 设置推理服务层级（fluent）。"flex" = 离线推理，降低成本 */
    public VideoOptions serviceTier(String v) { this.serviceTier = v; return this; }

    /** 设置推理服务层级（setter） */
    public VideoOptions setServiceTier(String v) { this.serviceTier = v; return this; }

    /** @return 是否返回尾帧图 */
    public Boolean getReturnLastFrame() { return returnLastFrame; }

    /** 设置是否返回尾帧图（fluent）。结果通过 {@code VideoResponse.getLastFrameUrl()} 获取 */
    public VideoOptions returnLastFrame(Boolean v) { this.returnLastFrame = v; return this; }

    /** 设置是否返回尾帧图（setter） */
    public VideoOptions setReturnLastFrame(Boolean v) { this.returnLastFrame = v; return this; }

    /** @return 是否启用联网搜索（仅纯文本输入时有效） */
    public Boolean getEnableWebSearch() { return enableWebSearch; }

    /** 设置是否启用联网搜索（fluent）。开启后模型可搜索互联网获取时效性信息 */
    public VideoOptions enableWebSearch(Boolean v) { this.enableWebSearch = v; return this; }

    /** 设置是否启用联网搜索（setter） */
    public VideoOptions setEnableWebSearch(Boolean v) { this.enableWebSearch = v; return this; }

    /** @return 是否添加水印；null 表示使用 provider instance 默认值 */
    public Boolean getWatermark() { return watermark; }

    /** 设置是否添加水印（fluent）。null = 沿用 provider 默认 */
    public VideoOptions watermark(Boolean v) { this.watermark = v; return this; }

    /** 设置是否添加水印（setter） */
    public VideoOptions setWatermark(Boolean v) { this.watermark = v; return this; }

    // ==================== 多模态参考素材列表 ====================

    /** @return 多张参考图片 URL 列表（最多 9 张） */
    public List<String> getRefImageUrls() { return refImageUrls; }

    /** 设置参考图片列表（fluent） */
    public VideoOptions refImageUrls(List<String> v) { this.refImageUrls = v; return this; }

    /** 设置参考图片列表（setter） */
    public VideoOptions setRefImageUrls(List<String> v) { this.refImageUrls = v; return this; }

    /**
     * 追加一张参考图片 URL。
     *
     * @param url 图片 URL，支持 http(s):// 和 asset:// 格式
     */
    public VideoOptions addRefImageUrl(String url) {
        if (this.refImageUrls == null) this.refImageUrls = new ArrayList<>();
        this.refImageUrls.add(url);
        return this;
    }

    /**
     * 设置多张参考图片 URL（可变参数，fluent）。
     *
     * @param urls 图片 URL 数组，最多 9 张
     */
    public VideoOptions refImageUrls(String... urls) {
        this.refImageUrls = new ArrayList<>(Arrays.asList(urls));
        return this;
    }

    /** @return 多个参考视频 URL 列表（最多 3 个） */
    public List<String> getRefVideoUrls() { return refVideoUrls; }

    /** 设置参考视频列表（fluent） */
    public VideoOptions refVideoUrls(List<String> v) { this.refVideoUrls = v; return this; }

    /** 设置参考视频列表（setter） */
    public VideoOptions setRefVideoUrls(List<String> v) { this.refVideoUrls = v; return this; }

    /**
     * 追加一个参考视频 URL。
     *
     * @param url 视频 URL，支持 http(s):// 和 asset:// 格式
     */
    public VideoOptions addRefVideoUrl(String url) {
        if (this.refVideoUrls == null) this.refVideoUrls = new ArrayList<>();
        this.refVideoUrls.add(url);
        return this;
    }

    /**
     * 设置多个参考视频 URL（可变参数，fluent）。
     *
     * @param urls 视频 URL 数组，最多 3 个
     */
    public VideoOptions refVideoUrls(String... urls) {
        this.refVideoUrls = new ArrayList<>(Arrays.asList(urls));
        return this;
    }

    /** @return 多个参考音频 URL 列表（最多 3 个） */
    public List<String> getRefAudioUrls() { return refAudioUrls; }

    /** 设置参考音频列表（fluent） */
    public VideoOptions refAudioUrls(List<String> v) { this.refAudioUrls = v; return this; }

    /** 设置参考音频列表（setter） */
    public VideoOptions setRefAudioUrls(List<String> v) { this.refAudioUrls = v; return this; }

    /**
     * 追加一个参考音频 URL。
     *
     * @param url 音频 URL，支持 http(s):// 和 asset:// 格式
     */
    public VideoOptions addRefAudioUrl(String url) {
        if (this.refAudioUrls == null) this.refAudioUrls = new ArrayList<>();
        this.refAudioUrls.add(url);
        return this;
    }

    /**
     * 设置多个参考音频 URL（可变参数，fluent）。
     *
     * @param urls 音频 URL 数组，最多 3 个
     */
    public VideoOptions refAudioUrls(String... urls) {
        this.refAudioUrls = new ArrayList<>(Arrays.asList(urls));
        return this;
    }

    // ==================== 文件 / 网页链接（WAN 3.0） ====================

    /** @return 文件 URL（WAN 3.0 type=file） */
    public String getFileUrl() { return fileUrl; }

    /** 设置文件 URL（fluent）。支持 docx/xlsx/pptx/pdf/txt 等，不可与 link 同时使用 */
    public VideoOptions fileUrl(String v) { this.fileUrl = v; return this; }

    /** 设置文件 URL（setter） */
    public VideoOptions setFileUrl(String v) { this.fileUrl = v; return this; }

    /** @return 网页链接 URL（WAN 3.0 type=link） */
    public String getLinkUrl() { return linkUrl; }

    /** 设置网页链接 URL（fluent）。仅支持无需登录的公开网页，不可与 file 同时使用 */
    public VideoOptions linkUrl(String v) { this.linkUrl = v; return this; }

    /** 设置网页链接 URL（setter） */
    public VideoOptions setLinkUrl(String v) { this.linkUrl = v; return this; }

    // ==================== Kling 3.0 扩展 ====================

    /** @return 是否生成多镜头视频；null 沿用服务端默认 */
    public Boolean getMultiShot() { return multiShot; }

    /** 设置是否生成多镜头视频（fluent）。多镜头 prompt 格式：“镜头 n, m, words; 镜头 n, m, words;” */
    public VideoOptions multiShot(Boolean v) { this.multiShot = v; return this; }

    /** 设置是否生成多镜头视频（setter） */
    public VideoOptions setMultiShot(Boolean v) { this.multiShot = v; return this; }

    /** @return 人物朝向参考（"image" / "video"），动作控制专用 */
    public String getCharacterOrientation() { return characterOrientation; }

    /** 设置人物朝向参考（fluent）。"image" 与形象参考图一致，"video" 与动作参考视频一致 */
    public VideoOptions characterOrientation(String v) { this.characterOrientation = v; return this; }

    /** 设置人物朝向参考（setter） */
    public VideoOptions setCharacterOrientation(String v) { this.characterOrientation = v; return this; }

    /** @return 主体库主体 ID 列表 */
    public List<String> getRefElementIds() { return refElementIds; }

    /** 设置主体 ID 列表（fluent） */
    public VideoOptions refElementIds(List<String> v) { this.refElementIds = v; return this; }

    /** 设置主体 ID 列表（setter） */
    public VideoOptions setRefElementIds(List<String> v) { this.refElementIds = v; return this; }

    /**
     * 追加一个主体 ID。
     *
     * @param elementId 主体库主体 ID（由可灵主体管理 API 返回）
     */
    public VideoOptions addRefElementId(String elementId) {
        if (this.refElementIds == null) this.refElementIds = new ArrayList<>();
        this.refElementIds.add(elementId);
        return this;
    }

    /**
     * 设置主体 ID 列表（可变参数，fluent）。请求中以 @element_1、@element_2 ... 引用。
     *
     * @param ids 主体 ID 数组
     */
    public VideoOptions refElementIds(String... ids) {
        this.refElementIds = new ArrayList<>(Arrays.asList(ids));
        return this;
    }

    /** @return 待编辑视频 URL */
    public String getBaseVideoUrl() { return baseVideoUrl; }

    /** 设置待编辑视频 URL（fluent）。Kling 3.0 Omni 视频编辑（base_video） */
    public VideoOptions baseVideoUrl(String v) { this.baseVideoUrl = v; return this; }

    /** 设置待编辑视频 URL（setter） */
    public VideoOptions setBaseVideoUrl(String v) { this.baseVideoUrl = v; return this; }

    /** @return 是否保留参考视频原声 */
    public Boolean getKeepSourceAudio() { return keepSourceAudio; }

    /** 设置是否保留参考视频原声（fluent）。对应 Kling 3.0 audio=original，优先于 {@link #generateAudio(Boolean)} */
    public VideoOptions keepSourceAudio(Boolean v) { this.keepSourceAudio = v; return this; }

    /** 设置是否保留参考视频原声（setter） */
    public VideoOptions setKeepSourceAudio(Boolean v) { this.keepSourceAudio = v; return this; }

    // ==================== Seedance 2.5 扩展 ====================

    /** @return 输出视频格式（"mp4" / "mov"） */
    public String getOutputFormat() { return outputFormat; }

    /** 设置输出视频格式（fluent）。"mov" 采用专业编码（H.264+yuv444p+PCM），适用于调色/抠像/合成 */
    public VideoOptions outputFormat(String v) { this.outputFormat = v; return this; }

    /** 设置输出视频格式（setter） */
    public VideoOptions setOutputFormat(String v) { this.outputFormat = v; return this; }

    /** @return 全模态参考任务类型（"auto" / "reference" / "edit" / "extend"） */
    public String getOmniReferenceTaskType() { return omniReferenceTaskType; }

    /** 设置全模态参考任务类型（fluent）。"edit" 需 reference_video + ratio=adaptive + duration=-1；"extend" 需 reference_video + ratio=adaptive */
    public VideoOptions omniReferenceTaskType(String v) { this.omniReferenceTaskType = v; return this; }

    /** 设置全模态参考任务类型（setter） */
    public VideoOptions setOmniReferenceTaskType(String v) { this.omniReferenceTaskType = v; return this; }

    /** @return 执行优先级（0-9） */
    public Integer getPriority() { return priority; }

    /** 设置执行优先级（fluent）。数值越大优先级越高，同优先级按 FIFO 排序 */
    public VideoOptions priority(Integer v) { this.priority = v; return this; }

    /** 设置执行优先级（setter） */
    public VideoOptions setPriority(Integer v) { this.priority = v; return this; }

    /** @return 任务超时阈值（秒） */
    public Integer getExecutionExpiresAfter() { return executionExpiresAfter; }

    /** 设置任务超时阈值（fluent）。范围 [3600, 259200]，默认 172800（48 小时） */
    public VideoOptions executionExpiresAfter(Integer v) { this.executionExpiresAfter = v; return this; }

    /** 设置任务超时阈值（setter） */
    public VideoOptions setExecutionExpiresAfter(Integer v) { this.executionExpiresAfter = v; return this; }

    /** @return 视频帧数（Seedance 1.0 系列） */
    public Integer getFrames() { return frames; }

    /** 设置视频帧数（fluent）。范围 [29, 289]，需满足 25+4n 格式，与 duration 二选一 */
    public VideoOptions frames(Integer v) { this.frames = v; return this; }

    /** 设置视频帧数（setter） */
    public VideoOptions setFrames(Integer v) { this.frames = v; return this; }
}
