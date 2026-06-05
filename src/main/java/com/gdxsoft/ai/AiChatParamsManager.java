package com.gdxsoft.ai;

import java.util.List;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.modes.Mode;
import com.gdxsoft.ai.modes.ParamCheck;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.datasource.DataConnection;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * AI 会话参数管理器，负责 AI_CHAT_PARAMS 表的读写操作。
 * <p>
 * 主要功能：
 * <ul>
 * <li>根据 request_id 加载 AI 会话信息（AI_ID, AIM_ID）</li>
 * <li>加载对话上下文用于参数提取</li>
 * <li>保存提取的参数到 AI_CHAT_PARAMS 表</li>
 * </ul>
 */
public class AiChatParamsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiChatParamsManager.class);

    /**
     * AI 会话信息
     */
    public static class AiChatInfo {
        private long aiId;
        private long aimId;

        public AiChatInfo(long aiId, long aimId) {
            this.aiId = aiId;
            this.aimId = aimId;
        }

        public long getAiId() {
            return aiId;
        }

        public long getAimId() {
            return aimId;
        }

        public boolean isValid() {
            return aiId > 0 && aimId > 0;
        }
    }

    /**
     * 参数提取结果
     */
    public static class ExtractResult {
        private boolean success;
        private String errorMessage;
        private JSONObject params;
        private String aiRawOutput;
        private long aiId;
        private long aimId;

        public static ExtractResult failure(String msg) {
            ExtractResult r = new ExtractResult();
            r.success = false;
            r.errorMessage = msg;
            return r;
        }

        public static ExtractResult success(JSONObject params, String aiRaw, long aiId, long aimId) {
            ExtractResult r = new ExtractResult();
            r.success = true;
            r.params = params;
            r.aiRawOutput = aiRaw;
            r.aiId = aiId;
            r.aimId = aimId;
            return r;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public JSONObject getParams() {
            return params;
        }

        public String getAiRawOutput() {
            return aiRawOutput;
        }

        public long getAiId() {
            return aiId;
        }

        public long getAimId() {
            return aimId;
        }
    }

    /**
     * 根据 request_id 加载 AI 会话信息
     *
     * @param requestId 会话 UID
     * @param rv        请求参数容器
     * @return AiChatInfo 对象，包含 AI_ID 和 LAST_AIM_ID
     */
    public static AiChatInfo loadAiInfo(String requestId, RequestValue rv) {
        if (requestId == null || requestId.trim().isEmpty()) {
            LOGGER.debug("loadAiInfo: requestId 为空");
            return new AiChatInfo(0, 0);
        }
        rv.addOrUpdateValue("request_id", requestId);
        String sql = "select c.AI_ID, isnull(max(m.AIM_ID), 0) as LAST_AIM_ID "
                + "from AI_CHAT c left join AI_CHAT_MSG m on c.AI_ID = m.AI_ID "
                + "where c.AI_UID = @request_id group by c.AI_ID";
        try {

            DTTable tb = DTTable.getJdbcTable(sql, rv);
            if (tb.getCount() == 0) {
                LOGGER.debug("loadAiInfo: 未找到会话, requestId={}", requestId);
                return new AiChatInfo(0, 0);
            }
            long aiId = tb.getCell(0, "AI_ID").toLong();
            long aimId = tb.getCell(0, "LAST_AIM_ID").toLong();
            LOGGER.debug("loadAiInfo: 成功, aiId={}, aimId={}", aiId, aimId);
            return new AiChatInfo(aiId, aimId);
        } catch (Exception ex) {
            LOGGER.error("loadAiInfo 失败: {}", ex.getMessage());
            return new AiChatInfo(0, 0);
        }
    }

    /**
     * 加载对话上下文（用于 AI 参数提取）
     *
     * @param requestId 会话 UID
     * @param rv        请求参数容器
     * @return 格式化的对话历史字符串（role: message 格式）
     */
    public static String loadConversationContext(String requestId, RequestValue rv) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return "";
        }
        rv.addOrUpdateValue("request_id", requestId);
        String sql = "select top 30 AIM_ROLE, AIM_MSG from AI_CHAT_MSG m "
                + "inner join AI_CHAT c on m.AI_ID = c.AI_ID "
                + "where c.AI_UID = @request_id "
                + "and isnull(m.AIM_SKIP_APPEND, 0) = 0 "
                + "order by m.AIM_ID desc";
        DTTable tb = DTTable.getJdbcTable(sql, rv);
        try {

            LOGGER.debug("loadConversationContext: 加载 {} 条消息", tb.getCount());
            StringBuilder sb = new StringBuilder();
            for (int i = tb.getCount() - 1; i >= 0; i--) {
                String role = tb.getCell(i, "AIM_ROLE").toString();
                Object value = tb.getCell(i, "AIM_MSG").getValue();
                String msg = value == null ? "" : value.toString();
                if (msg.trim().isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(role).append(": ").append(msg);
            }
            LOGGER.debug("loadConversationContext: 生成上下文长度={}", sb.length());
            return sb.toString();
        } catch (Exception ex) {
            LOGGER.error("loadConversationContext 失败: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * 执行完整的参数提取流程：
     * 1. 构建 AI 提取 prompt
     * 2. 调用 AI 提取参数
     * 3. 解析 AI 返回结果
     * 4. 保存到 AI_CHAT_PARAMS 表
     *
     * @param requestId  会话 UID
     * @param mode       AI 模式对象（包含参数定义）
     * @param rv         请求参数容器
     * @param aiModel    AI 模型名称（如 qwen-plus）
     * @param aiProvider AI 提供商（如 QWEN）
     * @return 提取结果
     */
    public static ExtractResult extractAndSaveParams(String requestId, Mode mode,
            RequestValue rv, String aiModel, String aiProvider) {

        LOGGER.debug("extractAndSaveParams 开始: requestId={}, mode={}, aiModel={}", requestId, mode.getName(), aiModel);

        // 1. 加载 AI 会话信息
        AiChatInfo aiInfo = loadAiInfo(requestId, rv);
        if (!aiInfo.isValid()) {
            LOGGER.debug("extractAndSaveParams: AI 会话信息无效");
            return ExtractResult.failure("未找到对应的 AI 会话");
        }

        // 2. 加载对话上下文
        String context = loadConversationContext(requestId, rv);
        if (context.isEmpty()) {
            LOGGER.debug("extractAndSaveParams: 对话上下文为空");
            return ExtractResult.failure("会话中没有对话内容");
        }

        // 3. 获取参数定义
        List<ParamCheck> paramChecks = mode.getParamChecks();
        if (paramChecks == null || paramChecks.isEmpty()) {
            LOGGER.debug("extractAndSaveParams: 参数定义为空");
            return ExtractResult.failure("未找到参数定义");
        }
        LOGGER.debug("extractAndSaveParams: 参数定义数量={}", paramChecks.size());

        // 4. 构建提取 prompt
        String extractPrompt = ParamCheck.buildExtractPrompt(paramChecks, mode, rv);
        LOGGER.debug("extractAndSaveParams: prompt 长度={}", extractPrompt.length());

        // 5. 调用 AI 提取参数（同时记录到 AI_CHAT_MSG）
        String aiText = callAiForExtraction(extractPrompt, context, rv, aiModel, aiProvider, aiInfo);
        if (aiText == null || aiText.isEmpty()) {
            LOGGER.debug("extractAndSaveParams: AI 返回内容为空");
            return ExtractResult.failure("AI 未返回参数结果");
        }
        LOGGER.debug("extractAndSaveParams: AI 返回长度={}", aiText.length());

        // 6. 解析 AI 返回的 JSON
        JSONObject params = parseAiJsonResult(aiText);
        if (params == null) {
            LOGGER.debug("extractAndSaveParams: JSON 解析失败");
            return ExtractResult.failure("参数结果解析失败");
        }
        LOGGER.debug("extractAndSaveParams: JSON 解析成功, keys={}", params.keySet());

        // 7. 保存到数据库
        boolean saved = saveParamsToDatabase(aiInfo.getAiId(), aiInfo.getAimId(), paramChecks, params, rv);
        if (!saved) {
            LOGGER.debug("extractAndSaveParams: 数据库保存失败");
            return ExtractResult.failure("保存参数失败");
        }

        // 8. 返回成功结果
        JSONObject savedParams = buildSavedParamsJson(paramChecks, params);
        LOGGER.debug("extractAndSaveParams: 成功, 保存参数={}", savedParams.keySet());
        return ExtractResult.success(savedParams, aiText, aiInfo.getAiId(), aiInfo.getAimId());
    }

    /**
     * 调用 AI 进行参数提取，并记录到 AI_CHAT_MSG 表
     */
    private static String callAiForExtraction(String extractPrompt, String context,
            RequestValue rv, String aiModel, String aiProvider, AiChatInfo aiInfo) {
        try {
            LOGGER.debug("callAiForExtraction: 开始调用 AI, model={}, provider={}", aiModel, aiProvider);

            // 记录 prompt 到 AI_CHAT_MSG
            String fullPrompt = extractPrompt + "\n\n对话内容：\n" + context;
            if (aiInfo.isValid()) {
                recordToAiChatMsg(aiInfo.getAiId(), fullPrompt, "user", rv);
                LOGGER.debug("callAiForExtraction: 已记录 prompt 到 AI_CHAT_MSG, aiId={}", aiInfo.getAiId());
            }

            // 配置 AI 请求参数
            if (aiModel != null && !aiModel.isEmpty()) {
                rv.addOrUpdateValue("ai_model", aiModel);
            }
            if (aiProvider != null && !aiProvider.isEmpty()) {
                rv.addOrUpdateValue("ai_provider", aiProvider);
            }
            rv.addOrUpdateValue("mode", "getcity");
            rv.addOrUpdateValue("prompt", fullPrompt);
            String tempRequestId = java.util.UUID.randomUUID().toString();
            rv.addOrUpdateValue("request_id", tempRequestId);
            LOGGER.debug("callAiForExtraction: 配置完成, tempRequestId={}", tempRequestId);

            // 调用 AI
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter swWriter = new java.io.PrintWriter(sw);
            com.gdxsoft.ai.AiStreamOrPost handle = new com.gdxsoft.ai.AiStreamOrPost();
            if (!handle.init(rv, "", swWriter)) {
                LOGGER.error("callAiForExtraction: AI 初始化失败");
                return null;
            }
            handle.processRequest();

            // 解析 AI 返回结果
            String rawOutput = sw.toString();
            LOGGER.debug("callAiForExtraction: AI 原始输出长度={}", rawOutput.length());

            String aiContent;
            try {
                JSONObject output = new JSONObject(rawOutput);
                aiContent = normalizeText(output.optString("content"));
                LOGGER.debug("callAiForExtraction: 提取 content 长度={}", aiContent.length());
            } catch (Exception ex) {
                aiContent = normalizeText(rawOutput);
                LOGGER.debug("callAiForExtraction: 直接使用原始输出");
            }

            // 记录 AI 响应到 AI_CHAT_MSG
            if (aiInfo.isValid() && !aiContent.isEmpty()) {
                long aimId = recordToAiChatMsg(aiInfo.getAiId(), aiContent, "assistant", rv);
                LOGGER.debug("callAiForExtraction: 已记录 AI 响应到 AI_CHAT_MSG, aimId={}", aimId);
            }

            return aiContent;
        } catch (Exception ex) {
            LOGGER.error("callAiForExtraction 失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 将消息记录到 AI_CHAT_MSG 表
     *
     * @param aiId    AI 会话 ID
     * @param content 消息内容
     * @param role    消息角色（user/assistant/system）
     * @param rv      请求参数容器
     * @return AIM_ID 新插入的消息 ID
     */
    private static long recordToAiChatMsg(long aiId, String content, String role, RequestValue rv) {
        long aimId = 0;
        DataConnection cnn = null;
        try {
            cnn = new DataConnection();
            cnn.setRequestValue(rv);
            cnn.setConfigName("");

            // 插入消息记录
            rv.addOrUpdateValue("AI_ID", aiId, "long", 100);
            rv.addOrUpdateValue("AIM_ROLE", role);
            rv.addOrUpdateValue("AIM_MSG", content);
            rv.addOrUpdateValue("AIM_SKIP_APPEND", 1); // 标记为不追加到上下文

            String insertSql = "INSERT INTO AI_CHAT_MSG (AI_ID, AIM_ROLE, AIM_MSG, AIM_SKIP_APPEND) "
                    + "VALUES (@AI_ID, @AIM_ROLE, @AIM_MSG, @AIM_SKIP_APPEND)";
            cnn.executeUpdate(insertSql);

            // 获取插入的 AIM_ID
            String sql = "SELECT @@IDENTITY AS AIM_ID";
            DTTable tb = DTTable.getJdbcTable(sql, rv);
            if (tb.getCount() > 0) {
                aimId = tb.getCell(0, "AIM_ID").toLong();
            }

            LOGGER.debug("recordToAiChatMsg: 成功, aiId={}, role={}, aimId={}", aiId, role, aimId);
        } catch (Exception ex) {
            LOGGER.error("recordToAiChatMsg 失败: {}", ex.getMessage());
        } finally {
            if (cnn != null) {
                cnn.close();
            }
        }
        return aimId;
    }

    /**
     * 解析 AI 返回的 JSON 结果
     */
    private static JSONObject parseAiJsonResult(String aiText) {
        int jsonStart = aiText.indexOf('{');
        int jsonEnd = aiText.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            LOGGER.warn("parseAiJsonResult: JSON 格式无效, 文本前100字符={}",
                    aiText.substring(0, Math.min(100, aiText.length())));
            return null;
        }
        String jsonStr = aiText.substring(jsonStart, jsonEnd + 1);
        LOGGER.debug("parseAiJsonResult: JSON 字符串长度={}", jsonStr.length());
        try {
            JSONObject result = new JSONObject(jsonStr);
            LOGGER.debug("parseAiJsonResult: 解析成功, keys={}", result.keySet());
            return result;
        } catch (Exception ex) {
            LOGGER.error("parseAiJsonResult: JSON 解析失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 保存参数到 AI_CHAT_PARAMS 表
     */
    private static boolean saveParamsToDatabase(long aiId, long aimId,
            List<ParamCheck> paramChecks, JSONObject params, RequestValue rv) {
        LOGGER.debug("saveParamsToDatabase: 开始保存, aiId={}, aimId={}, 参数数量={}", aiId, aimId, paramChecks.size());
        DataConnection cnn = new DataConnection();
        cnn.setRequestValue(rv);
        cnn.setConfigName("");

        try {
            cnn.transBegin();

            // 删除旧参数
            cnn.executeUpdate("DELETE FROM AI_CHAT_PARAMS WHERE AI_ID=" + aiId);
            LOGGER.debug("saveParamsToDatabase: 已删除旧参数, aiId={}", aiId);

            // 插入新参数
            int inserted = 0;
            for (ParamCheck pc : paramChecks) {
                String value = extractParamValue(params, pc);
                if (value != null) {
                    rv.addOrUpdateValue("AI_ID", aiId, "long", 100);
                    rv.addOrUpdateValue("AIM_ID", aimId, "long", 100);
                    rv.addOrUpdateValue("AIP_NAME", pc.getName());
                    rv.addOrUpdateValue("AIP_VAL", value);
                    rv.addOrUpdateValue("AIP_TYPE", pc.getType());

                    String insertSql = "INSERT INTO AI_CHAT_PARAMS (AI_ID, AIM_ID, AIP_NAME, AIP_VAL, AIP_TYPE) "
                            + "VALUES (@AI_ID, @AIM_ID, @AIP_NAME, @AIP_VAL, @AIP_TYPE)";
                    cnn.executeUpdate(insertSql);
                    inserted++;
                    LOGGER.debug("saveParamsToDatabase: 插入参数 {}={}", pc.getName(), value);
                } else {
                    LOGGER.debug("saveParamsToDatabase: 参数 {} 值为空，跳过", pc.getName());
                }
            }

            cnn.transCommit();
            LOGGER.debug("saveParamsToDatabase: 提交成功, 插入 {} 条", inserted);
            return true;
        } catch (Exception ex) {
            try {
                cnn.transRollback();
            } catch (Exception ignore) {
            }
            LOGGER.error("saveParamsToDatabase 失败: {}", ex.getMessage());
            return false;
        } finally {
            cnn.close();
        }
    }

    /**
     * 从 AI 返回的 JSON 中提取参数值
     */
    private static String extractParamValue(JSONObject params, ParamCheck pc) {
        try {
            if (!params.has(pc.getName()) || params.isNull(pc.getName())) {
                return null;
            }
            String value = normalizeText(params.optString(pc.getName()));
            if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
                // 使用默认值
                return (pc.getDefaultValue() != null && !pc.getDefaultValue().isEmpty())
                        ? pc.getDefaultValue()
                        : null;
            }
            return value;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 构建已保存参数的 JSON 响应
     */
    private static JSONObject buildSavedParamsJson(List<ParamCheck> paramChecks, JSONObject params) {
        JSONObject saved = new JSONObject();
        for (ParamCheck pc : paramChecks) {
            try {
                if (params.has(pc.getName()) && !params.isNull(pc.getName())) {
                    saved.put(pc.getName(), params.get(pc.getName()));
                } else if (pc.getDefaultValue() != null && !pc.getDefaultValue().isEmpty()) {
                    saved.put(pc.getName(), pc.getDefaultValue());
                }
            } catch (Exception ex) {
                // 忽略
            }
        }
        return saved;
    }

    /**
     * 清理文本（去除 \r 和前后空格）
     */
    private static String normalizeText(String text) {
        return text == null ? "" : text.replace("\r", "").trim();
    }
}
