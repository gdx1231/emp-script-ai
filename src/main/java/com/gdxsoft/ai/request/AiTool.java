package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 工具定义 — 统一格式，自动转换为各 Provider 的格式。
 * <p>
 * 支持三种 Provider 格式：
 * <ul>
 *   <li><b>OpenAI 风格</b>：{@code {type:"function", function:{name,description,parameters}}}</li>
 *   <li><b>Anthropic 风格</b>：{@code {name,description,input_schema}}</li>
 *   <li><b>Gemini 风格</b>：{@code {functionDeclarations:[{name,description,parameters}]}}</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * AiTool weather = AiTool.builder()
 *     .name("get_weather")
 *     .description("获取指定城市的天气信息")
 *     .param("city", "string", "城市名称，如北京、上海", true)
 *     .param("date", "string", "日期，格式 YYYY-MM-DD，默认为今天", false)
 *     .build();
 *
 * reqData.tools(weather, searchTool);
 * </pre>
 *
 * @since 1.1.0
 */
public class AiTool {

    private final String name;
    private final String description;
    private final Map<String, ToolParam> params;
    private final boolean strict;

    private AiTool(String name, String description, Map<String, ToolParam> params, boolean strict) {
        this.name = name;
        this.description = description;
        this.params = params;
        this.strict = strict;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, ToolParam> getParams() { return params; }
    public boolean isStrict() { return strict; }

    /**
     * 转换为 OpenAI 风格的工具 JSON。
     */
    public JSONObject toOpenAiFormat() {
        JSONObject result = new JSONObject();
        result.put("type", "function");

        JSONObject func = new JSONObject();
        func.put("name", name);
        if (description != null) func.put("description", description);
        func.put("parameters", buildJsonSchema());
        if (strict) func.put("strict", true);

        result.put("function", func);
        return result;
    }

    /**
     * 转换为 Anthropic 风格的工具 JSON。
     */
    public JSONObject toAnthropicFormat() {
        JSONObject result = new JSONObject();
        result.put("name", name);
        if (description != null) result.put("description", description);
        result.put("input_schema", buildJsonSchema());
        return result;
    }

    /**
     * 转换为 Gemini 风格的函数声明 JSON。
     */
    public JSONObject toGeminiFormat() {
        JSONObject result = new JSONObject();
        result.put("name", name);
        if (description != null) result.put("description", description);
        result.put("parameters", buildJsonSchema());
        return result;
    }

    /**
     * 构建 JSON Schema 参数定义。
     */
    private JSONObject buildJsonSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        if (params != null && !params.isEmpty()) {
            JSONObject properties = new JSONObject();
            JSONArray required = new JSONArray();

            for (Map.Entry<String, ToolParam> entry : params.entrySet()) {
                ToolParam p = entry.getValue();
                JSONObject prop = new JSONObject();
                prop.put("type", p.type);
                if (p.description != null) prop.put("description", p.description);
                if (p.enumValues != null && p.enumValues.length > 0) {
                    JSONArray enums = new JSONArray();
                    for (String v : p.enumValues) enums.put(v);
                    prop.put("enum", enums);
                }
                properties.put(entry.getKey(), prop);

                if (p.required) {
                    required.put(entry.getKey());
                }
            }

            schema.put("properties", properties);
            if (required.length() > 0) {
                schema.put("required", required);
            }
        }

        return schema;
    }

    /**
     * 将多个工具转换为 OpenAI 格式数组。
     */
    public static JSONArray toOpenAiArray(AiTool... tools) {
        JSONArray arr = new JSONArray();
        for (AiTool t : tools) arr.put(t.toOpenAiFormat());
        return arr;
    }

    /**
     * 将多个工具转换为 Anthropic 格式数组。
     */
    public static JSONArray toAnthropicArray(AiTool... tools) {
        JSONArray arr = new JSONArray();
        for (AiTool t : tools) arr.put(t.toAnthropicFormat());
        return arr;
    }

    /**
     * 将多个工具转换为 Gemini 格式数组。
     */
    public static JSONArray toGeminiArray(AiTool... tools) {
        JSONArray funcArr = new JSONArray();
        for (AiTool t : tools) funcArr.put(t.toGeminiFormat());
        JSONArray result = new JSONArray();
        JSONObject wrapper = new JSONObject();
        wrapper.put("functionDeclarations", funcArr);
        result.put(wrapper);
        return result;
    }

    /**
     * 创建 Builder。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 工具参数定义。
     */
    public static class ToolParam {
        public final String type;
        public final String description;
        public final boolean required;
        public final String[] enumValues;

        public ToolParam(String type, String description, boolean required, String... enumValues) {
            this.type = type;
            this.description = description;
            this.required = required;
            this.enumValues = enumValues;
        }
    }

    /**
     * Builder 用于构建 AiTool。
     */
    public static class Builder {
        private String name;
        private String description;
        private final Map<String, ToolParam> params = new LinkedHashMap<>();
        private boolean strict;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder strict(boolean strict) { this.strict = strict; return this; }

        /**
         * 添加参数。
         *
         * @param name        参数名
         * @param type        JSON Schema 类型：string, number, integer, boolean, array, object
         * @param description 参数描述
         * @param required    是否必填
         */
        public Builder param(String name, String type, String description, boolean required) {
            params.put(name, new ToolParam(type, description, required));
            return this;
        }

        /**
         * 添加枚举参数。
         */
        public Builder enumParam(String name, String description, boolean required, String... values) {
            params.put(name, new ToolParam("string", description, required, values));
            return this;
        }

        public AiTool build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Tool name is required");
            }
            return new AiTool(name, description, params, strict);
        }
    }
}
