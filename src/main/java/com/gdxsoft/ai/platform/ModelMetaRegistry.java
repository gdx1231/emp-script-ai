package com.gdxsoft.ai.platform;

/**
 * 模型元数据注册表（已废弃）。
 * <p>
 * 模型信息现在完全从各 provider 的 API 动态获取，不再代码内置。
 * 此类保留为空壳以兼容可能的旧引用。
 *
 * @deprecated 使用 {@link PlatformClient#listModels} 从 provider API 动态获取模型信息
 */
@Deprecated
public class ModelMetaRegistry {

    private ModelMetaRegistry() {}
}
