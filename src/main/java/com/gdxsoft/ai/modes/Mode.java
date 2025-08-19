package com.gdxsoft.ai.modes;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gdxsoft.ai.export.IAction;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UObjectValue;

// Class to represent a Mode
public class Mode {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Mode.class);
	private String name;
	private String description;
	// Sampling parameters for AI generation
	private double temperature = 1.0; // default
	private double topP = 1.0; // default
	// Whether to enable provider-specific "thinking" capability if supported
	private boolean thinking = false; // default false
	private List<Step> steps;
	private List<SqlQuery> sqlQueries;
	private List<Action> actions;

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
	public void createStepPrompts(Step step, String dbConfigName, RequestValue rv) throws Exception {
		for (int i = 0; i < step.getPrompts().size(); i++) {
			Prompt prompt = step.getPrompts().get(i);
			this.createStepPrompt(prompt, dbConfigName, rv);
		}
	}
/**
	 * 创建步骤的单个Prompt提示内容
	 * @param dbConfigName
	 * @param rv
	 * @param prompt
	 * @throws Exception
	 */
	public void createStepPrompt(Prompt prompt, String dbConfigName, RequestValue rv) throws Exception {
		 boolean isSqlPrompt = this.createStepPromptBySql(prompt, dbConfigName, rv);
		 if(isSqlPrompt){
			return;
		 }
		 boolean isActionPrompt = this.createStepPromptByAction(prompt, dbConfigName, rv);
		 LOGGER.info("isActionPrompt: " + isActionPrompt);
	}
	/**
	 * 创建步骤的单个Prompt提示内容（通过Prompt.sqlRef）
	 * @param prompt
	 * @param dbConfigName
	 * @param rv
	 * @return
	 * @throws Exception
	 */
	private boolean createStepPromptBySql(Prompt prompt, String dbConfigName, RequestValue rv) throws Exception{
		String sqlRef = prompt.getSqlRef();
		if (StringUtils.isBlank(sqlRef)) {
			return false;
		}

		SqlQuery sqlQuery = findSqlQueryByRef(sqlRef);
		if (sqlQuery == null) {
			throw new Exception("SQL query not found for reference: " + sqlRef);
		}

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
	 * @param prompt
	 * @param dbConfigName
	 * @param rv
	 * @return
	 * @throws Exception
	 */
	private boolean createStepPromptByAction(Prompt prompt, String dbConfigName, RequestValue rv) throws Exception{
		String actionName = prompt.getAction();
		if (StringUtils.isBlank(actionName)) {
			return false;
		}
		 Action action = this.getAction(actionName);
		String actionClassName = action.getClassName();
		System.out.println("加载 actionName=" + actionName + ", 类名：" + actionClassName);

		UObjectValue uv = new UObjectValue();
		// IExport exporter = new pf2023.AiModeEnqJny();
		IAction promptAction = (IAction) uv.loadClass(actionClassName, null);
		String result = promptAction.createPrompt(rv, dbConfigName);
		prompt.setContent(result );
		return true;
	}

	

	private SqlQuery findSqlQueryByRef(String sqlRef) {
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

	public double getTemperature() {
		return temperature;
	}

	public double getTopP() {
		return topP;
	}

	public boolean isThinking() {
		return thinking;
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

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	public void setTopP(double topP) {
		this.topP = topP;
	}

	public void setThinking(boolean thinking) {
		this.thinking = thinking;
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
	 * Create a deep copy of current Mode, including steps/prompts/sqlQueries/actions
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

		Mode copy = new Mode(this.name, this.description, stepsCopy, sqlsCopy, actionsCopy);
		copy.setTemperature(this.temperature);
		copy.setTopP(this.topP);
		copy.setThinking(this.thinking);
		return copy;
	}

	// Parse an XML <mode> element to a Mode instance
	public static Mode parseMode(Element root) {
		String modeName = root.getAttribute("name");
		String modeDescription = root.getAttribute("description");
		String temperatureAttr = root.getAttribute("temperature");
		String topPAttr = root.getAttribute("topP");
		String thinkingAttr = root.getAttribute("thinking");

		// Parse steps
		List<Step> steps = new ArrayList<>();
		NodeList stepNodes = root.getElementsByTagName("step");
		for (int i = 0; i < stepNodes.getLength(); i++) {
			Element stepElement = (Element) stepNodes.item(i);
			String stepName = stepElement.getAttribute("name");
			String stepDescription = stepElement.getAttribute("description");
			String stepAction = stepElement.getAttribute("action");
			String actionSqlRef = stepElement.getAttribute("actionSqlRef");
			String stepStreamAttr = stepElement.getAttribute("stream");
			boolean stepStream = true; // default true
			if (stepStreamAttr != null && stepStreamAttr.trim().length() > 0) {
				stepStream = Boolean.parseBoolean(stepStreamAttr.trim());
			}

			// Parse prompts within step
			List<Prompt> prompts = new ArrayList<>();
			NodeList promptsNode = stepElement.getElementsByTagName("prompts");
			if (promptsNode.getLength() > 0) {
				prompts = parsePrompts((Element) promptsNode.item(0));
			}
			Step step;
			if (stepAction != null && stepAction.length() > 0) {
				step = new Step(stepName, stepDescription, prompts, stepAction);
			} else {
				step = new Step(stepName, stepDescription, prompts);
			}
			step.setStream(stepStream);
			if (actionSqlRef != null && actionSqlRef.trim().length() > 0) {
				step.setActionSqlRef(actionSqlRef.trim());
			}
			steps.add(step);
		}

		// Parse SQL queries
		List<SqlQuery> sqlQueries = new ArrayList<>();
		NodeList sqlsNode = root.getElementsByTagName("sqls");
		if (sqlsNode.getLength() > 0) {
			sqlQueries = parseSqlQueries((Element) sqlsNode.item(0));
		}

		// Parse actions
		List<Action> actions = new ArrayList<>();
		NodeList actionsNodes = root.getElementsByTagName("actions");
		if (actionsNodes.getLength() > 0) {
			Element actionsElement = (Element) actionsNodes.item(0);
			NodeList actionNodes = actionsElement.getElementsByTagName("action");
			for (int i = 0; i < actionNodes.getLength(); i++) {
				Element actionElement = (Element) actionNodes.item(i);
				String actionName = actionElement.getAttribute("name");
				String actionDescription = actionElement.getAttribute("description");
				String className = actionElement.getAttribute("class");
				actions.add(new Action(actionName, actionDescription, className));
			}
		}

		Mode mode = new Mode(modeName, modeDescription, steps, sqlQueries, actions);
		if (temperatureAttr != null && temperatureAttr.trim().length() > 0) {
			try {
				mode.setTemperature(Double.parseDouble(temperatureAttr.trim()));
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid temperature attribute: {}", temperatureAttr);
			}
		}
		if (topPAttr != null && topPAttr.trim().length() > 0) {
			try {
				mode.setTopP(Double.parseDouble(topPAttr.trim()));
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid topP attribute: {}", topPAttr);
			}
		}
		if (thinkingAttr != null && thinkingAttr.trim().length() > 0) {
			mode.setThinking(Boolean.parseBoolean(thinkingAttr.trim()));
		}
		return mode;
	}

	// Helper methods for parsing XML content
	private static List<Prompt> parsePrompts(Element promptsElement) {
		List<Prompt> prompts = new ArrayList<>();
		NodeList promptNodes = promptsElement.getElementsByTagName("prompt");
		for (int i = 0; i < promptNodes.getLength(); i++) {
			Element promptElement = (Element) promptNodes.item(i);
			Prompt prompt = parsePrompt(promptElement);
			prompts.add(prompt);
		}
		return prompts;
	}

	private static Prompt parsePrompt(Element promptElement) {
		String promptName = promptElement.getAttribute("name");
		String role = promptElement.getAttribute("role");
		String description = promptElement.getAttribute("description");
		String sqlRef = promptElement.getAttribute("sqlRef");
		String dataType = promptElement.getAttribute("dataType");
		String prefix = promptElement.getAttribute("prefix");
		String content = getElementContent(promptElement);

		String dataGroupField = promptElement.getAttribute("dataGroupField");
		String action = promptElement.getAttribute("action");
		String showInChatAttr = promptElement.getAttribute("showInChat");
		Prompt p = new Prompt(promptName, role, description, sqlRef, dataType, prefix, content, action);
		if (dataGroupField != null && dataGroupField.length() > 0) {
			p.setDataGroupField(dataGroupField);
		}
		if (showInChatAttr != null && showInChatAttr.trim().length() > 0) {
			p.setShowInChat(Boolean.parseBoolean(showInChatAttr.trim()));
		}
		return p;
	}

	private static SqlQuery parseSqlQuery(Element sqlElement) {
		String sqlName = sqlElement.getAttribute("name");
		String sqlDescription = sqlElement.getAttribute("description");
		String sqlContent = getElementContent(sqlElement);
		return new SqlQuery(sqlName, sqlDescription, sqlContent);
	}

	private static List<SqlQuery> parseSqlQueries(Element sqlsElement) {
		List<SqlQuery> sqlQueries = new ArrayList<>();
		NodeList sqlNodes = sqlsElement.getElementsByTagName("sql");
		for (int i = 0; i < sqlNodes.getLength(); i++) {
			Element sqlElement = (Element) sqlNodes.item(i);
			SqlQuery sql = parseSqlQuery(sqlElement);
			sqlQueries.add(sql);
		}
		return sqlQueries;
	}

	// Helper method to get content of an element, handling CDATA
	private static String getElementContent(Element element) {
		StringBuilder content = new StringBuilder();
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() == Node.CDATA_SECTION_NODE || node.getNodeType() == Node.TEXT_NODE) {
				content.append(node.getTextContent().trim());
			}
		}
		return content.toString();
	}
}