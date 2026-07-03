package com.gdxsoft.ai.app.chatroom;

import java.util.Date;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.datasource.DataConnection;
import com.gdxsoft.easyweb.script.RequestValue;

public class AuthUser {
	private final static String SQL_SAVE_TOKEN = " insert into chat_user_token("
			+ " cht_usr_id, cht_token, cht_token_expire, cht_token_issued,cht_token_ip,cht_token_ua,cht_token_sup_id)"
			+ " values( @userId, @token.md5, @expireTime, @sys_date, @ip, @useragent, @g_sup_id)";;
	private final static String SQL_LOAD_USER = "select * from chat_user_token where cht_token=@token.md5";
	private String errorMessage;
	private long userId;
	private int supId;
	private String userName;
	private String ip = "";
	private String userAgent = "";

	/**
	 * 保存用户的 token 到数据库
	 * 
	 * @param token      用户的 token
	 * @param expireTime token 的过期时间
	 * @param rv1        请求值对象，用于传递参数
	 */
	public void saveToken(String token, Date expireTime) {
		RequestValue rv1 = new RequestValue();

		rv1.addOrUpdateValue("userId", this.userId);
		rv1.addOrUpdateValue("g_sup_id", this.supId);
		rv1.addOrUpdateValue("token", token);
		rv1.addOrUpdateValue("expireTime", expireTime, "date", 30);
		if (ip != null) {
			rv1.addOrUpdateValue("ip", this.ip);
		} else {
			rv1.addOrUpdateValue("ip", "");
		}
		if (userAgent != null) {
			rv1.addOrUpdateValue("useragent", this.userAgent);
		} else {
			rv1.addOrUpdateValue("useragent", "");
		}
		DataConnection.updateAndClose(SQL_SAVE_TOKEN, "", rv1);
	}

	/**
	 * 根据 token 加载用户信息
	 * 
	 * @param token 用户的 token
	 * @return 如果加载成功返回 true，否则返回 false
	 */
	public boolean loadUser(String token) {

		RequestValue rv1 = new RequestValue();
		rv1.addOrUpdateValue("token", token);

		DTTable tb = DTTable.getJdbcTable(SQL_LOAD_USER, "", rv1);

		if (tb.getCount() == 0) {
			errorMessage = "Invalid token : " + token;
			return false;
		}

		try {
			long expireTime = tb.getCell(0, "cht_token_expire").toTime();
			if (expireTime < System.currentTimeMillis()) {
				errorMessage = "The token had expired";
				return false;
			}
			String sql1 = "select * from chat_user where cht_usr_id=" + tb.getCell(0, "cht_usr_id");

			DTTable tb1 = DTTable.getJdbcTable(sql1, "", rv1);

			if (tb1.getCount() == 0) {
				errorMessage = "The user not found";
				return false;
			}

			if ("DEL".equalsIgnoreCase(tb1.getCell(0, "cht_usr_status").toString())) {
				errorMessage = "The user had removed";
				return false;
			}
			this.userId = tb1.getCell(0, "cht_usr_id").toLong();
			this.supId = tb1.getCell(0, "cht_usr_sup_id").toInt();
			this.userName = tb1.getCell(0, "cht_usr_name").toString();

			return true;
		} catch (Exception e) {
			errorMessage = e.getLocalizedMessage();
			return false;
		}

	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public long getUserId() {
		return userId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
	}

	public int getSupId() {
		return supId;
	}

	public void setSupId(int supId) {
		this.supId = supId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

}
