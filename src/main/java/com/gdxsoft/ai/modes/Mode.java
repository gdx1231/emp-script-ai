package com.gdxsoft.ai.modes;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;

import com.gdxsoft.ai.export.IAction;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UJSon;
import com.gdxsoft.easyweb.utils.UNet;
import com.gdxsoft.easyweb.utils.UObjectValue;

/**
 * 表示一个模式（<mode>），包含步骤、SQL 片段、动作与采样参数。
 * <p>
 * Represents a mode (<mode>), containing steps, SQL snippets, actions and
 * sampling params.
 */
public class Mode {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Mode.class);
	private String name;
	private String description;
	// Sampling parameters for AI generation
	private double temperature = 1.0; // default
	private double topP = 1.0; // default
	// Whether to enable provider-specific "thinking" capability if supported
	private boolean thinking = false; // default false
	// Response format for mode outputs. Currently only 'text/json_object' is
	// supported.
	private String responseFormat;
	private List<Step> steps;
	private List<SqlQuery> sqlQueries;
	private List<Action> actions;
	private List<Api> apis;
	// UI HTML outputs: welcome (new session) and complete (after itinerary generation)
	private String uiWelcome;
	private String uiComplete;
	private String uiCompleteTest;
	// Parameter check definitions for input validation
	private List<ParamCheck> paramChecks;
	// Debug output control: whether to show technical details like API calls
	private boolean debugOutput = false; // default false, hide technical details from users
	// Maximum number of history messages to include in each turn (default 30)
	private int maxHistoryMessages = 30;
	// Maximum total token count for history messages, in thousands (default 100 = 100K tokens)
	private int maxHistoryTokensK = 100;

	/**
	 * 获取模式中的步骤
	 *
	 * @param stepName
	 * @return
	 */
	public Step getStep(String stepName) {
		for (Step step : steps) {
			if (step.getName().equalsIgnoreCase(stepName)) {
				return step;
			}
		}
		return null;
	}

	/**
	 * 从 AI_CHAT_PARAMS 表加载已保存的参数到 RequestValue 中
	 * 用于 SQL 执行时的参数替换（如 @city_ids_split, @g_sup_id 等）
	 */
	private void loadParamsFromAiChatParams(RequestValue rv) {
		String requestId = rv.s("request_id");
		if (StringUtils.isBlank(requestId)) {
			return;
		}
		try {
			String sql = "SELECT AIP_NAME, AIP_VAL FROM AI_CHAT_PARAMS p "
				+ "INNER JOIN AI_CHAT c ON p.AI_ID = c.AI_ID "
				+ "WHERE c.AI_UID = @request_id "
				+ "AND AIP_VAL IS NOT NULL AND AIP_VAL <> ''";
			DTTable tb = DTTable.getJdbcTable(sql, rv);
			for (int i = 0; i < tb.getCount(); i++) {
				String name = tb.getCell(i, "AIP_NAME").toString();
				String val = tb.getCell(i, "AIP_VAL").toString();
				if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(val)) {
					rv.addOrUpdateValue(name, val);
				}
			}
		} catch (Exception e) {
			// 参数表不存在或查询失败时静默忽略，不影响正常流程
			LOGGER.debug("Failed to load params from AI_CHAT_PARAMS for request_id={}", requestId, e);
		}
	}

	/**
	 * 获取模式中的步骤
	 * 
	 * @param index
	 * @return
	 */
	public Step getStep(int index) {
		if (index < 0 || index >= this.getSteps().size()) {
			return null;
		} else {
			return this.getSteps().get(index);
		}
	}

	/**
	 * 创建步骤的完整文本内容
	 * 
	 * @param step
	 * @param dbConfigName
	 * @param rv
	 * @return
	 * @throws Exception
	 */
	public String createStepActionRefFulleText(Step step, String dbConfigName, RequestValue rv) throws Exception {
		String actionSqlRef = step.getActionSqlRef();
		if (StringUtils.isBlank(actionSqlRef)) {
			return null; // No action SQL reference, nothing to do
		}

		SqlQuery sqlQuery = findSqlQueryByRef(actionSqlRef);
		if (sqlQuery == null) {
			throw new Exception("SQL query not found for reference: " + actionSqlRef);
		}

		String sql = sqlQuery.getContent();
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (!tb.isOk()) {
			throw new Exception("Error executing SQL query: " + sql);
		}

		StringBuilder fullText = new StringBuilder();
		for (int i = 0; i < tb.getCount(); i++) {
			String text = tb.getCell(i, "full_text").toString();
			if (text != null) {
				fullText.append(text);
			}
		}
		if (fullText.length() == 0) {
			return null; // No content found
		} else {
			return fullText.toString();
		}
	}

	/**
	 * 创建步骤的Prompts提示内容
	 * 
	 * @param step
	 * @param dbConfigName
	 * @param rv
	 * @throws Exception
	 */
	public void createStepPrompts(Step step, String dbConfigName, RequestValue rv, Map<String, String> refHeaders)
			throws Exception {
		for (int i = 0; i < step.getPrompts().size(); i++) {
			Prompt prompt = step.getPrompts().get(i);
			this.createStepPrompt(prompt, dbConfigName, rv, refHeaders);
		}
	}

	/**
	 * 创建步骤的单个Prompt提示内容
	 * 
	 * @param dbConfigName
	 * @param rv
	 * @param prompt
	 * @throws Exception
	 */
	public void createStepPrompt(Prompt prompt, String dbConfigName, RequestValue rv, Map<String, String> refHeaders)
			throws Exception {
		boolean isSqlPrompt = this.createStepPromptBySql(prompt, dbConfigName, rv);
		if (isSqlPrompt) {
			return;
		}
		boolean isActionPrompt = this.createStepPromptByAction(prompt, dbConfigName, rv);
		if (isActionPrompt) {
			return;
		}
		boolean isApiPrompt = this.createStepPromptByApi(prompt, rv, refHeaders);
		LOGGER.info("isApiPrompt: " + isApiPrompt);
	}

	/**
	 * 创建步骤的单个Prompt提示内容（通过Prompt.sqlRef）
	 * 
	 * @param prompt
	 * @param dbConfigName
	 * @param rv
	 * @return
	 * @throws Exception
	 */
	public boolean createStepPromptBySql(Prompt prompt, String dbConfigName, RequestValue rv) throws Exception {
		String sqlRef = prompt.getSqlRef();
		if (StringUtils.isBlank(sqlRef)) {
			return false;
		}

		SqlQuery sqlQuery = findSqlQueryByRef(sqlRef);
		if (sqlQuery == null) {
			throw new Exception("SQL query not found for reference: " + sqlRef);
		}

		// 从 AI_CHAT_PARAMS 加载已保存的参数到 rv（供 SQL 中使用）
		loadParamsFromAiChatParams(rv);

		String sql = sqlQuery.getContent();
		DTTable tb = DTTable.getJdbcTable(sql, dbConfigName, rv);
		if (!tb.isOk()) {
			throw new Exception("Error executing SQL query: " + sql);
		}

		if ("json".equalsIgnoreCase(prompt.getDataType())) {
			String groupField = prompt.getDataGroupField();
			if (groupField != null && groupField.trim().length() > 0) {
				org.json.JSONObject grouped = tb.toJSONObjectGroup(groupField);
				prompt.setContent(grouped.toString());
			} else {
				prompt.setContent(tb.toJSONArray().toString());
			}
		} else if ("csv".equalsIgnoreCase(prompt.getDataType())) {
			prompt.setContent(tb.toCSV());
		} else if ("xml".equalsIgnoreCase(prompt.getDataType())) {
			prompt.setContent(tb.toXml(rv));
		}

		return true;
	}

	/**
	 * 创建步骤的单个Prompt提示内容（通过Prompt.action）
	 * 
	 * @param prompt
	 * @param dbConfigName
	 * @param rv
	 * @return
	 * @throws Exception
	 */
	public boolean createStepPromptByAction(Prompt prompt, String dbConfigName, RequestValue rv) throws Exception {
		String actionName = prompt.getAction();
		if (StringUtils.isBlank(actionName)) {
			return false;
		}
		Action action = this.getAction(actionName);
		if (action == null) {
			throw new Exception("Action not found for name: " + actionName);
		}
		String actionClassName = action.getClassName();
		LOGGER.info("加载 actionName=" + actionName + ", 类名：" + actionClassName);

		UObjectValue uv = new UObjectValue();
		// IExport exporter = new pf2023.AiModeEnqJny();
		IAction promptAction = (IAction) uv.loadClass(actionClassName, null);
		String result = promptAction.createPrompt(rv, dbConfigName);
		prompt.setContent(result);
		return true;
	}

	public String createApiUrl(Api api, RequestValue rv) {
		String url = rv.replaceParameters(api.getUrl());
		if (api.getParameters() != null && api.getParameters().trim().length() > 0) {
			String paras = rv.replaceParameters(api.getParameters().trim());
			url = url + (url.indexOf("?") > 0 ? "&" : "?") + paras;
		}
		return url;

	}

	/**
	 * 根据API创建步骤提示
	 * 
	 * @param prompt     提示对象
	 * @param rv         请求参数容器
	 * @param refHeaders 引用的Http headers
	 * @return 创建成功返回true，否则返回false
	 */
	public boolean createStepPromptByApi(Prompt prompt, RequestValue rv, Map<String, String> refHeaders)
			throws Exception {
		String apiName = prompt.getApi();
		if (StringUtils.isBlank(apiName)) {
			return false;
		}
		Api api = this.getApi(apiName);
		if (api == null) {
			throw new Exception("API not found for name: " + apiName);
		}

		String url = this.createApiUrl(api, rv);

		LOGGER.info("调用 API: " + apiName + ", URL: " + url);

		UNet net = new UNet();
		if (api.getTimeout() > 0) {
			net.setTimeout(api.getTimeout());
		}
		if (api.isRefRequest() && refHeaders != null) {
			for (String key : refHeaders.keySet()) {
				if ("content-length".equalsIgnoreCase(key)) {
					continue; // content-length由UNet自动处理
				}
				if ("origin".equalsIgnoreCase(key) || "host".equalsIgnoreCase(key)
						|| "connection".equalsIgnoreCase(key)) {
					continue; // origin, host, connection等头部通常不需要在API请求中设置
				}
				net.addHeader(key, refHeaders.get(key));
			}
		}

		for (int i = 0; i < api.getHeaders().size(); i++) {
			ApiHeader field = api.getHeaders().get(i);
			net.addHeader(field.getName(), rv.replaceParameters(field.getValue()));
		}

		Map<String, String> vals = new HashMap<String, String>();
		for (int i = 0; i < api.getForm().size(); i++) {
			ApiField field = api.getForm().get(i);
			vals.put(field.getName(), rv.replaceParameters(field.getValue()));
		}
		String body = api.getBody();
		boolean hasbody = api.getBody() != null && api.getBody().trim().length() > 0;
		String result;

		// net.setIsShowLog(true);
		if (api.getMethod().equalsIgnoreCase("POST")) {
			if (hasbody) {
				result = net.doPost(url, body);
			} else {
				result = net.doPost(url, vals);
			}
		} else if (api.getMethod().equalsIgnoreCase("PUT")) {
			if (hasbody) {
				result = net.doPut(url, body);
			} else {
				result = net.doPut(url, vals);
			}
		} else if (api.getMethod().equalsIgnoreCase("DELETE")) {
			if (hasbody) {
				result = net.doDelete(url, body);
			} else {
				result = net.doDelete(url, vals);
			}
		} else if (api.getMethod().equalsIgnoreCase("PATCH")) {
			if (hasbody) {
				result = net.doPatch(url, body);
			} else {
				result = net.doPatch(url, vals);
			}
		} else {
			result = net.doGet(url);
		}
		String apiCurl = net.createLastCurl();
		prompt.setApiCurl(apiCurl);

		if (net.getLastStatusCode() != 200) {
			result = UJSon.rstFalse(net.getLastErr()).put("HTTP_CODE", net.getLastStatusCode()).toString();
		}

		// 暂时返回一个占位符内容
		prompt.setContent(result);

		return true;
	}

	 
	/**
	 * 根据名称查找 SQL 查询定义
	 *
	 * @param sqlRef SQL 引用名称
	 * @return SqlQuery 对象，未找到返回 null
	 */
	public SqlQuery findSqlQueryByRef(String sqlRef) {
		for (SqlQuery query : sqlQueries) {
			if (sqlRef.equalsIgnoreCase(query.getName())) {
				return query;
			}
		}
		return null;
	}

	public Mode(String name, String description, List<Step> steps, List<SqlQuery> sqlQueries, List<Action> actions) {
		this.name = name;
		this.description = description;
		this.steps = steps;
		this.sqlQueries = sqlQueries;
		this.actions = actions;
		this.apis = new ArrayList<>();
	}

	public Mode(String name, String description, List<Step> steps, List<SqlQuery> sqlQueries, List<Action> actions,
			List<Api> apis) {
		this.name = name;
		this.description = description;
		this.steps = steps;
		this.sqlQueries = sqlQueries;
		this.actions = actions;
		this.apis = apis != null ? apis : new ArrayList<>();
	}

	// Getters
	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public List<Step> getSteps() {
		return steps;
	}

	public List<SqlQuery> getSqlQueries() {
		return sqlQueries;
	}

	public List<Action> getActions() {
		return actions;
	}

	public List<Api> getApis() {
		return apis;
	}

	public double getTemperature() {
		return temperature;
	}

	public double getTopP() {
		return topP;
	}

	public boolean isThinking() {
		return thinking;
	}

	/**
	 * 返回响应格式，目前仅支持 "text"
	 * 
	 * @return responseFormat
	 */
	public String getResponseFormat() {
		return responseFormat;
	}

	// Setters
	public void setName(String name) {
		this.name = name;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setSteps(List<Step> steps) {
		this.steps = steps;
	}

	public void setSqlQueries(List<SqlQuery> sqlQueries) {
		this.sqlQueries = sqlQueries;
	}

	public void setActions(List<Action> actions) {
		this.actions = actions;
	}

	public void setApis(List<Api> apis) {
		this.apis = apis != null ? apis : new ArrayList<>();
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	public void setTopP(double topP) {
		this.topP = topP;
	}

	public void setThinking(boolean thinking) {
		this.thinking = thinking;
	}

	/**
	 * 设置响应格式。返回内容的格式。可选值：{"type": "text"}或{"type": "json_object"}。
	 *
	 * @param responseFormat 格式字符串
	 */
	public void setResponseFormat(String responseFormat) {
		if (responseFormat == null) {
			this.responseFormat = null;
			return;
		}
		// 返回内容的格式。可选值：{"type": "text"}或{"type": "json_object"}。设置为{"type":
		// "json_object"}时会输出标准格式的JSON字符串。使用方法请参见：结构化输出。
		// 如果指定该参数为{"type": "json_object"}，您需要在System Message或User
		// Message中指引模型输出JSON格式，如：“请按照json格式输出。”
		String v = responseFormat.trim();
		if ("text".equalsIgnoreCase(v)) {
			this.responseFormat = "text";
		} else if ("json_object".equalsIgnoreCase(v)) {
			this.responseFormat = "json_object";
		} else {
			// only 'text' is supported; keep existing value and log
			org.slf4j.LoggerFactory.getLogger(Mode.class)
					.warn("Unsupported responseFormat: {}. Only 'text or json_object' is supported.", responseFormat);
		}
	}

	public String getUiWelcome() {
		return uiWelcome;
	}

	public void setUiWelcome(String uiWelcome) {
		this.uiWelcome = uiWelcome;
	}

	public String getUiComplete() {
		return uiComplete;
	}

	public void setUiComplete(String uiComplete) {
		this.uiComplete = uiComplete;
	}

	public String getUiCompleteTest() {
		return uiCompleteTest;
	}

	public void setUiCompleteTest(String uiCompleteTest) {
		this.uiCompleteTest = uiCompleteTest;
	}

	public List<ParamCheck> getParamChecks() {
		return paramChecks;
	}

	public void setParamChecks(List<ParamCheck> paramChecks) {
		this.paramChecks = paramChecks;
	}

	public ParamCheck getParamCheck(String name) {
		if (paramChecks == null)
			return null;
		for (ParamCheck pc : paramChecks) {
			if (pc.getName() != null && pc.getName().equalsIgnoreCase(name)) {
				return pc;
			}
		}
		return null;
	}

	/**
	 * 获取调试输出开关:是否向用户显示技术细节(如API调用信息)
	 * @return true=显示调试信息, false=隐藏(默认)
	 */
	public boolean isDebugOutput() {
		return debugOutput;
	}

	/**
	 * 设置调试输出开关
	 * @param debugOutput true=显示调试信息, false=隐藏(默认)
	 */
	public void setDebugOutput(boolean debugOutput) {
		this.debugOutput = debugOutput;
	}

	/**
	 * 获取历史消息最大条数
	 * @return maxHistoryMessages
	 */
	public int getMaxHistoryMessages() {
		return maxHistoryMessages;
	}

	/**
	 * 设置历史消息最大条数
	 * @param maxHistoryMessages 最大条数
	 */
	public void setMaxHistoryMessages(int maxHistoryMessages) {
		this.maxHistoryMessages = maxHistoryMessages;
	}

	/**
	 * 获取历史消息最大 token 数（单位 K，返回值 = 原始值 * 1000）
	 * @return maxHistoryTokensK * 1000
	 */
	public int getMaxHistoryTokens() {
		return maxHistoryTokensK * 1000;
	}

	/**
	 * 设置历史消息最大 token 数（单位 K）
	 * @param maxHistoryTokensK 最大 token 数，单位 K
	 */
	public void setMaxHistoryTokensK(int maxHistoryTokensK) {
		this.maxHistoryTokensK = maxHistoryTokensK;
	}

	public Action getAction(String actionName) {
		if (actions == null)
			return null;
		for (Action a : actions) {
			if (a.getName() != null && a.getName().equalsIgnoreCase(actionName)) {
				return a;
			}
		}
		return null;
	}

	/**
	 * 根据名称获取API配置
	 * 
	 * @param apiName API名称
	 * @return API配置对象，如果找不到则返回null
	 */
	public Api getApi(String apiName) {
		if (apis == null)
			return null;
		for (Api api : apis) {
			if (api.getName() != null && api.getName().equalsIgnoreCase(apiName)) {
				return api;
			}
		}
		return null;
	}

	/**
	 * Create a deep copy of current Mode, including
	 * steps/prompts/sqlQueries/actions
	 */
	public Mode cloneMode() {
		List<Step> stepsCopy = new ArrayList<>();
		if (this.steps != null) {
			for (Step s : this.steps) {
				List<Prompt> promptsCopy = new ArrayList<>();
				if (s.getPrompts() != null) {
					for (Prompt p : s.getPrompts()) {
						Prompt np = new Prompt(p.getName(), p.getRole(), p.getDescription(), p.getSqlRef(),
								p.getDataType(), p.getPrefix(), p.getContent(), p.getAction());
						if (p.getDataGroupField() != null) {
							np.setDataGroupField(p.getDataGroupField());
						}
						np.setShowInChat(p.isShowInChat());
						if (p.getApi() != null) {
							np.setApi(p.getApi());
						}
						np.setApisCheck(p.isApisCheck());
						promptsCopy.add(np);
					}
				}
				Step ns;
				if (s.getAction() != null && s.getAction().length() > 0) {
					ns = new Step(s.getName(), s.getDescription(), promptsCopy, s.getAction());
				} else {
					ns = new Step(s.getName(), s.getDescription(), promptsCopy);
				}
				ns.setStream(s.isStream());
				ns.setActionSqlRef(s.getActionSqlRef());
				if (s.getApi() != null) {
					ns.setApi(s.getApi());
				}
				ns.setInnerCall(s.isInnerCall());
				ns.setMultiOnlyUserMsg(s.isMultiOnlyUserMsg());
				if (s.getValidateParams() != null) {
					ns.setValidateParams(s.getValidateParams());
				}
				stepsCopy.add(ns);
			}
		}

		List<SqlQuery> sqlsCopy = new ArrayList<>();
		if (this.sqlQueries != null) {
			for (SqlQuery q : this.sqlQueries) {
				sqlsCopy.add(new SqlQuery(q.getName(), q.getDescription(), q.getContent()));
			}
		}

		List<Action> actionsCopy = new ArrayList<>();
		if (this.actions != null) {
			for (Action a : this.actions) {
				actionsCopy.add(new Action(a.getName(), a.getDescription(), a.getClassName()));
			}
		}

		List<Api> apisCopy = new ArrayList<>();
		if (this.apis != null) {
			for (Api api : this.apis) {
				Api newApi = new Api(api.getName(), api.getDescription(), api.getUrl());
				newApi.setMethod(api.getMethod());
				newApi.setTimeout(api.getTimeout());
				newApi.setRefRequest(api.isRefRequest());
				newApi.setParameters(api.getParameters());
				newApi.setKey(api.getKey());
				newApi.setBody(api.getBody());
				// 复制请求头
				if (api.getHeaders() != null) {
					List<ApiHeader> headersCopy = new ArrayList<>();
					for (ApiHeader header : api.getHeaders()) {
						headersCopy.add(new ApiHeader(header.getName(), header.getValue()));
					}
					newApi.setHeaders(headersCopy);
				}
				// 复制表单字段
				if (api.getForm() != null) {
					List<ApiField> formCopy = new ArrayList<>();
					for (ApiField field : api.getForm()) {
						formCopy.add(new ApiField(field.getName(), field.getValue()));
					}
					newApi.setForm(formCopy);
				}
				apisCopy.add(newApi);
			}
		}

		Mode copy = new Mode(this.name, this.description, stepsCopy, sqlsCopy, actionsCopy, apisCopy);
		copy.setTemperature(this.temperature);
		copy.setTopP(this.topP);
		copy.setThinking(this.thinking);
		// copy responseFormat
		copy.setResponseFormat(this.responseFormat);
		copy.setUiWelcome(this.uiWelcome);
		copy.setUiComplete(this.uiComplete);
		copy.setUiCompleteTest(this.uiCompleteTest);
		copy.setDebugOutput(this.debugOutput);
		copy.setMaxHistoryMessages(this.maxHistoryMessages);
		copy.setMaxHistoryTokensK(this.maxHistoryTokensK);
		if (this.paramChecks != null) {
			List<ParamCheck> paramChecksCopy = new ArrayList<>();
			for (ParamCheck pc : this.paramChecks) {
				paramChecksCopy.add(new ParamCheck(pc.getName(), pc.getDes(), pc.getType(),
						pc.getDefaultValue(), pc.getOptions()));
			}
			copy.setParamChecks(paramChecksCopy);
		}
		return copy;
	}

	/**
	 * 解析XML <mode> 元素为Mode实例
	 * 该方法委托给ModeParser处理
	 * 
	 * @param root mode元素
	 * @return Mode对象
	 */
	public static Mode parseMode(Element root) {
		return ModeParser.parseMode(root);
	}

}