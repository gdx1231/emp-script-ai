package com.gdxsoft.ai;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.request.ActionEventsOut;
import com.gdxsoft.ai.request.IOutEvents;
import com.gdxsoft.ai.request.IRequestAI;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UJSon;
import com.gdxsoft.easyweb.utils.ULogic;

/**
 * AiStreamOrPost 负责管理 AI 聊天请求的初始化与处理，支持流式和非流式（POST）两种模式。 它协调聊天管理器、AI
 * 请求/响应处理以及输出机制之间的交互。
 * <p>
 * 主要职责包括：
 * <ul>
 * <li>初始化聊天管理器并验证请求参数。</li>
 * <li>处理 AI 请求，包括准备请求数据、执行请求（流式或 POST）、处理响应。</li>
 * <li>管理错误处理并输出错误信息。</li>
 * <li>支持步骤动作和每次 AI 交互的令牌用量记录。</li>
 * </ul>
 * <p>
 * 本类适用于需要 AI 聊天交互的 Web 或服务场景，并为自定义步骤动作和输出事件处理提供了扩展点。
 *
 * <p>
 * 示例用法：
 * 
 * <pre>
 * AiStreamOrPost aiHandler = new AiStreamOrPost();
 * if (aiHandler.init(rv, dbConfigName, writer)) {
 * 	String response = aiHandler.processRequest();
 * 	// 处理响应
 * } else {
 * 	// 处理初始化错误
 * }
 * </pre>
 *
 * @author (gdx)
 * @since 1.0
 */
public class AiStreamOrPost {
	private static final Logger LOGGER = LoggerFactory.getLogger(AiStreamOrPost.class);

	// 国际化消息映射
	private static final Map<String, Map<String, String>> MESSAGES = new HashMap<>();

	static {
		// 中文消息
		Map<String, String> zhcnMessages = new HashMap<>();
		zhcnMessages.put("NO_PROMPTS_NO_ACTION", "没有定义提示词，且没有步骤动作 action，stepName=");
		zhcnMessages.put("ACTION_EXECUTION_ERROR", "执行动作发生错误，");
		zhcnMessages.put("GENERAL_ERROR", "发生错误，");
		zhcnMessages.put("UNSUPPORTED_AI_PROVIDER", "不支持的AI提供商 ai_provider，");
		zhcnMessages.put("REQUEST_SUCCESS", "请求成功");
		zhcnMessages.put("SYSTEM_ERROR", "系统发生错误：");
		zhcnMessages.put("STEP_ACTION_ERROR", "执行步骤动作时发生错误：");
		zhcnMessages.put("GLOBAL_EXCEPTION_ERROR", "处理全局异常时发生错误：");
		zhcnMessages.put("AI_REQUEST_ERROR", "AI请求处理错误：");
		zhcnMessages.put("EXPORT_RESULT", "导出结果：");

		// 英文消息
		Map<String, String> enMessages = new HashMap<>();
		enMessages.put("NO_PROMPTS_NO_ACTION", "No prompts defined and no step action, stepName=");
		enMessages.put("ACTION_EXECUTION_ERROR", "Error executing action, ");
		enMessages.put("GENERAL_ERROR", "Error occurred, ");
		enMessages.put("UNSUPPORTED_AI_PROVIDER", "Unsupported AI provider ai_provider, ");
		enMessages.put("REQUEST_SUCCESS", "Request successful");
		enMessages.put("SYSTEM_ERROR", "System error occurred: ");
		enMessages.put("STEP_ACTION_ERROR", "Error executing step action: ");
		enMessages.put("GLOBAL_EXCEPTION_ERROR", "Error handling global exception: ");
		enMessages.put("AI_REQUEST_ERROR", "AI request processing error: ");
		enMessages.put("EXPORT_RESULT", "Export result: ");

		MESSAGES.put("zhcn", zhcnMessages);
		MESSAGES.put("enus", enMessages);
	}

	private ChatManagerBase chatManager;
	private PrintWriter writer;
	private JSONObject lastError;

	private boolean en;

	/**
	 * 获取本地化消息
	 * 
	 * @param key 消息键
	 * @return 本地化消息
	 */
	private String getMessage(String key) {
		String lang = this.en ? "enus" : "zhcn"; // 默认中文

		Map<String, String> langMessages = MESSAGES.get(lang);
		if (langMessages != null && langMessages.containsKey(key)) {
			return langMessages.get(key);
		}

		// 如果找不到对应语言的消息，返回中文默认消息
		Map<String, String> defaultMessages = MESSAGES.get("zhcn");
		return defaultMessages.getOrDefault(key, key);
	}

