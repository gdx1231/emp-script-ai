package com.gdxsoft.ai.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import org.json.JSONObject;

/**
 * MiniMax Music 响应的统一模型。
 */
public class MusicResponse {
    private final String audioHex;
    private final byte[] audioBytes;
    private final String audioUrl;
    private final Integer status;
    private final String traceId;
    private final JSONObject extraInfo;
    private final JSONObject raw;

    public MusicResponse(String audioHex, String audioUrl, Integer status,
            String traceId, JSONObject extraInfo, JSONObject raw) {
        this.audioHex = audioHex;
        this.audioBytes = audioHex == null || audioHex.isEmpty()
                ? null : HexFormat.of().parseHex(audioHex);
        this.audioUrl = audioUrl;
        this.status = status;
        this.traceId = traceId;
        this.extraInfo = extraInfo;
        this.raw = raw;
    }

    public boolean hasAudioBytes() { return audioBytes != null && audioBytes.length > 0; }
    public String getAudioHex() { return audioHex; }
    public byte[] getAudioBytes() { return audioBytes; }
    public String getAudioUrl() { return audioUrl; }
    public Integer getStatus() { return status; }
    public String getTraceId() { return traceId; }
    public JSONObject getExtraInfo() { return extraInfo; }
    public JSONObject getRaw() { return raw; }

    /** 将 hex 音频写入文件。 */
    public Path save(Path output) throws IOException {
        if (!hasAudioBytes()) {
            throw new IOException("Music response has no hex audio data; use audioUrl instead");
        }
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Files.write(output, audioBytes);
        return output;
    }

    public static byte[] hexToBytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
