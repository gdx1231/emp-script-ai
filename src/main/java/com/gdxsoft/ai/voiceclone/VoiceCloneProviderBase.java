package com.gdxsoft.ai.voiceclone;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 声音克隆 provider 公共基类——配置 map、默认值、apiUrl/apiKey 状态。
 *
 * @since 1.1.0
 */
public abstract class VoiceCloneProviderBase implements IVoiceCloneProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(VoiceCloneProviderBase.class);

    protected String apiUrl;
    protected String apiKey;
    protected final Map<String, String> extras = new HashMap<>();

    @Override
    public String getApiUrl() { return apiUrl; }
    @Override
    public void setApiUrl(String url) { this.apiUrl = url; }

    @Override
    public String getApiKey() { return apiKey; }
    @Override
    public void setApiKey(String key) { this.apiKey = key; }

    @Override
    public void setConfig(String key, String value) {
        if (key == null) return;
        if (value == null) extras.remove(key);
        else extras.put(key, value);
    }

    @Override
    public String getConfig(String key) {
        return key == null ? null : extras.get(key);
    }

    /** 构造 curl 调试命令时输出 header（敏感值脱敏）。 */
    protected StringBuilder curlHeader(StringBuilder sb, String name, String value, boolean isSensitive) {
        sb.append("-H '").append(name).append(": ");
        if (value == null) sb.append("'");
        else if (isSensitive) sb.append("****'");
        else sb.append(value.replace("'", "'\\''")).append("'");
        return sb;
    }
}
