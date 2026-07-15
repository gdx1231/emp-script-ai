package com.gdxsoft.ai.stt;

import java.io.IOException;

/**
 * High-level facade for STT transcription.
 * <p>
 * Typical use:
 * <pre>{@code
 * SttResponse r = SttClient.of("openai_stt")
 *     .transcribe(AudioSource.fromFile(Path.of("recording.mp3")));
 * System.out.println(r.getText());
 * }</pre>
 *
 * @since 1.1.0
 */
public final class SttClient {
    private final ISttProvider provider;

    public SttClient(ISttProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    /** Convenience factory. */
    public static SttClient of(String providerName) {
        return new SttClient(SttProviderFactory.create(providerName));
    }

    /** Convenience factory using an already-configured provider. */
    public static SttClient of(ISttProvider provider) {
        return new SttClient(provider);
    }

    /** Transcribe with default options. */
    public SttResponse transcribe(AudioSource audio) throws IOException, InterruptedException {
        return provider.transcribe(new SttRequest(audio));
    }

    /** Transcribe with the supplied options. */
    public SttResponse transcribe(AudioSource audio, SttOptions options) throws IOException, InterruptedException {
        return provider.transcribe(new SttRequest(audio, options));
    }

    /** Transcribe with a fully-formed request. */
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        return provider.transcribe(request);
    }

    public ISttProvider getProvider() { return provider; }
}