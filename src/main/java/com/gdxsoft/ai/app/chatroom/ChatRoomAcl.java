package com.gdxsoft.ai.app.chatroom;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.acl.IAcl;
import com.gdxsoft.easyweb.acl.SampleAcl;
import com.gdxsoft.easyweb.script.restful.RestfulResult;
import com.gdxsoft.easyweb.utils.Utils;

/**
 * 兼容 JWT（无论是否带 "Bearer " 前缀）的 ACL。
 *
 * </p>
 */
public class ChatRoomAcl  extends SampleAcl implements IAcl {
	private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoomAcl.class);

	private RestfulResult<Void> rst;

	public boolean canRun() {
		String restful = super.getRequestValue() == null ? null : super.getRequestValue().s("ewa_restful");
		if (Utils.cvtBool(restful)) {
			return this.canRunRestful();
		} else {
			return true;
		}
	}

	public String getDenyMessage() {
		if (rst != null) {
			return rst.toJson().toString();
		} else {
			return null;
		}
	}

	public boolean canRunRestful() {
		this.rst = new RestfulResult<>();

		if (super.getRequestValue() == null || super.getRequestValue().getRequest() == null) {
			return false;
		}
		String authorization = super.getRequestValue().getRequest().getHeader("authorization");

		if (StringUtils.isBlank(authorization)) {
			// 下载图片
			String ewaAjax = super.getRequestValue().s("ewa_ajax");
			if ("download".equalsIgnoreCase(ewaAjax) || "download-inline".equalsIgnoreCase(ewaAjax)) {
				authorization = super.getRequestValue().s("authorization");
			}
		}
		if (StringUtils.isBlank(authorization)) {
			rst.setCode(124);
			rst.setSuccess(false);
			rst.setMessage("Invalid access token.");
			LOGGER.warn("Invalid access token. Request: {}", super.getRequestValue().getRequest().getRequestURI());
			return false;
		}
			// jwt
			return this.handleAuthorizationSup(authorization);

	}

	/**
	 * 加载商户/ user的权限
	 * 
	 * @param authorization
	 * @return
	 */
	private boolean handleAuthorizationSup(String authorization) {
		String token = authorization;
		if (authorization.startsWith("Bearer ")) {
			token = authorization.substring(7);
		}
		Auth auth = new Auth();
		if (!auth.validJwtToken(token.trim())) {
			rst.setCode(122);
			rst.setSuccess(false);
			rst.setMessage(auth.getErrorMessage());
			return false;
		}
		int supId = auth.getSupMain().optInt("sup_id");
		if(supId <= 0) {
			rst.setCode(126);
			rst.setSuccess(false);
			rst.setMessage("Invalid supplier id in JWT.");
			return false;
		}
		super.getRequestValue().addOrUpdateValue("g_sup_id", supId);
		super.getRequestValue().addOrUpdateValue("g_sup_unid", auth.getSupMain().optString("sup_unid"));
		if(auth.getChtUsrId() > 0) {
			super.getRequestValue().addOrUpdateValue("cht_usr_id", auth.getChtUsrId());
			super.getRequestValue().addOrUpdateValue("g_usr_id", auth.getChtUsrId());
		}
		// 商户授权
		super.getRequestValue().addOrUpdateValue("authorization_type", "supply");
		return true;
	}

}
