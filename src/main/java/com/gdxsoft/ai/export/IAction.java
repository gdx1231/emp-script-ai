package com.gdxsoft.ai.export;

import org.json.JSONObject;

import com.gdxsoft.easyweb.script.RequestValue;

public interface IAction {
	/**
	 * 执行动作
	 * @param rv
	 * @param fullText
	 * @return
	 */
	JSONObject doAction(RequestValue rv, String fullText);
	/**
	 * 创建提示内容
	 * @param rv
	 * @return
	 * @throws Exception
	 */
	String createPrompt(RequestValue rv, String dbConfigName ) throws Exception;
}

