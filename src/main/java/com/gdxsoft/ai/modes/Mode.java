package com.gdxsoft.ai.modes;

import java.util.List;

// Class to represent a Mode
public class Mode {
    private String name;
    private String description;
    private List<Step> steps;
    private List<SqlQuery> sqlQueries;

    public Mode(String name, String description, List<Step> steps, List<SqlQuery> sqlQueries) {
        this.name = name;
        this.description = description;
        this.steps = steps;
        this.sqlQueries = sqlQueries;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public List<SqlQuery> getSqlQueries() {
        return sqlQueries;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public void setSqlQueries(List<SqlQuery> sqlQueries) {
        this.sqlQueries = sqlQueries;
    }
}