	/**
	 * 初始化
	 * 
	 * @param rv           请求对象
	 * @param dbConfigName 数据库配置名称
	 * @param writer       输出对象
	 * @return 是否初始化成功
	 * @throws IOException 发生IO错误
	 */
	public boolean init(RequestValue rv, String dbConfigName, PrintWriter writer) {
		// 创建ChatManager实例
		this.writer = writer;
		this.en = "enus".equalsIgnoreCase(rv.getLang());
		chatManager = new ChatManagerBase(rv, dbConfigName, writer);
		try {
			// 验证参数
			if (!validateParameters()) {
				return false;
			}
		} catch (Exception e) {
			try {
				handleGlobalException(e);
			} catch (IOException e1) {
				LOGGER.error(getMessage("GLOBAL_EXCEPTION_ERROR") + "{}", e1.getMessage(), e1);
			}
			return false;
		}
		return true;
	}

	/**
	 * 处理AI流式请求
	 */
	public String processRequest() throws IOException {
		// 创建ChatManager实例
		try {
			// 处理 innerCall 步骤（如果有）
			String innerCallResult = processInnerCallIfAny();
			if (innerCallResult != null) {
				try {
					org.json.JSONObject innerJson = new org.json.JSONObject(innerCallResult);

					// 优先信任 AI 的明确判断：如果返回了 message 字段
					if (innerJson.has("message")) {
						if (!innerJson.optBoolean("RST")) {
							// AI 明确判断参数不完整或不是旅游请求
							String message = innerJson.optString("message", "");
							if (!message.isEmpty()) {
								chatManager.outEvent(message);
								return message;
							}
						} else {
							// AI 认为参数完整，用 validateParams 兜底检查
							String missing = checkMissingParams(innerJson, chatManager.getMode());
							if (!missing.isEmpty()) {
								String msg = "请确认：" + missing + "？";
								chatManager.outEvent(msg);
								return msg;
							}
							// 参数确实完整，提取并保存
						}
					} else {
						// AI 返回格式不对（可能是工具原始结果），用 validateParams 检查
						String missing = checkMissingParams(innerJson, chatManager.getMode());
						if (!missing.isEmpty()) {
							String msg = "请确认：" + missing + "？";
							chatManager.outEvent(msg);
							return msg;
						}
					}

					// 参数校验通过，提取 params 并注入到 RequestValue 供后续 step 使用
					// 可能嵌套在 "params" 中，也可能是顶层字段（工具原始结果）
					org.json.JSONObject paramsObj = null;
					if (innerJson.has("params") && innerJson.getJSONObject("params").length() > 0) {
						paramsObj = innerJson.getJSONObject("params");
					} else if (innerJson.has("people") || innerJson.has("departure_date") || innerJson.has("cities")) {
						// 工具原始结果，直接用顶层对象
						paramsObj = innerJson;
					}

						if (paramsObj != null) {
							// 提取顶层键值对
							for (String key : paramsObj.keySet()) {
								String val = paramsObj.optString(key, "");
								if (val != null && !val.isEmpty()) {
									chatManager.getRv().addOrUpdateValue(key, val);
								}
							}
							// 提取嵌套的 "people" 对象
							if (paramsObj.has("people")) {
								org.json.JSONObject people = paramsObj.optJSONObject("people");
								if (people != null) {
									for (String key : people.keySet()) {
										String val = people.optString(key, "");
										if (val != null && !val.isEmpty()) {
											chatManager.getRv().addOrUpdateValue(key, val);
										}
									}
								}
							}
							// 保存参数到 AI_CHAT_PARAMS 表，供后续轮次的 checkparamsapi 读取
							try {
								long aiId = chatManager.getAiId();
								if (aiId > 0) {
									for (String saveKey : paramsObj.keySet()) {
										String saveVal = paramsObj.optString(saveKey, "");
										if (saveVal != null && !saveVal.isEmpty() && !saveVal.startsWith("{")) {
											String sql = "insert into AI_CHAT_PARAMS (AI_ID, AIM_ID, AIP_NAME, AIP_VAL, AIP_TYPE) "
												+ "values (" + aiId + ", 0, '" + saveKey.replace("'", "''") + "', '" + saveVal.replace("'", "''") + "', 'validate')";
											com.gdxsoft.easyweb.data.DTTable.getJdbcTable(sql, chatManager.getRv());
										}
									}
								}
							} catch (Exception saveEx) {
								System.out.println("保存 innerCall 参数到 AI_CHAT_PARAMS 失败: " + saveEx.getMessage());
							}
						}
				} catch (Exception e) {
					// 不是 JSON，忽略
				}
			}

			// 处理无提示词的情况
			if (handleNoPromptsCase()) {
				return null;
			}

			// 准备AI请求数据
			IRequestData reqData = prepareRequestData();
			if (reqData == null) {
				return null;
			}

			// 执行AI请求并处理结果
			String fullText = executeAiRequest(reqData);
			return fullText;
		} catch (Exception e) {
			handleGlobalException(e);
			return null;
		}
	}

