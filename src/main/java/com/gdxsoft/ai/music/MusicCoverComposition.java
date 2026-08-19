package com.gdxsoft.ai.music;

/** 两步翻唱（预处理 + 生成）组成的一次完整结果。 */
public class MusicCoverComposition {
    private final MusicCoverPreprocessResponse preprocess;
    private final MusicResponse music;

    public MusicCoverComposition(MusicCoverPreprocessResponse preprocess, MusicResponse music) {
        this.preprocess = preprocess;
        this.music = music;
    }

    public MusicCoverPreprocessResponse getPreprocess() { return preprocess; }
    public MusicResponse getMusic() { return music; }
}
