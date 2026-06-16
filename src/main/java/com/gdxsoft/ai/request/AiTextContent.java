package com.gdxsoft.ai.request;

/**
 * 纯文本内容片段。
 *
 * @since 1.1.0
 */
public class AiTextContent extends AiContent {
    private final String text;

    public AiTextContent(String text) {
        super(AiContentType.TEXT);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }
}
