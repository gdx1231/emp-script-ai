package com.gdxsoft.ai.app.chatroom;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.script.restful.RestfulResult;
import com.gdxsoft.easyweb.utils.UJSon;
import com.gdxsoft.easyweb.utils.UPath;
import com.gdxsoft.easyweb.websocket.*;


/**
 * WebSocket 聊天消息处理器 — 合并 pool 调度 + 业务逻辑
 *
 * 线程池针对 I/O 密集型场景（RESTful API 调用 + WebSocket 广播）进行优化，
 * 100 并发下可立即处理，避免长时间排队。
 */
public class HandleChatImpl implements Runnable, IHandleMsg {
	private static final Logger LOGGER = LoggerFactory.getLogger(HandleChatImpl.class);

	// ==================== 线程池 ====================

	private static final int CORE_POOL_SIZE = 20;
	private static final int MAX_POOL_SIZE = 200;
	private static final long KEEP_ALIVE_SECONDS = 120;
	private static final int QUEUE_CAPACITY = 500;

	private static final AtomicInteger THREAD_NUMBER = new AtomicInteger(1);
	private static final ThreadFactory THREAD_FACTORY = r -> {
		Thread t = new Thread(r, "chat-handler-" + THREAD_NUMBER.getAndIncrement());
		t.setDaemon(true);
		return t;
	};

