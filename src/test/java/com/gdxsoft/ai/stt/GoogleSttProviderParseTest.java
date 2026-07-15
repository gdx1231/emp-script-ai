package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.stt.providers.google.GoogleSttProvider;

class GoogleSttProviderParseTest {

    @Test
    void parseFirstAlternative() throws IOException {
        GoogleSttProvider p = new GoogleSttProvider();
        JSONObject resp = new JSONObject(
                "{\"results\":[{\"alternatives\":[{\"transcript\":\"Hello world.\"}]}]}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withLanguage("en-US"));
        SttResponse r = p.parseResponse(resp, req);

        assertEquals("Hello world.", r.getText());
        assertEquals("en-US", r.getLanguage());
    }

    @Test
    void parseMultipleResultsJoined() throws IOException {
        GoogleSttProvider p = new GoogleSttProvider();
        JSONObject resp = new JSONObject(
                "{\"results\":[" +
                "  {\"alternatives\":[{\"transcript\":\"Hello.\"}]}," +
                "  {\"alternatives\":[{\"transcript\":\"World.\"}]}" +
                "]}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withLanguage("en-US"));
        SttResponse r = p.parseResponse(resp, req);
        assertEquals("Hello. World.", r.getText());
    }

    @Test
    void emptyResultsHandledGracefully() throws IOException {
        GoogleSttProvider p = new GoogleSttProvider();
        JSONObject resp = new JSONObject("{\"results\":[]}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withLanguage("en-US"));
        SttResponse r = p.parseResponse(resp, req);
        assertEquals("", r.getText());
    }

    @Test
    void mimeEncodingMapping() {
        assertEquals("LINEAR16", GoogleSttProvider.mapEncoding("audio/wav"));
        assertEquals("MP3", GoogleSttProvider.mapEncoding("audio/mpeg"));
        assertEquals("OGG_OPUS", GoogleSttProvider.mapEncoding("audio/opus"));
        assertEquals("ENCODING_UNSPECIFIED", GoogleSttProvider.mapEncoding("audio/webm"));
        assertEquals("ENCODING_UNSPECIFIED", GoogleSttProvider.mapEncoding(null));
    }
}