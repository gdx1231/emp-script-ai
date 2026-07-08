package com.gdxsoft.ai.app.chatroom;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.*;

import com.gdxsoft.ai.AiStreamOrPost;
import com.gdxsoft.ai.ChatManagerBase;
import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;
import com.gdxsoft.easyweb.utils.Utils;
import com.gdxsoft.easyweb.websocket.EwaWebSocketBus;
import com.gdxsoft.easyweb.websocket.IHandleMsg;
import javax.websocket.Session;

/**
 * AI 聊天消息处理
 *
 * 线程池针对 I/O 密集型场景（LLM 流式调用 + RESTful API + 数据库查询）进行优化， 100 并发下可立即处理，避免长时间排队。
 */
public class HandleAiChatImpl implements Runnable, IHandleMsg {
	private static final Logger LOGGER = LoggerFactory.getLogger(HandleAiChatImpl.class);

	/** 核心线程数：保持常驻，避免冷启动延迟 */
	private static final int CORE_POOL_SIZE = 20;
	/** 最大线程数：I/O 密集型，足够支撑 100+ 并发 */
	private static final int MAX_POOL_SIZE = 200;
	/** 空闲线程存活时间 */
	private static final long KEEP_ALIVE_SECONDS = 120;
	/** 有界队列容量：防 OOM，超出时由 CallerRunsPolicy 产生背压 */
	private static final int QUEUE_CAPACITY = 500;

	private static final AtomicInteger THREAD_NUMBER = new AtomicInteger(1);
	private static final ThreadFactory THREAD_FACTORY = r -> {
		Thread t = new Thread(r, "ai-chat-handler-" + THREAD_NUMBER.getAndIncrement());
		t.setDaemon(true);
		return t;
	};

