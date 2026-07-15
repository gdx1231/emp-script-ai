package com.gdxsoft.ai.stt;

/**
 * A single STT transcription request.
 *
 * @since 1.1.0
 */
public class SttRequest {
    private final AudioSource audio;
    private final SttOptions options;

    public SttRequest(AudioSource audio) {
        this(audio, new SttOptions());
    }

    public SttRequest(AudioSource audio, SttOptions options) {
        if (audio == null) throw new IllegalArgumentException("audio is required");
        if (options == null) throw new IllegalArgumentException("options is required");
        this.audio = audio;
        this.options = options;
    }

    public AudioSource getAudio() { return audio; }
    public SttOptions getOptions() { return options; }
}