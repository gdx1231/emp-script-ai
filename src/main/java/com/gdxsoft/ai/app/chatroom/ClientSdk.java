package com.gdxsoft.ai.app.chatroom;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.script.restful.RestfulResult;
import com.gdxsoft.easyweb.utils.UNet;
import com.gdxsoft.easyweb.utils.UUrl;
import com.gdxsoft.easyweb.utils.Utils;

public class ClientSdk {
	/**
	 * 创建 错误提示信息
	 * 
	 * @param errorMsg
	 * @param statusCode
	 * @return
	 */
	public static RestfulResult<Object> createErrorResult(String errorMsg, int errorCode, int httpStatusCode) {
		RestfulResult<Object> rr = new RestfulResult<>();
		rr.setCode(errorCode);
		rr.setHttpStatusCode(httpStatusCode);
		rr.setSuccess(false);
		rr.setMessage(errorMsg);
		rr.setRawData(rr.toJson());
		rr.setData(rr.toJson());

		return rr;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ClientSdk.class);

	private String restfulRoot;
	private String userToken;
	private String parames;
	private String fromIp; // 客户端来源地址 ipv4/ipv6
	private String fromUserAgent; // 客户端浏览器UA
	private long chatUserId; // JWT 认证时传递 chat user id

	public ClientSdk(String restfulRoot, String userToken) {
		this.restfulRoot = restfulRoot;
		this.userToken = userToken;
	}

