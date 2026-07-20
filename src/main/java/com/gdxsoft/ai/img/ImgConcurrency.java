package com.gdxsoft.ai.img;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrency control for image generation.
 * <p>
 * Provides rate limiting, parallel generation, and retry with backoff.
 * A single instance wraps one provider and can be safely shared.
 *
 * <h3>Typical use</h3>
 * <pre>{@code
 * ImgConcurrency concurrency = ImgConcurrency.of("doubao_img")
 *     .apiKey("...")
 *     .maxConcurrency(3)       // max 3 parallel requests
 *     .maxRetries(2);          // retry on 429
 *
 * // Generate 10 images in parallel with concurrency control
 * List<ImgResponse> results = concurrency.generateAll(List.of(
 *     new ImgOptions("prompt 1"),
 *     new ImgOptions("prompt 2"),
 *     ...
 * ));
 *
 * // Or stream to files
 * List<Path> files = concurrency.generateAllToFiles(
 *     prompts, Path.of("/tmp/images"));
 *
 * // Or with callbacks
 * concurrency.generateAll(prompts,
 *     (i, resp) -> System.out.println("Done: " + i),
 *     (i, err) -> System.err.println("Failed: " + i + " - " + err));
 * }</pre>
 *
 * @since 1.2.0
 */
public final class ImgConcurrency {
    private static final Logger LOG = LoggerFactory.getLogger(ImgConcurrency.class);

    private final IImgProvider provider;
    private final Semaphore semaphore;
    private final Executor executor;
    private int maxRetries = 2;
    private long retryDelayMs = 2000;
    private long rateLimitDelayMs = 5000;

