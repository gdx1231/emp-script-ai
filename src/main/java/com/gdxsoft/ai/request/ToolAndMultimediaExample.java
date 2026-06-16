package com.gdxsoft.ai.request;

/**
 * Tool Calling + 多模态（图片/音频/视频）+ Web Search + MCP 使用示例。
 * <p>
 * 本文件仅作为示例参考，不会被构建包含。
 *
 * @since 1.1.0
 */
public class ToolAndMultimediaExample {

    // ========================================================================
    // 示例 1：Tool Calling（工具调用）
    // ========================================================================
    void example1_toolCalling() {
        // 1. 定义工具
        AiTool weatherTool = AiTool.builder()
            .name("get_weather")
            .description("获取指定城市的天气信息")
            .param("city", "string", "城市名称，如北京、上海、东京", true)
            .param("date", "string", "日期，格式 YYYY-MM-DD，默认为今天", false)
            .build();

        AiTool searchTool = AiWebSearch.asTool();

        // 2. 创建请求并添加工具
        IRequestData reqData = RequestDataFactory.createRequestData("openai");
        reqData.model("gpt-4o")
               .stream(true)
               .systemMessage("你是一个天气助手，可以查询天气并搜索信息。")
               .userMessage("北京今天天气怎么样？")
               .tools(weatherTool, searchTool)        // 添加多个工具
               .toolChoice("auto");                   // AI 自动决定是否调用

        // 3. 发送请求
        // String json = reqData.buildJson();
        // IRequestAI req = RequestAIFactory.createRequestAI("openai");
        // req.initUrlAndKey(url, key);
        // String response = req.doPost(reqData);

        // 4. 如果 AI 返回 tool_calls，提取并执行工具
        // JSONObject json = req.extraceJson(response, true);
        // if (json.has("tool_calls")) {
        //     for (int i = 0; i < json.getJSONArray("tool_calls").length(); i++) {
        //         JSONObject tc = json.getJSONArray("tool_calls").getJSONObject(i);
        //         String id = tc.getString("id");
        //         String name = tc.getJSONObject("function").getString("name");
        //         String args = tc.getJSONObject("function").getString("arguments");
        //
        //         // 执行工具
        //         String result = executeTool(name, args);
        //
        //         // 将结果回传给 AI
        //         reqData.addToolResult(id, result);
        //     }
        // }

        // 5. 再次调用 AI，传入工具结果
        // reqData.userMessage("请根据工具结果回答用户问题");
        // String finalResponse = req.doPost(reqData);
    }

    // ========================================================================
    // 示例 2：多模态 — 图片理解（Vision）
    // ========================================================================
    void example2_imageVision() {
        IRequestData reqData = RequestDataFactory.createRequestData("openai");
        reqData.model("gpt-4o")
               .systemMessage("你是一个图片分析助手，请详细描述图片内容。")
               .addUserMultiPart(
                   AiContent.text("请描述这张图片中的内容："),
                   AiContent.imageUrl("https://example.com/photo.jpg")
               );

        // 或者使用 Base64 图片
        // reqData.addUserMultiPart(
        //     AiContent.text("这张图是什么？"),
        //     AiContent.imageBase64("image/png", base64String)
        // );
    }

    // ========================================================================
    // 示例 3：多模态 — 图片 + 文本混合
    // ========================================================================
    void example3_multiImage() {
        IRequestData reqData = RequestDataFactory.createRequestData("openai");
        reqData.model("gpt-4o")
               .userMessage("比较这两张图片的区别")
               .addUserMultiPart(
                   AiContent.text("图1:"),
                   AiContent.imageUrl("https://example.com/image1.jpg"),
                   AiContent.text("图2:"),
                   AiContent.imageUrl("https://example.com/image2.jpg"),
                   AiContent.text("请详细描述它们的区别。")
               );
    }

    // ========================================================================
    // 示例 4：多模态 — Gemini 图片 + 视频
    // ========================================================================
    void example4_geminiMultimodal() {
        IRequestData reqData = RequestDataFactory.createRequestData("gemini");
        reqData.model("gemini-2.5-pro")
               .addUserMultiPart(
                   AiContent.text("分析这段视频的内容："),
                   AiContent.videoUrl("gs://my-bucket/video.mp4")
               );
    }

