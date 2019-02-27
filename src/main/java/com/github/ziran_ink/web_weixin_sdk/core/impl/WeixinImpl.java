package com.github.ziran_ink.web_weixin_sdk.core.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.ziran_ink.web_weixin_sdk.beans.BaseMsg;
import com.github.ziran_ink.web_weixin_sdk.beans.RecommendInfo;
import com.github.ziran_ink.web_weixin_sdk.core.MsgHandler;
import com.github.ziran_ink.web_weixin_sdk.core.Storage;
import com.github.ziran_ink.web_weixin_sdk.core.Weixin;
import com.github.ziran_ink.web_weixin_sdk.utils.CommonTools;
import com.github.ziran_ink.web_weixin_sdk.utils.Config;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.MsgCodeEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.MsgTypeEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.ResultEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.RetCodeEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.StorageLoginInfoEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.URLEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.VerifyFriendEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.parameters.BaseParaEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.parameters.LoginParaEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.parameters.StatusNotifyParaEnum;
import com.github.ziran_ink.web_weixin_sdk.utils.enums.parameters.UUIDParaEnum;

public class WeixinImpl implements Weixin {
	private static Logger LOG = LoggerFactory.getLogger(WeixinImpl.class);
	private Storage storage = new Storage();

	@Override
	public Storage getStorage() {
		return storage;
	}

	@Override
	public boolean login() {
		boolean isLogin = false;
		// 组装参数和URL
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		params.add(new BasicNameValuePair(LoginParaEnum.LOGIN_ICON.para(), LoginParaEnum.LOGIN_ICON.value()));
		params.add(new BasicNameValuePair(LoginParaEnum.UUID.para(), storage.getUuid()));
		params.add(new BasicNameValuePair(LoginParaEnum.TIP.para(), LoginParaEnum.TIP.value()));

		while (!isLogin) {
			LOG.info("微信登陆中……");
			long millis = System.currentTimeMillis();
			params.add(new BasicNameValuePair(LoginParaEnum.R.para(), String.valueOf(millis / 1579L)));
			params.add(new BasicNameValuePair(LoginParaEnum._.para(), String.valueOf(millis)));
			HttpEntity entity = storage.getHttpClientWrapper().doGet(URLEnum.LOGIN_URL.getUrl(), params, true, null);

			try {
				String result = EntityUtils.toString(entity);
				String status = checklogin(result);

				if (ResultEnum.SUCCESS.getCode().equals(status)) {
					processLoginInfo(result); // 处理结果
					isLogin = true;
					storage.setAlive(isLogin);
					LOG.info("微信登陆成功");
					break;
				}
				if (ResultEnum.WAIT_CONFIRM.getCode().equals(status)) {
					LOG.info("请点击微信确认按钮，进行登陆");
				}

			} catch (Exception e) {
				LOG.error("微信登陆异常！", e);
			}
		}
		return isLogin;
	}