    private ImgConcurrency(IImgProvider provider, int maxConcurrency) {
        this.provider = provider;
        this.semaphore = new Semaphore(maxConcurrency);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Create from a provider name.
     *
     * @param providerName e.g. "doubao_img", "openai_img"
     */
    public static ImgConcurrency of(String providerName) {
        return of(ImgProviderFactory.create(providerName));
    }

    /** Create from an existing provider instance. */
    public static ImgConcurrency of(IImgProvider provider) {
        return new ImgConcurrency(provider, 2); // default: 2 concurrent
    }

    // ---- Configuration (fluent) ----

    /** Set API key on the underlying provider. */
    public ImgConcurrency apiKey(String key) {
        provider.setApiKey(key);
        return this;
    }

    /** Max simultaneous requests (default 2). */
    public ImgConcurrency maxConcurrency(int max) {
        if (max < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
        // Create new semaphore — existing waiters are not affected
        return new ImgConcurrency(provider, max)
                .maxRetries(this.maxRetries)
                .retryDelayMs(this.retryDelayMs)
                .rateLimitDelayMs(this.rateLimitDelayMs);
    }

    /** Max retries on failure (default 2). */
    public ImgConcurrency maxRetries(int retries) {
        this.maxRetries = retries;
        return this;
    }

    /** Delay between retries in ms (default 2000). */
    public ImgConcurrency retryDelayMs(long ms) {
        this.retryDelayMs = ms;
        return this;
    }

    /** Delay after rate limit (429) in ms (default 5000). */
    public ImgConcurrency rateLimitDelayMs(long ms) {
        this.rateLimitDelayMs = ms;
        return this;
    }

    // ---- Single generation with retry ----

    /** Generate one image with retry on failure. */
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        semaphore.acquire();
        try {
            return generateWithRetry(request, 0);
        } finally {
            semaphore.release();
        }
    }

    /** Generate one image from options. */
    public ImgResponse generate(ImgOptions opts) throws IOException, InterruptedException {
        return generate(new ImgRequest(opts));
    }

    // ---- Parallel generation ----

    /**
     * Generate all requests in parallel with concurrency control.
     * Results are in the same order as inputs.
     */
    public List<ImgResponse> generateAll(List<ImgRequest> requests)
            throws IOException, InterruptedException {
        return generateAll(requests, null, null);
    }

    /**
     * Generate all with progress callbacks.
     *
     * @param requests  list of requests
     * @param onSuccess called for each successful generation: (index, response)
     * @param onError   called for each failed generation: (index, error)
     */
    public List<ImgResponse> generateAll(
            List<ImgRequest> requests,
            Consumer<ImgResponse> onSuccess,
            Consumer<Throwable> onError) throws IOException, InterruptedException {

        List<CompletableFuture<ImgResponse>> futures = new ArrayList<>();
        for (ImgRequest req : requests) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    ImgResponse r = generate(req);
                    if (onSuccess != null) onSuccess.accept(r);
                    return r;
                } catch (Exception e) {
                    if (onError != null) onError.accept(e);
                    throw new RuntimeException(e);
                }
            }, executor));
        }

        List<ImgResponse> results = new ArrayList<>();
        for (CompletableFuture<ImgResponse> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof IOException) throw (IOException) cause;
                if (cause instanceof InterruptedException) throw (InterruptedException) cause;
                throw new IOException("Parallel generation failed: " + cause.getMessage(), cause);
            }
        }
        return results;
    }

    /**
     * Generate all with index-aware callbacks.
     *
     * @param onItemDone called for each item: (index, response, error).
     *                   error is null on success.
     */
    public List<ImgResponse> generateAll(
            List<ImgRequest> requests,
            ItemCallback onItemDone) throws IOException, InterruptedException {

        List<CompletableFuture<ImgResult>> futures = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            final int idx = i;
            ImgRequest req = requests.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    ImgResponse r = generate(req);
                    return new ImgResult(idx, r, null);
                } catch (Exception e) {
                    return new ImgResult(idx, null, e);
                }
            }, executor));
        }

        List<ImgResponse> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            results.add(null); // placeholder
        }
        for (CompletableFuture<ImgResult> f : futures) {
            ImgResult r;
            try {
                r = f.get();
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof IOException) throw (IOException) cause;
                if (cause instanceof InterruptedException) throw (InterruptedException) cause;
                throw new IOException("Parallel generation failed: " + cause.getMessage(), cause);
            }
            results.set(r.index, r.response);
            if (onItemDone != null) onItemDone.accept(r.index, r.response, r.error);
        }
        return results;
    }

    /**
     * Convenience: generate all and save to files.
     */
    public List<Path> generateAllToFiles(List<ImgRequest> requests, Path outputDir)
            throws IOException, InterruptedException {
        List<ImgResponse> responses = generateAll(requests);
        List<Path> allFiles = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            ImgResponse r = responses.get(i);
            if (r != null && !r.getImages().isEmpty()) {
                List<Path> files = r.saveAll(outputDir, "batch_" + i + "_");
                allFiles.addAll(files);
                for (ImgResponse.GeneratedImage img : r.getImages()) img.release();
            }
        }
        return allFiles;
    }

    // ---- Internal ----

    private ImgResponse generateWithRetry(ImgRequest request, int attempt)
            throws IOException, InterruptedException {
        try {
            return provider.generate(request);
        } catch (IOException e) {
            String msg = e.getMessage();
            boolean isRateLimit = msg != null &&
                    (msg.contains("429") || msg.contains("Throttling") ||
                     msg.contains("RateQuota") || msg.contains("rate limit") ||
                     msg.contains("Too Many Requests"));

            if (isRateLimit && attempt < maxRetries) {
                long delay = rateLimitDelayMs * (attempt + 1);
                LOG.warn("Rate limited (attempt {}/{}), waiting {}ms...",
                        attempt + 1, maxRetries + 1, delay);
                Thread.sleep(delay);
                return generateWithRetry(request, attempt + 1);
            }

            if (attempt < maxRetries) {
                LOG.warn("Generation failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt + 1, maxRetries + 1, retryDelayMs, msg);
                Thread.sleep(retryDelayMs);
                return generateWithRetry(request, attempt + 1);
            }

            throw e;
        }
    }

    // ---- Types ----

    private record ImgResult(int index, ImgResponse response, Throwable error) {}

    /** Callback for parallel generation with index. */
    @FunctionalInterface
    public interface ItemCallback {
        /**
         * @param index   position in the input list
         * @param response the generated image response (null on error)
         * @param error    the exception (null on success)
         */
        void accept(int index, ImgResponse response, Throwable error);
    }

    // ---- Accessors ----

    public IImgProvider getProvider() { return provider; }
    public int getMaxRetries() { return maxRetries; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public long getRateLimitDelayMs() { return rateLimitDelayMs; }
}
