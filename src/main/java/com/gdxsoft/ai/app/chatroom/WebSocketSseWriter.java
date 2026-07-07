package com.gdxsoft.ai.app.chatroom;

import java.io.Writer;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.websocket.EwaWebSocketBus;
import com.gdxsoft.easyweb.websocket.EwaWebSocketContainer;


/**
 * 拦截 AiStreamOrPost 的 SSE PrintWriter 输出，
 * 将 SSE data: {...} 行转换为 WebSocket ai_stream_* 广播
 */
public class WebSocketSseWriter extends Writer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketSseWriter.class);

    private final EwaWebSocketBus socket;
    private final StringBuilder lineBuf = new StringBuilder();
    private final StringBuilder fullText = new StringBuilder();
    private boolean streamStarted;
    private final String requestId;
    private final long chatRoomId;
    private final boolean isPrivate;

    public WebSocketSseWriter(EwaWebSocketBus socket, String requestId, long chatRoomId, boolean isPrivate) {
        this.socket = socket;
        this.requestId = requestId;
        this.chatRoomId = chatRoomId;
        this.isPrivate = isPrivate;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        for (int i = off; i < off + len; i++) {
            char c = cbuf[i];
            if (c == '\n') {
                processLine(lineBuf.toString());
                lineBuf.setLength(0);
            } else if (c != '\r') {
                lineBuf.append(c);
            }
        }
    }

    private void processLine(String line) {
        if (line.isEmpty()) {
            return;
        }

        if (line.startsWith("data: ")) {
            String payload = line.substring(6).trim();

            if ("[DONE]".equals(payload)) {
                broadcast(AI_STREAM_END, null);
            } else {
                // 尝试解析为 JSON，提取 content 字段
                String text = payload;
                try {
                    JSONObject obj = new JSONObject(payload);
                    // emp-script-ai 流结束标记：RST=true 且无 content 字段
                    if (obj.optBoolean("RST", false) && !obj.has("content") && !obj.has("reasoning_content")) {
                        broadcast(AI_STREAM_END, null);
                        return;
                    }
                    if (obj.has("content")) {
                        text = obj.optString("content", "");
                    }
                    // 检查是否是错误消息
                    if (obj.has("RST") && !obj.optBoolean("RST", true)) {
                        broadcast(AI_STREAM_END, obj.optString("ERR", obj.optString("MSG", "")));
                        return;
                    }
                } catch (Exception e) {
                    // 非 JSON，直接使用原始文本
                }

                if (!streamStarted) {
                    broadcast(AI_STREAM_START, null);
                    streamStarted = true;
                }
                broadcast(AI_STREAM_DELTA, text);
                // 收集完整回答内容
                if (text != null && !text.isEmpty()) {
                    fullText.append(text);
                }
            }
        } else if (line.startsWith("event: ")) {
            String eventType = line.substring(7).trim();
            if ("error".equals(eventType)) {
                broadcast(AI_STREAM_END, "AI stream error");
            }
        } else if (line.startsWith("{")) {
            // 非流式 JSON 响应（如 validate 失败）
            try {
                JSONObject obj = new JSONObject(line);
                broadcast(AI_STREAM_END, obj.optString("ERR", obj.optString("MSG", "")));
            } catch (Exception e) {
                broadcast(AI_STREAM_END, line);
            }
        }
    }

    private void broadcast(String broadcastId, String text) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("BROADCAST_ID", broadcastId);
            msg.put("CHAT_ROOM_ID", String.valueOf(this.chatRoomId));
            if (requestId != null) {
                msg.put("REQUEST_ID", requestId);
            }
            if (text != null) {
                msg.put("TEXT", text);
            }
            if (this.isPrivate) {
                // 私密：只发给请求者
                this.socket.sendToClient(msg.toString());
            } else {
                // 公开：广播给聊天室所有在线用户
                Map<String, Boolean> group = ClientChatUserGroup.getGroup(this.chatRoomId);
                if (group != null && !group.isEmpty()) {
                    String payload = msg.toString();
                    for (Iterator<String> it = group.keySet().iterator(); it.hasNext();) {
                        String unid = it.next();
                        EwaWebSocketBus ws = EwaWebSocketContainer.getSocketByUnid(unid);
                        if (ws == null) {
                            it.remove();
                            continue;
                        }
                        ws.sendToClient(payload);
                    }
                } else {
                    this.socket.sendToClient(msg.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("AI broadcast failed: {}", e.getMessage());
        }
    }

    // ── 常量 ──
    static final String AI_STREAM_START = "ai_stream_start";
    static final String AI_STREAM_DELTA = "ai_stream_delta";
    static final String AI_STREAM_END = "ai_stream_end";

    /**
     * 获取完整的 AI 回答内容
     */
    public String getFullText() {
        return fullText.toString();
    }

    @Override
    public void flush() {
        // PrintWriter.flush() 触发，已在 write() 中实时处理
    }

    @Override
    public void close() {
        // 确保结束时发送 end 事件
        if (streamStarted) {
            broadcast(AI_STREAM_END, null);
            streamStarted = false;
        }
    }
}
