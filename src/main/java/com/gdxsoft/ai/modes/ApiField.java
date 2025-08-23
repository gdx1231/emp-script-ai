package com.gdxsoft.ai.modes;

/**
 * API表单字段的数据模型，对应 XML <field> 元素。
 * <p>
 * Data model for API form field, corresponds to XML <field> element.
 * 
 * @author PF2023项目组
 * @since 2025-08-23
 */
public class ApiField {
    private String name;
    private String value;

    /**
     * 默认构造函数
     */
    public ApiField() {
    }

    /**
     * 构造函数
     * 
     * @param name  字段名称
     * @param value 字段值
     */
    public ApiField(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * 获取字段名称
     * 
     * @return 字段名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置字段名称
     * 
     * @param name 字段名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取字段值
     * 
     * @return 字段值
     */
    public String getValue() {
        return value;
    }

    /**
     * 设置字段值
     * 
     * @param value 字段值
     */
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ApiField{" +
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

        ApiField apiField = (ApiField) o;

        if (name != null ? !name.equals(apiField.name) : apiField.name != null)
            return false;
        return value != null ? value.equals(apiField.value) : apiField.value == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}