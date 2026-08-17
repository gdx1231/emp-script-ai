package com.gdxsoft.ai.img;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test the unified async image mode (submit/poll + ImgTaskRunner).
 *
 * <p>Uses a fake provider on top of {@link ImgProviderBase}'s local async
 * fallback — no external services required.
 *
 * @since 1.4.0
 */
class ImgTaskRunnerTest {

    /** 假 provider：可控制 generate 的延迟与成功/失败 */
    private static class FakeImgProvider extends ImgProviderBase {
        private final long delayMs;
        private final boolean fail;

        FakeImgProvider(long delayMs, boolean fail) {
            this.delayMs = delayMs;
            this.fail = fail;
        }

        @Override
        public ImgProviderType getProviderType() {
            return ImgProviderType.OPENAI;
        }

        @Override
        public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            if (fail) {
                throw new IOException("模拟生成失败");
            }
            ImgResponse.GeneratedImage img =
                    new ImgResponse.GeneratedImage("https://example.com/a.png", null, null);
            return new ImgResponse(List.of(img), null, null, "fake-model", null,
                    new JSONObject().put("fake", true));
        }

        @Override
        public String curl(ImgRequest request) {
            return "curl fake";
        }
    }

    /** 轮询直到终态（最多 5s） */
    private static ImgTaskStatus pollUntilDone(IImgProvider p, String taskId, ImgOptions opts)
            throws Exception {
        for (int i = 0; i < 50; i++) {
            ImgTaskStatus st = p.pollTask(taskId, opts);
            if (!st.isProcessing()) {
                return st;
            }
            Thread.sleep(100);
        }
        fail("任务未在 5s 内到达终态");
        return null;
    }

    @Test
    @DisplayName("本地回退：submit 立即返回 local- taskId，poll 最终 succeeded")
    void localFallbackSuccess() throws Exception {
        FakeImgProvider provider = new FakeImgProvider(200, false);
        ImgOptions opts = new ImgOptions("a cat");

        ImgTaskSubmit submit = provider.submitTask(new ImgRequest(opts));
        assertNotNull(submit.getTaskId());
        assertTrue(submit.getTaskId().startsWith("local-"));
        assertNull(submit.getRaw());

        ImgTaskStatus st = pollUntilDone(provider, submit.getTaskId(), opts);
        assertTrue(st.isSucceeded());
        assertNotNull(st.getResponse());
        assertEquals("https://example.com/a.png", st.getResponse().getFirstImage().getUrl());
        assertNotNull(st.getRaw());
    }

    @Test
    @DisplayName("本地回退：generate 抛异常时 poll 得 failed")
    void localFallbackFailure() throws Exception {
        FakeImgProvider provider = new FakeImgProvider(0, true);
        ImgOptions opts = new ImgOptions("a dog");

        ImgTaskSubmit submit = provider.submitTask(new ImgRequest(opts));
        ImgTaskStatus st = pollUntilDone(provider, submit.getTaskId(), opts);
        assertTrue(st.isFailed());
        assertNotNull(st.getError());
        assertTrue(st.getError().contains("模拟生成失败"));
    }

    @Test
    @DisplayName("本地回退：未知 taskId 抛 IllegalArgumentException")
    void localFallbackUnknownTask() {
        FakeImgProvider provider = new FakeImgProvider(0, false);
        assertThrows(IllegalArgumentException.class,
                () -> provider.pollTask("local-not-exists", new ImgOptions("x")));
    }

    @Test
    @DisplayName("ImgTaskRunner：无日志器时 submit/poll 正常走完")
    void runnerWithoutLogger() throws Exception {
        FakeImgProvider provider = new FakeImgProvider(100, false);
        ImgTaskRunner runner = new ImgTaskRunner(provider, null);
        ImgOptions opts = new ImgOptions("a bird");

        ImgTaskSubmit submit = runner.submit(new ImgRequest(opts));
        assertNotNull(submit.getTaskId());

        ImgTaskStatus st = null;
        for (int i = 0; i < 50; i++) {
            st = runner.poll(submit.getTaskId(), opts);
            if (!st.isProcessing()) break;
            Thread.sleep(100);
        }
        assertNotNull(st);
        assertTrue(st.isSucceeded());
        assertEquals(provider, runner.getProvider());
        assertNull(runner.getLogger());
    }
}
