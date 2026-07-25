package com.gdxsoft.ai.export;

import org.json.JSONObject;

import com.gdxsoft.ai.ChatManagerDb;
import com.gdxsoft.ai.modes.Action;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 定义业务动作接口，例如根据 AI 输出执行业务逻辑，或生成 Prompt 内容。
 */
public interface IAction {
	/**
	 * 设置当前 Action 的 XML 配置（由框架调用）。
	 */
	default void setAction(Action action) {
	}

	/**
	 * 获取当前 Action 的 XML 配置。
	 */
	default Action getAction() {
		return null;
	}

	JSONObject doAction(RequestValue rv, String fullText);

	String createPrompt(RequestValue rv, String dbConfigName) throws Exception;

	default void setChatManagerDb(ChatManagerDb db) {
	};

	default ChatManagerDb getChatManagerDb() {
		return null;
	}
}
