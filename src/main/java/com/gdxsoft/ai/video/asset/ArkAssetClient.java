package com.gdxsoft.ai.video.asset;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SimpleTimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.HttpUtils;

/**
 * 火山方舟私域素材库客户端。
 * <p>
 * 支持真人人像素材的认证、上传、查询，用于 Seedance 视频生成时的版权肖像保护。
 * <p>
 * 使用流程：
 * <ol>
 *   <li>{@link #createVisualValidateSession(String, String)} 生成 H5 真人认证链接</li>
 *   <li>终端用户完成 H5 认证后，{@link #getVisualValidateResult(String, String)} 获取 GroupId</li>
 *   <li>{@link #createAsset(String, String, String, String)} 上传素材（图片/视频/音频）</li>
 *   <li>{@link #getAsset(String, String)} 轮询素材状态，直到 Active</li>
 *   <li>使用 {@code asset://<AssetId>} 在 Seedance 视频生成中引用</li>
 * </ol>
 * <p>
 * 鉴权方式：AK/SK + HmacSHA256 签名（火山引擎标准鉴权）。
 *
 * @since 1.3.0
 * @see <a href="https://docs.volcengine.com/docs/82379/2333589">私域真人人像素材资产使用指南</a>
 */
public class ArkAssetClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArkAssetClient.class);

    /** 火山方舟服务域名 */
    public static final String DEFAULT_HOST = "open.volcengineapi.com";
    /** 服务名称 */
    public static final String SERVICE = "ark";
    /** API 版本 */
    public static final String VERSION = "2024-01-01";
    /** 区域 */
    public static final String REGION = "cn-beijing";

    private final String accessKey;
    private final String secretKey;
    private final String host;

    /**
     * 构造客户端。
     *
     * @param accessKey 火山引擎 Access Key（AK）
     * @param secretKey 火山引擎 Secret Key（SK）
     */
    public ArkAssetClient(String accessKey, String secretKey) {
        this(accessKey, secretKey, DEFAULT_HOST);
    }

    /**
     * 构造客户端（自定义域名）。
     *
     * @param accessKey 火山引擎 Access Key（AK）
     * @param secretKey 火山引擎 Secret Key（SK）
     * @param host      服务域名
     */
    public ArkAssetClient(String accessKey, String secretKey, String host) {
        if (accessKey == null || accessKey.isEmpty())
            throw new IllegalArgumentException("accessKey 不能为空");
        if (secretKey == null || secretKey.isEmpty())
            throw new IllegalArgumentException("secretKey 不能为空");
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.host = host != null ? host : DEFAULT_HOST;
    }

    // ==================== 真人认证 ====================

    /**
     * 创建真人认证会话，生成 H5 认证页链接。
     * <p>
     * 终端用户通过 H5Link 完成真人认证后，将跳转到 CallbackURL 并附带 resultCode 参数。
     * resultCode=10000 表示认证成功，此时可使用返回的 BytedToken 调用
     * {@link #getVisualValidateResult(String, String)} 获取 GroupId。
     *
     * @param callbackURL 认证完成后的回调 URL（必填）
     * @param projectName 资源项目名称，默认 "default"
     * @return 包含 H5Link、BytedToken、CallbackURL 的响应
     * @throws IOException 请求失败
     */
    public JSONObject createVisualValidateSession(String callbackURL, String projectName) throws IOException {
        if (callbackURL == null || callbackURL.isEmpty())
            throw new IllegalArgumentException("callbackURL 不能为空");

        JSONObject body = new JSONObject();
        body.put("CallbackURL", callbackURL);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("CreateVisualValidateSession", body);
    }

    /**
     * 获取真人认证结果及创建的 Asset Group ID。
     * <p>
     * 在终端用户完成 H5 认证（resultCode=10000）后调用，使用 CreateVisualValidateSession
     * 返回的 BytedToken 查询本次认证创建的 GroupId。
     *
     * @param bytedToken  认证凭证（来自 CreateVisualValidateSession 返回）
     * @param projectName 资源项目名称，需与创建会话时一致
     * @return 包含 GroupId 的响应
     * @throws IOException 请求失败
     */
    public JSONObject getVisualValidateResult(String bytedToken, String projectName) throws IOException {
        if (bytedToken == null || bytedToken.isEmpty())
            throw new IllegalArgumentException("bytedToken 不能为空");

        JSONObject body = new JSONObject();
        body.put("BytedToken", bytedToken);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("GetVisualValidateResult", body);
    }

    // ==================== 素材管理 ====================

    /**
     * 创建素材资产。
     * <p>
     * 上传素材到指定的 Asset Group。素材入库时会与真人认证的基准图像进行面部特征一致性比对。
     * 上传后素材状态为 Processing，需轮询 {@link #getAsset(String, String)} 直到状态变为 Active。
     *
     * @param groupId     素材组 ID（来自 GetVisualValidateResult）
     * @param url         素材的可访问 URL（图片/视频/音频）
     * @param assetType   素材类型："Image" / "Video" / "Audio"
     * @param projectName 资源项目名称，默认 "default"
     * @return 包含素材 Id 的响应
     * @throws IOException 请求失败
     */
    public JSONObject createAsset(String groupId, String url, String assetType, String projectName) throws IOException {
        if (groupId == null || groupId.isEmpty())
            throw new IllegalArgumentException("groupId 不能为空");
        if (url == null || url.isEmpty())
            throw new IllegalArgumentException("url 不能为空");
        if (assetType == null || assetType.isEmpty())
            throw new IllegalArgumentException("assetType 不能为空");

        JSONObject body = new JSONObject();
        body.put("GroupId", groupId);
        body.put("URL", url);
        body.put("AssetType", assetType);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("CreateAsset", body);
    }

    /**
     * 获取单个素材信息。
     * <p>
     * 用于轮询素材状态。仅当 Status 为 "Active" 时，素材可用于视频生成。
     *
     * @param assetId     素材 ID
     * @param projectName 资源项目名称
     * @return 素材详情（包含 Status、URL、GroupId 等）
     * @throws IOException 请求失败
     */
    public JSONObject getAsset(String assetId, String projectName) throws IOException {
        if (assetId == null || assetId.isEmpty())
            throw new IllegalArgumentException("assetId 不能为空");

        JSONObject body = new JSONObject();
        body.put("Id", assetId);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("GetAsset", body);
    }

    /**
     * 查询素材列表。
     *
     * @param groupIds    素材组 ID 列表（可选，null 表示不按组过滤）
     * @param statuses    状态过滤（可选，如 ["Active", "Processing"]）
     * @param name        名称模糊搜索（可选）
     * @param projectName 资源项目名称
     * @param pageNumber  页码（从 1 开始）
     * @param pageSize    每页数量
     * @return 素材列表（包含 Items、TotalCount、PageNumber、PageSize）
     * @throws IOException 请求失败
     */
    public JSONObject listAssets(String[] groupIds, String[] statuses, String name,
                                  String projectName, int pageNumber, int pageSize) throws IOException {
        JSONObject body = new JSONObject();

        JSONObject filter = new JSONObject();
        if (groupIds != null && groupIds.length > 0) {
            filter.put("GroupIds", groupIds);
        }
        if (statuses != null && statuses.length > 0) {
            filter.put("Statuses", statuses);
        }
        if (name != null && !name.isEmpty()) {
            filter.put("Name", name);
        }
        body.put("Filter", filter);
        body.put("PageNumber", pageNumber > 0 ? pageNumber : 1);
        body.put("PageSize", pageSize > 0 ? pageSize : 10);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("ListAssets", body);
    }

    /**
     * 查询素材组列表。
     *
     * @param name        名称模糊搜索（可选）
     * @param groupType   素材组类型（如 "LivenessFace" 表示真人人像）
     * @param projectName 资源项目名称
     * @param pageNumber  页码
     * @param pageSize    每页数量
     * @return 素材组列表
     * @throws IOException 请求失败
     */
    public JSONObject listAssetGroups(String name, String groupType,
                                       String projectName, int pageNumber, int pageSize) throws IOException {
        JSONObject body = new JSONObject();

        JSONObject filter = new JSONObject();
        if (name != null && !name.isEmpty()) {
            filter.put("Name", name);
        }
        if (groupType != null && !groupType.isEmpty()) {
            filter.put("GroupType", groupType);
        }
        body.put("Filter", filter);
        body.put("PageNumber", pageNumber > 0 ? pageNumber : 1);
        body.put("PageSize", pageSize > 0 ? pageSize : 10);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("ListAssetGroups", body);
    }

    /**
     * 删除素材。
     *
     * @param assetId     素材 ID
     * @param projectName 资源项目名称
     * @return 删除结果
     * @throws IOException 请求失败
     */
    public JSONObject deleteAsset(String assetId, String projectName) throws IOException {
        if (assetId == null || assetId.isEmpty())
            throw new IllegalArgumentException("assetId 不能为空");

        JSONObject body = new JSONObject();
        body.put("Id", assetId);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("DeleteAsset", body);
    }

    /**
     * 删除素材组。
     *
     * @param groupId     素材组 ID
     * @param projectName 资源项目名称
     * @return 删除结果
     * @throws IOException 请求失败
     */
    public JSONObject deleteAssetGroup(String groupId, String projectName) throws IOException {
        if (groupId == null || groupId.isEmpty())
            throw new IllegalArgumentException("groupId 不能为空");

        JSONObject body = new JSONObject();
        body.put("Id", groupId);
        body.put("ProjectName", projectName != null ? projectName : "default");

        return doRequest("DeleteAssetGroup", body);
    }

    // ==================== 辅助方法 ====================

    /**
     * 轮询等待素材状态变为 Active。
     *
     * @param assetId       素材 ID
     * @param projectName   资源项目名称
     * @param maxAttempts   最大轮询次数
     * @param intervalMs    轮询间隔（毫秒）
     * @return 素材详情（Status=Active）
     * @throws IOException          请求失败
     * @throws InterruptedException 线程中断
     * @throws IllegalStateException 素材状态为 Failed 或超时
     */
    public JSONObject waitForAssetActive(String assetId, String projectName,
                                          int maxAttempts, long intervalMs)
            throws IOException, InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            JSONObject asset = getAsset(assetId, projectName);
            String status = asset.optString("Status", "");

            if ("Active".equals(status)) {
                return asset;
            }
            if ("Failed".equals(status)) {
                throw new IllegalStateException("素材处理失败: " + assetId);
            }

            LOGGER.debug("素材 {} 状态: {}，等待 {}ms...", assetId, status, intervalMs);
            Thread.sleep(intervalMs);
        }
        throw new IllegalStateException("素材处理超时: " + assetId);
    }

    /**
     * 构造 asset URI，用于在 Seedance 视频生成中引用已认证的素材。
     *
     * @param assetId 素材 ID
     * @return asset URI，格式为 {@code asset://<assetId>}
     */
    public static String toAssetUri(String assetId) {
        if (assetId == null || assetId.isEmpty())
            throw new IllegalArgumentException("assetId 不能为空");
        return "asset://" + assetId;
    }

    // ==================== 请求签名与发送 ====================

    /**
     * 执行 API 请求。
     */
    private JSONObject doRequest(String action, JSONObject body) throws IOException {
        String url = buildUrl(action);
        String responseBody = postJson(url, body);
        JSONObject json = new JSONObject(responseBody);

        // 检查响应元数据中的错误
        JSONObject metadata = json.optJSONObject("ResponseMetadata");
        if (metadata != null) {
            JSONObject error = metadata.optJSONObject("Error");
            if (error != null) {
                String code = error.optString("Code", "");
                String message = error.optString("Message", "");
                throw new IOException("火山方舟 API 错误 [" + code + "]: " + message);
            }
        }

        // 返回 Result 部分
        JSONObject result = json.optJSONObject("Result");
        return result != null ? result : json;
    }

    /**
     * 构建请求 URL。
     */
    private String buildUrl(String action) {
        return "https://" + host + "/?Action=" + action + "&Version=" + VERSION;
    }

    /**
     * 发送 POST 请求（带火山引擎 AK/SK 签名）。
     */
    private String postJson(String url, JSONObject body) throws IOException {
        try {
            // 生成签名所需的时间戳和日期
            String datetime = getUtcDatetime();
            String date = datetime.substring(0, 8);

            // 构建签名
            String canonicalRequest = buildCanonicalRequest(body.toString());
            String stringToSign = buildStringToSign(datetime, canonicalRequest);
            String signature = sign(stringToSign, date);

            // 构建 Authorization header
            String authorization = buildAuthorization(datetime, signature);

            HttpClient client = HttpUtils.createHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Host", host)
                    .header("X-Date", datetime)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        } catch (Exception e) {
            throw new IOException("请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建规范请求。
     */
    private String buildCanonicalRequest(String payload) throws Exception {
        String canonicalHeaders = "content-type:application/json\n" +
                "host:" + host + "\n" +
                "x-content-sha256:" + sha256Hex(payload) + "\n";
        String signedHeaders = "content-type;host;x-content-sha256";

        return "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256Hex(payload);
    }

    /**
     * 构建待签名字符串。
     */
    private String buildStringToSign(String datetime, String canonicalRequest) throws Exception {
        String date = datetime.substring(0, 8);
        String credentialScope = date + "/" + REGION + "/" + SERVICE + "/request";
        return "HMAC-SHA256\n" + datetime + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);
    }

    /**
     * 计算签名。
     */
    private String sign(String stringToSign, String date) throws Exception {
        String credentialScope = date + "/" + REGION + "/" + SERVICE + "/request";

        // 派生签名密钥
        byte[] kDate = hmacSha256(("SK" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmacSha256(kDate, REGION);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        byte[] kSigning = hmacSha256(kService, "request");

        // 计算签名
        byte[] signature = hmacSha256(kSigning, stringToSign);
        return bytesToHex(signature);
    }

    /**
     * 构建 Authorization header。
     */
    private String buildAuthorization(String datetime, String signature) {
        String date = datetime.substring(0, 8);
        String credentialScope = date + "/" + REGION + "/" + SERVICE + "/request";
        String signedHeaders = "content-type;host;x-content-sha256";

        return "HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope +
                ", SignedHeaders=" + signedHeaders +
                ", Signature=" + signature;
    }

    /**
     * 获取 UTC 时间字符串（格式：yyyyMMdd'T'HHmmss'Z'）。
     */
    private String getUtcDatetime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(new SimpleTimeZone(0, "UTC"));
        return sdf.format(new Date());
    }

    /**
     * HmacSHA256 计算。
     */
    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA256 哈希（十六进制）。
     */
    private String sha256Hex(String data) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * 字节数组转十六进制字符串。
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
