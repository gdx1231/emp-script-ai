package com.gdxsoft.ai.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.export.IAction;
import com.gdxsoft.ai.img.IImgProvider;
import com.gdxsoft.ai.img.ImgConcurrency;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgProviderFactory;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * 漫画多图生成 Action 参考实现。
 * <p>
 * 使用场景：用户通过 mode.xml 与 AI 对话创作故事，AI 根据故事拆分漫画分镜并输出
 * 多个图片需求（JSON 数组），本 Action 解析需求后批量生成图片，返回每张图的 URL 并落盘。
 *
 * <h3>Mode XML 配置示例</h3>
 *
 * <pre>
 * &lt;actions&gt;
 *   &lt;action name="comic_img" des="批量生成漫画图片" class="com.gdxsoft.ai.test.ComicImgActionExample"/&gt;
 * &lt;/actions&gt;
 * &lt;steps&gt;
 *   &lt;!-- AI 根据故事输出图片需求 JSON 数组，step 完成后框架自动调用 doAction --&gt;
 *   &lt;step name="plan" stream="true" action="comic_img"&gt;
 *     &lt;prompt name="p1" role="user"&gt;&lt;![CDATA[
 * 根据故事拆分漫画分镜，只输出 JSON 数组，每项含 name（分镜名）和 prompt（英文绘图提示词）：
 * [{"name":"分镜1","prompt":"..."}]
 * 故事：@{prompt}
 *     ]]&gt;&lt;/prompt&gt;
 *   &lt;/step&gt;
 * &lt;/steps&gt;
 * </pre>
 *
 * <h3>上游 AI 输出契约（fullText）</h3>
 *
 * <pre>[{"name":"分镜1","prompt":"a cat sitting on a windowsill, comic style"}, ...]</pre>
 *
 * 容忍 markdown 代码围栏或前后杂文本（自动截取首个 [ 到末个 ] 之间的内容）。
 *
 * <h3>参数与配置</h3>
 * <ul>
 * <li>rv 参数 img_output_dir：图片落盘目录，缺省为系统临时目录下新建的 comic_ 前缀目录</li>
 * <li>API 配置：优先 AI_PROVIDER_URL 表（ap_code=doubao_img），兜底环境变量 DOUBAO_API_KEY</li>
 * </ul>
 *
 * <h3>返回 JSON</h3>
 *
 * <pre>
 * {"RST": true, "output_dir": "/tmp/comic_x", "images": [
 *   {"name": "分镜1", "url": "https://...", "files": ["/tmp/comic_x/scene_0_1.png"]},
 *   {"name": "分镜2", "error": "生成失败原因"}
 * ]}
 * </pre>
 *
 * 单张失败不影响整批；全部失败时 RST=false。
 */
public class ComicImgActionExample implements IAction {
	private static final Logger LOGGER = LoggerFactory.getLogger(ComicImgActionExample.class);

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
		// 1. 解析上游 AI 输出的图片需求 JSON 数组
		JSONArray requirements = extractRequirements(fullText);
		if (requirements == null || requirements.isEmpty()) {
			return UJSon.rstFalse("未能从 AI 回复中解析出图片需求 JSON 数组");
		}

		// 2. 读取 API 配置
		String[] urlAndKey = loadApiConfig(rv);
		if (urlAndKey == null) {
			return UJSon.rstFalse("未找到图片供应商配置（AI_PROVIDER_URL.ap_code=" + IMG_PROVIDER + "），" + "且环境变量 "
					+ ENV_API_KEY + " 未设置");
		}

		// 3. 构建批量生成请求
		List<ImgRequest> requests = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (int i = 0; i < requirements.length(); i++) {
			JSONObject item = requirements.optJSONObject(i);
			if (item == null) {
				continue;
			}
			String prompt = item.optString("prompt", "").trim();
			if (prompt.isEmpty()) {
				continue;
			}
			names.add(item.optString("name", "分镜" + (i + 1)));
			requests.add(new ImgRequest(new ImgOptions(prompt).size("2K").n(1).responseFormat("url")));
		}
		if (requests.isEmpty()) {
			return UJSon.rstFalse("图片需求中没有有效的 prompt");
		}

