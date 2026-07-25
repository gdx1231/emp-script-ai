/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: ChatManagerDb.java
 * 描述: AI 聊天管理器数据库操作类，负责 AI_CHAT / AI_CHAT_MSG / AI_CHAT_PARAMS 等表的增删改查
 */
package com.gdxsoft.ai;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.modes.ParamCheck;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.datasource.DataConnection;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * AI 聊天数据库操作类。
 * <p>
 * 封装 AI_CHAT、AI_CHAT_MSG、AI_CHAT_PARAMS、AI_PROVIDER_MODEL 等表的全部操作， 与
 * {@link ChatManagerBase} 的业务逻辑解耦。所有 SQL 均使用参数化查询（{@code @param} 占位符）， 通过
 * {@link RequestValue} 传参，防止 SQL 注入。
 * <p>
 * 表结构概览：
 * <ul>
 * <li><b>AI_CHAT</b> — 聊天会话主表（一次 request_id 对应一条记录）</li>
 * <li><b>AI_CHAT_MSG</b> — 聊天消息表（system/user/assistant/agent 角色消息）</li>
 * <li><b>AI_CHAT_PARAMS</b> — 聊天参数表（AI 从对话中提取的结构化参数）</li>
 * <li><b>AI_PROVIDER_MODEL / AI_PROVIDER / AI_PROVIDER_URL</b> — 供应商配置表</li>
 * </ul>
 *
 * @author guolei
 * @see ChatManagerBase
 */
