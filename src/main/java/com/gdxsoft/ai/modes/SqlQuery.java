package com.gdxsoft.ai.modes;

// Class to represent an SQL query
public class SqlQuery {
    private String name;
    private String description;
    private String content;

    public SqlQuery(String name, String description, String content) {
        this.name = name;
        this.description = description;
        this.content = content;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }

    // Setters
    /**
     * Sets the name of the SQL query
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the description of the SQL query
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the content of the SQL query
     * @param content the content to set
     */
    public void setContent(String content) {
        this.content = content;
    }
}