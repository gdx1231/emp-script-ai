package com.gdxsoft.ai.video;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * VideoPromptBuilder 单元测试。
 * <p>
 * 纯单元测试，不需要 API Key。
 *
 * <pre>
 * mvn test -Dtest=VideoPromptBuilderTest
 * </pre>
 */
class VideoPromptBuilderTest {

    // ==================== 素材引用编号 ====================

    @Nested
    @DisplayName("素材引用自动编号")
    class MediaRefNumbering {

        @Test
        @DisplayName("Wan3.0 风格：图1/视频1/音频1")
        void wan3Style() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            assertEquals("图1", b.refImage("https://img1.jpg"));
            assertEquals("图2", b.refImage("https://img2.jpg"));
            assertEquals("视频1", b.refVideo("https://vid1.mp4"));
            assertEquals("音频1", b.refAudio("https://aud1.mp3"));
            assertEquals("图3", b.refImage("https://img3.jpg"));
            assertEquals("视频2", b.refVideo("https://vid2.mp4"));
        }

        @Test
        @DisplayName("Seedance 风格：图片1/视频1/音频1")
        void seedanceStyle() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            assertEquals("图片1", b.refImage("https://img1.jpg"));
            assertEquals("视频1", b.refVideo("https://vid1.mp4"));
            assertEquals("音频1", b.refAudio("https://aud1.mp3"));
            assertEquals("图片2", b.refImage("https://img2.jpg"));
        }

        @Test
        @DisplayName("Kling 风格：image_1/video_1/element_1")
        void klingStyle() {
            VideoPromptBuilder b = VideoPromptBuilder.forKling();
            assertEquals("image_1", b.refImage("https://img1.jpg"));
            assertEquals("video_1", b.refVideo("https://vid1.mp4"));
            assertEquals("element_1", b.addElement("elem-123"));
            assertEquals("image_2", b.refImage("https://img2.jpg"));
        }

        @Test
        @DisplayName("STYLE_NONE 返回 null")
        void noneStyle() {
            VideoPromptBuilder b = VideoPromptBuilder.forMiniMax();
            assertNull(b.refImage("https://img1.jpg"));
            assertNull(b.refVideo("https://vid1.mp4"));
        }

        @Test
        @DisplayName("同一 URL 不重复编号")
        void deduplicate() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            assertEquals("图1", b.refImage("https://same.jpg"));
            assertEquals("图1", b.refImage("https://same.jpg"));
            assertEquals(1, b.getImageCount());
        }

        @Test
        @DisplayName("null/空 URL 返回 null 且不计数")
        void nullUrl() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            assertNull(b.refImage(null));
            assertNull(b.refImage(""));
            assertEquals(0, b.getImageCount());
        }

        @Test
        @DisplayName("getRefName 映射查询")
        void refNameMap() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            b.refImage("https://img1.jpg");
            b.refVideo("https://vid1.mp4");
            assertEquals("图1", b.getRefName("https://img1.jpg"));
            assertEquals("视频1", b.getRefName("https://vid1.mp4"));
            assertNull(b.getRefName("https://unknown.jpg"));
        }

        @Test
        @DisplayName("getRefNameMap 返回不可变映射")
        void refNameMapImmutable() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            b.refImage("https://img1.jpg");
            Map<String, String> map = b.getRefNameMap();
            assertThrows(UnsupportedOperationException.class,
                    () -> map.put("x", "y"));
        }

        @Test
        @DisplayName("各类型独立计数")
        void independentCounters() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            b.refImage("img1");
            b.refImage("img2");
            b.refVideo("vid1");
            b.refAudio("aud1");
            b.refAudio("aud2");
            b.refAudio("aud3");
            assertEquals(2, b.getImageCount());
            assertEquals(1, b.getVideoCount());
            assertEquals(3, b.getAudioCount());
            assertEquals(0, b.getElementCount());
        }
    }

    // ==================== 长度校验 ====================

    @Nested
    @DisplayName("长度校验")
    class LengthValidation {

        @Test
        @DisplayName("未超限不截断")
        void withinLimit() {
            VideoPromptBuilder b = VideoPromptBuilder.forCogVideoX();
            b.prompt("一只猫在玩耍");
            b.validateLength();
            assertEquals("一只猫在玩耍", b.buildPrompt());
        }

        @Test
        @DisplayName("超限截断到 maxLength")
        void overLimit() {
            VideoPromptBuilder b = new VideoPromptBuilder(VideoPromptBuilder.STYLE_NONE);
            b.maxLength(10);
            b.prompt("这是一段超过十个字符的很长的提示词内容");
            b.validateLength();
            assertEquals(10, b.buildPrompt().length());
        }

        @Test
        @DisplayName("预置常量值正确")
        void constants() {
            assertEquals(512, VideoPromptBuilder.MAX_COGVIDEOX);
            assertEquals(3072, VideoPromptBuilder.MAX_KLING);
            assertEquals(7000, VideoPromptBuilder.MAX_MINIMAX);
            assertEquals(20000, VideoPromptBuilder.MAX_WAN3);
            assertEquals(500, VideoPromptBuilder.MAX_SEEDANCE_ZH);
        }

        @Test
        @DisplayName("currentLength 反映完整构建结果")
        void currentLength() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            b.prompt("基础提示词");
            b.encodeDuration(5);
            assertTrue(b.currentLength() > b.getPrompt().length());
        }
    }

    // ==================== 多镜头分镜 ====================

    @Nested
    @DisplayName("多镜头分镜格式")
    class MultiShot {

        @Test
        @DisplayName("Kling 多镜头格式输出")
        void klingFormat() {
            VideoPromptBuilder b = VideoPromptBuilder.forKling();
            b.addShot(1, 3, "女孩走进咖啡厅")
             .addShot(2, 2, "端起咖啡杯")
             .addShot(3, 5, "窗外阳光洒落");
            String result = b.buildMultiShot();
            assertEquals("镜头 1, 3, 女孩走进咖啡厅; 镜头 2, 2, 端起咖啡杯; 镜头 3, 5, 窗外阳光洒落;", result);
        }

        @Test
        @DisplayName("无分镜时返回基础 prompt")
        void noShots() {
            VideoPromptBuilder b = VideoPromptBuilder.forKling();
            b.prompt("普通提示词");
            assertEquals("普通提示词", b.buildMultiShot());
        }

        @Test
        @DisplayName("超过 6 个分镜抛异常")
        void tooManyShots() {
            VideoPromptBuilder b = VideoPromptBuilder.forKling();
            for (int i = 1; i <= 6; i++) {
                b.addShot(i, 1, "分镜" + i);
            }
            assertThrows(IllegalArgumentException.class,
                    () -> b.addShot(7, 1, "超出限制"));
        }

        @Test
        @DisplayName("分镜时长 <1 抛异常")
        void invalidDuration() {
            VideoPromptBuilder b = VideoPromptBuilder.forKling();
            assertThrows(IllegalArgumentException.class,
                    () -> b.addShot(1, 0, "无效时长"));
        }

        @Test
        @DisplayName("buildPrompt 含分镜时使用分镜格式")
        void buildPromptWithShots() {
            VideoPromptBuilder b = VideoPromptBuilder.forKling();
            b.prompt("这个会被分镜覆盖");
            b.addShot(1, 5, "猫咪跳跃");
            String result = b.buildPrompt();
            assertTrue(result.startsWith("镜头 1, 5, 猫咪跳跃;"));
            assertFalse(result.contains("这个会被分镜覆盖"));
        }
    }

    // ==================== 参数编码 ====================

    @Nested
    @DisplayName("参数编码（--flag 格式）")
    class ParamEncoding {

        @Test
        @DisplayName("基础参数编码")
        void basicEncoding() {
            VideoPromptBuilder b = VideoPromptBuilder.forJimeng();
            b.prompt("一只猫");
            b.encodeDuration(5);
            b.encodeAspectRatio("16:9");
            String result = b.buildPrompt();
            assertEquals("一只猫 --duration 5 --ar 16:9", result);
        }

        @Test
        @DisplayName("负向提示词用双引号包裹")
        void negativeQuoted() {
            VideoPromptBuilder b = VideoPromptBuilder.forJimeng();
            b.prompt("风景");
            b.encodeNegativePrompt("模糊 低质量");
            assertEquals("风景 --negative \"模糊 低质量\"", b.buildPrompt());
        }

        @Test
        @DisplayName("null 值跳过")
        void nullSkipped() {
            VideoPromptBuilder b = VideoPromptBuilder.forJimeng();
            b.prompt("测试");
            b.encodeDuration(null);
            b.encodeSeed(null);
            assertEquals("测试", b.buildPrompt());
        }

        @Test
        @DisplayName("Jimeng 完整参数编码（模拟迁移后行为）")
        void jimengFullEncoding() {
            VideoPromptBuilder b = VideoPromptBuilder.forJimeng();
            b.prompt("赛博城市漫步");
            b.encodeDuration(5);
            b.encodeFps(24);
            b.encodeAspectRatio("16:9");
            b.encodeCameraFixed(false);
            b.encodeWatermark(true);
            b.encodeSeed(42L);
            String result = b.buildPrompt();
            assertTrue(result.contains("--duration 5"));
            assertTrue(result.contains("--fps 24"));
            assertTrue(result.contains("--ar 16:9"));
            assertTrue(result.contains("--camerafixed false"));
            assertTrue(result.contains("--watermark true"));
            assertTrue(result.contains("--seed 42"));
        }

        @Test
        @DisplayName("含空格的值自动引号包裹")
        void spaceValueQuoted() {
            VideoPromptBuilder b = new VideoPromptBuilder(VideoPromptBuilder.STYLE_NONE);
            b.prompt("test");
            b.encodeParam("desc", "hello world");
            assertEquals("test --desc \"hello world\"", b.buildPrompt());
        }
    }

    // ==================== 有声对话格式 ====================

    @Nested
    @DisplayName("有声对话格式")
    class Dialogue {

        @Test
        @DisplayName("formatDialogue 输出正确格式")
        void formatDialogueOutput() {
            String result = VideoPromptBuilder.formatDialogue("男人", "你好");
            assertEquals("男人说：{你好}", result);
        }

        @Test
        @DisplayName("appendDialogue 追加到 prompt")
        void appendDialogueToPrompt() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.prompt("街头场景");
            b.appendDialogue("男人", "你记住，以后不可以用手指指月亮");
            String result = b.buildPrompt();
            assertTrue(result.contains("街头场景"));
            assertTrue(result.contains("男人说：{你记住，以后不可以用手指指月亮}"));
        }

        @Test
        @DisplayName("多段对话用空格分隔")
        void multipleDialogues() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.prompt("对话场景");
            b.appendDialogue("A", "你好");
            b.appendDialogue("B", "再见");
            String result = b.buildPrompt();
            assertTrue(result.contains("A说：{你好} B说：{再见}"));
        }
    }

    // ==================== 综合场景 ====================

    @Nested
    @DisplayName("综合场景")
    class Integration {

        @Test
        @DisplayName("Wan3.0 全能参考：prompt + 素材引用")
        void wan3FullScenario() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            b.prompt("视频1抱着图3，在图4的椅子上弹奏");
            b.refImage("https://img1.jpg");  // 图1
            b.refVideo("https://vid1.mp4");  // 视频1
            b.refImage("https://img2.jpg");  // 图2
            b.refImage("https://img3.jpg");  // 图3
            b.refImage("https://img4.jpg");  // 图4

            // 验证引用名
            assertEquals("视频1", b.getRefName("https://vid1.mp4"));
            assertEquals("图3", b.getRefName("https://img3.jpg"));
            assertEquals("图4", b.getRefName("https://img4.jpg"));

            // 验证计数
            assertEquals(4, b.getImageCount());
            assertEquals(1, b.getVideoCount());
        }

        @Test
        @DisplayName("Kling Omni：@引用拼接")
        void klingOmniScenario() {
            VideoPromptBuilder b = VideoPromptBuilder.forKling();
            b.prompt("@image_1 拿着 @image_2 走过 @video_1");
            b.refImage("https://img1.jpg");  // image_1
            b.refImage("https://img2.jpg");  // image_2
            b.refVideo("https://vid1.mp4");  // video_1

            // provider 用 @ + refName 拼接
            assertEquals("@image_1", "@" + b.getRefName("https://img1.jpg"));
            assertEquals("@video_1", "@" + b.getRefName("https://vid1.mp4"));
        }

        @Test
        @DisplayName("reset 清空所有状态")
        void resetClearsState() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            b.prompt("原始");
            b.refImage("img1");
            b.encodeDuration(5);
            b.appendDialogue("A", "你好");
            b.addShot(1, 3, "分镜");

            b.reset();

            assertEquals("", b.buildPrompt());
            assertEquals(0, b.getImageCount());
            assertTrue(b.getRefNameMap().isEmpty());
            assertTrue(b.getShots().isEmpty());
        }

        @Test
        @DisplayName("链式调用流畅性")
        void fluentApi() {
            VideoPromptBuilder b = VideoPromptBuilder.forWan3();
            b.prompt("一只猫在月光下奔跑");
            b.refImage("https://cat.jpg"); // refImage 返回 String，不参与链式
            String result = b.encodeDuration(5).buildPrompt();
            assertTrue(result.contains("一只猫在月光下奔跑"));
            assertTrue(result.contains("--duration 5"));
        }
    }

    // ==================== 素材标签 & 注释 ====================

    @Nested
    @DisplayName("素材标签与注释")
    class MediaLabels {

        @Test
        @DisplayName("refImage(url, label) 注册标签")
        void refImageWithLabel() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.refImage("https://bg.jpg", "背景图(场景)");
            b.refImage("https://char.jpg", "程诺（体型高挑修长）");
            assertEquals("背景图(场景)", b.getRefNameLabels().get("https://bg.jpg"));
            assertEquals("程诺（体型高挑修长）", b.getRefNameLabels().get("https://char.jpg"));
        }

        @Test
        @DisplayName("buildMediaRefNote 输出正确格式")
        void buildMediaRefNote() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.refImage("https://bg.jpg", "背景图(场景)");
            b.refImage("https://char.jpg", "程诺");
            b.refAudio("https://voice.mp3", "小美（角色音色样本）");
            String note = b.buildMediaRefNote();
            assertTrue(note.startsWith("[素材引用]"));
            assertTrue(note.contains("图片1：背景图(场景)"));
            assertTrue(note.contains("图片2：程诺"));
            assertTrue(note.contains("音频1：小美（角色音色样本）"));
        }

        @Test
        @DisplayName("无标签素材使用空标签")
        void noLabelEmpty() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.refImage("https://img.jpg"); // 无标签
            String note = b.buildMediaRefNote();
            assertTrue(note.contains("图片1："));
        }

        @Test
        @DisplayName("无素材时返回空字符串")
        void emptyNote() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            assertEquals("", b.buildMediaRefNote());
        }

        @Test
        @DisplayName("STYLE_NONE 的 buildMediaRefNote 返回空")
        void noneStyleNote() {
            VideoPromptBuilder b = VideoPromptBuilder.forMiniMax();
            b.refImage("https://img.jpg", "标签");
            assertEquals("", b.buildMediaRefNote());
        }

        @Test
        @DisplayName("reset 清空标签")
        void resetClearsLabels() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.refImage("https://img.jpg", "标签");
            b.reset();
            assertTrue(b.getRefNameLabels().isEmpty());
        }
    }

    // ==================== 音色对话 ====================

    @Nested
    @DisplayName("音色对话格式")
    class VoiceDialogue {

        @Test
        @DisplayName("有音频引用时的完整格式")
        void withAudioRefs() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.refAudio("https://voice1.mp3", "小美");
            b.refAudio("https://voice2.mp3", "阿强");
            b.appendVoiceDialogue(
                    java.util.List.of("https://voice1.mp3", "https://voice2.mp3"),
                    "今天的阳光真好。");
            String result = b.buildPrompt();
            assertTrue(result.contains("[台词] 参考 @音频1、@音频2 中对应角色的音色，用其声线说：{今天的阳光真好。}"));
        }

        @Test
        @DisplayName("无音频引用时退化格式")
        void withoutAudioRefs() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.appendVoiceDialogue(null, "你好世界");
            String result = b.buildPrompt();
            assertTrue(result.contains("[台词] 说：{你好世界}"));
            assertFalse(result.contains("参考"));
        }

        @Test
        @DisplayName("部分音频已注册时只引用已注册的")
        void partialAudioRefs() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.refAudio("https://voice1.mp3", "小美");
            // voice2 未注册
            b.appendVoiceDialogue(
                    java.util.List.of("https://voice1.mp3", "https://unknown.mp3"),
                    "测试");
            String result = b.buildPrompt();
            assertTrue(result.contains("@音频1"));
            assertFalse(result.contains("@音频2"));
        }
    }

    // ==================== 运动提示词组装 ====================

    @Nested
    @DisplayName("运动提示词组装")
    class MotionPrompt {

        @Test
        @DisplayName("完整字段组装")
        void fullFields() {
            String result = VideoPromptBuilder.buildMotionPrompt(
                    "女孩在雨中奔跑", "广角", "缓推", "忧郁而唯美", "", "无字幕,无品牌logo");
            assertEquals("女孩在雨中奔跑，广角·缓推，忧郁而唯美。约束：无字幕,无品牌logo", result);
        }

        @Test
        @DisplayName("仅 subject 和 constraints")
        void minimalFields() {
            String result = VideoPromptBuilder.buildMotionPrompt(
                    "猫咪跳跃", "", "", "", "", "无变形");
            assertEquals("猫咪跳跃。约束：无变形", result);
        }

        @Test
        @DisplayName("含 timing")
        void withTiming() {
            String result = VideoPromptBuilder.buildMotionPrompt(
                    "城市延时摄影", "", "快进", "繁华", "节奏紧凑", "无字幕");
            assertEquals("城市延时摄影，快进，繁华。节奏紧凑 约束：无字幕", result);
        }

        @Test
        @DisplayName("cameraSpec 无 camera 时不加点号")
        void cameraSpecOnly() {
            String result = VideoPromptBuilder.buildMotionPrompt(
                    "人物特写", "85mm特写", "", "柔和光线", "", "无变形");
            assertEquals("人物特写，85mm特写，柔和光线。约束：无变形", result);
        }

        @Test
        @DisplayName("全部为空返回空字符串")
        void allEmpty() {
            String result = VideoPromptBuilder.buildMotionPrompt("", "", "", "", "", "");
            assertEquals("", result);
        }

        @Test
        @DisplayName("null 安全")
        void nullSafe() {
            String result = VideoPromptBuilder.buildMotionPrompt(null, null, null, null, null, null);
            assertEquals("", result);
        }
    }

    // ==================== 角色名替换 ====================

    @Nested
    @DisplayName("角色名 → 图片引用替换")
    class CharacterSubstitution {

        @Test
        @DisplayName("基础替换：角色名 → 图片引用")
        void basicSubstitution() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.prompt("程诺在大雨中追上黎昕，紧紧地抱紧黎昕");
            b.refImage("https://scene.jpg", "背景图");
            b.refImage("https://chengnuo.jpg", "程诺");
            b.mapCharacter("程诺");
            b.refImage("https|://lixin.jpg", "黎昕");
            b.mapCharacter("黎昕");
            b.substituteCharacters();
            assertEquals("图片2在大雨中追上图片3，紧紧地抱紧图片3", b.buildPrompt());
        }

        @Test
        @DisplayName("移除尾部角色声明段")
        void removeTrailingDeclaration() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.prompt("程诺在大雨中追上黎昕，紧紧地抱紧黎昕，图 2 程诺，图 3 黎昕");
            b.mapCharacter("程诺", "图片2");
            b.mapCharacter("黎昕", "图片3");
            b.substituteCharacters();
            String result = b.buildPrompt();
            assertFalse(result.contains("图 2 程诺"));
            assertFalse(result.contains("图 3 黎昕"));
            assertTrue(result.contains("图片2"));
            assertTrue(result.contains("图片3"));
        }

        @Test
        @DisplayName("按角色名长度降序替换（避免子串误替换）")
        void longestFirst() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.prompt("张小明明天出发");
            b.mapCharacter("张小明", "图片1");
            b.mapCharacter("小明", "图片2");
            b.substituteCharacters();
            // "张小明" 先替换，不会被 "小明" 误伤
            assertEquals("图片1明天出发", b.buildPrompt());
        }

        @Test
        @DisplayName("无映射时不修改 prompt")
        void noMappingNoChange() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.prompt("普通提示词");
            b.substituteCharacters();
            assertEquals("普通提示词", b.buildPrompt());
        }

        @Test
        @DisplayName("mapCharacter(name) 自动绑定最近 refImage 的引用")
        void autoBind() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.refImage("https://bg.jpg", "背景");     // 图片1
            b.refImage("https://char.jpg", "角色A");   // 图片2
            b.mapCharacter("角色A");                    // 自动绑定到 图片2
            assertEquals("图片2", b.getCharacterMapping().get("角色A"));
        }

        @Test
        @DisplayName("reset 清空角色映射")
        void resetClearsMapping() {
            VideoPromptBuilder b = VideoPromptBuilder.forSeedance();
            b.mapCharacter("程诺", "图片1");
            b.reset();
            assertTrue(b.getCharacterMapping().isEmpty());
        }
    }
}
