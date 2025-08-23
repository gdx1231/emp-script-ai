package com.gdxsoft.ai.modes;

/**
 * API请求头的数据模型，对应 XML <header> 元素。
 * <p>
 * Data model for API header, corresponds to XML <header> element.
 * 
 * @author PF2023项目组
 * @since 2025-08-23
 */
public class ApiHeader {
    private String name;
    private String value;

    /**
     * 默认构造函数
     */
    public ApiHeader() {
    }

    /**
     * 构造函数
     * 
     * @param name  请求头名称
     * @param value 请求头值
     */
    public ApiHeader(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * 获取请求头名称
     * 
     * @return 请求头名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置请求头名称
     * 
     * @param name 请求头名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取请求头值
     * 
     * @return 请求头值
     */
    public String getValue() {
        return value;
    }

    /**
     * 设置请求头值
     * 
     * @param value 请求头值
     */
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ApiHeader{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ApiHeader apiHeader = (ApiHeader) o;

        if (name != null ? !name.equals(apiHeader.name) : apiHeader.name != null)
            return false;
        return value != null ? value.equals(apiHeader.value) : apiHeader.value == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}