/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: ImgChatLogger.java
 * 描述: 图片生成日志记录器，将图片生成任务持久化到 AI_CHAT / AI_CHAT_MSG 表
 */
package com.gdxsoft.ai.img;

import java.util.UUID;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.ChatManagerDb;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 图片生成日志记录器。
 * <p>
 * 将图片生成任务的提示词、参数、结果持久化到 AI_CHAT / AI_CHAT_MSG 表，
 * 便于在聊天历史中回溯图片创作记录。
 * <p>
 * 用法：
 * <pre>{@code
 * ImgChatLogger logger = ImgChatLogger.create(rv, "dbConfigName");
 * logger.logStart("openai_img", "dall-e-3", prompt, optsJson);
 * try {
 *     ImgResponse resp = provider.generate(request);
 *     logger.logSuccess(resp);
 * } catch (Exception e) {
 *     logger.logError(e);
 *     throw e;
 * }
 * }</pre>
 * <p>
 * 数据库未配置时静默跳过（不影响图片生成主流程）。
 *
 * @since 1.3.0
 */
public class ImgChatLogger {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImgChatLogger.class);

	private final ChatManagerDb db;
	private final RequestValue rv;
	private long aiId;
	private String requestId;
	private short noi = 1;
	private String ref;
	private String refId;

	private long userMsgId;

	private ImgChatLogger(ChatManagerDb db, RequestValue rv) {
		this.db = db;
		this.rv = rv;
	}

	/**
	 * 创建日志记录器（无用户上下文）。
	 * <p>
	 * 使用独立的空 RequestValue，聊天记录的 AI_UID 等用户字段为空。
	 * 适用于 Action 中自动记录图片生成任务的场景。
	 *
	 * @param dbConfigName 数据库配置名称
	 * @return 日志记录器实例 
	 */
	public static ImgChatLogger create(String dbConfigName) {
		try {
			RequestValue newRv = new RequestValue();
			ChatManagerDb db = new ChatManagerDb(newRv, dbConfigName);
			return new ImgChatLogger(db, newRv);
		} catch (Exception e) {
			LOGGER.warn("ImgChatLogger 初始化失败，日志记录已跳过: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 创建日志记录器。
	 *
	 * @param rv           请求上下文（含 g_ADM_ID、G_WEB_USR_ID、g_SUP_ID 等用户信息）
	 * @param dbConfigName 数据库配置名称（对应 ewa_conf.xml 中的配置节）
	 * @return 日志记录器实例； 
	 */
	public static ImgChatLogger create(RequestValue rv, String dbConfigName) {
		try {
			ChatManagerDb db = new ChatManagerDb(rv, dbConfigName);
			return new ImgChatLogger(db, rv);
		} catch (Exception e) {
			LOGGER.warn("ImgChatLogger 初始化失败，日志记录已跳过: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 从已有的 aiId 恢复日志记录器（用于异步线程中记录结果）。
	 * <p>
	 * 使用独立的 RequestValue，避免跨线程共享原始 rv。
	 *
	 * @param aiId         已创建的 AI_CHAT ID
	 * @param dbConfigName 数据库配置名称
	 * @return 日志记录器实例；若 dbConfigName 为空则返回 null
	 */
	public static ImgChatLogger restore(long aiId, String dbConfigName) {
		try {
			RequestValue newRv = new RequestValue();
			ChatManagerDb db = new ChatManagerDb(newRv, dbConfigName);
			ImgChatLogger logger = new ImgChatLogger(db, newRv);
			logger.aiId = aiId;
			return logger;
		} catch (Exception e) {
			LOGGER.warn("ImgChatLogger 恢复失败: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 记录任务开始：创建 AI_CHAT 会话 + 用户消息（提示词 + 参数）。
	 *
	 * @param provider provider 名称（如 openai_img、doubao_img）
	 * @param model    模型 ID（如 dall-e-3）
	 * @param prompt   文本提示词
	 * @param options  参数 JSON（可为 null）
	 */
	public void logStart(String provider, String model, String prompt, JSONObject options) {
		try {
			String requestId = UUID.randomUUID().toString();
			this.requestId = requestId;
			rv.addOrUpdateValue("request_id", requestId );
			rv.addOrUpdateValue("p_ai_pid", 0);
			rv.addOrUpdateValue("AI_PROVIDER", provider);
			rv.addOrUpdateValue("AI_MODEL", model);
			rv.addOrUpdateValue("AI_THINKING", 0);
			rv.addOrUpdateValue("AI_STREAM", 0);
			rv.addOrUpdateValue("AIM_STEP", "img_generate");
			rv.addOrUpdateValue("MODE", "img");
			rv.addOrUpdateValue("AI_MAX_TOKEN", 0);
			rv.addOrUpdateValue("AI_REF", this.ref);
			rv.addOrUpdateValue("AI_REF_ID", this.refId);

			aiId = db.createChat(rv);

			// 用户消息：提示词 + 参数
			StringBuilder msg = new StringBuilder();
			msg.append(prompt);
			if (options != null && options.length() > 0) {
				msg.append("\n\n--- 参数 ---\n").append(options.toString(2));
			}

			userMsgId = db.addMessage(aiId, msg.toString(), "user", "img_generate",
					null, null, null, noi, false, true);

		} catch (Exception e) {
			LOGGER.warn("记录图片任务开始日志失败: {}", e.getMessage());
		}
	}

	/**
	 * 记录 curl 请求命令。
	 *
	 * @param curl curl 命令文本
	 */
	public void logCurl(String curl) {
		try {
			if (aiId == 0 || curl == null || curl.isEmpty()) return;
			db.addMessage(aiId, curl, "curl", "img_generate",
					null, null, null, noi, true, false);
		} catch (Exception e) {
			LOGGER.warn("记录 curl 日志失败: {}", e.getMessage());
		}
	}

	/**
	 * 记录 API 原始返回结果（异步任务的创建/查询返回）。
	 *
	 * @param label 标签（如 "创建任务返回"、"查询结果返回"）
	 * @param raw   原始 JSON 响应，null 时跳过
	 */
	public void logRawResponse(String label, JSONObject raw) {
		try {
			if (aiId == 0 || raw == null) return;
			String msg = (label != null ? "【" + label + "】\n" : "") + raw.toString(2);
			db.addMessage(aiId, msg, "assistant", "img_generate",
					null, null, null, noi, true, false);
		} catch (Exception e) {
			LOGGER.warn("记录原始返回日志失败: {}", e.getMessage());
		}
	}

	/**
	 * 记录任务成功：添加 assistant 消息（图片 URL + 元数据）。
	 *
	 * @param response 图片生成响应
	 */
	public void logSuccess(ImgResponse response) {
		try {
			if (aiId == 0 || response == null) return;
			noi = db.getNextInteractionNumber(aiId);

			StringBuilder msg = new StringBuilder();
			msg.append("图片生成成功\n\n");

			// 记录每张图片的信息
			for (int i = 0; i < response.getImages().size(); i++) {
				ImgResponse.GeneratedImage img = response.getImages().get(i);
				if (response.getImages().size() > 1) {
					msg.append("【图片 ").append(i + 1).append("】\n");
				}
				if (img.isUrl()) {
					msg.append("图片 URL: ").append(img.getUrl()).append("\n");
				} else if (img.isBase64()) {
					msg.append("图片格式: base64 (").append(img.getB64Json().length()).append(" chars)\n");
				}
				if (img.getRevisedPrompt() != null) {
					msg.append("优化提示词: ").append(img.getRevisedPrompt()).append("\n");
				}
			}

			// 元数据
			if (response.getRevisedPrompt() != null) {
				msg.append("\n总体优化提示词: ").append(response.getRevisedPrompt()).append("\n");
			}
			if (response.getModel() != null) {
				msg.append("模型: ").append(response.getModel()).append("\n");
			}
			if (response.getCreated() != null) {
				msg.append("创建时间: ").append(response.getCreated()).append("\n");
			}
			if (response.getUsage() != null) {
				msg.append("Token 用量: ").append(response.getUsage().toString()).append("\n");
			}
			// 记录原始返回 JSON
			if (response.getRaw() != null) {
				msg.append("\n--- 原始返回 ---\n").append(response.getRaw().toString(2)).append("\n");
			}

			long aimId = db.addMessage(aiId, msg.toString(), "assistant", "img_generate",
					null, null, null, noi, false, false);

			// 更新 token 用量
			if (response.getUsage() != null) {
				db.updateMessageTokens(aimId, response.getUsage());
			}

		} catch (Exception e) {
			LOGGER.warn("记录图片任务成功日志失败: {}", e.getMessage());
		}
	}

	/**
	 * 记录任务失败：添加 assistant 错误消息。
	 *
	 * @param error 异常
	 */
	public void logError(Throwable error) {
		try {
			if (aiId == 0) return;
			noi = db.getNextInteractionNumber(aiId);

			String msg = "图片生成失败: " + (error.getMessage() != null ? error.getMessage() : error.toString());
			db.addMessage(aiId, msg, "assistant", "img_generate",
					null, null, null, noi, false, false);

		} catch (Exception e) {
			LOGGER.warn("记录图片任务失败日志失败: {}", e.getMessage());
		}
	}

	/** @return 创建的 AI_CHAT ID */
	public long getAiId() { return aiId; }

	/** @return 请求 ID（UUID），logStart 后有效 */
	public String getRequestId() { return requestId; }

	/**
	 * @return the ref
	 */
	public String getRef() {
		return ref;
	}

	/**
	 * @param ref the ref to set
	 */
	public void setRef(String ref) {
		this.ref = ref;
	}

	/**
	 * @return the refId
	 */
	public String getRefId() {
		return refId;
	}

	/**
	 * @param refId the refId to set
	 */
	public void setRefId(String refId) {
		this.refId = refId;
	}

	/**
	 * @return the userMsgId
	 */
	public long getUserMsgId() {
		return userMsgId;
	}
}
