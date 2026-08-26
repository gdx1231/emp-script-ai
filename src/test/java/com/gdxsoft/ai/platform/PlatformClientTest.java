package com.gdxsoft.ai.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.platform.adapters.*;
import com.gdxsoft.ai.request.ProviderType;

/**
 * PlatformClient 单元测试
 */
class PlatformClientTest {

    @Test
    void testGetAdapterForAllProviders() {
        assertNotNull(PlatformClient.getAdapter(ProviderType.OPENAI));
        assertNotNull(PlatformClient.getAdapter(ProviderType.QWEN));
        assertNotNull(PlatformClient.getAdapter(ProviderType.DOUBAO));
        assertNotNull(PlatformClient.getAdapter(ProviderType.ANTHROPIC));
        assertNotNull(PlatformClient.getAdapter(ProviderType.GEMINI));
        assertNotNull(PlatformClient.getAdapter(ProviderType.DEEPSEEK));
        assertNotNull(PlatformClient.getAdapter(ProviderType.GROK));
        assertNotNull(PlatformClient.getAdapter(ProviderType.OPENROUTER));
        assertNotNull(PlatformClient.getAdapter(ProviderType.TENCENT));
        assertNotNull(PlatformClient.getAdapter(ProviderType.OPENAI_COMPAT));
        assertNotNull(PlatformClient.getAdapter(ProviderType.ANTHROPIC_COMPAT));
    }

    @Test
    void testAdapterTypes() {
        assertInstanceOf(OpenAiAdapter.class, PlatformClient.getAdapter(ProviderType.OPENAI));
        assertInstanceOf(QwenAdapter.class, PlatformClient.getAdapter(ProviderType.QWEN));
        assertInstanceOf(DoubaoAdapter.class, PlatformClient.getAdapter(ProviderType.DOUBAO));
        assertInstanceOf(AnthropicAdapter.class, PlatformClient.getAdapter(ProviderType.ANTHROPIC));
        assertInstanceOf(GeminiAdapter.class, PlatformClient.getAdapter(ProviderType.GEMINI));
        assertInstanceOf(DeepSeekAdapter.class, PlatformClient.getAdapter(ProviderType.DEEPSEEK));
        assertInstanceOf(GrokAdapter.class, PlatformClient.getAdapter(ProviderType.GROK));
        assertInstanceOf(OpenRouterAdapter.class, PlatformClient.getAdapter(ProviderType.OPENROUTER));
        assertInstanceOf(TencentAdapter.class, PlatformClient.getAdapter(ProviderType.TENCENT));
    }

    @Test
    void testListAllModelsWithEmptyConfigs() {
        PlatformClient client = new PlatformClient();
        List<ModelInfo> result = client.listAllModels(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testListAllModelsHandlesFailure() {
        PlatformClient client = new PlatformClient();
        ProviderConfig badConfig = new ProviderConfig(ProviderType.OPENAI, "http://invalid-host-xyz", "bad-key");
        List<ModelInfo> result = client.listAllModels(List.of(badConfig));
        assertTrue(result.isEmpty());
    }

    @Test
    void testListModelsNullProvider() {
        PlatformClient client = new PlatformClient();
        // null provider 会抛异常（NPE 或 IllegalArgumentException）
        assertThrows(Exception.class,
                () -> client.listModels(null, "http://example.com", "key"));
    }
}
