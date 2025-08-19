package com.gdxsoft.ai.modes;

/**
 * <action> 元素的数据模型，来自 XML <actions> 下的子项。
 * <p>
 * Data model for <action> element under XML <actions> node.
 */
public class Action {
    private String name;
    private String description;
    // maps from XML attribute 'class'
    private String className;

    public Action(String name, String description, String className) {
        this.name = name;
        this.description = description;
        this.className = className;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getClassName() {
        return className;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setClassName(String className) {
        this.className = className;
    }
}
