package com.gdxsoft.ai.video.workflow.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个阶段定义，对应 workflow.json phases 数组中的一项。
 *
 * <p>阶段类型：
 * <ul>
 *   <li>{@code llm_storyboard} — 调用 LLM 拆分分镜</li>
 *   <li>{@code image_generation} — 批量生成图片素材</li>
 *   <li>{@code video_generation} — 逐分镜生成视频</li>
 *   <li>{@code video_compose} — ffmpeg 拼接合成</li>
 *   <li>{@code tts} — TTS 语音合成（可选）</li>
 * </ul>
 *
 * @since 1.4.0
 */
public class PhaseDef {

    // ---- 通用字段 ----

    /** 阶段名 */
    private String name;

    /** 阶段类型 */
    private String type;

    /** 依赖的前置阶段名（null 表示无依赖） */
    private String dependsOn;

    /** 供应商标识 */
    private String provider;

    /** 模型标识 */
    private String model;

    /** 该阶段内部的并发数 */
    private int concurrency = 1;

    // ---- llm_storyboard 专用 ----

    /** 提示词模板（支持 @{input} 占位符） */
    private String promptTemplate;

    /** LLM 响应格式 (text / json_object) */
    private String responseFormat;

    // ---- image_generation 专用 ----

    /** 图片尺寸 (如 "1024x1024") */
    private String size;

    // ---- video_generation 专用 ----

    /** 是否启用尾帧续接（Shot N 尾帧 = Shot N+1 首帧） */
    private boolean chainShots = true;

    /** 是否请求返回尾帧图 */
    private boolean returnLastFrame = true;

    /** 分镜最大时长（秒），默认 15 */
    private int maxDuration = 15;

    /** 默认宽高比 */
    private String defaultAspectRatio;

    /** 默认分辨率 */
    private String defaultResolution;

    /** 是否生成音频 */
    private boolean generateAudio;

    // ---- video_compose 专用 ----

    /** ffmpeg 合成参数 */
    private Map<String, Object> ffmpeg;

    // ===== Getters / Setters =====

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getDependsOn() { return dependsOn; }
    public void setDependsOn(String v) { this.dependsOn = v; }

    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }

    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }

    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int v) { this.concurrency = v; }

    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String v) { this.promptTemplate = v; }

    public String getResponseFormat() { return responseFormat; }
    public void setResponseFormat(String v) { this.responseFormat = v; }

    public String getSize() { return size; }
    public void setSize(String v) { this.size = v; }

    public boolean isChainShots() { return chainShots; }
    public void setChainShots(boolean v) { this.chainShots = v; }

    public boolean isReturnLastFrame() { return returnLastFrame; }
    public void setReturnLastFrame(boolean v) { this.returnLastFrame = v; }

    public int getMaxDuration() { return maxDuration; }
    public void setMaxDuration(int v) { this.maxDuration = v; }

    public String getDefaultAspectRatio() { return defaultAspectRatio; }
    public void setDefaultAspectRatio(String v) { this.defaultAspectRatio = v; }

    public String getDefaultResolution() { return defaultResolution; }
    public void setDefaultResolution(String v) { this.defaultResolution = v; }

    public boolean isGenerateAudio() { return generateAudio; }
    public void setGenerateAudio(boolean v) { this.generateAudio = v; }

    public Map<String, Object> getFfmpeg() { return ffmpeg; }
    public void setFfmpeg(Map<String, Object> v) { this.ffmpeg = v; }

    // ===== ffmpeg 便捷方法 =====

    /** 获取 ffmpeg 子参数（字符串） */
    public String getFfmpegString(String key) {
        if (ffmpeg == null) return null;
        Object v = ffmpeg.get(key);
        return v != null ? v.toString() : null;
    }

    /** 获取 ffmpeg 子参数（整数） */
    public int getFfmpegInt(String key, int defaultValue) {
        if (ffmpeg == null) return defaultValue;
        Object v = ffmpeg.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return defaultValue;
    }

    /** 获取 ffmpeg 子参数（布尔） */
    public boolean getFfmpegBoolean(String key, boolean defaultValue) {
        if (ffmpeg == null) return defaultValue;
        Object v = ffmpeg.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return defaultValue;
    }

    /** 获取 ffmpeg 子参数（浮点） */
    public double getFfmpegDouble(String key, double defaultValue) {
        if (ffmpeg == null) return defaultValue;
        Object v = ffmpeg.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return defaultValue;
    }
}
