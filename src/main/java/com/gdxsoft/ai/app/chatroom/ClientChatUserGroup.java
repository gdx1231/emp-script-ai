package com.gdxsoft.ai.app.chatroom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientChatUserGroup {
	public final static Map<Long, Map<String, Boolean>> TOPIC_USERS = new ConcurrentHashMap<>();
	private static Logger LOGGER = LoggerFactory.getLogger(ClientChatUserGroup.class);

	public static Map<String, Boolean> getGroup(long chatRoomId) {
		return TOPIC_USERS.get(chatRoomId);
	}

	/**
	 * 添加活动用户进入房间组
	 * @param chatRoomId
	 * @param unid
	 */
	public static void addUserToTopicGroup(long chatRoomId, String unid) {

		if (!TOPIC_USERS.containsKey(chatRoomId)) {
			TOPIC_USERS.put(chatRoomId, new ConcurrentHashMap<>());
			LOGGER.debug("创建新分组chatRoomId: {}", chatRoomId);
		}
		Map<String, Boolean> map = TOPIC_USERS.get(chatRoomId);

		if (!map.containsKey(unid)) {
			map.put(unid, true);
			LOGGER.debug("从分组 chatRoomId: {}， 添加用户：{}，用户数量：{}", chatRoomId, unid, map.size());
		}

	}
}
