package com.gdxsoft.ai.app.chatroom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.restful.RestfulResult;
import com.gdxsoft.easyweb.utils.UNet;
import com.gdxsoft.easyweb.utils.UPath;
import com.gdxsoft.easyweb.utils.USnowflake;

public class ServerSdk {
	private static Map<String, Auth> AUTHS = new ConcurrentHashMap<>();
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerSdk.class);

	public static ServerSdk getInstanceBySupId(int supId) throws Exception {
		String restfulRoot = UPath.getInitPara("chat_restful_server_root");
		if (StringUtils.isBlank(restfulRoot)) {
			throw new Exception("请在 ewa_conf.xml的 initPara中设置 chat_restful_server_root参数（访问restful的网址和前缀）");
		}

		return getInstanceBySupId(supId, restfulRoot);

	}

	public static ServerSdk getInstanceBySupId(int supId, String restfulRoot) throws Exception {
		String key = supId + "///" + restfulRoot;
		Auth auth = null;
		ServerSdk server = new ServerSdk(restfulRoot);

		if (AUTHS.containsKey(key)) {
			auth = AUTHS.get(key);
			if (auth.getEndTime() > System.currentTimeMillis()) { // 未过期
				server.serverToken = auth.getJwtToken();
				return server;
			}
		}

		auth = new Auth();
		if (auth.createJwtToken(supId)) {
			// 放到缓存中
			AUTHS.put(key, auth);
			server.serverToken = auth.getJwtToken();
			return server;
		} else {
			throw new Exception(auth.getErrorMessage());
		}

	}

	private String errorMessage;
	private String result;
	private int httpStatusCode;
	private String apiRoot;
	private String serverToken;
	private String databaseName;

	public ServerSdk(String apiRoot) {
		this.apiRoot = apiRoot;
	}

	/**
	 * 发布系统通知
	 * 
	 * @param chatRoomId
	 * @param notification
	 * @return
	 */
	public RestfulResult<Object> postSystemNotification(long chatRoomId, String notification) {
		long userId = 0; // 系统消息
		String messageType = "notification";
		RestfulResult<Object> rr = this.postMessage(chatRoomId, userId, notification, messageType);

		return rr;
	}

	/**
	 * 发布消息
	 * 
	 * @param chatRoomId
	 * @param message
	 * @param messageType
	 * @return
	 */
	public RestfulResult<Object> postMessage(long chatRoomId, long userId, String message, String messageType) {
		JSONObject body = new JSONObject();
		body.put("cht_cnt", message);
		body.put("cht_type", messageType);
		body.put("cht_usr_id", userId);

		UNet net = getNet();
		String url = getApiPath("chatRooms/" + chatRoomId+"/topics");
		String bodyStr = body.toString();
		String result = net.postMsg(url, bodyStr);
		this.logNon200Warning(net, "POST", url, bodyStr);
		// System.out.println(result);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		return rr;
	}

	public int getHttpStatusCode() {
		return this.httpStatusCode;
	}

	public String getResult() {
		return this.result;
	}

	public String getErrorMessage() {
		return this.errorMessage;
	}

	/**
	 * 当 HTTP 状态码不为 200 时，输出 WARN 日志和对应的 curl 命令
	 *
	 * @param net    UNet 实例
	 * @param method HTTP 方法 (GET/POST/PUT/DELETE)
	 * @param url    请求 URL
	 * @param body   请求体（可为 null）
	 */
	private void logNon200Warning(UNet net, String method, String url, String body) {
		int statusCode = net.getLastStatusCode();
		if (statusCode >= 200 && statusCode < 300) {
			return;
		}
		LOGGER.warn("HTTP status code {} for {} {}", statusCode, method, url);

		java.util.List<String> parts = new java.util.ArrayList<>();
		parts.add("curl -X " + method + " '" + url + "'");

		if (StringUtils.isNotBlank(this.serverToken)) {
			parts.add("  -H 'Authorization: Bearer " + this.serverToken + "'");
		}
		if (StringUtils.isNotBlank(body)) {
			// 转义 body 中的单引号，安全拼接
			String escaped = body.replace("'", "'\\''");
			parts.add("  -d '" + escaped + "'");
		}

		String curlStr = String.join(" \\\n", parts);
		LOGGER.warn("Curl:\n{}", curlStr);
	}

	public String getApiPath(String path) {
		return this.apiRoot + (this.apiRoot.endsWith("/") ? "" : "/") + path;
	}

	public String getSuperToken() {

		return serverToken;

	}

	public UNet getNet() {
		UNet net = new UNet();

		net.addHeader("Authorization", "Bearer " + getSuperToken());
		net.setIsShowLog(false);

		return net;
	}

	public String createUserToken(long chatUserId) {

		UNet net = getNet();
		String url = getApiPath("chatUsers/" + chatUserId + "/tokens");
		String result = net.postMsg(url, "{}");
		this.logNon200Warning(net, "POST", url, "{}");

		System.out.println(result);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			System.out.println(rr.getMessage());
			return null;
		}

		JSONObject obj = (JSONObject) rr.getRawData();

		return obj.optString("cht_token");

	}

	public long addUserToServer(int userId) {
		
		String sqlWebUser = "select * from web_user where usr_id=" + userId;
		DTTable tbWebUser = DTTable.getJdbcTable(sqlWebUser, "");
		JSONObject chatUser = new JSONObject();
		if (tbWebUser.getCount() == 0) {
			return -1;
		}
		long chatUserId = 	USnowflake.nextId();
		chatUser.put("cht_usr_id", chatUserId);
		try {
			chatUser.put("cht_usr_name", tbWebUser.getCell(0, "usr_name").toString());
			chatUser.put("cht_usr_gender", tbWebUser.getCell(0, "usr_sex").toString());
			chatUser.put("cht_usr_mobile", tbWebUser.getCell(0, "usr_mobile").toString());
		}  catch (Exception e) {
			return -1;
		}
		
		chatUser.put("cht_usr_ref", "web_user.usr_id");
		chatUser.put("cht_usr_ref_id", userId + "");

		 

		String body = chatUser.toString();
		System.out.println(body);

		UNet net = getNet();
		String url = getApiPath("chatUsers");
		String result = net.postMsg(url, body);
		this.logNon200Warning(net, "POST", url, body);

		System.out.println(result);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			System.out.println(rr.getMessage());
			return -1;
		}

		return chatUserId;
	}

	public long checkChatUser(int userId) {
		UNet net = getNet();

		String url = getApiPath("chatUsers");
		url += "?cht_usr_ref=web_user.usr_id&cht_usr_ref_id=" + userId;

		String result = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);
		int code = net.getLastStatusCode();

		this.result = result;
		this.httpStatusCode = code;
		System.out.println(result);
		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			System.out.println(rr.getMessage());
			return -1;
		}

		if (rr.getRecordCount() == 0) {
			return 0;
		}

		JSONArray arr = (JSONArray) rr.getRawData();
		JSONObject user = arr.getJSONObject(0);

		return user.optLong("cht_usr_id");
	}

	public long addRoom(long chatUserId, String roomType, String roomName, String roomNameEn) {
		UNet net = getNet();
		String url = getApiPath("chatRooms");

		JSONObject body = new JSONObject();
		body.put("cht_rom_creator", chatUserId);
		body.put("cht_rom_owner", 0);
		body.put("cht_rom_type", roomType);
		body.put("cht_rom_ref", roomType);
		body.put("cht_rom_ref_id", chatUserId);
		body.put("cht_rom_name", roomName == null ? "" : roomName);
		body.put("cht_rom_name_en", roomNameEn == null ? "" : roomNameEn);

		LOGGER.info("{}", body);

		String bodyStr = body.toString();
		String result = net.postMsg(url, bodyStr);
		this.logNon200Warning(net, "POST", url, bodyStr);
		LOGGER.info("{}", result);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			System.out.println(rr.getMessage());
			return -1;
		}

		JSONObject obj = (JSONObject) rr.getRawData();

		return obj.getLong("cht_rom_id");
	}

	public long checkRoomSystem(long chatUserId, String roomType) {
		UNet net = getNet();
		String url = getApiPath("chatRooms");
		url += "?EWA_IS_SPLIT_PAGE=no&ref=" + roomType + "&ref_id=" + chatUserId + "&cht_usr_id=" + chatUserId;

		String result = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);
		int code = net.getLastStatusCode();

		this.result = result;
		this.httpStatusCode = code;
		System.out.println(result);
		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			System.out.println(rr.getMessage());
			return -1;
		}

		if (rr.getRecordCount() == 0) {
			return 0;
		}

		JSONArray arr = (JSONArray) rr.getRawData();
		JSONObject room = arr.getJSONObject(0);

		return room.optLong("cht_rom_id");
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}
}
