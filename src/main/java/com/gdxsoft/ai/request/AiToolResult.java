package com.gdxsoft.ai.request;

/**
 * 工具执行结果 — 工具执行后返回的内容，回传给 AI 模型。
 * <p>
 * 格式：
 * <pre>
 * {
 *   "type": "tool_result",
 *   "toolCallId": "call_abc123",
 *   "content": "北京今日天气：晴，25°C"
 * }
 * </pre>
 *
 * @since 1.1.0
 */
public class AiToolResult extends AiContent {
    /** 对应的工具调用 ID */
    private final String toolCallId;
    /** 工具执行结果内容 */
    private final String content;

    public AiToolResult(String toolCallId, String content) {
        super(AiContentType.TOOL_RESULT);
        this.toolCallId = toolCallId;
        this.content = content;
    }

    public String getToolCallId() { return toolCallId; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        return "ToolResult{toolCallId='" + toolCallId + "', content=" + content + "}";
    }
}
