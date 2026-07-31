package com.gdxsoft.ai.video.workflow.model;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 分镜脚本 — LLM 分镜拆解的输出。
 *
 * <p>包含人物、环境、分镜三个列表。生成图片/视频时按引用关系关联。
 *
 * <p>JSON 结构：
 * <pre>{@code
 * {
 *   "title": "咖啡店的一天",
 *   "characters": [{"name":"小明","description":"年轻男子","imgPrompt":"..."}],
 *   "environments": [{"name":"咖啡店","description":"温馨角落","imgPrompt":"..."}],
 *   "shots": [{"index":1,"description":"...","videoPrompt":"...","duration":5,...}]
 * }
 * }</pre>
 *
 * @since 1.4.0
 */
public class Storyboard {

    private String title;
    private String synopsis;
    private final List<StoryCharacter> characters = new ArrayList<>();
    private final List<StoryEnvironment> environments = new ArrayList<>();
    private final List<StoryShot> shots = new ArrayList<>();

    // ===== 内部类 =====

    public static class StoryCharacter {
        private String name;
        private String description;
        private String imgPrompt;
        private String imageUrl;    // 生成后回填

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public String getImgPrompt() { return imgPrompt; }
        public void setImgPrompt(String v) { this.imgPrompt = v; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String v) { this.imageUrl = v; }
    }

    public static class StoryEnvironment {
        private String name;
        private String description;
        private String imgPrompt;
        private String imageUrl;    // 生成后回填

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public String getImgPrompt() { return imgPrompt; }
        public void setImgPrompt(String v) { this.imgPrompt = v; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String v) { this.imageUrl = v; }
    }

    public static class StoryShot {
        private int index;
        private String description;     // 中文描述
        private String videoPrompt;     // 英文视频生成提示词
        private int duration = 5;       // 秒，≤ 15
        private String aspectRatio;     // 如 "16:9", null=default
        private String resolution;      // 如 "720p", null=default
        private String cameraMovement;  // 运镜方式
        private List<String> characterRefs;  // 引用 characters 的 name
        private String environmentRef;       // 引用 environments 的 name
        private String dialogue;        // 台词（可选）
        private String narration;       // 旁白（可选）
        private String videoUrl;        // 生成后回填

        public int getIndex() { return index; }
        public void setIndex(int v) { this.index = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public String getVideoPrompt() { return videoPrompt; }
        public void setVideoPrompt(String v) { this.videoPrompt = v; }
        public int getDuration() { return duration; }
        public void setDuration(int v) { this.duration = v; }
        public String getAspectRatio() { return aspectRatio; }
        public void setAspectRatio(String v) { this.aspectRatio = v; }
        public String getResolution() { return resolution; }
        public void setResolution(String v) { this.resolution = v; }
        public String getCameraMovement() { return cameraMovement; }
        public void setCameraMovement(String v) { this.cameraMovement = v; }
        public List<String> getCharacterRefs() { return characterRefs; }
        public void setCharacterRefs(List<String> v) { this.characterRefs = v; }
        public String getEnvironmentRef() { return environmentRef; }
        public void setEnvironmentRef(String v) { this.environmentRef = v; }
        public String getDialogue() { return dialogue; }
        public void setDialogue(String v) { this.dialogue = v; }
        public String getNarration() { return narration; }
        public void setNarration(String v) { this.narration = v; }
        public String getVideoUrl() { return videoUrl; }
        public void setVideoUrl(String v) { this.videoUrl = v; }
    }

    // ===== Getters / Setters =====

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String v) { this.synopsis = v; }
    public List<StoryCharacter> getCharacters() { return characters; }
    public List<StoryEnvironment> getEnvironments() { return environments; }
    public List<StoryShot> getShots() { return shots; }

    // ===== 查找方法 =====

    public StoryCharacter findCharacter(String name) {
        for (StoryCharacter c : characters) {
            if (c.getName().equals(name)) return c;
        }
        return null;
    }

    public StoryEnvironment findEnvironment(String name) {
        for (StoryEnvironment e : environments) {
            if (e.getName().equals(name)) return e;
        }
        return null;
    }

    public StoryShot getShot(int index) {
        for (StoryShot s : shots) {
            if (s.getIndex() == index) return s;
        }
        return null;
    }

    public int getShotCount() { return shots.size(); }
    public int getCharacterCount() { return characters.size(); }
    public int getEnvironmentCount() { return environments.size(); }

    /** 总素材数（人物图 + 环境图） */
    public int getTotalMaterialCount() { return characters.size() + environments.size(); }

    // ===== JSON 序列化 =====

    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        j.put("title", title != null ? title : "");
        j.put("synopsis", synopsis != null ? synopsis : "");

        JSONArray ca = new JSONArray();
        for (StoryCharacter c : characters) {
            JSONObject cj = new JSONObject();
            cj.put("name", c.getName()); cj.put("description", c.getDescription());
            cj.put("imgPrompt", c.getImgPrompt());
            if (c.getImageUrl() != null) cj.put("imageUrl", c.getImageUrl());
            ca.put(cj);
        }
        j.put("characters", ca);

        JSONArray ea = new JSONArray();
        for (StoryEnvironment e : environments) {
            JSONObject ej = new JSONObject();
            ej.put("name", e.getName()); ej.put("description", e.getDescription());
            ej.put("imgPrompt", e.getImgPrompt());
            if (e.getImageUrl() != null) ej.put("imageUrl", e.getImageUrl());
            ea.put(ej);
        }
        j.put("environments", ea);

        JSONArray sa = new JSONArray();
        for (StoryShot s : shots) {
            JSONObject sj = new JSONObject();
            sj.put("index", s.getIndex()); sj.put("description", s.getDescription());
            sj.put("videoPrompt", s.getVideoPrompt()); sj.put("duration", s.getDuration());
            if (s.getAspectRatio() != null) sj.put("aspectRatio", s.getAspectRatio());
            if (s.getResolution() != null) sj.put("resolution", s.getResolution());
            if (s.getCameraMovement() != null) sj.put("cameraMovement", s.getCameraMovement());
            if (s.getCharacterRefs() != null) sj.put("characterRefs", s.getCharacterRefs());
            if (s.getEnvironmentRef() != null) sj.put("environmentRef", s.getEnvironmentRef());
            if (s.getDialogue() != null) sj.put("dialogue", s.getDialogue());
            if (s.getNarration() != null) sj.put("narration", s.getNarration());
            if (s.getVideoUrl() != null) sj.put("videoUrl", s.getVideoUrl());
            sa.put(sj);
        }
        j.put("shots", sa);
        return j;
    }

    @Override
    public String toString() { return toJson().toString(2); }
}
