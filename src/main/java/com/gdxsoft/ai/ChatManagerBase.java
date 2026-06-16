/*
 * Copyright (c) 2025 GDX Software
 * 
 * 文件名: ChatManagerBase.java
 * 创建时间: 2025年9月7日
 * 作者: 郭磊 (guolei)
 * 版本: 1.0
 * 
 * 描述:
 * AI聊天管理器基础类，负责管理AI聊天会话的核心功能。
 * 本类提供了AI聊天系统的完整生命周期管理，包括：
 * 
 * 主要功能:
 * 1. 聊天会话管理 - 创建、维护和管理AI聊天会话
 * 2. 消息处理 - 处理用户输入、AI响应和系统消息
 * 3. AI模式管理 - 加载和管理不同的AI工作模式
 * 4. 步骤执行 - 管理AI处理步骤的执行流程
 * 5. 提示词管理 - 处理和组织AI提示词
 * 6. API调用管理 - 管理外部API的调用和集成
 * 7. 动作执行 - 执行自定义的AI动作和操作
 * 8. 数据持久化 - 管理聊天记录和消息的数据库存储
 * 9. 事件处理 - 处理实时输出事件和用户交互
 * 10. 参数验证 - 验证和管理AI请求参数
 * 
 * 核心特性:
 * - 支持多种AI提供商（如OpenAI、Anthropic等）
 * - 支持流式和非流式响应
 * - 支持思考模式和普通模式
 * - 支持多阶段提示词处理
 * - 支持API工具调用和检查
 * - 支持自定义动作和扩展
 * - 完整的国际化支持
 * - 线程安全的会话管理
 * 
 * 使用示例:
 * ```java
 * ChatManagerBase manager = new ChatManagerBase(requestValue, dbConfig, writer);
 * JSONObject result = manager.checkParams();
 * if (result.optBoolean("RST")) {
 *     manager.appendPrompts(requestData);
 *     // 处理AI响应...
 * }
 * ```
 * 
 * 注意事项:
 * - 本类使用了线程安全的ConcurrentHashMap来管理AI请求实例缓存
 * - 所有数据库操作都使用了参数化查询以防止SQL注入
 * - 支持事务性的消息处理和错误回滚
 * - 提供了完整的错误处理和日志记录机制
 * 
 * 依赖项:
 * - com.gdxsoft.easyweb.* - EasyWeb框架核心组件
 * - org.json.* - JSON处理库
 * - org.apache.commons.* - Apache Commons工具库
 * - org.slf4j.* - 日志框架
 * 
 * 历史记录:
 * 2025-09-06: 初始版本创建，实现基础聊天管理功能
 * 2025-09-07: 添加API检查和调用功能，重构代码结构
 * 
 * @author 郭磊 (guolei)
 * @version 1.0
 * @since 2025-09-06
 */
package com.gdxsoft.ai;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.ChatManagerI18nConstants.ErrorMessages;
import com.gdxsoft.ai.ChatManagerI18nConstants.LogMessages;
import com.gdxsoft.ai.ChatManagerI18nConstants.StatusMessages;
import com.gdxsoft.ai.export.IAction;
import com.gdxsoft.ai.modes.*;
import com.gdxsoft.ai.request.DefaultOutEvents;
import com.gdxsoft.ai.request.IOutEvents;
import com.gdxsoft.ai.request.IRequestAI;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.RequestAIFactory;
import com.gdxsoft.ai.request.RequestDataFactory;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.datasource.DataConnection;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UJSon;
import com.gdxsoft.easyweb.utils.UObjectValue;
import com.gdxsoft.easyweb.utils.Utils;

/**
 * AI聊天管理器 负责管理AI聊天会话、处理消息、管理AI模式和步骤 提供AI请求的创建、参数检查、提示词管理等核心功能
 * 
 * @author guolei
 */
public class ChatManagerBase {
	/** AI请求实例缓存，使用线程安全的ConcurrentHashMap */
	public static final Map<String, IRequestAI> REQUEST_AIS = new ConcurrentHashMap<>();

	/**
	 * 添加AI请求实例到缓存
	 * 
	 * @param key 缓存键
	 * @param req AI请求实例
	 */
	public static void putRequestAI(String key, IRequestAI req) {
		REQUEST_AIS.put(key, req);
	}

	/**
	 * 从缓存中移除AI请求实例
	 * 
	 * @param key 缓存键
	 */
	public static void removeRequestAI(String key) {
		REQUEST_AIS.remove(key);
	}

	/**
	 * 从缓存中获取AI请求实例
	 * 
	 * @param key 缓存键
	 * @return AI请求实例，如果不存在则返回null
	 */
	public static IRequestAI getRequestAI(String key) {
		return REQUEST_AIS.get(key);
	}

	/**
	 * 加载AI模式配置
	 * 
	 * @param path 配置文件路径
	 * @throws Exception 加载失败时抛出异常
	 */
	public static void loadModes(String path, final ClassLoader classLoader) throws Exception {
		// 从资源文件中读取AI模式配置
		String xml = IOUtils.resourceToString(path, StandardCharsets.UTF_8, classLoader);
		// .getResourceContent(path);
		com.gdxsoft.ai.modes.Modes modes = new com.gdxsoft.ai.modes.Modes();
		modes.loadModes(xml);
	}

