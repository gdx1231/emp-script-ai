package com.gdxsoft.ai.modes;

// Class to represent a Prompt
public class Prompt {
    private String name;
    private String role;
    private String description;
    private String sqlRef;
    private String dataType;
    private String prefix;
    private String content;
    // Optional: when dataType is 'json', group result by this field
    private String dataGroupField;

    public Prompt(String name, String role, String description, String sqlRef, String dataType, String prefix,
            String content) {
        this.name = name;
        this.role = role;
        this.description = description;
        this.sqlRef = sqlRef;
        this.dataType = dataType;
        this.prefix = prefix;
        this.content = content;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getDescription() {
        return description;
    }

    public String getSqlRef() {
        return sqlRef;
    }

    public String getDataType() {
        return dataType;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getContent() {
        return content;
    }

    public String getDataGroupField() {
        return dataGroupField;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSqlRef(String sqlRef) {
        this.sqlRef = sqlRef;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setDataGroupField(String dataGroupField) {
        this.dataGroupField = dataGroupField;
    }
}