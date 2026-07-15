# Adding a New STT Provider to emp-script-ai

## Context

`emp-script-ai` provides an `ISttProvider` contract for speech-to-text in
`com.gdxsoft.ai.stt`. The companion skill `add-ai-provider/` covers chat-completion
providers; this skill is for **STT providers** (Whisper-style API, Azure Speech, Google
Speech, local Vosk/whisper.cpp wrappers).

Each STT provider is one file under `com.gdxsoft.ai.stt.providers.<name>` that extends
`SttProviderBase`. There is no chat-style "two-layer" split (style base + concrete
provider) because STT APIs vary more in auth/body shape — each provider implements its
own transport directly.

## Steps

### 1. Add to `SttProviderType` enum

Edit `src/main/java/com/gdxsoft/ai/stt/SttProviderType.java`. Append a new constant
with its lowercase identifier:

```java
VOSK("vosk"),
```

The `name` is the string used in `ai_settings.json` and `SttProviderFactory.create(String)`.

### 2. Create the provider package

```bash
mkdir -p src/main/java/com/gdxsoft/ai/stt/providers/vosk
```

### 3. Implement the provider

```java
package com.gdxsoft.ai.stt.providers.vosk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;
import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.stt.*;

public class VoskSttProvider extends SttProviderBase {
    public VoskSttProvider() { this.apiUrl = "http://localhost:8080/infer"; }

    @Override public SttProviderType getProviderType() { return SttProviderType.VOSK; }

    @Override
    public SttResponse transcribe(SttRequest req) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("audio_base64", java.util.Base64.getEncoder()
                .encodeToString(req.getAudio().materialize()));
        body.put("mime_type", req.getAudio().mimeType());
        if (req.getOptions().getLanguage() != null) body.put("language", req.getOptions().getLanguage());

        HttpResponse<String> resp = HttpUtils.createHttpClient().send(
                HttpRequest.newBuilder(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return parseResponse(new JSONObject(resp.body()), req);
    }

    @Override
    public String curl(SttRequest request) {
        try {
            return "curl -X POST '" + apiUrl + "' -H 'Content-Type: application/json' " +
                   "-d '" + buildBody(request).replace("'", "'\\''") + "'";
        } catch (IOException e) { return "curl: " + e.getMessage(); }
    }

    public SttResponse parseResponse(JSONObject root, SttRequest req) {
        String text = root.optString("text", "");
        String lang = root.optString("language",
                req.getOptions().getLanguage() == null ? null : req.getOptions().getLanguage());
        return new SttResponse(text, lang, null, null, root);
    }
}
```

### 4. Register in `SttProviderFactory`

Edit `src/main/java/com/gdxsoft/ai/stt/SttProviderFactory.java`:

```java
case VOSK -> new com.gdxsoft.ai.stt.providers.vosk.VoskSttProvider();
```

### 5. Add tests

Create `src/test/java/com/gdxsoft/ai/stt/VoskSttProviderParseTest.java`:

```java
class VoskSttProviderParseTest {
    @Test void parsesTextAndLanguage() throws Exception {
        VoskSttProvider p = new VoskSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"));
        SttResponse r = p.parseResponse(
                new JSONObject("{\"text\":\"hi\",\"language\":\"en\"}"), req);
        assertEquals("hi", r.getText());
        assertEquals("en", r.getLanguage());
    }
}
```

For an integration test, mirror `OpenAiSttProviderIntegrationTest.java` (HSQLDB +
`TestDatabase.isProviderConfigured` + `assumeTrue` skip pattern).

### 6. Update `ai_settings.json.example`

Add a template entry:

```json
"vosk": {
  "_comment": "Local Vosk HTTP server",
  "api_url": "http://localhost:8080/infer",
  "api_key": "",
  "model": ""
}
```

### 7. i18n

If your provider introduces new error conditions, add keys to
`ChatManagerI18nConstants.I18N_MESSAGES` and constants to a new inner class. Reuse
existing STT keys (`ERROR_STT_NO_API_KEY`, `ERROR_STT_HTTP_ERROR`, etc.) when possible.

## Reuse Tips

- **Multipart upload?** Use `com.gdxsoft.ai.stt.internal.MultipartSttSupport.buildBody(req)`
  and `MultipartSttSupport.send(body, url, authHeaderValue)`. Reuses the shared
  `HttpUtils.buildMultipart` helper.
- **JSON body?** Build a `JSONObject` and `POST` via `HttpUtils.createHttpClient()`.
- **Raw bytes?** (e.g. Azure short-audio REST) Use `HttpRequest.BodyPublishers.ofByteArray(audioBytes)`
  with a content-type header matching the audio MIME.
- **Local wrapper?** Default to JSON `{"audio_base64":"…","mime_type":"…"}` shape;
  users with custom wrappers can subclass and override `parseResponse`.

## Auth Header Conventions

| Auth | Header |
|---|---|
| OpenAI / OpenAI-compat | `Authorization: Bearer <KEY>` |
| Azure | `Ocp-Apim-Subscription-Key: <KEY>` |
| Google | `?key=<KEY>` query param OR OAuth bearer |
| Local | Optional `Authorization: Bearer` if apiKey non-blank |

Always **mask** the key in `curl(...)` output.

## Verification

```bash
mvn -DskipTests package              # main code compiles
mvn test -Dtest='VoskSttProviderParseTest'   # parse unit test
mvn test                              # full unit-test run
```