	/**
	 * 获取所有房间未读消息计数
	 *
	 * @return 每个房间的 {cht_rom_id, unread} 列表
	 */
	public RestfulResult<Object> getUnreadCounts() {
		String path = "/unreads";
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 获取我的好友关系
	 *
	 * @param relativeRoomId 关联在房间号码
	 * @return
	 */
	public RestfulResult<Object> myFriends(Long relativeRoomId) {
		String path = "/friends";
		String url = this.createUrl(path);
		if (relativeRoomId != null) {
			url += "?relativeRoomId=" + relativeRoomId;
		}
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	public RestfulResult<Object> newMessage(long chatRomId, JSONObject msg) {
		String path = "/chatRooms/" + chatRomId + "/topics";
		String url = this.createUrl(path);
		String body = msg.optJSONObject("body").toString();
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doPost(url, body);
		this.logNon200Warning(net, "POST", url, body);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 删除房间帖子内容
	 *
	 * @param chatRomId 房间号
	 * @param messageId 帖子号码
	 * @return
	 */
	public RestfulResult<Object> deleteMessage(long chatRomId, long messageId) {
		String path = "/chatRooms/" + chatRomId + "/topics/" + messageId;
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doDelete(url);
		this.logNon200Warning(net, "DELETE", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		if (net.getLastStatusCode() == 200 || net.getLastStatusCode() == 204) {
			rr.setSuccess(true);
			rr.setHttpStatusCode(net.getLastStatusCode());
		}

		return rr;
	}

	/**
	 * 附加查询参数
	 * 
	 * @param url
	 * @return
	 */
	private String attacheParameters(String url) {
		return attacheParameters(url, true);
	}

	private String attacheParametersWithoutUserId(String url) {
		return attacheParameters(url, false);
	}

	private String attacheParameters(String url, boolean includeUserId) {
		// JWT 认证时，自动附加 cht_usr_id 供 JwtAcl 读取
		String userIdPara = "";
		if (includeUserId && this.chatUserId > 0) {
			userIdPara = "cht_usr_id=" + this.chatUserId;
		}

		String allParams = "";
		if (StringUtils.isNotBlank(this.parames)) {
			allParams = this.parames;
		}
		if (StringUtils.isNotBlank(userIdPara)) {
			allParams = StringUtils.isBlank(allParams) ? userIdPara : allParams + "&" + userIdPara;
		}

		if (StringUtils.isBlank(allParams)) {
			return url;
		}

		if (url.indexOf("?") > 0) {
			url += "&" + allParams;
		} else {
			url += "?" + allParams;
		}

		return url;
	}

	/**
	 * 获取房间的帖子内容
	 * 
	 * @param chatRomId   房间号
	 * @param lastTopicId 最后一个帖子号码
	 * @return
	 */
	public RestfulResult<Object> getChatRoomTopics(long chatRomId, Long lastTopicId) {
		String path = "/chatRooms/" + chatRomId + "/topics";
		String url = this.createUrl(path);
		if (lastTopicId != null) {
			url += "?lastTopicId=" + lastTopicId;
		}

		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	public RestfulResult<Object> getChatRoomMembers(long chatRomId, Integer limits) {
		String path = "/chatRooms/" + chatRomId + "/members";
		String url = this.createUrl(path);
		if (limits != null && limits > 0) {
			url += "?ewa_pagesize=" + limits;
		}
		// members 端点不附加 cht_usr_id，否则 SQL 只返回自己
		url = this.attacheParametersWithoutUserId(url);

		UNet net = this.createNet();
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 退出房间
	 *
	 * @param chatRomId
	 * @return
	 */
	public RestfulResult<Object> exitChatRoom(long chatRomId) {
		String path = "/chatRooms/" + chatRomId + "/myAcl";
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doDelete(url);
		this.logNon200Warning(net, "DELETE", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	public RestfulResult<Object> notification(long chatRomId, String msg) {
		JSONObject notication = new JSONObject();
		// cht_cnt: "notification"
		// cht_rom_id: "64748967224672256"
		// cht_type: "text"
		// cht_usr_id
		notication.append("cht_cnt", msg);
		notication.append("cht_type", "notication");

		return this.newMessage(chatRomId, notication);
	}

	/**
	 * 加入房间（自动创建 ACL 权限，幂等调用，已存在不报错）。
	 * 新用户进入房间时必须先调用此方法，否则无法查看帖子和发帖。
	 *
	 * @param chatRoomId 房间 ID
	 * @return 操作结果
	 */
	public RestfulResult<Object> joinRoom(long chatRoomId) {
		String path = "/chatRooms/" + chatRoomId + "/members";
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		JSONObject body = new JSONObject();
		body.put("cht_acl_master", "N");
		body.put("cht_acl_top", "N");
		String bodyContent = body.toString();

		UNet net = this.createNet();
		String rst = net.postMsg(url, bodyContent);
		this.logNon200Warning(net, "POST", url, bodyContent);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	public RestfulResult<Object> getChatRoom(long chatRoomId) {
		String path = "/chatRooms/" + chatRoomId;
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 跟新房间信息
	 *
	 * @param chatRoomId 房间号
	 * @param msg        更新的内容 cht_rom_name,cht_rom_memo,cht_rom_owner
	 * @return
	 */
	public RestfulResult<Object> updateRoomInfo(long chatRoomId, JSONObject msg) {
		String path = "/chatRooms/" + chatRoomId;
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		String body = msg.getJSONObject("body").toString();

		UNet net = this.createNet();
		String rst = net.doPut(url, body);
		this.logNon200Warning(net, "PUT", url, body);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;

	}

	/**
	 * 获取房间
	 *
	 * @param chatRomId 房间号
	 * @param search    房间名称查询
	 * @return
	 */
	public RestfulResult<Object> getChatRooms(String search) {
		String path = "/chatRooms";
		String url = this.createUrl(path);
		if (StringUtils.isNotBlank(search)) {
			UUrl uu = new UUrl(url);
			uu.add("ewa_search", "cht_rom_name[lk]" + search);
			url = uu.getUrlWithDomain();
		}

		url = this.attacheParameters(url);

		UNet net = this.createNet();
		net.setIsShowLog(true);
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 添加用户到房间里
	 *
	 * @param ids
	 * @return
	 */
	public RestfulResult<Object> addUserRoomMembers(long chatRoomId, String ids) {
		String path = "/chatRooms/" + chatRoomId + "/members";
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		JSONObject body = new JSONObject();
		body.put("users_id_split", ids);

		String bodyContent = body.toString();

		UNet net = this.createNet();
		net.setIsShowLog(true);
		String rst = net.postMsg(url, bodyContent);
		this.logNon200Warning(net, "POST", url, bodyContent);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 删除房间成员
	 *
	 * @param chatRoomId
	 * @param deleteUserIds 删除的成员 userId的字符串
	 * @return
	 */
	public RestfulResult<Object> deleteChatRoomMembers(long chatRoomId, String deleteUserIds) {
		String path = "/chatRooms/" + chatRoomId + "/members";
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		JSONObject body = new JSONObject();
		body.put("users_id_split", deleteUserIds);

		String bodyContent = body.toString();

		UNet net = this.createNet();
		net.setIsShowLog(true);
		String rst = net.doDelete(url, bodyContent);
		this.logNon200Warning(net, "DELETE", url, bodyContent);

		RestfulResult<Object> rr = new RestfulResult<>();

		if (rst == null) {
			rr.setHttpStatusCode(net.getLastStatusCode());
		} else {
			rr.parse(rst);
		}

		return rr;
	}

	/**
	 * 用户创建房间
	 *
	 * @param ids
	 * @return
	 */
	public RestfulResult<Object> createUserRoom(String ids) {
		String path = "/chatRooms/";
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		JSONObject body = new JSONObject();
		body.put("users_id_split", ids);
		body.put("cht_rom_name", "");
		body.put("cht_rom_name_en", "");
		body.put("cht_rom_type", "");
		body.put("cht_rom_ref", "user");
		body.put("cht_rom_ref_id", Utils.getGuid());

		String bodyContent = body.toString();

		UNet net = this.createNet();
		net.setIsShowLog(true);
		String rst = net.postMsg(url, bodyContent);
		this.logNon200Warning(net, "POST", url, bodyContent);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 获取房间的帖子内容
	 *
	 * @param chatRomId   房间号
	 * @param lastTopicId 最后一个帖子号码
	 * @return
	 */
	public RestfulResult<Object> getChatRoomTopicUploaded(long chatRomId, String uploadRef, String uploadRefId) {
		String path = "/chatRooms/" + chatRomId + "/topics";
		String url = this.createUrl(path);
		url += "?ref=" + uploadRef + "&Ref_Id=" + uploadRefId;
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
	}

	/**
	 * 获取我的信息
	 *
	 * @return
	 */
	public RestfulResult<Object> myInfo() {
		String path = "/chatUsers/myself";
		String url = this.createUrl(path);
		url = this.attacheParameters(url);

		UNet net = this.createNet();
		String rst = net.doGet(url);
		this.logNon200Warning(net, "GET", url, null);

		RestfulResult<Object> rr = new RestfulResult<>();
		rr.parse(rst);

		return rr;
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

		if (StringUtils.isNotBlank(this.userToken)) {
			parts.add("  -H 'Authorization: " + this.userToken + "'");
		}
		if (StringUtils.isNotBlank(fromIp)) {
			parts.add("  -H 'X-Forwarded-For: " + fromIp + "'");
		}
		if (StringUtils.isNotBlank(this.fromUserAgent)) {
			parts.add("  -H 'User-Agent: " + this.fromUserAgent + "'");
		}
		if (StringUtils.isNotBlank(body)) {
			// 转义 body 中的单引号，安全拼接
			String escaped = body.replace("'", "'\\''");
			parts.add("  -d '" + escaped + "'");
		}

		String curlStr = String.join(" \\\n", parts);
		LOGGER.warn("Curl:\n{}", curlStr);
	}

	private String createUrl(String path) {
		String url = this.restfulRoot + path;
		return url;
	}

	public UNet createNet() {
		UNet net = new UNet();
		net.addHeader("Authorization", this.userToken);
		net.setIsShowLog(false);
		if (StringUtils.isNotBlank(fromIp)) { // 客户端来源地址
			net.addHeader("X-Forwarded-For", fromIp);
		}
		if (StringUtils.isNotBlank(this.fromUserAgent)) { // 客户端浏览器UA
			net.setUserAgent(fromUserAgent);
		}
		return net;
	}

	public String getParames() {
		return parames;
	}

	public void setParames(String parames) {
		this.parames = parames;
	}

	/**
	 * 客户端来源地址 ipv4/ipv6
	 * 
	 * @return IP地址 (ipv4/ipv6)
	 */
	public String getFromIp() {
		return fromIp;
	}

	/**
	 * 客户端来源地址 ipv4/ipv6
	 * 
	 * @param fromIp IP地址 (ipv4/ipv6)
	 */
	public void setFromIp(String fromIp) {
		this.fromIp = fromIp;
	}

	/**
	 * 客户端浏览器UA
	 * 
	 * @return UserAgent
	 */
	public String getFromUserAgent() {
		return fromUserAgent;
	}

	/**
	 * 客户端浏览器UA
	 * 
	 * @param fromUserAgent 览器UserAgent
	 */
	public void setFromUserAgent(String fromUserAgent) {
		this.fromUserAgent = fromUserAgent;
	}

	public long getChatUserId() {
		return chatUserId;
	}

	public void setChatUserId(long chatUserId) {
		this.chatUserId = chatUserId;
	}

}
