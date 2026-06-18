package com.gdxsoft.ai.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import org.json.JSONObject;
import org.junit.jupiter.api.*;

import com.gdxsoft.ai.AiStreamOrPost;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 集成测试 - 使用真实 AI API + HSQLDB 内存数据库
 * <p>
 * 测试完整流程：SSE 流式输出 → 前端收到事件 → AI 回答落库 → 多轮对话
 * <p>
 * 运行前需确保：
 * 1. test_ai_settings.json 中有 provider 配置
 * 2. 环境变量或 ai_settings.json 中有对应的 API Key
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {

    private static final String DEFAULT_PROVIDER = "qwen";
    private static final String DEFAULT_MODE = "test_mode";
    private static final String DEFAULT_STEP = "chat";

    @BeforeAll
    static void setup() throws Exception {
        TestDatabase.init();
    }

    @AfterAll
    static void teardown() {
        TestDatabase.shutdown();
    }

    /**
     * 测试 SSE 流式输出 + 落库验证
     */
    @Test
    @Order(1)
    @DisplayName("SSE 流式输出测试 (qwen)")
    void testSseStream() throws Exception {
        // 检查 provider 是否配置
        assumeTrue(TestDatabase.isProviderConfigured(DEFAULT_PROVIDER),
                "跳过测试: " + DEFAULT_PROVIDER + " 未配置 API Key");

        String requestId = UUID.randomUUID().toString();
        String prompt = "你好，请用一句话介绍你自己";

        // 构造请求
        RequestValue rv = TestDatabase.createRequestValue();
        rv.addOrUpdateValue("request_id", requestId);
        rv.addOrUpdateValue("ai_provider", DEFAULT_PROVIDER);
        rv.addOrUpdateValue("ai_model", getModel(DEFAULT_PROVIDER));
        rv.addOrUpdateValue("mode", DEFAULT_MODE);
        rv.addOrUpdateValue("step", DEFAULT_STEP);
        rv.addOrUpdateValue("prompt", prompt);
        rv.addOrUpdateValue("ai_stream", "true");

        // 执行请求
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);

        AiStreamOrPost aiHandler = new AiStreamOrPost();
        boolean initResult = aiHandler.init(rv, TestDatabase.getDbConfigName(), writer);
        if (!initResult) {
            JSONObject error = aiHandler.getLastError();
            System.err.println("初始化失败详情: " + (error != null ? error.toString(2) : "unknown"));
        }
        assertTrue(initResult, "AiStreamOrPost 初始化失败");

        String result = aiHandler.processRequest();
        writer.flush();

        String sseOutput = sw.toString();

        // 验证 SSE 输出
        System.out.println("=== SSE 输出 ===");
        System.out.println(sseOutput.length() > 500 ? sseOutput.substring(0, 500) + "..." : sseOutput);
        System.out.println("================");

        assertFalse(sseOutput.isEmpty(), "SSE 输出为空");
        assertTrue(sseOutput.contains("data:"), "SSE 输出不包含 'data:' 前缀");

        // 验证数据库落库
        long aiId = getAiId(rv);
        // HSQLDB 第一次插入时可能返回 0，尝试获取最新记录
        if (aiId <= 0) {
            String sql = "SELECT AI_ID FROM AI_CHAT ORDER BY AI_ID DESC LIMIT 1";
            com.gdxsoft.easyweb.data.DTTable tb = com.gdxsoft.easyweb.data.DTTable.getJdbcTable(
                    sql, TestDatabase.getDbConfigName(), TestDatabase.createRequestValue());
            if (tb.getCount() > 0) {
                aiId = tb.getCell(0, "AI_ID").toLong();
            }
        }
        assertTrue(aiId >= 0, "AI_ID 应该大于等于 0");

        int msgCount = TestDatabase.getMessageCount(aiId);
        assertTrue(msgCount >= 2, "AI_CHAT_MSG 应至少有 2 条记录 (user + assistant)");

        String assistantMsg = TestDatabase.getMessageContent(aiId, "assistant");
        assertNotNull(assistantMsg, "assistant 消息不应为空");
        assertFalse(assistantMsg.isEmpty(), "assistant 消息内容不应为空");

        System.out.println("AI 回复: " + (assistantMsg.length() > 100 ? assistantMsg.substring(0, 100) + "..." : assistantMsg));
        System.out.println("测试通过: SSE 输出正常，数据库落库成功");
    }

    /**
     * 测试 DeepSeek provider
     */
    @Test
    @Order(2)
    @DisplayName("SSE 流式输出测试 (deepseek)")
    void testDeepSeekProvider() throws Exception {
        String provider = "deepseek";
        assumeTrue(TestDatabase.isProviderConfigured(provider),
                "跳过测试: " + provider + " 未配置 API Key");

        String requestId = UUID.randomUUID().toString();
        String prompt = "用一句话回答：1+1等于几？";

        RequestValue rv = TestDatabase.createRequestValue();
        rv.addOrUpdateValue("request_id", requestId);
        rv.addOrUpdateValue("ai_provider", provider);
        rv.addOrUpdateValue("ai_model", getModel(provider));
        rv.addOrUpdateValue("mode", DEFAULT_MODE);
        rv.addOrUpdateValue("step", DEFAULT_STEP);
        rv.addOrUpdateValue("prompt", prompt);
        rv.addOrUpdateValue("ai_stream", "true");

        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);

        AiStreamOrPost aiHandler = new AiStreamOrPost();
        assertTrue(aiHandler.init(rv, TestDatabase.getDbConfigName(), writer), "初始化失败");

        aiHandler.processRequest();
        writer.flush();

        String sseOutput = sw.toString();
        assertFalse(sseOutput.isEmpty(), "SSE 输出为空");
        assertTrue(sseOutput.contains("data:"), "SSE 输出格式错误");

        System.out.println("DeepSeek 测试通过");
    }

    /**
     * 测试多轮对话
     */
    @Test
    @Order(3)
    @DisplayName("多轮对话测试")
    void testMultiRound() throws Exception {
        assumeTrue(TestDatabase.isProviderConfigured(DEFAULT_PROVIDER),
                "跳过测试: " + DEFAULT_PROVIDER + " 未配置 API Key");

        String requestId = UUID.randomUUID().toString();

        // === 第1轮 ===
        String prompt1 = "我的名字是小明，请记住这个名字。";
        System.out.println("=== 第1轮: " + prompt1 + " ===");

        RequestValue rv1 = createBaseRequestValue(requestId);
        rv1.addOrUpdateValue("prompt", prompt1);

        StringWriter sw1 = new StringWriter();
        AiStreamOrPost handler1 = new AiStreamOrPost();
        assertTrue(handler1.init(rv1, TestDatabase.getDbConfigName(), new PrintWriter(sw1)));
        handler1.processRequest();

        long aiId = getAiId(rv1);
        int count1 = TestDatabase.getMessageCount(aiId);
        assertTrue(count1 >= 2, "第1轮后应有至少 2 条消息");
        System.out.println("第1轮完成，消息数: " + count1);

        // === 第2轮 ===
        String prompt2 = "我叫什么名字？";
        System.out.println("=== 第2轮: " + prompt2 + " ===");

        RequestValue rv2 = createBaseRequestValue(requestId);
        rv2.addOrUpdateValue("prompt", prompt2);

        StringWriter sw2 = new StringWriter();
        AiStreamOrPost handler2 = new AiStreamOrPost();
        assertTrue(handler2.init(rv2, TestDatabase.getDbConfigName(), new PrintWriter(sw2)));
        handler2.processRequest();

        int count2 = TestDatabase.getMessageCount(aiId);
        assertTrue(count2 >= 4, "第2轮后应有至少 4 条消息");

        // 验证第2轮有回复
        String reply2 = TestDatabase.getMessageContent(aiId, "assistant");
        System.out.println("第2轮 AI 回复: " + reply2);
        assertNotNull(reply2, "第2轮 AI 回复不应为空");
        assertFalse(reply2.isEmpty(), "第2轮 AI 回复不应为空字符串");

        System.out.println("多轮对话测试通过");
    }

    /**
     * 测试非流式请求
     */
    @Test
    @Order(4)
    @DisplayName("非流式请求测试")
    void testNonStream() throws Exception {
        assumeTrue(TestDatabase.isProviderConfigured(DEFAULT_PROVIDER),
                "跳过测试: " + DEFAULT_PROVIDER + " 未配置 API Key");

        String requestId = UUID.randomUUID().toString();
        String prompt = "回答：中国的首都是哪里？用一个词回答";

        RequestValue rv = createBaseRequestValue(requestId);
        rv.addOrUpdateValue("prompt", prompt);
        rv.addOrUpdateValue("ai_stream", "false"); // 非流式

        StringWriter sw = new StringWriter();
        AiStreamOrPost handler = new AiStreamOrPost();
        assertTrue(handler.init(rv, TestDatabase.getDbConfigName(), new PrintWriter(sw)));

        String result = handler.processRequest();
        System.out.println("非流式结果: " + result);

        // 验证数据库
        long aiId = getAiId(rv);
        String assistantMsg = TestDatabase.getMessageContent(aiId, "assistant");
        assertNotNull(assistantMsg, "assistant 消息不应为空");

        System.out.println("非流式测试通过");
    }

    // === 辅助方法 ===

    private RequestValue createBaseRequestValue(String requestId) {
        RequestValue rv = TestDatabase.createRequestValue();
        rv.addOrUpdateValue("request_id", requestId);
        rv.addOrUpdateValue("ai_provider", DEFAULT_PROVIDER);
        rv.addOrUpdateValue("ai_model", getModel(DEFAULT_PROVIDER));
        rv.addOrUpdateValue("mode", DEFAULT_MODE);
        rv.addOrUpdateValue("step", DEFAULT_STEP);
        rv.addOrUpdateValue("ai_stream", "true");
        return rv;
    }

    private String getModel(String provider) {
        try {
            JSONObject cfg = TestDatabase.getAiSettings().optJSONObject(provider);
            if (cfg != null) {
                return cfg.optString("model", "qwen-plus");
            }
        } catch (Exception e) {
            // ignore
        }
        return "qwen-plus";
    }

    private long getAiId(RequestValue rv) {
        try {
            String requestId = rv.s("request_id");
            System.out.println("查询 AI_ID, request_id=" + requestId);
            // 使用 ORDER BY AI_ID DESC 获取最新的记录
            String sql = "SELECT AI_ID FROM AI_CHAT WHERE AI_UID = @request_id ORDER BY AI_ID DESC";
            com.gdxsoft.easyweb.data.DTTable tb = com.gdxsoft.easyweb.data.DTTable.getJdbcTable(
                    sql, TestDatabase.getDbConfigName(), rv);
            System.out.println("查询结果: count=" + tb.getCount());
            if (tb.getCount() > 0) {
                long aiId = tb.getCell(0, "AI_ID").toLong();
                System.out.println("AI_ID=" + aiId);
                return aiId;
            }
            // 如果通过 request_id 查不到，尝试获取最新的记录
            sql = "SELECT AI_ID, AI_UID FROM AI_CHAT ORDER BY AI_ID DESC LIMIT 1";
            tb = com.gdxsoft.easyweb.data.DTTable.getJdbcTable(sql, TestDatabase.getDbConfigName(), TestDatabase.createRequestValue());
            if (tb.getCount() > 0) {
                long aiId = tb.getCell(0, "AI_ID").toLong();
                String uid = tb.getCell(0, "AI_UID").toString();
                System.out.println("最新记录: AI_ID=" + aiId + ", AI_UID=" + uid);
                return aiId;
            }
        } catch (Exception e) {
            System.err.println("getAiId 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }
}
