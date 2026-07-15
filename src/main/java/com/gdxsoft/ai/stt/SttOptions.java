package com.gdxsoft.ai.stt;

/**
 * Provider-agnostic options for an STT request.
 * <p>
 * Each provider maps these to its own request shape (multipart fields, JSON config, etc.).
 *
 * @since 1.1.0
 */
public class SttOptions {
    private String model = "whisper-1";
    private String language;
    private String responseFormat = "json";
    private String prompt;
    private Double temperature;

    public SttOptions() {}

    public String getModel() { return model; }
    public SttOptions withModel(String model) { this.model = model; return this; }
    public SttOptions setModel(String model) { this.model = model; return this; }

    public String getLanguage() { return language; }
    public SttOptions withLanguage(String language) { this.language = language; return this; }
    public SttOptions setLanguage(String language) { this.language = language; return this; }

    public String getResponseFormat() { return responseFormat; }
    public SttOptions withResponseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }
    public SttOptions setResponseFormat(String responseFormat) { this.responseFormat = responseFormat; return this; }

    public String getPrompt() { return prompt; }
    public SttOptions withPrompt(String prompt) { this.prompt = prompt; return this; }
    public SttOptions setPrompt(String prompt) { this.prompt = prompt; return this; }

    public Double getTemperature() { return temperature; }
    public SttOptions withTemperature(Double temperature) { this.temperature = temperature; return this; }
    public SttOptions setTemperature(Double temperature) { this.temperature = temperature; return this; }
}