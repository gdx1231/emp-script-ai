package com.gdxsoft.ai.providers;

import java.io.PrintWriter;

import org.json.JSONObject;

/**
 * 默认的输出SSE（Server-Sent Events）处理类
 */
public class DefaultOutEvents implements IOutEvents {
	private String lang = "zhcn"; // 默认语言为中文
	private String name;
	
	private int messageCount = 0;
	private String line;
	private JSONObject contenJson;
	@Override
	public void outEvent(String msg, PrintWriter writer) {
		writer.println("data: " + msg.toString() + "\n\n");
		writer.flush();
	}
	/**
	 * @return the messageCount
	 */
	public int getMessageCount() {
		return messageCount;
	}
	/**
	 * @param messageCount the messageCount to set
	 */
	public void setMessageCount(int messageCount) {
		this.messageCount = messageCount;
	}
	/**
	 * @return the line
	 */
	public String getLine() {
		return line;
	}
	/**
	 * @param line the line to set
	 */
	public void setLine(String line) {
		this.line = line;
	}
	 
	/**
	 * @return the contenJson
	 */
	public JSONObject getContenJson() {
		return contenJson;
	}
	/**
	 * @param contenJson the contenJson to set
	 */
	public void setContenJson(JSONObject contenJson) {
		this.contenJson = contenJson;
	}
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * @return the lang
	 */
	public String getLang() {
		return lang;
	}
	/**
	 * @param lang the lang to set
	 */
	public void setLang(String lang) {
		this.lang = lang;
	}

}
