package com.gdxsoft.ai.music;

/**
 * MiniMax 翻唱前处理请求。
 */
public class MusicCoverPreprocessRequest {
    public static final String MODEL = "music-cover";

    private String audioUrl;
    private String audioBase64;

    public MusicCoverPreprocessRequest() {}

    public MusicCoverPreprocessRequest(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public static MusicCoverPreprocessRequest audioUrl(String url) {
        return new MusicCoverPreprocessRequest(url);
    }

    public static MusicCoverPreprocessRequest audioBase64(String base64) {
        MusicCoverPreprocessRequest request = new MusicCoverPreprocessRequest();
        request.audioBase64 = base64;
        return request;
    }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getAudioBase64() { return audioBase64; }
    public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
}