public class ChatManagerDb {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ChatManagerDb.class);

	/** 请求参数值对象，用于 SQL 参数化绑定的上下文 */
	private final RequestValue rv;
	/** 数据库配置名称（对应 ewa_conf.xml 中的配置节） */
	private final String dbConfigName;

	/**
	 * 构造函数
	 *
	 * @param rv           请求参数值对象，贯穿所有 DB 操作的参数上下文
	 * @param dbConfigName 数据库配置名称（对应 ewa_conf.xml 中的配置节）
	 */
	public ChatManagerDb(RequestValue rv, String dbConfigName) {
		this.rv = rv;
		this.dbConfigName = dbConfigName;
	}

	// ==================== AI_CHAT 表 ====================

	/**
	 * 根据 request_id 查询已有的 AI 聊天记录。
	 * <p>
	 * 返回的 JSON 字段（大写键名）：AI_ID, AI_STEP_PREV, AI_UID, AI_REF, AI_REF_ID。
	 *
	 * @param requestId 用户请求唯一标识（对应 AI_UID 字段）
	 * @return 聊天记录 JSON（大写键名），无记录返回 {@code null}
	 */
	public JSONObject queryChatByRequestId(String requestId) {
		rv.addOrUpdateValue("request_id", requestId);
		String sql = "select AI_ID, AI_CUR_STEP as AI_STEP_PREV, AI_UID, AI_REF, AI_REF_ID from ai_chat where ai_uid=@request_id";
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (tb.getCount() == 0) {
			return null;
		}
		return tb.getRow(0).toJson("UPPER");
	}

	/**
	 * 更新 AI_CHAT 的当前步骤名称和修改时间。
	 *
	 * @param aiId     聊天会话 ID
	 * @param stepName 新的步骤名称
	 */
	public void updateChatStep(long aiId, String stepName) {
		rv.addOrUpdateValue("AIM_STEP", stepName);
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		String sql = "update AI_CHAT set AI_CUR_STEP=@AIM_STEP, AI_MDATE=@sys_date where AI_ID=@ai_id";
		DataConnection.updateAndClose(sql, dbConfigName, rv);
	}

	/**
	 * 创建新的 AI_CHAT 记录。
	 * <p>
	 * 插入前需确保 {@code rv} 中已设置以下参数：
	 * {@code request_id, p_ai_pid, AI_PROVIDER, AI_MODEL, AI_THINKING, AI_STREAM,
	 * AIM_STEP, MODE, AI_MAX_TOKEN, g_ADM_ID, G_WEB_USR_ID, g_SUP_ID, AI_REF, AI_REF_ID}。
	 *
	 * @param rv 请求参数值对象（含插入所需的全部参数）
	 * @return 自动生成的 AI_ID
	 */
	public long createChat(RequestValue rv) {
		String sql = """
				INSERT INTO AI_CHAT (
				    AI_UID, AI_PID, AI_PROVIDER, AI_MODEL, AI_THINKING, AI_STREAM, AI_CUR_STEP
				  , AI_MODE, AI_MAX_TOKEN, AI_CDATE, AI_MDATE, ADM_ID, USR_ID, SUP_ID
				  , AI_REF, AI_REF_ID
				) VALUES(
				    @request_id, @p_ai_pid, @AI_PROVIDER, @AI_MODEL, @AI_THINKING, @AI_STREAM, @AIM_STEP
				  , @MODE, @AI_MAX_TOKEN, @sys_DATE, @sys_DATE, @g_ADM_ID, @G_WEB_USR_ID, @g_SUP_ID
				  , @AI_REF, @AI_REF_ID
				)
				""";
		return DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
	}

	/**
	 * 获取当前聊天会话的下一轮交互轮次号。
	 * <p>
	 * 查询 AI_CHAT_MSG 中 AIM_NOI 的最大值并 +1；无消息时返回 1。
	 *
	 * @param aiId 聊天会话 ID
	 * @return 下一轮交互轮次号（从 1 开始）
	 */
	public short getNextInteractionNumber(long aiId) {
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		String sql = "select max(AIM_NOI) a from ai_chat_msg where ai_id=@ai_id";
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (tb.getCount() > 0 && !tb.getCell(0, 0).isNull()) {
			return (short) (Short.parseShort(tb.getCell(0, 0).toString()) + 1);
		}
		return 1;
	}

	// ==================== AI_CHAT_MSG 表 — 增删改 ====================

	/**
	 * 插入一条聊天消息。
	 *
	 * @param aiId                 聊天会话 ID
	 * @param msg                  消息内容
	 * @param role                 角色（system / user / assistant / agent）
	 * @param stepName             当前步骤名称
	 * @param promptName           提示词名称（可为 null）
	 * @param actionName           动作名称（可为 null）
	 * @param actionClass          动作类名（可为 null）
	 * @param numberOfInteractions 当前交互轮次号
	 * @param isSkipAppend         是否跳过后续追加到 AI 请求上下文（true = 仅记录不追加）
	 * @param byUser               是否为用户主动发送的消息
	 * @return 自动生成的 AIM_ID
	 */
	public long addMessage(long aiId, String msg, String role, String stepName, String promptName, String actionName,
			String actionClass, short numberOfInteractions, boolean isSkipAppend, boolean byUser) {
		rv.addOrUpdateValue("AIM_MSG", msg);
		rv.addOrUpdateValue("AIM_ROLE", role);
		if (!"assistant".equals(role)) {
			rv.addOrUpdateValue("AIM_TIME_END", new Date(), "date", 100);
		}
		rv.addOrUpdateValue("AIM_BY_USER", byUser ? 1 : 0);
		rv.addOrUpdateValue("AIM_NOI", numberOfInteractions);
		rv.addOrUpdateValue("AIM_STEP", stepName);
		rv.addOrUpdateValue("AIM_ACTION", actionName);
		rv.addOrUpdateValue("AIM_ACTION_CLASS", actionClass);
		rv.addOrUpdateValue("AIM_PROMPT_NAME", promptName);

		String sql = String.format("""
					INSERT INTO AI_CHAT_MSG( AI_ID, AIM_NOI, AIM_MSG, AIM_ROLE, AIM_BY_USER, AIM_TIME_BEGIN
						, AIM_TIME_END, AIM_STEP, AIM_ACTION, AIM_ACTION_CLASS, AIM_PROMPT_NAME, AIM_SKIP_APPEND)
					VALUES(@ai_id, @AIM_NOI, @AIM_MSG, @AIM_ROLE, @AIM_BY_USER, @sys_date
						, @AIM_TIME_END, @AIM_STEP, @AIM_ACTION, @AIM_ACTION_CLASS, @AIM_PROMPT_NAME, %d)
				""", (isSkipAppend ? 1 : 0));
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		return DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
	}

	/**
	 * 更新消息内容（流式输出完成后调用）。
	 *
	 * @param aimId 消息 ID
	 * @param msg   新的消息内容
	 */
	public void updateMessage(long aimId, String msg) {
		rv.addOrUpdateValue("AIM_MSG", msg);
		rv.addOrUpdateValue("AIM_TIME_END", new Date(), "date", 100);
		rv.addOrUpdateValue("_aim_id", aimId, "bigint", 100);
		String sql = "update AI_CHAT_MSG set AIM_MSG = @AIM_MSG, AIM_TIME_END= @AIM_TIME_END where AIM_ID = @_aim_id";
		DataConnection.updateAndClose(sql, dbConfigName, rv);
	}

	/**
	 * 更新消息的 Token 使用情况（从 JSON usage 对象中提取）。
	 * <p>
	 * 自动解析 {@code total_tokens}、{@code completion_tokens}、{@code prompt_tokens}， 以及
	 * {@code prompt_tokens_details.cached_tokens}（如存在）。
	 *
	 * @param aimId 消息 ID
	 * @param usage AI 响应中的 usage JSON 对象
	 */
	public void updateMessageTokens(long aimId, JSONObject usage) {
		long totalTokens = usage.optLong("total_tokens");
		long completionTokens = usage.optLong("completion_tokens");
		long promptTokens = usage.optLong("prompt_tokens");
		long cachedTokens = 0;
		if (usage.has("prompt_tokens_details")) {
			JSONObject details = usage.optJSONObject("prompt_tokens_details");
			if (details != null) {
				cachedTokens = details.optLong("cached_tokens");
			}
		}
		updateMessageTokens(aimId, totalTokens, completionTokens, promptTokens, cachedTokens);
	}

	/**
	 * 更新消息的 Token 使用情况（指定各项数值）。
	 *
	 * @param aimId            消息 ID
	 * @param totalTokens      总 Token 数
	 * @param completionTokens 完成 Token 数
	 * @param promptTokens     提示词 Token 数
	 * @param cachedTokens     缓存命中 Token 数（无缓存传 0）
	 */
	public void updateMessageTokens(long aimId, long totalTokens, long completionTokens, long promptTokens,
			long cachedTokens) {
		rv.addOrUpdateValue("_aim_id", aimId, "bigint", 100);
		rv.addOrUpdateValue("_total_tokens", totalTokens, "int", 100);
		rv.addOrUpdateValue("_completion_tokens", completionTokens, "int", 100);
		rv.addOrUpdateValue("_prompt_tokens", promptTokens, "int", 100);
		rv.addOrUpdateValue("_cached_tokens", cachedTokens, "int", 100);
		String sql = """
				update AI_CHAT_MSG set AIM_TOTAL_TOKENS = @_total_tokens,
				AIM_COMPLETION_TOKENS = @_completion_tokens,
				AIM_PROMPT_TOKENS = @_prompt_tokens,
				AIM_CACHED_TOKENS = @_cached_tokens
				where AIM_ID = @_aim_id
				""";
		DataConnection.updateAndClose(sql, dbConfigName, rv);
	}

	/**
	 * 获取指定聊天会话最后一条消息的 AIM_ID。
	 *
	 * @param aiId 聊天会话 ID
	 * @return 最大的 AIM_ID，无消息时返回 0
	 */
	public long getLastAimId(long aiId) {
		try {
			rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
			String sql = "select isnull(max(AIM_ID), 0) as LAST_AIM_ID from AI_CHAT_MSG where AI_ID = @ai_id";
			DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
			if (tb.getCount() > 0) {
				return tb.getCell(0, "LAST_AIM_ID").toLong();
			}
		} catch (Exception e) {
			LOGGER.error("Failed to get last AIM_ID for AI_ID={}", aiId, e);
		}
		return 0;
	}

	// ==================== AI_CHAT_MSG 表 — 查询 ====================

	/**
	 * 查询 agent 角色的消息列表（按 AIM_ID 倒序）。
	 * <p>
	 * 用于 {@code doAction} 去重：比较已有 agent 消息的 MD5 与当前动作输出的 MD5， 相同则跳过重复执行。
	 *
	 * @param aiId 聊天会话 ID
	 * @return agent 消息表，每行包含 AIM_MSG 字段
	 */
	public DTTable queryAgentMessages(long aiId) {
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		String sql = "select aim_msg from AI_CHAT_MSG where ai_id=@ai_id and AIM_ROLE='agent' order by AIM_ID desc";
		return DTTable.getJdbcTable(sql, rv);
	}

	/**
	 * 查询最新一条 system 角色消息的时间戳。
	 * <p>
	 * 用于 {@code checkPreviousOverTime} 判断缓存是否过期： 将返回时间与 {@code cachedSeconds}
	 * 比较，决定是否重建 system 提示词。
	 *
	 * @param aiId 聊天会话 ID
	 * @return 单行单列表（AIM_TIME_BEGIN），无 system 消息时返回空表
	 */
	public DTTable queryLatestSystemMessageTime(long aiId) {
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		String sql = "select AIM_TIME_BEGIN from AI_CHAT_MSG where ai_id=@ai_id and AIM_ROLE='system' order by AIM_ID desc";
		return DTTable.getJdbcTable(sql, "AIM_ID", 1, 1, "", rv);
	}

	/**
	 * 加载历史消息（按 AIM_ID 倒序，限制条数）。
	 * <p>
	 * 排除条件：AIM_ACTION 非空的动作消息、AIM_SKIP_APPEND=1 的已跳过消息。 仅加载 user / system /
	 * assistant 三种角色的消息。
	 *
	 * @param aiId     聊天会话 ID
	 * @param maxCount 最大加载条数
	 * @return 历史消息表，调用方需反转为正序后使用
	 */
	public DTTable loadHistoryMessages(long aiId, int maxCount) {
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		String sql = """
				select * from AI_CHAT_MSG
				where ai_id = @ai_id and AIM_ACTION is null
				and AIM_ROLE in ('user', 'system', 'assistant')
				and case when AIM_SKIP_APPEND is null then 0 else AIM_SKIP_APPEND end = 0
				order by AIM_ID desc
				""";
		return DTTable.getJdbcTable(sql, "AIM_ID", maxCount, 1, "", rv);
	}

	/**
	 * 标记指定 step 的旧消息为 {@code AIM_SKIP_APPEND=1}。
	 * <p>
	 * 缓存过期重建 system 提示词时调用：将当前 step 下所有带 AIM_PROMPT_NAME 的消息标记为跳过， 使其不再被
	 * {@link #loadHistoryMessages} 返回，从而让 {@code processPrompt} 重新执行 step prompts
	 * 获取最新数据。
	 *
	 * @param aiId     聊天会话 ID
	 * @param stepName 步骤名称
	 */
	public void markStepMessagesSkipped(long aiId, String stepName) {
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		rv.addOrUpdateValue("_skip_step", stepName);
		String sql = """
				update AI_CHAT_MSG set AIM_SKIP_APPEND=1 where ai_id=@ai_id \
				and AIM_STEP=@_skip_step \
				and AIM_PROMPT_NAME is not null and AIM_PROMPT_NAME<>'' \
				and (AIM_SKIP_APPEND is null or AIM_SKIP_APPEND=0)""";
		DTTable.getJdbcTable(sql, rv);
	}

	/**
	 * 加载对话上下文（最近 30 条消息），用于参数提取的 AI 调用。
	 * <p>
	 * 返回格式为 {@code role: msg} 的多行文本，按 AIM_ID 正序排列。 排除 {@code AIM_SKIP_APPEND=1}
	 * 的消息。
	 *
	 * @param aiId 聊天会话 ID
	 * @return 对话上下文文本，无消息时返回空字符串
	 */
	public String loadConversationContext(long aiId) {
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		String sql = """
				select top 30 AIM_ROLE, AIM_MSG from AI_CHAT_MSG m
				inner join AI_CHAT c on m.AI_ID = c.AI_ID
				where c.AI_ID = @ai_id
				and isnull(m.AIM_SKIP_APPEND, 0) = 0
				order by m.AIM_ID desc
				""";
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		StringBuilder sb = new StringBuilder();
		try {
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
			return sb.toString();
		} catch (Exception e) {
			LOGGER.error("Failed to load conversation context for AI_ID={}", aiId, e);
			return "";
		}
	}

	/**
	 * 查询父 chat 的 AI_PID（用于 multiOnlyUserMsg 模式）。
	 * <p>
	 * 当 step 设置了 {@code multiOnlyUserMsg=true} 时，需要找到父 chat 并提取 所有子 chat
	 * 中的用户消息来构建完整的对话上下文。
	 *
	 * @param aiId 聊天会话 ID
	 * @return 父 chat 的 AI_ID，无父级或查询失败返回 {@code null}
	 */
	public Long queryParentAiId(long aiId) {
		String sql = "select AI_PID from AI_CHAT where AI_ID=@ai_id";
		DTTable pidTb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (pidTb.getCount() == 0) {
			return null;
		}
		try {
			rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);

			Double pidObj = pidTb.getCell(0, "AI_PID").toDouble();
			if (pidObj == null) {
				return null;
			}
			long parentAiId = pidObj.longValue();
			return parentAiId <= 0 ? null : parentAiId;
		} catch (Exception e) {
			LOGGER.error("Failed to query parent AI_ID for aiId={}", aiId, e);
			return null;
		}
	}

	/**
	 * 查询父 chat 及所有子 chat 中的用户消息（{@code AIM_BY_USER=1}）。
	 * <p>
	 * 按 AIM_ID 正序返回，用于构建多轮对话的用户输入历史。
	 *
	 * @param parentAiId 父 chat 的 AI_ID（即 AI_CHAT.AI_PID 的值）
	 * @return 用户消息表，每行包含 AIM_MSG 字段
	 */
	public DTTable queryParentUserMessages(long parentAiId) {
		rv.addOrUpdateValue("parent_ai_id", parentAiId, "bigint", 100);
		String sql = """
				select AIM_MSG from AI_CHAT_MSG m
				where m.AIM_BY_USER = 1 and m.AIM_ROLE = 'user'
				and m.AI_ID in (
				  select AI_ID from AI_CHAT where AI_PID = @parent_ai_id
				) order by m.AIM_ID
				""";
		return DTTable.getJdbcTable(sql, dbConfigName, rv);
	}

	// ==================== AI_CHAT_PARAMS 表 ====================

	/**
	 * 加载已保存的聊天参数。
	 * <p>
	 * 从 AI_CHAT_PARAMS 表查询指定聊天会话的所有已保存参数（名称-值对）。
	 *
	 * @param aiId 聊天会话 ID
	 * @return 参数名 → 值 的 Map，无参数时返回空 Map
	 */
	public Map<String, String> loadSavedParams(long aiId) {
		Map<String, String> savedParams = new HashMap<>();
		rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
		String sql = "SELECT AIP_NAME, AIP_VAL FROM AI_CHAT_PARAMS WHERE AI_ID = @ai_id";
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		try {

			for (int i = 0; i < tb.getCount(); i++) {
				String name = tb.getCell(i, "AIP_NAME").toString();
				String val = tb.getCell(i, "AIP_VAL").toString();
				if (name != null && val != null) {
					savedParams.put(name, val);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load AI_CHAT_PARAMS for AI_ID={}", aiId, e);
		}
		return savedParams;
	}

	/**
	 * 保存 AI 提取的参数到 AI_CHAT_PARAMS 表（事务操作：先删后插）。
	 * <p>
	 * 遍历 {@code paramChecks} 中定义的参数，从 {@code extractedParams} 中取值， 值为空时使用
	 * {@code ParamCheck} 的默认值。整个操作在事务中执行， 失败时自动回滚。
	 *
	 * @param aiId            聊天会话 ID
	 * @param extractedParams AI 提取的参数 JSON（键为参数名，值为提取结果）
	 * @param paramChecks     参数定义列表（含名称、类型、默认值等）
	 * @return 实际保存的参数名 → 值 JSON
	 */
	public JSONObject saveParams(long aiId, JSONObject extractedParams, List<ParamCheck> paramChecks) {
		JSONObject saved = new JSONObject();
		long aimId = getLastAimId(aiId);

		DataConnection cnn = new DataConnection();
		cnn.setRequestValue(rv);
		cnn.setConfigName(dbConfigName);
		cnn.transBegin();
		String insertSql = """
				INSERT INTO AI_CHAT_PARAMS (AI_ID, AIM_ID, AIP_NAME, AIP_VAL, AIP_TYPE)
				VALUES (@AI_ID, @AIM_ID, @AIP_NAME, @AIP_VAL, @AIP_TYPE)
				""";
		try {
			rv.addOrUpdateValue("ai_id", aiId, "bigint", 100);
			cnn.executeUpdate("DELETE FROM AI_CHAT_PARAMS WHERE AI_ID=@ai_id");

			for (ParamCheck pc : paramChecks) {
				String name = pc.getName();
				String value = null;

				if (extractedParams.has(name) && !extractedParams.isNull(name)) {
					value = extractedParams.optString(name, "").trim();
				}

				if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
					value = pc.getDefaultValue();
				}

				if (value != null && !value.isEmpty()) {
					rv.addOrUpdateValue("AI_ID", aiId, "long", 100);
					rv.addOrUpdateValue("AIM_ID", aimId, "long", 100);
					rv.addOrUpdateValue("AIP_NAME", name);
					rv.addOrUpdateValue("AIP_VAL", value);
					rv.addOrUpdateValue("AIP_TYPE", pc.getType());

					cnn.executeUpdate(insertSql);
					saved.put(name, value);
				}
			}

			cnn.transCommit();
		} catch (Exception e) {
			cnn.transRollback();
			LOGGER.error("Failed to save params to database", e);
		} finally {
			cnn.close();
		}

		return saved;
	}

	// ==================== AI_PROVIDER_MODEL / AI_PROVIDER 表 ====================

	/**
	 * 检查 AI 供应商和模型是否有效，并加载 API URL 和 Key。
	 * <p>
	 * 校验逻辑：
	 * <ol>
	 * <li>查询 AI_PROVIDER_MODEL 确认模型存在且状态为 USED</li>
	 * <li>查询 AI_PROVIDER 确认供应商状态为 USED</li>
	 * <li>查询 AI_PROVIDER_URL 获取可用的 API URL 和 Key</li>
	 * </ol>
	 * <p>
	 * 返回 JSONObject：
	 * <ul>
	 * <li>成功：{@code RST=true, apiUrl, apiKey}</li>
	 * <li>失败：{@code RST=false, errorKey（i18n 键名）, errorArgs（格式化参数数组）}</li>
	 * </ul>
	 * 调用方根据 {@code errorKey} 通过 {@link ChatManagerI18nConstants} 获取本地化错误信息。
	 *
	 * @param aiProvider 供应商代码（如 qwen, openai, anthropic）
	 * @param aiModel    模型代码（如 qwen-plus, gpt-4o）
	 * @param apiOwnerId 模型归属供应商
	 * @return 校验结果 JSON
	 */
	public JSONObject checkProviderAndModel(String aiProvider, String aiModel, String apiOwnerId) {
		JSONObject result = new JSONObject();
		String sql = """
				select a.*, b.ap_status from AI_PROVIDER_MODEL a
				inner join AI_PROVIDER b on a.AP_CODE = b.AP_CODE
				where a.apm_code = @ai_model and a.ap_code = @ai_provider
				""";
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (tb.getCount() == 0) {
			result.put("RST", false);
			result.put("errorKey", ChatManagerI18nConstants.ErrorMessages.ERROR_MODEL_NOT_EXIST);
			result.put("errorArgs", new JSONArray().put(aiModel).put(aiProvider));
			return result;
		}
		try {
			if (!"USED".equalsIgnoreCase(tb.getCell(0, "APM_STATUS").toString())) {
				result.put("RST", false);
				result.put("errorKey", ChatManagerI18nConstants.ErrorMessages.ERROR_MODEL_OFFLINE_0);
				result.put("errorArgs", new JSONArray().put(aiModel).put(aiProvider));
				return result;
			}
			if (!"USED".equalsIgnoreCase(tb.getCell(0, "ap_status").toString())) {
				result.put("RST", false);
				result.put("errorKey", ChatManagerI18nConstants.ErrorMessages.ERROR_MODEL_OFFLINE_1);
				result.put("errorArgs", new JSONArray().put(aiModel).put(aiProvider));
				return result;
			}
			String sql1 = "select APU_URL, APU_KEY from AI_PROVIDER_URL where APU_STATUS='USED' and ap_code=@ai_provider ";
			if (apiOwnerId != null) {
				rv.addOrUpdateValue("_APU_OWN_ID", apiOwnerId);
				sql1 += " and APU_OWN_ID = @_APU_OWN_ID ";
			}
			sql1 += " order by APU_MDATE desc";
			DTTable tb1 = DTTable.getJdbcTable(sql1, dbConfigName, rv);
			if (tb1.getCount() == 0) {
				result.put("RST", false);
				result.put("errorKey", ChatManagerI18nConstants.ErrorMessages.ERROR_API_CONFIG_NOT_EXIST);
				result.put("errorArgs", new JSONArray().put(aiProvider));
				return result;
			}
			String apiUrl = tb1.getCell(0, "APU_URL").toString();
			String apiKey = tb1.getCell(0, "APU_KEY").toString();
			if (apiUrl == null || apiUrl.isEmpty()) {
				result.put("RST", false);
				result.put("errorKey", ChatManagerI18nConstants.ErrorMessages.ERROR_API_CONFIG_NOT_URL);
				result.put("errorArgs", new JSONArray().put(aiProvider));
				return result;
			}
			if (apiKey == null || apiKey.isEmpty()) {
				result.put("RST", false);
				result.put("errorKey", ChatManagerI18nConstants.ErrorMessages.ERROR_API_CONFIG_NOT_APIKEY);
				result.put("errorArgs", new JSONArray().put(aiProvider));
				return result;
			}
			result.put("RST", true);
			result.put("apiUrl", apiUrl);
			result.put("apiKey", apiKey);
		} catch (Exception e) {
			result.put("RST", false);
			result.put("errorKey", ChatManagerI18nConstants.ErrorMessages.ERROR_GENERAL);
			result.put("errorArgs", new JSONArray().put(e.getLocalizedMessage()));
		}
		return result;
	}

	 /**
	  * 将图片文件信息写入 AI_CHAT_EXP_ATTS。
	  * @param fileFrom
	  * @param fileRId
	  * @param filePath
	  * @param realPath
	  * @param fileExt
	  * @param fileSize
	  * @param fileMd5
	  * @param upJsp
	  * @return
	  */
	public long addChatExpAtts(String fileFrom, long fileRId, String filePath, String realPath, String fileExt,
			int fileSize, String fileMd5, String upJsp) {
		synchronized (rv) {
			rv.addOrUpdateValue("file_From", fileFrom);
			rv.addOrUpdateValue("file_RId", fileRId, "bigint", 100);
			rv.addOrUpdateValue("file_path", filePath);
			rv.addOrUpdateValue("file_real_path", realPath);
			rv.addOrUpdateValue("file_Ext", fileExt);
			rv.addOrUpdateValue("file_Size", fileSize, "int", 100);
			rv.addOrUpdateValue("file_md5", fileMd5);
			rv.addOrUpdateValue("file_up_jsp", upJsp);
			String sql = """
						INSERT INTO AI_CHAT_EXP_ATTS(FILE_STATUS,FILE_FROM, SUP_ID, ADM_ID, FILE_CDATE
							, FILE_EXT, FILE_SIZE, FILE_MD5
							, FILE_RID, FILE_PATH, FILE_REAL_PATH, FILE_UP_JSP)
						 VALUES('USED', @file_From, @G_SUP_ID, @G_ADM_ID, @sys_date
							 , @FILE_EXT, @FILE_SIZE, @FILE_MD5
							 , @file_RId, @file_path, @file_real_path, @file_up_jsp)
					""";
			return DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
		}
	}
}
