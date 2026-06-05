---
name: add-ai-provider
description: Procedure for adding a new AI provider to emp-script-ai (Java library wrapping multiple LLM providers via OpenAI-compatible interfaces)
source: auto-skill
extracted_at: '2026-06-05T01:43:21.257Z'
---

# Adding a New AI Provider to emp-script-ai

## Context

`emp-script-ai` is a Java 17 Maven project that wraps multiple LLM providers (Qwen, OpenAI, Gemini, Doubao, Grok, Tencent) behind unified `IRequestAI` and `IRequestData` interfaces. Most providers use OpenAI-compatible API endpoints.

## Steps

### 1. Add to `ProviderType` enum

Edit `src/main/java/com/gdxsoft/ai/request/ProviderType.java`:

```java
public enum ProviderType {
    // ... existing entries ...
    DEEPSEEK("deepseek");  // lowercase name used as identifier
```

### 2. Create provider package

```bash
mkdir -p src/main/java/com/gdxsoft/ai/providers/<provider_name>/
```

### 3. Create `RequestData.java`

```java
package com.gdxsoft.ai.providers.<provider_name>;

import org.json.JSONObject;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

public class RequestData extends RequestDataBase {
    public static String DEFAULT_MODEL_NAME = "<default-model-name>";

    public RequestData() {
        super(DEFAULT_MODEL_NAME);
        this.providerType = ProviderType.<PROVIDER_NAME>;
    }

    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        requestData.put("model", this.model);
        requestData.put("messages", messages);
        return requestData;
    }
}
```

- `parameters` (from base) holds `stream`, `temperature`, `top_p`, etc.
- `messages` (from base) is the `JSONArray` of conversation messages.
- If the provider has a **unique request format**, override methods like `thinking()`, `stream()`, or `responseFormat()`.

### 4. Create `RequestAI.java`

```java
package com.gdxsoft.ai.providers.<provider_name>;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;

public class RequestAI extends RequestAIBase {
    public static final String DEFAULT_URL = "https://<api-domain>/v1/chat/completions";

    public RequestAI() {
        this.providerType = ProviderType.<PROVIDER_NAME>;
    }
}
```

- **OpenAI-compatible providers**: The `RequestAIBase` already handles HTTP POST, SSE streaming, JSON extraction, curl generation, and token usage tracking. No overrides needed.
- **Non-compatible providers** (e.g., Gemini): Override `doStream()`, `extraceJson()`, `curl()`, and `createUrl()` as needed. See `com.gdxsoft.ai.providers.gemini.RequestAI` for reference.

### 5. Register in `RequestAIFactory`

Edit `src/main/java/com/gdxsoft/ai/request/RequestAIFactory.java`:

```java
case DEEPSEEK:
    return new com.gdxsoft.ai.providers.deepseek.RequestAI();
```

### 6. Register in `RequestDataFactory`

Edit `src/main/java/com/gdxsoft/ai/request/RequestDataFactory.java`:

**In `createRequestData(ProviderType type)`:**
```java
case DEEPSEEK:
    return new com.gdxsoft.ai.providers.deepseek.RequestData();
```

**In `inferProviderFromModel(String modelName)`:**
```java
// <ProviderName> model detection
if (lowerModelName.contains("<provider-keyword>")) {
    return ProviderType.<PROVIDER_NAME>;
}
```

### 7. Build and verify

```bash
mvn -DskipTests compile
```

Ensure `BUILD SUCCESS` and no new warnings/errors.

## Key Interfaces

| Interface | Purpose |
|-----------|---------|
| `IRequestAI` | HTTP request execution (POST/Stream), JSON extraction, curl generation |
| `IRequestData` | Request body construction (model, messages, parameters) |
| `RequestAIBase` | Base class with full HTTP/SSE handling for OpenAI-compatible APIs |
| `RequestDataBase` | Base class with message management and parameter building |

## Gotchas