	private static String checklogin(String result) {
		String regEx = "window.code=(\\d+)";
		Matcher matcher = CommonTools.getMatcher(regEx, result);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	private void processLoginInfo(String loginContent) {
		String regEx = "window.redirect_uri=\"(\\S+)\";";
		Matcher matcher = CommonTools.getMatcher(regEx, loginContent);
		if (matcher.find()) {
			String originalUrl = matcher.group(1);
			String url = originalUrl.substring(0, originalUrl.lastIndexOf('/')); // https://wx2.qq.com/cgi-bin/mmwebwx-bin
			storage.getLoginInfo().put("url", url);
			Map<String, List<String>> possibleUrlMap = this.getPossibleUrlMap();
			Iterator<Entry<String, List<String>>> iterator = possibleUrlMap.entrySet().iterator();
			Map.Entry<String, List<String>> entry;
			String fileUrl;
			String syncUrl;
			while (iterator.hasNext()) {
				entry = iterator.next();
				String indexUrl = entry.getKey();
				fileUrl = "https://" + entry.getValue().get(0) + "/cgi-bin/mmwebwx-bin";
				syncUrl = "https://" + entry.getValue().get(1) + "/cgi-bin/mmwebwx-bin";
				if (storage.getLoginInfo().get("url").toString().contains(indexUrl)) {
					storage.setIndexUrl(indexUrl);
					storage.getLoginInfo().put("fileUrl", fileUrl);
					storage.getLoginInfo().put("syncUrl", syncUrl);
					break;
				}
			}
			if (storage.getLoginInfo().get("fileUrl") == null && storage.getLoginInfo().get("syncUrl") == null) {
				storage.getLoginInfo().put("fileUrl", url);
				storage.getLoginInfo().put("syncUrl", url);
			}
			storage.getLoginInfo().put("deviceid", "e" + String.valueOf(new Random().nextLong()).substring(1, 16)); // 生成15位随机数
			storage.getLoginInfo().put("BaseRequest", new ArrayList<String>());
			String text = "";

			try {
				HttpEntity entity = storage.getHttpClientWrapper().doGet(originalUrl, null, false, null);
				text = EntityUtils.toString(entity);
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
				return;
			}
			// 如果登录被禁止时，则登录返回的message内容不为空，下面代码则判断登录内容是否为空，不为空则退出程序
			String msg = getLoginMessage(text);
			if (!"".equals(msg)) {
				LOG.error(msg);
			}
			Document doc = CommonTools.xmlParser(text);
			if (doc != null) {
				storage.getLoginInfo().put(StorageLoginInfoEnum.skey.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.skey.getKey()).item(0).getFirstChild()
								.getNodeValue());
				storage.getLoginInfo().put(StorageLoginInfoEnum.wxsid.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.wxsid.getKey()).item(0).getFirstChild()
								.getNodeValue());
				storage.getLoginInfo().put(StorageLoginInfoEnum.wxuin.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.wxuin.getKey()).item(0).getFirstChild()
								.getNodeValue());
				storage.getLoginInfo().put(StorageLoginInfoEnum.pass_ticket.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.pass_ticket.getKey()).item(0).getFirstChild()
								.getNodeValue());
			}
		}
	}

	/**
	 * 解析登录返回的消息，如果成功登录，则message为空
	 */
	private static String getLoginMessage(String result) {
		String[] strArr = result.split("<message>");
		String[] rs = strArr[1].split("</message>");
		if (rs != null && rs.length > 1) {
			return rs[0];
		}
		return "";
	}

	@Override
	public String getUuid() {
		// 组装参数和URL
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		params.add(new BasicNameValuePair(UUIDParaEnum.APP_ID.para(), UUIDParaEnum.APP_ID.value()));
		params.add(new BasicNameValuePair(UUIDParaEnum.FUN.para(), UUIDParaEnum.FUN.value()));
		params.add(new BasicNameValuePair(UUIDParaEnum.LANG.para(), UUIDParaEnum.LANG.value()));
		params.add(new BasicNameValuePair(UUIDParaEnum._.para(), String.valueOf(System.currentTimeMillis())));

		HttpEntity entity = storage.getHttpClientWrapper().doGet(URLEnum.UUID_URL.getUrl(), params, true, null);

		try {
			String result = EntityUtils.toString(entity);
			String regEx = "window.QRLogin.code = (\\d+); window.QRLogin.uuid = \"(\\S+?)\";";
			Matcher matcher = CommonTools.getMatcher(regEx, result);
			if (matcher.find()) {
				if ((ResultEnum.SUCCESS.getCode().equals(matcher.group(1)))) {
					storage.setUuid(matcher.group(2));
				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return storage.getUuid();
	}

	@Override
	public boolean getQR(String qrPath) {
		String qrUrl = URLEnum.QRCODE_URL.getUrl() + storage.getUuid();
		HttpEntity entity = storage.getHttpClientWrapper().doGet(qrUrl, null, true, null);
		try (OutputStream out = new FileOutputStream(qrPath)) {
			out.write(EntityUtils.toByteArray(entity));
			try {
				CommonTools.printQr(qrPath); // 打开登陆二维码图片
			} catch (Exception e) {
				LOG.info(e.getMessage(), e);
			}
		} catch (Exception e) {
			LOG.info(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public boolean webWxInit() {
		storage.setAlive(true);
		storage.setLastNormalRetcodeTime(System.currentTimeMillis());
		// 组装请求URL和参数
		String url = String.format(URLEnum.INIT_URL.getUrl(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()),
				String.valueOf(System.currentTimeMillis() / 3158L),
				storage.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = storage.getParamMap();

		// 请求初始化接口
		HttpEntity entity = storage.getHttpClientWrapper().doPost(url, JSON.toJSONString(paramMap));
		try {
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(result);
			JSONObject user = obj.getJSONObject(StorageLoginInfoEnum.User.getKey());
			JSONObject syncKey = obj.getJSONObject(StorageLoginInfoEnum.SyncKey.getKey());
			storage.getLoginInfo().put(StorageLoginInfoEnum.InviteStartCount.getKey(),
					obj.getInteger(StorageLoginInfoEnum.InviteStartCount.getKey()));
			storage.getLoginInfo().put(StorageLoginInfoEnum.SyncKey.getKey(), syncKey);

			JSONArray syncArray = syncKey.getJSONArray("List");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < syncArray.size(); i++) {
				sb.append(syncArray.getJSONObject(i).getString("Key") + "_"
						+ syncArray.getJSONObject(i).getString("Val") + "|");
			}
			// 1_661706053|2_661706420|3_661706415|1000_1494151022|
			String synckey = sb.toString();
			// 1_661706053|2_661706420|3_661706415|1000_1494151022
			storage.getLoginInfo().put(StorageLoginInfoEnum.synckey.getKey(),
					synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
			storage.setUserName(user.getString("UserName"));
			storage.setNickName(user.getString("NickName"));
			storage.setUserSelf(obj.getJSONObject("User"));

			String chatSet = obj.getString("ChatSet");
			String[] chatSetArray = chatSet.split(",");
			for (int i = 0; i < chatSetArray.length; i++) {
				if (chatSetArray[i].indexOf("@@") != -1) {
					// 更新GroupIdList
					storage.getGroupIdList().add(chatSetArray[i]); //
				}
			}
//			JSONArray contactListArray = obj.getJSONArray("ContactList");
//			for (int i = 0; i < contactListArray.size(); i++) {
//				JSONObject o = contactListArray.getJSONObject(i);
//				if (o.getString("UserName").indexOf("@@") != -1) {
//					storage.getGroupIdList().add(o.getString("UserName"));
//					// 更新GroupIdList
//					storage.getGroupList().add(o); // 更新GroupList
//					storage.getGroupNickNameList().add(o.getString("NickName"));
//				}
//			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public void wxStatusNotify() {
		// 组装请求URL和参数
		String url = String.format(URLEnum.STATUS_NOTIFY_URL.getUrl(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = storage.getParamMap();
		paramMap.put(StatusNotifyParaEnum.CODE.para(), StatusNotifyParaEnum.CODE.value());
		paramMap.put(StatusNotifyParaEnum.FROM_USERNAME.para(), storage.getUserName());
		paramMap.put(StatusNotifyParaEnum.TO_USERNAME.para(), storage.getUserName());
		paramMap.put(StatusNotifyParaEnum.CLIENT_MSG_ID.para(), System.currentTimeMillis());
		String paramStr = JSON.toJSONString(paramMap);

		try {
			HttpEntity entity = storage.getHttpClientWrapper().doPost(url, paramStr);
			EntityUtils.toString(entity, Consts.UTF_8);
		} catch (Exception e) {
			LOG.error("微信状态通知接口失败！", e);
		}
	}

	@Override
	public void startReceiving() {
		storage.setAlive(true);
		new Thread(new Runnable() {
			int retryCount = 0;

			@Override
			public void run() {
				while (storage.isAlive()) {
					try {
						Map<String, String> resultMap = syncCheck();
						LOG.info(JSONObject.toJSONString(resultMap));
						String retcode = resultMap.get("retcode");
						String selector = resultMap.get("selector");
						if (retcode.equals(RetCodeEnum.UNKOWN.getCode())) {
							LOG.info(RetCodeEnum.UNKOWN.getType());
							continue;
						} else if (retcode.equals(RetCodeEnum.LOGIN_OUT.getCode())) { // 退出
							LOG.info(RetCodeEnum.LOGIN_OUT.getType());
							break;
						} else if (retcode.equals(RetCodeEnum.LOGIN_OTHERWHERE.getCode())) { // 其它地方登陆
							LOG.info(RetCodeEnum.LOGIN_OTHERWHERE.getType());
							break;
						} else if (retcode.equals(RetCodeEnum.MOBILE_LOGIN_OUT.getCode())) { // 移动端退出
							LOG.info(RetCodeEnum.MOBILE_LOGIN_OUT.getType());
							break;
						} else if (retcode.equals(RetCodeEnum.NORMAL.getCode())) {
							storage.setLastNormalRetcodeTime(System.currentTimeMillis()); // 最后收到正常报文时间
							JSONObject msgObj = webWxSync();
							if (selector.equals("2")) {
								if (msgObj != null) {
									try {
										JSONArray msgList = new JSONArray();
										msgList = msgObj.getJSONArray("AddMsgList");
										msgList = produceMsg(msgList);
										for (int j = 0; j < msgList.size(); j++) {
											BaseMsg baseMsg = JSON.toJavaObject(msgList.getJSONObject(j),
													BaseMsg.class);
											storage.getMsgList().add(baseMsg);
										}
									} catch (Exception e) {
										LOG.error(e.getMessage(), e);
									}
								}
							} else if (selector.equals("7")) {
								webWxSync();
							} else if (selector.equals("4")) {
								continue;
							} else if (selector.equals("3")) {
								continue;
							} else if (selector.equals("6")) {
								if (msgObj != null) {
									try {
										JSONArray msgList = new JSONArray();
										msgList = msgObj.getJSONArray("AddMsgList");
										JSONArray modContactList = msgObj.getJSONArray("ModContactList"); // 存在删除或者新增的好友信息
										msgList = produceMsg(msgList);
										for (int j = 0; j < msgList.size(); j++) {
											JSONObject userInfo = modContactList.getJSONObject(j);
											// 存在主动加好友之后的同步联系人到本地
											storage.getContactList().add(userInfo);
										}
									} catch (Exception e) {
										LOG.error(e.getMessage(), e);
									}
								}

							}
						} else {
							JSONObject obj = webWxSync();
						}
					} catch (Exception e) {
						LOG.error(e.getMessage(), e);
						retryCount += 1;
						if (storage.getReceivingRetryCount() < retryCount) {
							storage.setAlive(false);
						} else {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException interruptedException) {
								LOG.error(interruptedException.getMessage(), interruptedException);
							}
						}
					}

				}
			}
		}).start();
	}

	@Override
	public void startCheckLoginStatus() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				while (storage.isAlive()) {
					long t1 = System.currentTimeMillis(); // 秒为单位
					if (t1 - storage.getLastNormalRetcodeTime() > 60 * 1000) { // 超过60秒，判为离线
						storage.setAlive(false);
						LOG.info("微信已离线");
					}
					try {
						Thread.sleep(10 * 1000); // 休眠10秒
					} catch (InterruptedException e) {
						LOG.error(e.getMessage(), e);
					}
				}

			}
		}).start();
	}

	@Override
	public void startHandleMsg(MsgHandler msgHandler) {
		LOG.info("+++++++++++++++++++开始消息处理+++++++++++++++++++++");
		new Thread(new Runnable() {
			@Override
			public void run() {
				handleMsg(msgHandler);
			}
		}).start();
	}

	@Override
	public void webWxGetContact() {
		String url = String.format(URLEnum.WEB_WX_GET_CONTACT.getUrl(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()));
		Map<String, Object> paramMap = storage.getParamMap();
		HttpEntity entity = storage.getHttpClientWrapper().doPost(url, JSON.toJSONString(paramMap));

		try {
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject fullFriendsJsonList = JSON.parseObject(result);
			// 查看seq是否为0，0表示好友列表已全部获取完毕，若大于0，则表示好友列表未获取完毕，当前的字节数（断点续传）
			long seq = 0;
			long currentTime = 0L;
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			if (fullFriendsJsonList.get("Seq") != null) {
				seq = fullFriendsJsonList.getLong("Seq");
				currentTime = new Date().getTime();
			}
			storage.setMemberCount(fullFriendsJsonList.getInteger(StorageLoginInfoEnum.MemberCount.getKey()));
			JSONArray member = fullFriendsJsonList.getJSONArray(StorageLoginInfoEnum.MemberList.getKey());
			// 循环获取seq直到为0，即获取全部好友列表 ==0：好友获取完毕 >0：好友未获取完毕，此时seq为已获取的字节数
			while (seq > 0) {
				// 设置seq传参
				params.add(new BasicNameValuePair("r", String.valueOf(currentTime)));
				params.add(new BasicNameValuePair("seq", String.valueOf(seq)));
				entity = storage.getHttpClientWrapper().doGet(url, params, false, null);

				params.remove(new BasicNameValuePair("r", String.valueOf(currentTime)));
				params.remove(new BasicNameValuePair("seq", String.valueOf(seq)));

				result = EntityUtils.toString(entity, Consts.UTF_8);
				fullFriendsJsonList = JSON.parseObject(result);

				if (fullFriendsJsonList.get("Seq") != null) {
					seq = fullFriendsJsonList.getLong("Seq");
					currentTime = new Date().getTime();
				}

				// 累加好友列表
				member.addAll(fullFriendsJsonList.getJSONArray(StorageLoginInfoEnum.MemberList.getKey()));
			}
			storage.setMemberCount(member.size());
			for (Iterator<?> iterator = member.iterator(); iterator.hasNext();) {
				JSONObject o = (JSONObject) iterator.next();
				if ((o.getInteger("VerifyFlag") & 8) != 0) { // 公众号/服务号
					storage.getPublicUsersList().add(o);
				} else if (Config.API_SPECIAL_USER.contains(o.getString("UserName"))) { // 特殊账号
					storage.getSpecialUsersList().add(o);
				} else if (o.getString("UserName").indexOf("@@") != -1) { // 群聊
					if (!storage.getGroupIdList().contains(o.getString("UserName"))) {
						storage.getGroupNickNameList().add(o.getString("NickName"));
						storage.getGroupIdList().add(o.getString("UserName"));
						storage.getGroupList().add(o);
					}
				} else if (o.getString("UserName").equals(storage.getUserSelf().getString("UserName"))) { // 自己
					storage.getContactList().remove(o);
				} else { // 普通联系人
					storage.getContactList().add(o);
				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	@Override
	public void WebWxBatchGetContact() {
		String url = String.format(URLEnum.WEB_WX_BATCH_GET_CONTACT.getUrl(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()), new Date().getTime(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = storage.getParamMap();
		paramMap.put("Count", storage.getGroupIdList().size());
		List<Map<String, String>> list = new ArrayList<Map<String, String>>();
		for (int i = 0; i < storage.getGroupIdList().size(); i++) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("UserName", storage.getGroupIdList().get(i));
			map.put("EncryChatRoomId", "");
			list.add(map);
		}
		paramMap.put("List", list);
		HttpEntity entity = storage.getHttpClientWrapper().doPost(url, JSON.toJSONString(paramMap));
		try {
			String text = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(text);
			JSONArray contactList = obj.getJSONArray("ContactList");
			for (int i = 0; i < contactList.size(); i++) { // 群好友
				if (contactList.getJSONObject(i).getString("UserName").indexOf("@@") > -1) { // 群
					storage.getGroupNickNameList().add(contactList.getJSONObject(i).getString("NickName")); // 更新群昵称列表
					storage.getGroupList().add(contactList.getJSONObject(i)); // 更新群信息（所有）列表
					storage.getGroupMemeberMap().put(contactList.getJSONObject(i).getString("UserName"),
							contactList.getJSONObject(i).getJSONArray("MemberList")); // 更新群成员Map
				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private Map<String, List<String>> getPossibleUrlMap() {
		Map<String, List<String>> possibleUrlMap = new HashMap<String, List<String>>();
		possibleUrlMap.put("wx.qq.com", new ArrayList<String>() {
			private static final long serialVersionUID = 1L;
			{
				add("file.wx.qq.com");
				add("webpush.wx.qq.com");
			}
		});
		possibleUrlMap.put("wx2.qq.com", new ArrayList<String>() {
			private static final long serialVersionUID = 1L;
			{
				add("file.wx2.qq.com");
				add("webpush.wx2.qq.com");
			}
		});
		possibleUrlMap.put("wx8.qq.com", new ArrayList<String>() {
			private static final long serialVersionUID = 1L;
			{
				add("file.wx8.qq.com");
				add("webpush.wx8.qq.com");
			}
		});
		possibleUrlMap.put("web2.wechat.com", new ArrayList<String>() {
			private static final long serialVersionUID = 1L;
			{
				add("file.web2.wechat.com");
				add("webpush.web2.wechat.com");
			}
		});
		possibleUrlMap.put("wechat.com", new ArrayList<String>() {
			private static final long serialVersionUID = 1L;
			{
				add("file.web.wechat.com");
				add("webpush.web.wechat.com");
			}
		});
		return possibleUrlMap;
	}

	private JSONObject webWxSync() {
		JSONObject result = null;
		String url = String.format(URLEnum.WEB_WX_SYNC_URL.getUrl(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()),
				storage.getLoginInfo().get(StorageLoginInfoEnum.wxsid.getKey()),
				storage.getLoginInfo().get(StorageLoginInfoEnum.skey.getKey()),
				storage.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = storage.getParamMap();
		paramMap.put(StorageLoginInfoEnum.SyncKey.getKey(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.SyncKey.getKey()));
		paramMap.put("rr", -new Date().getTime() / 1000);
		String paramStr = JSON.toJSONString(paramMap);
		try {
			HttpEntity entity = storage.getHttpClientWrapper().doPost(url, paramStr);
			String text = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(text);
			if (obj.getJSONObject("BaseResponse").getInteger("Ret") != 0) {
				result = null;
			} else {
				result = obj;
				storage.getLoginInfo().put(StorageLoginInfoEnum.SyncKey.getKey(), obj.getJSONObject("SyncCheckKey"));
				JSONArray syncArray = obj.getJSONObject(StorageLoginInfoEnum.SyncKey.getKey()).getJSONArray("List");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < syncArray.size(); i++) {
					sb.append(syncArray.getJSONObject(i).getString("Key") + "_"
							+ syncArray.getJSONObject(i).getString("Val") + "|");
				}
				String synckey = sb.toString();
				storage.getLoginInfo().put(StorageLoginInfoEnum.synckey.getKey(),
						synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return result;
	}

	/**
	 * 检查是否有新消息 check whether there's a message
	 */
	private Map<String, String> syncCheck() {
		Map<String, String> resultMap = new HashMap<String, String>();
		// 组装请求URL和参数
		String url = storage.getLoginInfo().get(StorageLoginInfoEnum.syncUrl.getKey())
				+ URLEnum.SYNC_CHECK_URL.getUrl();
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		for (BaseParaEnum baseRequest : BaseParaEnum.values()) {
			params.add(new BasicNameValuePair(baseRequest.para().toLowerCase(),
					storage.getLoginInfo().get(baseRequest.value()).toString()));
		}
		params.add(new BasicNameValuePair("r", String.valueOf(new Date().getTime())));
		params.add(new BasicNameValuePair("synckey", (String) storage.getLoginInfo().get("synckey")));
		params.add(new BasicNameValuePair("_", String.valueOf(new Date().getTime())));
		try {
			Thread.sleep(7);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		try {
			HttpEntity entity = storage.getHttpClientWrapper().doGet(url, params, true, null);
			if (entity == null) {
				resultMap.put("retcode", "9999");
				resultMap.put("selector", "9999");
				return resultMap;
			}
			String text = EntityUtils.toString(entity);
			String regEx = "window.synccheck=\\{retcode:\"(\\d+)\",selector:\"(\\d+)\"\\}";
			Matcher matcher = CommonTools.getMatcher(regEx, text);
			if (!matcher.find() || matcher.group(1).equals("2")) {
				LOG.info(String.format("Unexpected sync check result: %s", text));
			} else {
				resultMap.put("retcode", matcher.group(1));
				resultMap.put("selector", matcher.group(2));
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return resultMap;
	}

	@Override
	public Object getDownloadFn(BaseMsg msg, String type, String path) {
		Map<String, String> headerMap = new HashMap<String, String>();
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		String url = "";
		if (type.equals(MsgTypeEnum.PIC.getType())) {
			url = String.format(URLEnum.WEB_WX_GET_MSG_IMG.getUrl(), (String) storage.getLoginInfo().get("url"));
		} else if (type.equals(MsgTypeEnum.VOICE.getType())) {
			url = String.format(URLEnum.WEB_WX_GET_VOICE.getUrl(), (String) storage.getLoginInfo().get("url"));
		} else if (type.equals(MsgTypeEnum.VIEDO.getType())) {
			headerMap.put("Range", "bytes=0-");
			url = String.format(URLEnum.WEB_WX_GET_VIEDO.getUrl(), (String) storage.getLoginInfo().get("url"));
		} else if (type.equals(MsgTypeEnum.MEDIA.getType())) {
			headerMap.put("Range", "bytes=0-");
			url = String.format(URLEnum.WEB_WX_GET_MEDIA.getUrl(), (String) storage.getLoginInfo().get("fileUrl"));
			params.add(new BasicNameValuePair("sender", msg.getFromUserName()));
			params.add(new BasicNameValuePair("mediaid", msg.getMediaId()));
			params.add(new BasicNameValuePair("filename", msg.getFileName()));
		}
		params.add(new BasicNameValuePair("msgid", msg.getNewMsgId()));
		params.add(new BasicNameValuePair("skey", (String) storage.getLoginInfo().get("skey")));
		HttpEntity entity = storage.getHttpClientWrapper().doGet(url, params, true, headerMap);
		try {
			OutputStream out = new FileOutputStream(path);
			byte[] bytes = EntityUtils.toByteArray(entity);
			out.write(bytes);
			out.flush();
			out.close();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			return false;
		}
		return null;
	}

	@Override
	public JSONArray produceMsg(JSONArray msgList) {
		JSONArray result = new JSONArray();
		for (int i = 0; i < msgList.size(); i++) {
			JSONObject msg = new JSONObject();
			JSONObject m = msgList.getJSONObject(i);
			m.put("groupMsg", false);// 是否是群消息
			if (m.getString("FromUserName").contains("@@") || m.getString("ToUserName").contains("@@")) { // 群聊消息
				if (m.getString("FromUserName").contains("@@")
						&& !storage.getGroupIdList().contains(m.getString("FromUserName"))) {
					storage.getGroupIdList().add((m.getString("FromUserName")));
				} else if (m.getString("ToUserName").contains("@@")
						&& !storage.getGroupIdList().contains(m.getString("ToUserName"))) {
					storage.getGroupIdList().add((m.getString("ToUserName")));
				}
				// 群消息与普通消息不同的是在其消息体（Content）中会包含发送者id及":<br/>"消息，这里需要处理一下，去掉多余信息，只保留消息内容
				if (m.getString("Content").contains("<br/>")) {
					String content = m.getString("Content").substring(m.getString("Content").indexOf("<br/>") + 5);
					m.put("Content", content);
					m.put("groupMsg", true);
				}
			} else {
				CommonTools.msgFormatter(m, "Content");
			}
			if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_TEXT.getCode())) { // words
																						// 文本消息
				if (m.getString("Url").length() != 0) {
					String regEx = "(.+?\\(.+?\\))";
					Matcher matcher = CommonTools.getMatcher(regEx, m.getString("Content"));
					String data = "Map";
					if (matcher.find()) {
						data = matcher.group(1);
					}
					msg.put("Type", "Map");
					msg.put("Text", data);
				} else {
					msg.put("Type", MsgTypeEnum.TEXT.getType());
					msg.put("Text", m.getString("Content"));
				}
				m.put("Type", msg.getString("Type"));
				m.put("Text", msg.getString("Text"));
			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_IMAGE.getCode())
					|| m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_EMOTICON.getCode())) { // 图片消息
				m.put("Type", MsgTypeEnum.PIC.getType());
			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_VOICE.getCode())) { // 语音消息
				m.put("Type", MsgTypeEnum.VOICE.getType());
			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_VERIFYMSG.getCode())) {// friends
				// 好友确认消息
				// addFriend(core, userName, 3, ticket); // 确认添加好友
				m.put("Type", MsgTypeEnum.VERIFYMSG.getType());

			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_SHARECARD.getCode())) { // 共享名片
				m.put("Type", MsgTypeEnum.NAMECARD.getType());

			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_VIDEO.getCode())
					|| m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_MICROVIDEO.getCode())) {// viedo
				m.put("Type", MsgTypeEnum.VIEDO.getType());
			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_MEDIA.getCode())) { // 多媒体消息
				m.put("Type", MsgTypeEnum.MEDIA.getType());
			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_STATUSNOTIFY.getCode())) {// phone
				// init
				// 微信初始化消息

			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_SYS.getCode())) {// 系统消息
				m.put("Type", MsgTypeEnum.SYS.getType());
			} else if (m.getInteger("MsgType").equals(MsgCodeEnum.MSGTYPE_RECALLED.getCode())) { // 撤回消息

			} else {
				LOG.info("Useless msg");
			}
			LOG.info("收到消息一条，来自: " + m.getString("FromUserName"));
			result.add(m);
		}
		return result;
	}

	@Override
	public void handleMsg(MsgHandler msgHandler) {
		while (true) {
			if (storage.getMsgList().size() > 0 && storage.getMsgList().get(0).getContent() != null) {
				if (storage.getMsgList().get(0).getContent().length() > 0) {
					BaseMsg msg = storage.getMsgList().get(0);
					if (msg.getType() != null) {
						try {
							if (msg.getType().equals(MsgTypeEnum.TEXT.getType())) {
								String result = msgHandler.textMsgHandle(msg);
								sendMsgById(result, storage.getMsgList().get(0).getFromUserName());
							} else if (msg.getType().equals(MsgTypeEnum.PIC.getType())) {
								String result = msgHandler.picMsgHandle(msg);
								sendMsgById(result, storage.getMsgList().get(0).getFromUserName());
							} else if (msg.getType().equals(MsgTypeEnum.VOICE.getType())) {
								String result = msgHandler.voiceMsgHandle(msg);
								sendMsgById(result, storage.getMsgList().get(0).getFromUserName());
							} else if (msg.getType().equals(MsgTypeEnum.VIEDO.getType())) {
								String result = msgHandler.viedoMsgHandle(msg);
								sendMsgById(result, storage.getMsgList().get(0).getFromUserName());
							} else if (msg.getType().equals(MsgTypeEnum.NAMECARD.getType())) {
								String result = msgHandler.nameCardMsgHandle(msg);
								sendMsgById(result, storage.getMsgList().get(0).getFromUserName());
							} else if (msg.getType().equals(MsgTypeEnum.SYS.getType())) { // 系统消息
								msgHandler.sysMsgHandle(msg);
							} else if (msg.getType().equals(MsgTypeEnum.VERIFYMSG.getType())) { // 确认添加好友消息
								String result = msgHandler.verifyAddFriendMsgHandle(msg);
								sendMsgById(result, storage.getMsgList().get(0).getRecommendInfo().getUserName());
							} else if (msg.getType().equals(MsgTypeEnum.MEDIA.getType())) { // 多媒体消息
								String result = msgHandler.mediaMsgHandle(msg);
								sendMsgById(result, storage.getMsgList().get(0).getFromUserName());
							}
						} catch (Exception e) {
							LOG.error(e.getMessage(), e);
						}
					}
				}
				storage.getMsgList().remove(0);
			}
			try {
				TimeUnit.MILLISECONDS.sleep(1000);
			} catch (InterruptedException e) {
				LOG.error(e.getMessage(), e);
			}
		}
	}

	@Override
	public void sendMsg(String text, String toUserName) {
		if (text == null) {
			return;
		}
		LOG.info(String.format("发送消息 %s: %s", toUserName, text));
		webWxSendMsg(1, text, toUserName);
	}

	@Override
	public void sendMsgById(String text, String id) {
		if (text == null) {
			return;
		}
		sendMsg(text, id);
	}

	@Override
	public boolean sendMsgByNickName(String text, String nickName) {
		if (nickName != null) {
			String toUserName = getUserNameByNickName(nickName);
			if (toUserName != null) {
				webWxSendMsg(1, text, toUserName);
				return true;
			}
		}
		return false;
	}

	@Override
	public void webWxSendMsg(int msgType, String content, String toUserName) {
		String url = String.format(URLEnum.WEB_WX_SEND_MSG.getUrl(), storage.getLoginInfo().get("url"));
		Map<String, Object> msgMap = new HashMap<String, Object>();
		msgMap.put("Type", msgType);
		msgMap.put("Content", content);
		msgMap.put("FromUserName", storage.getUserName());
		msgMap.put("ToUserName", toUserName == null ? storage.getUserName() : toUserName);
		msgMap.put("LocalID", new Date().getTime() * 10);
		msgMap.put("ClientMsgId", new Date().getTime() * 10);
		Map<String, Object> paramMap = storage.getParamMap();
		paramMap.put("Msg", msgMap);
		paramMap.put("Scene", 0);
		try {
			String paramStr = JSON.toJSONString(paramMap);
			HttpEntity entity = storage.getHttpClientWrapper().doPost(url, paramStr);
			EntityUtils.toString(entity, Consts.UTF_8);
		} catch (Exception e) {
			LOG.error("webWxSendMsg", e);
		}
	}

	@Override
	public JSONObject webWxUploadMedia(String filePath) {
		File f = new File(filePath);
		if (!f.exists() && f.isFile()) {
			LOG.info("file is not exist");
			return null;
		}
		String url = String.format(URLEnum.WEB_WX_UPLOAD_MEDIA.getUrl(), storage.getLoginInfo().get("fileUrl"));
		String mimeType = new MimetypesFileTypeMap().getContentType(f);
		String mediaType = "";
		if (mimeType == null) {
			mimeType = "text/plain";
		} else {
			mediaType = mimeType.split("/")[0].equals("image") ? "pic" : "doc";
		}
		String lastModifieDate = new SimpleDateFormat("yyyy MM dd HH:mm:ss").format(new Date());
		long fileSize = f.length();
		String passTicket = (String) storage.getLoginInfo().get("pass_ticket");
		String clientMediaId = String.valueOf(new Date().getTime())
				+ String.valueOf(new Random().nextLong()).substring(0, 4);
		String webwxDataTicket = storage.getHttpClientWrapper().getCookie("webwx_data_ticket");
		if (webwxDataTicket == null) {
			LOG.error("get cookie webwx_data_ticket error");
			return null;
		}

		Map<String, Object> paramMap = storage.getParamMap();

		paramMap.put("ClientMediaId", clientMediaId);
		paramMap.put("TotalLen", fileSize);
		paramMap.put("StartPos", 0);
		paramMap.put("DataLen", fileSize);
		paramMap.put("MediaType", 4);

		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

		builder.addTextBody("id", "WU_FILE_0", ContentType.TEXT_PLAIN);
		builder.addTextBody("name", filePath, ContentType.TEXT_PLAIN);
		builder.addTextBody("type", mimeType, ContentType.TEXT_PLAIN);
		builder.addTextBody("lastModifieDate", lastModifieDate, ContentType.TEXT_PLAIN);
		builder.addTextBody("size", String.valueOf(fileSize), ContentType.TEXT_PLAIN);
		builder.addTextBody("mediatype", mediaType, ContentType.TEXT_PLAIN);
		builder.addTextBody("uploadmediarequest", JSON.toJSONString(paramMap), ContentType.TEXT_PLAIN);
		builder.addTextBody("webwx_data_ticket", webwxDataTicket, ContentType.TEXT_PLAIN);
		builder.addTextBody("pass_ticket", passTicket, ContentType.TEXT_PLAIN);
		builder.addBinaryBody("filename", f, ContentType.create(mimeType), filePath);
		HttpEntity reqEntity = builder.build();
		HttpEntity entity = storage.getHttpClientWrapper().doPostFile(url, reqEntity);
		if (entity != null) {
			try {
				String result = EntityUtils.toString(entity, Consts.UTF_8);
				return JSON.parseObject(result);
			} catch (Exception e) {
				LOG.error("webWxUploadMedia 错误： ", e);
			}

		}
		return null;
	}

	@Override
	public boolean sendPicMsgByNickName(String nickName, String filePath) {
		String toUserName = getUserNameByNickName(nickName);
		if (toUserName != null) {
			return sendPicMsgByUserId(toUserName, filePath);
		}
		return false;
	}

	@Override
	public boolean sendPicMsgByUserId(String userId, String filePath) {
		JSONObject responseObj = webWxUploadMedia(filePath);
		if (responseObj != null) {
			String mediaId = responseObj.getString("MediaId");
			if (mediaId != null) {
				return webWxSendMsgImg(userId, mediaId);
			}
		}
		return false;
	}

	/**
	 * 发送图片消息，内部调用
	 */
	private boolean webWxSendMsgImg(String userId, String mediaId) {
		String url = String.format("%s/webwxsendmsgimg?fun=async&f=json&pass_ticket=%s",
				storage.getLoginInfo().get("url"), storage.getLoginInfo().get("pass_ticket"));
		Map<String, Object> msgMap = new HashMap<String, Object>();
		msgMap.put("Type", 3);
		msgMap.put("MediaId", mediaId);
		msgMap.put("FromUserName", storage.getUserSelf().getString("UserName"));
		msgMap.put("ToUserName", userId);
		String clientMsgId = String.valueOf(new Date().getTime())
				+ String.valueOf(new Random().nextLong()).substring(1, 5);
		msgMap.put("LocalID", clientMsgId);
		msgMap.put("ClientMsgId", clientMsgId);
		Map<String, Object> paramMap = storage.getParamMap();
		paramMap.put("BaseRequest", storage.getParamMap().get("BaseRequest"));
		paramMap.put("Msg", msgMap);
		String paramStr = JSON.toJSONString(paramMap);
		HttpEntity entity = storage.getHttpClientWrapper().doPost(url, paramStr);
		if (entity != null) {
			try {
				String result = EntityUtils.toString(entity, Consts.UTF_8);
				return JSON.parseObject(result).getJSONObject("BaseResponse").getInteger("Ret") == 0;
			} catch (Exception e) {
				LOG.error("webWxSendMsgImg 错误： ", e);
			}
		}
		return false;
	}

	@Override
	public boolean sendFileMsgByUserId(String userId, String filePath) {
		String title = new File(filePath).getName();
		Map<String, String> data = new HashMap<String, String>();
		data.put("appid", Config.API_WXAPPID);
		data.put("title", title);
		data.put("totallen", "");
		data.put("attachid", "");
		data.put("type", "6"); // APPMSGTYPE_ATTACH
		data.put("fileext", title.split("\\.")[1]); // 文件后缀
		JSONObject responseObj = webWxUploadMedia(filePath);
		if (responseObj != null) {
			data.put("totallen", responseObj.getString("StartPos"));
			data.put("attachid", responseObj.getString("MediaId"));
		} else {
			LOG.error("sednFileMsgByUserId 错误: ", data);
		}
		return webWxSendAppMsg(userId, data);
	}

	@Override
	public boolean sendFileMsgByNickName(String nickName, String filePath) {
		String toUserName = getUserNameByNickName(nickName);
		if (toUserName != null) {
			return sendFileMsgByUserId(toUserName, filePath);
		}
		return false;
	}

	/**
	 * 内部调用
	 */
	private boolean webWxSendAppMsg(String userId, Map<String, String> data) {
		String url = String.format("%s/webwxsendappmsg?fun=async&f=json&pass_ticket=%s",
				storage.getLoginInfo().get("url"), storage.getLoginInfo().get("pass_ticket"));
		String clientMsgId = String.valueOf(new Date().getTime())
				+ String.valueOf(new Random().nextLong()).substring(1, 5);
		String content = "<appmsg appid='wxeb7ec651dd0aefa9' sdkver=''><title>" + data.get("title")
				+ "</title><des></des><action></action><type>6</type><content></content><url></url><lowurl></lowurl>"
				+ "<appattach><totallen>" + data.get("totallen") + "</totallen><attachid>" + data.get("attachid")
				+ "</attachid><fileext>" + data.get("fileext") + "</fileext></appattach><extinfo></extinfo></appmsg>";
		Map<String, Object> msgMap = new HashMap<String, Object>();
		msgMap.put("Type", data.get("type"));
		msgMap.put("Content", content);
		msgMap.put("FromUserName", storage.getUserSelf().getString("UserName"));
		msgMap.put("ToUserName", userId);
		msgMap.put("LocalID", clientMsgId);
		msgMap.put("ClientMsgId", clientMsgId);

		Map<String, Object> paramMap = storage.getParamMap();
		paramMap.put("Msg", msgMap);
		paramMap.put("Scene", 0);
		String paramStr = JSON.toJSONString(paramMap);
		HttpEntity entity = storage.getHttpClientWrapper().doPost(url, paramStr);
		if (entity != null) {
			try {
				String result = EntityUtils.toString(entity, Consts.UTF_8);
				return JSON.parseObject(result).getJSONObject("BaseResponse").getInteger("Ret") == 0;
			} catch (Exception e) {
				LOG.error("错误: ", e);
			}
		}
		return false;
	}

	@Override
	public void addFriend(BaseMsg msg, boolean accept) {
		if (!accept) { // 不添加
			return;
		}
		int status = VerifyFriendEnum.ACCEPT.getCode(); // 接受好友请求
		RecommendInfo recommendInfo = msg.getRecommendInfo();
		String userName = recommendInfo.getUserName();
		String ticket = recommendInfo.getTicket();

//		更新好友列表
//		TODO 此处需要更新好友列表
//		storage.getContactList().add(msg.getJSONObject("RecommendInfo"));

		String url = String.format(URLEnum.WEB_WX_VERIFYUSER.getUrl(), storage.getLoginInfo().get("url"),
				String.valueOf(System.currentTimeMillis() / 3158L), storage.getLoginInfo().get("pass_ticket"));

		List<Map<String, Object>> verifyUserList = new ArrayList<Map<String, Object>>();
		Map<String, Object> verifyUser = new HashMap<String, Object>();
		verifyUser.put("Value", userName);
		verifyUser.put("VerifyUserTicket", ticket);
		verifyUserList.add(verifyUser);

		List<Integer> sceneList = new ArrayList<Integer>();
		sceneList.add(33);

		JSONObject body = new JSONObject();
		body.put("BaseRequest", storage.getParamMap().get("BaseRequest"));
		body.put("Opcode", status);
		body.put("VerifyUserListSize", 1);
		body.put("VerifyUserList", verifyUserList);
		body.put("VerifyContent", "");
		body.put("SceneListCount", 1);
		body.put("SceneList", sceneList);
		body.put("skey", storage.getLoginInfo().get(StorageLoginInfoEnum.skey.getKey()));

		String result = null;
		try {
			String paramStr = JSON.toJSONString(body);
			HttpEntity entity = storage.getHttpClientWrapper().doPost(url, paramStr);
			result = EntityUtils.toString(entity, Consts.UTF_8);
		} catch (Exception e) {
			LOG.error("webWxSendMsg", e);
		}

		if (StringUtils.isBlank(result)) {
			LOG.error("被动添加好友失败");
		}

		LOG.debug(result);
	}

	@Override
	public void sendMsgByUserName(String msg, String toUserName) {
		sendMsgById(msg, toUserName);
	}

	@Override
	public String getUserNameByNickName(String nickName) {
		for (JSONObject o : storage.getContactList()) {
			if (o.getString("NickName").equals(nickName)) {
				return o.getString("UserName");
			}
		}
		return null;
	}

	@Override
	public List<String> getContactNickNameList() {
		List<String> contactNickNameList = new ArrayList<String>();
		for (JSONObject o : storage.getContactList()) {
			contactNickNameList.add(o.getString("NickName"));
		}
		return contactNickNameList;
	}

	@Override
	public JSONArray getMemberListByGroupId(String groupId) {
		return storage.getGroupMemeberMap().get(groupId);
	}

	@Override
	public void logout() {
		webWxLogout();
	}

	private boolean webWxLogout() {
		String url = String.format(URLEnum.WEB_WX_LOGOUT.getUrl(),
				storage.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()));
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		params.add(new BasicNameValuePair("redirect", "1"));
		params.add(new BasicNameValuePair("type", "1"));
		params.add(new BasicNameValuePair("skey",
				(String) storage.getLoginInfo().get(StorageLoginInfoEnum.skey.getKey())));
		try {
			HttpEntity entity = storage.getHttpClientWrapper().doGet(url, params, false, null);
			String text = EntityUtils.toString(entity, Consts.UTF_8); // 无消息
			return true;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return false;
	}

	@Override
	public void setUserInfo() {
		for (JSONObject o : storage.getContactList()) {
			storage.getUserInfoMap().put(o.getString("NickName"), o);
			storage.getUserInfoMap().put(o.getString("UserName"), o);
		}
	}

	@Override
	public void remarkNameByNickName(String nickName, String remName) {
		String url = String.format(URLEnum.WEB_WX_REMARKNAME.getUrl(), storage.getLoginInfo().get("url"),
				storage.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> msgMap = new HashMap<String, Object>();
		Map<String, Object> msgMap_BaseRequest = new HashMap<String, Object>();
		msgMap.put("CmdId", 2);
		msgMap.put("RemarkName", remName);
		msgMap.put("UserName", storage.getUserInfoMap().get(nickName).get("UserName"));
		msgMap_BaseRequest.put("Uin", storage.getLoginInfo().get(StorageLoginInfoEnum.wxuin.getKey()));
		msgMap_BaseRequest.put("Sid", storage.getLoginInfo().get(StorageLoginInfoEnum.wxsid.getKey()));
		msgMap_BaseRequest.put("Skey", storage.getLoginInfo().get(StorageLoginInfoEnum.skey.getKey()));
		msgMap_BaseRequest.put("DeviceID", storage.getLoginInfo().get(StorageLoginInfoEnum.deviceid.getKey()));
		msgMap.put("BaseRequest", msgMap_BaseRequest);
		try {
			String paramStr = JSON.toJSONString(msgMap);
			HttpEntity entity = storage.getHttpClientWrapper().doPost(url, paramStr);
			// String result = EntityUtils.toString(entity, Consts.UTF_8);
			LOG.info("修改备注" + remName);
		} catch (Exception e) {
			LOG.error("remarkNameByUserName", e);
		}
	}

	@Override
	public boolean getWechatStatus() {
		return storage.isAlive();
	}
}
