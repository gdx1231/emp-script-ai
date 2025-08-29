package com.gdxsoft.ai;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

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
import static com.gdxsoft.ai.ChatManagerI18nConstants.*;

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
		this.addAiChatMsg(result.toString(), "agent");

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

		// mode.createStepPrompts(step, "", g_rv);
		for (int i = 0; i < step.getPrompts().size(); i++) {
			Prompt p = step.getPrompts().get(i);
			if (!"system".equalsIgnoreCase(p.getRole()) && promptSet.size() > 0 && !promptSet.contains(p.getName())) {
				// 如果不包含在请求的提示词中，则跳过
				continue;
			}
			String key = step.getName() + "|" + p.getName();
			if (existsMessages.containsKey(key)) {
				// 已经存在该消息，不再添加
				continue;
			}
			mode.createStepPrompt(p, "", rv, refHeaders);
			String role = p.getRole();
			if (StringUtils.isBlank(role)) {
				role = "user";
			}
			String promptContent = p.getContent();
			if (!StringUtils.isBlank(p.getPrefix())) {
				promptContent = p.getPrefix() + promptContent;
			}
			reqData.addMessage(promptContent, role);

			// 记录到数据库中
			rv.addOrUpdateValue("AIM_PROMPT_NAME", p.getName());
			this.addAiChatMsg(promptContent, role);
			rv.addOrUpdateValue("AIM_PROMPT_NAME", null);

			// 如果是用户角色，且需要在聊天中显示
			if (p.isShowInChat()) {
				JSONObject promptMsg = UJSon.rstTrue("");
				promptMsg.put("content", "```prompt\n" + promptContent + "\n```\n\n");

				promptMsg.put("prompt", p.getName());
				this.outEvent(promptMsg.toString());
			}
		}

	}

	/**
	 * 添加历史消息到请求数据中
	 * 
	 * @param reqData 请求数据对象
	 * @param rv      请求值对象
	 */
	public void appendPreviousMessages(IRequestData reqData, Map<String, Boolean> existsMessages) throws Exception {
		String existsSql = "select * from AI_CHAT_MSG where ai_id=@ai_id and AIM_ACTION is null order by AIM_ID";
		DTTable tbMsg = DTTable.getJdbcTable(existsSql, rv);
		for (int i = 0; i < tbMsg.getCount(); i++) {
			String msg = tbMsg.getCell(i, "AIM_MSG").toString();
			String role = tbMsg.getCell(i, "AIM_ROLE").toString();
			String step = tbMsg.getCell(i, "AIM_STEP").toString();
			String promptName = tbMsg.getCell(i, "AIM_PROMPT_NAME").toString();
			String key = step + "|" + promptName;
			existsMessages.put(key, true);

			reqData.addMessage(msg, role);
		}
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
		LOGGER.info(getText(LogMessages.AI_CHAT_RECORD), chat.toString(2));

		rv.addOrUpdateValue("ai_id", this.aiId, "bigint", 100);

		// chat 包含 AI_ID, AI_STEP_PREV
		String stepPrev = this.getAiStepPrev() == null ? "----gdx----!!-" : this.getAiStepPrev();
		rv.addOrUpdateValue("AI_STEP_PREV", stepPrev);

		JSONObject rst = UJSon.rstTrue();
		rst.put("api_url", this.apiUrl);
		rst.put("api_key", this.apiKey);
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
		String sql = "select AI_ID, AI_CUR_STEP as AI_STEP_PREV, AI_UID from ai_chat where adm_id=@g_adm_id and ai_uid=@request_id";
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
		sbIns.append("    AI_UID, AI_PROVIDER, AI_MODEL, AI_THINKING, AI_STREAM, AI_CUR_STEP\n");
		sbIns.append("  , AI_MODE, AI_MAX_TOKEN, AI_CDATE, AI_MDATE, ADM_ID, USR_ID, SUP_ID\n");
		sbIns.append(") VALUES(\n");
		sbIns.append("    @request_id, @AI_PROVIDER, @AI_MODEL, " + (this.aiThinking?1:0) + ", " + (this.aiStream?1:0)
				+ ", @AIM_STEP\n");
		sbIns.append("  , @MODE, @AI_MAX_TOKEN, @sys_DATE, @sys_DATE, @g_ADM_ID, @G_WEB_USR_ID, @g_SUP_ID\n");
		sbIns.append(")");

		DataConnection.insertAndReturnAutoIdLong(sbIns.toString(), dbConfigName, rv);
		tb = DTTable.getJdbcTable(sql, dbConfigName, rv);

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
	public long addAiChatMsg(String msg, String role) {
		rv.addOrUpdateValue("AIM_MSG", msg);
		rv.addOrUpdateValue("AIM_ROLE", role);
		if (!"assistant".equals(role)) {
			rv.addOrUpdateValue("AIM_TIME_END", new Date(), "date", 100);
		}

		String sql = "INSERT INTO AI_CHAT_MSG( AI_ID, AIM_MSG, AIM_ROLE, AIM_TIME_BEGIN, AIM_TIME_END, AIM_STEP, AIM_ACTION, AIM_ACTION_CLASS, AIM_PROMPT_NAME)"
				+ " VALUES(@ai_id, @AIM_MSG, @AIM_ROLE, @sys_date, @AIM_TIME_END, @AIM_STEP, @AIM_ACTION, @AIM_ACTION_CLASS, @AIM_PROMPT_NAME)";

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
	public void updateAiChatMsg(long aimId, String msg, RequestValue rv) {
		rv.addOrUpdateValue("AIM_MSG", msg);
		rv.addOrUpdateValue("AIM_TIME_END", new Date(), "date", 100);

		String sql = "update AI_CHAT_MSG set AIM_MSG = @AIM_MSG, AIM_TIME_END= @AIM_TIME_END where AIM_ID = " + aimId;

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

}