	private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
			CORE_POOL_SIZE,
			MAX_POOL_SIZE,
			KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(QUEUE_CAPACITY),
			THREAD_FACTORY,
			new ThreadPoolExecutor.CallerRunsPolicy()
	);

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("正在关闭聊天线程池...");
			POOL.shutdown();
			try {
				if (!POOL.awaitTermination(10, TimeUnit.SECONDS)) {
					POOL.shutdownNow();
				}
			} catch (InterruptedException e) {
				POOL.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}, "chat-pool-shutdown"));
	}

	// ==================== 常量 ====================

	public static final String METHOD = "chat";

	public static final String CHAT_BROAD_MSG_ID = "chat_broad_msg";
	public static final String CHAT_BROAD_DELETE_ID = "chat_broad_delete_it";
	public static final String CHAT_BROAD_ROOM_CREATED = "chat_broad_room_created";
	public static final String CHAT_BROAD_ROOM_KICKED = "chat_broad_room_kicked";
	public static final String CHAT_BROAD_ROOM_DISMISS = "chat_broad_room_dismiss";
	public static final String CHAT_BROAD_ROOM_NAME_CHANGED = "chat_broad_room_name_changed";

	private static final Map<Long, String> USER_MAP = new ConcurrentHashMap<>();

	// ==================== 实例字段 ====================

	private final EwaWebSocketBus socket;
	private final JSONObject command;
	private String action;
	private long chatRoomId;
	private ClientSdk client;

	// ==================== 构造 & IHandleMsg ====================

	public HandleChatImpl(EwaWebSocketBus socket, JSONObject command) {
		this.socket = socket;
		this.command = command;
		this.action = command.optString("action");
		if (StringUtils.isBlank(this.action)) {
			this.action = command.optString("ACTION");
		}
		if (this.action == null) {
			this.action = "";
		}
	}

	public static int getQueueSize() {
		return POOL.getQueue().size();
	}

	public static int getActiveCount() {
		return POOL.getActiveCount();
	}

	@Override
	public void setName(String name) {
		// 接口要求，实际不依赖
	}

	@Override
	public void start() {
		POOL.execute(this);
	}

	@Override
	public void run() {
		JSONObject result = null;
		String commandId = this.command.optString("ID", null);
		if (commandId != null && commandId.isEmpty()) {
			commandId = null;
		}

		try {
			result = this.doAction();
		} catch (Exception err) {
			LOGGER.error("Execute action {} error {}", this.command, err.getMessage(), err);
			RestfulResult<Object> rst = ClientSdk.createErrorResult(
					err.getMessage() != null ? err.getMessage() : "Internal error", 500, 500);
			result = rst.toJson();
		}

		if (result == null) {
			return;
		}
		if (commandId != null) {
			result.put("ID", commandId);
		}
		this.socket.sendToClient(result.toString());
	}

	// ==================== Action 路由 ====================

	private JSONObject doAction() {
		String userToken = command.optString("userToken");

		if (StringUtils.isBlank(userToken)) {
			RestfulResult<Object> rst = ClientSdk.createErrorResult("need_user_token", 0, 401);
			return rst.toJson();
		}

		String restfulRoot = UPath.getInitPara("chat_restful_root");
		if (StringUtils.isBlank(restfulRoot)) {
			return UJSon.rstFalse("请在 ewa_conf.xml的 initPara中设置 chat_restful_root参数（访问restful的网址和前缀）");
		}

		this.client = new ClientSdk(restfulRoot, userToken);
		this.client.setFromIp(this.socket.getRv().s("SYS_REMOTEIP"));
		this.client.setFromUserAgent(this.socket.getRv().s("SYS_USER_AGENT"));

		long resolvedUserId = 0;
		String userIdStr = this.socket.getRv().s("cht_usr_id");
		if (userIdStr != null && !userIdStr.isEmpty()) {
			try {
				resolvedUserId = Long.parseLong(userIdStr);
			} catch (NumberFormatException e) {
				LOGGER.warn("Invalid cht_usr_id: {}", userIdStr);
			}
		}
		LOGGER.info("WebSocket action={}, cht_usr_id={}, resolvedUserId={}",
				this.action, userIdStr, resolvedUserId);
		if (resolvedUserId > 0) {
			this.client.setChatUserId(resolvedUserId);
		}

		if (command.has("parameters")) {
			client.setParames(command.optString("parameters"));
		}

		if ("updateRoomName".equalsIgnoreCase(this.action)) {
			return this.actionUpdateRoomName();
		}
		if ("rooms".equalsIgnoreCase(this.action)) {
			return this.actionRooms();
		}
		if ("exitRoom".equalsIgnoreCase(this.action)) {
			return this.actionExitRoom();
		}
		if ("deleteRoomMembers".equalsIgnoreCase(this.action)) {
			return this.actionDeleteRoomMembers();
		}
		if ("addRoomMembers".equalsIgnoreCase(this.action)) {
			return this.actionAddRoomMembers();
		}
		if ("roomMembers".equalsIgnoreCase(this.action)) {
			return this.actionRoomMembers();
		}
		if ("createRoom".equalsIgnoreCase(this.action)) {
			return this.actionCreateRoom();
		}
		if ("friends".equalsIgnoreCase(this.action)) {
			return this.actionMyFriends();
		}
		if ("post".equalsIgnoreCase(this.action)) {
			return this.actionPost();
		}
		if ("topics".equalsIgnoreCase(this.action)) {
			return this.actionTopics();
		}
		if ("uploaded".equalsIgnoreCase(this.action)) {
			return this.actionUploaded();
		}
		if ("myinfo".equalsIgnoreCase(this.action)) {
			return this.actionMyInfo();
		}
		if ("delete".equalsIgnoreCase(this.action)) {
			return this.actionDelete();
		}
		if ("unreadCounts".equalsIgnoreCase(this.action)) {
			return this.actionUnreadCounts();
		}

		return notImplementsAction();
	}

	// ==================== Action 实现 ====================

	private JSONObject actionUpdateRoomName() {
		this.chatRoomId = command.getLong("chatRoomId");
		RestfulResult<Object> rst = client.updateRoomInfo(chatRoomId, command);
		String newRoomName = command.optJSONObject("body").optString("cht_rom_name");
		if (!rst.isSuccess()) {
			return rst.toJson();
		}

		String userName = this.socket.getRv().s("cht_usr_name");
		String notification = "\"" + userName + "\"{CHANGE_CHATROOM_NAME}\"" + newRoomName + "\"";
		RestfulResult<Object> rstServer = this.postSystemNotification(chatRoomId, notification);

		if (rstServer.isSuccess()) {
			JSONObject msg = (JSONObject) rstServer.getRawData();
			msg.put("notification", notification);
			this.boradRoomMessage(msg, CHAT_BROAD_ROOM_NAME_CHANGED, true);
		}
		return rst.toJson();
	}

	private JSONObject actionExitRoom() {
		this.chatRoomId = command.getLong("chatRoomId");
		RestfulResult<Object> rst = client.exitChatRoom(chatRoomId);
		return rst.toJson();
	}

	private JSONObject actionAddRoomMembers() {
		this.chatRoomId = command.getLong("chatRoomId");
		String ids = this.command.optString("ids");
		List<Long> longIds = this.parseIds(ids);

		RestfulResult<Object> rst = client.addUserRoomMembers(chatRoomId, ids);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}

		RestfulResult<Object> rst1 = client.getChatRoom(chatRoomId);
		if (!rst1.isSuccess()) {
			return rst1.toJson();
		}

		JSONObject result = new JSONObject(rst1.getRawData().toString());
		this.boradMessage(result, CHAT_BROAD_ROOM_CREATED, longIds);

		return new JSONObject(rst.getReturnResult());
	}

	private JSONObject actionMyFriends() {
		Long relativeRoomId = null;
		if (this.command.has("relativeRoomId")) {
			relativeRoomId = this.command.optLong("relativeRoomId");
		}
		RestfulResult<Object> rst = client.myFriends(relativeRoomId);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}
		return new JSONObject(rst.getReturnResult());
	}

	private JSONObject actionDeleteRoomMembers() {
		this.chatRoomId = command.getLong("chatRoomId");
		String ids = this.command.optString("ids");
		List<Long> longIds = this.parseIds(ids);

		RestfulResult<Object> rst = client.deleteChatRoomMembers(chatRoomId, ids);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}

		JSONObject result = new JSONObject();
		result.put("cht_rom_id", chatRoomId);
		this.boradMessage(result, CHAT_BROAD_ROOM_KICKED, longIds);

		return rst.toJson();
	}

	private JSONObject actionRoomMembers() {
		this.chatRoomId = command.getLong("chatRoomId");
		Integer limits = command.has("limits") ? command.optInt("limits") : null;
		RestfulResult<Object> rst = client.getChatRoomMembers(chatRoomId, limits);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}
		return new JSONObject(rst.getReturnResult());
	}

	private JSONObject actionCreateRoom() {
		String ids = this.command.optString("ids");
		List<Long> longIds = this.parseIds(ids);

		RestfulResult<Object> rst = client.createUserRoom(ids);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}

		JSONObject result = new JSONObject(rst.getRawData().toString());
		this.boradMessage(result, CHAT_BROAD_ROOM_CREATED, longIds);

		return new JSONObject(rst.getReturnResult());
	}

	private JSONObject actionRooms() {
		String search = this.command.optString("search", null);
		RestfulResult<Object> rst = client.getChatRooms(search);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}
		return new JSONObject(rst.getReturnResult());
	}

	private JSONObject actionDelete() {
		this.chatRoomId = command.getLong("chatRoomId");
		long messageId = command.getLong("messageId");
		RestfulResult<Object> rst = client.deleteMessage(chatRoomId, messageId);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}

		JSONObject postDelete = new JSONObject();
		postDelete.put("messageId", messageId);
		postDelete.put("chatRoomId", chatRoomId);
		this.boradRoomMessage(postDelete, CHAT_BROAD_DELETE_ID, false);

		return rst.toJson();
	}

	private JSONObject actionUnreadCounts() {
		JSONObject result = new JSONObject();
		result.put("METHOD", METHOD);
		result.put("ACTION", this.action);
		result.put("ID", this.command.optString("ID"));
		try {
			if (this.client.getChatUserId() <= 0) {
				result.put("RST", false);
				result.put("ERR", "User not identified");
				return result;
			}
			RestfulResult<Object> apiRst = this.client.getUnreadCounts();
			if (!apiRst.isSuccess()) {
				result.put("RST", false);
				result.put("ERR", apiRst.getMessage());
				return result;
			}
			Object rawData = apiRst.getData();
			JSONArray data;
			if (rawData instanceof JSONArray) {
				data = (JSONArray) rawData;
			} else if (rawData instanceof JSONObject) {
				data = ((JSONObject) rawData).optJSONArray("DATA");
				if (data == null) {
					data = new JSONArray();
				}
			} else {
				data = new JSONArray();
			}
			result.put("RST", true);
			result.put("DATA", data);
		} catch (Exception e) {
			LOGGER.error("查询未读计数失败", e);
			result.put("RST", false);
			result.put("ERR", "查询未读计数失败: " + e.getMessage());
		}
		return result;
	}

	private JSONObject actionPost() {
		this.chatRoomId = command.getLong("chatRoomId");
		RestfulResult<Object> rst = client.newMessage(chatRoomId, command);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}

		JSONObject postResult = new JSONObject(rst.getRawData().toString());
		long newTopicId = postResult.optLong("swid");

		RestfulResult<Object> topicsRst = client.getChatRoomTopics(chatRoomId, null);
		JSONObject fullData = null;
		if (topicsRst.isSuccess() && topicsRst.getRawData() != null) {
			JSONArray list = new JSONArray(topicsRst.getRawData().toString());
			for (int i = 0; i < list.length(); i++) {
				JSONObject item = list.getJSONObject(i);
				if (item.optLong("cht_id") == newTopicId) {
					fullData = item;
					break;
				}
			}
		}

		if (fullData != null) {
			this.boradRoomMessage(fullData, CHAT_BROAD_MSG_ID, false);
		}

		return new JSONObject(rst.getReturnResult());
	}

	private JSONObject actionMyInfo() {
		RestfulResult<Object> rst = client.myInfo();
		if (!rst.isSuccess()) {
			return rst.toJson();
		}
		JSONObject returnJson = new JSONObject(rst.getReturnResult());
		JSONObject userJson = returnJson.optJSONObject("data");
		if (userJson != null) {
			long userId = userJson.getLong("cht_usr_id");
			socket.getRv().addValues(userJson);
			USER_MAP.put(userId, this.socket.getUnid());
		}
		return returnJson;
	}

	private JSONObject actionUploaded() {
		this.chatRoomId = command.getLong("chatRoomId");
		String ref = command.optString("ref");
		String refId = command.optString("ref_id");

		RestfulResult<Object> rst = client.getChatRoomTopicUploaded(chatRoomId, ref, refId);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}

		JSONArray msgs = (JSONArray) rst.getRawData();
		this.boradRoomMessage(msgs, CHAT_BROAD_MSG_ID, false);

		JSONObject ret = rst.toJson();
		ret.put("data", msgs.getJSONObject(0));
		return ret;
	}

	private JSONObject actionTopics() {
		this.chatRoomId = command.getLong("chatRoomId");

		Long lastTopicId = null;
		if (command.has("lastTopicId") && command.get("lastTopicId") != JSONObject.NULL) {
			lastTopicId = command.optLong("lastTopicId");
			if (lastTopicId == 0) {
				lastTopicId = null;
			}
		}
		if (lastTopicId == null) {
			this.client.joinRoom(this.chatRoomId);
		}
		ClientChatUserGroup.addUserToTopicGroup(this.chatRoomId, socket.getUnid());

		RestfulResult<Object> rst = client.getChatRoomTopics(chatRoomId, lastTopicId);
		if (!rst.isSuccess()) {
			return rst.toJson();
		}
		return new JSONObject(rst.getReturnResult());
	}

	// ==================== 辅助 ====================

	private List<Long> parseIds(String ids) {
		String[] arrIds = StringUtils.split(ids, ",");
		List<Long> longIds = new ArrayList<>();
		for (String id : arrIds) {
			longIds.add(Long.parseLong(id));
		}
		return longIds;
	}

	private ServerSdk getServerSdk() throws Exception {
		RequestValue rv = socket.getRv().clone();
		if (rv.isBlank("cht_sup_id")) {
			throw new Exception("Not found User info");
		}
		int supId = rv.getInt("cht_sup_id");
		return ServerSdk.getInstanceBySupId(supId);
	}

	private RestfulResult<Object> postSystemNotification(long chatRoomId, String notification) {
		try {
			ServerSdk server = this.getServerSdk();
			return server.postSystemNotification(chatRoomId, notification);
		} catch (Exception e) {
			return ClientSdk.createErrorResult(e.getMessage(), 500, 500);
		}
	}

	private JSONObject notImplementsAction() {
		RestfulResult<Object> rst = ClientSdk.createErrorResult(
				"Not implements action: (" + this.action + ")", 405, 405);
		return rst.toJson();
	}

	// ==================== 广播 ====================

	private void boradMessage(JSONArray msgs, String broadcastId, List<Long> toUserIds) {
		JSONObject result = new JSONObject();
		result.put("BROADCAST_ID", broadcastId);
		for (int i = 0; i < msgs.length(); i++) {
			msgs.getJSONObject(i).put("_msg_from_", this.socket.getUnid());
		}
		result.put("LIST", msgs);
		String broadcast = result.toString();

		for (Long userId : toUserIds) {
			if (!USER_MAP.containsKey(userId)) {
				continue;
			}
			String socketUnid = USER_MAP.get(userId);
			EwaWebSocketBus ws = EwaWebSocketContainer.getSocketByUnid(socketUnid);
			if (ws != null) {
				ws.sendToClient(broadcast);
			}
		}
	}

	private void boradMessage(JSONObject msg, String broadcastId, List<Long> toUserIds) {
		JSONArray msgs = new JSONArray();
		msgs.put(msg);
		this.boradMessage(msgs, broadcastId, toUserIds);
	}

	private void boradRoomMessage(JSONObject msg, String broadcastId, boolean includeMyself) {
		JSONArray msgs = new JSONArray();
		msgs.put(msg);
		this.boradRoomMessage(msgs, broadcastId, includeMyself);
	}

	private void boradRoomMessage(JSONArray msgs, String broadcastId, boolean includeMyself) {
		if (msgs.length() == 0) {
			return;
		}
		Map<String, Boolean> map = ClientChatUserGroup.getGroup(this.chatRoomId);
		if (map == null || map.isEmpty()) {
			return;
		}
		for (Iterator<String> it = map.keySet().iterator(); it.hasNext();) {
			String key = it.next();
			EwaWebSocketBus ws = EwaWebSocketContainer.getSocketByUnid(key);
			if (ws == null) {
				it.remove();
				continue;
			}
			if (!includeMyself && key.equals(this.socket.getUnid())) {
				continue;
			}
			JSONObject result = new JSONObject();
			result.put("BROADCAST_ID", broadcastId);
			result.put("MSG_FROM", this.socket.getUnid());
			result.put("LIST", msgs);
			ws.sendToClient(result.toString());
		}
	}

	// ==================== RequestValue ====================

	public RequestValue cloneRv() {
		RequestValue rv_clone = this.socket.getRv().clone();
		Iterator<?> it = this.command.keys();
		while (it.hasNext()) {
			String key = it.next().toString();
			String val = this.command.optString(key);
			rv_clone.getPageValues().remove(key);
			rv_clone.addValue(key, val);
		}
		rv_clone.resetDateTime();
		rv_clone.resetSysUnid();
		return rv_clone;
	}

	@Override
	public String getMethod() {
		return METHOD;
	}
}
