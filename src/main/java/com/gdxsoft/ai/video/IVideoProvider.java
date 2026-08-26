package com.gdxsoft.ai.video;

import java.io.IOException;

/**
 * Contract for a single video generation provider.
 *
 * @since 1.3.0
 */
public interface IVideoProvider {

    VideoProviderType getProviderType();

    String getApiUrl();
    void setApiUrl(String url);

    String getApiKey();
    void setApiKey(String key);

    void setConfig(String key, String value);
    String getConfig(String key);

    /**
     * Generate video. Video generation is async — the provider
     * submits a task and polls for completion.
     */
    VideoResponse generate(VideoRequest request) throws IOException, InterruptedException;

    /**
     * Submit task (non-blocking). Returns immediately with task_id.
     * <p>
     * Default implementation throws {@link UnsupportedOperationException}.
     * Providers that support async submission (Doubao / MiniMax / Qwen) override this.
     */
    default VideoTaskSubmit submitTask(VideoRequest request) throws Exception {
        throw new UnsupportedOperationException(
            getProviderType().getName() + " does not support non-blocking submitTask");
    }

    /**
     * Poll task status (non-blocking). Returns current status without blocking.
     * <p>
     * Default implementation throws {@link UnsupportedOperationException}.
     * Providers that support async polling (Doubao / MiniMax / Qwen) override this.
     */
    default VideoTaskStatus pollTask(String taskId, VideoOptions opts) throws Exception {
        throw new UnsupportedOperationException(
            getProviderType().getName() + " does not support non-blocking pollTask");
    }

    /** Debug curl representation. */
    String curl(VideoRequest request);

    /**
     * Build a curl command to query the task status (for logging into ai_chat_msg
     * at submit time). Providers may override when the query URL differs.
     * <p>
     * Default: {@code GET apiUrl/{taskId}} with Bearer auth (Doubao / Jimeng style).
     *
     * @param taskId server-side async task id
     * @return curl command, or empty when taskId is null/empty
     */
    default String queryCurl(String taskId) {
        if (taskId == null || taskId.isEmpty()) return "";
        return "curl -X GET '" + getApiUrl() + "/" + taskId
                + "' \\\n  -H 'Authorization: Bearer " + getApiKey() + "'";
    }
}
