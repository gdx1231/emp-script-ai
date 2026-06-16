package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置 Web Search 工具。
 * <p>
 * 提供两种使用方式：
 * <ol>
 *   <li>作为 AiTool 定义，让 AI 模型决定是否调用搜索</li>
 *   <li>直接调用 {@link #search(String)} 执行搜索</li>
 * </ol>
 * <p>
 * 搜索结果以 JSON 格式返回，包含标题、摘要、URL 等字段，
 * 方便 AI 模型理解和引用。
 *
 * <h3>作为 Tool 使用</h3>
 * <pre>
 * AiTool searchTool = AiWebSearch.asTool();
 * reqData.tools(searchTool);
 * </pre>
 *
 * <h3>直接调用</h3>
 * <pre>
 * String results = AiWebSearch.search("Java 17 新特性");
 * </pre>
 *
 * @since 1.1.0
 */
public class AiWebSearch {

    /**
     * Web Search 工具名称
     */
    public static final String TOOL_NAME = "web_search";

    /**
     * Web Search 工具描述
     */
    public static final String TOOL_DESCRIPTION =
        "搜索互联网获取实时信息。当用户询问需要最新信息、新闻、天气、股价等时使用。";

    /**
     * 创建 Web Search 工具定义。
     * <p>
     * 返回的 AiTool 可以添加到 IRequestData 的 tools 列表中，
     * AI 模型会根据需要调用此工具。
     */
    public static AiTool asTool() {
        return AiTool.builder()
            .name(TOOL_NAME)
            .description(TOOL_DESCRIPTION)
            .param("query", "string", "搜索关键词或问题", true)
            .param("num_results", "integer", "返回结果数量，默认 5", false)
            .param("lang", "string", "语言偏好，如 zh-CN、en-US，默认 zh-CN", false)
            .build();
    }

    /**
     * 执行 Web 搜索。
     * <p>
     * 默认使用 DuckDuckGo HTML 搜索（无需 API Key）。
     * 如需使用其他搜索引擎，可配置 {@link #searchEngine}。
     *
     * @param query 搜索关键词
     * @return JSON 格式的搜索结果
     */
    public static String search(String query) {
        return search(query, 5, "zh-CN");
    }

    /**
     * 执行 Web 搜索。
     *
     * @param query      搜索关键词
     * @param numResults 返回结果数量
     * @param lang       语言偏好
     * @return JSON 格式的搜索结果
     */
    public static String search(String query, int numResults, String lang) {
        List<SearchResult> results = searchDuckDuckGo(query, numResults, lang);
        return resultsToJson(results);
    }

    /**
     * 使用 DuckDuckGo 执行搜索（免费，无需 API Key）。
     */
    private static List<SearchResult> searchDuckDuckGo(String query, int numResults, String lang) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery + "&kl=" + lang;

            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; AiBot/1.0)")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                results = parseDuckDuckGoHtml(response.body(), numResults);
            }
        } catch (Exception e) {
            // 搜索失败，返回空结果
            System.err.println("Web search failed: " + e.getMessage());
        }
        return results;
    }

    /**
     * 解析 DuckDuckGo HTML 搜索结果。
     */
    private static List<SearchResult> parseDuckDuckGoHtml(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        // 简单 HTML 解析（不依赖第三方库）
        // 提取 <a class="result__a"> 链接和 <a class="result__snippet"> 摘要
        String[] links = extractAttribute(html, "a", "class", "result__a", "href");
        String[] snippets = extractText(html, "a", "class", "result__snippet");

        int count = Math.min(maxResults, Math.min(links.length, snippets.length + 1));
        for (int i = 0; i < count; i++) {
            String title = extractTextBetween(links[i], ">", "<");
            String url = links[i];
            String snippet = i < snippets.length ? snippets[i] : "";

            results.add(new SearchResult(title, url, snippet));
        }

        return results;
    }

    /**
     * 提取 HTML 中指定标签的属性值数组。
     */
    private static String[] extractAttribute(String html, String tag, String attrName, String attrValue, String extractAttr) {
        List<String> values = new ArrayList<>();
        String searchTag = "<" + tag + "[^>]*" + attrName + "=[\"']" + Pattern.quote(attrValue);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "<" + tag + "[^>]*" + attrName + "=[\"']" + java.util.regex.Pattern.quote(attrValue) + "[\"'][^>]*>",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String tagContent = matcher.group();
            // 提取 extractAttr 的值
            java.util.regex.Pattern attrPattern = java.util.regex.Pattern.compile(
                extractAttr + "=[\"']([^\"']*)[\"']",
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher attrMatcher = attrPattern.matcher(tagContent);
            if (attrMatcher.find()) {
                values.add(attrMatcher.group(1));
            }
        }
        return values.toArray(new String[0]);
    }

    /**
     * 提取 HTML 中指定标签的文本内容数组。
     */
    private static String[] extractText(String html, String tag, String attrName, String attrValue) {
        List<String> texts = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "<" + tag + "[^>]*" + attrName + "=[\"']" + java.util.regex.Pattern.quote(attrValue) + "[\"'][^>]*>(.*?)</" + tag + ">",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String content = matcher.group(1);
            // 去除 HTML 标签
            String text = content.replaceAll("<[^>]+>", "").trim();
            if (!text.isEmpty()) {
                texts.add(text);
            }
        }
        return texts.toArray(new String[0]);
    }

    private static String extractTextBetween(String html, String start, String end) {
        int s = html.indexOf(start);
        if (s < 0) return "";
        int e = html.indexOf(end, s + start.length());
        if (e < 0) return "";
        return html.substring(s + start.length(), e).trim();
    }

    /**
     * 将搜索结果转换为 JSON 字符串。
     */
    private static String resultsToJson(List<SearchResult> results) {
        JSONArray arr = new JSONArray();
        for (SearchResult r : results) {
            JSONObject obj = new JSONObject();
            obj.put("title", r.title);
            obj.put("url", r.url);
            obj.put("snippet", r.snippet);
            arr.put(obj);
        }

        JSONObject result = new JSONObject();
        result.put("query", "");
        result.put("results", arr);
        result.put("count", results.size());
        return result.toString(2);
    }

    /**
     * 单个搜索结果。
     */
    public static class SearchResult {
        public final String title;
        public final String url;
        public final String snippet;

        public SearchResult(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }
}