	/**
	 * 验证请求参数
	 *
	 * @return 是否验证通过
	 * @throws Exception 发生错误
	 */
	private boolean validateParameters() throws Exception {
		JSONObject checkParamsRst = chatManager.checkParams();
		if (!checkParamsRst.optBoolean("RST")) {
			chatManager.outEvent(checkParamsRst.toString());
			this.lastError = checkParamsRst;
			return false;
		}
		return true;
	}

	/**
	 * 处理 innerCall 步骤（如果有）
	 * 查找 mode 中标记为 innerCall=true 的步骤，在正常流程前内部执行
	 * @return innerCall 步骤的 AI 返回内容，如果没有 innerCall 步骤返回 null
	 */
	private String processInnerCallIfAny() {
		if (chatManager == null || chatManager.getMode() == null || !chatManager.isNew()) {
			return null;
		}
		com.gdxsoft.ai.modes.Mode mode = chatManager.getMode();
		if (mode.getSteps() == null) {
			return null;
		}
		for (com.gdxsoft.ai.modes.Step s : mode.getSteps()) {
			if (s.isInnerCall() && s.getPrompts() != null && !s.getPrompts().isEmpty()) {
				System.out.println("AiStreamOrPost 执行 innerCall 步骤: " + s.getName());
				return chatManager.processInnerCallStep(s);
			}
		}
		return null;
	}

	/**
	 * 检查 innerCall 返回结果中是否缺少必填参数
	 * @param result innerCall AI 返回的 JSON
	 * @param mode 当前模式
	 * @return 缺失参数的中文描述，空字符串表示无缺失，null 表示所有参数都缺失（可能不是旅游请求）
	 */
	private String checkMissingParams(JSONObject result, com.gdxsoft.ai.modes.Mode mode) {
		if (mode == null || mode.getSteps() == null) return "";

		// 查找 innerCall step 的 validateParams（逗号分隔的 paramCheck name 列表）
		String validateParams = null;
		for (com.gdxsoft.ai.modes.Step s : mode.getSteps()) {
			if (s.isInnerCall() && s.getValidateParams() != null) {
				validateParams = s.getValidateParams();
				break;
			}
		}
		if (validateParams == null || validateParams.isEmpty()) return "";

		// 从 mode.getParamChecks() 构建 param -> des 映射
		java.util.Map<String, String> paramLabels = new java.util.LinkedHashMap<>();
		if (mode.getParamChecks() != null) {
			for (com.gdxsoft.ai.modes.ParamCheck pc : mode.getParamChecks()) {
				if (pc.getDes() != null && !pc.getDes().isEmpty()) {
					paramLabels.put(pc.getName(), pc.getDes());
				}
			}
		}

		// 解析 validateParams 列表
		java.util.List<String> paramNames = new java.util.ArrayList<>();
		String[] defs = validateParams.split(",");
		for (String def : defs) {
			String name = def.trim();
			if (!name.isEmpty()) paramNames.add(name);
		}

		// 检查每个参数是否有值
		StringBuilder missing = new StringBuilder();
		int missingCount = 0;
		for (String param : paramNames) {
			boolean hasValue = false;
			// 检查顶层字段
			if (result.has(param)) {
				Object val = result.get(param);
				if (val instanceof Number) {
					hasValue = ((Number) val).doubleValue() != 0;
				} else if (val instanceof String) {
					hasValue = !val.toString().trim().isEmpty();
				} else if (val instanceof org.json.JSONObject) {
					hasValue = val.toString().length() > 2;
				} else {
					hasValue = true;
				}
			}
			// 检查嵌套在 "people" 对象中的字段
			if (!hasValue && result.has("people")) {
				org.json.JSONObject people = result.optJSONObject("people");
				if (people != null && people.has(param)) {
					Object val = people.get(param);
					if (val instanceof Number) {
						hasValue = ((Number) val).doubleValue() != 0;
					} else if (val instanceof String) {
						hasValue = !val.toString().trim().isEmpty();
					} else {
						hasValue = true;
					}
				}
			}
			// 检查嵌套在 "params" 对象中的字段
			if (!hasValue && result.has("params")) {
				org.json.JSONObject paramsObj = result.optJSONObject("params");
				if (paramsObj != null && paramsObj.has(param)) {
					Object val = paramsObj.get(param);
					if (val instanceof Number) {
						hasValue = ((Number) val).doubleValue() != 0;
					} else if (val instanceof String) {
						hasValue = !val.toString().trim().isEmpty();
					} else {
						hasValue = true;
					}
				}
			}
			if (!hasValue) {
				missingCount++;
				String label = paramLabels.getOrDefault(param, param);
				if (missing.length() > 0) missing.append("和");
				missing.append(label);
			}
		}
		return missing.toString();
	}

