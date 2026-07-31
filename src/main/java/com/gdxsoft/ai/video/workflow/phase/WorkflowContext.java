package com.gdxsoft.ai.video.workflow.phase;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gdxsoft.ai.video.workflow.config.WorkflowConfig;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.model.Storyboard;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 工作流执行上下文 — 贯穿各 Phase 的共享状态。
 *
 * @since 1.4.0
 */
public class WorkflowContext {

    // ---- 基础参数 ----

    private long instanceId;
    private WorkflowConfig config;
    private WorkflowDb db;
    private RequestValue rv;

    /** 用户输入的原始故事文本 */
    private String userInput;

    /** 引擎进程 ID（用于日志和 DB 标识） */
    private String engineId;

    // ---- Phase 输出（各 Phase 完成后设置，后续 Phase 读取） ----

    /** Phase1 输出 */
    private Storyboard storyboard;

    /** Phase2 输出：素材资产 Map (refKey → {url, localPath}) */
    private final Map<String, ImageAsset> imageAssets = new LinkedHashMap<>();

    /** Phase3 输出：分镜视频 Map (shotIndex → {videoUrl, lastFrameUrl, localPath}) */
    private final Map<Integer, ShotAsset> shotAssets = new LinkedHashMap<>();

    /** Phase4 输出 */
    private String finalVideoUrl;
    private String finalVideoPath;

    /** 错误信息 */
    private String error;

    /** API 配置缓存 (providerName → {url, key}) */
    private final Map<String, ApiCredential> apiCredentials = new LinkedHashMap<>();

    // ===== 内部类 =====

    public static class ImageAsset {
        private String url;
        private String localPath;
        public String getUrl() { return url; }
        public void setUrl(String v) { this.url = v; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String v) { this.localPath = v; }
    }

    public static class ShotAsset {
        private String videoUrl;
        private String lastFrameUrl;
        private String localPath;
        public String getVideoUrl() { return videoUrl; }
        public void setVideoUrl(String v) { this.videoUrl = v; }
        public String getLastFrameUrl() { return lastFrameUrl; }
        public void setLastFrameUrl(String v) { this.lastFrameUrl = v; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String v) { this.localPath = v; }
    }

    public static class ApiCredential {
        private String url;
        private String key;
        public String getUrl() { return url; }
        public void setUrl(String v) { this.url = v; }
        public String getKey() { return key; }
        public void setKey(String v) { this.key = v; }
    }

    // ===== 构造 =====

    public WorkflowContext(long instanceId, WorkflowConfig config, WorkflowDb db,
                           String userInput, String engineId) {
        this.instanceId = instanceId;
        this.config = config;
        this.db = db;
        this.userInput = userInput;
        this.engineId = engineId;
    }

    // ===== API 凭证 =====

    public void putApiCredential(String provider, String url, String key) {
        ApiCredential c = new ApiCredential();
        c.setUrl(url);
        c.setKey(key);
        apiCredentials.put(provider, c);
    }

    public ApiCredential getApiCredential(String provider) {
        return apiCredentials.get(provider);
    }

    // ===== 素材 =====

    public void putImageAsset(String refKey, String url, String localPath) {
        ImageAsset a = new ImageAsset();
        a.setUrl(url);
        a.setLocalPath(localPath);
        imageAssets.put(refKey, a);
    }

    public ImageAsset getImageAsset(String refKey) {
        return imageAssets.get(refKey);
    }

    // ===== 分镜视频 =====

    public void putShotAsset(int shotIndex, String videoUrl, String lastFrameUrl, String localPath) {
        ShotAsset a = new ShotAsset();
        a.setVideoUrl(videoUrl);
        a.setLastFrameUrl(lastFrameUrl);
        a.setLocalPath(localPath);
        shotAssets.put(shotIndex, a);
    }

    public ShotAsset getShotAsset(int shotIndex) {
        return shotAssets.get(shotIndex);
    }

    public int getCompletedShotCount() { return shotAssets.size(); }

    // ===== Getters / Setters =====

    public long getInstanceId() { return instanceId; }
    public WorkflowConfig getConfig() { return config; }
    public WorkflowDb getDb() { return db; }
    public void setDb(WorkflowDb v) { this.db = v; }
    public RequestValue getRv() { return rv; }
    public void setRv(RequestValue v) { this.rv = v; }
    public String getUserInput() { return userInput; }
    public String getEngineId() { return engineId; }
    public Storyboard getStoryboard() { return storyboard; }
    public void setStoryboard(Storyboard v) { this.storyboard = v; }
    public String getFinalVideoUrl() { return finalVideoUrl; }
    public void setFinalVideoUrl(String v) { this.finalVideoUrl = v; }
    public String getFinalVideoPath() { return finalVideoPath; }
    public void setFinalVideoPath(String v) { this.finalVideoPath = v; }
    public String getError() { return error; }
    public void setError(String v) { this.error = v; }
    public boolean hasError() { return error != null && !error.isEmpty(); }
}
