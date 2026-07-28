package com.gdxsoft.ai.voiceclone;

import java.io.IOException;

/**
 * 声音克隆 provider 接口。
 * <p>
 * 每个实现封装一种声音克隆 API 协议（鉴权、请求格式、响应解析）。
 *
 * @since 1.1.0
 */
public interface IVoiceCloneProvider {

    VoiceCloneProviderType getProviderType();

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
     * 执行声音克隆（训练/升级）。
     *
     * @param request 克隆请求（含音频、speaker_id 等）
     * @return 克隆结果（含 speaker_id、状态等）
     * @throws IOException          HTTP 或传输错误
     * @throws InterruptedException 线程中断
     */
    VoiceCloneResponse clone(VoiceCloneRequest request) throws IOException, InterruptedException;

    /**
     * 查询声音状态。
     *
     * @param speakerId 声音 ID
     * @return 查询结果
     * @throws IOException          HTTP 或传输错误
     * @throws InterruptedException 线程中断
     */
    VoiceCloneResponse query(String speakerId) throws IOException, InterruptedException;

    /** 生成 curl 调试命令（敏感信息脱敏）。 */
    String curl(VoiceCloneRequest request);
}
