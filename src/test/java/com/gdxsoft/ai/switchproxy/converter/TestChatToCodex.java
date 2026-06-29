
package com.gdxsoft.ai.switchproxy.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;

/**
 * Test Chat → Responses (Codex) conversion with coding example.
 */
public class TestChatToCodex {
    @Test
    void testChatToCodex_codingExample() {
        // Sample Chat Completions request for coding
        JSONObject chatReq = new JSONObject();
        chatReq.put("model", "gpt-4o");
        chatReq.put("stream", true);
        chatReq.put("temperature", 0.7);
        chatReq.put("max_tokens", 2048);

        // Add messages
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "You are a helpful coding assistant. Write clean, well-commented code."));
        messages.put(new JSONObject().put("role", "user").put("content", "Write a Java function to reverse a string, with tests."));
        chatReq.put("messages", messages);

        // Route & Profile
        RouteConfig route = new RouteConfig();
        route.setModel("codex-mini");
        ProfileConfig profile = new ProfileConfig();

        // Convert
        JSONObject responsesReq = ChatToResponses.convert(chatReq, route, profile);

        // Verify the conversion
        assertEquals("codex-mini", responsesReq.getString("model"));
        assertTrue(responsesReq.getBoolean("stream"));
        assertEquals(0.7, responsesReq.getDouble("temperature"));
        assertEquals(2048, responsesReq.getInt("max_output_tokens"));
        assertEquals("You are a helpful coding assistant. Write clean, well-commented code.", responsesReq.getString("instructions"));

        JSONArray input = responsesReq.getJSONArray("input");
        assertEquals(1, input.length());
        JSONObject userInput = input.getJSONObject(0);
        assertEquals("user", userInput.getString("role"));
        JSONArray content = userInput.getJSONArray("content");
        assertEquals(1, content.length());
        assertEquals("input_text", content.getJSONObject(0).getString("type"));
        assertEquals("Write a Java function to reverse a string, with tests.", content.getJSONObject(0).getString("text"));
    }
}