- **API Key header**: `RequestAIBase.createHttpRequest()` handles `Authorization: Bearer` for all providers except Gemini (`x-goog-api-key`) and Anthropic (`x-api-key` + `anthropic-version`). If your provider uses a different header scheme, add a case in both `createHttpRequest()` and `curl()` in `RequestAIBase`.
- **Response format**: The base `extraceJson()` expects `choices[0].delta.content` (streaming) or `choices[0].message.content` (non-streaming). If the provider returns a different structure, override `extraceJson()` in `RequestAI`.
- **Model inference**: The `inferProviderFromModel()` method uses substring matching on model names. Choose keywords that won't cause false positives (e.g., `deepseek` won't collide with other providers).
- **Non-OpenAI-compatible providers**: Providers like Anthropic require:
  - Custom HTTP headers (`x-api-key`, `anthropic-version`) — add handling in `RequestAIBase.createHttpRequest()` and `curl()`
  - Custom request body format (e.g., `system` as a top-level field, mandatory `max_tokens`) — override `systemMessage()`, `maxTokens()`, `thinking()`, and `build()` in `RequestData`
  - Custom SSE event parsing (e.g., `content_block_delta`, `message_delta`) — override `doStream()` and `extraceJson()` in `RequestAI`
  - See `com.gdxsoft.ai.providers.anthropic.*` for a complete example
- **`thinking` parameter format**: Not all providers accept `thinking: boolean`. DeepSeek expects `{"thinking": {"type": "enabled"}}` when enabled and the field removed when disabled. Always override `thinking()` in `RequestData` if the provider has a non-standard format.
- **URL fallback**: `RequestAIBase` does NOT auto-fallback to `DEFAULT_URL` when `initUrlAndKey(null, apiKey)` is called with a null URL — it will throw NPE. Always pass `RequestAI.DEFAULT_URL` as the first argument, or ensure the provider's `RequestAI` overrides `createUrl()` to return `DEFAULT_URL` when `apiUrl` is null.
- **Test API keys**: Never hardcode API keys in test files. Use `System.getenv("PROVIDER_API_KEY")` so keys come from the environment.

## Generic Compat Providers

For users who need to connect to **arbitrary custom endpoints** that follow OpenAI or Anthropic formats, two generic providers exist:

### `openai_compat` — Generic OpenAI-compatible endpoint

- `providers/openai_compat/RequestData.java` — OpenAI format (`messages` array with `system` role), no default model/URL
- `providers/openai_compat/RequestAI.java` — Bearer Token auth, OpenAI SSE parsing
- **Usage**: Must call `initUrlAndKey("http://your-server/v1/chat/completions", apiKey)` — URL is required

### `anthropic_compat` — Generic Anthropic-compatible endpoint

- `providers/anthropic_compat/RequestData.java` — Anthropic format (`system` as top-level field, required `max_tokens`)
- `providers/anthropic_compat/RequestAI.java` — `x-api-key` + `anthropic-version` auth, Anthropic SSE parsing
- **Usage**: Must call `initUrlAndKey("http://your-server/v1/messages", apiKey)` — URL is required

Both are already registered in factories and `ProviderType`. No additional registration needed.

## Renaming an Existing Provider

If you need to rename a provider (e.g., `openroute` → `openrouter`):

1. **`ProviderType` enum**: Change both the enum constant name AND the string value (e.g., `OPENROUTE("openroute")` → `OPENROUTER("openrouter")`).
2. **New package**: Create `providers/<new_name>/` with `RequestData.java` and `RequestAI.java`, updating package declarations, `ProviderType` references, and `DEFAULT_URL` if needed.
3. **Factories**: Update both `RequestAIFactory` and `RequestDataFactory` switch cases to use the new enum constant and import path.
4. **Delete old package**: `rm -rf src/main/java/com/gdxsoft/ai/providers/<old_name>/`.
5. **Build**: `mvn -DskipTests compile` to verify.
