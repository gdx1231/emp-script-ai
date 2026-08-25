package com.gdxsoft.ai.img;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.ChatManagerDb;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * ImgChatLogger 日志写入行为测试。
 *
 * <p>用伪造的 {@link ChatManagerDb} 记录调用序列，无需数据库：
 * 重点覆盖 Web 入队 -> worker restore 链路上的 logStart 幂等保护
 * （restore 后 aiId 非零，重复 logStart 不得另建会话）。
 */
class ImgChatLoggerTest {

	/** 伪造数据库：记录 createChat/addMessage 调用，返回固定 id */
	private static class FakeChatDb extends ChatManagerDb {
		final List<String> calls = new java.util.ArrayList<>();

		FakeChatDb() {
			super(new RequestValue(), "fake");
		}

		@Override
		public long createChat(RequestValue rv) {
			calls.add("createChat");
			return 1001L;
		}

		@Override
		public long addMessage(long aiId, String msg, String role, String stepName, String promptName,
				String actionName, String actionClass, short numberOfInteractions, boolean isSkipAppend,
				boolean byUser) {
			calls.add("addMessage:" + role);
			return 2001L;
		}

		@Override
		public short getNextInteractionNumber(long aiId) {
			return 1;
		}
	}

	@Test
	@DisplayName("logStart 创建会话 + 用户消息，aiId 调用后生效")
	void logStartCreatesChatAndUserMessage() {
		FakeChatDb db = new FakeChatDb();
		ImgChatLogger lg = new ImgChatLogger(db, new RequestValue());
		assertEquals(0, lg.getAiId());

		lg.logStart("DOUBAO_IMG", "doubao-seedream-5-0", "a cat", new JSONObject().put("size", "1024x1024"));

		assertEquals(1001L, lg.getAiId());
		assertEquals(List.of("createChat", "addMessage:user"), db.calls);
	}

	@Test
	@DisplayName("restore 场景：aiId 非零时 logStart 幂等跳过，不重复建会话")
	void logStartSkipsWhenRestored() {
		FakeChatDb db = new FakeChatDb();
		ImgChatLogger lg = new ImgChatLogger(db, new RequestValue());
		lg.logStart("DOUBAO_IMG", "model", "a cat", null);
		assertEquals(2, db.calls.size());

		// worker 侧 restore 后 ImgTaskRunner.submit 会再次调用 logStart -- 应跳过
		lg.logStart("DOUBAO_IMG", "model", "a cat", null);
		assertEquals(2, db.calls.size());
		assertEquals(1001L, lg.getAiId());
	}

	@Test
	@DisplayName("restore 场景：logSuccess 追加 assistant 结果消息仍生效")
	void restoredLoggerStillAppendsResults() {
		FakeChatDb db = new FakeChatDb();
		ImgChatLogger lg = new ImgChatLogger(db, new RequestValue());
		lg.logStart("DOUBAO_IMG", "model", "a cat", null);

		ImgResponse resp = new ImgResponse(
				List.of(new ImgResponse.GeneratedImage("https://example.com/a.png", null, null)),
				null, null, "m", null, new JSONObject());
		lg.logSuccess(resp);

		assertTrue(db.calls.contains("addMessage:user"));
		assertTrue(db.calls.contains("addMessage:assistant"));
	}
}
