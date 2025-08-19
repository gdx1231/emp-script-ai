package com.gdxsoft.ai.request;

import java.io.PrintWriter;

import org.json.JSONObject;

import com.gdxsoft.easyweb.utils.UJSon;

public class ActionEventsOut extends DefaultOutEvents {
	@Override
	public void outEvent(String msg, PrintWriter writer) {
		boolean isEn = !"zhcn".equals(this.getLang());
		JSONObject msg1 = UJSon.rstTrue();
		msg1.put("IDX", this.getMessageCount());
		if (this.getMessageCount() == 0) {
			msg1.put("content", isEn?"In data processing": "数据处理中");
			msg1.put("action", this.getName());
		} else {
			msg1.put("content", " .");
		}
		super.outEvent(msg1.toString(), writer);
	}
}