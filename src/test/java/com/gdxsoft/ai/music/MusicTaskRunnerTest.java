package com.gdxsoft.ai.music;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test the unified async music mode (submit/poll + MusicTaskRunner).
 *
 * <p>Uses a fake provider on top of {@link MusicProviderBase}'s local async
 * fallback — no external services required.
 *
 * @since 1.5.0
 */
class MusicTaskRunnerTest {

    /** 假 provider：可控制 generate 的延迟与成功/失败 */
    private static class FakeMusicProvider extends MusicProviderBase {
        private final long delayMs;
        private final boolean fail;

        FakeMusicProvider(long delayMs, boolean fail) {
            this.delayMs = delayMs;
            this.fail = fail;
        }

        @Override
        public MusicProviderType getProviderType() {
            return MusicProviderType.MINIMAX;
        }

        @Override
        public MusicResponse generate(MusicRequest request)
                throws IOException, InterruptedException {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            if (fail) {
                throw new IOException("模拟生成失败");
            }
            return new MusicResponse("00ff10", null, 0, "fake-trace", null,
                    new JSONObject().put("fake", true));
        }

        @Override
        public String curl(MusicRequest request) {
            return "curl fake";
        }

        @Override
        public MusicCoverPreprocessResponse preprocessCover(MusicCoverPreprocessRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MusicLyricsResponse generateLyrics(MusicLyricsRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String curl(MusicCoverPreprocessRequest request) {
            return "cover-curl";
        }

        @Override
        public String curl(MusicLyricsRequest request) {
            return "lyrics-curl";
        }
    }

    /** 轮询直到终态（最多 5s） */
    private static MusicTaskStatus pollUntilDone(
            IMusicProvider p, String taskId, MusicOptions opts) throws Exception {
        for (int i = 0; i < 50; i++) {
            MusicTaskStatus st = p.pollTask(taskId, opts);
            if (!st.isProcessing()) {
                return st;
            }
            Thread.sleep(100);
        }
        fail("任务未在 5s 内完成");
        return null;
    }

    @Test
    @DisplayName("本地异步回退：提交后轮询成功")
    void localFallbackSucceeds() throws Exception {
        FakeMusicProvider provider = new FakeMusicProvider(200, false);
        MusicOptions opts = new MusicOptions().model("music-3.0");

        MusicTaskSubmit submit = provider.submitTask(new MusicRequest("测试提示词", opts));
        assertTrue(submit.getTaskId().startsWith("local-"));
        assertNull(submit.getRaw());

        MusicTaskStatus status = pollUntilDone(provider, submit.getTaskId(), opts);
        assertTrue(status.isSucceeded());
        assertNotNull(status.getResponse());
        assertEquals("00ff10", status.getResponse().getAudioHex());
        assertNull(status.getError());
    }

    @Test
    @DisplayName("本地异步回退：生成失败写入失败状态")
    void localFallbackFails() throws Exception {
        FakeMusicProvider provider = new FakeMusicProvider(0, true);
        MusicOptions opts = new MusicOptions();

        MusicTaskSubmit submit = provider.submitTask(new MusicRequest("失败提示词", opts));
        MusicTaskStatus status = pollUntilDone(provider, submit.getTaskId(), opts);

        assertTrue(status.isFailed());
        assertNull(status.getResponse());
        assertEquals("模拟生成失败", status.getError());
    }

    @Test
    @DisplayName("未知 taskId 抛出 IllegalArgumentException")
    void unknownTaskIdThrows() {
        FakeMusicProvider provider = new FakeMusicProvider(0, false);
        assertThrows(IllegalArgumentException.class,
                () -> provider.pollTask("no-such-task", new MusicOptions()));
    }

    @Test
    @DisplayName("MusicTaskRunner 提交与轮询（logger 为 null）")
    void runnerSubmitAndPollWithoutLogger() throws Exception {
        FakeMusicProvider provider = new FakeMusicProvider(50, false);
        MusicTaskRunner runner = new MusicTaskRunner(provider, null);
        MusicOptions opts = new MusicOptions().model("music-3.0");

        MusicTaskSubmit submit = runner.submit(new MusicRequest("runner 提示词", opts));
        assertTrue(submit.getTaskId().startsWith("local-"));

        MusicTaskStatus status = pollUntilDone(provider, submit.getTaskId(), opts);
        assertTrue(status.isSucceeded());
        assertEquals("00ff10", status.getResponse().getAudioHex());
    }
}
