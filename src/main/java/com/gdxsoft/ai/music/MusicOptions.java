package com.gdxsoft.ai.music;

/**
 * AI 音乐生成参数。
 */
public class MusicOptions {
    /** 默认使用文档推荐的 Music 3.0 模型。 */
    public static final String DEFAULT_MODEL = "music-3.0";

    private String model = DEFAULT_MODEL;
    private String lyrics;
    private boolean stream;
    private String outputFormat = "hex";
    private Integer sampleRate = 44100;
    private Integer bitrate = 256000;
    private String format = "mp3";
    private Boolean watermark;
    private Boolean lyricsOptimizer;
    private Boolean instrumental;
    private String audioUrl;
    private String audioBase64;
    private String coverFeatureId;

    public String getModel() { return model; }
    public MusicOptions model(String model) { this.model = model; return this; }
    public MusicOptions setModel(String model) { this.model = model; return this; }

    public String getLyrics() { return lyrics; }
    public MusicOptions lyrics(String lyrics) { this.lyrics = lyrics; return this; }
    public MusicOptions setLyrics(String lyrics) { this.lyrics = lyrics; return this; }

    public boolean isStream() { return stream; }
    public MusicOptions stream(boolean stream) { this.stream = stream; return this; }
    public MusicOptions setStream(boolean stream) { this.stream = stream; return this; }

    /** 返回格式：{@code hex} 或 {@code url}；流式仅支持 {@code hex}。 */
    public String getOutputFormat() { return outputFormat; }
    public MusicOptions outputFormat(String outputFormat) { this.outputFormat = outputFormat; return this; }
    public MusicOptions setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; return this; }

    public Integer getSampleRate() { return sampleRate; }
    public MusicOptions sampleRate(Integer sampleRate) { this.sampleRate = sampleRate; return this; }
    public MusicOptions setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; return this; }

    public Integer getBitrate() { return bitrate; }
    public MusicOptions bitrate(Integer bitrate) { this.bitrate = bitrate; return this; }
    public MusicOptions setBitrate(Integer bitrate) { this.bitrate = bitrate; return this; }

    /** 音频编码：{@code mp3}、{@code wav} 或 {@code pcm}。 */
    public String getFormat() { return format; }
    public MusicOptions format(String format) { this.format = format; return this; }
    public MusicOptions setFormat(String format) { this.format = format; return this; }

    public Boolean getWatermark() { return watermark; }
    public MusicOptions watermark(Boolean watermark) { this.watermark = watermark; return this; }
    public MusicOptions setWatermark(Boolean watermark) { this.watermark = watermark; return this; }

    /** 开启后可仅提供 prompt，由服务端自动生成歌词。 */
    public Boolean getLyricsOptimizer() { return lyricsOptimizer; }
    public MusicOptions lyricsOptimizer(Boolean lyricsOptimizer) {
        this.lyricsOptimizer = lyricsOptimizer; return this;
    }
    public MusicOptions setLyricsOptimizer(Boolean lyricsOptimizer) {
        this.lyricsOptimizer = lyricsOptimizer; return this;
    }

    /** 纯音乐模式（无人声）。 */
    public Boolean getInstrumental() { return instrumental; }
    public MusicOptions instrumental(Boolean instrumental) { this.instrumental = instrumental; return this; }
    public MusicOptions setInstrumental(Boolean instrumental) { this.instrumental = instrumental; return this; }

    public String getAudioUrl() { return audioUrl; }
    public MusicOptions audioUrl(String audioUrl) { this.audioUrl = audioUrl; return this; }
    public MusicOptions setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; return this; }

    public String getAudioBase64() { return audioBase64; }
    public MusicOptions audioBase64(String audioBase64) { this.audioBase64 = audioBase64; return this; }
    public MusicOptions setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; return this; }

    public String getCoverFeatureId() { return coverFeatureId; }
    public MusicOptions coverFeatureId(String coverFeatureId) { this.coverFeatureId = coverFeatureId; return this; }
    public MusicOptions setCoverFeatureId(String coverFeatureId) { this.coverFeatureId = coverFeatureId; return this; }
}
