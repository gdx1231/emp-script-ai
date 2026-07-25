package com.gdxsoft.ai.modes;

/**
 * <action> 元素的数据模型，来自 XML <actions> 下的子项。
 * <p>
 * Data model for <action> element under XML <actions> node.
 */
public class Action {
    private String name;
    private String description;
    private String className;
    private String aiProvider;
    private String aiModel;

    public Action(String name, String description, String className) {
        this.name = name;
        this.description = description;
        this.className = className;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }

    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }

    public Action clone() {
        Action a = new Action(name, description, className);
        a.setAiProvider(aiProvider);
        a.setAiModel(aiModel);
        return a;
    }
}
