---
name: debug-sse-refactoring
description: Debugging SSE streaming failures after refactoring AI provider code, focusing on JSON null-safety and field ordering
source: auto-skill
extracted_at: '2026-06-17T07:21:35.326Z'
---

# Debugging SSE Streaming Failures After Provider Refactoring

When refactoring AI provider implementations (extracting provider-specific logic into shared base classes in `request/style/`), SSE streaming can silently fail with "no response" symptoms.

## Common Root Cause: JSON Null-Safety Pitfall

The `org.json.JSONObject` library has a critical difference between `getJSONObject()` and `optJSONObject()`:

- `json.has("field")` returns `true` even if the value is `null`
- `json.getJSONObject("field")` throws `JSONException` if the value is `null`
- `json.optJSONObject("field")` returns `null` if the value is `null` (safe)

**Example bug pattern:**
```java
// BUGGY: Throws exception when usage=null
if (json.has("usage")) {
    JSONObject usage = json.getJSONObject("usage");  // ← Throws!
    this.setTokensUsage(usage);
}

// FIXED: Safe null handling
JSONObject usageObj = json.optJSONObject("usage");
if (usageObj != null) {
    this.setTokensUsage(usageObj);
}
```

## Why This Breaks SSE Streaming

In OpenAI-compatible SSE streams (especially Qwen), every chunk may include `"usage": null`:
```
data: {"choices":[{"delta":{"content":"Hello"}}],"usage":null}
```

If the JSON parsing throws on `usage:null`:
1. Exception is caught → returns `RST=false`
2. `handleLine` checks `!json.getBoolean("RST")` → skips the chunk
3. **Every SSE chunk is rejected** → frontend receives nothing, `fullText` stays empty → nothing saved to database

## Debugging Methodology

When SSE fails after refactoring:

1. **Systematic directory-level diff**: Start by comparing entire source trees to identify all changed files
   ```bash
   # Compare two project directories to find all differences
   diff -rq old-project/src new-project/src | grep -v ".DS_Store"
   ```
   This quickly reveals which files changed, including new files (extracted base classes) and deleted files.

2. **Focus on the call chain**: Trace the SSE execution path from entry point to lowest level:
   - `AiStreamOrPost.processRequest()` → `executeRequest()` → `req.doStream()`
   - `RequestAIBase.doStream()` → `handleLine()` → `extraceJson()`
   - Compare each method in the chain between old and new implementations

3. **Check field processing order**: New code may check fields (like `usage`) before processing `choices`/`delta`, while old code processed `choices` first and ignored null fields

4. **Look for null-safety violations**: Search for `getJSONObject()`, `getJSONArray()`, `getString()` on optional fields that might be null

5. **Verify with actual SSE data**: Check provider documentation or logs for null fields in streaming chunks

## Fix Pattern

Replace unsafe accessors with optional variants:
- `getJSONObject()` → `optJSONObject()`
- `getJSONArray()` → `optJSONArray()`
- `getString()` → `optString()` (with default value)

Always check for null before using the result.

**Verified scope**: This bug affected all 8 OpenAI-compatible providers (qwen, deepseek, openai, doubao, grok, tencent, openrouter, openaiCompat) since they all inherit from `OpenAiRequestAI`. A single fix in the base class resolved the issue for all providers.

## Prevention

When extracting provider-specific JSON parsing into shared base classes:
- Preserve the original field processing order
- Use `opt*` methods for all optional fields
- Test with actual streaming data from each provider
- Verify that null values in optional fields don't break parsing
