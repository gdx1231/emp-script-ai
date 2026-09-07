package com.gdxsoft.ai.providers.qwen;

import java.io.IOException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.utils.UNet;

/**
 * Qwen（阿里云百炼 OpenAI 兼容模式）Files API 客户端。
 * <p>
 * 文档理解模型（如 qwen-long）不能直接通过普通 {@code file_url} content part
 * 传入文档 URL；需要先用 {@code purpose=file-extract} 上传文件，获得
 * {@code file_id} 后，在聊天消息中以 {@code fileid://file_id} 引用。
 *
 * @since 1.1.0
 */
public class QwenFileClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(QwenFileClient.class);
    private static final String DEFAULT_PURPOSE = "file-extract";

    private final String filesUrl;
    private final String apiKey;
    private final Duration timeout;

    /**
     * 创建客户端。
     *
     * @param chatApiUrl Chat Completions 地址或 compatible-mode/v1 基础地址
     * @param apiKey     DashScope / 百炼 API Key
     */
    public QwenFileClient(String chatApiUrl, String apiKey) {
        this(chatApiUrl, apiKey, Duration.ofSeconds(60));
    }

    /**
     * 创建客户端。
     *
     * @param chatApiUrl Chat Completions 地址或 compatible-mode/v1 基础地址
     * @param apiKey     DashScope / 百炼 API Key
     * @param timeout    上传超时时间
     */
    public QwenFileClient(String chatApiUrl, String apiKey, Duration timeout) {
        if (chatApiUrl == null || chatApiUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("chatApiUrl is required");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        this.filesUrl = resolveFilesUrl(chatApiUrl.trim());
        this.apiKey = apiKey.trim();
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    /**
     * 上传本地文件。
     */
    public QwenFileUpload upload(Path file) throws IOException, InterruptedException {
        if (file == null) {
            throw new IllegalArgumentException("file is required");
        }
        String filename = file.getFileName() != null ? file.getFileName().toString() : "file";
        UNet net = newDownloadClient();
        HashMap<String, String> params = new HashMap<>();
        params.put("purpose", DEFAULT_PURPOSE);
        LOGGER.info("使用 UNet 上传 Qwen Files API 文件: filename={}", filename);
        String result = net.doUpload(filesUrl, "file", file.toAbsolutePath().toString(), params);
        return checkUploadResponse(net, result);
    }

    /**
     * 上传内存数据。
     */
    public QwenFileUpload upload(byte[] data, String filename, String contentType)
            throws IOException, InterruptedException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("file data is empty");
        }
        if (filename == null || filename.trim().isEmpty()) {
            filename = "file";
        }
        String type = contentType == null || contentType.trim().isEmpty()
                ? guessContentType(filename) : contentType;

        UNet net = newUploadClient();
        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                .setCharset(StandardCharsets.UTF_8)
                .addBinaryBody("file", data, ContentType.create(type), filename)
                .addTextBody("purpose", DEFAULT_PURPOSE,
                        ContentType.MULTIPART_FORM_DATA.withCharset(StandardCharsets.UTF_8));
        LOGGER.info("使用 UNet 上传 Qwen Files API 文件: filename={}, size={}", filename, data.length);
        String result = net.doUpload(filesUrl, builder);
        return checkUploadResponse(net, result);
    }

    /**
     * 下载公网 URL 后上传。Qwen Files API 本身不接受外部 URL，因此先取回文件内容。
     */
    public QwenFileUpload uploadUrl(String url) throws IOException, InterruptedException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url is required");
        }
        UNet net = newUploadClient();
        byte[] data = net.downloadData(url.trim());
        int status = net.getLastStatusCode();
        if (status / 100 != 2) {
            throw new IOException("下载待上传文件失败 HTTP " + status + ": " + url);
        }
        return upload(data, fileNameFromUrl(url), null);
    }

    /**
     * 上传并只返回 fileid 引用。
     */
    public String uploadForFileId(Path file) throws IOException, InterruptedException {
        return toFileId(upload(file).id());
    }

    /**
     * 获取 Files API 地址。
     */
    public String getFilesUrl() {
        return filesUrl;
    }

    /**
     * 判断字符串是否已是 fileid 引用。
     */
    public static boolean isFileId(String value) {
        return value != null && value.trim().toLowerCase().startsWith("fileid://");
    }

    /**
     * 将 file_id 包装为 Qwen 聊天协议使用的 fileid 引用。
     */
    public static String toFileId(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("fileId is required");
        }
        return "fileid://" + fileId.trim();
    }

    /**
     * 根据 Chat Completions 地址推导 Files API 地址。
     */
    static String resolveFilesUrl(String apiUrl) {
        String url = apiUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/files")) {
            return url;
        }
        if (url.endsWith("/chat/completions")) {
            return url.substring(0, url.length() - "/chat/completions".length()) + "/files";
        }
        return url + "/files";
    }

    /**
     * 解析 Files API 响应。
     */
    static QwenFileUpload parse(String json) {
        JSONObject root = new JSONObject(json);
        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            throw new IllegalArgumentException("Qwen Files API error: "
                    + error.optString("message", error.optString("code", "upload failed")));
        }
        String id = root.optString("id", null);
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Qwen Files API response has no id");
        }
        return new QwenFileUpload(id, root.optString("filename", null), root.optString("purpose", null),
                root.optLong("bytes", -1), root);
    }

    private UNet newUploadClient() {
        UNet net = new UNet();
        net.setTimeout((int) timeout.toMillis());
        net.addHeader("Authorization", "Bearer " + apiKey);
        return net;
    }

    private UNet newDownloadClient() {
        UNet net = new UNet();
        net.setTimeout((int) timeout.toMillis());
        return net;
    }

    private static QwenFileUpload checkUploadResponse(UNet net, String result) throws IOException {
        int status = net.getLastStatusCode();
        if (status / 100 != 2) {
            throw new IOException("Qwen Files API HTTP " + status + ": " + result);
        }
        return parse(result);
    }

    private static String guessContentType(String filename) {
        String type = URLConnection.guessContentTypeFromName(filename);
        return type != null ? type : "application/octet-stream";
    }

    private static String fileNameFromUrl(String url) {
        String path = url.trim();
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        try {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // 使用原始文件名
        }
        return name.isEmpty() ? "file" : name;
    }

    /**
     * Files API 上传结果。
     */
    public record QwenFileUpload(String id, String filename, String purpose, long bytes, JSONObject raw) {
    }
}
