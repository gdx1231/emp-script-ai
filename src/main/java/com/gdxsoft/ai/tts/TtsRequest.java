package com.gdxsoft.ai.tts;

/**
 * A single TTS synthesis request.
 *
 * @since 1.1.0
 */
public class TtsRequest {
    private final String text;
    private final TtsOptions options;

    public TtsRequest(String text) {
        this(text, new TtsOptions());
    }

    public TtsRequest(String text, TtsOptions options) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("text is required");
        if (options == null) throw new IllegalArgumentException("options is required");
        this.text = text;
        this.options = options;
    }

    public String getText() { return text; }
    public TtsOptions getOptions() { return options; }
}
