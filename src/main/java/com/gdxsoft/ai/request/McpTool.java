package com.gdxsoft.ai.request;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 工具适配器。
 * <p>
 * 将 MCP Server 提供的工具转换为 {@link AiTool}，
 * 使得 AI 模型可以通过统一的 tool calling 机制调用 MCP 工具。
 * <p>
 * MCP 是一种标准化协议，用于连接 AI 模型与外部工具和数据源。
 * 本实现支持通过 HTTP+SSE 或 stdio 与 MCP Server 通信。
 * <p>
 * 使用示例：
 * <pre>
 * // 从 MCP Server 的工具定义创建 AiTool
 * AiTool mcpTool = McpTool.fromMcpDefinition(
 *     "filesystem",                    // tool name
 *     "读取和写入文件系统",             // description
 *     mcpInputSchema                    // MCP input schema (JSON Schema)
 * );
 *
 * // 添加到请求
 * reqData.tools(mcpTool);
 *
 * // 当 AI 返回 tool_call 时，通过 McpClient 执行
 * String result = McpClient.callTool(mcpServerUrl, toolCall);
 * </pre>
 *
 * @see McpClient
 * @since 1.1.0
 */
public class McpTool {

    /**
     * 从 MCP 工具定义创建 AiTool。
     *
     * @param name        MCP 工具名称
     * @param description MCP 工具描述
     * @param inputSchema MCP 工具的 input schema（JSON Schema 格式）
     * @return AiTool 实例
     */
    public static AiTool fromMcpDefinition(String name, String description, JSONObject inputSchema) {
        AiTool.Builder builder = AiTool.builder()
            .name(name)
            .description(description);

        if (inputSchema != null && inputSchema.has("properties")) {
            JSONObject properties = inputSchema.getJSONObject("properties");
            for (String key : properties.keySet()) {
                JSONObject prop = properties.getJSONObject(key);
                String type = prop.optString("type", "string");
                String desc = prop.optString("description", "");
                boolean required = inputSchema.has("required") &&
                    inputSchema.getJSONArray("required").toList().contains(key);
                builder.param(key, type, desc, required);
            }
        }

        return builder.build();
    }

    /**
     * 将 AiTool 转换为 MCP 工具调用格式。
     *
     * @param tool       工具定义
     * @param toolCallId 工具调用 ID
     * @param arguments  工具参数（JSON 字符串）
     * @return MCP 格式的工具调用 JSON
     */
    public static JSONObject toMcpCall(AiTool tool, String toolCallId, String arguments) {
        JSONObject result = new JSONObject();
        result.put("jsonrpc", "2.0");
        result.put("id", toolCallId);
        result.put("method", "tools/call");

        JSONObject params = new JSONObject();
        params.put("name", tool.getName());

        if (arguments != null && !arguments.isEmpty()) {
            try {
                params.put("arguments", new JSONObject(arguments));
            } catch (Exception e) {
                // arguments 不是有效 JSON，作为字符串传递
                params.put("arguments", arguments);
            }
        }

        result.put("params", params);
        return result;
    }

    /**
     * 解析 MCP Server 返回的工具列表，转换为 AiTool 数组。
     *
     * @param mcpToolsResponse MCP tools/list 响应
     * @return AiTool 数组
     */
    public static AiTool[] parseMcpToolsList(JSONObject mcpToolsResponse) {
        if (mcpToolsResponse == null || !mcpToolsResponse.has("tools")) {
            return new AiTool[0];
        }

        var toolsArr = mcpToolsResponse.getJSONArray("tools");
        AiTool[] tools = new AiTool[toolsArr.length()];

        for (int i = 0; i < toolsArr.length(); i++) {
            JSONObject toolDef = toolsArr.getJSONObject(i);
            String name = toolDef.getString("name");
            String description = toolDef.optString("description", "");
            JSONObject inputSchema = toolDef.optJSONObject("inputSchema");

            if (inputSchema == null) {
                // MCP 新格式可能用 input_schema 或直接嵌套
                inputSchema = toolDef.optJSONObject("input_schema");
            }

            tools[i] = fromMcpDefinition(name, description, inputSchema);
        }

        return tools;
    }

    /**
     * 构建 MCP 工具调用结果。
     *
     * @param toolCallId 工具调用 ID
     * @param content    工具返回内容
     * @param isError    是否为错误
     * @return AiToolResult 实例
     */
    public static AiToolResult buildResult(String toolCallId, String content, boolean isError) {
        if (isError) {
            return new AiToolResult(toolCallId, "Error: " + content);
        }
        return new AiToolResult(toolCallId, content);
    }
}