	/**
	 * 处理无提示词的情况
	 * 
	 * @return 是否处理了无提示词的情况
	 */
	private boolean handleNoPromptsCase() {
		if (!chatManager.isNew()
				&& (chatManager.getStep().getPrompts() == null || chatManager.getStep().getPrompts().size() == 0)) {
			if (chatManager.getStepAction() == null) {
				JSONObject rst = UJSon.rstFalse(getMessage("NO_PROMPTS_NO_ACTION") + chatManager.getStepName());
				this.lastError = rst;
				chatManager.outEvent(rst.toString());
				return true;
			}
			try {
				chatManager.doAction();
			} catch (Exception err) {
				JSONObject rst = UJSon.rstFalse("stepName=" + chatManager.getStepName() + ", "
						+ getMessage("ACTION_EXECUTION_ERROR") + err.getMessage());
				this.lastError = rst;
				chatManager.outEvent(rst.toString());
			}
			return true;
		}
		return false;
	}

	/**
	 * 准备AI请求数据
	 * 
	 * @return 请求数据
	 */
	private IRequestData prepareRequestData() {
		try {
			IRequestData reqData = chatManager.createRequestData();
			chatManager.appendPrompts(reqData);
			String resolvedPrompt = chatManager.getResolvedPrompt();
			reqData.userMessage(resolvedPrompt);
			chatManager.addAiChatMsg(resolvedPrompt, "user", false, true);
			return reqData;
		} catch (Exception e) {
			JSONObject rst = UJSon.rstFalse(getMessage("GENERAL_ERROR") + e.getMessage());
			this.lastError = rst;
			chatManager.outEvent(rst.toString());
			return null;
		}
	}

	/**
	 * 执行AI请求并处理结果
	 * 
	 * @param reqData 请求数据
	 * @param writer  输出对象
	 */
	private String executeAiRequest(IRequestData reqData) {
		String fullText = null;
		IRequestAI req = null;
		long aimId = -1;

		try {
			// 创建AI请求实例
			req = createAiRequestInstance();
			if (req == null) {
				return null;
			}

			// 准备请求
			ChatManagerBase.putRequestAI(chatManager.getRequestId(), req);
			chatManager.addAiChatMsg(req.curl(reqData), "curl", true);

			aimId = chatManager.addAiChatMsg("", "assistant", false);

			// 执行请求
			fullText = executeRequest(req, reqData);

			// 处理响应
			processAiResponse(req, fullText, aimId);

		} catch (Exception err) {
			handleAiRequestException(req, err, aimId, fullText);
		} finally {
			// 清理资源
			ChatManagerBase.removeRequestAI(chatManager.getRequestId());
		}
		return fullText;
	}

	/**
	 * 创建AI请求实例
	 * 
	 * @param chatManager Chat管理器
	 * @return AI请求实例
	 */
	private IRequestAI createAiRequestInstance() {
		IRequestAI req = chatManager.createRequestAI();
		if (req != null) {
			return req;
		}
		JSONObject rst = UJSon.rstFalse(getMessage("UNSUPPORTED_AI_PROVIDER") + chatManager.getAiProvider());
		this.lastError = rst;
		chatManager.outEvent(rst.toString());
		return null;
	}

