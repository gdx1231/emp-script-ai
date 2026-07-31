package com.gdxsoft.ai.video.workflow.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作流配置（解析 workflow.json）。
 *
 * <p>典型用法：
 * <pre>{@code
 * WorkflowConfig cfg = WorkflowConfig.load(Paths.get("workflow.json"));
 * LimitsConfig limits = cfg.getLimits();
 * PhaseDef planning = cfg.getPhase("planning");
 * }</pre>
 *
 * <p>支持从 DB 加载已注册的工作流定义 JSON 字符串：
 * <pre>{@code
 * WorkflowConfig cfg = WorkflowConfig.loadFromJson(dbJson);
 * }</pre>
 *
 * @since 1.4.0
 */
public class WorkflowConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowConfig.class);

    // ---- 元数据 ----

    private String name;
    private String description;
    private String version;

    // ---- 原始 JSON ----

    private String rawJson;
    private String md5;

    // ---- 子配置 ----

    private LimitsConfig limits;
    private final List<PhaseDef> phases = new ArrayList<>();
    private final Map<String, PhaseDef> phaseMap = new LinkedHashMap<>();

    // ---- output 节点 ----

    private String outputBaseDir;
    private String dbConfigName;
    private String ffmpegPath;

    // ===== 工厂方法 =====

    /**
     * 从文件加载。
     *
     * @param configPath workflow.json 文件路径
     * @return 解析后的配置
     * @throws IOException 文件不存在或 JSON 格式错误
     */
    public static WorkflowConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("workflow.json 不存在: " + configPath);
        }
        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        LOGGER.info("加载 workflow.json: {}", configPath);
        return loadFromJson(json);
    }

    /**
     * 从 JSON 字符串加载。
     *
     * @param json workflow.json 内容
     * @return 解析后的配置
     * @throws IOException JSON 格式错误
     */
    public static WorkflowConfig loadFromJson(String json) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            WorkflowConfig cfg = new WorkflowConfig();
            cfg.rawJson = json;
            cfg.md5 = md5(json);

            // 元数据
            cfg.name = root.optString("name", "unnamed");
            cfg.description = root.optString("description", "");
            cfg.version = root.optString("version", "1.0");

            // limits
            if (root.has("limits")) {
                cfg.limits = parseLimits(root.getJSONObject("limits"));
            } else {
                cfg.limits = new LimitsConfig();
            }

            // phases
            JSONArray arr = root.optJSONArray("phases");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    PhaseDef phase = parsePhase(arr.getJSONObject(i));
                    cfg.phases.add(phase);
                    cfg.phaseMap.put(phase.getName(), phase);
                }
            }

            // output
            if (root.has("output")) {
                JSONObject out = root.getJSONObject("output");
                cfg.outputBaseDir = out.optString("baseDir", ".");
                cfg.dbConfigName = out.optString("dbConfigName", "default");
                cfg.ffmpegPath = out.optString("ffmpegPath", "ffmpeg");
            }

            LOGGER.info("工作流配置解析完成: name={}, phases={}, limits(wf={},img={},video={})",
                    cfg.name, cfg.phases.size(),
                    cfg.limits.getMaxConcurrentWorkflows(),
                    cfg.limits.getMaxConcurrentImageGen(),
                    cfg.limits.getMaxConcurrentVideoGen());

            return cfg;
        } catch (Exception e) {
            throw new IOException("workflow.json 解析失败: " + e.getMessage(), e);
        }
    }

    // ===== 阶段查询 =====

    /** 按名称查询阶段 */
    public PhaseDef getPhase(String name) {
        return phaseMap.get(name);
    }

    /** 查询第一个指定类型的阶段（如 "video_generation"） */
    public PhaseDef getPhaseByType(String type) {
        for (PhaseDef p : phases) {
            if (type.equals(p.getType())) return p;
        }
        return null;
    }

    /** 获取阶段下标（从 0 开始），用于进度计算 */
    public int getPhaseIndex(String name) {
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /** 获取某阶段的前置阶段名（返回 null 表示无依赖，即首阶段） */
    public String getDependsOn(String phaseName) {
        PhaseDef p = getPhase(phaseName);
        return p != null ? p.getDependsOn() : null;
    }

    /** 计算进度百分比（基于阶段下标 + 阶段内任务进度） */
    public int calcProgress(String currentPhase, double phaseFraction) {
        int idx = getPhaseIndex(currentPhase);
        if (idx < 0 || phases.isEmpty()) return 0;
        double total = phases.size();
        double progress = (idx + phaseFraction) / total * 100;
        return Math.min(99, (int) progress);
    }

    // ===== Getters =====

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public String getRawJson() { return rawJson; }
    public String getMd5() { return md5; }
    public LimitsConfig getLimits() { return limits; }
    public List<PhaseDef> getPhases() { return phases; }
    public int getPhaseCount() { return phases.size(); }

    public String getOutputBaseDir() { return outputBaseDir; }
    public void setOutputBaseDir(String v) { this.outputBaseDir = v; }

    public String getDbConfigName() { return dbConfigName; }
    public void setDbConfigName(String v) { this.dbConfigName = v; }

    public String getFfmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String v) { this.ffmpegPath = v; }

    // ===== private helpers =====

    private static LimitsConfig parseLimits(JSONObject j) {
        LimitsConfig l = new LimitsConfig();
        if (j.has("maxConcurrentWorkflows")) l.setMaxConcurrentWorkflows(j.getInt("maxConcurrentWorkflows"));
        if (j.has("maxConcurrentImageGen"))  l.setMaxConcurrentImageGen(j.getInt("maxConcurrentImageGen"));
        if (j.has("maxConcurrentVideoGen"))  l.setMaxConcurrentVideoGen(j.getInt("maxConcurrentVideoGen"));
        if (j.has("maxRetries"))             l.setMaxRetries(j.getInt("maxRetries"));
        if (j.has("retryDelayMs"))           l.setRetryDelayMs(j.getLong("retryDelayMs"));
        if (j.has("taskTimeoutMs"))          l.setTaskTimeoutMs(j.getLong("taskTimeoutMs"));
        if (j.has("pollIntervalMs"))         l.setPollIntervalMs(j.getLong("pollIntervalMs"));
        return l;
    }

    private static PhaseDef parsePhase(JSONObject j) {
        PhaseDef p = new PhaseDef();
        p.setName(j.optString("name"));
        p.setType(j.optString("type"));
        p.setDependsOn(j.optString("dependsOn", null));
        p.setProvider(j.optString("provider", null));
        p.setModel(j.optString("model", null));
        if (j.has("concurrency")) p.setConcurrency(j.getInt("concurrency"));

        // llm_storyboard
        p.setPromptTemplate(j.optString("promptTemplate", null));
        p.setResponseFormat(j.optString("responseFormat", null));

        // image_generation
        p.setSize(j.optString("size", null));

        // video_generation
        if (j.has("chainShots")) p.setChainShots(j.getBoolean("chainShots"));
        if (j.has("returnLastFrame")) p.setReturnLastFrame(j.getBoolean("returnLastFrame"));
        if (j.has("maxDuration")) p.setMaxDuration(j.getInt("maxDuration"));
        p.setDefaultAspectRatio(j.optString("defaultAspectRatio", null));
        p.setDefaultResolution(j.optString("defaultResolution", null));
        if (j.has("generateAudio")) p.setGenerateAudio(j.getBoolean("generateAudio"));

        // ffmpeg 子参数
        if (j.has("ffmpeg")) {
            JSONObject ffmpegJson = j.getJSONObject("ffmpeg");
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : ffmpegJson.keySet()) {
                map.put(key, ffmpegJson.get(key));
            }
            p.setFfmpeg(map);
        }

        return p;
    }

    private static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
