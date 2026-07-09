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
import com.gdxsoft.easyweb.script.restful.SdkBase;
import com.gdxsoft.easyweb.utils.UJSon;
import com.gdxsoft.easyweb.utils.UNet;
import com.gdxsoft.easyweb.utils.UPath;
import com.gdxsoft.easyweb.utils.UUrl;
import com.gdxsoft.easyweb.utils.Utils;

public class ServerSdk extends SdkBase {
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
		server.setSupId(supId);
		
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
	
	private int supId;
	

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

	public RestfulResult<Object> getChatRoom(long chatRoomId) {
		String path = "/chatRooms/" + chatRoomId;
		String url = this.getApiPath(path);

		UNet net = this.getNet();

		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

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
		String url = getApiPath("chatRooms/" + chatRoomId + "/topics");
		String bodyStr = body.toString();
		String result = net.postMsg(url, bodyStr);
		this.logNon200Warning(net, "POST", url, bodyStr);
		// System.out.println(result);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		return rr;
	}

	public String createUserToken(long chatUserId) {
		RestfulResult<Object> rr = super.apiPost("chatUsers/" + chatUserId + "/tokens", "{}");

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			LOGGER.warn("Failed to create user token: {}", rr.getMessage());
			return null;
		}

		JSONArray obj = (JSONArray) rr.getRawData();

