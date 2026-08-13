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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import com.gdxsoft.ai.export.ActionBase;
import com.gdxsoft.ai.export.IAction;
import com.gdxsoft.ai.modes.*;
import com.gdxsoft.ai.request.AiTool;
import com.gdxsoft.ai.request.DefaultOutEvents;
import com.gdxsoft.ai.request.IOutEvents;
import com.gdxsoft.ai.request.IRequestAI;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.RequestAIFactory;
import com.gdxsoft.ai.request.RequestDataFactory;
import com.gdxsoft.easyweb.data.DTTable;
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
	 * <p>
	 * 解析顺序（<b>文件优先</b>）：
	 * <ol>
	 * <li>如果 {@code path} 指向一个存在的<b>文件系统</b>文件（绝对路径或 JVM 工作目录下的相对路径）， 直接读取该文件 — 用于
	 * IDE 源码布局下的 {@code src/main/resources/...} 直读， 以及运维侧对打包配置的临时覆盖。</li>
	 * <li>否则按<b>类路径</b>资源查找 — 适用于打包到 JAR 内的 XML
	 * （{@code classpath:ai_chat_room_group.xml} 或
	 * {@code ai_chat_room_group.xml}）。</li>
	 * </ol>
	 * 也就是说：当文件系统和类路径同时存在同名资源时，<b>文件优先</b>。
	 *
	 * @param path        配置文件路径（绝对/相对文件系统路径，或类路径相对名）
	 * @param classLoader 用于类路径资源查找的 ClassLoader
	 * @throws Exception 文件与类路径都找不到时抛出
	 */
	public static void loadModes(String path, final ClassLoader classLoader) throws Exception {
		String xml = null;
		// 1) 文件优先：path 指向一个真实存在的常规文件时直接读
		if (path != null && Files.isRegularFile(Paths.get(path))) {
			xml = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
			LOGGER.info("loadModes: 从文件系统读取 '{}'", path);
		}
		// 2) 回退到类路径资源（保持原有打包到 JAR 时的行为）
		if (xml == null) {
			xml = IOUtils.resourceToString(path, StandardCharsets.UTF_8, classLoader);
		}
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

	/** 关联引用类型 */
	private String aiRef;

	/** 关联引用ID */
	private String aiRefId;

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
	/** 数据库操作代理 */
	private ChatManagerDb db;
	/** AI提供商 */
	private String aiProvider;
	/** AI模型名称 */
	private String aiModel;
	/** 是否开启思考模式 */
	private boolean aiThinking;
	/**
	 * 思考预算 token 数。0 表示使用 Mode 的默认或 provider 自身默认；
	 * &gt;0 时由各 provider 自行转换为对应字段（如 Anthropic budget_tokens、
	 * Gemini thinkingConfig.thinkingBudget、Qwen thinking.budget、OpenRouter
	 * reasoning.max_tokens、OpenAI o-series reasoning_effort），不支持的 model 忽略。
	 */
	private int aiThinkingBudget;

	/** 是否开启流式输出 */
	private boolean aiStream;

	/** 是否使用英文 */
	private boolean en;

	/** 交互次数 **/
	private short aimNumberOfInteractions = 0;

	/** API工具检查结果缓存 (promptName -> apiResult)，避免修改共享Prompt对象 */
	private Map<String, String> apiCheckResults = new HashMap<>();

	/** mode=auto 路由结果详情（JSON 字符串），非 null 表示本轮发生了自动路由 */
	private String routeInfo;
	/** mode=auto 路由分类调用的 token 用量 */
	private JSONObject routeUsage;

	private String apiOwnerId = null;

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
	 * 获取关联引用类型
	 *
	 * @return 关联引用类型
	 */
	public String getAiRef() {
		return aiRef;
	}

	/**
	 * 设置关联引用类型
	 *
	 * @param aiRef 关联引用类型
	 */
	public void setAiRef(String aiRef) {
		this.aiRef = aiRef;
	}

	/**
	 * 获取关联引用ID
	 *
	 * @return 关联引用ID
	 */
	public String getAiRefId() {
		return aiRefId;
	}

	/**
	 * 设置关联引用ID
	 *
	 * @param aiRefId 关联引用ID
	 */
	public void setAiRefId(String aiRefId) {
		this.aiRefId = aiRefId;
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
	 * 获取思考预算 token 数。
	 *
	 * @return 思考预算 token 数，0 表示未设置或使用 mode/provider 默认
	 */
	public int getAiThinkingBudget() {
		return aiThinkingBudget;
	}

	/**
	 * 设置思考预算 token 数。
	 *
	 * @param aiThinkingBudget 思考预算 token 数，&lt;=0 视为清除
	 */
	public void setAiThinkingBudget(int aiThinkingBudget) {
		this.aiThinkingBudget = aiThinkingBudget > 0 ? aiThinkingBudget : 0;
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
		this.db = new ChatManagerDb(rv, dbConfigName);
		// 从请求参数中获取语言设置
		this.en = rv.isEn();
	}

	/**
	 * 构造函数
	 * 
	 * @param rv           请求参数值对象
	 * @param dbConfigName 数据库配置名称
	 * @param apiOwnerId   apiKey的拥有者
	 * @param writer
	 */
	public ChatManagerBase(RequestValue rv, String dbConfigName, String apiOwnerId, PrintWriter writer) {
		this.rv = rv;
		this.writer = writer;
		this.dbConfigName = dbConfigName;
		this.db = new ChatManagerDb(rv, dbConfigName);
		// 从请求参数中获取语言设置
		this.en = rv.isEn();
		this.apiOwnerId = apiOwnerId;
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
		int thinkingBudget = this.aiThinkingBudget > 0 ? this.aiThinkingBudget : mode.getThinkingBudget();
		if (thinkingBudget > 0) {
			reqData.thinkingBudget(thinkingBudget);
		}
		if (mode.getTemperature() != 0) {
			reqData.temperature(mode.getTemperature());
		}
		if (mode.getTopP() != 0) {
			reqData.topP(mode.getTopP());
		}
		if (mode.isEnableSearch()) {
			// 联网搜索（如 qwen enable_search），provider 不支持时忽略该字段
			reqData.getParameters().put("enable_search", true);
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
	 * mode=auto 自动路由：根据用户输入 prompt，用一次轻量 LLM 分类调用（非流式、json_object）
	 * 从 {@link RouterMode} 声明的候选 mode 中选出最合适的一个，并替换 this.modeName / this.mode。
	 * <p>
	 * 分类失败或返回无效 mode 时的兜底顺序：
	 * <ol>
	 * <li>routerMode 的 {@code default} 指定的默认 mode；</li>
	 * <li>已有会话沿用数据库中记录的 AI_MODE；</li>
	 * <li>新会话返回错误：有 {@code <reminder>} 提醒词则用它，否则用 i18n 文案。</li>
	 * </ol>
	 *
	 * @param routerMode 路由声明（候选 mode 集合 + 默认 mode）
	 * @return 成功返回 null（this.mode 已设置），失败返回 RST=false 的 JSONObject
	 * @throws Exception 调用 AI 接口失败时抛出
	 */
	private JSONObject routeModeByPrompt(RouterMode routerMode) throws Exception {
		if (StringUtils.isBlank(this.prompt)) {
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_MODE_ROUTE_FAILED) + "(prompt is empty)");
		}
		List<String> routeNames = routerMode.getRoutes();
		if (routeNames == null || routeNames.isEmpty()) {
			return UJSon.rstFalse(
					getText(ErrorMessages.ERROR_MODE_ROUTE_FAILED) + "(no routes in routerMode: " + routerMode.getName() + ")");
		}
		// 候选：routerMode 显式声明的 mode（不存在的给出 WARN 并跳过）
		List<Mode> candidates = new ArrayList<>();
		for (String routeName : routeNames) {
			Mode m = Modes.getMode(routeName);
			if (m == null) {
				LOGGER.warn("routerMode {} 声明的候选 mode 不存在：{}", routerMode.getName(), routeName);
				continue;
			}
			candidates.add(m);
		}
		if (candidates.isEmpty()) {
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_MODE_ROUTE_FAILED)
					+ "(no valid routes in routerMode: " + routerMode.getName() + ")");
		}

		StringBuilder sb = new StringBuilder();
		sb.append(getText(ChatManagerI18nConstants.ToolMessages.ROUTE_INSTRUCTION)).append("\n");
		sb.append(getText(ChatManagerI18nConstants.ToolMessages.ROUTE_CANDIDATES)).append("\n");
		for (Mode m : candidates) {
			// description 为空时用 name 兜底，保证分类仍有语义
			String desc = StringUtils.isBlank(m.getDescription()) ? m.getName() : m.getDescription();
			sb.append("- ").append(m.getName()).append(": ").append(desc).append("\n");
		}
		sb.append(getText(ChatManagerI18nConstants.ToolMessages.USER_INPUT)).append(this.prompt);

		IRequestData reqData = this.createRequestDataForApiCheck();
		reqData.addMessage(sb.toString(), "user");
		IRequestAI req = this.createRequestAI();
		String fullText = req.doPost(reqData);
		JSONObject json = req.extraceJson(fullText, true);
		String content = json.optString("content").trim();
		// 分类调用的 token 用量（无论最终命中哪种兜底都记录）
		this.routeUsage = req.getTokensUsage();

		String routedName = extractRoutedModeName(content);
		Mode routed = routedName == null ? null : Modes.getMode(routedName);
		if (routed == null && routedName != null) {
			// 容忍大小写差异
			for (Mode m : candidates) {
				if (m.getName().equalsIgnoreCase(routedName)) {
					routed = m;
					break;
				}
			}
		}

		if (routed == null) {
			// 兜底 1：routerMode.default 指定的默认 mode
			String defName = routerMode.getDefaultMode();
			Mode defMode = StringUtils.isBlank(defName) ? null : Modes.getMode(defName);
			if (defMode != null) {
				LOGGER.warn("mode route failed, use default mode={}, content: {}", defName, content);
				this.modeName = defMode.getName();
				this.mode = defMode;
				this.recordRouteInfo(defMode.getName(), "default", content);
				return null;
			}
			// 兜底 2：已有会话沿用数据库中记录的 AI_MODE，保证多轮对话不中断
			JSONObject chat = db.queryChatByRequestId(this.requestId);
			String curMode = chat == null ? null : chat.optString("AI_MODE");
			Mode fallback = StringUtils.isBlank(curMode) ? null : Modes.getMode(curMode);
			if (fallback == null) {
				LOGGER.warn("mode route failed, content: {}", content);
				// 有自定义提醒词则优先用它作为用户可见提示，否则回退到 i18n 文案
				String reminder = resolveReminder(routerMode);
				if (!StringUtils.isBlank(reminder)) {
					return UJSon.rstFalse(reminder);
				}
				return UJSon.rstFalse(getText(ErrorMessages.ERROR_MODE_ROUTE_FAILED) + content);
			}
			LOGGER.warn("mode route failed, fallback to current mode={}, content: {}", curMode, content);
			this.modeName = fallback.getName();
			this.mode = fallback;
			this.recordRouteInfo(fallback.getName(), "db", content);
			return null;
		}

		this.modeName = routed.getName();
		this.mode = routed;
		this.recordRouteInfo(routed.getName(), "llm", content);
		LOGGER.info("mode routed to mode: {}", routed.getName());
		return null;
	}

	/**
	 * 解析 {@code <reminder>} 提醒词：支持 {@code api}/{@code tool} 属性调用共享 API
	 * 并把结果作为提醒词；最终内容统一做 {@code @para} 占位符替换。
	 *
	 * @param routerMode 路由声明（含 reminder 文本、reminder 引用的共享 API）
	 * @return 解析后的提醒词文本，无 reminder 时返回空串
	 * @throws Exception API 调用失败时抛出
	 */
	private String resolveReminder(RouterMode routerMode) throws Exception {
		String content = null;
		String apiName = routerMode.getReminderApi();
		if (!StringUtils.isBlank(apiName)) {
			Api api = routerMode.getApi(apiName);
			if (api == null) {
				LOGGER.warn("routerMode {} 的 reminder 引用的共享 API 不存在：{}", routerMode.getName(), apiName);
			} else if (api instanceof Tool && ((Tool) api).isUseMode()) {
				// reminder 仅支持 URL / 本地程序调用，不支持 useMode 子 mode 调用
				LOGGER.warn("routerMode {} 的 reminder 引用的是 useMode 工具，已忽略：{}", routerMode.getName(), apiName);
			} else {
				Map<String, String> refHeaders = new HashMap<>();
				if (this.rv.getHttpHeaders() != null) {
					refHeaders.putAll(this.rv.getHttpHeaders());
				}
				Prompt apiPrompt = new Prompt();
				apiPrompt.setApi(apiName);
				content = Mode.executeApi(api, this.rv, refHeaders, apiPrompt);
			}
		}
		if (content == null) {
			content = routerMode.getReminder();
		}
		if (content == null) {
			return "";
		}
		// reminder 支持 @para 占位符替换（与 prompt 内容一致）
		return this.rv.replaceParameters(content);
	}

	/**
	 * 暂存路由详情（待 chat 建立后落库为 mode_route 隐藏消息；此时新会话还没有 aiId）。
	 */
	private void recordRouteInfo(String modeName, String source, String content) {
		JSONObject info = UJSon.rstTrue("");
		info.put("route_mode", modeName);
		info.put("route_source", source);
		info.put("route_llm_content", content);
		this.routeInfo = info.toString();
	}

	/**
	 * 从 LLM 分类返回内容中解析路由的 mode 名称。
	 * <p>
	 * 期望格式 {@code {"mode":"名称"}}，容忍 markdown 代码块包裹。
	 *
	 * @param content LLM 返回内容
	 * @return mode 名称，解析失败返回 null
	 */
	static String extractRoutedModeName(String content) {
		if (content == null) {
			return null;
		}
		String c = content.trim();
		// 去掉 markdown 代码块包裹（```json ... ```）
		if (c.startsWith("```")) {
			int first = c.indexOf('\n');
			int last = c.lastIndexOf("```");
			if (first > 0 && last > first) {
				c = c.substring(first + 1, last).trim();
			}
		}
		try {
			JSONObject obj = new JSONObject(c);
			String name = obj.optString("mode", "").trim();
			return name.isEmpty() ? null : name;
		} catch (Exception e) {
			return null;
		}
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
		DTTable tbAgent = db.queryAgentMessages(this.aiId);
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

		// 将 action 的 aiProvider / aiModel 注入 rv
		if (action.getAiProvider() != null) rv.addOrUpdateValue("ai_provider", action.getAiProvider());
		if (action.getAiModel() != null) rv.addOrUpdateValue("ai_model", action.getAiModel());
		// 将 dbConfigName 注入 rv，供 Action 中的子模块（图片/视频生成等）自动记录日志
		rv.addOrUpdateValue("ewa_db_config", this.dbConfigName);

		// 确保 ActionBase 能输出 SSE 事件（outEvents 是延迟初始化的）
		if (stepAction instanceof ActionBase) {
			((ActionBase) stepAction).setOutEvents(this.outEvents);
			((ActionBase) stepAction).setWriter(this.writer);
		}

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

		}
		Map<String, String> refHeaders;
		if (this.rv.getHttpHeaders() != null) {// websocket的header是空的
			refHeaders = this.rv.getHttpHeaders();
			for (String name : refHeaders.keySet()) {
				String value = refHeaders.get(name);
				refHeaders.put(name, value);
			}
		} else {
			refHeaders = new HashMap<>();
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

		// 确保 system 消息在最前面（缓存过期重建时 system 可能被追加到 user/assistant 之后）
		ensureSystemMessagesFirst(reqData);
	}

	/**
	 * 将 messages 中 role=system 的消息移到数组最前面，保持其余消息的相对顺序。
	 * <p>
	 * 当多轮对话缓存过期触发提示词重建时，旧的 system 消息被标记跳过，
	 * 但 user/assistant 消息保留，导致新的 system prompt 被追加到末尾。
	 * 此方法确保发送给 AI 的消息顺序始终为 system → user → assistant → ...
	 */
	private void ensureSystemMessagesFirst(IRequestData reqData) {
		JSONArray messages = reqData.getMessages();
		if (messages == null || messages.length() <= 1) {
			return;
		}
		// 检查是否需要重排：如果第一条已经是 system 则跳过
		boolean needsReorder = false;
		for (int i = 0; i < messages.length(); i++) {
			JSONObject msg = messages.optJSONObject(i);
			if (msg != null && "system".equalsIgnoreCase(msg.optString("role"))) {
				if (i > 0) {
					needsReorder = true;
				}
				break;
			}
		}
		if (!needsReorder) {
			return;
		}

		// 分离 system 和非 system 消息
		List<Object> systemMsgs = new ArrayList<>();
		List<Object> otherMsgs = new ArrayList<>();
		for (int i = 0; i < messages.length(); i++) {
			JSONObject msg = messages.optJSONObject(i);
			if (msg != null && "system".equalsIgnoreCase(msg.optString("role"))) {
				systemMsgs.add(msg);
			} else {
				otherMsgs.add(msg);
			}
		}

		// 重建 JSONArray：system 在前，其余保持原序
		messages.clear();
		for (Object msg : systemMsgs) {
			messages.put(msg);
		}
		for (Object msg : otherMsgs) {
			messages.put(msg);
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

			// 存储结果到独立map，避免修改共享的Prompt对象（共享Prompt会被多次调用）
			this.apiCheckResults.put(p.getName(), apiResult);
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
		// 自动附加本 mode 所有带调用说明（usage）的工具清单，无需在 prompt 中手写
		String apisUsage = mode.getApisUsage();
		if (!StringUtils.isBlank(apisUsage)) {
			promptContent += "\n\n" + getText(ChatManagerI18nConstants.ToolMessages.AVAILABLE_TOOLS) + "\n" + apisUsage;
		}
		String nowStr = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"));
		promptContent += "\n\n" + getText(ChatManagerI18nConstants.ToolMessages.CURRENT_TIME) + nowStr + "\n"
				+ getText(ChatManagerI18nConstants.ToolMessages.USER_INPUT) + this.prompt;
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

		// 记录 Token 使用情况（非流式响应的 usage 由 extraceJson 解析到 req 中）
		JSONObject usage = req.getTokensUsage();
		if (usage != null) {
			this.updateAiChatMsgTokens(aimId, usage);
		}
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
		// useMode 工具：进程内调用子 mode，不走 HTTP / 本地程序
		Api apiDef = mode.getApi(toolName);
		if (apiDef instanceof Tool && ((Tool) apiDef).isUseMode()) {
			return executeUseModeCall(toolName, (Tool) apiDef, args, refHeaders);
		}

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

		// 根据 debugOutput 开关决定是否输出调试信息
		if (mode.isDebugOutput()) {
			JSONObject msg = UJSon.rstTrue("");
			msg.put("reasoning_content", "调用API: " + toolName + ", " + args.toString() + "\n\n");
			this.outEvent(msg.toString());
		}

		mode.createStepPromptByApi(apiPrompt, rv, refHeaders);
		String calledContent = apiPrompt.getContent();
		String apiCallCurl = apiPrompt.getApiCurl();
		// 添加一条 curl 命令记录
		this.addAiChatMsg(apiCallCurl, "api_call_curl", true);
		// 记录api调用的结果
		this.addAiChatMsg(calledContent, "api_call_content", true);

		var api = mode.getApi(toolName);
		String apiContent = api.getDescription() + "数据:\n" + calledContent;

		return apiContent;
	}

	/**
	 * 执行 useMode 工具调用：进程内调用子 mode（非流式、单次请求）。
	 * <p>
	 * 子 mode 各 step 的 prompt（CDATA、sqlRef、api 等数据源）按定义拼接为消息；
	 * LLM 给出的 args 注入 RequestValue（供 prompt/api/sql 的 @占位符 使用），
	 * 同时作为子 mode 的用户输入——单参数时取其值，多参数时传 JSON 字符串。
	 * 采样参数（temperature/topP/thinking/responseFormat）取子 mode 的定义，
	 * provider/model/key 沿用当前会话。不执行子 mode 的 action。
	 * <p>
	 * 调用过程记录到一个新建的子 chat（AI_CHAT.AI_PID = 父 chat 的 AI_ID，
	 * request_id 为全新 UUID），父 chat 的对话历史不受影响。
	 *
	 * @param toolName   工具名称
	 * @param tool       useMode 工具定义
	 * @param args       LLM 给出的工具参数
	 * @param refHeaders 请求头信息
	 * @return 子 mode 的 AI 响应内容
	 * @throws Exception 子 mode 未定义或调用失败时抛出异常
	 */
	private String executeUseModeCall(String toolName, Tool tool, JSONObject args, Map<String, String> refHeaders)
			throws Exception {
		String subModeName = tool.getUseMode().trim();
		Mode subMode = Modes.getMode(subModeName);
		if (subMode == null) {
			throw new Exception("useMode 未找到: " + subModeName + " (tool: " + toolName + ")");
		}

		// args 注入 RequestValue，供子 mode 的 prompt/api/sql 中 @占位符 使用
		RequestValue rvPrev = this.rv;
		// 复制一份，同时切换 db 的 rv 引用，避免 db 操作污染原始 rv
		this.rv = rvPrev.clone();
		this.db.setRv(this.rv);
		for (String argName : args.keySet()) {
			this.rv.addOrUpdateValue(argName, args.optString(argName));
		}

		// 构建请求：非流式，沿用当前 provider/model，采样参数取子 mode 的定义
		IRequestData reqData = RequestDataFactory.createRequestData(aiProvider);
		reqData.stream(false).model(this.aiModel).thinking(subMode.isThinking());
		if (subMode.getThinkingBudget() > 0) {
			reqData.thinkingBudget(subMode.getThinkingBudget());
		}
		if (subMode.getTemperature() != 0) {
			reqData.temperature(subMode.getTemperature());
		}
		if (subMode.getTopP() != 0) {
			reqData.topP(subMode.getTopP());
		}
		if (!StringUtils.isBlank(subMode.getResponseFormat())) {
			reqData.responseFormat(subMode.getResponseFormat());
		}
		if (subMode.isEnableSearch()) {
			// 联网搜索（如 qwen enable_search），provider 不支持时忽略该字段
			reqData.getParameters().put("enable_search", true);
		}

		// 拼接子 mode 各 step 的 prompts（跳过 apisCheck 与空内容 prompt）
		for (Step s : subMode.getSteps()) {
			if (s.getPrompts() == null) {
				continue;
			}
			for (Prompt p : s.getPrompts()) {
				if (p.isApisCheck()) {
					continue;
				}
				subMode.createStepPrompt(p, this.dbConfigName, this.rv, refHeaders);
				String promptContent = p.getContent();
				if (StringUtils.isBlank(promptContent)) {
					continue;
				}
				if (!StringUtils.isBlank(p.getPrefix())) {
					promptContent = p.getPrefix() + promptContent;
				}
				String role = StringUtils.isBlank(p.getRole()) ? "user" : p.getRole();
				reqData.addMessage(promptContent, role);
			}
		}

		// 用户输入：单参数取其值，多参数传 JSON 字符串
		String userInput;
		if (args.length() == 1) {
			String onlyKey = args.keySet().iterator().next();
			userInput = args.optString(onlyKey);
		} else {
			userInput = args.toString();
		}
		reqData.addMessage(userInput, "user");

		// 根据 debugOutput 开关决定是否输出调试信息
		if (mode.isDebugOutput()) {
			JSONObject msg = UJSon.rstTrue("");
			msg.put("reasoning_content", "调用子Mode: " + subModeName + ", " + args.toString() + "\n\n");
			this.outEvent(msg.toString());
		}
		LOGGER.info("useMode 调用: tool={}, mode={}, args={}", toolName, subModeName, args.toString());

		// 记录父级状态，创建子 chat（AI_CHAT.AI_PID = 父 chat 的 AI_ID），
		// 子 mode 的调用过程记录到子 chat，父 chat 的对话历史不受影响
		long parentAiId = this.aiId;
		String prevRequestId = this.requestId;
		String prevStepName = this.stepName;
		boolean prevIsNew = this.isNew;
		short prevInteractions = this.aimNumberOfInteractions;

		try {
			this.rv.addOrUpdateValue("p_ai_pid", parentAiId);
			this.rv.addOrUpdateValue("MODE", subModeName);
			this.stepName = toolName;
			this.rv.addOrUpdateValue("AIM_STEP", this.stepName);
			// 子 chat 使用全新的 request_id（AI_UID），必然走 insert 分支
			this.requestId = Utils.getGuid();
			this.rv.addOrUpdateValue("request_id", this.requestId );
			
			getOrNewAiChat();
			this.aimNumberOfInteractions = db.getNextInteractionNumber(this.aiId);

			// 记录子 mode 的完整调用过程到子 chat（隐藏消息，不参与对话历史）
			IRequestAI req = createRequestAI();
			this.addAiChatMsg("useMode: " + subModeName + "\n" + req.curl(reqData), "api_call_curl", true);
			this.addAiChatMsg(userInput, "user", true);

			String fullText = req.doPost(reqData);
			JSONObject json = req.extraceJson(fullText, true);
			String content = json != null && json.has("content") ? json.getString("content") : fullText;

			long aimId = this.addAiChatMsg(content, "assistant", true);
			JSONObject usage = req.getTokensUsage();
			if (usage != null) {
				this.updateAiChatMsgTokens(aimId, usage);
			}

			return tool.getDescription() + "数据:\n" + content;
		} finally {
			// 恢复父 chat 上下文
			this.rv = rvPrev;
			this.db.setRv(this.rv);

			this.aiId = parentAiId;
			this.requestId = prevRequestId;
			this.stepName = prevStepName;
			this.isNew = prevIsNew;
			this.aimNumberOfInteractions = prevInteractions;
		}
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
		// 如果这个 prompt 是 API 调用并且有 curl 记录，就保存到数据库
		if (p.getApi() != null && p.getApiCurl() != null) {
			rv.addOrUpdateValue("AIM_PROMPT_NAME", p.getName() + "_curl");
			addAiChatMsg(p.getApiCurl(), "api_call_curl", true);
		}
		String role = p.getRole();
		if (StringUtils.isBlank(role)) {
			role = "user";
		}
		String promptContent;
		// 对于 apisCheck 的 prompt，使用 apiToolsChecks 中已存储的 API 调用结果，
		// 而不是从共享 Prompt 对象中读取（避免因 setContent 导致的跨请求状态污染）
		if (p.isApisCheck() && this.apiCheckResults.containsKey(p.getName())) {
			promptContent = this.apiCheckResults.get(p.getName());
		} else {
			promptContent = p.getContent();
		}
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

	private boolean checkPreviousOverTime() {
		if (this.step.getCachedSeconds() <= 0) {
			return false;
		}
		DTTable tbMsg = db.queryLatestSystemMessageTime(this.aiId);
		if (tbMsg.getCount() == 0) {
			return true;
		}
		if (tbMsg.getCell(0, 0).isNull()) {
			return true;
		}
		long messageTime = tbMsg.getCell(0, 0).toTime();
		long expireTime = messageTime + step.getCachedSeconds() * 1000;
		return expireTime < System.currentTimeMillis();
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
		boolean isOverTime = checkPreviousOverTime();

		if (isOverTime && this.step != null && !StringUtils.isBlank(this.step.getName())) {
			// 缓存过期：标记当前 step 的旧消息为 SKIP
			db.markStepMessagesSkipped(this.aiId, this.step.getName());
			LOGGER.info("cachedSeconds expired for step '{}', old prompts marked as skipped", this.step.getName());

			// 记录一条重建 system 提示的消息，便于排查
			String nowStr = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
					.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"));
			String rebuildMsg = "系统提示词重建：" + this.step.getName() + "，时间：" + nowStr;
			rv.addOrUpdateValue("AIM_PROMPT_NAME", "system_rebuild");
			this.addAiChatMsg(rebuildMsg, "system", true);
			rv.addOrUpdateValue("AIM_PROMPT_NAME", null);
		}

		// 使用 DTTable 分页方法限制返回条数，兼容所有数据库
		DTTable tbMsg = db.loadHistoryMessages(this.aiId, maxMsgCount);

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
			String promptContent = this.apiCheckResults.get(p.getName());
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
	 * 估算文本的 token 数量 中文/日文/韩文按 1.5 char/token，其他按 4 char/token 估算
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
		if (maxTokens <= 0)
			return messages;

		int totalTokens = 0;
		for (org.json.JSONObject msg : messages) {
			totalTokens += estimateTokens(msg.optString("content", ""));
		}

		if (totalTokens <= maxTokens) {
			return messages; // 未超限，无需截断
		}

		// 从最早的消息开始删除，但保留 system 角色的消息
		List<org.json.JSONObject> result = new ArrayList<>(messages);
		int removed = 0;
		while (totalTokens > maxTokens && result.size() > 1) {
			// 找到第一条非 system 消息
			int removeIdx = -1;
			for (int i = 0; i < result.size(); i++) {
				if (!"system".equalsIgnoreCase(result.get(i).optString("role"))) {
					removeIdx = i;
					break;
				}
			}
			if (removeIdx < 0) {
				break; // 剩余全是 system 消息，不再删除
			}
			String removedContent = result.get(removeIdx).optString("content", "");
			totalTokens -= estimateTokens(removedContent);
			result.remove(removeIdx);
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

		String prompt = rv.s("prompt");
		this.prompt = prompt;

		RouterMode routerMode = Modes.getRouterMode(modeName.trim());
		if (routerMode != null) {
			// 命中 routerMode：根据用户输入做 LLM 分类，路由到 routerMode 声明的候选 mode（每轮都分类）
			JSONObject routeRst = this.routeModeByPrompt(routerMode);
			if (routeRst != null) {
				return routeRst;
			}
		} else {
			Mode mode = Modes.getMode(modeName);
			if (mode == null) {
				return UJSon.rstFalse(getText(ErrorMessages.ERROR_MODE_NOT_FOUND) + modeName);
			}
			this.mode = mode;
		}

		// 是否思考模式
		if (StringUtils.isBlank(rv.s("ai_thinking"))) {
			this.aiThinking = mode.isThinking();
		} else {
			this.aiThinking = Utils.cvtBool(rv.s("ai_thinking"));
		}

		// 是否思考预算（请求参数 ai_thinking_budget 覆盖 mode 默认；0 或空表示使用 mode 默认）
		String aiThinkingBudgetStr = rv.s("ai_thinking_budget");
		if (StringUtils.isNotBlank(aiThinkingBudgetStr)) {
			try {
				this.aiThinkingBudget = Integer.parseInt(aiThinkingBudgetStr.trim());
				if (this.aiThinkingBudget < 0) {
					this.aiThinkingBudget = 0;
				}
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid ai_thinking_budget value: {}", aiThinkingBudgetStr);
				this.aiThinkingBudget = mode.getThinkingBudget();
			}
		} else {
			this.aiThinkingBudget = mode.getThinkingBudget();
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

		// mode=auto 路由落库：记录隐藏消息与分类 token 用量；已有会话 mode 变化时切换 AI_MODE
		if (this.routeInfo != null) {
			long routeAimId = this.addAiChatMsg(this.routeInfo, "mode_route", true);
			if (this.routeUsage != null) {
				this.updateAiChatMsgTokens(routeAimId, this.routeUsage);
			}
			if (!this.isNew && !this.modeName.equalsIgnoreCase(chat.optString("AI_MODE"))) {
				db.updateChatMode(this.aiId, this.modeName);
				LOGGER.info("mode=auto switch AI_CHAT.AI_MODE to {} (aiId={})", this.modeName, this.aiId);
			}
		}

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
		rst.put("ai_thinking_budget", this.aiThinkingBudget);
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
	 * 检查输入参数是否满足当前模式的paramChecks定义 从AI_CHAT_PARAMS表加载已保存的参数，与paramChecks定义进行校验
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

		Map<String, String> savedParams = db.loadSavedParams(this.aiId);

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
	 * 从用户请求中提取参数并保存到AI_CHAT_PARAMS表 使用AI从对话上下文中提取结构化参数（出发城市、目的地、天数等）
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
		return db.loadConversationContext(this.aiId);
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
			if (!first)
				sb.append(",");
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
		return db.saveParams(this.aiId, extractedParams, paramChecks);
	}

	public JSONObject checkProviderAndModel() {
		JSONObject result = db.checkProviderAndModel(this.aiProvider, this.aiModel, this.apiOwnerId);
		if (!result.optBoolean("RST")) {
			String errorKey = result.optString("errorKey");
			JSONArray errorArgs = result.optJSONArray("errorArgs");
			Object[] args = new Object[errorArgs != null ? errorArgs.length() : 0];
			for (int i = 0; i < args.length; i++) {
				args[i] = errorArgs.get(i);
			}
			return UJSon.rstFalse(getText(errorKey, args));
		}
		this.apiUrl = result.optString("apiUrl");
		this.apiKey = result.optString("apiKey");
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
		stepAction.setAction(action);
		if (stepAction instanceof ActionBase) {
			((ActionBase) stepAction).setChatManagerDb(this.db);
			((ActionBase) stepAction).setOutEvents(this.outEvents);
			((ActionBase) stepAction).setWriter(this.writer);
		}
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
		JSONObject chat = db.queryChatByRequestId(this.requestId);
		if (chat != null) {
			chat.put("IS_NEW", false);
			if (!chat.optString("AI_STEP_PREV").equalsIgnoreCase(this.stepName)) {
				db.updateChatStep(chat.optLong("AI_ID"), this.stepName);
			}
			this.isNew = false;
			this.aiId = chat.optLong("AI_ID");
			this.aiStepPrev = chat.optString("AI_STEP_PREV");
			this.aiRef = chat.optString("AI_REF");
			this.aiRefId = chat.optString("AI_REF_ID");
			this.aimNumberOfInteractions = db.getNextInteractionNumber(this.aiId);
			return chat;
		}

		// 设置插入所需的参数
		rv.addOrUpdateValue("AI_PROVIDER", this.aiProvider);
		rv.addOrUpdateValue("AI_MODEL", this.aiModel);
		rv.addOrUpdateValue("AI_THINKING", this.aiThinking ? 1 : 0);
		rv.addOrUpdateValue("AI_STREAM", this.aiStream ? 1 : 0);
		rv.addOrUpdateValue("AIM_STEP", this.stepName);
		rv.addOrUpdateValue("MODE", this.modeName);

		db.createChat(rv);
		// 重新查询获取完整记录
		chat = db.queryChatByRequestId(this.requestId);
		if (chat == null) {
			LOGGER.error(getText(ErrorMessages.ERROR_AI_CHAT_CREATE_FAILED), "new AI_CHAT");
			return UJSon.rstFalse(getText(ErrorMessages.ERROR_AI_CHAT_CREATE_FAILED) + " new AI_CHAT");
		}
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
		return db.addMessage(this.aiId, msg, role, this.stepName, rv.s("AIM_PROMPT_NAME"), rv.s("AIM_ACTION"),
				rv.s("AIM_ACTION_CLASS"), this.aimNumberOfInteractions, isSkipAppend, byUser);
	}

	/**
	 * 更新AI聊天消息
	 *
	 * @param aimId 消息ID
	 * @param msg   消息内容
	 */
	public void updateAiChatMsg(long aimId, String msg) {
		db.updateMessage(aimId, msg);
	}

	/**
	 * 更新AI聊天消息的Token使用情况
	 * 
	 * @param aimId 消息ID
	 * @param usage Token使用情况JSON对象，包含total_tokens, completion_tokens,
	 *              prompt_tokens字段
	 */
	public void updateAiChatMsgTokens(long aimId, JSONObject usage) {
		db.updateMessageTokens(aimId, usage);
	}

	/**
	 * 更新AI聊天消息的Token使用情况
	 */
	public void updateAiChatMsgTokens(long aimId, long totalTokens, long completionTokens, long promptTokens) {
		db.updateMessageTokens(aimId, totalTokens, completionTokens, promptTokens, 0);
	}

	/**
	 * 更新AI聊天消息的Token使用情况（含缓存Token）
	 */
	public void updateAiChatMsgTokens(long aimId, long totalTokens, long completionTokens, long promptTokens,
			long cachedTokens) {
		db.updateMessageTokens(aimId, totalTokens, completionTokens, promptTokens, cachedTokens);
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
	 * 处理 innerCall 步骤：内部调用，输出不返回给用户 使用现有的 apisCheck 机制调用 API（通过 UNet），AI 响应直接返回
	 * 
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

				@Override
				public void outEvent(String msg, java.io.PrintWriter w) {
					capturedOutput.append(msg).append("\n");
				}

				@Override
				public int getMessageCount() {
					return messageCount;
				}

				@Override
				public void setMessageCount(int c) {
					this.messageCount = c;
				}

				@Override
				public String getLine() {
					return null;
				}

				@Override
				public void setLine(String l) {
				}

				@Override
				public org.json.JSONObject getContenJson() {
					return null;
				}

				@Override
				public void setContenJson(org.json.JSONObject j) {
				}

				@Override
				public String getName() {
					return null;
				}

				@Override
				public void setName(String n) {
				}

				@Override
				public String getLang() {
					return "zhcn";
				}

				@Override
				public void setLang(String l) {
				}
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
			long aimId = addAiChatMsg(content, "assistant", true);

			// 记录 Token 使用情况（非流式响应的 usage 由 extraceJson 解析到 req 中）
			JSONObject usage = req.getTokensUsage();
			if (usage != null) {
				this.updateAiChatMsgTokens(aimId, usage);
			}

			return content;
		} catch (Exception e) {
			LOGGER.error("processInnerCallStep failed for step: {}", innerStep != null ? innerStep.getName() : "null",
					e);
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
		if (prompt == null)
			return null;
		if (step == null || !step.isMultiOnlyUserMsg()) {
			return prompt;
		}
		// 通过 AI_PID 找到父 chat，然后提取所有相关 chat 的用户消息
		try {
			Long parentAiId = db.queryParentAiId(this.aiId);
			if (parentAiId == null)
				return prompt;

			DTTable msgTb = db.queryParentUserMessages(parentAiId);
			if (msgTb.getCount() == 0)
				return prompt;

			StringBuilder sb = new StringBuilder();
			sb.append("【历史对话】\n");
			String lastMsg = null;
			int roundNum = 0;
			for (int i = 0; i < msgTb.getCount(); i++) {
				String msg = msgTb.getCell(i, "AIM_MSG").toString();
				String actualInput = msg;
				int idx = msg.lastIndexOf("【当前输入】");
				if (idx >= 0) {
					actualInput = msg.substring(idx + 6).trim();
				}
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

	/**
	 * 保存单个校验参数到 {@code AI_CHAT_PARAMS}（委托给 {@link ChatManagerDb#saveValidateParam}）。
	 *
	 * @param name  参数名
	 * @param value 参数值
	 */
	public void saveValidateParam(String name, String value) {
		this.db.saveValidateParam(this.aiId, name, value);
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

	/**
	 * 切换到指定的步骤（用于 innerCall 完成后切换到主步骤）
	 *
	 * @param stepName 目标步骤名称
	 * @param step     目标步骤对象
	 */
	public void switchStep(String stepName, Step step) {
		this.stepName = stepName;
		this.step = step;
		this.rv.addOrUpdateValue("AIM_STEP", stepName);
		// 更新数据库中的 AI_CUR_STEP
		try {
			if (this.aiId > 0) {
				db.updateChatStep(this.aiId, stepName);
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to update AI_CUR_STEP in database: {}", e.getMessage());
		}
		// 清除旧的 action 引用
		this.stepAction = null;
		this.actionName = null;
		this.action = null;
		this.actionClassName = null;
		// 重新加载 action（如果新步骤有 action）
		this.loadAction();
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

	public String getAiModel() {
		return aiModel;
	}

	public void setAiModel(String aiModel) {
		this.aiModel = aiModel;
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
	 * 
	 * <pre>
	 * ChatManagerBase manager = new ChatManagerBase(rv, dbConfig, writer);
	 * manager.checkParams(); // 初始化 provider/model/apiUrl/apiKey/aiId
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
	 * @return 包含 content（回复内容）和 usage（Token 用量）的 JSONObject 失败时返回 {RST:false,
	 *         error:错误信息}
	 */
	public JSONObject callAI(String prompt) {
		AiTool[] nulltools = null;
		return callAI(prompt, nulltools);
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
	public JSONObject callAI(String prompt, AiTool... tools) {
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
			addAiChatMsg(prompt, "user", false, true);

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
	 * 最简用法：传入 provider、model、apiUrl、apiKey 和 prompt 即可返回 AI 响应。 无需初始化
	 * ChatManagerBase，无需数据库配置，无需 RequestValue。 此为静态工具方法，不支持多轮对话（每次调用为独立会话）。
	 *
	 * <h3>使用示例</h3>
	 * 
	 * <pre>
	 * // 一次性调用（无历史上下文）
	 * JSONObject result = ChatManagerBase.callAI("openai", "gpt-4o", "https://api.openai.com/v1/chat/completions",
	 * 		"sk-xxx...", "你好");
	 * String content = result.getString("content");
	 *
	 * // 带系统提示词
	 * JSONObject result = ChatManagerBase.callAI("qwen", "qwen-max", apiUrl, apiKey, "请翻译以下内容：Hello World",
	 * 		"你是一个专业的翻译助手");
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
	 * @return 包含 content（回复内容）和 usage（Token 用量）的 JSONObject 失败时返回 {RST:false,
	 *         error:错误信息}
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
	public static JSONObject callAI(String provider, String model, String apiUrl, String apiKey, String prompt,
			String systemMsg) {
		AiTool[] nulltools = null;
		return callAI(provider, model, apiUrl, apiKey, prompt, systemMsg, nulltools);
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
	public static JSONObject callAI(String provider, String model, String apiUrl, String apiKey, String prompt,
			String systemMsg, AiTool... tools) {
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

	/**
	 * 获取模型归属供应商，供应商自己的 apikey
	 */
	public String getApiOwnerId() {
		return apiOwnerId;
	}

	/**
	 * 获取模型归属供应商，供应商自己的 apikey
	 * 
	 * @param apiOwnerId 供应商Id
	 */
	public void setApiOwnerId(String apiOwnerId) {
		this.apiOwnerId = apiOwnerId;
	}

}
