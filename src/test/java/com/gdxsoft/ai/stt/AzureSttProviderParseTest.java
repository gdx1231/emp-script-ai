package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.stt.providers.azure.AzureSttProvider;

class AzureSttProviderParseTest {

    @Test
    void parseSuccessResponse() throws IOException {
        AzureSttProvider p = new AzureSttProvider();
        p.setApiKey("dummy");
        p.setConfig("region", "eastus");
        JSONObject resp = new JSONObject(
                "{\"RecognitionStatus\":\"Success\",\"DisplayText\":\"Hello world.\"," +
                "\"Offset\":1000000,\"Duration\":32000000}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withLanguage("en-US"));
        SttResponse r = p.parseResponse(resp, req);

        assertEquals("Hello world.", r.getText());
        assertEquals("en-US", r.getLanguage());
        assertEquals(3.2, r.getDuration(), 1e-6);
    }

    @Test
    void buildUrlContainsRegionAndLanguage() throws IOException {
        AzureSttProvider p = new AzureSttProvider();
        p.setApiKey("dummy");
        p.setConfig("region", "eastus");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withLanguage("zh-CN"));
        String url = p.buildUrl(req, "eastus");
        assertTrue(url.startsWith("https://eastus.stt.speech.microsoft.com/"));
        assertTrue(url.contains("language=zh-CN"));
    }

    @Test
    void defaultLanguageFromConfigWhenOptionsEmpty() throws IOException {
        AzureSttProvider p = new AzureSttProvider();
        p.setConfig("region", "eastus");
        p.setConfig("language", "fr-FR");
        SttRequest req = new SttRequest(AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"));
        String url = p.buildUrl(req, "eastus");
        assertTrue(url.contains("language=fr-FR"));
    }

    @Test
    void missingRegionThrows() {
        AzureSttProvider p = new AzureSttProvider();
        p.setApiKey("dummy");
        SttRequest req = new SttRequest(AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"));
        assertThrows(IllegalStateException.class, () -> p.buildUrl(req, null));
    }

    @Test
    void missingApiKeyThrows() {
        AzureSttProvider p = new AzureSttProvider();
        p.setConfig("region", "eastus");
        SttRequest req = new SttRequest(AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"));
        assertThrows(IllegalStateException.class, () -> {
            try {
                p.transcribe(req);
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        });
    }
}