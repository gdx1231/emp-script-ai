package com.gdxsoft.ai.video;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrency control for video generation.
 * Semaphore-based rate limiting + retry.
 *
 * @since 1.3.0
 */
public final class VideoConcurrency {
    private static final Logger LOG = LoggerFactory.getLogger(VideoConcurrency.class);

    private final IVideoProvider provider;
    private final Semaphore semaphore;
    private final Executor executor;
    private int maxRetries = 1;
    private long retryDelayMs = 5000;

    private VideoConcurrency(IVideoProvider provider, int maxConcurrency) {
        this.provider = provider;
        this.semaphore = new Semaphore(maxConcurrency);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public static VideoConcurrency of(String name) {
        return of(VideoProviderFactory.create(name));
    }

    public static VideoConcurrency of(IVideoProvider p) {
        return new VideoConcurrency(p, 1); // video is slow, default 1
    }

    public VideoConcurrency apiKey(String key) { provider.setApiKey(key); return this; }
    public VideoConcurrency maxConcurrency(int v) {
        return new VideoConcurrency(provider, v).maxRetries(maxRetries).retryDelayMs(retryDelayMs);
    }
    public VideoConcurrency maxRetries(int v) { this.maxRetries = v; return this; }
    public VideoConcurrency retryDelayMs(long v) { this.retryDelayMs = v; return this; }

    public VideoResponse generate(VideoRequest req) throws IOException, InterruptedException {
        semaphore.acquire();
        try { return generateWithRetry(req, 0); }
        finally { semaphore.release(); }
    }

    public List<VideoResponse> generateAll(List<VideoRequest> requests)
            throws IOException, InterruptedException {
        List<CompletableFuture<VideoResponse>> futures = new ArrayList<>();
        for (VideoRequest req : requests) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { return generate(req); }
                catch (Exception e) { throw new RuntimeException(e); }
            }, executor));
        }
        List<VideoResponse> results = new ArrayList<>();
        for (CompletableFuture<VideoResponse> f : futures) {
            try { results.add(f.get()); }
            catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                if (c instanceof IOException) throw (IOException) c;
                if (c instanceof InterruptedException) throw (InterruptedException) c;
                throw new IOException(c.getMessage(), c);
            }
        }
        return results;
    }

    private VideoResponse generateWithRetry(VideoRequest req, int attempt)
            throws IOException, InterruptedException {
        try { return provider.generate(req); }
        catch (IOException e) {
            if (attempt < maxRetries) {
                LOG.warn("Retry {}/{}: {}", attempt + 1, maxRetries + 1, e.getMessage());
                Thread.sleep(retryDelayMs);
                return generateWithRetry(req, attempt + 1);
            }
            throw e;
        }
    }

    public IVideoProvider getProvider() { return provider; }
}
