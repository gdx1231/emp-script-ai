package com.gdxsoft.ai.tts;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level facade for TTS synthesis.
 * <p>
 * Typical use:
 * <pre>{@code
 * TtsResponse r = TtsClient.of("qwen_tts")
 *     .synthesize("你好，世界");
 * r.save(Path.of("hello.wav"));
 * }</pre>
 *
 * @since 1.1.0
 */
public final class TtsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TtsClient.class);

    private final ITtsProvider provider;

    public TtsClient(ITtsProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    /** Convenience factory. */
    public static TtsClient of(String providerName) {
        return new TtsClient(TtsProviderFactory.create(providerName));
    }

    /** Convenience factory using an already-configured provider. */
    public static TtsClient of(ITtsProvider provider) {
        return new TtsClient(provider);
    }

    /** Synthesize with default options. */
    public TtsResponse synthesize(String text) throws IOException, InterruptedException {
        return synthesize(new TtsRequest(text));
    }

    /** Synthesize with the supplied options. */
    public TtsResponse synthesize(String text, TtsOptions options) throws IOException, InterruptedException {
        return synthesize(new TtsRequest(text, options));
    }

    /** Synthesize with a fully-formed request. */
    public TtsResponse synthesize(TtsRequest request) throws IOException, InterruptedException {
        // 记录请求的 curl 命令（含完整 key，未脱敏），便于排查问题
        if (LOGGER.isInfoEnabled()) {
            try {
                LOGGER.info("TTS curl [{}]: {}", provider.getProviderType().getName(),
                        provider.curl(request));
            } catch (Exception e) {
                LOGGER.warn("TTS curl render failed: {}", e.getMessage());
            }
        }
        return provider.synthesize(request);
    }

    public ITtsProvider getProvider() { return provider; }
}
