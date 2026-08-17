package com.gdxsoft.ai.music;

import org.json.JSONObject;

/**
 * MiniMax 歌词生成响应。
 */
public class MusicLyricsResponse {
    private final String songTitle;
    private final String styleTags;
    private final String lyrics;
    private final JSONObject raw;

    public MusicLyricsResponse(String songTitle, String styleTags, String lyrics, JSONObject raw) {
        this.songTitle = songTitle;
        this.styleTags = styleTags;
        this.lyrics = lyrics;
        this.raw = raw;
    }

    public String getSongTitle() { return songTitle; }
    public String getStyleTags() { return styleTags; }
    public String getLyrics() { return lyrics; }
    public JSONObject getRaw() { return raw; }
}
