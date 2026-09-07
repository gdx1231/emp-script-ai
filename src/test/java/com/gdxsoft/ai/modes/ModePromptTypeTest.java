package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * 测试 prompt 的多模态 type 属性解析和默认值。
 */
class ModePromptTypeTest {

    private static final String XML = "<modes>"
            + "<mode name='M'>"
            + "<step name='s'><prompts>"
            + "<prompt name='text' role='user'><![CDATA[普通文本]]></prompt>"
            + "<prompt name='image' role='user' type='IMAGE_URL'><![CDATA[@image_url]]></prompt>"
            + "<prompt name='file' role='user' type='file_url'><![CDATA[@file_url]]></prompt>"
            + "<prompt name='audio' role='user' type='input_audio'><![CDATA[@audio_url]]></prompt>"
            + "<prompt name='video' role='user' type='video'><![CDATA[@video_url]]></prompt>"
            + "</prompts></step>"
            + "</mode>"
            + "</modes>";

    @Test
    void parsePromptTypesAndDefaultToText() throws Exception {
        Modes modes = new Modes();
        modes.loadModes(XML);
        Mode mode = Modes.getMode("M");
        Step step = mode.getStep("s");
        assertNotNull(step);

        assertEquals("text", step.getPrompts().get(0).getType());
        assertEquals("image_url", step.getPrompts().get(1).getType());
        assertEquals("file_url", step.getPrompts().get(2).getType());
        assertEquals("input_audio", step.getPrompts().get(3).getType());
        assertEquals("video", step.getPrompts().get(4).getType());
    }

    @Test
    void cloneKeepsPromptType() {
        Prompt prompt = new Prompt();
        prompt.setType("Image_Url");
        assertEquals("image_url", prompt.clone().getType());
    }
}
