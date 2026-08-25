package com.gdxsoft.ai.video.asset;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArkAssetClient 单元测试 -- 参数校验与辅助方法。
 * <p>
 * 纯单元测试，不需要真实 AK/SK。
 *
 * <pre>
 * mvn test -Dtest=ArkAssetClientTest
 * </pre>
 */
class ArkAssetClientTest {

    // ==================== 构造参数校验 ====================

    @Test
    @DisplayName("构造函数：AK 为空报错")
    void constructorEmptyAccessKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArkAssetClient("", "sk"));
    }

    @Test
    @DisplayName("构造函数：SK 为空报错")
    void constructorEmptySecretKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArkAssetClient("ak", ""));
    }

    @Test
    @DisplayName("构造函数：正常创建")
    void constructorValid() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertNotNull(client);
    }

    // ==================== toAssetUri ====================

    @Test
    @DisplayName("toAssetUri：正常转换")
    void toAssetUriValid() {
        assertEquals("asset://asset-20260318035710-abc",
                ArkAssetClient.toAssetUri("asset-20260318035710-abc"));
    }

    @Test
    @DisplayName("toAssetUri：空 ID 报错")
    void toAssetUriEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> ArkAssetClient.toAssetUri(""));
        assertThrows(IllegalArgumentException.class,
                () -> ArkAssetClient.toAssetUri(null));
    }

    // ==================== 方法参数校验 ====================

    @Test
    @DisplayName("createVisualValidateSession：callbackURL 为空报错")
    void createSessionEmptyCallback() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.createVisualValidateSession("", "default"));
    }

    @Test
    @DisplayName("getVisualValidateResult：bytedToken 为空报错")
    void getValidateResultEmptyToken() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.getVisualValidateResult("", "default"));
    }

    @Test
    @DisplayName("createAsset：groupId 为空报错")
    void createAssetEmptyGroupId() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.createAsset("", "https://example.com/img.jpg", "Image", "default"));
    }

    @Test
    @DisplayName("createAsset：url 为空报错")
    void createAssetEmptyUrl() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.createAsset("group-123", "", "Image", "default"));
    }

    @Test
    @DisplayName("createAsset：assetType 为空报错")
    void createAssetEmptyType() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.createAsset("group-123", "https://example.com/img.jpg", "", "default"));
    }

    @Test
    @DisplayName("getAsset：assetId 为空报错")
    void getAssetEmptyId() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.getAsset("", "default"));
    }

    @Test
    @DisplayName("deleteAsset：assetId 为空报错")
    void deleteAssetEmptyId() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.deleteAsset("", "default"));
    }

    @Test
    @DisplayName("deleteAssetGroup：groupId 为空报错")
    void deleteAssetGroupEmptyId() {
        ArkAssetClient client = new ArkAssetClient("ak", "sk");
        assertThrows(IllegalArgumentException.class,
                () -> client.deleteAssetGroup("", "default"));
    }

    // ==================== 常量 ====================

    @Test
    @DisplayName("常量值正确")
    void constants() {
        assertEquals("open.volcengineapi.com", ArkAssetClient.DEFAULT_HOST);
        assertEquals("ark", ArkAssetClient.SERVICE);
        assertEquals("2024-01-01", ArkAssetClient.VERSION);
        assertEquals("cn-beijing", ArkAssetClient.REGION);
    }
}
