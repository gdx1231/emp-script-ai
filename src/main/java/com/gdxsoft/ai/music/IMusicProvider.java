package com.gdxsoft.ai.music;

import java.io.IOException;

/**
 * 音乐生成 Provider 契约。
 */
public interface IMusicProvider {
    MusicProviderType getProviderType();

    String getApiUrl();
    void setApiUrl(String url);

    String getApiKey();
    void setApiKey(String key);

    void setConfig(String key, String value);
    String getConfig(String key);

    MusicResponse generate(MusicRequest request) throws IOException, InterruptedException;

    /**
     * 提交任务（非阻塞），立即返回 taskId。
     * <p>
     * {@link MusicProviderBase} 提供本地异步回退（虚拟线程 + 内存注册表），
     * 因此所有 provider 默认都支持本方法；原生异步 provider 可覆盖。
     */
    default MusicTaskSubmit submitTask(MusicRequest request) throws Exception {
        throw new UnsupportedOperationException(
            getProviderType().getName() + " does not support non-blocking submitTask");
    }

    /**
     * 轮询任务状态（非阻塞），返回当前状态而不阻塞。
     * <p>
     * {@link MusicProviderBase} 提供本地异步回退（虚拟线程 + 内存注册表），
     * 因此所有 provider 默认都支持本方法；原生异步 provider 可覆盖。
     */
    default MusicTaskStatus pollTask(String taskId, MusicOptions opts) throws Exception {
        throw new UnsupportedOperationException(
            getProviderType().getName() + " does not support non-blocking pollTask");
    }

    /** 预处理参考音频，获得两步翻唱特征与 ASR 歌词。 */
    MusicCoverPreprocessResponse preprocessCover(MusicCoverPreprocessRequest request)
            throws IOException, InterruptedException;

    /** 生成或编辑歌词。 */
    MusicLyricsResponse generateLyrics(MusicLyricsRequest request)
            throws IOException, InterruptedException;

    /** 渲染翻唱前处理请求的脱敏 curl。 */
    String curl(MusicCoverPreprocessRequest request);

    /** 渲染歌词生成请求的脱敏 curl。 */
    String curl(MusicLyricsRequest request);

    /** 输出脱敏后的 curl，用于问题排查。 */
    String curl(MusicRequest request);
}
