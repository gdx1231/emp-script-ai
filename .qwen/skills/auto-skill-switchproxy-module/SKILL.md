---
name: switchproxy-module
description: Architecture and implementation patterns for the AI API proxy/gateway module (switchproxy) that converts between OpenAI/Anthropic/Responses API formats, with IP access control, key-based auth, multi-host listening, HTML status page, Web admin UI, and tool config export
source: auto-skill
extracted_at: '2026-06-23T09:02:21.129Z'
---

# Switchproxy Module Architecture

## Overview

`com.gdxsoft.ai.switchproxy` is an AI API proxy/gateway built on JDK `com.sun.net.httpserver.HttpServer`. It exposes OpenAI-compatible endpoints and forwards requests to real AI providers, converting between different API formats as needed.

## Core Architecture

### Handler Hierarchy (Template Method Pattern)

```
ProxyHandler (abstract base)
├── PassthroughHandler          # Direct forwarding, no conversion
├── Chat2AnthropicHandler       # OpenAI Chat → Anthropic Messages
├── Chat2ResponsesHandler       # OpenAI Chat → Responses API
└── Responses2AnthropicHandler  # Responses API → Anthropic Messages (for Codex → Anthropic providers)
```

**ProxyHandler** encapsulates common proxy logic:
- Read request body
- Build forward request (abstract: `buildForwardBody`, `buildForwardHeaders`)
- Send HTTP request via `java.net.http.HttpClient`
- Handle SSE stream (abstract: `handleSseLine`)
- Write XML log after completion

Subclasses implement format-specific conversion and SSE line handling.

### Format Converters

Stateful converters (one instance per request):

| Converter | Direction | Key Mappings |
|-----------|-----------|--------------|
| `OpenAiToAnthropic` | Request: OpenAI → Anthropic | `messages[{role:"system"}]` → `system`, `tools[].function.parameters` → `tools[].input_schema` |
| `AnthropicToOpenAi` | Response: Anthropic SSE → OpenAI SSE | `content_block_delta` → `choices[].delta.content`, `message_delta.stop_reason` → `finish_reason` |
| `ChatToResponses` | Request: OpenAI → Responses | `messages` → `input[]` + `instructions`, `max_tokens` → `max_output_tokens` |
| `ResponsesToChat` | Response: Responses SSE → OpenAI SSE | `response.output_text.delta` → `choices[].delta.content`, `response.completed` → `finish_reason` + `[DONE]` |
| `ResponsesToAnthropic` | Request: Responses → Anthropic | `input[]` → `messages[]`, `instructions` → `system`, `function_call_output` → user message with tool result |
| `AnthropicToResponses` | Response: Anthropic SSE → Responses SSE | `message_start` → `response.created`, `content_block_delta` → `response.output_text.delta`, `message_stop` → `response.completed` |

### Two-Phase Log Building

**Phase 1: Streaming Accumulation (during SSE)**
- `appendOutput(chunk)` / `appendThinking(chunk)` → append to text buffers
- `appendRawSseLine(line)` → accumulate raw SSE lines (for phase 2 only, not written to log)

**Phase 2: Structured Extraction (after SSE ends)**
- `finalize(upstreamFormat)` → parse accumulated SSE lines, extract `finishReason`, `responseId`, `toolCalls`, `usage`
- Different parsing strategies for OpenAI / Anthropic / Responses formats
- `RequestLogger.log(entry)` → serialize to XML with CDATA safety

### Configuration-Driven Routing

```xml
<switch>
  <server host="0.0.0.0" port="8180" 
          allow-ips="192.168.1.2,192.168.2.0/24,::1,fe80::/10" />
  
  <profiles>
    <profile name="claude" api-url="..." api-key="..." model="..." />
  </profiles>
  <routes>
    <route path="/claude/anthropic/v1" mode="chat2anthropic" profile="claude" />
  </routes>
  
  <access-keys>
    <access-key key="emai-switch-abc123..." name="dev-key" 
                created-at="2026-06-23 14:00:00" enabled="true" />
  </access-keys>
</switch>
```

