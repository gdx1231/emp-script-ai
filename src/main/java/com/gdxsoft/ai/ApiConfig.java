package com.gdxsoft.ai;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * AI 接口配置（供应商 + 模型 + URL + Key + 类型）。
 * <p>
 * 对应数据库表 {@code AI_PROVIDER_MODEL}（模型，含 APM_TYPE）与
 * {@code AI_PROVIDER_URL}（接入点 URL / Key），通过 {@link #instanceOfDb} /
 * {@link #instanceOfDbByType} 加载。
 */
public class ApiConfig {
    private String apiKey;
    private String apiUrl;
    private String apiModel;
    private String apiProvider;
    private String apiType;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiModel() {
        return apiModel;
    }

    public void setApiModel(String apiModel) {
        this.apiModel = apiModel;
    }

    public String getApiProvider() {
        return apiProvider;
    }

    public void setApiProvider(String apiProvider) {
        this.apiProvider = apiProvider;
    }

    public String getApiType() {
        return apiType;
    }

    public void setApiType(String apiType) {
        this.apiType = apiType;
    }

    /**
     * 按模型代码从数据库加载接口配置。
     * <p>
     * 查询 {@code AI_PROVIDER_MODEL} 关联 {@code AI_PROVIDER_URL}（均要求状态为 USED），
     * 按 {@code APU_MDATE} 倒序取第一条。
     *
     * @param apiProvider  供应商代码（{@code AI_PROVIDER_MODEL.AP_CODE}，如 qwen、doubao_stt）
     * @param apiModel     模型代码（{@code AI_PROVIDER_MODEL.APM_CODE}，如 qwen3-asr-flash）
     * @param dbConfigName 数据库配置名称（ewa_conf.xml），null/空表示默认配置
     * @param apiOwnerId   API Key 拥有者（{@code AI_PROVIDER_URL.APU_OWN_ID}），null/空表示不限制
     * @return 配置对象；未找到匹配记录时返回 null
     */
    public static ApiConfig instanceOfDb(String apiProvider, String apiModel, String dbConfigName,
            String apiOwnerId) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("_ap_code", apiProvider);
        rv.addOrUpdateValue("_apm_code", apiModel);
        return loadFromDb("and a.APM_CODE = @_apm_code", rv, dbConfigName, apiOwnerId);
    }

    /**
     * 按模型类型从数据库加载接口配置（同一供应商下同类型有多个模型时，取 {@code APU_MDATE} 最新的一条）。
     *
     * @param apiProvider  供应商代码（{@code AI_PROVIDER_MODEL.AP_CODE}，如 qwen、doubao_stt）
     * @param apiType      模型类型（{@code AI_PROVIDER_MODEL.APM_TYPE}，如 AI_TP_STT）
     * @param dbConfigName 数据库配置名称（ewa_conf.xml），null/空表示默认配置
     * @param apiOwnerId   API Key 拥有者（{@code AI_PROVIDER_URL.APU_OWN_ID}），null/空表示不限制
     * @return 配置对象；未找到匹配记录时返回 null
     */
    public static ApiConfig instanceOfDbByType(String apiProvider, String apiType, String dbConfigName,
            String apiOwnerId) {
        RequestValue rv = new RequestValue();
        rv.addOrUpdateValue("_ap_code", apiProvider);
        rv.addOrUpdateValue("_apm_type", apiType);
        return loadFromDb("and a.APM_TYPE = @_apm_type", rv, dbConfigName, apiOwnerId);
    }

    /**
     * 执行配置查询（两个工厂方法共用）。
     *
     * @param modelCondition 模型表的附加条件（含前导 and，占位符已写入 rv）
     */
    private static ApiConfig loadFromDb(String modelCondition, RequestValue rv, String dbConfigName,
            String apiOwnerId) {
        StringBuilder sql = new StringBuilder("""
                select a.AP_CODE, a.APM_CODE, a.APM_TYPE, b.APU_URL, b.APU_KEY
                from AI_PROVIDER_MODEL a
                inner join AI_PROVIDER_URL b on a.AP_CODE = b.AP_CODE
                where a.APM_STATUS = 'USED' and b.APU_STATUS = 'USED'
                  and a.AP_CODE = @_ap_code
                """);
        sql.append(' ').append(modelCondition);
        if (apiOwnerId != null && !apiOwnerId.isEmpty()) {
            rv.addOrUpdateValue("_apu_own_id", apiOwnerId);
            sql.append(" and b.APU_OWN_ID = @_apu_own_id ");
        }
        sql.append(" order by b.APU_MDATE desc");

        DTTable tb = DTTable.getJdbcTable(sql.toString(), dbConfigName, rv);
        if (tb.getCount() == 0) {
            return null;
        }

        ApiConfig conf = new ApiConfig();
        conf.setApiProvider(cellString(tb, "AP_CODE"));
        conf.setApiModel(cellString(tb, "APM_CODE"));
        conf.setApiType(cellString(tb, "APM_TYPE"));
        conf.setApiUrl(cellString(tb, "APU_URL"));
        conf.setApiKey(cellString(tb, "APU_KEY"));
        return conf;
    }

    /**
     * 将本配置应用到 {@link ChatManagerBase}（apiKey / apiUrl / aiProvider / aiModel）。
     * <p>
     * null 字段跳过，不覆盖目标对象已有值。
     *
     * @param cmb 聊天管理器
     */
    public void setChatManagerApiParas(ChatManagerBase cmb) {
        if (cmb == null) {
            throw new IllegalArgumentException("cmb is null");
        }
        if (apiKey != null) cmb.setApiKey(apiKey);
        if (apiUrl != null) cmb.setApiUrl(apiUrl);
        if (apiProvider != null) cmb.setAiProvider(apiProvider);
        if (apiModel != null) cmb.setAiModel(apiModel);
    }

    /** 安全读取第一行指定列（列不存在或值为 null 时返回 null）。 */
    private static String cellString(DTTable tb, String colName) {
        try {
            Object v = tb.getCell(0, colName);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 调试输出（apiKey 脱敏）。 */
    @Override
    public String toString() {
        String masked = apiKey == null ? null
                : (apiKey.length() <= 4 ? "****" : apiKey.substring(0, 4) + "****");
        return "ApiConfig{provider=" + apiProvider + ", model=" + apiModel + ", type=" + apiType
                + ", url=" + apiUrl + ", key=" + masked + "}";
    }
}
