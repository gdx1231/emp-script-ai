package com.gdxsoft.ai.request;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.request.style.AnthropicRequestData;
import com.gdxsoft.ai.request.style.GeminiRequestData;

/**
 * thinkingBudget 端到端验证：同一调用方调用 {@link IRequestData#thinkingBudget(int)}，
 * 各 provider / model 应自动翻译为对应协议字段。
 *
 * <p>Provider 名称冲突处理：本文件多个 provider 都用 {@code RequestData} 类名，
 * 因此在字段/局部变量处用全限定名（FQN）区分。</p>
 */
class ThinkingBudgetTest {

	// ====================== Anthropic ======================

	@Test
	void anthropic_claudeSonnet4_writesBudgetTokens() {
		com.gdxsoft.ai.providers.anthropic.RequestData rd =
				new com.gdxsoft.ai.providers.anthropic.RequestData();
		rd.model("claude-sonnet-4-20250514");
		rd.thinkingBudget(4096);

		JSONObject body = rd.build();
		assertTrue(body.has("thinking"), "Anthropic Sonnet 4 应写入 thinking 字段");
		JSONObject thinking = body.getJSONObject("thinking");
		assertEquals("enabled", thinking.getString("type"));
		assertEquals(4096, thinking.getInt("budget_tokens"));
		assertEquals(4096, rd.getThinkingBudget());
	}

	@Test
	void anthropic_claude37Sonnet_writesBudgetTokens() {
		com.gdxsoft.ai.providers.anthropic.RequestData rd =
				new com.gdxsoft.ai.providers.anthropic.RequestData();
		rd.model("claude-3-7-sonnet-20250219");
		rd.thinkingBudget(2048);

		JSONObject thinking = rd.build().getJSONObject("thinking");
		assertEquals("enabled", thinking.getString("type"));
		assertEquals(2048, thinking.getInt("budget_tokens"));
	}

	@Test
	void anthropic_claude3Haiku_ignored() {
		com.gdxsoft.ai.providers.anthropic.RequestData rd =
				new com.gdxsoft.ai.providers.anthropic.RequestData();
		rd.model("claude-3-haiku-20240307");
		rd.thinkingBudget(2048);

		JSONObject body = rd.build();
		assertFalse(body.has("thinking"), "Claude 3 Haiku 不支持扩展思考，thinking 字段应缺失");
		// 缓存值仍保留
		assertEquals(2048, rd.getThinkingBudget());
	}

	@Test
	void anthropic_zeroBudget_doesNotEmitField() {
		com.gdxsoft.ai.providers.anthropic.RequestData rd =
				new com.gdxsoft.ai.providers.anthropic.RequestData();
		rd.model("claude-sonnet-4-20250514");
		rd.thinkingBudget(0);

		assertFalse(rd.build().has("thinking"));
		assertEquals(0, rd.getThinkingBudget());
	}

	// ====================== Gemini ======================

	@Test
	void gemini_25pro_writesThinkingConfig() {
		com.gdxsoft.ai.providers.gemini.RequestData rd =
				new com.gdxsoft.ai.providers.gemini.RequestData();
		rd.model("gemini-2.5-pro");
		rd.thinkingBudget(8192);

		JSONObject body = rd.build();
		JSONObject genConfig = body.getJSONObject("generationConfig");
		assertTrue(genConfig.has("thinkingConfig"));
		JSONObject thinkingConfig = genConfig.getJSONObject("thinkingConfig");
		assertEquals(8192, thinkingConfig.getInt("thinkingBudget"));
		assertTrue(thinkingConfig.getBoolean("includeThoughts"));
	}

	@Test
	void gemini_25flash_writesThinkingConfig() {
		com.gdxsoft.ai.providers.gemini.RequestData rd =
				new com.gdxsoft.ai.providers.gemini.RequestData();
		rd.model("gemini-2.5-flash");
		rd.thinkingBudget(1024);

		JSONObject thinkingConfig = rd.build().getJSONObject("generationConfig")
				.getJSONObject("thinkingConfig");
		assertEquals(1024, thinkingConfig.getInt("thinkingBudget"));
	}

	@Test
	void gemini_15pro_ignored() {
		com.gdxsoft.ai.providers.gemini.RequestData rd =
				new com.gdxsoft.ai.providers.gemini.RequestData();
		rd.model("gemini-1.5-pro");
		rd.thinkingBudget(4096);

		JSONObject body = rd.build();
		if (body.has("generationConfig")) {
			JSONObject genConfig = body.getJSONObject("generationConfig");
			assertFalse(genConfig.has("thinkingConfig"),
					"gemini-1.5-pro 不应写入 thinkingConfig");
		}
		assertEquals(4096, rd.getThinkingBudget(), "缓存值仍保留");
	}