	/** 日志记录器 */
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ChatManagerBase.class);

	/** 请求参数值对象 */
	private RequestValue rv;

	/** 输出事件处理器 */
	private IOutEvents outEvents;
	/** 请求ID，用于标识唯一的聊天会话 */
	private String requestId;
	/** 用户输入的提示词 */
	private String prompt;
	/** AI API的URL地址 */
	private String apiUrl;
	/** AI API的访问密钥 */
	private String apiKey;

	/** 是否为新的聊天会话 */
	private boolean isNew;
	/** AI聊天记录ID */
	private long aiId;
	/** 上一步的步骤名称 */
	private String aiStepPrev;

	/** AI模式名称 */
	private String modeName;
	/** AI模式对象 */
	private Mode mode;

	/** 当前步骤名称 */
	private String stepName;
	/** 当前步骤对象 */
	private Step step;
	/** 步骤执行动作 */
	private IAction stepAction;

	/** 动作名称 */
	private String actionName;
	/** 动作对象 */
	private Action action;
	/** 动作类名 */
	private String actionClassName;

	/** 输出流写入器 */
	private PrintWriter writer;
	/** 数据库配置名称 */
	private String dbConfigName;
	/** AI提供商 */
	private String aiProvider;
	/** AI模型名称 */
	private String aiModel;
	/** 是否开启思考模式 */
	private boolean aiThinking;

	/** 是否开启流式输出 */
	private boolean aiStream;

	/** 是否使用英文 */
	private boolean en;

	/**
	 * 获取是否开启流式输出
	 * 
	 * @return
	 */
	public boolean isAiStream() {
		return aiStream;
	}

	/**
	 * 设置是否开启流式输出
	 * 
	 * @param aiStream
	 */
	public void setAiStream(boolean aiStream) {
		this.aiStream = aiStream;
	}

	/**
	 * 获取是否使用英文
	 * 
	 * @return 是否使用英文
	 */
	public boolean isEn() {
		return en;
	}

	/**
	 * 设置是否使用英文
	 * 
	 * @param en 是否使用英文
	 */
	public void setEn(boolean en) {
		this.en = en;
	}

	/**
	 * 获取是否开启思考模式
	 * 
	 * @return 思考模式状态
	 */
	public boolean isAiThinking() {
		return aiThinking;
	}

	/**
	 * 设置思考模式
	 * 
	 * @param thinking 思考模式状态
	 */
	public void setAiThinking(boolean thinking) {
		this.aiThinking = thinking;
	}

	/**
	 * 构造函数
	 * 
	 * @param rv           请求参数值对象
	 * @param dbConfigName 数据库配置名称
	 * @param writer       输出流写入器
	 */
	public ChatManagerBase(RequestValue rv, String dbConfigName, PrintWriter writer) {
		this.rv = rv;
		this.writer = writer;
		this.dbConfigName = dbConfigName;
		// 从请求参数中获取语言设置
		this.en = rv.isEn();
	}

	/**
	 * 获取输出事件处理器
	 * 
	 * @return 输出事件处理器
	 */
	public IOutEvents getOutEvents() {
		return outEvents;
	}

	/**
	 * 设置输出事件处理器
	 * 
	 * @param outEvents 输出事件处理器
	 */
	public void setOutEvents(IOutEvents outEvents) {
		this.outEvents = outEvents;
	}

	/**
	 * 创建AI请求实例
	 * 
	 * @return AI请求实例，创建失败返回null
	 */
	public IRequestAI createRequestAI() {
		// 根据AI提供商创建对应的请求实例
		IRequestAI req = RequestAIFactory.createRequestAI(this.aiProvider);
		if (req == null) {
			return null;
		}
		// 初始化API URL和密钥
		req.initUrlAndKey(apiUrl, apiKey);

		return req;
	}

	public IRequestData createRequestData() {
		IRequestData reqData = RequestDataFactory.createRequestData(aiProvider);
		/*
		 * 低Temperature（0.1-0.5）： 回答事实性问题（如“1+1=？”）。 生成结构化输出（如JSON、代码）。 确保一致性和准确性。
		 * 
		 * 中Temperature（0.5-0.8）： 日常对话、用户交互。 平衡创造性和可预测性。
		 * 
		 * 高Temperature（0.9-1.5）： 创意写作（如故事、诗歌）。 头脑风暴或生成多样化点子
		 */
		reqData.stream(this.isAiStream()).model(this.aiModel).thinking(this.isAiThinking());
		if (mode.getTemperature() != 0) {
			reqData.temperature(mode.getTemperature());
		}
		if (mode.getTopP() != 0) {
			reqData.topP(mode.getTopP());
		}
		LOGGER.info(getText(LogMessages.MODEL_REQUEST_PARAMS), reqData.buildJson());
		return reqData;
	}

	public IRequestData createRequestDataForApiCheck() {
		IRequestData reqData = RequestDataFactory.createRequestData(aiProvider);
		/*
		 * 低Temperature（0.1-0.5）： 回答事实性问题（如“1+1=？”）。 生成结构化输出（如JSON、代码）。 确保一致性和准确性。
		 * 
		 * 中Temperature（0.5-0.8）： 日常对话、用户交互。 平衡创造性和可预测性。
		 * 
		 * 高Temperature（0.9-1.5）： 创意写作（如故事、诗歌）。 头脑风暴或生成多样化点子
		 */
		reqData.stream(false).model(this.aiModel).thinking(false);
		reqData.temperature(0.3);
		reqData.topP(0.3);
		reqData.responseFormat("json_object");

		LOGGER.info(getText(LogMessages.MODEL_REQUEST_PARAMS), reqData.buildJson());
		return reqData;
	}

	/**
	 * 执行AI动作 首先检查是否存在相同内容的历史记录，如果存在则直接返回 否则执行新的动作并保存结果
	 * 
	 * @throws Exception 执行失败时抛出异常
	 */
	public void doAction() throws Exception {
		// 生成完整的文本内容和MD5摘要
		String fullText = mode.createStepActionRefFulleText(step, this.dbConfigName, rv);
		String md5 = Utils.md5(fullText);

		// 查询之前的AI代理消息，检查是否已有相同内容的处理结果
		String sqlAgent = "select aim_msg from AI_CHAT_MSG where ai_id=@ai_id and AIM_ROLE='agent' order by AIM_ID desc";
		DTTable tbAgent = DTTable.getJdbcTable(sqlAgent, rv);
		for (int i = 0; i < tbAgent.getCount(); i++) {
			String msg = tbAgent.getCell(i, "AIM_MSG").toString();
			try {
				JSONObject jsonMsg = new JSONObject(msg);
				// 如果找到相同MD5的历史记录，直接返回该结果
				if (jsonMsg.has("source_md5") && jsonMsg.optString("source_md5").equals(md5)) {
					outEvent(jsonMsg.toString());
					return;
				}
			} catch (Exception e) {
				// 如果消息不是JSON格式，忽略该消息
				LOGGER.info(getText(LogMessages.MESSAGE_NOT_JSON) + msg);
			}
		}

		// 发送处理中的提示消息
		JSONObject msg1 = UJSon.rstTrue("");
		msg1.put("content", "<i>" + getText(StatusMessages.ACTION_CREATING) + "</i>");
		msg1.put("action", actionName);
		outEvent(msg1.toString());

		// 执行实际的动作
		JSONObject result = stepAction.doAction(rv, fullText);
		result.put("source_md5", md5);
		LOGGER.info(getText(LogMessages.EXPORT_RESULT), result.toString(2));
		// 保存结果到数据库
		this.addAiChatMsg(result.toString(), "agent", true);

		// 输出最终结果
		outEvent(result.toString());
	}

	/**
	 * 添加提示词到请求数据中
	 * 
	 * @param isNew   是否为新会话
	 * @param reqData 请求数据对象
	 */
	public void appendPrompts(IRequestData reqData) throws Exception {
		// key = step+ "|" + promptName ;
		Map<String, Boolean> existsMessages = new HashMap<>();
		if (!isNew) {
			// 会排除action 的消息
			this.appendPreviousMessages(reqData, existsMessages);

			if (step.getName().equals(aiStepPrev) && StringUtils.isBlank(actionName)) {
				// 当前step和上次的step相同，且没有action，action不附加到以前的消息里
				return;
			}
		}
		Map<String, String> refHeaders = this.rv.getHttpHeaders();
		for (String name : refHeaders.keySet()) {
			String value = refHeaders.get(name);
			refHeaders.put(name, value);
		}

		// 指定的提示词
		String prompts = rv.s("prompts");
		Set<String> promptSet = new HashSet<>();
		if (!StringUtils.isBlank(prompts)) {
			String[] ps = prompts.split(",");
			for (String p : ps) {
				promptSet.add(p.trim());
			}
		}

		this.apiToolsChecks(refHeaders);
		// mode.createStepPrompts(step, "", g_rv);
		// 分两阶段处理提示词：先处理非apisCheck的提示词，再处理apisCheck=true的提示词

		for (int i = 0; i < step.getPrompts().size(); i++) {
			Prompt p = step.getPrompts().get(i);
			// 处理单个提示词
			processPrompt(p, promptSet, existsMessages, reqData, refHeaders);
		}

	}

	/**
	 * 利用用API检查提示词是否调用Apis
	 * 
	 * @param refHeaders
	 * @throws Exception
	 */
	private List<Prompt> apiToolsChecks(Map<String, String> refHeaders) throws Exception {
		List<Prompt> prompts = new ArrayList<>();
		for (int i = 0; i < step.getPrompts().size(); i++) {
			Prompt p = step.getPrompts().get(i);
			if (p.isApisCheck()) {
				prompts.add(p);
			}
		}

		if (prompts.size() == 0) {
			return prompts;
		}
		StringBuilder totalApiResult = new StringBuilder();
		for (int i = 0; i < prompts.size(); i++) {
			Prompt p = prompts.get(i);
			String apiResult = apiToolsCheck(p, totalApiResult.toString(), refHeaders);

			p.setContent(apiResult);
			if (totalApiResult.length() > 0) {
				totalApiResult.append(",\n");
			}
			totalApiResult.append(apiResult);
		}

		return prompts;
	}

	/**
	 * 处理单个API检查提示词
	 * 
	 * @param prompt             要处理的提示词
	 * @param previousApiResults 之前的API调用结果
	 * @param refHeaders         请求头信息
	 * @return API调用结果内容
	 * @throws Exception 处理失败时抛出异常
	 */
	private String apiToolsCheck(Prompt prompt, String previousApiResults, Map<String, String> refHeaders)
			throws Exception {
		var reqCheckData = this.createRequestDataForApiCheck();

		if (previousApiResults.length() > 0) {
			// 已经有api调用结果，直接设置内容
			reqCheckData.addMessage(previousApiResults, "assistant");
		}

		// 处理单个提示词
		mode.createStepPrompt(prompt, "", rv, refHeaders);
		String role = prompt.getRole();
		if (StringUtils.isBlank(role)) {
			role = "user";
		}
		String promptContent = prompt.getContent();
		if (!StringUtils.isBlank(prompt.getPrefix())) {
			promptContent = prompt.getPrefix() + promptContent;
		}
		promptContent = this.rv.replaceParameters(promptContent);
		promptContent += "\n\n用户输入：" + this.prompt;
		reqCheckData.addMessage(promptContent, role);

		// 记录到数据库中
		rv.addOrUpdateValue("AIM_PROMPT_NAME", prompt.getName());
		this.addAiChatMsg(promptContent, "api_tools_checks", true);
		rv.addOrUpdateValue("AIM_PROMPT_NAME", null);

		var req = this.createRequestAI();
		// 记录一条 curl 命令
		String aiCurl = req.curl(reqCheckData);
		this.addAiChatMsg(aiCurl, "api_check_curl", true);

		long aimId = this.addAiChatMsg("", "assistant", true);
		// 调用AI接口
		String fullText = req.doPost(reqCheckData);
		this.updateAiChatMsg(aimId, fullText);

		// 提取 JSON 响应并返回成功信息
		JSONObject json = req.extraceJson(fullText, true);
		String content = json.getString("content").trim();
		JSONArray tools;
		if (content.startsWith("{")) {
			// 不是JSON数组，直接返回
			JSONObject tool = new JSONObject(content);
			tools = new JSONArray();
			tools.put(tool);
		} else {
			tools = new JSONArray(content);
		}
		StringBuilder sbApisContent = new StringBuilder();
		for (int ia = 0; ia < tools.length(); ia++) {
			JSONObject tool = tools.getJSONObject(ia);
			String toolName = tool.optString("tool");
			if (toolName.equalsIgnoreCase("none")) {
				continue;
			}
			JSONObject args = tool.optJSONObject("args");
			if (args == null) {
				continue;
			}

			String apiCallResult = executeApiCall(toolName, args, refHeaders);
			if (sbApisContent.length() > 0) {
				sbApisContent.append(",\n");
			}
			sbApisContent.append(apiCallResult);
		}

		return sbApisContent.toString();
	}

	/**
	 * 执行单个API调用
	 * 
	 * @param toolName   API工具名称
	 * @param args       API参数
	 * @param refHeaders 请求头信息
	 * @return API调用结果内容
	 * @throws Exception 执行失败时抛出异常
	 */
	private String executeApiCall(String toolName, JSONObject args, Map<String, String> refHeaders) throws Exception {
		RequestValue rv = this.rv.clone();
		for (String argName : args.keySet()) {
			String argValue = args.optString(argName);
			rv.addOrUpdateValue(argName, argValue);
		}

		Prompt apiPrompt = new Prompt();
		apiPrompt.setName(toolName + "##_api");
		apiPrompt.setRole("user");
		apiPrompt.setContent("");
		apiPrompt.setDescription(toolName + " API调用");
		apiPrompt.setApi(toolName);

		String apiCallCurl = mode.createCurlOfPromptApi(apiPrompt, rv, refHeaders);
		// 添加一条 curl 命令记录
		this.addAiChatMsg(apiCallCurl, "api_call_curl", true);

		// 根据 debugOutput 开关决定是否输出调试信息
		if (mode.isDebugOutput()) {
			JSONObject msg = UJSon.rstTrue("");
			msg.put("reasoning_content", "调用API: " + toolName + ", " + args.toString() + "\n\n");
			this.outEvent(msg.toString());
		}

		mode.createStepPromptByApi(apiPrompt, rv, refHeaders);
		String calledContent = apiPrompt.getContent();
		// 记录api调用的结果
		this.addAiChatMsg(calledContent, "api_call_content", true);

		var api = mode.getApi(toolName);
		String apiContent = api.getDescription() + "数据:\n" + calledContent;

		return apiContent;
	}

	/**
	 * 处理单个提示词
	 * 
	 * @param p              提示词对象
	 * @param promptSet      指定的提示词集合
	 * @param existsMessages 已存在的消息映射
	 * @param reqData        请求数据对象
	 * @param refHeaders     请求头信息
	 * @throws Exception 处理失败时抛出异常
	 */
	private void processPrompt(Prompt p, Set<String> promptSet, Map<String, Boolean> existsMessages,
			IRequestData reqData, Map<String, String> refHeaders) throws Exception {

		if (!"system".equalsIgnoreCase(p.getRole()) && promptSet.size() > 0 && !promptSet.contains(p.getName())) {
			// 如果不包含在请求的提示词中，则跳过
			return;
		}
		String key = step.getName() + "|" + p.getName();
		if (existsMessages.containsKey(key)) {
			// 已经存在该消息，不再添加
			return;
		}
		mode.createStepPrompt(p, "", rv, refHeaders);
		String role = p.getRole();
		if (StringUtils.isBlank(role)) {
			role = "user";
		}
		String promptContent = p.getContent();
		if (StringUtils.isBlank(promptContent)) {
			// 为空则跳过
			return;
		}

		if (!StringUtils.isBlank(p.getPrefix())) {
			promptContent = p.getPrefix() + promptContent;
		}
		promptContent = this.rv.replaceParameters(promptContent);
		reqData.addMessage(promptContent, role);

		// 记录到数据库中
		rv.addOrUpdateValue("AIM_PROMPT_NAME", p.getName());
		this.addAiChatMsg(promptContent, role, false);
		rv.addOrUpdateValue("AIM_PROMPT_NAME", null);

		// 如果是用户角色，且需要在聊天中显示
		if (p.isShowInChat()) {
			JSONObject promptMsg = UJSon.rstTrue("");
			promptMsg.put("content", "```prompt\n" + promptContent + "\n```\n\n");

			promptMsg.put("prompt", p.getName());
			this.outEvent(promptMsg.toString());
		}

	}

	/**
	 * 添加历史消息到请求数据中
	 *
	 * @param reqData 请求数据对象
	 * @param rv      请求值对象
	 */
	public void appendPreviousMessages(IRequestData reqData, Map<String, Boolean> existsMessages) throws Exception {
		// 限制历史消息条数（使用 DTTable 分页，不依赖 SQL 方言）
		int maxMsgCount = 30; // 默认值
		if (this.mode != null) {
			maxMsgCount = this.mode.getMaxHistoryMessages();
		}

		String existsSql = "select * from AI_CHAT_MSG where ai_id=@ai_id and AIM_ACTION is null \n"
				+ " and AIM_ROLE in ('user', 'system', 'assistant') \n"
				+ " and case when AIM_SKIP_APPEND is null then 0 else AIM_SKIP_APPEND end = 0 order by AIM_ID desc";
		// 使用 DTTable 分页方法限制返回条数，兼容所有数据库
		DTTable tbMsg = DTTable.getJdbcTable(existsSql, "AIM_ID", maxMsgCount, 1, "", rv);

		if (tbMsg == null || !tbMsg.isOk()) {
			// 查询失败或无数据，仅添加 API prompts
			addApiPrompts(reqData, rv.getHttpHeaders());
			return;
		}

		// 反转为正序（因为用了 ORDER BY AIM_ID desc）
		List<org.json.JSONObject> msgList = new ArrayList<>();
		for (int i = tbMsg.getCount() - 1; i >= 0; i--) {
			String msg = tbMsg.getCell(i, "AIM_MSG").toString();
			if (StringUtils.isBlank(msg)) {
				continue;
			}
			String role = tbMsg.getCell(i, "AIM_ROLE").toString();
			String step = tbMsg.getCell(i, "AIM_STEP").toString();
			String promptName = tbMsg.getCell(i, "AIM_PROMPT_NAME").toString();
			String key = step + "|" + promptName;
			existsMessages.put(key, true);

			org.json.JSONObject msgObj = new org.json.JSONObject();
			msgObj.put("role", role);
			msgObj.put("content", msg);
			msgList.add(msgObj);
		}

		// Token 估算和截断
		int maxTokens = 100000; // 默认值
		if (this.mode != null) {
			maxTokens = this.mode.getMaxHistoryTokens();
		}
		msgList = truncateByTokens(msgList, maxTokens);

		// 添加到请求
		for (org.json.JSONObject msgObj : msgList) {
			reqData.addMessage(msgObj.getString("content"), msgObj.getString("role"));
		}

		addApiPrompts(reqData, rv.getHttpHeaders());
	}

	/**
	 * 添加 API 工具检查提示
	 */
	private void addApiPrompts(IRequestData reqData, Map<String, String> refHeaders) throws Exception {
		var apiPrompts = this.apiToolsChecks(refHeaders);
		for (var p : apiPrompts) {
			String role = p.getRole();
			if (StringUtils.isBlank(role)) {
				role = "user";
			}
			String promptContent = p.getContent();
			if (StringUtils.isBlank(promptContent)) {
				continue;
			}
			if (!StringUtils.isBlank(p.getPrefix())) {
				promptContent = p.getPrefix() + promptContent;
			}
			reqData.addMessage(promptContent, role);
		}
	}

	/**
	 * 估算文本的 token 数量
	 * 中文/日文/韩文按 1.5 char/token，其他按 4 char/token 估算
	 */
	private int estimateTokens(String text) {
		int cjk = 0, other = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= 0x4E00 && c <= 0x9FFF) {
				cjk++;
			} else if (c >= 0xAC00 && c <= 0xD7A3) {
				cjk++; // Korean
			} else if (c >= 0x3040 && c <= 0x30FF) {
				cjk++; // Japanese hiragana/katakana
			} else {
				other++;
			}
		}
		return (cjk / 2) + (other / 4) + 1; // +1 避免为 0
	}

	/**
	 * 根据 token 限制截断消息列表，从最早的消息开始删除
	 */
	private List<org.json.JSONObject> truncateByTokens(List<org.json.JSONObject> messages, int maxTokens) {
		if (maxTokens <= 0) return messages;

		int totalTokens = 0;
		for (org.json.JSONObject msg : messages) {
			totalTokens += estimateTokens(msg.optString("content", ""));
		}

		if (totalTokens <= maxTokens) {
			return messages; // 未超限，无需截断
		}

		// 从最早的消息开始删除，直到低于限制
		List<org.json.JSONObject> result = new ArrayList<>(messages);
		int removed = 0;
		while (totalTokens > maxTokens && result.size() > 1) {
			String removedContent = result.get(0).optString("content", "");
			totalTokens -= estimateTokens(removedContent);
			result.remove(0);
			removed++;
		}

		if (removed > 0) {
			LOGGER.warn("History truncated: removed {} messages to stay under {} token limit", removed, maxTokens);
		}

		return result;
	}

	/**
	 * 输出事件数据到客户端
	 * 
	 * @param msg    消息内容
	 * @param writer 输出流
	 */
	public void outEvent(Object msg) {
		// writer.println("data: " + msg.toString() + "\n\n");
		// writer.flush();
		if (this.outEvents == null) {
			this.outEvents = new DefaultOutEvents();
		}
		this.outEvents.outEvent(msg.toString(), writer);
	}

	/**
	 * 检查参数, request_id, ai_provider, ai_model, prompt
	 * 
	 * @param rv 请求值对象
	 * @return 检查结果
	 */
	public JSONObject checkParams() throws Exception {

		String requestId = rv.s("request_id");
		if (requestId == null || requestId.trim().length() == 0) {
			JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_NO_REQUEST_ID));
			return rst;
		}
		this.requestId = requestId;
		String aiProvider = rv.s("ai_provider");
		if (aiProvider == null || aiProvider.trim().length() == 0) {
			JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_NO_AI_PROVIDER));
			return rst;
		}
		this.aiProvider = aiProvider;
		String aiModel = rv.s("ai_model");
		if (aiModel == null || aiModel.trim().length() == 0) {
			JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_NO_AI_MODEL));
			return rst;
		}
		this.aiModel = aiModel;

		String modeName = rv.s("mode");
		if (modeName == null || modeName.trim().length() == 0) {
			JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_NO_AI_MODE));
			return rst;
		}
		this.modeName = modeName;
		Mode mode = Modes.getMode(modeName);
		if (mode == null) {
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_MODE_NOT_FOUND) + modeName);
		}
		this.mode = mode;

		String prompt = rv.s("prompt");
		this.prompt = prompt;

		// 是否思考模式
		if (StringUtils.isBlank(rv.s("ai_thinking"))) {
			this.aiThinking = mode.isThinking();
		} else {
			this.aiThinking = Utils.cvtBool(rv.s("ai_thinking"));
		}

		String stepName = rv.s("step");
		if (!StringUtils.isBlank(stepName)) {
			step = mode.getStep(stepName);
			if (step == null) {
				return UJSon.rstFalse(getText(ErrorMessages.ERROR_STEP_NOT_FOUND) + stepName);
			}
		} else {
			step = mode.getStep(0);
			stepName = step.getName();
		}
		this.stepName = stepName;

		// 是否思考模式
		if (StringUtils.isBlank(rv.s("ai_stream"))) {
			this.aiStream = step.isStream();
		} else {
			this.aiStream = Utils.cvtBool(rv.s("ai_stream"));
		}

		try {
			this.loadAction();
		} catch (Exception e) {
			LOGGER.error(getText(ErrorMessages.ACTION_LOAD_FAILED), e.getLocalizedMessage());
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_ACTION_LOAD_FAILED) + e.getMessage());
		}
		var checkedProviderAndModel = checkProviderAndModel();

		if (!checkedProviderAndModel.optBoolean("RST")) {
			return checkedProviderAndModel;
		}

		if (StringUtils.isBlank(this.apiUrl)) {
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_API_URL_EMPTY));
		}

		// 当前步骤名称
		rv.addOrUpdateValue("AIM_STEP", this.stepName);
		// 当前action名称
		rv.addOrUpdateValue("AIM_ACTION", this.actionName);
		// 当前action类名
		rv.addOrUpdateValue("AIM_ACTION_CLASS", this.actionClassName);

		JSONObject chat = this.getOrNewAiChat();
		if (chat.optBoolean("RST")) {
			return chat;
		}
		LOGGER.info(getText(LogMessages.AI_CHAT_RECORD), chat.toString(2));

		rv.addOrUpdateValue("ai_id", this.aiId, "bigint", 100);

		// chat 包含 AI_ID, AI_STEP_PREV
		String stepPrev = this.getAiStepPrev() == null ? "----gdx----!!-" : this.getAiStepPrev();
		rv.addOrUpdateValue("AI_STEP_PREV", stepPrev);

		JSONObject rst = UJSon.rstTrue();
		rst.put("api_url", this.apiUrl);
		rst.put("api_key", maskApiKey(this.apiKey));
		rst.put("ai_provider", aiProvider);
		rst.put("ai_model", aiModel);
		rst.put("ai_thinking", this.aiThinking);
		rst.put("ai_temperature", mode.getTemperature());
		rst.put("ai_top_p", mode.getTopP());

		rst.put("prompt", prompt);
		rst.put("request_id", requestId);
		rst.put("mode", modeName);
		rst.put("step", stepName);
		rst.put("action", actionName);
		rst.put("action_class", actionClassName);

		rst.put("ai_id", this.aiId);
		rst.put("is_new", this.isNew);
		rst.put("ai_step_prev", this.aiStepPrev);

		return rst;
	}

	/**
	 * 检查输入参数是否满足当前模式的paramChecks定义
	 * 从AI_CHAT_PARAMS表加载已保存的参数，与paramChecks定义进行校验
	 * 
	 * @return 校验结果JSON：{RST:true/false, params:{...}, missing:[...], invalid:[...]}
	 */
	public JSONObject checkInputParams() {
		List<ParamCheck> paramChecks = mode.getParamChecks();
		if (paramChecks == null || paramChecks.isEmpty()) {
			JSONObject rst = UJSon.rstTrue("No paramChecks defined");
			rst.put("params", new JSONObject());
			rst.put("missing", new JSONArray());
			rst.put("invalid", new JSONArray());
			return rst;
		}

		Map<String, String> savedParams = new HashMap<>();
		try {
			rv.addOrUpdateValue("ai_id", this.aiId, "bigint", 100);
			String sql = "SELECT AIP_NAME, AIP_VAL FROM AI_CHAT_PARAMS WHERE AI_ID = @ai_id";
			DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
			for (int i = 0; i < tb.getCount(); i++) {
				String name = tb.getCell(i, "AIP_NAME").toString();
				String val = tb.getCell(i, "AIP_VAL").toString();
				if (name != null && val != null) {
					savedParams.put(name, val);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load AI_CHAT_PARAMS for AI_ID={}", this.aiId, e);
		}

		JSONObject params = new JSONObject();
		JSONArray missing = new JSONArray();
		JSONArray invalid = new JSONArray();

		for (ParamCheck pc : paramChecks) {
			String name = pc.getName();
			String value = savedParams.get(name);

			if (value == null || value.trim().isEmpty()) {
				if (pc.getDefaultValue() != null && !pc.getDefaultValue().isEmpty()) {
					value = pc.getDefaultValue();
				} else {
					missing.put(name);
					continue;
				}
			}

			if ("int".equals(pc.getType())) {
				try {
					Integer.parseInt(value);
				} catch (NumberFormatException e) {
					JSONObject err = new JSONObject();
					err.put("name", name);
					err.put("value", value);
					err.put("reason", "Invalid integer");
					invalid.put(err);
					continue;
				}
			} else if ("enum".equals(pc.getType())) {
				if (!pc.isValidEnumValue(value)) {
					JSONObject err = new JSONObject();
					err.put("name", name);
					err.put("value", value);
					err.put("reason", "Invalid enum value, expected one of: " + pc.getOptionKeys());
					invalid.put(err);
					continue;
				}
			}

			params.put(name, value);
		}

		boolean isValid = missing.length() == 0 && invalid.length() == 0;
		JSONObject rst = isValid ? UJSon.rstTrue() : UJSon.rstFalse("Parameter validation failed");
		rst.put("params", params);
		rst.put("missing", missing);
		rst.put("invalid", invalid);
		return rst;
	}

	/**
	 * 从用户请求中提取参数并保存到AI_CHAT_PARAMS表
	 * 使用AI从对话上下文中提取结构化参数（出发城市、目的地、天数等）
	 * 
	 * @return 保存结果JSON：{RST:true/false, params:{...}}
	 */
	public JSONObject saveInputParams() {
		List<ParamCheck> paramChecks = mode.getParamChecks();
		if (paramChecks == null || paramChecks.isEmpty()) {
			return UJSon.rstTrue("No paramChecks defined");
		}

		String context = loadConversationContext();
		if (context.isEmpty()) {
			return UJSon.rstFalse("No conversation context");
		}

		String extractPrompt = buildExtractPrompt(paramChecks);
		String aiResponse = callAiForExtraction(extractPrompt + "\n\n对话内容：\n" + context);
		if (aiResponse == null || aiResponse.isEmpty()) {
			return UJSon.rstFalse("AI extraction failed");
		}

		JSONObject extractedParams = parseExtractedParams(aiResponse);
		if (extractedParams == null) {
			return UJSon.rstFalse("Failed to parse AI response as JSON");
		}

		JSONObject savedParams = saveParamsToDatabase(extractedParams, paramChecks);
		JSONObject rst = UJSon.rstTrue();
		rst.put("params", savedParams);
		return rst;
	}

	private String loadConversationContext() {
		try {
			rv.addOrUpdateValue("ai_id", this.aiId, "bigint", 100);
			String sql = "select top 30 AIM_ROLE, AIM_MSG from AI_CHAT_MSG m "
					+ "inner join AI_CHAT c on m.AI_ID = c.AI_ID "
					+ "where c.AI_ID = @ai_id "
					+ "and isnull(m.AIM_SKIP_APPEND, 0) = 0 "
					+ "order by m.AIM_ID desc";
			DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
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
			return sb.toString();
		} catch (Exception e) {
			LOGGER.error("Failed to load conversation context for AI_ID={}", this.aiId, e);
			return "";
		}
	}

	private String buildExtractPrompt(List<ParamCheck> paramChecks) {
		StringBuilder sb = new StringBuilder();
		sb.append("你是一个旅游参数提取专家。请从对话内容中提取以下参数，返回 JSON 格式。\n\n");
		sb.append("需要提取的参数：\n");
		for (ParamCheck pc : paramChecks) {
			sb.append("- ").append(pc.getName()).append("（").append(pc.getDes()).append("）");
			if ("int".equals(pc.getType())) {
				sb.append("，类型：整数");
			} else if ("enum".equals(pc.getType())) {
				sb.append("，类型：枚举，可选值：").append(pc.getOptions());
			}
			if (pc.getDefaultValue() != null && !pc.getDefaultValue().isEmpty()) {
				sb.append("，默认值：").append(pc.getDefaultValue());
			}
			sb.append("\n");
		}
		sb.append("\n输出格式（仅返回 JSON，不要其他文字）：\n");
		sb.append("{");
		boolean first = true;
		for (ParamCheck pc : paramChecks) {
			if (!first) sb.append(",");
			sb.append("\"").append(pc.getName()).append("\": \"提取到的值或null\"");
			first = false;
		}
		sb.append("}\n\n");
		sb.append("规则：\n");
		sb.append("- 如果对话中未提及某参数，对应值设为 null\n");
		sb.append("- 出发城市：用户出发地，通常在\"从XX出发\"\"XX出发\"等表达中\n");
		sb.append("- 目的地城市：用户要去的城市，可能有多个，用逗号分隔\n");
		sb.append("- 行程天数：从\"X天\"\"X日游\"等表达中提取\n");
		sb.append("- 领队人数：从\"X领队\"\"X全陪\"\"X老师\"等表达中提取\n");
		sb.append("- 团员人数：游客人数，不含领队\n");
		sb.append("- 行程类型：根据行程内容判断最匹配的类型\n");
		return sb.toString();
	}

	private String callAiForExtraction(String fullPrompt) {
		try {
			IRequestData reqData = this.createRequestDataForApiCheck();
			reqData.addMessage(fullPrompt, "user");

			IRequestAI req = this.createRequestAI();
			String response = req.doPost(reqData);
			JSONObject json = req.extraceJson(response, true);
			return json.optString("content", "").trim();
		} catch (Exception e) {
			LOGGER.error("AI extraction call failed", e);
			return null;
		}
	}

	private JSONObject parseExtractedParams(String aiText) {
		int jsonStart = aiText.indexOf('{');
		int jsonEnd = aiText.lastIndexOf('}');
		if (jsonStart < 0 || jsonEnd <= jsonStart) {
			return null;
		}
		String jsonStr = aiText.substring(jsonStart, jsonEnd + 1);
		try {
			return new JSONObject(jsonStr);
		} catch (Exception e) {
			LOGGER.error("Failed to parse extracted params JSON: {}", jsonStr, e);
			return null;
		}
	}

	private JSONObject saveParamsToDatabase(JSONObject extractedParams, List<ParamCheck> paramChecks) {
		JSONObject saved = new JSONObject();
		long aimId = getLastAimId();

		DataConnection cnn = new DataConnection();
		cnn.setRequestValue(rv);
		cnn.setConfigName(dbConfigName);
		cnn.transBegin();

		try {
			rv.addOrUpdateValue("ai_id", this.aiId, "bigint", 100);
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
					rv.addOrUpdateValue("AI_ID", this.aiId, "long", 100);
					rv.addOrUpdateValue("AIM_ID", aimId, "long", 100);
					rv.addOrUpdateValue("AIP_NAME", name);
					rv.addOrUpdateValue("AIP_VAL", value);
					rv.addOrUpdateValue("AIP_TYPE", pc.getType());

					String insertSql = "INSERT INTO AI_CHAT_PARAMS (AI_ID, AIM_ID, AIP_NAME, AIP_VAL, AIP_TYPE) "
							+ "VALUES (@AI_ID, @AIM_ID, @AIP_NAME, @AIP_VAL, @AIP_TYPE)";
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

	private long getLastAimId() {
		try {
			rv.addOrUpdateValue("ai_id", this.aiId, "bigint", 100);
			String sql = "select isnull(max(AIM_ID), 0) as LAST_AIM_ID from AI_CHAT_MSG where AI_ID = @ai_id";
			DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
			if (tb.getCount() > 0) {
				return tb.getCell(0, "LAST_AIM_ID").toLong();
			}
		} catch (Exception e) {
			LOGGER.error("Failed to get last AIM_ID for AI_ID={}", this.aiId, e);
		}
		return 0;
	}

	public JSONObject checkProviderAndModel() {
		String sql = "select a.*,b.ap_status from AI_PROVIDER_MODEL a "
				+ " inner join AI_PROVIDER b on a.AP_CODE= b.AP_CODE  "
				+ " where a.apm_code=@ai_model and a.ap_code=@ai_provider";
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (tb.getCount() == 0) {
			JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_MODEL_NOT_EXIST, aiModel, aiProvider));
			return rst;
		}
		try {
			if (!"USED".equalsIgnoreCase(tb.getCell(0, "APM_STATUS").toString())) {
				JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_MODEL_OFFLINE_0, aiModel, aiProvider));
				return rst;
			}
			if (!"USED".equalsIgnoreCase(tb.getCell(0, "ap_status").toString())) {
				JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_MODEL_OFFLINE_1, aiModel, aiProvider));
				return rst;
			}
			String sql1 = "select APU_URL, APU_KEY from AI_PROVIDER_URL where APU_STATUS='USED' and ap_code=@ai_provider order by APU_MDATE desc";
			DTTable tb1 = DTTable.getJdbcTable(sql1, dbConfigName, rv);
			if (tb1.getCount() == 0) {
				JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_API_CONFIG_NOT_EXIST, aiProvider));
				return rst;
			}
			this.apiUrl = tb1.getCell(0, "APU_URL").toString();
			this.apiKey = tb1.getCell(0, "APU_KEY").toString();
		} catch (Exception e) {
			JSONObject rst = UJSon.rstFalse(getText(ErrorMessages.ERROR_GENERAL, e.getLocalizedMessage()));
			return rst;
		}
		return UJSon.rstTrue(getText(StatusMessages.SUCCESS_OK));
	}

	public void loadAction() {
		if (StringUtils.isBlank(step.getAction())) {
			return;
		}
		String actionName = step.getAction();
		Action action = mode.getAction(actionName);
		String actionClassName = action.getClassName();

		LOGGER.info(getText(LogMessages.ACTION_LOADING), actionName, actionClassName);

		UObjectValue uv = new UObjectValue();
		// IExport exporter = new pf2023.AiModeEnqJny();
		IAction stepAction = (IAction) uv.loadClass(actionClassName, null);
		this.stepAction = stepAction;
		this.actionName = actionName;
		this.action = action;
		this.actionClassName = actionClassName;
	}

	/**
	 * 获取或创建AI聊天记录
	 * 
	 * @param rv 请求值对象 AI_ID, AI_STEP_PREV
	 * @return 聊天记录
	 */
	public JSONObject getOrNewAiChat() {
		String sql = "select AI_ID, AI_CUR_STEP as AI_STEP_PREV, AI_UID from ai_chat where ai_uid=@request_id";
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (tb.getCount() > 0) {
			JSONObject chat = tb.getRow(0).toJson();
			chat.put("IS_NEW", false);
			if (!chat.optString("AI_STEP_PREV").equalsIgnoreCase(this.stepName)) {
				// 更新AI_STEP
				String sqlUpdate = "update AI_CHAT set AI_CUR_STEP=@AIM_STEP, AI_MDATE=@sys_date where AI_ID="
						+ chat.optLong("AI_ID");
				DataConnection.updateAndClose(sqlUpdate, dbConfigName, rv);
			}
			this.isNew = false;
			this.aiId = chat.optLong("AI_ID");
			this.aiStepPrev = chat.optString("AI_STEP_PREV");
			return chat;
		}

		StringBuilder sbIns = new StringBuilder();
		sbIns.append("INSERT INTO AI_CHAT (\n");
		sbIns.append("    AI_UID, AI_PID, AI_PROVIDER, AI_MODEL, AI_THINKING, AI_STREAM, AI_CUR_STEP\n");
		sbIns.append("  , AI_MODE, AI_MAX_TOKEN, AI_CDATE, AI_MDATE, ADM_ID, USR_ID, SUP_ID\n");
		sbIns.append(") VALUES(\n");
		sbIns.append("    @request_id, @p_ai_pid, @AI_PROVIDER, @AI_MODEL, " + (this.aiThinking ? 1 : 0) + ", "
				+ (this.aiStream ? 1 : 0) + ", @AIM_STEP\n");
		sbIns.append("  , @MODE, @AI_MAX_TOKEN, @sys_DATE, @sys_DATE, @g_ADM_ID, @G_WEB_USR_ID, @g_SUP_ID\n");
		sbIns.append(")");

		DataConnection.insertAndReturnAutoIdLong(sbIns.toString(), dbConfigName, rv);
		tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (tb.getCount() == 0) {
			LOGGER.error(getText(ErrorMessages.ERROR_AI_CHAT_CREATE_FAILED), "new AI_CHAT");
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_AI_CHAT_CREATE_FAILED) + " new AI_CHAT");
		}
		JSONObject chat = tb.getRow(0).toJson();
		chat.put("IS_NEW", true);

		this.isNew = true;
		this.aiId = chat.optLong("AI_ID");
		return chat;
	}

	/**
	 * 添加AI聊天消息
	 * 
	 * @param aiId AI聊天ID
	 * @param msg  消息内容
	 * @param role 角色
	 * @param rv   请求值对象
	 * @return 消息ID
	 */
	public long addAiChatMsg(String msg, String role, boolean isSkipAppend) {
		return addAiChatMsg(msg, role, isSkipAppend, false);
	}

	public long addAiChatMsg(String msg, String role, boolean isSkipAppend, boolean byUser) {
		rv.addOrUpdateValue("AIM_MSG", msg);
		rv.addOrUpdateValue("AIM_ROLE", role);
		if (!"assistant".equals(role)) {
			rv.addOrUpdateValue("AIM_TIME_END", new Date(), "date", 100);
		}
		rv.addOrUpdateValue("AIM_BY_USER", byUser ? 1 : 0);

		String sql = "INSERT INTO AI_CHAT_MSG( AI_ID, AIM_MSG, AIM_ROLE, AIM_BY_USER, AIM_TIME_BEGIN, AIM_TIME_END, AIM_STEP, AIM_ACTION, AIM_ACTION_CLASS, AIM_PROMPT_NAME, AIM_SKIP_APPEND)"
				+ " VALUES(@ai_id, @AIM_MSG, @AIM_ROLE, @AIM_BY_USER, @sys_date, @AIM_TIME_END, @AIM_STEP, @AIM_ACTION, @AIM_ACTION_CLASS, @AIM_PROMPT_NAME, "
				+ (isSkipAppend ? 1 : 0) + ")";

		long aimId = DataConnection.insertAndReturnAutoIdLong(sql, dbConfigName, rv);
		return aimId;
	}

	/**
	 * 更新AI聊天消息
	 * 
	 * @param aimId 消息ID
	 * @param msg   消息内容
	 * @param rv    请求值对象
	 */
	public void updateAiChatMsg(long aimId, String msg) {
		rv.addOrUpdateValue("AIM_MSG", msg);
		rv.addOrUpdateValue("AIM_TIME_END", new Date(), "date", 100);

		String sql = "update AI_CHAT_MSG set AIM_MSG = @AIM_MSG, AIM_TIME_END= @AIM_TIME_END where AIM_ID = " + aimId;

		DataConnection.updateAndClose(sql, dbConfigName, rv);
	}

	/**
	 * 更新AI聊天消息的Token使用情况
	 * 
	 * @param aimId 消息ID
	 * @param usage Token使用情况JSON对象，包含total_tokens, completion_tokens,
	 *              prompt_tokens字段
	 */
	public void updateAiChatMsgTokens(long aimId, JSONObject usage) {
		long totalTokens = usage.optLong("total_tokens");
		long completionTokens = usage.optLong("completion_tokens");
		long promptTokens = usage.optLong("prompt_tokens");

		this.updateAiChatMsgTokens(aimId, totalTokens, completionTokens, promptTokens);
	}

	/**
	 * 更新AI聊天消息的Token使用情况
	 * 
	 * @param aimId            消息ID
	 * @param totalTokens      总Token数
	 * @param completionTokens 完成Token数
	 * @param promptTokens     提示词Token数
	 */
	public void updateAiChatMsgTokens(long aimId, long totalTokens, long completionTokens, long promptTokens) {
		String sql = "update AI_CHAT_MSG set AIM_TOTAL_TOKENS = " + totalTokens + ", AIM_COMPLETION_TOKENS= "
				+ completionTokens + ", AIM_PROMPT_TOKENS = " + promptTokens + " where AIM_ID = " + aimId;

		DataConnection.updateAndClose(sql, dbConfigName, rv);
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public String getPrompt() {
		return prompt;
	}

	/**
	 * 处理 innerCall 步骤：内部调用，输出不返回给用户
	 * 使用现有的 apisCheck 机制调用 API（通过 UNet），AI 响应直接返回
	 * @return AI 返回的内容，如果无 innerCall 步骤或失败返回 null
	 */
	public String processInnerCallStep(Step innerStep) {
		if (innerStep == null || innerStep.getPrompts() == null || innerStep.getPrompts().isEmpty()) {
			return null;
		}
		// 保存当前步骤、输出事件和 AI_ID
		Step prevStep = this.step;
		String prevStepName = this.stepName;
		IOutEvents prevOutEvents = this.outEvents;
		long parentAiId = this.aiId; // 记录父级 AI_ID

		try {
			// 设置 innerCall 步骤
			this.step = innerStep;
			this.stepName = innerStep.getName();
			this.rv.addOrUpdateValue("AIM_STEP", this.stepName);

			// 设置父级 AI_PID，让子 chat 关联到父 chat
			this.rv.addOrUpdateValue("p_ai_pid", parentAiId);

			// 捕获输出（不写到 response）
			final StringBuilder capturedOutput = new StringBuilder();
			this.outEvents = new com.gdxsoft.ai.request.IOutEvents() {
				private int messageCount = 0;
				@Override public void outEvent(String msg, java.io.PrintWriter w) { capturedOutput.append(msg).append("\n"); }
				@Override public int getMessageCount() { return messageCount; }
				@Override public void setMessageCount(int c) { this.messageCount = c; }
				@Override public String getLine() { return null; }
				@Override public void setLine(String l) {}
				@Override public org.json.JSONObject getContenJson() { return null; }
				@Override public void setContenJson(org.json.JSONObject j) {}
				@Override public String getName() { return null; }
				@Override public void setName(String n) {}
				@Override public String getLang() { return "zhcn"; }
				@Override public void setLang(String l) {}
			};

			// 创建新的 AI 会话记录 innerCall（AI_PID 会自动关联到父 chat）
			getOrNewAiChat();

			// 准备请求数据（会调用 apiToolsChecks 和 appendPrompts）
			boolean settingStream = this.isAiStream();
			// innerCall 步骤默认不使用流式输出，避免干扰父级响应
			this.setAiStream(false);
			IRequestData reqData = createRequestData();
			this.setAiStream(settingStream);
			
			appendPrompts(reqData);
			reqData.userMessage(this.prompt);
			addAiChatMsg(this.prompt, "user", false, true);

			// 调用 AI
			IRequestAI req = createRequestAI();
			
			String fullText = req.doPost(reqData);
			JSONObject json = req.extraceJson(fullText, true);
			String content = json != null && json.has("content") ? json.getString("content") : fullText;

			// 记录 AI 响应
			addAiChatMsg(content, "assistant", true);

			return content;
		} catch (Exception e) {
			LOGGER.error("processInnerCallStep failed for step: {}", innerStep != null ? innerStep.getName() : "null", e);
			return null;
		} finally {
			// 恢复原步骤和输出事件
			this.step = prevStep;
			this.stepName = prevStepName;
			this.rv.addOrUpdateValue("AIM_STEP", this.stepName);
			this.outEvents = prevOutEvents;
		}
	}

	public String getResolvedPrompt() {
		if (prompt == null) return null;
		if (step == null || !step.isMultiOnlyUserMsg()) {
			return prompt;
		}
		// 通过 AI_PID 找到父 chat，然后提取所有相关 chat 的用户消息
		try {
			rv.addOrUpdateValue("ai_id", this.aiId, "bigint", 100);
			String sql = "select AI_PID from AI_CHAT where AI_ID=@ai_id";
			DTTable pidTb = DTTable.getJdbcTable(sql, dbConfigName, rv);
			if (pidTb.getCount() == 0) return prompt;
			Double pidObj = pidTb.getCell(0, "AI_PID").toDouble();
			if (pidObj == null) return prompt;
			long parentAiId = pidObj.longValue();

			if (parentAiId <= 0) return prompt;

			// 提取父 chat 和所有子 chat 中 AIM_BY_USER=1 的用户消息
			rv.addOrUpdateValue("parent_ai_id", parentAiId, "bigint", 100);
			String msgSql = "select AIM_MSG from AI_CHAT_MSG m "
				+ "where m.AIM_BY_USER = 1 and m.AIM_ROLE = 'user' "
				+ "and m.AI_ID in ("
				+ "  select AI_ID from AI_CHAT where AI_PID = @parent_ai_id"
				+ ") order by m.AIM_ID";
			DTTable msgTb = DTTable.getJdbcTable(msgSql, dbConfigName, rv);
			if (msgTb.getCount() == 0) return prompt;

			StringBuilder sb = new StringBuilder();
			sb.append("【历史对话】\n");
			String lastMsg = null;
			int roundNum = 0;
			for (int i = 0; i < msgTb.getCount(); i++) {
				String msg = msgTb.getCell(i, "AIM_MSG").toString();
				// 从消息中提取【当前输入】部分（去除嵌套的历史对话）
				// 使用 lastIndexOf 找到最后一个【当前输入】，避免嵌套问题
				String actualInput = msg;
				int idx = msg.lastIndexOf("【当前输入】");
				if (idx >= 0) {
					actualInput = msg.substring(idx + 6).trim();
				}
				// 去重
				if (actualInput.equals(lastMsg)) {
					continue;
				}
				roundNum++;
				sb.append("第").append(roundNum).append("轮用户输入：").append(actualInput).append("\n");
				lastMsg = actualInput;
			}
			sb.append("【当前输入】\n").append(prompt);
			return sb.toString();
		} catch (Exception e) {
			LOGGER.error("getResolvedPrompt failed for aiId={}", this.aiId, e);
			return prompt;
		}
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public String getApiUrl() {
		return apiUrl;
	}

	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public boolean isNew() {
		return isNew;
	}

	public long getAiId() {
		return aiId;
	}

	public String getAiStepPrev() {
		return aiStepPrev;
	}

	public void setAiStepPrev(String aiStepPrev) {
		this.aiStepPrev = aiStepPrev;
	}

	public RequestValue getRv() {
		return rv;
	}

	public String getModeName() {
		return modeName;
	}

	public Mode getMode() {
		return mode;
	}

	public String getStepName() {
		return stepName;
	}

	public Step getStep() {
		return step;
	}

	public IAction getStepAction() {
		return stepAction;
	}

	public String getActionName() {
		return actionName;
	}

	public Action getAction() {
		return action;
	}

	public String getActionClassName() {
		return actionClassName;
	}

	public String getAiProvider() {
		return aiProvider;
	}

	public void setAiProvider(String aiProvider) {
		this.aiProvider = aiProvider;
	}

	/**
	 * 获取国际化文本
	 *
	 * @param key  文本键（使用ChatManagerI18nConstants中定义的常量）
	 * @param args 格式化参数
	 * @return 根据语言设置返回对应文本
	 */
	private String getText(String key, Object... args) {
		return ChatManagerI18nConstants.getText(key, this.en, args);
	}

	/**
	 * 直接调用 AI 并返回结果（非流式、同步调用）。
	 * <p>
	 * 利用当前 ChatManagerBase 的上下文（provider、model、apiUrl、apiKey、历史消息等）
	 * 自动加载历史对话、保存用户消息和 AI 响应到数据库，支持多轮对话。
	 *
	 * <h3>使用示例</h3>
	 * <pre>
	 * ChatManagerBase manager = new ChatManagerBase(rv, dbConfig, writer);
	 * manager.checkParams();  // 初始化 provider/model/apiUrl/apiKey/aiId
	 *
	 * // 第一轮
	 * JSONObject result = manager.callAI("你好");
	 * String content = result.getString("content");
	 *
	 * // 第二轮（自动附带第一轮的历史消息）
	 * manager.setPrompt("请继续");
	 * JSONObject result2 = manager.callAI("请继续");
	 * </pre>
	 *
	 * @param prompt 用户输入内容
	 * @return 包含 content（回复内容）和 usage（Token 用量）的 JSONObject
	 *         失败时返回 {RST:false, error:错误信息}
	 */
	public JSONObject callAI(String prompt) {
		return callAI(prompt, null);
	}

	/**
	 * 直接调用 AI 并返回结果（非流式、同步调用）。
	 * <p>
	 * 支持传入工具列表，自动加载历史消息并保存本轮对话到数据库。
	 *
	 * @param prompt 用户输入内容
	 * @param tools  工具列表（可选，传 null 表示不使用工具）
	 * @return 包含 content 和 usage 的 JSONObject
	 */
	public JSONObject callAI(String prompt, com.gdxsoft.ai.request.AiTool... tools) {
		try {
			// 创建请求实例
			IRequestAI req = createRequestAI();
			if (req == null) {
				return UJSon.rstFalse(getText(ErrorMessages.ERROR_NO_AI_PROVIDER));
			}

			// 创建请求数据
			boolean prevStream = this.aiStream;
			this.aiStream = false; // 非流式
			IRequestData reqData = createRequestData();
			this.aiStream = prevStream;

			// 加载历史消息（多轮对话支持）
			if (!isNew) {
				Map<String, Boolean> existsMessages = new HashMap<>();
				appendPreviousMessages(reqData, existsMessages);
			}

			// 添加当前用户消息
			reqData.addMessage(prompt, "user");

			// 添加工具
			if (tools != null && tools.length > 0) {
				reqData.tools(tools);
			}

			// 保存用户消息到数据库
			long userMsgId = addAiChatMsg(prompt, "user", false, true);

			// 创建 AI 响应占位消息
			long aimId = addAiChatMsg("", "assistant", true);

			// 调用 AI
			String fullText = req.doPost(reqData);
			JSONObject json = req.extraceJson(fullText, true);

			String content = json != null && json.optBoolean("RST", false) && json.has("content")
					? json.getString("content")
					: fullText;

			// 更新 AI 响应到数据库
			updateAiChatMsg(aimId, content);

			// 更新 Token 使用情况
			JSONObject usage = req.getTokensUsage();
			if (usage != null) {
				updateAiChatMsgTokens(aimId, usage);
			}

			// 构建返回结果
			JSONObject result = UJSon.rstTrue();
			result.put("content", content);
			if (usage != null) {
				result.put("usage", usage);
			}
			result.put("aim_id", aimId);

			return result;
		} catch (Exception e) {
			LOGGER.error("callAI failed for prompt: {}", prompt, e);
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_GENERAL, e.getMessage()));
		}
	}

	/**
	 * 直接调用 AI 并返回结果（非流式、同步调用）。
	 * <p>
	 * 最简用法：传入 provider、model、apiUrl、apiKey 和 prompt 即可返回 AI 响应。
	 * 无需初始化 ChatManagerBase，无需数据库配置，无需 RequestValue。
	 * 此为静态工具方法，不支持多轮对话（每次调用为独立会话）。
	 *
	 * <h3>使用示例</h3>
	 * <pre>
	 * // 一次性调用（无历史上下文）
	 * JSONObject result = ChatManagerBase.callAI("openai", "gpt-4o",
	 *     "https://api.openai.com/v1/chat/completions", "sk-xxx...", "你好");
	 * String content = result.getString("content");
	 *
	 * // 带系统提示词
	 * JSONObject result = ChatManagerBase.callAI("qwen", "qwen-max",
	 *     apiUrl, apiKey, "请翻译以下内容：Hello World",
	 *     "你是一个专业的翻译助手");
	 *
	 * // 查看 Token 使用情况
	 * JSONObject usage = result.optJSONObject("usage");
	 * int totalTokens = usage.optInt("total_tokens");
	 * </pre>
	 *
	 * @param provider 提供商名称（openai, qwen, gemini, anthropic, deepseek 等）
	 * @param model    模型名称（gpt-4o, qwen-max, gemini-2.5-flash 等）
	 * @param apiUrl   AI API 地址
	 * @param apiKey   API 密钥
	 * @param prompt   用户输入内容
	 * @return 包含 content（回复内容）和 usage（Token 用量）的 JSONObject
	 *         失败时返回 {RST:false, error:错误信息}
	 */
	public static JSONObject callAI(String provider, String model, String apiUrl, String apiKey, String prompt) {
		return callAI(provider, model, apiUrl, apiKey, prompt, null);
	}

	/**
	 * 直接调用 AI 并返回结果（非流式、同步调用）。
	 * <p>
	 * 支持传入系统提示词（system prompt）。
	 *
	 * @param provider  提供商名称
	 * @param model     模型名称
	 * @param apiUrl    AI API 地址
	 * @param apiKey    API 密钥
	 * @param prompt    用户输入内容
	 * @param systemMsg 系统提示词（可选，传 null 表示无系统提示）
	 * @return 包含 content 和 usage 的 JSONObject
	 */
	public static JSONObject callAI(String provider, String model, String apiUrl, String apiKey,
			String prompt, String systemMsg) {
		return callAI(provider, model, apiUrl, apiKey, prompt, systemMsg, null);
	}

	/**
	 * 直接调用 AI 并返回结果（非流式、同步调用）。
	 * <p>
	 * 支持系统提示词和工具列表。
	 *
	 * @param provider  提供商名称
	 * @param model     模型名称
	 * @param apiUrl    AI API 地址
	 * @param apiKey    API 密钥
	 * @param prompt    用户输入内容
	 * @param systemMsg 系统提示词（可选）
	 * @param tools     工具列表（可选，传 null 表示不使用工具）
	 * @return 包含 content 和 usage 的 JSONObject
	 */
	public static JSONObject callAI(String provider, String model, String apiUrl, String apiKey,
			String prompt, String systemMsg, com.gdxsoft.ai.request.AiTool... tools) {
		try {
			IRequestAI req = RequestAIFactory.createRequestAI(provider);
			req.initUrlAndKey(apiUrl, apiKey);

			IRequestData reqData = RequestDataFactory.createRequestData(provider);
			reqData.model(model).stream(false);

			if (systemMsg != null && !systemMsg.isEmpty()) {
				reqData.addMessage(systemMsg, "system");
			}
			reqData.addMessage(prompt, "user");

			if (tools != null && tools.length > 0) {
				reqData.tools(tools);
			}

			String fullText = req.doPost(reqData);
			JSONObject json = req.extraceJson(fullText, true);

			JSONObject result = new JSONObject();
			if (json != null && json.optBoolean("RST", false) && json.has("content")) {
				result.put("content", json.getString("content"));
				UJSon.rstSetTrue(result, null);
			} else {
				result.put("content", fullText);
				UJSon.rstSetTrue(result, null);
			}

			// 附加 Token 使用情况
			JSONObject usage = req.getTokensUsage();
			if (usage != null) {
				result.put("usage", usage);
			}

			return result;
		} catch (Exception e) {
			LOGGER.error("callAI failed: provider={}, model={}", provider, model, e);
			JSONObject error = UJSon.rstFalse(e.getMessage());
			error.put("provider", provider);
			error.put("model", model);
			return error;
		}
	}

	/**
	 * 脱敏 API Key，防止日志泄露。
	 * <p>
	 * 格式：前4位 + **** + 后4位，长度不足8时返回 ****。
	 *
	 * @param key 原始 API Key
	 * @return 脱敏后的字符串
	 */
	private String maskApiKey(String key) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		if (key.length() <= 8) {
			return "****";
		}
		return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
	}

}
