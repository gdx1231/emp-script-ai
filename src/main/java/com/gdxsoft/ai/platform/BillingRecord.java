package com.gdxsoft.ai.platform;

/**
 * 费用记录
 */
public class BillingRecord {
    private String model;
    private double inputCost;
    private double outputCost;
    private double totalCost;
    private String period;
    private String currency = "USD";

    public BillingRecord() {}

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public double getInputCost() { return inputCost; }
    public void setInputCost(double inputCost) { this.inputCost = inputCost; }

    public double getOutputCost() { return outputCost; }
    public void setOutputCost(double outputCost) { this.outputCost = outputCost; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
