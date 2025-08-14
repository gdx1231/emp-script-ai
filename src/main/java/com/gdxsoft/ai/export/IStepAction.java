package com.gdxsoft.ai.export;

import org.json.JSONObject;

import com.gdxsoft.easyweb.script.RequestValue;

public interface IStepAction {
	JSONObject doAction(RequestValue rv, String fullText);
}