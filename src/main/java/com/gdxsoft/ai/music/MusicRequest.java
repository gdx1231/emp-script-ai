package com.gdxsoft.ai.music;

/**
 * 一次音乐生成请求：prompt 为风格/场景描述，lyrics 位于 MusicOptions。
 */
public class MusicRequest {
    private final String prompt;
    private final MusicOptions options;

    public MusicRequest(String prompt) {
        this(prompt, new MusicOptions());
    }

    public MusicRequest(String prompt, MusicOptions options) {
        this.prompt = prompt;
        this.options = options == null ? new MusicOptions() : options;
    }

    public String getPrompt() { return prompt; }
    public MusicOptions getOptions() { return options; }
}
