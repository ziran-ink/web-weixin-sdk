package com.github.ziran_ink.web_weixin_sdk.core;

import java.util.List;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.ziran_ink.web_weixin_sdk.beans.BaseMsg;

public interface Weixin {

	/**
	 * 存储
	 */
	Storage getStorage();

	/**
	 * 登陆
	 */
	boolean login();

	/**
	 * 获取UUID
	 */
	String getUuid();

	/**
	 * 获取二维码图片
	 */
	boolean getQR(String qrPath);

	/**
	 * web初始化
	 */
	boolean webWxInit();

	/**
	 * 微信状态通知
	 */
	void wxStatusNotify();

	/**
	 * 接收消息
	 */
	void startReceiving();

	/**
	 * 检查微信在线状态 <br/>
	 * 如何来感知微信状态？
	 * 微信会有心跳包，LoginServiceImpl.syncCheck()正常在线情况下返回的消息中retcode报文应该为"0"，心跳间隔一般在25秒，
	 * 那么可以通过最后收到正常报文的时间来作为判断是否在线的依据。若报文间隔大于60秒，则认为已掉线。
	 */
	void startCheckLoginStatus();

	void startHandleMsg(MsgHandler msgHandler);

	/**
	 * 获取微信联系人
	 */
	void webWxGetContact();

	/**
	 * 批量获取联系人信息
	 */
	void WebWxBatchGetContact();

	/**
	 * 处理下载任务
	 */
	Object getDownloadFn(BaseMsg msg, String type, String path);

	/**
	 * 接收消息，放入队列
	 */
	JSONArray produceMsg(JSONArray msgList);

	/**
	 * 消息处理
	 */
	void handleMsg(MsgHandler msgHandler);

	/**
	 * 根据UserName发送文本消息
	 */
	void sendMsg(String text, String toUserName);

	/**
	 * 根据ID发送文本消息
	 */
	void sendMsgById(String text, String id);

	/**
	 * 根据NickName发送文本消息
	 */
	boolean sendMsgByNickName(String text, String nickName);

	/**
	 * 消息发送
	 */
	void webWxSendMsg(int msgType, String content, String toUserName);

	/**
	 * 上传多媒体文件到 微信服务器，目前应该支持3种类型: 1. pic 直接显示，包含图片，表情 2.video 3.doc 显示为文件，包含PDF等
	 */
	JSONObject webWxUploadMedia(String filePath);

	/**
	 * 根据NickName发送图片消息
	 */
	boolean sendPicMsgByNickName(String nickName, String filePath);

	/**
	 * 根据用户id发送图片消息
	 */
	boolean sendPicMsgByUserId(String userId, String filePath);

	/**
	 * 根据用户id发送文件
	 */
	boolean sendFileMsgByUserId(String userId, String filePath);

	/**
	 * 根据用户昵称发送文件消息
	 */
	boolean sendFileMsgByNickName(String nickName, String filePath);

	/**
	 * 被动添加好友
	 */
	void addFriend(BaseMsg msg, boolean accept);

	/**
	 * 根据用户名发送文本消息
	 */
	void sendMsgByUserName(String msg, String toUserName);

	/**
	 * <p>
	 * 通过RealName获取本次UserName
	 * </p>
	 * <p>
	 * 如NickName为"yaphone"，则获取UserName=
	 * "@1212d3356aea8285e5bbe7b91229936bc183780a8ffa469f2d638bf0d2e4fc63"，
	 * 可通过UserName发送消息
	 * </p>
	 */
	String getUserNameByNickName(String nickName);

	/**
	 * 返回好友昵称列表
	 */
	List<String> getContactNickNameList();

	/**
	 * 根据groupIdList返回群成员列表
	 */
	JSONArray getMemberListByGroupId(String groupId);

	/**
	 * 退出微信
	 */
	void logout();

	void setUserInfo();

	/**
	 * 根据用户昵称设置备注名称
	 */
	void remarkNameByNickName(String nickName, String remName);
}
