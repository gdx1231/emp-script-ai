package com.gdxsoft.ai.video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 视频提示词构建器 —— 有状态、链式 API。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>素材引用自动编号：添加参考图片/视频/音频时自动分配引用名（如"图1""视频1"），
 *       命名格式通过 {@link MediaRefStyle} 按 provider 配置</li>
 *   <li>提示词长度校验 + 截断：按模型上限 warn + 截断</li>
 *   <li>多镜头分镜格式：Kling 3.0 的 {@code 镜头 n, m, words;} 格式</li>
 *   <li>参数编码：Jimeng/Seedance 弱校验方式的 {@code --flag value} 追加</li>
 *   <li>有声对话格式：Seedance 的 {@code 角色说："台词"} 格式</li>
 * </ul>
 * <p>
 * 典型用法：
 * <pre>{@code
 * VideoPromptBuilder b = VideoPromptBuilder.forWan3();
 * b.prompt("视频1抱着图3，在图4的椅子上弹奏");
 * b.refImage(url1);  // → "图1"
 * b.refVideo(url2);  // → "视频1"
 * b.refImage(url3);  // → "图2"
 * String finalPrompt = b.buildPrompt();
 * }</pre>
 *
 * @since 1.3.0
 */
public class VideoPromptBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoPromptBuilder.class);

    // ==================== 各模型提示词长度上限 ====================

    /** CogVideoX 系列：512 字符 */
    public static final int MAX_COGVIDEOX = 512;
    /** Kling 3.0：3072 字符（建议 ≤2500） */
    public static final int MAX_KLING = 3072;
    /** Kling 3.0 建议上限 */
    public static final int MAX_KLING_RECOMMENDED = 2500;
    /** MiniMax H3：7000 字符 */
    public static final int MAX_MINIMAX = 7000;
    /** WAN 3.0：20000 字符 */
    public static final int MAX_WAN3 = 20000;
    /** HappyHorse 中文：2500 字符 */
    public static final int MAX_HAPPYHORSE_ZH = 2500;
    /** HappyHorse 非中文：5000 字符 */
    public static final int MAX_HAPPYHORSE = 5000;
    /** Seedance 中文：500 字 */
    public static final int MAX_SEEDANCE_ZH = 500;
    /** Seedance 英文：1000 词 */
    public static final int MAX_SEEDANCE_EN_WORDS = 1000;

    // ==================== 素材引用命名风格 ====================

    /**
     * 素材引用名的命名策略。各 provider 的引用格式不同：
     * <ul>
     *   <li>Wan3.0: 图1 / 视频1 / 音频1</li>
     *   <li>Seedance: 图片1 / 视频1 / 音频1</li>
     *   <li>Kling: image_1 / video_1 / element_1</li>
     * </ul>
     */
    @FunctionalInterface
    public interface MediaRefStyle {
        /**
         * 生成引用名。
         *
         * @param type 素材类型：image / video / audio / element
         * @param idx  1-based 序号（同类型内递增）
         * @return 引用名字符串
         */
        String format(String type, int idx);
    }

    /** Wan3.0 风格：图1 / 视频1 / 音频1 */
    public static final MediaRefStyle STYLE_WAN3 = (type, idx) -> {
        switch (type) {
            case "image": return "图" + idx;
            case "video": return "视频" + idx;
            case "audio": return "音频" + idx;
            default: return type + idx;
        }
    };

    /** Seedance 风格：图片1 / 视频1 / 音频1 */
    public static final MediaRefStyle STYLE_SEEDANCE = (type, idx) -> {
        switch (type) {
            case "image": return "图片" + idx;
            case "video": return "视频" + idx;
            case "audio": return "音频" + idx;
            default: return type + idx;
        }
    };

    /** Kling 风格：image_1 / video_1 / element_1 */
    public static final MediaRefStyle STYLE_KLING = (type, idx) -> type + "_" + idx;

    /** 无引用风格（MiniMax / CogVideoX 等不需要在 prompt 中引用素材） */
    public static final MediaRefStyle STYLE_NONE = (type, idx) -> null;

    // ==================== 实例字段 ====================

    private final MediaRefStyle refStyle;

    private String prompt = "";
    private String negativePrompt;
    private int maxLength = Integer.MAX_VALUE;

    // 素材引用计数（每类型独立）
    private int imageCounter = 0;
    private int videoCounter = 0;
    private int audioCounter = 0;
    private int elementCounter = 0;

    /** url → 引用名（保持插入顺序） */
    private final Map<String, String> refNameMap = new LinkedHashMap<>();

    /** url → 标签描述（用于 buildMediaRefNote） */
    private final Map<String, String> refNameLabels = new LinkedHashMap<>();

    /** 角色名 → 图片引用名（用于 prompt 内联替换） */
    private final Map<String, String> characterMapping = new LinkedHashMap<>();

    // 多镜头分镜
    private final List<Shot> shots = new ArrayList<>();

    // 参数编码（--flag 格式）
    private final StringBuilder paramBuffer = new StringBuilder();

    // 追加内容（对话、自定义文本等）
    private final StringBuilder appendBuffer = new StringBuilder();

    // ==================== 构造 ====================

    public VideoPromptBuilder(MediaRefStyle refStyle) {
        this.refStyle = refStyle != null ? refStyle : STYLE_NONE;
    }

    // ==================== 静态工厂 ====================

    /** Wan3.0：图N / 视频N / 音频N，长度上限 20000 */
    public static VideoPromptBuilder forWan3() {
        return new VideoPromptBuilder(STYLE_WAN3).maxLength(MAX_WAN3);
    }

    /** Seedance（Doubao）：图片N / 视频N / 音频N，长度上限 500 中文字 */
    public static VideoPromptBuilder forSeedance() {
        return new VideoPromptBuilder(STYLE_SEEDANCE).maxLength(MAX_SEEDANCE_ZH);
    }

    /** Kling 3.0：image_N / video_N / element_N，长度上限 3072 */
    public static VideoPromptBuilder forKling() {
        return new VideoPromptBuilder(STYLE_KLING).maxLength(MAX_KLING);
    }

    /** MiniMax H3：无引用格式，长度上限 7000 */
    public static VideoPromptBuilder forMiniMax() {
        return new VideoPromptBuilder(STYLE_NONE).maxLength(MAX_MINIMAX);
    }

    /** CogVideoX：无引用格式，长度上限 512 */
    public static VideoPromptBuilder forCogVideoX() {
        return new VideoPromptBuilder(STYLE_NONE).maxLength(MAX_COGVIDEOX);
    }

    /** Jimeng（Seedance 1.0 旧接口）：图片N 风格 + 默认启用参数编码 */
    public static VideoPromptBuilder forJimeng() {
        return new VideoPromptBuilder(STYLE_SEEDANCE).maxLength(MAX_SEEDANCE_ZH);
    }

    /** HappyHorse：无引用格式，中文上限 2500 */
    public static VideoPromptBuilder forHappyHorse() {
        return new VideoPromptBuilder(STYLE_NONE).maxLength(MAX_HAPPYHORSE_ZH);
    }

    // ==================== 提示词 ====================

    /**
     * 设置基础提示词。
     *
     * @param text 提示词文本
     * @return this
     */
    public VideoPromptBuilder prompt(String text) {
        this.prompt = text != null ? text : "";
        return this;
    }

    /**
     * 设置负向提示词。
     *
     * @param text 负向提示词
     * @return this
     */
    public VideoPromptBuilder negativePrompt(String text) {
        this.negativePrompt = text;
        return this;
    }

    /** @return 当前基础提示词（不含追加内容和参数编码） */
    public String getPrompt() {
        return prompt;
    }

    // ==================== 长度校验 ====================

    /**
     * 设置提示词最大长度。
     *
     * @param max 最大字符数
     * @return this
     */
    public VideoPromptBuilder maxLength(int max) {
        this.maxLength = max;
        return this;
    }

    /** @return 当前最大长度限制 */
    public int getMaxLength() {
        return maxLength;
    }

    /**
     * 校验提示词长度，超限时 warn 并截断。
     * 应在 {@link #buildPrompt()} 之前调用。
     *
     * @return this
     */
    public VideoPromptBuilder validateLength() {
        String final_prompt = buildPrompt();
        if (final_prompt.length() > maxLength) {
            LOGGER.warn("视频提示词长度 {} 超过上限 {}，将截断。模型可能忽略细节信息。",
                    final_prompt.length(), maxLength);
            this.prompt = final_prompt.substring(0, maxLength);
            // 清空追加内容和参数（已被截断进 prompt）
            appendBuffer.setLength(0);
            paramBuffer.setLength(0);
        }
        return this;
    }

    /**
     * @return 当前已构建的 prompt 总长度（含追加内容和参数编码）
     */
    public int currentLength() {
        return buildPrompt().length();
    }

    // ==================== 素材引用 ====================

    /**
     * 添加一张参考图片，返回自动分配的引用名。
     *
     * @param url 图片 URL
     * @return 引用名（如"图1"），若 refStyle 为 STYLE_NONE 则返回 null
     */
    public String refImage(String url) {
        if (url == null || url.isEmpty()) return null;
        String existing = refNameMap.get(url);
        if (existing != null) return existing;
        imageCounter++;
        String name = refStyle.format("image", imageCounter);
        refNameMap.put(url, name);
        return name;
    }

    /**
     * 添加一个参考视频，返回自动分配的引用名。
     *
     * @param url 视频 URL
     * @return 引用名（如"视频1"）
     */
    public String refVideo(String url) {
        if (url == null || url.isEmpty()) return null;
        String existing = refNameMap.get(url);
        if (existing != null) return existing;
        videoCounter++;
        String name = refStyle.format("video", videoCounter);
        refNameMap.put(url, name);
        return name;
    }

    /**
     * 添加一段参考音频，返回自动分配的引用名。
     *
     * @param url 音频 URL
     * @return 引用名（如"音频1"）
     */
    public String refAudio(String url) {
        if (url == null || url.isEmpty()) return null;
        String existing = refNameMap.get(url);
        if (existing != null) return existing;
        audioCounter++;
        String name = refStyle.format("audio", audioCounter);
        refNameMap.put(url, name);
        return name;
    }

    /**
     * 添加一张参考图片并附带标签描述，返回自动分配的引用名。
     *
     * @param url   图片 URL
     * @param label 标签描述（如"背景图(场景)"、"程诺（体型高挑修长）"）
     * @return 引用名
     */
    public String refImage(String url, String label) {
        String name = refImage(url);
        if (name != null && label != null) refNameLabels.put(url, label);
        return name;
    }

    /**
     * 添加一个参考视频并附带标签描述。
     *
     * @param url   视频 URL
     * @param label 标签描述
     * @return 引用名
     */
    public String refVideo(String url, String label) {
        String name = refVideo(url);
        if (name != null && label != null) refNameLabels.put(url, label);
        return name;
    }

    /**
     * 添加一段参考音频并附带标签描述。
     *
     * @param url   音频 URL
     * @param label 标签描述（如"小美（角色音色样本）"）
     * @return 引用名
     */
    public String refAudio(String url, String label) {
        String name = refAudio(url);
        if (name != null && label != null) refNameLabels.put(url, label);
        return name;
    }

    /**
     * 添加一个主体元素（Kling），返回自动分配的引用名。
     *
     * @param elementId 主体 ID
     * @return 引用名（如"element_1"）
     */
    public String addElement(String elementId) {
        if (elementId == null || elementId.isEmpty()) return null;
        String existing = refNameMap.get(elementId);
        if (existing != null) return existing;
        elementCounter++;
        String name = refStyle.format("element", elementCounter);
        refNameMap.put(elementId, name);
        return name;
    }

    // ==================== 角色名 → 图片引用替换 ====================

    /**
     * 将角色名绑定到下一个图片引用。
     * 调用 {@link #refImage(String)} 后调用此方法，将角色名与刚分配的图片引用关联。
     * <p>
     * 示例：
     * <pre>{@code
     * builder.refImage(sceneUrl, "背景图");        // → "图片1"
     * builder.refImage(charAUrl, "程诺");           // → "图片2"
     * builder.mapCharacter("程诺");                 // 程诺 → 图片2
     * builder.refImage(charBUrl, "黎昕");           // → "图片3"
     * builder.mapCharacter("黎昕");                 // 黎昕 → 图片3
     * }</pre>
     *
     * @param characterName 角色名（如"程诺"）
     * @return this
     */
    public VideoPromptBuilder mapCharacter(String characterName) {
        if (characterName == null || characterName.isEmpty()) return this;
        // 取最近一次 refImage 分配的引用名
        String lastImageRef = refStyle.format("image", imageCounter);
        if (lastImageRef != null) {
            characterMapping.put(characterName, lastImageRef);
        }
        return this;
    }

    /**
     * 显式绑定角色名到指定图片引用。
     *
     * @param characterName 角色名
     * @param imageRefName  图片引用名（如"图2"）
     * @return this
     */
    public VideoPromptBuilder mapCharacter(String characterName, String imageRefName) {
        if (characterName == null || characterName.isEmpty()) return this;
        if (imageRefName != null) {
            characterMapping.put(characterName, imageRefName);
        }
        return this;
    }

    /**
     * 在 prompt 中将角色名替换为图片引用，并移除尾部的角色声明段。
     * <p>
     * 替换前：{@code 程诺在大雨中追上黎昕，紧紧地抱紧黎昕，图 2 程诺，图 3 黎昕}
     * <br>替换后：{@code 图片2 在大雨中追上图片3，紧紧地抱紧图片3}
     * <p>
     * 尾部声明段的匹配模式：{@code 图N 角色名} 或 {@code 图片N 角色名}（逗号/分号/换行分隔）。
     *
     * @return this
     */
    public VideoPromptBuilder substituteCharacters() {
        if (characterMapping.isEmpty()) return this;

        // 1. 移除尾部角色声明段（如 "，图 2 程诺，图 3 黎昕"）
        //    匹配模式：逗号/分号/换行 + 可选空格 + "图/图片" + 数字 + 空格 + 角色名
        prompt = prompt.replaceAll(
                "[，,；;\\s]+(?:图\\s*\\d+|图片\\s*\\d+)\\s+[\\u4e00-\\u9fa5\\w]+",
                "");
        // 清理尾部多余的标点
        prompt = prompt.replaceAll("[，,；;\\s]+$", "").trim();

        // 2. 按角色名长度降序替换（避免短名误替换长名的子串）
        List<Map.Entry<String, String>> sorted = new ArrayList<>(characterMapping.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        for (Map.Entry<String, String> entry : sorted) {
            String name = entry.getKey();
            String ref = entry.getValue();
            prompt = prompt.replace(name, ref);
        }

        return this;
    }

    /** @return 角色名 → 图片引用名 的映射 */
    public Map<String, String> getCharacterMapping() {
        return Collections.unmodifiableMap(characterMapping);
    }

    /**
     * 查询已添加素材的引用名。
     *
     * @param urlOrId 素材 URL 或主体 ID
     * @return 引用名，未添加过则返回 null
     */
    public String getRefName(String urlOrId) {
        return refNameMap.get(urlOrId);
    }

    /**
     * @return url → 引用名 的有序映射（供 provider 构建 media/contents 数组时对照）
     */
    public Map<String, String> getRefNameMap() {
        return Collections.unmodifiableMap(refNameMap);
    }

    /** @return 已添加的参考图片数量 */
    public int getImageCount() { return imageCounter; }

    /** @return 已添加的参考视频数量 */
    public int getVideoCount() { return videoCounter; }

    /** @return 已添加的参考音频数量 */
    public int getAudioCount() { return audioCounter; }

    /** @return 已添加的主体元素数量 */
    public int getElementCount() { return elementCounter; }

    // ==================== 多镜头分镜 ====================

    /**
     * 添加一条分镜（Kling 3.0 多镜头格式）。
     *
     * @param index       分镜序号（1-based）
     * @param duration    分镜时长（秒，≥1）
     * @param description 分镜描述（最大 512 字符）
     * @return this
     */
    public VideoPromptBuilder addShot(int index, int duration, String description) {
        if (shots.size() >= 6) {
            throw new IllegalArgumentException("Kling 3.0 最多支持 6 个分镜，当前已有 " + shots.size() + " 个");
        }
        if (duration < 1) {
            throw new IllegalArgumentException("分镜时长必须 ≥1，当前: " + duration);
        }
        if (description != null && description.length() > 512) {
            LOGGER.warn("分镜 {} 描述长度 {} 超过 512，将被截断", index, description.length());
            description = description.substring(0, 512);
        }
        shots.add(new Shot(index, duration, description));
        return this;
    }

    /**
     * 构建 Kling 3.0 多镜头格式提示词。
     * 格式：{@code 镜头 1, 3, xxx; 镜头 2, 2, yyy;}
     * <p>
     * 校验：最多 6 个分镜。
     *
     * @return 多镜头格式字符串
     */
    public String buildMultiShot() {
        if (shots.isEmpty()) return prompt;
        StringBuilder sb = new StringBuilder();
        for (Shot shot : shots) {
            sb.append("镜头 ").append(shot.index)
              .append(", ").append(shot.duration)
              .append(", ").append(shot.description)
              .append("; ");
        }
        return sb.toString().trim();
    }

    /** @return 已添加的分镜列表 */
    public List<Shot> getShots() {
        return Collections.unmodifiableList(shots);
    }

    // ==================== 参数编码（--flag 格式） ====================

    /**
     * 编码一个参数为 {@code --flag value} 格式，追加到 prompt 尾部。
     *
     * @param flag  参数名（如 "duration"）
     * @param value 参数值
     * @return this
     */
    public VideoPromptBuilder encodeParam(String flag, Object value) {
        if (value == null) return this;
        String strVal = value.toString();
        if (strVal.isEmpty()) return this;
        // 含空格的值用双引号包裹
        if (strVal.contains(" ") && !strVal.startsWith("\"")) {
            strVal = "\"" + strVal + "\"";
        }
        paramBuffer.append(" --").append(flag).append(" ").append(strVal);
        return this;
    }

    /** 编码 duration 参数 */
    public VideoPromptBuilder encodeDuration(Integer duration) {
        return encodeParam("duration", duration);
    }

    /** 编码宽高比参数 */
    public VideoPromptBuilder encodeAspectRatio(String ar) {
        return encodeParam("ar", ar);
    }

    /** 编码分辨率参数 */
    public VideoPromptBuilder encodeResolution(String resolution) {
        return encodeParam("rs", resolution);
    }

    /** 编码种子参数 */
    public VideoPromptBuilder encodeSeed(Long seed) {
        return encodeParam("seed", seed);
    }

    /** 编码帧率参数 */
    public VideoPromptBuilder encodeFps(Integer fps) {
        return encodeParam("fps", fps);
    }

    /** 编码水印参数 */
    public VideoPromptBuilder encodeWatermark(Boolean watermark) {
        return encodeParam("watermark", watermark);
    }

    /** 编码固定摄像头参数 */
    public VideoPromptBuilder encodeCameraFixed(Boolean cameraFixed) {
        return encodeParam("camerafixed", cameraFixed);
    }

    /** 编码负向提示词参数 */
    public VideoPromptBuilder encodeNegativePrompt(String negative) {
        if (negative == null || negative.isEmpty()) return this;
        // 负向提示词始终用双引号包裹
        paramBuffer.append(" --negative \"").append(negative).append("\"");
        return this;
    }

    // ==================== 有声对话格式 ====================

    /**
     * 格式化 Seedance 有声对话：{@code 角色说："台词"}。
     * 对话内容置于双引号内可优化音频生成效果。
     *
     * @param speaker 角色名
     * @param line    台词内容
     * @return 格式化后的对话字符串
     */
    public static String formatDialogue(String speaker, String line) {
        return speaker + "说：{" + line + "}";
    }

    /**
     * 追加一段对话到提示词。
     *
     * @param speaker 角色名
     * @param line    台词内容
     * @return this
     */
    public VideoPromptBuilder appendDialogue(String speaker, String line) {
        if (appendBuffer.length() > 0) appendBuffer.append(" ");
        appendBuffer.append(formatDialogue(speaker, line));
        return this;
    }

    /**
     * 追加自定义文本到提示词尾部。
     *
     * @param text 追加文本
     * @return this
     */
    public VideoPromptBuilder append(String text) {
        if (text == null || text.isEmpty()) return this;
        if (appendBuffer.length() > 0) appendBuffer.append(" ");
        appendBuffer.append(text);
        return this;
    }

    // ==================== 素材引用注释 & 音色对话 ====================

    /**
     * 构建素材引用注释段落（{@code [素材引用]}）。
     * 按注册顺序列出每个素材的引用名和标签：
     * <pre>
     * [素材引用]
     * 图片1：背景图(场景)
     * 图片2：程诺（体型高挑修长）
     * 音频1：小美（角色音色样本）
     * </pre>
     * 未注册标签的素材使用空标签。无素材时返回空字符串。
     *
     * @return 素材引用注释段落
     */
    public String buildMediaRefNote() {
        if (refNameMap.isEmpty()) return "";
        StringBuilder sb = null;
        for (Map.Entry<String, String> entry : refNameMap.entrySet()) {
            String refName = entry.getValue();
            if (refName == null) continue; // STYLE_NONE
            if (sb == null) sb = new StringBuilder("[素材引用]\n");
            String label = refNameLabels.getOrDefault(entry.getKey(), "");
            sb.append(refName).append("：").append(label).append("\n");
        }
        return sb != null ? sb.toString().trim() : "";
    }

    /**
     * @return url → 标签描述 的有序映射
     */
    public Map<String, String> getRefNameLabels() {
        return Collections.unmodifiableMap(refNameLabels);
    }

    /**
     * 追加多角色音色对话段落。格式：
     * <pre>
     * [台词] 参考 @音频1、@音频2 中对应角色的音色，用其声线说：{台词内容}
     * </pre>
     * 无音频引用时退化为：
     * <pre>
     * [台词] 说：{台词内容}
     * </pre>
     *
     * @param audioUrls    已注册的音频 URL 列表（按引用顺序）
     * @param dialogueText 台词内容
     * @return this
     */
    public VideoPromptBuilder appendVoiceDialogue(List<String> audioUrls, String dialogueText) {
        if (dialogueText == null || dialogueText.isEmpty()) return this;
        StringBuilder sb = new StringBuilder("[台词] ");
        // 收集已注册的音频引用名
        List<String> audioRefs = new ArrayList<>();
        if (audioUrls != null) {
            for (String url : audioUrls) {
                String name = refNameMap.get(url);
                if (name != null) audioRefs.add(name);
            }
        }
        if (!audioRefs.isEmpty()) {
            sb.append("参考 ");
            for (int i = 0; i < audioRefs.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append("@").append(audioRefs.get(i));
            }
            sb.append(" 中对应角色的音色，用其声线");
        }
        sb.append("说：{").append(dialogueText.trim()).append("}");
        return append(sb.toString());
    }

    // ==================== 运动提示词组装（静态工具） ====================

    /**
     * 组装运动提示词（从 LLM 输出的分解字段构建基础 prompt）。
     * 格式：{@code {subject}，{cameraSpec}·{camera}，{atmosphere}。{timing} 约束：{constraints}}
     *
     * @param subject     主体动作描述
     * @param cameraSpec  镜头规格（如"广角"、"特写"）
     * @param camera      运镜方式（如"缓推"、"环绕"）
     * @param atmosphere  氛围描述
     * @param timing      节奏描述（仅 duration ≥ 10 时有效）
     * @param constraints 约束/负向描述
     * @return 组装后的运动提示词
     */
    public static String buildMotionPrompt(String subject, String cameraSpec, String camera,
                                            String atmosphere, String timing, String constraints) {
        StringBuilder mp = new StringBuilder();
        if (subject != null && !subject.isEmpty()) mp.append(subject);
        // cameraSpec 和 camera 拼接（中间用 · 连接）
        StringBuilder cameraPart = new StringBuilder();
        if (cameraSpec != null && !cameraSpec.isEmpty()) cameraPart.append(cameraSpec);
        if (camera != null && !camera.isEmpty()) {
            if (cameraPart.length() > 0) cameraPart.append("·");
            cameraPart.append(camera);
        }
        if (cameraPart.length() > 0) {
            if (mp.length() > 0) mp.append("，");
            mp.append(cameraPart);
        }
        if (atmosphere != null && !atmosphere.isEmpty()) {
            if (mp.length() > 0) mp.append("，");
            mp.append(atmosphere);
        }
        if (mp.length() > 0) mp.append("。");
        if (timing != null && !timing.isEmpty()) mp.append(timing).append(" ");
        if (constraints != null && !constraints.isEmpty()) {
            mp.append("约束：").append(constraints);
        }
        return mp.toString();
    }

    // ==================== 构建 ====================

    /**
     * 构建最终提示词。
     * 按顺序拼接：基础 prompt + 多镜头（如有）+ 追加内容（对话/自定义）+ 参数编码。
     *
     * @return 最终提示词字符串
     */
    public String buildPrompt() {
        StringBuilder sb = new StringBuilder();

        // 1. 多镜头格式 或 基础 prompt
        if (!shots.isEmpty()) {
            sb.append(buildMultiShot());
        } else {
            sb.append(prompt);
        }

        // 2. 追加内容（对话、自定义文本）
        if (appendBuffer.length() > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(appendBuffer);
        }

        // 3. 参数编码（--flag 格式）
        if (paramBuffer.length() > 0) {
            sb.append(paramBuffer);
        }

        return sb.toString();
    }

    /**
     * 重置 builder 状态，可复用实例。
     *
     * @return this
     */
    public VideoPromptBuilder reset() {
        prompt = "";
        negativePrompt = null;
        imageCounter = 0;
        videoCounter = 0;
        audioCounter = 0;
        elementCounter = 0;
        refNameMap.clear();
        refNameLabels.clear();
        characterMapping.clear();
        shots.clear();
        paramBuffer.setLength(0);
        appendBuffer.setLength(0);
        return this;
    }

    // ==================== 内部类型 ====================

    /** 多镜头分镜数据 */
    public static class Shot {
        public final int index;
        public final int duration;
        public final String description;

        public Shot(int index, int duration, String description) {
            this.index = index;
            this.duration = duration;
            this.description = description;
        }
    }
}
