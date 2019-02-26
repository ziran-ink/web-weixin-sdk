package com.github.ziran_ink.web_weixin_sdk.utils.enums;

public enum ResultEnum {
	SUCCESS("200", "成功"), //
	WAIT_CONFIRM("201", "请在手机上点击确认"), //
	WAIT_SCAN("400", "请扫描二维码");

	private String code;
	private String msg;

	ResultEnum(String code, String msg) {
		this.code = code;
		this.msg = msg;
	}

	public String getCode() {
		return code;
	}

	public String getMsg() {
		return msg;
	}
}
