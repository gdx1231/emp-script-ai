package com.gdxsoft.ai.modes;

import java.util.List;

public class Step {
    private String name;
    private String description;
    private List<Prompt> prompts;

    public Step(String name, String description, List<Prompt> prompts) {
        this.name = name;
        this.description = description;
        this.prompts = prompts;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Prompt> getPrompts() {
        return prompts;
    }

    // Setters
    /**
     * Sets the name of the step
     * 
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the description of the step
     * 
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the prompts of the step
     * 
     * @param prompts the prompts to set
     */
    public void setPrompts(List<Prompt> prompts) {
        this.prompts = prompts;
    }
}