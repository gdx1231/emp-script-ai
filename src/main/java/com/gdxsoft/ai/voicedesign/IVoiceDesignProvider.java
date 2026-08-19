package com.gdxsoft.ai.voicedesign;

import java.io.IOException;

/**
 * 声音设计 provider 接口。
 * <p>
 * 每个实现封装一种声音设计 API 协议（鉴权、请求格式、响应解析）。
 *
 * @since 1.1.0
 */
public interface IVoiceDesignProvider {

    VoiceDesignProviderType getProviderType();

    /** Provider API URL（完整端点）。 */
    String getApiUrl();
    void setApiUrl(String url);

    /** API Key。 */
    String getApiKey();
    void setApiKey(String key);

    /** provider 特定配置项。 */
    void setConfig(String key, String value);
    String getConfig(String key);

    /**
     * 通过声音描述创建音色。
     *
     * @param request 声音设计请求（含声音描述、预览文本等）
     * @return 创建结果（含音色 ID、预览音频等）
     * @throws IOException          HTTP 或传输错误
     * @throws InterruptedException 线程中断
     */
    VoiceDesignResponse create(VoiceDesignRequest request) throws IOException, InterruptedException;

    /**
     * 查询音色状态/详情。
     *
     * @param voiceId 音色 ID
     * @return 查询结果
     * @throws IOException          HTTP 或传输错误
     * @throws InterruptedException 线程中断
     */
    VoiceDesignResponse query(String voiceId) throws IOException, InterruptedException;

    /** 生成 curl 调试命令（含真实 apiKey，便于排查）。 */
    String curl(VoiceDesignRequest request);
}
