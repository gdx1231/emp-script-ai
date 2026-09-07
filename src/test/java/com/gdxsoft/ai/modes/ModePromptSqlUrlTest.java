package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gdxsoft.easyweb.data.DTTable;

/**
 * 验证 SQL 查询结果转换为 prompt 媒体 URL 列表。
 */
class ModePromptSqlUrlTest {

    @Test
    void formatSingleColumnRowsAsUrlLines() throws Exception {
        DTTable table = new DTTable();
        table.initData(new org.json.JSONArray("[{\"IMAGE_URL\":\"https://example.com/a.png\"},"
                + "{\"IMAGE_URL\":\"https://example.com/b.jpg\"}]"));
        assertEquals("https://example.com/a.png\nhttps://example.com/b.jpg", Mode.toUrlList(table));
    }

    @Test
    void preferUrlColumn() throws Exception {
        DTTable table = new DTTable();
        table.initData(new org.json.JSONArray(
                "[{\"NAME\":\"封面\",\"IMG\":\"https://example.com/a.png\"}]"));
        assertEquals("https://example.com/a.png", Mode.toUrlList(table));
    }
}