	@Test
	void gemini_zeroBudget_removesThinkingConfig() {
		com.gdxsoft.ai.providers.gemini.RequestData rd =
				new com.gdxsoft.ai.providers.gemini.RequestData();
		rd.model("gemini-2.5-flash");
		rd.thinkingBudget(2048);
		rd.thinkingBudget(0);

		JSONObject body = rd.build();
		if (body.has("generationConfig")) {
			assertFalse(body.getJSONObject("generationConfig").has("thinkingConfig"));
		}
		assertEquals(0, rd.getThinkingBudget());
	}

	// ====================== Qwen ======================

	@Test
	void qwen_qwq_writesMaxThinkingTokens() {
		com.gdxsoft.ai.providers.qwen.RequestData rd =
				new com.gdxsoft.ai.providers.qwen.RequestData();
		rd.model("qwq-32b-preview");
		rd.thinkingBudget(4096);

		JSONObject body = rd.build();
		JSONObject thinking = body.getJSONObject("thinking");
		assertEquals("enabled", thinking.getString("type"));
		assertEquals(4096, thinking.getInt("max_thinking_tokens"));
		assertFalse(thinking.has("budget"),
				"DashScope 原生字段为 max_thinking_tokens，不应使用 thinking.budget");
	}

	@Test
	void qwen_qwen3Thinking_writesMaxThinkingTokens() {
		com.gdxsoft.ai.providers.qwen.RequestData rd =
				new com.gdxsoft.ai.providers.qwen.RequestData();
		rd.model("qwen3-max-thinking");
		rd.thinkingBudget(8192);

		JSONObject thinking = rd.build().getJSONObject("thinking");
		assertEquals("enabled", thinking.getString("type"));
		assertEquals(8192, thinking.getInt("max_thinking_tokens"));
	}

	@Test
	void qwen_qvq_writesMaxThinkingTokens() {
		com.gdxsoft.ai.providers.qwen.RequestData rd =
				new com.gdxsoft.ai.providers.qwen.RequestData();
		rd.model("qvq-max");
		rd.thinkingBudget(2048);

		JSONObject thinking = rd.build().getJSONObject("thinking");
		assertEquals("enabled", thinking.getString("type"));
		assertEquals(2048, thinking.getInt("max_thinking_tokens"));
	}

	@Test
	void qwen_qwenPlus_ignored() {
		com.gdxsoft.ai.providers.qwen.RequestData rd =
				new com.gdxsoft.ai.providers.qwen.RequestData();
		rd.model("qwen-plus");
		rd.thinkingBudget(4096);

		JSONObject body = rd.build();
		assertFalse(body.has("thinking"), "qwen-plus 不应写入 thinking 字段");
		assertEquals(4096, rd.getThinkingBudget());
	}

	// ====================== Doubao ======================

	@Test
	void doubao_thinking_writesLevelLow() {
		com.gdxsoft.ai.providers.doubao.RequestData rd =
				new com.gdxsoft.ai.providers.doubao.RequestData();
		rd.model("doubao-seed-1-6-thinking-250615");
		rd.thinkingBudget(2048);

		JSONObject body = rd.build();
		JSONObject thinking = body.getJSONObject("thinking");
		assertEquals("enabled", thinking.getString("type"));
		assertEquals("low", thinking.getString("level"));
	}

	@Test
	void doubao_thinking_writesLevelMedium() {
		com.gdxsoft.ai.providers.doubao.RequestData rd =
				new com.gdxsoft.ai.providers.doubao.RequestData();
		rd.model("doubao-1-5-thinking-pro-250415");
		rd.thinkingBudget(8192);

		JSONObject thinking = rd.build().getJSONObject("thinking");
		assertEquals("medium", thinking.getString("level"));
	}

	@Test
	void doubao_thinking_writesLevelHigh() {
		com.gdxsoft.ai.providers.doubao.RequestData rd =
				new com.gdxsoft.ai.providers.doubao.RequestData();
		rd.model("doubao-seed-1-6-thinking-250615");
		rd.thinkingBudget(32768);

		JSONObject thinking = rd.build().getJSONObject("thinking");
		assertEquals("high", thinking.getString("level"));
	}