- **Profile**: reusable provider config (api-url, api-key, model, max-tokens, **format**)
- **Route**: URL path → mode + profile reference
- **Mode**: `passthrough` / `chat2anthropic` / `chat2responses` / `responses2anthropic`
- Effective values: route model > profile model > client original

### ⚠️ Format Field — Must Be Explicitly Stored

**Critical design point**: The `format` (openai/anthropic/responses) MUST be stored as an explicit attribute on `<profile>`, NOT inferred from the API URL.

**Why**: URL-based inference is unreliable — e.g., `https://api.deepseek.com/anthropic` contains "anthropic" but a user might configure it as `openai` format for a different conversion path. The user's explicit choice must be preserved.

```xml
<!-- Correct: format is explicit -->
<profile name="deepseek" api-url="https://api.deepseek.com/anthropic/v1/messages"
         api-key="sk-xxx" model="deepseek-v4-pro" format="anthropic" />

<!-- Wrong: format missing, will fall back to URL inference -->
<profile name="deepseek" api-url="https://api.deepseek.com/anthropic/v1/messages"
         api-key="sk-xxx" model="deepseek-v4-pro" />
```

**Implementation pattern**: All display/routing code should check `profile.getFormat()` first, only fall back to `inferFormat(apiUrl)` when format is null/empty:

```java
String format = profile.getFormat();
if (format == null || format.isEmpty()) {
    format = inferFormat(profile.getApiUrl()); // fallback only
}
```

**AdminHandler edit form** includes a format `<select>` dropdown pre-populated with the current format, allowing users to change it. Changing format updates both the profile and the route (path + mode).

### Access Control

**IP Access Control (`IpAccessController`)**
- Configured via `allow-ips` attribute on `<server>` element
- Supports IPv4, IPv6, and CIDR notation (comma-separated)
- Examples: `192.168.1.2`, `192.168.2.0/24`, `::1`, `fe80::/10`, `0.0.0.0/0`
- Empty/null means allow all
- Checked via `X-Forwarded-For` → `X-Real-IP` → socket address
- Returns 403 if denied

**Access Key Authentication (`AccessKeyConfig`)**
- Key format: `emai-switch-{uuid}` (12 prefix + 32 hex = 44 chars)
- Configured in `<access-keys>` section
- If any keys exist, all requests must provide a valid key
- Extracted from `X-Access-Key` header or `Authorization: Bearer {key}`
- Returns 401 if invalid/missing
- Tracks `last-used-at` timestamp
- Can be enabled/disabled per key

### Multi-Host Listening

`SwitchServer` supports binding to multiple IPv4/IPv6 addresses simultaneously:

```xml
<server host="0.0.0.0,::" port="8180" />
<!-- or -->
<server host="192.168.1.2,::1,fe80::1" port="8180" />
```

- `host` attribute accepts comma-separated addresses
- Each address creates an independent `HttpServer` instance
- All servers share the same thread pool (`ExecutorService`) and route handlers
- IPv6 brackets are auto-stripped (`[::]` → `::`)
- Default: `0.0.0.0` (IPv4 only)

```java
static List<String> parseHosts(String hostConfig) {
    // Splits by comma, strips brackets, defaults to "0.0.0.0"
}
```

### HTML Status Page (`StatusHandler`)

