package com.gdxsoft.ai.music;

/**
 * MiniMax 歌词生成请求。
 */
public class MusicLyricsRequest {
    public static final String MODE_WRITE_FULL_SONG = "write_full_song";
    public static final String MODE_EDIT = "edit";

    private String mode = MODE_WRITE_FULL_SONG;
    private String prompt;
    private String lyrics;
    private String title;

    public MusicLyricsRequest() {}

    public MusicLyricsRequest(String prompt) {
        this.prompt = prompt;
    }

    public static MusicLyricsRequest writeFullSong(String prompt) {
        return new MusicLyricsRequest(prompt).setMode(MODE_WRITE_FULL_SONG);
    }

    public static MusicLyricsRequest edit(String prompt, String lyrics) {
        return new MusicLyricsRequest(prompt).setMode(MODE_EDIT).setLyrics(lyrics);
    }

    public String getMode() { return mode; }
    public MusicLyricsRequest setMode(String mode) { this.mode = mode; return this; }

    public String getPrompt() { return prompt; }
    public MusicLyricsRequest setPrompt(String prompt) { this.prompt = prompt; return this; }

    public String getLyrics() { return lyrics; }
    public MusicLyricsRequest setLyrics(String lyrics) { this.lyrics = lyrics; return this; }

    public String getTitle() { return title; }
    public MusicLyricsRequest setTitle(String title) { this.title = title; return this; }
}
