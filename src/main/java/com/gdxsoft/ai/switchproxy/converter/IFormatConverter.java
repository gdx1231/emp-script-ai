package com.gdxsoft.ai.switchproxy.converter;

import org.json.JSONObject;

import com.gdxsoft.ai.switchproxy.RouteConfig;
import com.gdxsoft.ai.switchproxy.ProfileConfig;

/**
 * 格式转换接口。
 * <p>
 * 每个请求创建一个新的 converter 实例（有状态）。
 */
public interface IFormatConverter {

	/**
	 * 将客户端请求 body 转换为目标 API 格式。
	 */
	JSONObject convertRequest(JSONObject clientRequest, RouteConfig route, ProfileConfig profile);

	/**
	 * 将目标 API 的 SSE 行转换为客户端期望的格式。
	 *
	 * @param rawLine 原始 SSE 行（含 data: 前缀）
	 * @return 转换后的 SSE 行，null 表示跳过该行
	 */
	String convertSseLine(String rawLine);
}
