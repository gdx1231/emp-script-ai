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

	public Step getStep(String stepName) {
		for (Step step : steps) {
			if (step.getName().equalsIgnoreCase(stepName)) {
				return step;
			}
		}
		return null;
	}

	public Step getStep(int index) {
		if (index < 0 || index >= this.getSteps().size()) {
			return null; 
		} else {
			return this.getSteps().get(index);
		}
	}

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

			if ("json".equals(prompt.getDataType())) {
				prompt.setContent(tb.toJSONArray().toString());
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

	public Mode(String name, String description, List<Step> steps, List<SqlQuery> sqlQueries) {
		this.name = name;
		this.description = description;
		this.steps = steps;
		this.sqlQueries = sqlQueries;
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
}