	private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
			KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), THREAD_FACTORY,
			new ThreadPoolExecutor.CallerRunsPolicy());

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("正在关闭 AI 聊天线程池...");
			POOL.shutdown();
			try {
				if (!POOL.awaitTermination(10, TimeUnit.SECONDS)) {
					POOL.shutdownNow();
				}
			} catch (InterruptedException e) {
				POOL.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}, "ai-chat-pool-shutdown"));
	}

	/** 限制同时调用 LLM API 的数量，防止打爆第三方 API 限流 */
	private static final Semaphore AI_LIMIT = new Semaphore(50);

	/** 获取 Semaphore 当前可用许可数，用于监控 */
	public static int getAiAvailablePermits() {
		return AI_LIMIT.availablePermits();
	}

	public final static String METHOD = "ai_chat";

	// AI 流式响应开始
	public final static String AI_STREAM_START = "ai_stream_start";
	// AI 流式响应增量
	public final static String AI_STREAM_DELTA = "ai_stream_delta";
	// AI 流式响应结束
	public final static String AI_STREAM_END = "ai_stream_end";
	// AI 回复反馈
	public final static String AI_FEEDBACK = "ai_feedback";

	private EwaWebSocketBus socket_;
	private JSONObject command_;
	private String action_;

	public HandleAiChatImpl(EwaWebSocketBus socket, JSONObject command) {
		this.socket_ = socket;
		this.command_ = command;
		// 兼容客户端发送 action / ACTION
		String act = command_.optString("action", "");
		if (act.isEmpty()) {
			act = command_.optString("ACTION", "");
		}
		this.action_ = act;
	}

	@Override
	public void setName(String name) {
		// 接口要求，但实际不依赖此方法
	}

	/**
	 * 当前排队的任务数，用于监控
	 */
	public static int getQueueSize() {
		return POOL.getQueue().size();
	}

	/**
	 * 当前活跃线程数，用于监控
	 */
	public static int getActiveCount() {
		return POOL.getActiveCount();
	}

	@Override
	public void start() {
		POOL.execute(this);
	}

	@Override
	public void run() {
		LOGGER.info("HandleAiChatImpl.run() action={}, ID={}", this.action_, this.command_.optString("ID"));
		javax.websocket.Session wsSession = this.socket_.getSession();

		if (wsSession == null || wsSession.getUserProperties().get("cht_usr_id") == null) {
			LOGGER.warn("HandleAiChatImpl: Unauthorized");
			JSONObject denied = new JSONObject();
			denied.put("RST", false);
			denied.put("ERR", "Unauthorized");
			this.socket_.sendToClient(denied.toString());
			return;
		}
		try {
			this.doAction();
		} catch (Exception err) {
			LOGGER.error("AI 聊天处理失败", err);
			JSONObject result = new JSONObject();
			result.put("RST", false);
			result.put("ERR", "Internal error: " + err.getMessage());
			this.socket_.sendToClient(result.toString());
		}
	}

	private void doAction() {
		// ask 是流式处理，内部已发消息，不需要此处发 result
		if (this.action_.equalsIgnoreCase("ask")) {
			this.doActionAsk();
			return;
		}

		// 非流式 action：构建 JSON result 并通过 sendToClient 返回
		JSONObject result = new JSONObject();
		result.put("METHOD", METHOD);
		result.put("ID", this.command_.optString("ID"));
		result.put("ACTION", this.action_);

		result.put("RST", false);
		result.put("ERR", "Unknown action: " + this.action_);
		this.socket_.sendToClient(result.toString());

		this.socket_.sendToClient(result.toString());
	}

	/**
	 * AI 提问 — 流式接入 emp-script-ai
	 */
	private void doActionAsk() {
		String roomId = this.command_.optString("CHAT_ROOM_ID", "");
		String msg = this.command_.optString("MSG", "");

		if (roomId.isEmpty() || msg.isEmpty()) {
			JSONObject err = createErrorResult("Missing CHAT_ROOM_ID or MSG");
			this.socket_.sendToClient(err.toString());
			return;
		}

		RequestValue rv = this.socket_.getRv();

		// 1. 查房间 UUID
		String roomUnid = null;
		try {
			RequestValue qr = rv.clone();
			qr.addOrUpdateValue("cht_rom_id", roomId);
			DTTable tb = DTTable.getJdbcTable("SELECT cht_rom_unid FROM chat_room WHERE cht_rom_id=@cht_rom_id", "chat",
					qr);
			if (tb.getCount() > 0 && tb.getCell(0, "cht_rom_unid") != null) {
				roomUnid = tb.getCell(0, "cht_rom_unid").toString();
			}
		} catch (Exception e) {
			LOGGER.warn("查询房间信息失败, roomId={}: {}", roomId, e.getMessage());
		}

		// 2. 通过 roomId 查房间内的 bot 用户 → 获取关联的 AI 模型 ID
		String xmlPath = null;
		String provider = null;
		String model = null;
		String modeName = null;
		boolean thinking = false;
		String botUserId = null;
		String botName = null;
		try {
			RequestValue qr = rv.clone();
			qr.addOrUpdateValue("room_id", roomId);
			DTTable botTable = DTTable.getJdbcTable("""
					SELECT u.cht_usr_id, u.cht_usr_name, u.cht_ai_model_id FROM chat_user u
						JOIN chat_acl a ON a.cht_usr_id = u.cht_usr_id
					WHERE a.cht_rom_id=@room_id
						AND u.cht_usr_ref IN ('ai_bot','chat_ai_model')
						AND u.cht_usr_status='USED'
					""", "chat", qr);
			if (botTable.getCount() == 0) {
				JSONObject err = createErrorResult("No bot found in room: " + roomId);
				this.socket_.sendToClient(err.toString());
				return;
			}
			if (botTable.getCell(0, "cht_usr_id") != null) {
				botUserId = botTable.getCell(0, "cht_usr_id").toString();
			}
			if (botTable.getCell(0, "cht_usr_name") != null) {
				botName = botTable.getCell(0, "cht_usr_name").toString();
			}
			Object aiModelIdObj = botTable.getCell(0, "cht_ai_model_id");
			if (aiModelIdObj != null) {
				String aiModelId = aiModelIdObj.toString();
				// 3. 通过 cht_ai_model_id 查 chat_ai_model 获取模型配置
				RequestValue mr = rv.clone();
				mr.addOrUpdateValue("ai_model_id", aiModelId);
				DTTable modelTable = DTTable.getJdbcTable("""
						SELECT xml_path, provider, model, mode_name, thinking
						FROM chat_ai_model 
						WHERE id=@ai_model_id 
							AND enabled=1
						""", "chat", mr);
				if (modelTable.getCount() > 0) {
					if (modelTable.getCell(0, "xml_path") != null) {
						xmlPath = modelTable.getCell(0, "xml_path").toString();
					}
					if (modelTable.getCell(0, "provider") != null) {
						provider = modelTable.getCell(0, "provider").toString();
					}
					if (modelTable.getCell(0, "model") != null) {
						model = modelTable.getCell(0, "model").toString();
					}
					if (modelTable.getCell(0, "mode_name") != null) {
						modeName = modelTable.getCell(0, "mode_name").toString();
					}
					if (modelTable.getCell(0, "thinking") != null) {
						thinking = "1".equals(modelTable.getCell(0, "thinking").toString());
					}
				} else {
					JSONObject err = createErrorResult("Bot 关联的 model id=" + aiModelId + " 未启用或不存在");
					this.socket_.sendToClient(err.toString());
					return;
				}
			} else {
				JSONObject err = createErrorResult("Bot 未关联 model");
				this.socket_.sendToClient(err.toString());
				return;
			}
		} catch (Exception e) {
			LOGGER.warn("查询 bot/model 配置失败, roomId={}: {}", roomId, e.getMessage());
		}

		LOGGER.info("AI ask: roomId={}, bot={}, botName={}, xmlPath={}, provider={}, model={}, mode={}, thinking={}",
				roomId, botUserId, botName, xmlPath, provider, model, modeName, thinking);

		// 3. 加载 Mode XML
		try {
			ChatManagerBase.loadModes(xmlPath, this.getClass().getClassLoader());
		} catch (Exception e) {
			LOGGER.error("加载 Mode XML 失败: {}", xmlPath, e);
			JSONObject err = createErrorResult("AI 配置加载失败: " + e.getMessage());
			this.socket_.sendToClient(err.toString());
			return;
		}

		// 4. 构建 RequestValue
		String requestId = (roomUnid != null && !roomUnid.isEmpty()) ? roomUnid : Utils.getGuid();
		RequestValue aiRv = rv.clone();
		aiRv.addOrUpdateValue("request_id", requestId);
		aiRv.addOrUpdateValue("ai_provider", provider);
		aiRv.addOrUpdateValue("ai_model", model);
		aiRv.addOrUpdateValue("mode", modeName);
		aiRv.addOrUpdateValue("prompt", msg);
		aiRv.addOrUpdateValue("ai_thinking", thinking ? "true" : "false");
		aiRv.addOrUpdateValue("CHAT_ROOM_ID", roomId);

		// 5. 创建 WebSocket Writer + PrintWriter
		boolean isPrivate = "true".equalsIgnoreCase(this.command_.optString("isPrivate"));
		WebSocketSseWriter wsWriter = new WebSocketSseWriter(this.socket_, requestId, Long.parseLong(roomId),
				isPrivate);
		PrintWriter pw = new PrintWriter(wsWriter);

		// 用户提问已由 HandleChatImpl 落库，此处不重复保存
		// 6. 初始化并执行 AiStreamOrPost
		AiStreamOrPost handle = new AiStreamOrPost();
		if (!handle.init(aiRv, "chat", pw)) {
			// init 失败时错误已通过 writer 发送
			pw.close();
			return;
		}
		boolean limitAcquired = false;
		try {
			AI_LIMIT.acquire();
			limitAcquired = true;
			handle.processRequest();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.warn("AI 调用被中断: {}", e.getMessage());
		} catch (Exception e) {
			LOGGER.error("AI processRequest 失败: {}", e.getMessage(), e);
		} finally {
			if (limitAcquired) {
				AI_LIMIT.release();
			}
			pw.close();
		}

		// 7. AI 回答落库到 chat_topic + chat_cnt
		String aiReply = wsWriter.getFullText();
		if (aiReply != null && !aiReply.isEmpty()) {
			saveAiReplyToTopic(roomId, botUserId, aiReply, rv, isPrivate);
		}
	}

	/**
	 * 将 AI 回答保存到 chat_topic + chat_cnt（通过 ClientSdk 调用 RESTful API）
	 */
	private void saveAiReplyToTopic(String roomId, String botUserId, String content, RequestValue rv,
			boolean isPrivate) {
		try {
			// 从 WebSocket 握手时提取的 sup_id
			Object supIdObj = getSession().getUserProperties().get("sup_id");
			int supId = supIdObj instanceof Integer ? (Integer) supIdObj : 0;
			if (supId <= 0) {
				LOGGER.warn("sup_id 不可用，无法创建 bot JWT token");
				return;
			}

			// 为 bot 用户创建 JWT token
			Auth auth = new Auth();
			long botIdLong = Long.parseLong(botUserId);
			if (!auth.createJwtTokenUser(supId, botIdLong)) {
				LOGGER.warn("创建 bot JWT token 失败: {}", auth.getErrorMessage());
				return;
			}
			String jwtToken = auth.getJwtToken();

			// RESTful API 根路径（从 ewa_conf.xml 的 initparas 中读取）
			String apiRoot = com.gdxsoft.easyweb.utils.UPath.getInitPara("chat_restful_root");
			if (apiRoot == null || apiRoot.isEmpty()) {
				LOGGER.warn("ewa_conf.xml 中未配置 chat_restful_root");
				return;
			}

			// 创建 ClientSdk
			ClientSdk sdk = new ClientSdk(apiRoot, jwtToken);
			sdk.setChatUserId(botIdLong);

			// 构建消息
			JSONObject body = new JSONObject();
			body.put("cht_cnt", content);
			body.put("cht_cnt_txt", content);
			body.put("cht_type", "text");
			// 私密模式下记录 AI 回复目标用户
			if (isPrivate) {
				Object chtUsrIdObj = getSession().getUserProperties().get("cht_usr_id");
				if (chtUsrIdObj != null) {
					body.put("cht_to_usr_id", chtUsrIdObj);
				}
			}
			JSONObject msg = new JSONObject();
			msg.put("body", body);

			// 调用 newMessage
			long roomIdLong = Long.parseLong(roomId);
			com.gdxsoft.easyweb.script.restful.RestfulResult<Object> result = sdk.newMessage(roomIdLong, msg);

			if (result.isSuccess()) {
				LOGGER.info("AI 回答已落库: roomId={}, botUserId={}, length={}", roomId, botUserId, content.length());
			} else {
				LOGGER.warn("AI 回答落库失败: code={}, message={}", result.getCode(), result.getMessage());
			}
		} catch (Exception e) {
			LOGGER.error("AI 回答落库失败: {}", e.getMessage(), e);
		}
	}

	private Session getSession() {
		return this.socket_.getSession();
	}

	/**
	 * 创建标准错误响应（包含 METHOD、ID、ACTION 字段）
	 */
	private JSONObject createErrorResult(String errorMsg) {
		JSONObject err = new JSONObject();
		err.put("METHOD", METHOD);
		err.put("ID", this.command_.optString("ID"));
		err.put("ACTION", this.action_);
		err.put("RST", false);
		err.put("ERR", errorMsg);
		return err;
	}

	@Override
	public String getMethod() {
		return METHOD;
	}
}
