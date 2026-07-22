package com.gdxsoft.ai.test;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.export.IAction;
import com.gdxsoft.ai.img.ImgClient;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgResponse;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * 图片生成 Action 参考实现。
 * <p>
 * 演示如何在 Mode XML 的 &lt;step action="..."&gt; 中调用 {@link ImgClient} 生成图片。
 * 框架会在 AI 对话完成后反射加载本类并调用 {@link #doAction(RequestValue, String)}，
 * 返回的 JSON 会以 agent 角色落库（AI_CHAT_MSG），流式模式下同时通过 SSE 推送给前端。
 *
 * <h3>Mode XML 配置示例</h3>
 *
 * <pre>
 * &lt;actions&gt;
 *   &lt;action name="gen_img" des="生成海报" class="com.gdxsoft.ai.test.GenImgActionExample"/&gt;
 * &lt;/actions&gt;
 * &lt;steps&gt;
 *   &lt;step name="draw" stream="true" action="gen_img"&gt;
 *     &lt;prompt name="p1" role="user"&gt;&lt;![CDATA[根据用户需求写一段绘图提示词：@{prompt}]]&gt;&lt;/prompt&gt;
 *   &lt;/step&gt;
 * &lt;/steps&gt;
 * </pre>
 *
 * <h3>API Key 配置</h3>
 * 与聊天共用 AI_PROVIDER_URL 表（APU_STATUS='USED'，ap_code 为图片供应商标识，
 * 如 doubao_img / qwen_img / openai_img）。注意 APU_URL 必须指向图片生成端点
 * （如豆包 https://ark.cn-beijing.volces.com/api/v3/images/generations），
 * 与 chat completions 端点不同。也可用环境变量兜底（本例读 DOUBAO_API_KEY）。
 */
public class GenImgActionExample implements IAction {
	private static final Logger LOGGER = LoggerFactory.getLogger(GenImgActionExample.class);

	/**
	 * 图片供应商标识（ImgProviderType），也可改为从 rv 参数读取
	 */
	private static final String IMG_PROVIDER = "doubao_img";

	/**
	 * 环境变量名兜底（AI_PROVIDER_URL 表未配置时使用）
	 */
	private static final String ENV_API_KEY = "DOUBAO_API_KEY";

	@Override
	public JSONObject doAction(RequestValue rv, String fullText) {
		try {
			// 绘图提示词：优先取请求参数 img_prompt，否则用上游 AI 的完整回复
			String prompt = rv.getString("img_prompt");
			if (prompt == null || prompt.trim().isEmpty()) {
				prompt = fullText;
			}
			if (prompt == null || prompt.trim().isEmpty()) {
				return UJSon.rstFalse("绘图提示词为空");
			}

			// 从 AI_PROVIDER_URL 表读取图片供应商的 URL 和 Key（与聊天同一套配置）
			String[] urlAndKey = loadApiConfig(rv);
			if (urlAndKey == null) {
				return UJSon.rstFalse("未找到图片供应商配置（AI_PROVIDER_URL.ap_code=" + IMG_PROVIDER + "），"
						+ "且环境变量 " + ENV_API_KEY + " 未设置");
			}

			ImgClient client = ImgClient.of(IMG_PROVIDER).apiKey(urlAndKey[1]);
			if (urlAndKey[0] != null && !urlAndKey[0].isEmpty()) {
				client.apiUrl(urlAndKey[0]);
			}

			// 生成图片：URL 返回模式，避免 b64 大图撑爆内存
			ImgResponse resp = client.generate(new ImgOptions(prompt).size("2K").n(1).responseFormat("url"));

			JSONObject rst = UJSon.rstTrue("图片生成成功");
			rst.put("url", resp.getFirstImage().getUrl());
			rst.put("model", resp.getModel());
			return rst;
		} catch (Exception e) {
			LOGGER.error("图片生成失败", e);
			return UJSon.rstFalse("图片生成失败：" + e.getMessage());
		}
	}

	/**
	 * 从 AI_PROVIDER_URL 表读取供应商配置，取不到时回退到环境变量
	 *
	 * @param rv 请求上下文
	 * @return [url, key]，都取不到返回 null
	 */
	private String[] loadApiConfig(RequestValue rv) {
		try {
			rv.addOrUpdateValue("ap_code_img", IMG_PROVIDER);
			String sql = "select APU_URL, APU_KEY from AI_PROVIDER_URL "
					+ "where APU_STATUS='USED' and ap_code=@ap_code_img order by APU_MDATE desc";
			DTTable tb = DTTable.getJdbcTable(sql, rv);
			if (tb.getCount() > 0) {
				String url = tb.getCell(0, "APU_URL").toString();
				String key = tb.getCell(0, "APU_KEY").toString();
				if (key != null && !key.isEmpty()) {
					return new String[] { url, key };
				}
			}
		} catch (Exception e) {
			LOGGER.warn("读取 AI_PROVIDER_URL 失败，尝试环境变量：{}", e.getMessage());
		}
		String envKey = System.getenv(ENV_API_KEY);
		if (envKey != null && !envKey.isEmpty()) {
			return new String[] { null, envKey };
		}
		return null;
	}

	@Override
	public String createPrompt(RequestValue rv, String dbConfigName) throws Exception {
		// 本 Action 不用于生成 Prompt（仅 doAction 场景），返回 null 即可
		return null;
	}
}
