package com.github.ziran_ink.web_weixin_sdk;

import com.alibaba.fastjson.JSON;

public class HelloWorld {

	public static void main(String[] args) {
		Wechat wechat = new Wechat(new SimpleMsgHandler(), "/Users/xuzewei/Downloads/qr.jpg");
		wechat.start();
		System.out.println(JSON.toJSONString(wechat.getWeixin().getStorage().getUserSelf()));
		System.out.println(JSON.toJSONString(wechat.getWeixin().getStorage().getLoginInfo()));
		System.out.println(JSON.toJSONString(wechat.getWeixin().getStorage().getNickName()));
		System.out.println(wechat.getWeixin().sendMsgByNickName("测试消息😁", "罗晓娟"));
	}

}
