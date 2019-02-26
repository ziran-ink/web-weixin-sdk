package com.github.ziran_ink.web_weixin_sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ziran_ink.web_weixin_sdk.core.MsgHandler;
import com.github.ziran_ink.web_weixin_sdk.core.Weixin;
import com.github.ziran_ink.web_weixin_sdk.core.impl.WeixinImpl;
import com.github.ziran_ink.web_weixin_sdk.utils.CommonTools;

public class Wechat {
	private static final Logger LOG = LoggerFactory.getLogger(Wechat.class);
	private Weixin weixin = new WeixinImpl();
	private MsgHandler msgHandler;
	private String qrcodeFilePath;

	static {
		System.setProperty("jsse.enableSNIExtension", "false"); // 防止SSL错误
	}

	public Wechat(MsgHandler msgHandler, String qrcodeFilePath) {
		this.msgHandler = msgHandler;
		this.qrcodeFilePath = qrcodeFilePath;
	}

	public void start() {
		if (weixin.getStorage().isAlive()) { // 已登陆
			LOG.info("已登陆");
			return;
		}
		while (true) {
			for (int count = 0; count < 10; count++) {
				LOG.info("获取UUID");
				while (weixin.getUuid() == null) {
					LOG.info("1. 获取微信UUID");
					while (weixin.getUuid() == null) {
						LOG.warn("1.1. 获取微信UUID失败，两秒后重新获取");
						CommonTools.sleep(2000);
					}
				}
				LOG.info("2. 获取登陆二维码图片");
				if (weixin.getQR(qrcodeFilePath)) {
					break;
				} else if (count == 10) {
					LOG.error("2.2. 获取登陆二维码图片失败，系统退出");
					System.exit(0);
				}
			}
			LOG.info("3. 请扫描二维码图片，并在手机上确认");
			if (!weixin.getStorage().isAlive()) {
				weixin.login();
				weixin.getStorage().setAlive(true);
				LOG.info(("登陆成功"));
				break;
			}
			LOG.info("4. 登陆超时，请重新扫描二维码图片");
		}

		LOG.info("5. 登陆成功，微信初始化");
		if (!weixin.webWxInit()) {
			LOG.info("6. 微信初始化异常");
			System.exit(0);
		}

		LOG.info("6. 开启微信状态通知");
		weixin.wxStatusNotify();

		LOG.info("7. 清除。。。。");
		CommonTools.clearScreen();
		LOG.info(String.format("欢迎回来， %s", weixin.getStorage().getNickName()));

		LOG.info("8. 开始接收消息");
		weixin.startReceiving();

		LOG.info("9. 获取联系人信息");
		weixin.webWxGetContact();

		LOG.info("10. 获取群好友及群好友列表");
		weixin.WebWxBatchGetContact();

		LOG.info("11. 缓存本次登陆好友相关消息");
		weixin.setUserInfo(); // 登陆成功后缓存本次登陆好友相关消息（NickName, UserName）

		LOG.info("12.开启微信状态检测线程");
		weixin.startCheckLoginStatus();

		weixin.startHandleMsg(msgHandler);
	}

	public Weixin getWeixin() {
		return weixin;
	}
}
