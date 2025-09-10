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
			reqData.userMessage(chatManager.getPrompt());
			chatManager.addAiChatMsg(chatManager.getPrompt(), "user", false);
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
