package com.gdxsoft.ai.modes;

import java.util.List;

/**
 * 流程中的一步（<step>）。包含描述、prompts、可选 action、以及是否流式。
 * <p>
 * A step in the flow (<step>), including description, prompts, optional action,
 * and whether to use streaming.
 */
public class Step {
    private String name;
    private String description;
    private List<Prompt> prompts;
    // optional action reference name, e.g., createEnqJny
    private String action;
    // whether this step uses stream mode, default true
    private boolean stream = true;
    // optional SQL reference name for action
    private String actionSqlRef;

    /** Create a step without action. */
    public Step(String name, String description, List<Prompt> prompts) {
        this.name = name;
        this.description = description;
        this.prompts = prompts;
    }

    /** Create a step with action. */
    public Step(String name, String description, List<Prompt> prompts, String action) {
        this.name = name;
        this.description = description;
        this.prompts = prompts;
        this.action = action;
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

    public String getAction() {
        return action;
    }

    public boolean isStream() {
        return stream;
    }

    public String getActionSqlRef() {
        return actionSqlRef;
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

    public void setAction(String action) {
        this.action = action;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public void setActionSqlRef(String actionSqlRef) {
        this.actionSqlRef = actionSqlRef;
    }
}