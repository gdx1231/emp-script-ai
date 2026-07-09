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
    // optional API reference name for API calls
    private String api;
    // whether this step uses stream mode, default true
    private boolean stream = true;
    // optional SQL reference name for action
    private String actionSqlRef;
    // whether this step is an internal call (not displayed to user), default false
    private boolean innerCall = false;
    // whether to extract only user messages from parent chat for multi-turn conversations
    private boolean multiOnlyUserMsg = false;
    // comma-separated required parameter names for validation (e.g., "people_num,departure_date")
    private String validateParams;
    // cache seconds for prompts, 0 means no cache
    private int cachedSeconds = 0;

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

    /**
     * 获取API引用名称
     * 
     * @return API引用名称
     */
    public String getApi() {
        return api;
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

    /**
     * 设置API引用名称
     * 
     * @param api API引用名称
     */
    public void setApi(String api) {
        this.api = api;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public void setActionSqlRef(String actionSqlRef) {
        this.actionSqlRef = actionSqlRef;
    }

    /**
     * 是否为内部调用（不向用户展示）
     * @return innerCall
     */
    public boolean isInnerCall() {
        return innerCall;
    }

    /**
     * 设置是否为内部调用
     * @param innerCall true=内部调用
     */
    public void setInnerCall(boolean innerCall) {
        this.innerCall = innerCall;
    }

    /**
     * 是否仅提取父级 chat 中的用户消息
     * @return multiOnlyUserMsg
     */
    public boolean isMultiOnlyUserMsg() {
        return multiOnlyUserMsg;
    }

    /**
     * 设置是否仅提取父级 chat 用户消息
     * @param multiOnlyUserMsg true=仅提取用户消息
     */
    public void setMultiOnlyUserMsg(boolean multiOnlyUserMsg) {
        this.multiOnlyUserMsg = multiOnlyUserMsg;
    }

    /**
     * 获取校验参数列表（逗号分隔）
     * @return validateParams
     */
    public String getValidateParams() {
        return validateParams;
    }

    /**
     * 设置校验参数列表
     * @param validateParams 逗号分隔的必填参数名
     */
    public void setValidateParams(String validateParams) {
        this.validateParams = validateParams;
    }

    /**
     * 获取缓存秒数
     * @return cachedSeconds
     */
    public int getCachedSeconds() {
        return cachedSeconds;
    }

    /**
     * 设置缓存秒数
     * @param cachedSeconds 缓存秒数
     */
    public void setCachedSeconds(int cachedSeconds) {
        this.cachedSeconds = cachedSeconds;
    }
}