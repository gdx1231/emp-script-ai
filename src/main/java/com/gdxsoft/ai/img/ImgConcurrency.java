package com.gdxsoft.ai.img;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.script.RequestValue;

/**
 * Concurrency control for image generation.
 * <p>
 * Provides rate limiting, parallel generation, and retry with backoff. A single
 * instance wraps one provider and can be safely shared.
 *
 * <h3>Typical use</h3>
 * 
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
	/** 可选的聊天日志记录器（单线程场景显式设置） */
	private ImgChatLogger chatLogger;
	/** 数据库配置名称，用于并行场景下每次 generate 创建独立 logger */
	private String dbConfigName;

	private RequestValue rv;
	private String ref;
	private String refId;

	private ImgConcurrency(IImgProvider provider, int maxConcurrency) {
		this.provider = provider;
		this.semaphore = new Semaphore(maxConcurrency);
		this.executor = com.gdxsoft.ai.HttpUtils.createVirtualThreadExecutorService();
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
		if (max < 1)
			throw new IllegalArgumentException("maxConcurrency must be >= 1");
		// Create new semaphore — existing waiters are not affected
		return new ImgConcurrency(provider, max).maxRetries(this.maxRetries).retryDelayMs(this.retryDelayMs)
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

	/**
	 * 设置聊天日志记录器。 设置后，每次 generate 调用会自动记录到 AI_CHAT / AI_CHAT_MSG。
	 *
	 * @param logger 日志记录器，null 则不记录
	 * @return this
	 */
	public ImgConcurrency setChatLogger(ImgChatLogger logger) {
		this.chatLogger = logger;
		return this;
	}

	/** @return 当前聊天日志记录器（可能为 null） */
	public ImgChatLogger getChatLogger() {
		return chatLogger;
	}

	/**
	 * 设置请求上下文，自动从 rv 中读取 {@code ewa_db_config} 启用日志记录。
	 * <p>
	 * 并行场景下每次 generate() 调用会创建独立的 logger 实例（线程安全）。
	 *
	 * @param rv 请求上下文
	 * @return this
	 */
	public ImgConcurrency setRv(RequestValue rv) {
		this.rv = rv;
		return this;
	}

	public RequestValue getRv() {
		return this.rv;
	}

	public String getDbConfigName() {
		return dbConfigName;
	}

	public ImgConcurrency setDbConfigName(String dbConfigName) {
		this.dbConfigName = dbConfigName;
		return this;
	}

	// ---- Single generation with retry ----

	/** Generate one image with retry on failure. */
	public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
		ImgOptions opts = request.getOptions();
		// 解析 logger：显式设置的优先；否则按 dbConfigName 每次创建独立实例（线程安全）
		ImgChatLogger logger = chatLogger;
		if (logger == null) {
			logger = ImgChatLogger.create(this.rv, getDbConfigName());
		}
		if (logger != null) {
			logger.setRef(ref);
			logger.setRefId(refId);

			logger.logStart(provider.getProviderType().getName(), opts.getModel(), opts.getPrompt(),
					buildOptsJson(opts));
			logger.logCurl(provider.curl(request));
		}
		semaphore.acquire();
		try {
			ImgResponse resp = generateWithRetry(request, 0);
			if (logger != null) {
				logger.logSuccess(resp);
			}
			return resp;
		} catch (Exception e) {
			if (logger != null) {
				logger.logError(e);
			}
			throw e;
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
	 * Generate all requests in parallel with concurrency control. Results are in
	 * the same order as inputs.
	 */
	public List<ImgResponse> generateAll(List<ImgRequest> requests) throws IOException, InterruptedException {
		return generateAll(requests, null, null);
	}

	/**
	 * Generate all with progress callbacks.
	 *
	 * @param requests  list of requests
	 * @param onSuccess called for each successful generation: (index, response)
	 * @param onError   called for each failed generation: (index, error)
	 */
	public List<ImgResponse> generateAll(List<ImgRequest> requests, Consumer<ImgResponse> onSuccess,
			Consumer<Throwable> onError) throws IOException, InterruptedException {

		List<CompletableFuture<ImgResponse>> futures = new ArrayList<>();
		for (ImgRequest req : requests) {
			futures.add(CompletableFuture.supplyAsync(() -> {
				try {
					ImgResponse r = generate(req);
					if (onSuccess != null)
						onSuccess.accept(r);
					return r;
				} catch (Exception e) {
					if (onError != null)
						onError.accept(e);
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
				if (cause instanceof IOException)
					throw (IOException) cause;
				if (cause instanceof InterruptedException)
					throw (InterruptedException) cause;
				throw new IOException("Parallel generation failed: " + cause.getMessage(), cause);
			}
		}
		return results;
	}

	/**
	 * Generate all with index-aware callbacks.
	 *
	 * @param onItemDone called for each item: (index, response, error). error is
	 *                   null on success.
	 */
	public List<ImgResponse> generateAll(List<ImgRequest> requests, ItemCallback onItemDone)
			throws IOException, InterruptedException {

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
				if (cause instanceof IOException)
					throw (IOException) cause;
				if (cause instanceof InterruptedException)
					throw (InterruptedException) cause;
				throw new IOException("Parallel generation failed: " + cause.getMessage(), cause);
			}
			results.set(r.index, r.response);
			if (onItemDone != null)
				onItemDone.accept(r.index, r.response, r.error);
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
				for (ImgResponse.GeneratedImage img : r.getImages())
					img.release();
			}
		}
		return allFiles;
	}

	// ---- Internal ----

	private ImgResponse generateWithRetry(ImgRequest request, int attempt) throws IOException, InterruptedException {
		try {
			return provider.generate(request);
		} catch (IOException e) {
			String msg = e.getMessage();
			boolean isRateLimit = msg != null && (msg.contains("429") || msg.contains("Throttling")
					|| msg.contains("RateQuota") || msg.contains("rate limit") || msg.contains("Too Many Requests"));

			if (isRateLimit && attempt < maxRetries) {
				long delay = rateLimitDelayMs * (attempt + 1);
				LOG.warn("Rate limited (attempt {}/{}), waiting {}ms...", attempt + 1, maxRetries + 1, delay);
				Thread.sleep(delay);
				return generateWithRetry(request, attempt + 1);
			}

			// HTTP 4xx (except 429) are client errors — retrying won't help
			boolean isClientError = msg != null &&
					(msg.contains("HTTP 400") || msg.contains("HTTP 401") ||
					 msg.contains("HTTP 402") || msg.contains("HTTP 403") ||
					 msg.contains("HTTP 404") || msg.contains("HTTP 405") ||
					 msg.contains("HTTP 408") || msg.contains("HTTP 409"));
			if (isClientError) {
				throw e;
			}

			if (attempt < maxRetries) {
				LOG.warn("Generation failed (attempt {}/{}), retrying in {}ms: {}", attempt + 1, maxRetries + 1,
						retryDelayMs, msg);
				Thread.sleep(retryDelayMs);
				return generateWithRetry(request, attempt + 1);
			}

			throw e;
		}
	}

	// ---- Types ----

	private record ImgResult(int index, ImgResponse response, Throwable error) {
	}

	/** Callback for parallel generation with index. */
	@FunctionalInterface
	public interface ItemCallback {
		/**
		 * @param index    position in the input list
		 * @param response the generated image response (null on error)
		 * @param error    the exception (null on success)
		 */
		void accept(int index, ImgResponse response, Throwable error);
	}

	// ---- Accessors ----

	public IImgProvider getProvider() {
		return provider;
	}

	public int getMaxRetries() {
		return maxRetries;
	}

	public long getRetryDelayMs() {
		return retryDelayMs;
	}

	public long getRateLimitDelayMs() {
		return rateLimitDelayMs;
	}

	private static JSONObject buildOptsJson(ImgOptions opts) {
		JSONObject j = new JSONObject();
		if (opts.getSize() != null)
			j.put("size", opts.getSize());
		if (opts.getQuality() != null)
			j.put("quality", opts.getQuality());
		if (opts.getStyle() != null)
			j.put("style", opts.getStyle());
		if (opts.getN() != null)
			j.put("n", opts.getN());
		if (opts.getResponseFormat() != null)
			j.put("response_format", opts.getResponseFormat());
		if (opts.getNegativePrompt() != null)
			j.put("negative_prompt", opts.getNegativePrompt());
		if (opts.getSteps() != null)
			j.put("steps", opts.getSteps());
		if (opts.getSeed() != null)
			j.put("seed", opts.getSeed());
		if (opts.getRefImageUrl() != null)
			j.put("ref_image", opts.getRefImageUrl());
		if (opts.getRefStrength() != null)
			j.put("ref_strength", opts.getRefStrength());
		if (opts.getRefMode() != null)
			j.put("ref_mode", opts.getRefMode());
		return j.length() > 0 ? j : null;
	}

	/**
	 * @return the ref
	 */
	public String getRef() {
		return ref;
	}

	/**
	 * @param ref the ref to set
	 */
	public ImgConcurrency setRef(String ref) {
		this.ref = ref;
		return this;
	}

	/**
	 * @return the refId
	 */
	public String getRefId() {
		return refId;
	}

	/**
	 * @param refId the refId to set
	 */
	public ImgConcurrency setRefId(String refId) {
		this.refId = refId;
		return this;
	}

}