	/**
	 * 根据当前聊天步骤，使用流式或非流式方式执行AI请求。
	 *
	 * <p>
	 * 如果当前步骤为流式，则配置输出事件（如有动作），并调用流式请求；否则执行标准POST请求并处理响应。
	 * </p>
	 *
	 * @param req     要执行的AI请求实例
	 * @param reqData AI请求所需的数据
	 * @return AI请求的完整文本响应
	 * @throws Exception 执行请求时发生错误
	 */
	private String executeRequest(IRequestAI req, IRequestData reqData) throws Exception {
		String fullText;
		if (chatManager.getStep().isStream()) {
			if (chatManager.getStepAction() != null) {
				IOutEvents oes = new ActionEventsOut();
				oes.setName(chatManager.getActionName());
				req.setOutEvents(oes);
			}
			fullText = req.doStream(reqData, writer);
		} else {
			fullText = req.doPost(reqData);
			handleNonStreamResponse(req, fullText);
		}
		return fullText;
	}

	/**
	 * 处理非流式AI响应：从响应文本中提取JSON内容，构建结果JSON对象，并通过chatManager输出事件。
	 *
	 * @param req      AI请求对象，包含JSON提取方法
	 * @param fullText 需要处理的完整响应文本
	 * @param writer   输出用的PrintWriter（本方法未使用）
	 */
	private void handleNonStreamResponse(IRequestAI req, String fullText) {
		JSONObject json = req.extraceJson(fullText, true);
		JSONObject rst = UJSon.rstTrue(getMessage("REQUEST_SUCCESS"));
		if (json.has("content")) {
			rst.put("content", json.get("content"));
		}
		this.writer.write(rst.toString());
	}

	/**
	 * 处理AI响应
	 * 
	 * @param req      AI请求对象，包含令牌使用信息
	 * @param fullText AI响应的完整文本
	 * @param aimId    AI消息记录ID
	 */
	private void processAiResponse(IRequestAI req, String fullText, long aimId) {
		// 记录令牌使用情况
		recordTokenUsage(req, aimId);

		// 更新AI消息记录
		chatManager.updateAiChatMsg(aimId, fullText);

		// 处理步骤动作
		handleStepAction(fullText, writer);

		// 提取并保存用户请求中的参数到AI_CHAT_PARAMS表
		extractAndSaveInputParams();

		// AI 响应完成后，根据配置表达式判断是否发送 complete UI HTML
		if (evaluateUiTest(chatManager.getMode().getUiCompleteTest(), fullText)) {
			sendUiHtmlEvent("complete");
		}
	}

