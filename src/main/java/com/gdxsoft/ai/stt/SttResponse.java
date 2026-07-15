package com.gdxsoft.ai.stt;

import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Result of an STT transcription.
 *
 * @since 1.1.0
 */
public class SttResponse {
    private final String text;
    private final String language;
    private final Double duration;
    private final List<Segment> segments;
    private final JSONObject raw;

    public SttResponse(String text, String language, Double duration, List<Segment> segments, JSONObject raw) {
        this.text = text;
        this.language = language;
        this.duration = duration;
        this.segments = segments == null ? null : Collections.unmodifiableList(segments);
        this.raw = raw;
    }

    public String getText() { return text; }
    public String getLanguage() { return language; }
    public Double getDuration() { return duration; }
    public List<Segment> getSegments() { return segments; }
    public JSONObject getRaw() { return raw; }

    /** Convenience: transcript text only. */
    public static SttResponse simple(String text) {
        return new SttResponse(text, null, null, null, null);
    }

    /** A timed segment. */
    public record Segment(double start, double end, String text, String speaker) {}

    /**
     * Parse an OpenAI-shaped verbose_json response (or close derivatives).
     *
     * @param root  the JSON object returned by the provider
     * @param parseSegments if true, populate {@code segments} from {@code segments[]}
     */
    public static SttResponse fromOpenAiVerboseJson(JSONObject root, boolean parseSegments) {
        String text = root.optString("text", null);
        String lang = root.optString("language", null);
        double dur = root.optDouble("duration", Double.NaN);
        Double duration = Double.isNaN(dur) ? null : dur;

        List<Segment> segs = null;
        if (parseSegments) {
            JSONArray arr = root.optJSONArray("segments");
            if (arr != null) {
                segs = new java.util.ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    double start = s.optDouble("start", 0.0);
                    double end = s.optDouble("end", 0.0);
                    String t = s.optString("text", "");
                    String spk = s.optString("speaker", null);
                    segs.add(new Segment(start, end, t, spk));
                }
            }
        }
        return new SttResponse(text, lang, duration, segs, root);
    }
}