	@Test
	void doubao_deepseekR1_writesLevel() {
		// 火山方舟代理的 deepseek-r1 也走 thinking.level
		com.gdxsoft.ai.providers.doubao.RequestData rd =
				new com.gdxsoft.ai.providers.doubao.RequestData();
		rd.model("deepseek-r1-250120");
		rd.thinkingBudget(4096);

		JSONObject thinking = rd.build().getJSONObject("thinking");
		assertEquals("low", thinking.getString("level"));
	}

	@Test
	void doubao_doubaoPro_ignored() {
		com.gdxsoft.ai.providers.doubao.RequestData rd =
				new com.gdxsoft.ai.providers.doubao.RequestData();
		rd.model("doubao-pro-256k");
		rd.thinkingBudget(4096);

		JSONObject body = rd.build();
		assertFalse(body.has("thinking"), "doubao-pro 不应写入 thinking 字段");
		assertEquals(4096, rd.getThinkingBudget());
	}

	@Test
	void doubao_zeroBudget_removesLevel() {
		com.gdxsoft.ai.providers.doubao.RequestData rd =
				new com.gdxsoft.ai.providers.doubao.RequestData();
		rd.model("doubao-seed-1-6-thinking-250615");
		rd.thinkingBudget(8192);
		rd.thinkingBudget(0);

		JSONObject body = rd.build();
		if (body.has("thinking")) {
			assertFalse(body.getJSONObject("thinking").has("level"));
		}
	}

	// ====================== OpenRouter ======================

	@Test
	void openrouter_writesReasoningMaxTokens() {
		com.gdxsoft.ai.providers.openrouter.RequestData rd =
				new com.gdxsoft.ai.providers.openrouter.RequestData();
		rd.model("anthropic/claude-3.7-sonnet:thinking");
		rd.thinkingBudget(4096);

		JSONObject body = rd.build();
		JSONObject reasoning = body.getJSONObject("reasoning");
		assertEquals(4096, reasoning.getInt("max_tokens"));
	}

	@Test
	void openrouter_zeroBudget_removesMaxTokens() {
		com.gdxsoft.ai.providers.openrouter.RequestData rd =
				new com.gdxsoft.ai.providers.openrouter.RequestData();
		rd.model("anthropic/claude-3.7-sonnet:thinking");
		rd.thinkingBudget(4096);
		rd.thinkingBudget(0);

		JSONObject body = rd.build();
		if (body.has("reasoning")) {
			assertFalse(body.getJSONObject("reasoning").has("max_tokens"));
		}
	}

	// ====================== OpenAI ======================

	@Test
	void openai_o3mini_mapsToMediumEffort() {
		com.gdxsoft.ai.providers.openai.RequestData rd =
				new com.gdxsoft.ai.providers.openai.RequestData();
		rd.model("o3-mini");
		rd.thinkingBudget(8192);

		JSONObject body = rd.build();
		assertEquals("medium", body.getString("reasoning_effort"));
	}

	@Test
	void openai_o1_mapsToLowEffort() {
		com.gdxsoft.ai.providers.openai.RequestData rd =
				new com.gdxsoft.ai.providers.openai.RequestData();
		rd.model("o1-mini");
		rd.thinkingBudget(2048);

		assertEquals("low", rd.build().getString("reasoning_effort"));
	}

	@Test
	void openai_gpt5_mapsToHighEffort() {
		com.gdxsoft.ai.providers.openai.RequestData rd =
				new com.gdxsoft.ai.providers.openai.RequestData();
		rd.model("gpt-5");
		rd.thinkingBudget(32768);

		assertEquals("high", rd.build().getString("reasoning_effort"));
	}

	@Test
	void openai_gpt4o_ignored() {
		com.gdxsoft.ai.providers.openai.RequestData rd =
				new com.gdxsoft.ai.providers.openai.RequestData();
		rd.model("gpt-4o");
		rd.thinkingBudget(8192);

		JSONObject body = rd.build();
		assertFalse(body.has("reasoning_effort"), "gpt-4o 不应写入 reasoning_effort");
		assertEquals(8192, rd.getThinkingBudget(), "缓存值仍保留");
	}

	@Test
	void openai_zeroBudget_removesReasoningEffort() {
		com.gdxsoft.ai.providers.openai.RequestData rd =
				new com.gdxsoft.ai.providers.openai.RequestData();
		rd.model("o3-mini");
		rd.thinkingBudget(8192);
		rd.thinkingBudget(0);

		assertFalse(rd.build().has("reasoning_effort"));
	}

	// ====================== Default / Base behavior ======================

