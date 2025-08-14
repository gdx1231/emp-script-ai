package com.gdxsoft.ai.providers;

import java.io.PrintWriter;

import org.json.JSONObject;

public interface IOutEvents {
	 String getName() ;
	/**
	 * @param name the name to set
	 */
	 void setName(String name) ;
	 int getMessageCount();
	/**
	 * @param messageCount the messageCount to set
	 */
	 void setMessageCount(int messageCount);
	/**
	 * @return the line
	 */
	 String getLine();
	/**
	 * @param line the line to set
	 */
	 void setLine(String line);
	 
	/**
	 * @return the contenJson
	 */
	 JSONObject getContenJson();
	/**
	 * @param contenJson the contenJson to set
	 */
	 void setContenJson(JSONObject contenJson);
	
	/**
	 * 输出事件
	 * 
	 * @param msg    消息内容
	 * @param writer 输出流
	 */
	void outEvent(String msg, PrintWriter writer);
}
