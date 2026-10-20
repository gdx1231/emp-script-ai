package com.gdxsoft.ai.providers.qwen;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataFactory;

/**
 * Qwen 联网搜索请求参数测试：
 * - enable_search 位于请求体顶层
 * - search_options 嵌套 forced_search / search_strategy / enable_search_extension
 * - 关闭搜索或空选项时清除相关字段
 */
class QwenRequestDataSearchTest {

	@Test
	void enableSearch_writesTopLevelField() {
		RequestData data = new RequestData();
		data.model("qwen-plus").enableSearch(true);
		data.userMessage("今日新闻");

		JSONObject body = data.build();
		assertTrue(body.getBoolean("enable_search"));
		assertFalse(body.has("search_options"));
	}

	@Test
	void enableSearchFalse_removesFieldAndOptions() {
		RequestData data = new RequestData();
		data.enableSearch(true);
		data.forcedSearch(true);
		data.searchStrategy("agent");
		data.enableSearch(false);

		JSONObject body = data.build();
		assertFalse(body.has("enable_search"));
		assertFalse(body.has("search_options"));
	}

	@Test
	void searchOptions_areNestedUnderSearchOptions() {
		RequestData data = new RequestData();
		data.enableSearch(true);
		data.forcedSearch(true);
		data.searchStrategy("agent");
		data.enableSearchExtension(true);

		JSONObject options = data.build().getJSONObject("search_options");
		assertTrue(options.getBoolean("forced_search"));
		assertEquals("agent", options.getString("search_strategy"));
		assertTrue(options.getBoolean("enable_search_extension"));
	}

	@Test
	void searchOptions_nullOrEmptyClears() {
		RequestData data = new RequestData();
		data.enableSearch(true);
		data.forcedSearch(true);

		data.searchOptions(new JSONObject());
		assertFalse(data.build().has("search_options"));

		data.forcedSearch(true);
		data.searchOptions(null);
		assertFalse(data.build().has("search_options"));
	}

	@Test
	void searchStrategy_blankRemovesOption() {
		RequestData data = new RequestData();
		data.enableSearch(true);
		data.searchStrategy("max");
		data.searchStrategy("  ");

		assertFalse(data.build().has("search_options"));
	}

	@Test
	void openAiCompatibleProvider_stillWritesTopLevelField() {
		// 保持历史行为：OpenAI 兼容 provider 统一携带 enable_search，由服务端决定是否支持
		IRequestData data = RequestDataFactory.createRequestData(ProviderType.OPENAI);
		data.enableSearch(true);

		assertTrue(data.build().getBoolean("enable_search"));
	}
}
