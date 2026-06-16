package com.gdxsoft.ai.request;

/**
 * AI 内容片段类型。
 * <p>
 * 用于多模态消息（文本、图片、音频、视频、工具调用等）。
 *
 * @since 1.1.0
 */
public enum AiContentType {
    /** 纯文本 */
    TEXT("text"),
    /** 图片（URL 或 Base64） */
    IMAGE("image"),
    /** 音频（URL 或 Base64） */
    AUDIO("audio"),
    /** 视频（URL） */
    VIDEO("video"),
    /** 文件（通用，如 PDF、文档等） */
    FILE("file"),
    /** 工具调用 — AI 请求调用某个工具 */
    TOOL_USE("tool_use"),
    /** 工具调用结果 — 工具执行后返回的内容 */
    TOOL_RESULT("tool_result");

    private final String name;

    AiContentType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
