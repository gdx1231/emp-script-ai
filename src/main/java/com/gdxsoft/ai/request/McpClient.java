package com.gdxsoft.ai.request;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP (Model Context Protocol) 轻量级 HTTP 客户端。
 * <p>
 * 用于与 MCP Server 通信，支持：
 * <ul>
 *   <li>初始化连接（initialize）</li>
 *   <li>列出可用工具（tools/list）</li>
 *   <li>调用工具（tools/call）</li>
 * </ul>
 * <p>
 * MCP 使用 JSON-RPC 2.0 协议，通过 HTTP POST 发送请求，
 * SSE 端点用于服务器推送通知。
 * <p>
 * 使用示例：
 * <pre>
 * McpClient client = new McpClient("http://localhost:3000/mcp");
 *
 * // 初始化
 * client.initialize();
 *
 * // 列出所有工具
 * AiTool[] tools = client.listTools();
 *
 * // 添加到 AI 请求
 * reqData.tools(tools);
 *
 * // 当 AI 返回 tool_call 时执行
 * AiToolResult result = client.callTool("filesystem", argumentsJson);
 * </pre>
 *
 * @since 1.1.0
 */
public class McpClient {

    private final String serverUrl;
    private final String sessionId;
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private boolean initialized = false;

    /**
     * 创建 MCP 客户端。
     *
     * @param serverUrl MCP Server 的 HTTP 端点 URL
     */
    public McpClient(String serverUrl) {
        this(serverUrl, null);
    }

    /**
     * 创建 MCP 客户端。
     *
     * @param serverUrl MCP Server 的 HTTP 端点 URL
     * @param sessionId 会话 ID（可选，用于多会话场景）
     */
    public McpClient(String serverUrl, String sessionId) {
        this.serverUrl = serverUrl;
        this.sessionId = sessionId;
    }

    /**
     * 初始化 MCP 连接。
     * <p>
     * 发送 initialize 请求，获取服务器能力和协议版本。
     *
     * @return 服务器信息 JSON
     * @throws Exception 连接失败时抛出
     */
    public JSONObject initialize() throws Exception {
        if (initialized) {
            return serverInfo;
        }

        JSONObject request = createJsonRpcRequest("initialize");
        JSONObject params = request.getJSONObject("params");
        JSONObject clientInfo = new JSONObject();
        clientInfo.put("name", "emp-script-ai");
        clientInfo.put("version", "1.1.0");
        params.put("clientInfo", clientInfo);

        JSONObject protocolVersion = new JSONObject();
        protocolVersion.put("name", "2024-11-05");
        params.put("protocolVersion", "2024-11-05");

        serverInfo = sendRequest(request);
        initialized = true;

        // 发送 initialized 通知
        JSONObject notify = createJsonRpcRequest("notifications/initialized");
        notify.remove("id"); // 通知不需要 id
        try {
            sendRequest(notify);
        } catch (Exception e) {
            // 通知失败不影响初始化
        }

        return serverInfo;
    }

    /**
     * 列出 MCP Server 提供的所有工具。
     *
     * @return AiTool 数组
     * @throws Exception 请求失败时抛出
     */
    public AiTool[] listTools() throws Exception {
        ensureInitialized();

        JSONObject request = createJsonRpcRequest("tools/list");
        JSONObject response = sendRequest(request);

        return McpTool.parseMcpToolsList(response);
    }

    /**
     * 调用指定的 MCP 工具。
     *
     * @param toolName  工具名称
     * @param arguments 工具参数（JSON 对象）
     * @return 工具执行结果
     * @throws Exception 调用失败时抛出
     */
    public AiToolResult callTool(String toolName, JSONObject arguments) throws Exception {
        return callTool(toolName, arguments != null ? arguments.toString() : null);
    }

    /**
     * 调用指定的 MCP 工具。
     *
     * @param toolName  工具名称
     * @param arguments 工具参数（JSON 字符串）
     * @return 工具执行结果
     * @throws Exception 调用失败时抛出
     */
    public AiToolResult callTool(String toolName, String arguments) throws Exception {
        ensureInitialized();

        JSONObject request = createJsonRpcRequest("tools/call");
        JSONObject callParams = new JSONObject();
        callParams.put("name", toolName);
        if (arguments != null && !arguments.isEmpty()) {
            try {
                callParams.put("arguments", new JSONObject(arguments));
            } catch (Exception e) {
                callParams.put("arguments", arguments);
            }
        }
        request.put("params", callParams);

        JSONObject response = sendRequest(request);

        // 解析响应
        if (response.has("content")) {
            var contentArr = response.getJSONArray("content");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < contentArr.length(); i++) {
                JSONObject item = contentArr.getJSONObject(i);
                if ("text".equals(item.optString("type"))) {
                    sb.append(item.optString("text"));
                }
            }
            boolean isError = response.optBoolean("isError", false);
            return McpTool.buildResult(toolName, sb.toString(), isError);
        }

        return McpTool.buildResult(toolName, response.toString(), false);
    }

    /**
     * 便捷方法：从 AiToolCall 直接调用 MCP 工具。
     *
     * @param toolCall AI 返回的工具调用
     * @return 工具执行结果
     * @throws Exception 调用失败时抛出
     */
    public AiToolResult callTool(AiToolCall toolCall) throws Exception {
        return callTool(toolCall.getName(), toolCall.getArguments());
    }

    /**
     * 检查是否已初始化。
     */
    public boolean isInitialized() {
        return initialized;
    }

    public String getServerUrl() { return serverUrl; }
    public String getSessionId() { return sessionId; }

    // --- 内部实现 ---

    private JSONObject serverInfo;

    private void ensureInitialized() throws Exception {
        if (!initialized) {
            initialize();
        }
    }

    private JSONObject createJsonRpcRequest(String method) {
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        request.put("id", idCounter.getAndIncrement());

        JSONObject params = new JSONObject();
        request.put("params", params);

        return request;
    }

    private JSONObject sendRequest(JSONObject request) throws Exception {
        String body = request.toString();
        URI uri = new URI(serverUrl);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        if (sessionId != null) {
            conn.setRequestProperty("Mcp-Session-Id", sessionId);
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int statusCode = conn.getResponseCode();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                    statusCode >= 200 && statusCode < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            if (statusCode >= 200 && statusCode < 300) {
                return new JSONObject(sb.toString());
            } else {
                throw new Exception("MCP request failed: " + statusCode + " - " + sb);
            }
        }
    }
}
