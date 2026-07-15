package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.stt.providers.openai.OpenAiSttProvider;
import com.gdxsoft.ai.test.TestDatabase;

/**
 * Integration test for OpenAI Whisper. Skipped when no API key is configured.
 */
class OpenAiSttProviderIntegrationTest {

    private static final String FIXTURE = "/fixtures/hello.wav";

    @BeforeAll
    static void setup() {
        try {
            TestDatabase.init();
        } catch (Exception e) {
            // ewa_conf.xml not configured — skip integration tests via assumeTrue in test methods.
        }
    }

    @AfterAll
    static void teardown() {
        try { TestDatabase.shutdown(); } catch (Exception ignore) {}
    }

    @Test
    void transcribeWavFile() throws Exception {
        assumeTrue(TestDatabase.isProviderConfigured("openai_stt"),
                "openai_stt not configured in ai_settings.json — skipping integration test");

        Path wav = extractFixture();
        OpenAiSttProvider p = (OpenAiSttProvider) SttProviderFactory.create("openai_stt");
        SttResponse r = p.transcribe(new SttRequest(AudioSource.fromFile(wav)));
        assertNotNull(r.getText());
        assertFalse(r.getText().isBlank(), "expected non-blank transcript");
    }

    /**
     * Extract the bundled fixture to a temp file and return its path.
     * Falls back to skipping if the fixture is not present in test-classes.
     */
    private Path extractFixture() throws IOException {
        Path tmp = Files.createTempFile("hello-", ".wav");
        try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
            assumeTrue(in != null, "fixture not present at " + FIXTURE + " — skipping");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }
}