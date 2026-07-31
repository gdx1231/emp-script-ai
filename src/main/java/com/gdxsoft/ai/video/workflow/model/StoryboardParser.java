package com.gdxsoft.ai.video.workflow.model;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分镜脚本解析器 — 将 LLM 输出的 JSON 解析为 {@link Storyboard}。
 *
 * <p>容忍 markdown 代码围栏（{@code ```json ... ```}）和前后杂文本。
 *
 * <p>校验规则：
 * <ul>
 *   <li>每个 shot duration ≤ 15 秒</li>
 *   <li>shot 引用的 characterRefs 必须存在于 characters 列表</li>
 *   <li>shot 引用的 environmentRef 必须存在于 environments 列表</li>
 *   <li>至少需要一个 character 或 environment</li>
 *   <li>至少需要一个 shot</li>
 * </ul>
 *
 * @since 1.4.0
 */
public class StoryboardParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryboardParser.class);

    /**
     * 从 LLM 原始输出文本解析 Storyboard。
     *
     * @param llmOutput LLM 完整响应文本
     * @return 解析后的 Storyboard
     * @throws StoryboardParseException JSON 格式错误或校验不通过
     */
    public Storyboard parse(String llmOutput) throws StoryboardParseException {
        if (llmOutput == null || llmOutput.trim().isEmpty()) {
            throw new StoryboardParseException("LLM 输出为空");
        }

        JSONObject json = extractJson(llmOutput);
        if (json == null) {
            throw new StoryboardParseException("无法从 LLM 输出中解析 JSON");
        }

        Storyboard sb = new Storyboard();
        sb.setTitle(json.optString("title", "未命名"));
        sb.setSynopsis(json.optString("synopsis", json.optString("description", "")));

        // 解析人物
        JSONArray characters = json.optJSONArray("characters");
        if (characters != null) {
            for (int i = 0; i < characters.length(); i++) {
                JSONObject cj = characters.getJSONObject(i);
                Storyboard.StoryCharacter c = new Storyboard.StoryCharacter();
                c.setName(cj.optString("name", "角色" + i));
                c.setDescription(cj.optString("description", ""));
                c.setImgPrompt(cj.optString("imgPrompt", ""));

                if (c.getImgPrompt().isEmpty()) {
                    throw new StoryboardParseException("角色 '" + c.getName() + "' 缺少 imgPrompt（图片生成提示词）");
                }
                sb.getCharacters().add(c);
            }
        }

        // 解析环境
        JSONArray environments = json.optJSONArray("environments");
        if (environments != null) {
            for (int i = 0; i < environments.length(); i++) {
                JSONObject ej = environments.getJSONObject(i);
                Storyboard.StoryEnvironment e = new Storyboard.StoryEnvironment();
                e.setName(ej.optString("name", "场景" + i));
                e.setDescription(ej.optString("description", ""));
                e.setImgPrompt(ej.optString("imgPrompt", ""));

                if (e.getImgPrompt().isEmpty()) {
                    throw new StoryboardParseException("场景 '" + e.getName() + "' 缺少 imgPrompt（图片生成提示词）");
                }
                sb.getEnvironments().add(e);
            }
        }

        // 必须至少有一个人物或环境
        if (sb.getCharacters().isEmpty() && sb.getEnvironments().isEmpty()) {
            throw new StoryboardParseException("分镜脚本至少需要有一个人物或环境");
        }

        // 解析分镜
        JSONArray shots = json.optJSONArray("shots");
        if (shots == null || shots.isEmpty()) {
            throw new StoryboardParseException("分镜脚本缺少 shots 列表");
        }

        for (int i = 0; i < shots.length(); i++) {
            JSONObject sj = shots.getJSONObject(i);
            Storyboard.StoryShot s = new Storyboard.StoryShot();

            s.setIndex(sj.optInt("index", i + 1));
            s.setDescription(sj.optString("description", ""));
            s.setVideoPrompt(sj.optString("videoPrompt", ""));

            if (s.getVideoPrompt().isEmpty()) {
                throw new StoryboardParseException("分镜 " + s.getIndex() + " 缺少 videoPrompt（视频生成提示词）");
            }

            s.setDuration(sj.optInt("duration", 5));
            s.setAspectRatio(sj.optString("aspectRatio", null));
            s.setResolution(sj.optString("resolution", null));
            s.setCameraMovement(sj.optString("cameraMovement", null));
            s.setDialogue(sj.optString("dialogue", null));
            s.setNarration(sj.optString("narration", null));

            // 时长校验
            if (s.getDuration() > 15) {
                LOGGER.warn("分镜 {} duration={}s 超过 15s，已截断", s.getIndex(), s.getDuration());
                s.setDuration(15);
            }

            // 角色引用
            JSONArray refs = sj.optJSONArray("characterRefs");
            if (refs != null) {
                s.setCharacterRefs(new ArrayList<>());
                for (int j = 0; j < refs.length(); j++) {
                    String refName = refs.getString(j);
                    if (sb.findCharacter(refName) == null) {
                        throw new StoryboardParseException(
                                "分镜 " + s.getIndex() + " 引用了不存在的角色: " + refName);
                    }
                    s.getCharacterRefs().add(refName);
                }
            }

            // 环境引用
            String envRef = sj.optString("environmentRef", null);
            if (envRef != null && !envRef.isEmpty()) {
                if (sb.findEnvironment(envRef) == null) {
                    throw new StoryboardParseException(
                            "分镜 " + s.getIndex() + " 引用了不存在的场景: " + envRef);
                }
                s.setEnvironmentRef(envRef);
            }

            sb.getShots().add(s);
        }

        LOGGER.info("分镜解析完成: {} characters, {} environments, {} shots",
                sb.getCharacterCount(), sb.getEnvironmentCount(), sb.getShotCount());
        return sb;
    }

    /**
     * 从文本中提取 JSON 对象。
     * <p>先直接解析；失败则截取首个 {@code {} } 之间的内容重试；
     * 同时处理 {@code ```json ... ```} 围栏。
     */
    JSONObject extractJson(String rawText) throws StoryboardParseException {
        String text = rawText.trim();

        // 移除 markdown 代码围栏
        text = stripMarkdownFence(text);

        // 提取首个 { ... } 之间的内容
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new StoryboardParseException("LLM 输出中未找到 JSON 对象");
        }

        text = text.substring(start, end + 1);

        try {
            return new JSONObject(text);
        } catch (Exception e) {
            throw new StoryboardParseException("JSON 格式错误: " + e.getMessage());
        }
    }

    private String stripMarkdownFence(String text) {
        String t = text.trim();
        // ```json ... ``` 或 ``` ... ```
        if (t.startsWith("```")) {
            int newline = t.indexOf('\n');
            if (newline > 0) {
                t = t.substring(newline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }

    /** 校验 storyboard 完整性 */
    public void validate(Storyboard sb) throws StoryboardParseException {
        if (sb.getCharacters().isEmpty() && sb.getEnvironments().isEmpty()) {
            throw new StoryboardParseException("没有人物或环境");
        }
        if (sb.getShots().isEmpty()) {
            throw new StoryboardParseException("没有分镜");
        }
        for (Storyboard.StoryShot s : sb.getShots()) {
            if (s.getVideoPrompt() == null || s.getVideoPrompt().trim().isEmpty()) {
                throw new StoryboardParseException("分镜 " + s.getIndex() + " 缺少 videoPrompt");
            }
            if (s.getDuration() > 15) {
                throw new StoryboardParseException("分镜 " + s.getIndex() + " duration=" + s.getDuration() + " 超过 15s");
            }
        }
    }

    /**
     * 解析异常。
     */
    public static class StoryboardParseException extends Exception {
        public StoryboardParseException(String message) {
            super(message);
        }
    }
}
