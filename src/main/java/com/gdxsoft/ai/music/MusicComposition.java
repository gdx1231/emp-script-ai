package com.gdxsoft.ai.music;

/** 歌词生成与音乐生成组成的一次完整创作结果。 */
public class MusicComposition {
    private final MusicLyricsResponse lyrics;
    private final MusicResponse music;

    public MusicComposition(MusicLyricsResponse lyrics, MusicResponse music) {
        this.lyrics = lyrics;
        this.music = music;
    }

    public MusicLyricsResponse getLyrics() { return lyrics; }
    public MusicResponse getMusic() { return music; }
}
