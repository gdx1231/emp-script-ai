package com.gdxsoft.ai.providers;

/**
 * 支持的AI提供商类型
 */
public enum ProviderType {
    GEMINI("gemini"),
    GROK("grok"),
    OPENAI("openai"),
    QWEN("qwen");
    
    private final String name;
    
    ProviderType(String name) { 
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * 根据名称获取提供商类型
     */
    public static ProviderType fromName(String name) {
        if (name == null) {
            return null;
        }
        
        String lowerName = name.toLowerCase().trim();
        for (ProviderType type : values()) {
            if (type.getName().equals(lowerName)) {
                return type;
            }
        }
        return null;
    }
}