	@Test
	void defaultBase_cachesValueButDoesNotEmit() {
		// OpenAI 兼容模式无 thinkingBudget override，复用 RequestDataBase 默认实现
		com.gdxsoft.ai.providers.openaiCompat.RequestData rd =
				new com.gdxsoft.ai.providers.openaiCompat.RequestData();
		rd.model("any-model");
		rd.thinkingBudget(2048);

		// 缓存值保留
		assertEquals(2048, rd.getThinkingBudget());
		// 默认实现不写入 parameters
		JSONObject params = rd.getParameters();
		assertFalse(params.has("thinking"));
		assertFalse(params.has("reasoning"));
		assertFalse(params.has("reasoning_effort"));
		assertFalse(params.has("thinkingConfig"));
	}

	// ====================== Model capability helpers ======================

	@Test
	void anthropicModelKeywords_detection() {
		assertTrue(AnthropicRequestData
				.isExtendedThinkingModel("claude-sonnet-4-20250514"));
		assertTrue(AnthropicRequestData
				.isExtendedThinkingModel("claude-opus-4-1-20250805"));
		assertTrue(AnthropicRequestData
				.isExtendedThinkingModel("claude-3-7-sonnet-20250219"));
		assertFalse(AnthropicRequestData
				.isExtendedThinkingModel("claude-3-5-sonnet-20241022"));
		assertFalse(AnthropicRequestData
				.isExtendedThinkingModel("claude-3-haiku-20240307"));
		assertFalse(AnthropicRequestData
				.isExtendedThinkingModel(null));
	}

	@Test
	void geminiModelKeywords_detection() {
		assertTrue(GeminiRequestData
				.isThinkingModel("gemini-2.5-pro"));
		assertTrue(GeminiRequestData
				.isThinkingModel("gemini-2.5-flash-lite"));
		assertFalse(GeminiRequestData
				.isThinkingModel("gemini-1.5-pro"));
		assertFalse(GeminiRequestData
				.isThinkingModel("gemini-2.0-flash"));
	}

	@Test
	void qwenModelKeywords_detection() {
		assertTrue(com.gdxsoft.ai.providers.qwen.RequestData
				.isThinkingModel("qwen3-max-thinking"));
		assertTrue(com.gdxsoft.ai.providers.qwen.RequestData
				.isThinkingModel("qwq-32b"));
		assertTrue(com.gdxsoft.ai.providers.qwen.RequestData
				.isThinkingModel("qvq-72b"));
		assertFalse(com.gdxsoft.ai.providers.qwen.RequestData
				.isThinkingModel("qwen-plus"));
		assertFalse(com.gdxsoft.ai.providers.qwen.RequestData
				.isThinkingModel("qwen-turbo"));
	}

	@Test
	void openaiReasoningModel_detection() {
		assertTrue(com.gdxsoft.ai.providers.openai.RequestData
				.isReasoningModel("o1"));
		assertTrue(com.gdxsoft.ai.providers.openai.RequestData
				.isReasoningModel("o1-mini-2024-09-12"));
		assertTrue(com.gdxsoft.ai.providers.openai.RequestData
				.isReasoningModel("o3-mini"));
		assertTrue(com.gdxsoft.ai.providers.openai.RequestData
				.isReasoningModel("gpt-5"));
		assertFalse(com.gdxsoft.ai.providers.openai.RequestData
				.isReasoningModel("gpt-4o"));
		assertFalse(com.gdxsoft.ai.providers.openai.RequestData
				.isReasoningModel("gpt-4"));
		assertFalse(com.gdxsoft.ai.providers.openai.RequestData
				.isReasoningModel("gpt-3.5-turbo"));
	}

	@Test
	void doubaoModelKeywords_detection() {
		assertTrue(com.gdxsoft.ai.providers.doubao.RequestData
				.isThinkingModel("doubao-seed-1-6-thinking-250615"));
		assertTrue(com.gdxsoft.ai.providers.doubao.RequestData
				.isThinkingModel("doubao-1-5-thinking-pro-250415"));
		assertTrue(com.gdxsoft.ai.providers.doubao.RequestData
				.isThinkingModel("deepseek-r1-250120"));
		assertTrue(com.gdxsoft.ai.providers.doubao.RequestData
				.isThinkingModel("deepseek-v3-1-250821"));
		assertFalse(com.gdxsoft.ai.providers.doubao.RequestData
				.isThinkingModel("doubao-pro-256k"));
		assertFalse(com.gdxsoft.ai.providers.doubao.RequestData
				.isThinkingModel("doubao-lite-32k"));
	}
}
