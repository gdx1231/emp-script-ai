package com.gdxsoft.ai.app.chatroom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UJwt.JwtToken;

/**
 * WebSocket 握手时附加 RequestValue + JWT 认证。
 *
 * <p>
 * 身份来源：URL {@code ?token=} 携带的 JWT，payload 中包含 {@code sup_id} 和
 * {@code cht_usr_id}（由 {@code Auth.createJwtTokenUser} 写入）。不依赖 HttpSession。
 * </p>
 */
public class RvConfigure extends Configurator {
	private static Logger LOGGER = LoggerFactory.getLogger(RvConfigure.class);

	@Override
	public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
		HttpSession httpSession = (HttpSession) request.getHttpSession();
		Cookie[] cookies = null;
		Map<String, String> headers = new HashMap<String, String>();
		for (String key : request.getHeaders().keySet()) {
			List<String> lst = request.getHeaders().get(key);
			if (key.equalsIgnoreCase("cookie")) {
				cookies = this.getCookies(lst);
			} else {
				headers.put(key, lst.get(0));
			}
		}

		RequestValue rv = new RequestValue();
		rv.initParametersByHeaders(headers);
		rv.reloadSessions(httpSession);
		rv.reloadCookies(cookies);
		rv.reloadQueryValues(request.getQueryString());

		sec.getUserProperties().put(RequestValue.class.getName(), rv);
		if (httpSession != null) {
			sec.getUserProperties().put("HTTP_SESSION", httpSession);
		}

		// 从 URL ?token= 的 JWT payload 提取 sup_id + cht_usr_id
		String token = rv.s("token");
		if (token != null && !token.trim().isEmpty()) {
			validateAndExtract(token.trim(), sec, rv);
		} else {
			LOGGER.debug("WebSocket 连接未携带 token，消息层将按需鉴权");
		}
		// 注意：不在此处强制拦截。
		// HandleChatImpl / HandleAiChatImpl 等消息处理器各自做鉴权，
		// 未认证用户会在发送消息时收到 "Unauthorized" 响应，而非直接断连。
	}

	/**
	 * 验证 JWT 并从 payload 提取 sup_id、cht_usr_id，存入 UserProperties 和 RequestValue。
	 */
	private void validateAndExtract(String token, ServerEndpointConfig sec, RequestValue rv) {
		try {
			Auth auth = new Auth();
			if (!auth.validJwtToken(token)) {
				LOGGER.warn("JWT 验证失败: {}", auth.getErrorMessage());
				return;
			}

			JwtToken jwt = auth.getDecodedJWT();

			// 提取 sup_id
			int supId = jwt.getIntClaim("sup_id");
			sec.getUserProperties().put("sup_id", supId);
			rv.addOrUpdateValue("g_sup_id", supId);

			// 提取 cht_usr_id
			long chtUsrId = jwt.getLongClaim("cht_usr_id");
			if (chtUsrId > 0) {
				sec.getUserProperties().put("cht_usr_id", chtUsrId);
				rv.addOrUpdateValue("cht_usr_id", chtUsrId);
			}

			LOGGER.info("JWT auth: sup_id={}, cht_usr_id={}", supId, chtUsrId);
		} catch (Exception e) {
			LOGGER.error("Token validation failed: " + e.getMessage(), e);
		}
	}

	private Cookie[] getCookies(List<String> lst) {
		List<Cookie> cks = new ArrayList<Cookie>();
		for (int i = 0; i < lst.size(); i++) {
			String[] items = lst.get(i).split(";");
			for (int m = 0; m < items.length; m++) {
				String[] item = items[m].split("\\=");
				if (item.length == 2) {
					Cookie ck = new Cookie(item[0].trim(), item[1]);
					cks.add(ck);
				}
			}
		}
		Cookie[] cks1 = new Cookie[cks.size()];
		return cks.toArray(cks1);
	}
}
