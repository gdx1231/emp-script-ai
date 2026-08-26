package com.gdxsoft.ai.platform;

/**
 * 使用量记录
 */
public class UsageRecord {
    private String model;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long requestCount;
    private String timestamp;

    public UsageRecord() {}

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }

    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }

    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }

    public long getRequestCount() { return requestCount; }
    public void setRequestCount(long requestCount) { this.requestCount = requestCount; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