		// 4. 并发批量生成（单张失败不影响整批）
		IImgProvider provider = ImgProviderFactory.create(IMG_PROVIDER);
		provider.setApiKey(urlAndKey[1]);
		if (urlAndKey[0] != null && !urlAndKey[0].isEmpty()) {
			provider.setApiUrl(urlAndKey[0]);
		}
		ImgConcurrency concurrency = ImgConcurrency.of(provider).maxConcurrency(3).maxRetries(2);

		List<ImgResponse> responses;
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < requests.size(); i++) {
			errors.add(null);
		}
		try {
			responses = concurrency.generateAll(requests, (index, response, error) -> {
				if (error != null) {
					LOGGER.warn("第 {} 张图片生成失败：{}", index, error.getMessage());
					errors.set(index, error.getMessage());
				}
			});
		} catch (Exception e) {
			LOGGER.error("批量生成图片失败", e);
			return UJSon.rstFalse("批量生成图片失败：" + e.getMessage());
		}

		// 5. 确定落盘目录
		Path outputDir;
		try {
			String dirParam = rv.getString("img_output_dir");
			if (dirParam != null && !dirParam.trim().isEmpty()) {
				outputDir = Path.of(dirParam.trim());
				Files.createDirectories(outputDir);
			} else {
				outputDir = Files.createTempDirectory("comic_");
			}
		} catch (Exception e) {
			LOGGER.error("创建图片输出目录失败", e);
			return UJSon.rstFalse("创建图片输出目录失败：" + e.getMessage());
		}

		// 6. 汇总结果：URL + 落盘
		JSONArray images = new JSONArray();
		int successCount = 0;
		for (int i = 0; i < requests.size(); i++) {
			JSONObject item = new JSONObject();
			item.put("name", names.get(i));
			ImgResponse resp = responses.get(i);
			if (resp == null || resp.getImages().isEmpty()) {
				item.put("error", errors.get(i) != null ? errors.get(i) : "未返回图片");
				images.put(item);
				continue;
			}
			try {
				item.put("url", resp.getFirstImage().getUrl());
				List<Path> files = resp.saveAll(outputDir, "scene_" + i + "_");
				JSONArray fileArr = new JSONArray();
				for (Path f : files) {
					fileArr.put(f.toString());
				}
				item.put("files", fileArr);
				successCount++;
			} catch (Exception e) {
				LOGGER.warn("第 {} 张图片落盘失败：{}", i, e.getMessage());
				item.put("error", "落盘失败：" + e.getMessage());
			} finally {
				for (ImgResponse.GeneratedImage img : resp.getImages()) {
					img.release();
				}
			}
			images.put(item);
		}

		if (successCount == 0) {
			JSONObject rst = UJSon.rstFalse("全部图片生成失败");
			rst.put("images", images);
			return rst;
		}
		JSONObject rst = UJSon.rstTrue("图片生成完成，成功 " + successCount + "/" + requests.size() + " 张");
		rst.put("output_dir", outputDir.toString());
		rst.put("images", images);
		return rst;
	}

	/**
	 * 从 AI 回复中提取图片需求 JSON 数组。
	 * <p>
	 * 先尝试直接解析；失败则截取首个 [ 到末个 ] 之间的内容重试，
	 * 以容忍 markdown 代码围栏或前后杂文本。
	 *
	 * @param fullText AI 完整回复
	 * @return 需求数组，解析失败返回 null
	 */
	private JSONArray extractRequirements(String fullText) {
		if (fullText == null || fullText.trim().isEmpty()) {
			return null;
		}
		try {
			return new JSONArray(fullText.trim());
		} catch (Exception e) {
			// 继续尝试截取
		}
		int start = fullText.indexOf('[');
		int end = fullText.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return null;
		}
		try {
			return new JSONArray(fullText.substring(start, end + 1));
		} catch (Exception e) {
			LOGGER.warn("解析图片需求 JSON 失败：{}", e.getMessage());
			return null;
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
