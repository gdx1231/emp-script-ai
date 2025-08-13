package com.gdxsoft.ai.export;

import org.json.JSONObject;

import com.gdxsoft.easyweb.script.RequestValue;

public interface IExport {
	JSONObject doExport(RequestValue rv, String fullText);
}