Registered at root path `/`, serves a dynamic HTML dashboard:
- Server info (host, port, log directory)
- **Localhost detection**: shows admin features only from `127.0.0.1`/`::1`
- **Admin mode**: displays API keys, access keys, management links
- **Display host**: converts `0.0.0.0` → `localhost`, `::` → `[::1]` for user-friendly URLs (never show `0.0.0.0` to users — it's a bind address, not a client-accessible URL)
- Profiles table (name, model, format badge, API URL, API key if admin)
- Routes table (path, mode, profile, full URL with copy button)
- Usage example (curl command)
- Color-coded format badges: OpenAI (green), Anthropic (tan), Responses (purple)
- Links to `/admin` for web-based management

```java
server.createContext("/", new StatusHandler(config));
```

### Web Admin UI (`AdminHandler`)

Registered at `/admin`, provides full CRUD operations via web forms:
- **Localhost-only for mutations**: POST operations return 403 for remote access
- **Config viewing**: GET `/admin/claude-config` and `/admin/codex-config` accessible from both local and remote
- **Provider management**: add/edit/delete providers with web forms
- **Access key management**: add/delete keys
- **Auto-save**: all changes persist to `switch.settings.xml`
- **Redirect**: POST operations redirect back to `/admin`

**Endpoints:**
- `GET /admin` — management dashboard
- `GET /admin/add-provider` — add provider form
- `GET /admin/edit-provider?name=xxx` — edit provider form (includes format dropdown)
- `POST /admin/add-provider` — create provider + route
- `POST /admin/edit-provider` — update provider (format changes update route path + mode)
- `POST /admin/delete-provider` — delete provider + route
- `GET /admin/add-key` — add key form
- `POST /admin/add-key` — generate new key
- `POST /admin/delete-key` — remove key
- `GET /admin/claude-config` — view Claude Code config JSON (local: export button; remote: JSON only)
- `GET /admin/codex-config` — view Codex config JSON (local: export button; remote: JSON only)
- `POST /admin/export-claude` — write config to `~/.claude/settings.json` (localhost only)
- `POST /admin/export-codex` — write config to `~/.codex/config.json` (localhost only)

```java
server.createContext("/admin", new AdminHandler(config));
```

### Claude Code / Codex Config Export

Generates tool-specific configuration JSON pointing to the proxy server, based on existing provider profiles.

**Claude Code config** (from first Anthropic-format profile):
```json
{
  "env": {
    "ANTHROPIC_BASE_URL": "http://localhost:8180/{profile}/anthropic/v1",
    "ANTHROPIC_AUTH_TOKEN": "{api-key}",
    "ANTHROPIC_MODEL": "{model}",
    "ANTHROPIC_DEFAULT_SONNET_MODEL": "{model}",
    "ANTHROPIC_DEFAULT_OPUS_MODEL": "{model}",
    "ANTHROPIC_DEFAULT_HAIKU_MODEL": "{model}",
    "CLAUDE_CODE_SUBAGENT_MODEL": "{model}",
    "API_TIMEOUT_MS": "3000000",
    "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC": "1",
    "CLAUDE_CODE_SIMPLE": "1"
  },
  "skipDangerousModePermissionPrompt": true
}
```

**Codex config** (from first OpenAI-format profile):
```json
{
  "env": {
    "OPENAI_BASE_URL": "http://localhost:8180/{profile}/openai/v1",
    "OPENAI_API_KEY": "{api-key}",
    "OPENAI_MODEL": "{model}",
    "CODEX_MODEL": "{model}",
    "API_TIMEOUT_MS": "3000000"
  },
  "skipDangerousModePermissionPrompt": true
}
```

**Access behavior:**
- **Localhost**: shows JSON + "📥 导出到本地" button that overwrites the local config file
- **Remote**: shows JSON only with warning to manually copy
- Links also appear on the main status page (`/`) under "📤 工具配置" section

**Profile lookup**: `findProfileByFormat("anthropic")` for Claude, `findProfileByFormat("openai")` for Codex, with fallback chain.

## Key Implementation Details

### SSE Stream Handling

```java
HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
exchange.sendResponseHeaders(200, 0);

try (Stream<String> lines = response.body()) {
    lines.forEach(line -> {
        entry.appendRawSseLine(line);
        handleSseLine(line, clientOut, entry);  // subclass converts + writes
        clientOut.flush();
    });
}
```

### CDATA Safety in XML Logs

- Use `Document.createCDATASection(text)` — never manual string concatenation
- JDK serializer auto-splits `]]>` into `]]]]><![CDATA[>`
- `DocumentBuilderFactory` with `disallow-doctype-decl=true` prevents XXE

### Model Replacement

All modes replace the client's `model` field with the configured value:
- Priority: route.model > profile.model > client original
- Ensures requests hit the intended provider model regardless of client request

## Testing Patterns

### Converter Tests
- Test request conversion field mappings
- Test SSE event-by-event response conversion
- Test edge cases: multiple system messages, tool calls, thinking content

### Logger Tests
- CDATA escaping (`]]>` auto-split)
- XXE protection (DTD injection rejected)
- Full document structure (all nodes/attributes present)
- Finalize extraction (OpenAI/Anthropic/Responses SSE parsing)
- Raw SSE lines not persisted to log

### Config Tests
- Load/save round-trip (including access-keys and allow-ips)
- Effective value priority (route > profile)
- Path resolution (`~` expansion)

### IP Access Control Tests
- Single IPv4/IPv6 matching
- CIDR range matching (IPv4 /24, /8; IPv6 /10)
- `0.0.0.0/0` and `::/0` for allow-all (per protocol)
- Multiple rules (comma-delimited parsing)
- Invalid rules ignored gracefully
- Null/empty means allow all

### Access Key Tests
- Key format validation (`emai-switch-` + 32 hex)
- Unique generation
- Enable/disable
- Usage tracking (`last-used-at`)

## File Structure

```
com.gdxsoft.ai.switchproxy/
├── SwitchServer.java          # HttpServer entry point (multi-host support)
├── SwitchCli.java             # CLI commands (interactive mode supported)
├── SwitchConfig.java          # XML config parser (profiles, routes, access-keys, allow-ips)
├── ProfileConfig.java         # Profile POJO (name, apiUrl, apiKey, model, format, maxTokens)
├── RouteConfig.java           # Route POJO
├── IpAccessController.java    # IP/CIDR access control (IPv4+IPv6)
├── AccessKeyConfig.java       # Access key POJO (emai-switch-{uuid})
├── handler/
│   ├── ProxyHandler.java      # Base class (template method + IP/Key validation)
│   ├── PassthroughHandler.java
│   ├── Chat2AnthropicHandler.java
│   ├── Chat2ResponsesHandler.java
│   ├── StatusHandler.java     # HTML dashboard at "/" (localhost admin mode)
│   └── AdminHandler.java      # Web admin UI at "/admin" (localhost-only CRUD)
├── converter/
│   ├── IFormatConverter.java  # Interface (unused, converters are static)
│   ├── OpenAiToAnthropic.java
│   ├── AnthropicToOpenAi.java
│   ├── ChatToResponses.java
│   └── ResponsesToChat.java
├── entry/
│   └── RequestLogEntry.java   # Two-phase log builder
└── logger/
    └── RequestLogger.java     # DOM XML writer

bin/
├── start.sh                   # macOS/Linux startup + CLI wrapper
└── start.bat                  # Windows startup + CLI wrapper
```

## Startup & CLI

```bash
# macOS/Linux
./bin/start.sh              # Start proxy server
./bin/start.sh list         # List config
./bin/start.sh add-provider # Add provider (interactive if params missing)

# Windows
bin\start.bat

# CLI commands
./bin/start.sh add-provider          # Interactive provider setup
./bin/start.sh add-key               # Generate emai-switch-{uuid} key
./bin/start.sh list-keys             # List all access keys
./bin/start.sh remove-key            # Interactive key removal
./bin/start.sh allow-ip              # Set IP access rules (interactive)
./bin/start.sh allow-ip --ips "192.168.1.0/24,::1"  # Direct IP rules
```

Config: `~/.emp-script-ai/switch.settings.xml`
Logs: `~/.emp-script-ai/logs/{yyyy-MM-dd}/{request-id}.xml`
