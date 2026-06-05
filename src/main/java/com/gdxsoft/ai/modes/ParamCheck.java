package com.gdxsoft.ai.modes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParamCheck {
	private String name;
	private String des;
	private String type;
	private String defaultValue;
	private String options;
	private Map<String, String> optionMap;

	public ParamCheck(String name, String des, String type, String defaultValue, String options) {
		this.name = name;
		this.des = des;
		this.type = (type == null || type.trim().isEmpty()) ? "string" : type.trim();
		this.defaultValue = defaultValue;
		this.options = options;
		this.optionMap = new HashMap<>();
		if (options != null && !options.trim().isEmpty()) {
			for (String opt : options.split(",")) {
				String[] kv = opt.trim().split("=", 2);
				if (kv.length == 2) {
					optionMap.put(kv[0].trim(), kv[1].trim());
				}
			}
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDes() {
		return des;
	}

	public void setDes(String des) {
		this.des = des;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public String getOptions() {
		return options;
	}

	public void setOptions(String options) {
		this.options = options;
	}

	public Map<String, String> getOptionMap() {
		return optionMap;
	}

	public List<String> getOptionKeys() {
		return new ArrayList<>(optionMap.keySet());
	}

	public boolean isValidEnumValue(String value) {
		if (optionMap.isEmpty()) {
			return true;
		}
		return optionMap.containsKey(value);
	}

	public String getEnumLabel(String value) {
		return optionMap.getOrDefault(value, value);
	}
}
