package com.gdxsoft.ai.platform;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用量汇总
 */
public class UsageSummary {
    private String provider;
    private String startDate;
    private String endDate;
    private List<UsageRecord> records = new ArrayList<>();
    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private long totalRequests;

    public UsageSummary() {}

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public List<UsageRecord> getRecords() { return records; }
    public void setRecords(List<UsageRecord> records) { this.records = records; }

    public long getTotalPromptTokens() { return totalPromptTokens; }
    public void setTotalPromptTokens(long totalPromptTokens) { this.totalPromptTokens = totalPromptTokens; }

    public long getTotalCompletionTokens() { return totalCompletionTokens; }
    public void setTotalCompletionTokens(long totalCompletionTokens) { this.totalCompletionTokens = totalCompletionTokens; }

    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public void addRecord(UsageRecord record) {
        records.add(record);
        totalPromptTokens += record.getPromptTokens();
        totalCompletionTokens += record.getCompletionTokens();
        totalTokens += record.getTotalTokens();
        totalRequests += record.getRequestCount();
    }
}