	/**
	 * 提取并保存用户请求中的参数
	 * 从对话上下文中提取结构化参数（出发城市、目的地、天数等）并保存到数据库
	 */
	private void extractAndSaveInputParams() {
		try {
			JSONObject result = chatManager.saveInputParams();
			if (result.optBoolean("RST")) {
				LOGGER.info("Input params extracted and saved: {}", result.optJSONObject("params"));
			} else {
				LOGGER.debug("Input params extraction skipped: {}", result.optString("MSG"));
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to extract input params: {}", e.getMessage());
		}
	}

	/**
	 * 记录令牌使用情况
	 * 
	 * @param req   AI请求对象，包含令牌使用信息
	 * @param aimId AI消息记录ID
	 */
	private void recordTokenUsage(IRequestAI req, long aimId) {
		if (req.getTokensUsage() == null) {
			return;
		}
		chatManager.updateAiChatMsgTokens(aimId, req.getTokensUsage());
	}

	/**
	 * 处理步骤动作
	 * 
	 * @param chatManager Chat管理器
	 * @param fullText    AI响应的完整文本
	 * @param writer      输出用的PrintWriter
	 */
	private void handleStepAction(String fullText, PrintWriter writer) {
		if (chatManager.getStepAction() != null) {
			try {
				JSONObject grpInfo = chatManager.getStepAction().doAction(chatManager.getRv(), fullText);
				LOGGER.info(getMessage("EXPORT_RESULT") + "{}", grpInfo.toString(2));

				chatManager.addAiChatMsg(grpInfo.toString(), "agent", true);
				if (chatManager.getStep().isStream()) {
					chatManager.outEvent(grpInfo.toString());
				} else {
					writer.write(grpInfo.toString());
				}
			} catch (Exception e) {
				LOGGER.error(getMessage("STEP_ACTION_ERROR") + "{}", e.getMessage(), e);
			}
		}
	}

	/**
	 * 发送 UI HTML 事件到前端。
	 * <ul>
	 * <li>welcome：新会话开始时发送，由 Mode.uiWelcome 配置</li>
	 * <li>complete：AI 响应完成后发送，由 Mode.uiComplete 配置</li>
	 * </ul>
	 * 仅在流式输出模式下发送，前端通过 SSE 事件中的 ui_html 和 ui_type 字段渲染。
	 *
	 * @param type 事件类型，"welcome" 或 "complete"
	 */
	private void sendUiHtmlEvent(String type) {
		if (!chatManager.getStep().isStream()) {
			return;
		}
		String html = null;
		if ("welcome".equals(type) && chatManager.isNew()) {
			html = chatManager.getMode().getUiWelcome();
		} else if ("complete".equals(type)) {
			html = chatManager.getMode().getUiComplete();
		}
		if (html == null || html.trim().isEmpty()) {
			return;
		}
		html = chatManager.getRv().replaceParameters(html);
		JSONObject msg = UJSon.rstTrue("");
		msg.put("ui_html", html);
		msg.put("ui_type", type);
		chatManager.outEvent(msg.toString());
	}

	/**
	 * 评估 UI 测试表达式。
	 * 使用 EWA 框架的 ULogic.runLogic() 通过 HSQLDB 执行 SQL 逻辑表达式。
	 * <p>
	 * 表达式使用 SQL 语法，{@code @fullText} 会被替换为 AI 响应文本。例如：
	 * <ul>
	 * <li>空或 null — 返回 true（无条件发送）</li>
	 * <li>{@code @fullText like '%<day>%'} — 文本包含 &lt;day&gt; 标签</li>
	 * <li>{@code @fullText like '%<day>%' and @fullText like '%<gn>%'} — 多条件组合</li>
	 * </ul>
	 *
	 * @param expression SQL 逻辑表达式，来自 Mode 配置（{@code <complete test="...">}）
	 * @param fullText   AI 响应的完整文本
	 * @return 表达式是否成立
	 */
	private boolean evaluateUiTest(String expression, String fullText) {
		if (expression == null || expression.trim().isEmpty()) {
			return true;
		}
		if (fullText == null) {
			fullText = "";
		}
		// 将 @fullText 替换为 SQL 字符串值（转义单引号）
		String escaped = fullText.replace("'", "''");
		String exp = expression.replace("@fullText", "'" + escaped + "'");

		try {
			return ULogic.runLogic(exp);
		} catch (Exception e) {
			LOGGER.error("evaluateUiTest error: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * 处理AI请求异常
	 * 
	 * @param req   AI请求对象
	 * @param err   发生的异常
	 * @param aimId AI消息记录ID
	 */
	private void handleAiRequestException(IRequestAI req, Exception err, long aimId, String fullText) {
		if (aimId > 0) {
			chatManager.updateAiChatMsg(aimId, fullText + "\n" + getMessage("GENERAL_ERROR") + err.getMessage());
		}
		JSONObject rst = UJSon.rstFalse(getMessage("GENERAL_ERROR") + err.getMessage());
		this.lastError = rst;
		if (chatManager.getStep().isStream()) {
			chatManager.outEvent(rst.toString());
		} else {
			writer.print(rst.toString());
		}
		LOGGER.error(getMessage("AI_REQUEST_ERROR") + "{}", err.getMessage(), err);
	}

	/**
	 * 处理全局异常
	 * 
	 * @param e 发生的异常
	 * @throws IOException 发生IO错误
	 */
	private void handleGlobalException(Exception e) throws IOException {
		JSONObject rst = UJSon.rstFalse(getMessage("SYSTEM_ERROR") + e.getMessage());
		this.lastError = rst;

		writer.write(rst.toString());
		LOGGER.error(getMessage("SYSTEM_ERROR") + "{}", e.getMessage(), e);
	}

	/**
	 * 返回chatManager对象
	 * 
	 * @return the chatManager
	 */
	public ChatManagerBase getChatManager() {
		return chatManager;
	}

	/**
	 * 返回最后的错误信息
	 * 
	 * @return the lastError
	 */
	public JSONObject getLastError() {
		return lastError;
	}
}