    // ========================================================================
    // 示例 5：多模态 — Anthropic 图片
    // ========================================================================
    void example5_anthropicImage() {
        IRequestData reqData = RequestDataFactory.createRequestData("anthropic");
        reqData.model("claude-sonnet-4-20250514")
               .systemMessage("请描述图片内容。")
               .addUserMultiPart(
                   AiContent.text("这是什么？"),
                   AiContent.imageBase64("image/png", base64ImageData)
               );
    }

    // ========================================================================
    // 示例 6：Web Search 内置工具
    // ========================================================================
    void example6_webSearch() {
        // 方式 1：作为 Tool 让 AI 决定何时搜索
        AiTool searchTool = AiWebSearch.asTool();
        IRequestData reqData = RequestDataFactory.createRequestData("openai");
        reqData.model("gpt-4o")
               .systemMessage("你可以使用 web_search 工具搜索互联网获取最新信息。")
               .userMessage("2026年诺贝尔文学奖获得者是谁？")
               .tools(searchTool);

        // 方式 2：直接调用搜索
        // String results = AiWebSearch.search("Java 21 新特性", 5, "zh-CN");
        // 返回 JSON 格式：
        // {
        //   "results": [
        //     {"title": "...", "url": "...", "snippet": "..."},
        //     ...
        //   ],
        //   "count": 5
        // }
    }

    // ========================================================================
    // 示例 7：MCP (Model Context Protocol) 工具
    // ========================================================================
    void example7_mcp() throws Exception {
        // 1. 连接 MCP Server
        McpClient mcp = new McpClient("http://localhost:3000/mcp");
        mcp.initialize();

        // 2. 列出所有可用工具
        AiTool[] mcpTools = mcp.listTools();
        // 如：filesystem 的 read_file, write_file, list_directory 等

        // 3. 添加到 AI 请求
        IRequestData reqData = RequestDataFactory.createRequestData("openai");
        reqData.model("gpt-4o")
               .systemMessage("你可以使用文件系统工具来读写文件。")
               .userMessage("读取 /tmp/notes.txt 的内容")
               .tools(mcpTools);

        // 4. 发送请求...
        // 如果 AI 返回 tool_call，通过 MCP 客户端执行：
        // AiToolResult result = mcp.callTool(toolCall);
        // reqData.addToolResult(toolCall.getId(), result.getContent());
    }

    // ========================================================================
    // 示例 8：Tool Calling + 多模态 + Web Search 组合
    // ========================================================================
    void example8_combined() {
        AiTool weatherTool = AiTool.builder()
            .name("get_weather")
            .description("获取天气信息")
            .param("city", "string", "城市名", true)
            .build();

        AiTool searchTool = AiWebSearch.asTool();

        IRequestData reqData = RequestDataFactory.createRequestData("openai");
        reqData.model("gpt-4o")
               .systemMessage("你是全能助手，可以查看图片、搜索网络、查询天气。")
               .addUserMultiPart(
                   AiContent.text("这张图片中的建筑在哪里？帮我搜索它的历史并查询当地天气。"),
                   AiContent.imageUrl("https://example.com/building.jpg")
               )
               .tools(weatherTool, searchTool);
    }

    // ========================================================================
    // 示例 9：Anthropic 工具调用
    // ========================================================================
    void example9_anthropicTools() {
        AiTool calculatorTool = AiTool.builder()
            .name("calculator")
            .description("执行数学计算")
            .param("expression", "string", "数学表达式，如 2+3*4", true)
            .build();

        IRequestData reqData = RequestDataFactory.createRequestData("anthropic");
        reqData.model("claude-sonnet-4-20250514")
               .systemMessage("你可以使用计算器来确保计算准确。")
               .userMessage("1234 * 5678 等于多少？")
               .tools(calculatorTool)
               .toolChoice("auto");
    }

    // ========================================================================
    // 示例 10：Gemini 工具调用 + 图片
    // ========================================================================
    void example10_geminiToolsAndImage() {
        AiTool translateTool = AiTool.builder()
            .name("translate")
            .description("翻译文本")
            .param("text", "string", "要翻译的文本", true)
            .param("target_lang", "string", "目标语言代码，如 en, ja, ko", true)
            .build();

        IRequestData reqData = RequestDataFactory.createRequestData("gemini");
        reqData.model("gemini-2.5-pro")
               .addUserMultiPart(
                   AiContent.text("识别图片中的文字并翻译成英文"),
                   AiContent.imageUrl("https://example.com/text.jpg")
               )
               .tools(translateTool);
    }

    // 占位符
    private static String base64String = "";
    private static String base64ImageData = "";
    private String executeTool(String name, String args) { return ""; }
}
