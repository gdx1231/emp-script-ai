package com.gdxsoft.ai.platform;

/**
 * 模型元数据信息
 */
public class ModelInfo {
    private String id;
    private String name;
    private String provider;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private Double inputPricePer1M;
    private Double outputPricePer1M;
    private String modality;
    private String status;
    private String created;
    private String owner;

    public ModelInfo() {}

    public ModelInfo(String id, String provider) {
        this.id = id;
        this.provider = provider;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }

    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(Integer maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    public Double getInputPricePer1M() { return inputPricePer1M; }
    public void setInputPricePer1M(Double inputPricePer1M) { this.inputPricePer1M = inputPricePer1M; }

    public Double getOutputPricePer1M() { return outputPricePer1M; }
    public void setOutputPricePer1M(Double outputPricePer1M) { this.outputPricePer1M = outputPricePer1M; }

    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    @Override
    public String toString() {
        return "ModelInfo{id='" + id + "', provider='" + provider + "', contextWindow=" + contextWindow + "}";
    }
}
