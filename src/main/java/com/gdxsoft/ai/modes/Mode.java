package com.gdxsoft.ai.modes;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;

// Class to represent a Mode
public class Mode {
	private String name;
	private String description;
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
	 * 创建步骤的提示内容
	 * 
	 * @param step
	 * @param dbConfigName
	 * @param rv
	 * @throws Exception
	 */
	public void createStepPrompts(Step step, String dbConfigName, RequestValue rv) throws Exception {
		for (int i = 0; i < step.getPrompts().size(); i++) {
			Prompt prompt = step.getPrompts().get(i);
			String sqlRef = prompt.getSqlRef();
			if (StringUtils.isBlank(sqlRef)) {
				continue;
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
		}
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
}