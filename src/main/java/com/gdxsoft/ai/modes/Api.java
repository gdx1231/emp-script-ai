package com.gdxsoft.ai.modes;

import java.util.List;
import java.util.ArrayList;

/**
 * <api> 元素的数据模型，来自 XML <apis> 下的子项。
 * <p>
 * Data model for <api> element under XML <apis> node.
 * 
 */
public class Api {
    private String name;
    private String description;
    private String url;
    private String parameters;
    private boolean refRequest = false;
    private String key;
    private int timeout = 5000; // 默认5秒超时
    private String method = "GET"; // 默认GET方法
    private String body;
    private String usage; // 工具调用说明（<tool>/<api> 元素内的 CDATA），构建 apisCheck prompt 时自动附加
    private List<ApiHeader> headers;
    private List<ApiField> form;

    /**
     * 构造函数
     */
    public Api() {
        this.headers = new ArrayList<>();
        this.form = new ArrayList<>();
    }

    /**
     * 构造函数
     * 
     * @param name API名称
     * @param url  请求URL
     */
    public Api(String name, String url) {
        this();
        this.name = name;
        this.url = url;
    }

    /**
     * 构造函数
     * 
     * @param name        API名称
     * @param description API描述
     * @param url         请求URL
     */
    public Api(String name, String description, String url) {
        this();
        this.name = name;
        this.description = description;
        this.url = url;
    }

    /**
     * 构造函数
     * 
     * @param name       API名称
     * @param url        请求URL
     * @param method     HTTP方法
     * @param timeout    超时时间（毫秒）
     * @param refRequest 是否引用请求参数
     */
    public Api(String name, String url, String method, int timeout, boolean refRequest) {
        this();
        this.name = name;
        this.url = url;
        this.method = method;
        this.timeout = timeout;
        this.refRequest = refRequest;
    }

    // Getters and Setters

    /**
     * 获取API名称
     * 
     * @return API名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置API名称
     * 
     * @param name API名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取API描述
     * 
     * @return API描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置API描述
     * 
     * @param description API描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取请求URL
     * 
     * @return 请求URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置请求URL
     * 
     * @param url 请求URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * 获取请求参数字符串
     * 
     * @return 请求参数字符串
     */
    public String getParameters() {
        return parameters;
    }

    /**
     * 设置请求参数字符串
     * 
     * @param parameters 请求参数字符串
     */
    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    /**
     * 是否引用当前请求的参数
     * 
     * @return true表示引用当前请求参数，false表示不引用
     */
    public boolean isRefRequest() {
        return refRequest;
    }

    /**
     * 设置是否引用当前请求的参数
     * 
     * @param refRequest true表示引用当前请求参数，false表示不引用
     */
    public void setRefRequest(boolean refRequest) {
        this.refRequest = refRequest;
    }

    /**
     * 获取API密钥
     * 
     * @return API密钥
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置API密钥
     * 
     * @param key API密钥
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 获取请求超时时间（毫秒）
     * 
     * @return 超时时间
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * 设置请求超时时间（毫秒）
     * 
     * @param timeout 超时时间
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * 获取HTTP请求方法
     * 
     * @return HTTP方法，如GET、POST等
     */
    public String getMethod() {
        return method;
    }

    /**
     * 设置HTTP请求方法
     * 
     * @param method HTTP方法，如GET、POST等
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * 获取请求体内容
     * 
     * @return 请求体内容
     */
    public String getBody() {
        return body;
    }

    /**
     * 设置请求体内容
     * 
     * @param body 请求体内容
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * 获取工具调用说明（&lt;tool&gt;/&lt;api&gt; 元素内的 CDATA）
     * 
     * @return 调用说明，未定义返回 null
     */
    public String getUsage() {
        return usage;
    }

    /**
     * 设置工具调用说明
     * 
     * @param usage 调用说明
     */
    public void setUsage(String usage) {
        this.usage = usage;
    }

    /**
     * 获取请求头列表
     * 
     * @return 请求头列表
     */
    public List<ApiHeader> getHeaders() {
        return headers;
    }

    /**
     * 设置请求头列表
     * 
     * @param headers 请求头列表
     */
    public void setHeaders(List<ApiHeader> headers) {
        this.headers = headers != null ? headers : new ArrayList<>();
    }

    /**
     * 添加请求头
     * 
     * @param header 请求头对象
     */
    public void addHeader(ApiHeader header) {
        if (header != null) {
            this.headers.add(header);
        }
    }

    /**
     * 添加请求头
     * 
     * @param name  请求头名称
     * @param value 请求头值
     */
    public void addHeader(String name, String value) {
        this.headers.add(new ApiHeader(name, value));
    }

    /**
     * 获取表单字段列表
     * 
     * @return 表单字段列表
     */
    public List<ApiField> getForm() {
        return form;
    }

    /**
     * 设置表单字段列表
     * 
     * @param form 表单字段列表
     */
    public void setForm(List<ApiField> form) {
        this.form = form != null ? form : new ArrayList<>();
    }

    /**
     * 添加表单字段
     * 
     * @param field 表单字段对象
     */
    public void addField(ApiField field) {
        if (field != null) {
            this.form.add(field);
        }
    }

    /**
     * 添加表单字段
     * 
     * @param name  字段名称
     * @param value 字段值
     */
    public void addField(String name, String value) {
        this.form.add(new ApiField(name, value));
    }

    @Override
    public String toString() {
        return "Api{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", url='" + url + '\'' +
                ", method='" + method + '\'' +
                ", timeout=" + timeout +
                ", refRequest=" + refRequest +
                ", headersCount=" + (headers != null ? headers.size() : 0) +
                ", formFieldsCount=" + (form != null ? form.size() : 0) +
                '}';
    }
}