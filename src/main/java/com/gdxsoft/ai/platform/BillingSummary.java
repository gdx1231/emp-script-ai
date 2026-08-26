package com.gdxsoft.ai.platform;

import java.util.ArrayList;
import java.util.List;

/**
 * 费用汇总
 */
public class BillingSummary {
    private String provider;
    private String startDate;
    private String endDate;
    private String currency = "USD";
    private List<BillingRecord> records = new ArrayList<>();
    private double totalCost;

    public BillingSummary() {}

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public List<BillingRecord> getRecords() { return records; }
    public void setRecords(List<BillingRecord> records) { this.records = records; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public void addRecord(BillingRecord record) {
        records.add(record);
        totalCost += record.getTotalCost();
    }
}