		return obj.getJSONObject(0).optString("cht_token");

	}

	/**
	 * 批量创建聊天用户
	 * 
	 * @param users JSONArray 聊天用户信息数组，每个元素为一个JSONObject，包含以下字段：<br>
	 *              - cht_usr_id: 聊天用户ID（必填）<br>
	 *              - cht_usr_ref: 用户引用类型（必填）<br>
	 *              - cht_usr_ref_id: 用户引用ID（必填）<br>
	 *              - cht_usr_name: 聊天用户名称（必填）<br>
	 *              - cht_usr_gender: 聊 天用户性别（可选，默认值为 "U"）<br>
	 *              - cht_usr_mobile: 聊天用户手机号（可选）<br>
	 * @return JSONObject 创建结果，包含以下字段：<br>
	 *         - success: 是否创建成功<br>
	 *         - data: 创建成功的聊天用户信息数组
	 * 
	 */
	public JSONObject createChatUsers(JSONArray users) {
		String[] userids = new String[users.length()];
		String[] refs = new String[users.length()];
		String[] refIds = new String[users.length()];
		String[] names = new String[users.length()];
		String[] genders = new String[users.length()];
		String[] mobiles = new String[users.length()];

		for (int i = 0; i < users.length(); i++) {
			JSONObject user = users.getJSONObject(i);
			if (!user.has("cht_usr_name") || !user.has("cht_usr_id") || !user.has("cht_usr_ref")
					|| !user.has("cht_usr_ref_id")) {
				this.errorMessage = "Missing required fields: cht_usr_id or cht_usr_ref or cht_usr_ref_id";
				LOGGER.warn(this.errorMessage);
				return UJSon.rstFalse(errorMessage);
			}
			String userId = user.optString("cht_usr_id").replace(",", "");
			userids[i] = userId;
			String ref = user.optString("cht_usr_ref").replace(",", "");
			refs[i] = ref;
			String refId = user.optString("cht_usr_ref_id").replace(",", "");
			refIds[i] = refId;
			String name = user.optString("cht_usr_name").replace(",", "");
			names[i] = name;
			String gender = user.optString("cht_usr_gender", "U").replace(",", "");
			genders[i] = gender;
			String mobile = user.optString("cht_usr_mobile").replace(",", "");
			mobiles[i] = mobile;
		}

		JSONObject body = new JSONObject();
		body.put("userIds", Utils.arrayJoin(userids, ","));
		body.put("refs", Utils.arrayJoin(refs, ","));
		body.put("refIds", Utils.arrayJoin(refIds, ","));
		body.put("names", Utils.arrayJoin(names, ","));
		body.put("genders", Utils.arrayJoin(genders, ","));
		body.put("mobiles", Utils.arrayJoin(mobiles, ","));
		body.put("bat", "yes");
		RestfulResult<Object> rr = super.apiPost("chatUsers", body.toString());

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			LOGGER.warn("Failed to create chat users: {}", rr.getMessage());
			return null;
		}
		JSONObject rst = new JSONObject();
		UJSon.rstSetTrue(rst, null);
		rst.put("data", rr.getRawData());
		return rst;

	}

	public long addChatUser(String ref, String refId, JSONObject userInfo) {
		long chatUserId = checkChatUser(ref, refId);
		if (chatUserId > 0) {
			return chatUserId;
		}
		String body = userInfo.toString();
		RestfulResult<Object> rr = super.apiPost("chatUsers", body);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			LOGGER.warn("Failed to add user to server: {}", rr.getMessage());
			return -1;
		}
		JSONArray d = (JSONArray) rr.getRawData();

		return d.getJSONObject(0).optLong("cht_usr_id", -1);
	}

	public long addChatUser(String ref, String refId, String userName, String gender, String mobile) {
		JSONObject chatUser = new JSONObject();
		if (gender == null) {
			gender = "U";
		}
		chatUser.put("cht_usr_name", userName);
		chatUser.put("cht_usr_gender", gender);
		chatUser.put("cht_usr_mobile", mobile);
		chatUser.put("cht_usr_ref", ref);
		chatUser.put("cht_usr_ref_id", refId);

		return addChatUser(ref, refId, chatUser);
	}

	public long addUserToServer(int userId) {
		long chatUserId = checkChatUser(userId);
		if (chatUserId > 0) {
			return chatUserId;
		}

		String sqlWebUser = "select * from web_user where usr_id=" + userId;
		DTTable tbWebUser = DTTable.getJdbcTable(sqlWebUser, "");
		JSONObject chatUser = new JSONObject();
		if (tbWebUser.getCount() == 0) {
			return -1;
		}
		try {
			String gender = tbWebUser.getCell(0, "usr_sex").toString();
			if (gender == null || gender.equals("")) {
				gender = "U";
			}
			chatUser.put("cht_usr_name", tbWebUser.getCell(0, "usr_name").toString());
			chatUser.put("cht_usr_gender", gender);
			chatUser.put("cht_usr_mobile", tbWebUser.getCell(0, "usr_mobile").toString());

		} catch (Exception e) {
			return -1;
		}

		chatUser.put("cht_usr_ref", "web_user.usr_id");
		chatUser.put("cht_usr_ref_id", userId + "");

		return addChatUser("web_user.usr_id", String.valueOf(userId), chatUser);

	}

	/**
	 * 检查聊天用户是否存在 ref=web_user.usr_id
	 * 
	 * @param userId 用户ID
	 * @return 聊天用户ID，未找到返回0，失败返回-1
	 */
	public long checkChatUser(int userId) {
		return checkChatUser("web_user.usr_id", String.valueOf(userId));
	}

	/**
	 * 检查聊天用户是否存在
	 * 
	 * @param ref   用户引用类型
	 * @param refId 用户引用ID
	 * @return 聊天用户ID，未找到返回0，失败返回-1
	 */
	public long checkChatUser(String ref, String refId) {
		String queryString = "cht_usr_ref=" + ref + "&cht_usr_ref_id=" + refId;

		RestfulResult<Object> rr = super.apiGet("chatUsers", queryString);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			LOGGER.warn("Failed to check chat user: {}", rr.getMessage());
			return -1;
		}

		if (rr.getRecordCount() == 0) {
			return 0;
		}

		JSONArray arr = (JSONArray) rr.getRawData();
		JSONObject user = arr.getJSONObject(0);

		return user.optLong("cht_usr_id");
	}

	/**
	 * 创建聊天室请求体
	 * 
	 * @param chatUserId 创建者用户ID
	 * @param roomType   聊天室类型
	 * @param roomName   聊天室名称
	 * @param roomNameEn 聊天室英文名称
	 * @param roomRef    聊天室引用类型
	 * @param roomRefId  聊天室引用ID
	 * @param tag0       标签0
	 * @param tag1       标签1
	 * @param tag2       标签2
	 * @return JSONObject 请求体
	 */
	private JSONObject createRoomBody(long chatUserId, String roomType, String roomName, String roomNameEn,
			String roomRef, String roomRefId, String tag0, String tag1, String tag2) {
		JSONObject body = new JSONObject();
		body.put("cht_rom_creator", chatUserId);
		body.put("cht_rom_owner", chatUserId);
		body.put("cht_rom_type", roomType);
		body.put("cht_rom_ref", roomRef);
		body.put("cht_rom_ref_id", roomRefId);
		body.put("cht_rom_name", roomName == null ? "" : roomName);
		body.put("cht_rom_name_en", roomNameEn == null ? "" : roomNameEn);
		body.put("cht_rom_tag0", tag0);
		body.put("cht_rom_tag1", tag1);
		body.put("cht_rom_tag2", tag2);

		return body;
	}

	/**
	 * 修改聊天室信息
	 * 
	 * @param chatRoomId 聊天室ID
	 * @param chatUserId 创建者用户ID
	 * @param roomType   聊天室类型
	 * @param roomName   聊天室名称
	 * @param roomNameEn 聊天室英文名称
	 * @param roomRef    聊天室引用类型
	 * @param roomRefId  聊天室引用ID
	 * @param tag0       标签0
	 * @param tag1       标签1
	 * @param tag2       标签2
	 * @return 修改成功返回true，失败返回false
	 */
	public boolean modifyRoom(long chatRoomId, long chatUserId, String roomType, String roomName, String roomNameEn,
			String roomRef, String roomRefId, String tag0, String tag1, String tag2) {
		JSONObject body = this.createRoomBody(chatUserId, roomType, roomName, roomNameEn, roomRef, roomRefId, tag0,
				tag1, tag2);

		RestfulResult<Object> rr = this.apiPut("chatRooms/" + chatRoomId, body.toString());
		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			LOGGER.warn("Failed to modify room: {}", rr.getMessage());
			return false;
		}

		return true;
	}

	/**
	 * 添加聊天室
	 * 
	 * @param chatUserId 创建者用户ID
	 * @param roomType   聊天室类型
	 * @param roomName   聊天室名称
	 * @param roomNameEn 聊天室英文名称
	 * @param roomRef    聊天室引用类型
	 * @param roomRefId  聊天室引用ID
	 * @param tag0       标签0
	 * @param tag1       标签1
	 * @param tag2       标签2
	 * @return 新创建的聊天室ID，如果失败返回-1
	 */
	public long addRoom(long chatUserId, String roomType, String roomName, String roomNameEn, String roomRef,
			String roomRefId, String tag0, String tag1, String tag2) {
		UNet net = getNet();
		String url = getApiPath("chatRooms");

		JSONObject body = this.createRoomBody(chatUserId, roomType, roomName, roomNameEn, roomRef, roomRefId, tag0,
				tag1, tag2);

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

		JSONArray obj = (JSONArray) rr.getRawData();

		return obj.getJSONObject(0).getLong("cht_rom_id");
	}

	public long checkRoomSystem(long chatUserId, String roomType) {
		UNet net = getNet();
		String baseUrl = getApiPath("chatRooms");
		UUrl uu = new UUrl(baseUrl + "?x=1");
		uu.add("EWA_IS_SPLIT_PAGE", "no");
		uu.add("ref", roomType);
		uu.add("ref_id", chatUserId);
		uu.add("cht_usr_id", chatUserId);
		String url = baseUrl + "?" + uu.getParameters();

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

	/**
	 * 通过 room_ref 和 room_ref_id 查询聊天室
	 *
	 * @param roomRef   聊天室引用类型
	 * @param roomRefId 聊天室引用ID
	 * @return 聊天室ID，未找到返回0，失败返回-1
	 */
	public long checkRoom(String roomRef, String roomRefId) {

		String baseUrl = getApiPath("chatRooms");
		UUrl uu = new UUrl(baseUrl + "?x=1");
		uu.add("EWA_IS_SPLIT_PAGE", "no");
		uu.add("cht_rom_ref", roomRef);
		uu.add("cht_rom_ref_id", roomRefId);
		String url = uu.getUrlWithDomain();

		UNet net = getNet();
		String result = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(result);

		if (!rr.isSuccess()) {
			this.errorMessage = rr.getMessage();
			LOGGER.warn("checkRoom failed: {}", rr.getMessage());
			return -1;
		}

		if (rr.getRecordCount() == 0) {
			return 0;
		}

		JSONArray arr = (JSONArray) rr.getRawData();
		JSONObject room = arr.getJSONObject(0);

		return room.optLong("cht_rom_id");
	}

	public int getSupId() {
		return supId;
	}

	public void setSupId(int supId) {
		this.supId = supId;
	}

}
