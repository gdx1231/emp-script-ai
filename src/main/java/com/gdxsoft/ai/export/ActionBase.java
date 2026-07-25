package com.gdxsoft.ai.export;

import java.io.PrintWriter;

import org.json.JSONObject;

import com.gdxsoft.ai.ChatManagerDb;
import com.gdxsoft.ai.request.IOutEvents;
import com.gdxsoft.easyweb.script.RequestValue;

public class ActionBase {
	private com.gdxsoft.ai.modes.Action action;

	private String actionApiKey;
	private String actionApiUrl;
	private String actionApiModel;
	private ChatManagerDb db;
	private String actionApiOwnerId;
	private IOutEvents outEvents;
	private PrintWriter writer;

	public void setOutEvents(IOutEvents outEvents) { this.outEvents = outEvents; }
	public IOutEvents getOutEvents() { return outEvents; }

	public void setWriter(PrintWriter writer) { this.writer = writer; }

	public void outEvent(Object msg) {
		if (outEvents != null) {
			outEvents.outEvent(msg.toString(), writer);
		}
	}

	public void setChatManagerDb(ChatManagerDb db) {
		this.db = db;
	};

	public ChatManagerDb getChatManagerDb() {
		return db;
	}

	public void loadApiConfig(RequestValue rv) throws Exception {
		if (this.action == null)
			throw new Exception("The action is null");
		if (this.action.getAiProvider() == null || this.action.getAiProvider().length() == 0) {
			throw new Exception("The AiProvider is null or blank");
		}
		if (this.action.getAiModel() == null || this.action.getAiModel().length() == 0) {
			throw new Exception("The AiModel is null or blank");
		}
		JSONObject rst = db.checkProviderAndModel(this.action.getAiProvider(), this.action.getAiModel(),
				actionApiOwnerId);
		if (rst.optBoolean("RST")) {
			this.actionApiKey = rst.optString("apiKey");
			this.actionApiUrl = rst.optString("apiUrl");
		} else {
			throw new Exception(rst.toString());
		}
	}

	public void setAction(com.gdxsoft.ai.modes.Action action) {
		this.action = action;
	}

	public com.gdxsoft.ai.modes.Action getAction() {
		return action;
	}

	/**
	 * @return the actionApiKey
	 */
	public String getActionApiKey() {
		return actionApiKey;
	}

	/**
	 * @param actionApiKey the actionApiKey to set
	 */
	public void setActionApiKey(String actionApiKey) {
		this.actionApiKey = actionApiKey;
	}

	/**
	 * @return the actionApiUrl
	 */
	public String getActionApiUrl() {
		return actionApiUrl;
	}

	/**
	 * @param actionApiUrl the actionApiUrl to set
	 */
	public void setActionApiUrl(String actionApiUrl) {
		this.actionApiUrl = actionApiUrl;
	}

	/**
	 * @return the actionApiModel
	 */
	public String getActionApiModel() {
		return actionApiModel;
	}

	/**
	 * @param actionApiModel the actionApiModel to set
	 */
	public void setActionApiModel(String actionApiModel) {
		this.actionApiModel = actionApiModel;
	}

	public String getActionApiOwnerId() {
		return actionApiOwnerId;
	}

	public void setActionApiOwnerId(String actionApiOwnerId) {
		this.actionApiOwnerId = actionApiOwnerId;
	}

	/**
	 * 保存消息到 AI_CHAT_MSG 表，供 Action 实现类记录 API 调用日志等。
	 */
	public long saveMessage(RequestValue rv, String msg, String role) {
		if (db == null || action == null) return -1;
		try {
			long aiId = Long.parseLong(rv.s("ai_id"));
			return db.addMessage(aiId, msg, role,
					null, // stepName
					null, // promptName
					action.getName(),
					action.getClassName(),
					(short) 0, true, false);
		} catch (Exception e) {
			return -1;
		}
	}

}
