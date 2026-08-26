package com.gdxsoft.ai.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.platform.adapters.OpenAiCompatibleAdapter;
import com.gdxsoft.ai.request.ProviderType;

/**
 * 适配器 JSON 解析逻辑测试（mock HTTP 响应）
 */
class AdapterParsingTest {

    @Test
    void testOpenAiCompatibleParseModelsResponse() throws Exception {
        JSONObject response = new JSONObject();
        JSONArray data = new JSONArray();
        data.put(new JSONObject().put("id", "gpt-4o").put("object", "model").put("created", 1715367049).put("owned_by", "system"));
        data.put(new JSONObject().put("id", "gpt-4o-mini").put("object", "model").put("created", 1721172741).put("owned_by", "system"));
        data.put(new JSONObject().put("id", "custom-model").put("object", "model").put("created", 1700000000).put("owned_by", "org-xxx"));
        response.put("data", data);
        response.put("object", "list");

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(ProviderType.OPENAI);
        Method method = OpenAiCompatibleAdapter.class.getDeclaredMethod("parseModelsResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ModelInfo> models = (List<ModelInfo>) method.invoke(adapter, response.toString());

        assertEquals(3, models.size());
        assertEquals("gpt-4o", models.get(0).getId());
        assertEquals("openai", models.get(0).getProvider());
        assertEquals("gpt-4o-mini", models.get(1).getId());
        assertEquals("custom-model", models.get(2).getId());
        assertEquals("org-xxx", models.get(2).getOwner());
    }

    @Test
    void testBuildModelsUrl() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(ProviderType.OPENAI);
        Method method = OpenAiCompatibleAdapter.class.getDeclaredMethod("buildModelsUrl", String.class);
        method.setAccessible(true);

        // apiUrl 以 /v1 结尾 → 追加 /models
        assertEquals("https://api.openai.com/v1/models",
                method.invoke(adapter, "https://api.openai.com/v1"));
        assertEquals("https://api.openai.com/v1/models",
                method.invoke(adapter, "https://api.openai.com/v1/"));

        // 包含 /chat/completions → 替换为 /models
        assertEquals("https://api.openai.com/v1/models",
                method.invoke(adapter, "https://api.openai.com/v1/chat/completions"));

        // 包含 /models → 保留
        assertEquals("https://api.openai.com/v1/models",
                method.invoke(adapter, "https://api.openai.com/v1/models"));

        // 纯域名 → 追加 /v1/models
        assertEquals("https://api.deepseek.com/v1/models",
                method.invoke(adapter, "https://api.deepseek.com"));
    }

    @Test
    void testOpenAiCompatibleEmptyResponse() throws Exception {
        JSONObject response = new JSONObject();
        response.put("data", new JSONArray());
        response.put("object", "list");

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(ProviderType.DEEPSEEK);
        Method method = OpenAiCompatibleAdapter.class.getDeclaredMethod("parseModelsResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ModelInfo> models = (List<ModelInfo>) method.invoke(adapter, response.toString());
        assertTrue(models.isEmpty());
    }

    @Test
    void testOpenAiCompatibleNoDataField() throws Exception {
        JSONObject response = new JSONObject();
        response.put("object", "list");

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(ProviderType.GROK);
        Method method = OpenAiCompatibleAdapter.class.getDeclaredMethod("parseModelsResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ModelInfo> models = (List<ModelInfo>) method.invoke(adapter, response.toString());
        assertTrue(models.isEmpty());
    }

    @Test
    void testQwenAdapterBuildModelsUrl() throws Exception {
        com.gdxsoft.ai.platform.adapters.QwenAdapter adapter = new com.gdxsoft.ai.platform.adapters.QwenAdapter();
        Method method = com.gdxsoft.ai.platform.adapters.QwenAdapter.class.getDeclaredMethod("buildModelsUrl", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(adapter, "https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/models", result);
    }

    @Test
    void testOpenRouterAdapterBuildModelsUrl() throws Exception {
        com.gdxsoft.ai.platform.adapters.OpenRouterAdapter adapter = new com.gdxsoft.ai.platform.adapters.OpenRouterAdapter();
        Method method = com.gdxsoft.ai.platform.adapters.OpenRouterAdapter.class.getDeclaredMethod("buildModelsUrl", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(adapter, "https://openrouter.ai/api/v1");
        assertEquals("https://openrouter.ai/api/v1/models", result);
    }

    @Test
    void testParseModelFieldsFromApi() throws Exception {
        // 验证 API 返回的字段能正确映射到 ModelInfo
        JSONObject response = new JSONObject();
        JSONArray data = new JSONArray();
        data.put(new JSONObject()
                .put("id", "gpt-4o")
                .put("object", "model")
                .put("created", 1715367049)
                .put("owned_by", "system"));
        response.put("data", data);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(ProviderType.OPENAI);
        Method method = OpenAiCompatibleAdapter.class.getDeclaredMethod("parseModelsResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ModelInfo> models = (List<ModelInfo>) method.invoke(adapter, response.toString());

        ModelInfo info = models.get(0);
        assertEquals("gpt-4o", info.getId());
        assertEquals("openai", info.getProvider());
        assertEquals("gpt-4o", info.getName());
        assertEquals("active", info.getStatus());
        // API 未返回的字段为 null
        assertNull(info.getContextWindow());
        assertNull(info.getInputPricePer1M());
    }
}
