package com.gdxsoft.ai.platform;

import java.time.LocalDate;
import java.util.List;

import com.gdxsoft.ai.request.ProviderType;

/**
 * Provider 平台适配器接口。
 * <p>
 * 每个 AI 服务商实现此接口，提供模型列表、使用量、费用等查询能力。
 */
public interface PlatformAdapter {

    /**
     * 对应的 provider 类型
     */
    ProviderType getProviderType();

    /**
     * 查询可用模型列表（调用 provider 的 models API）
     *
     * @param apiUrl API 基础地址
     * @param apiKey API 密钥
     * @return 模型信息列表
     */
    List<ModelInfo> listModels(String apiUrl, String apiKey) throws Exception;

    /**
     * 查询使用量（如果 provider 支持）
     *
     * @param apiUrl API 基础地址
     * @param apiKey API 密钥
     * @param start  开始日期
     * @param end    结束日期
     * @return 使用量汇总，不支持时返回 null
     */
    UsageSummary getUsage(String apiUrl, String apiKey, LocalDate start, LocalDate end) throws Exception;

    /**
     * 查询费用（如果 provider 支持）
     *
     * @param apiUrl API 基础地址
     * @param apiKey API 密钥
     * @param start  开始日期
     * @param end    结束日期
     * @return 费用汇总，不支持时返回 null
     */
    BillingSummary getBilling(String apiUrl, String apiKey, LocalDate start, LocalDate end) throws Exception;
}
