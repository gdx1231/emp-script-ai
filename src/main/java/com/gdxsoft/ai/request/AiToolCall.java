package com.gdxsoft.ai.request;

/**
 * AI 工具调用 — 表示 AI 模型请求调用某个工具。
 * <p>
 * 通常出现在 assistant 消息中，格式为：
 * <pre>
 * {
 *   "type": "tool_use",
 *   "id": "call_abc123",
 *   "name": "get_weather",
 *   "arguments": "{\"city\":\"北京\"}"
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public class AiToolCall extends AiContent {
    /** 工具调用唯一标识 */
    private final String id;
    /** 工具名称 */
    private final String name;
    /** 工具参数（JSON 字符串） */
    private final String arguments;

    public AiToolCall(String id, String name, String arguments) {
        super(AiContentType.TOOL_USE);
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getArguments() { return arguments; }

    @Override
    public String toString() {
        return "ToolCall{id='" + id + "', name='" + name + "', args=" + arguments + "}";
    }
}
