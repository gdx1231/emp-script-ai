package com.gdxsoft.ai.music;

import org.json.JSONObject;

/**
 * MiniMax 翻唱前处理响应。
 */
public class MusicCoverPreprocessResponse {
    private final String coverFeatureId;
    private final String formattedLyrics;
    private final String structureResult;
    private final Double audioDuration;
    private final String traceId;
    private final JSONObject raw;

    public MusicCoverPreprocessResponse(String coverFeatureId, String formattedLyrics,
            String structureResult, Double audioDuration, String traceId, JSONObject raw) {
        this.coverFeatureId = coverFeatureId;
        this.formattedLyrics = formattedLyrics;
        this.structureResult = structureResult;
        this.audioDuration = audioDuration;
        this.traceId = traceId;
        this.raw = raw;
    }

    public String getCoverFeatureId() { return coverFeatureId; }
    public String getFormattedLyrics() { return formattedLyrics; }
    public String getStructureResult() { return structureResult; }
    public JSONObject getStructure() {
        return structureResult == null || structureResult.isBlank() ? null : new JSONObject(structureResult);
    }
    public Double getAudioDuration() { return audioDuration; }
    public String getTraceId() { return traceId; }
    public JSONObject getRaw() { return raw; }
}
