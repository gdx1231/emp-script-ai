package com.gdxsoft.ai.video;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

/**
 * Unified result of a video generation request.
 * <p>
 * Video generation is always async — the provider submits a task and polls.
 * The response contains the final video URL(s).
 *
 * @since 1.3.0
 */
public class VideoResponse {
    private final List<GeneratedVideo> videos;
    private final String taskId;
    private final String model;
    private final JSONObject usage;
    private final JSONObject raw;
    private final String lastFrameUrl;  // 尾帧图 URL（return_last_frame=true 时）

    public VideoResponse(List<GeneratedVideo> videos, String taskId,
                         String model, JSONObject usage, JSONObject raw) {
        this(videos, taskId, model, usage, raw, null);
    }

    public VideoResponse(List<GeneratedVideo> videos, String taskId,
                         String model, JSONObject usage, JSONObject raw,
                         String lastFrameUrl) {
        this.videos = videos == null ? Collections.emptyList()
                : Collections.unmodifiableList(videos);
        this.taskId = taskId;
        this.model = model;
        this.usage = usage;
        this.raw = raw;
        this.lastFrameUrl = lastFrameUrl;
    }

    public List<GeneratedVideo> getVideos() { return videos; }
    public GeneratedVideo getFirstVideo() { return videos.isEmpty() ? null : videos.get(0); }
    public String getTaskId() { return taskId; }
    public String getModel() { return model; }
    public JSONObject getUsage() { return usage; }
    public JSONObject getRaw() { return raw; }
    public String getLastFrameUrl() { return lastFrameUrl; }

    /**
     * A single generated video.
     */
    public static class GeneratedVideo {
        private final String url;
        private final String coverUrl;    // thumbnail
        private final Double duration;
        private final String resolution;

        public GeneratedVideo(String url, String coverUrl, Double duration, String resolution) {
            this.url = url;
            this.coverUrl = coverUrl;
            this.duration = duration;
            this.resolution = resolution;
        }

        public String getUrl() { return url; }
        public String getCoverUrl() { return coverUrl; }
        public Double getDuration() { return duration; }
        public String getResolution() { return resolution; }
    }
}
