package com.gdxsoft.ai.app.chatroom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.gdxsoft.easyweb.utils.UObjectValue;
import com.gdxsoft.easyweb.utils.UXml;
import com.gdxsoft.easyweb.websocket.EwaWebSocketBus;
import com.gdxsoft.easyweb.websocket.IHandleMsg;

/**
 * 通过配置文件加载消息处理的方法
 */
public class LoadHandleMessage {
	private static final String path = "/HandleMessage.xml";
	private static final Logger LOGGER = LoggerFactory.getLogger(LoadHandleMessage.class);
	private static volatile Map<String, String> CLASS_MAP;
	private static final Map<String, Integer> HASH_MAP = new ConcurrentHashMap<>();

	/**
	 * 获取方法对应的类
	 */
	public static IHandleMsg getInstance(String methodName, EwaWebSocketBus webSocket, JSONObject obj) {
		checkClassMap();

		String name1 = methodName.trim().toUpperCase();
		if (!CLASS_MAP.containsKey(name1)) {
			return null;
		}

		String className = CLASS_MAP.get(name1);

		UObjectValue ov = new UObjectValue();
		Object[] constructorParameters = new Object[2];
		constructorParameters[0] = webSocket;
		constructorParameters[1] = obj;
		Object classLoaded = ov.loadClass(className, constructorParameters);

		if (classLoaded == null) {
			return null;
		}
		IHandleMsg instance = (IHandleMsg) classLoaded;

		return instance;
	}

	private static synchronized void checkClassMap() {
		try {
			String xmlContent = Resources.getResourceContent(path);
			int xmlHashCode = xmlContent.hashCode();
			if (!HASH_MAP.containsKey(path) || HASH_MAP.get(path) != xmlHashCode) {
				initClassMap(xmlContent);
				HASH_MAP.put(path, xmlHashCode);
			}
		} catch (Exception e) {
			LOGGER.error("加载 HandleMessage 配置失败", e);
		}
	}

	private static void initClassMap(String xmlContent) {
		Document doc = UXml.asDocument(xmlContent);

		Map<String, String> newMap = new ConcurrentHashMap<>();
		NodeList nl = doc.getElementsByTagName("HandleMessage");
		for (int i = 0; i < nl.getLength(); i++) {
			Element ele = (Element) nl.item(i);
			String method = ele.getAttribute("Method").toUpperCase().trim();
			String className = ele.getAttribute("Class").trim();
			newMap.put(method, className);
			LOGGER.info("{}: {}", method, className);
		}
		CLASS_MAP = newMap;
	}
}
