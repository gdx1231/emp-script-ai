package com.gdxsoft.ai.app.chatroom;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UJwt;
import com.gdxsoft.easyweb.utils.UJwt.JwtToken;

public class Auth {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Auth.class);
	private static final long DEF_LIFE_SECONDS = 7200; // 默认两个小时

	// ==================== 缓存 ====================

	/**
	 * 缓存条目：数据 + 过期时间
	 */
	private static class CacheEntry<T> {
		final T data;
		final long expireAt;

		CacheEntry(T data) {
			this.data = data;
			this.expireAt = System.currentTimeMillis() + CACHE_TTL_MS;
		}

		boolean isValid() {
			return System.currentTimeMillis() < expireAt;
		}
	}

	/** sup_main 缓存，key=sup_id */
	private static final ConcurrentHashMap<Integer, CacheEntry<JSONObject>> SUP_CACHE = new ConcurrentHashMap<>();

	/** api_main 缓存，key=sup_unid */
	private static final ConcurrentHashMap<String, CacheEntry<JSONObject>> API_CACHE = new ConcurrentHashMap<>();

	/** 缓存 TTL，默认 5 分钟（sup/api 信息属于配置类数据，很少变动） */
	private static volatile long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

	/**
	 * 设置缓存有效期（毫秒），0 表示禁用缓存
	 */
	public static void setCacheTtlMs(long ttlMs) {
		CACHE_TTL_MS = ttlMs;
		if (ttlMs <= 0) {
			clearCache();
		}
	}

	/**
	 * 清空所有缓存
	 */
	public static void clearCache() {
		SUP_CACHE.clear();
		API_CACHE.clear();
		LOGGER.info("Auth 缓存已清空");
	}

	/**
	 * 获取缓存统计信息
	 */
	public static String getCacheStats() {
		return String.format("sup=%d, api=%d, ttl=%dms", SUP_CACHE.size(), API_CACHE.size(), CACHE_TTL_MS);
	}

	private static String DEF_DatabaseName;

	/**
	 * 默认的数据库名称
	 * 
	 * @param defDatabaseName
	 */
	public static void setDefDatabaseName(String defDatabaseName) {
		DEF_DatabaseName = defDatabaseName;
	}

	/**
	 * 默认的数据库名称
	 * 
	 * @return 数据库名称
	 */
	public static String getDefDatabaseName() {
		return DEF_DatabaseName;
	}

	private static String DEF_ConnectName; // ewa_conf -> database

	/**
	 * 默认的数据库连接池名称
	 * 
	 * @return
	 */
	public static String getDefConnectName() {
		return DEF_ConnectName;
	}

	/**
	 * 默认的数据库连接池名称
	 * 
	 * @param defConnectName 据库连接池名称
	 */
	public static void setDefConnectName(String defConnectName) {
		DEF_ConnectName = defConnectName;
	}

	private String errorMessage;
	private JSONObject supMain;
	private JSONObject apiMain;
	private JwtToken validJwt;
	private String jwtToken;

	private String databaseName;
	private String connectName;

	private long endTime;

	private String ip = "";
	private String userAgent = "";
	private long chtUsrId = 0;

	public Auth() {
		this.databaseName = DEF_DatabaseName;
		this.connectName = DEF_ConnectName;
	}

	public Auth(String databaseName, String connectName) {
		this.databaseName = databaseName;
		this.connectName = connectName;
	}

	/**
	 * Create a supply token (7200s)
	 * 
	 * @param supId
	 * @return
	 */
	public boolean createJwtToken(int supId) {
		JSONObject payload = new JSONObject();
		payload.put("sup_id", supId);
		return this.createJwtToken(supId, DEF_LIFE_SECONDS, payload);
	}

	/**
	 * Create a supply token for user (7200s)
	 * 
	 * @param supId
	 * @param userId
	 * @return
	 */
	public boolean createJwtTokenUser(int supId, long userId) {
		return this.createJwtTokenUser(supId, userId, DEF_LIFE_SECONDS);
	}

	/**
	 * Create a supply token for user
	 * 
	 * @param supId
	 * @param userId
	 * @param lifeSeconds
	 * @return
	 */
	public boolean createJwtTokenUser(int supId, long userId, long lifeSeconds) {
		JSONObject payload = new JSONObject();
		payload.put("sup_id", supId);
		payload.put("cht_usr_id", userId);
		boolean rst = this.createJwtToken(supId, lifeSeconds, payload);
		if (rst) {
			AuthUser au = new AuthUser();
			au.setSupId(supId);
			au.setUserId(userId);
			au.setIp(this.ip);
			au.setUserAgent(this.userAgent);
			au.saveToken(jwtToken, new Date(endTime));
		}

		return rst;
	}

	/**
	 * Create a supply token
	 *
	 * @param supId
	 * @param lifeSeconds
	 * @return
	 */
	public boolean createJwtToken(int supId, long lifeSeconds) {
		return this.createJwtToken(supId, lifeSeconds, null);
	}

	/**
	 * Create a supply token with extra payload claims
	 *
	 * @param supId
	 * @param lifeSeconds
	 * @param payload     额外的 claims（如 sup_id, cht_usr_id 等）
	 * @return
	 */
	public boolean createJwtToken(int supId, long lifeSeconds, JSONObject payload) {
		RequestValue rv1 = new RequestValue();
		rv1.addOrUpdateValue("g_sup_id", supId);

		if (!this.loadSup(supId)) {
			return false;
		}

		if (!this.loadApiMain(this.supMain.optString("sup_unid"))) {
			return false;
		}

		try {
			this.jwtToken = this.createJwtToken(payload);
			LOGGER.info("Create JWT token. sup_id={}, cht_usr_id={}", supId, payload != null ? payload.optLong("cht_usr_id") : 0);
			return true;
		} catch (Exception e) {
			this.errorMessage = e.getMessage();
			LOGGER.error("Create JWT token error: {}", e.getMessage(), e);
			return false;
		}

	}

	private String createJwtToken(JSONObject payload) throws Exception {
		String apiSecret = this.apiMain.optString("api_sign_code");
		int lifeSeconds = 7200; // 2hour
		// 结束时间早10秒
		endTime = System.currentTimeMillis() + (lifeSeconds - 10) * 1000;
		Date signTime = new Date();
		Date expireAt = new Date(signTime.getTime() + endTime * 1000);
		return UJwt.hs256Builder().expiration(expireAt).issuedAt(signTime).claim(payload).create(apiSecret);
	}

	/**
	 * Valid the token
	 * 
	 * @param token
	 * @return true/false
	 */
	public boolean validJwtToken(String token) {
		this.jwtToken = token;
		try {
			JwtToken parseJwt = UJwt.parse(token);
			int supId = parseJwt.getIntClaim("sup_id");
			if (!this.loadSup(supId)) {
				return false;
			}
			if (!this.loadApiMain(this.supMain.optString("sup_unid"))) {
				return false;
			}

			validJwt = UJwt.verifyHs256(token, this.apiMain.optString("api_sign_code"));
			// 过期检查
			Date expire = validJwt.getExpiration();
			long diff = expire.getTime() - System.currentTimeMillis();
			if (diff < 0) {
				this.errorMessage = "Expired. (" + (diff * -1) + "ms)";
				return false;
			}
			if(validJwt.hasClaim("cht_usr_id")) {
				this.chtUsrId  = validJwt.getLongClaim("cht_usr_id");
			}
			LOGGER.info("Valid JWT token. sup_id={}, cht_usr_id={}", supId, this.chtUsrId);
			return true;

		} catch (Exception e) {
			this.errorMessage = e.getMessage();
			LOGGER.error("Valid JWT token error: {}", e.getMessage(), e);
			return false;
		}

	}

	private boolean loadApiMain(String supUnid) {
		// 检查缓存
		if (CACHE_TTL_MS > 0) {
			CacheEntry<JSONObject> entry = API_CACHE.get(supUnid);
			if (entry != null && entry.isValid()) {
				this.apiMain = entry.data;
				return true;
			}
			// 过期或不存在 → 移除过期条目后查库
			if (entry != null) {
				API_CACHE.remove(supUnid);
			}
		}

		RequestValue rv1 = new RequestValue();
		rv1.addOrUpdateValue("sup_unid", supUnid);
		String sqlApiMain = "select api_key, api_sign_code from api_main where sup_unid=@sup_unid";
		DTTable tbApi = DTTable.getJdbcTable(sqlApiMain, this.connectName, rv1);

		if (tbApi.getCount() == 0) {
			errorMessage = "The sup NOT register api. (" + this.supMain.optString("sup_name") + ")";
			return false;
		}
		this.apiMain = tbApi.toJSONArray().getJSONObject(0);

		// 写入缓存
		if (CACHE_TTL_MS > 0) {
			API_CACHE.put(supUnid, new CacheEntry<>(this.apiMain));
		}

		return true;
	}

	private boolean loadSup(int supId) {
		// 检查缓存
		if (CACHE_TTL_MS > 0) {
			CacheEntry<JSONObject> entry = SUP_CACHE.get(supId);
			if (entry != null && entry.isValid()) {
				this.supMain = entry.data;
				return true;
			}
			// 过期或不存在 → 移除过期条目后查库
			if (entry != null) {
				SUP_CACHE.remove(supId);
			}
		}

		RequestValue rv1 = new RequestValue();
		rv1.addOrUpdateValue("g_sup_id", supId);
		String sqlSupMain = "select sup_id, sup_name, sup_unid, sup_state from sup_main where sup_id=@g_sup_id";
		DTTable tb = DTTable.getJdbcTable(sqlSupMain, this.connectName, rv1);

		if (tb.getCount() == 0) {
			errorMessage = "NO supmain info with sup_id. (" + supId + ")";
			return false;
		}

		this.supMain = tb.toJSONArray().getJSONObject(0);
		if ("DEL".equalsIgnoreCase(this.supMain.optString("sup_state"))
				|| "CRM_S_DEL".equalsIgnoreCase(this.supMain.optString("sup_state"))) {
			errorMessage = "The supmain has deleted";
			return false;
		}

		// 写入缓存
		if (CACHE_TTL_MS > 0) {
			SUP_CACHE.put(supId, new CacheEntry<>(this.supMain));
		}

		return true;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public JSONObject getSupMain() {
		return supMain;
	}

	public JSONObject getApiMain() {
		return apiMain;
	}

	public JwtToken getDecodedJWT() {
		return this.validJwt;
	}

	public String getJwtToken() {
		return jwtToken;
	}

	public void setJwtToken(String jwtToken) {
		this.jwtToken = jwtToken;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	/**
	 * 指定查询的数据库名称
	 * 
	 * @param databaseName
	 */
	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public String getConnectName() {
		return connectName;
	}

	public void setConnectName(String connectName) {
		this.connectName = connectName;
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

	/**
	 * @return the chtUsrId
	 */
	public long getChtUsrId() {
		return chtUsrId;
	}

	/**
	 * @param chtUsrId the chtUsrId to set
	 */
	public void setChtUsrId(long chtUsrId) {
		this.chtUsrId = chtUsrId;
	}

}
