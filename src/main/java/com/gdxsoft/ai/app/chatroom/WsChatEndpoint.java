package com.gdxsoft.ai.app.chatroom;

import com.gdxsoft.easyweb.websocket.EwaWebSocketBus;

/**
 * WebSocket 聊天 endpoint。
 * 路径由 {@link WsAppConfig} 从 ewa_conf.xml 动态读取，不写死在注解里。
 */
public class WsChatEndpoint extends EwaWebSocketBus {
    // onOpen / onMessage / onClose / onError 均由父类 EwaWebSocketBus 处理
}
