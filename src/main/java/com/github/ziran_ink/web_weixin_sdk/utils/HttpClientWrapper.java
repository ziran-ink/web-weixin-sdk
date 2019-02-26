package com.github.ziran_ink.web_weixin_sdk.utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientWrapper {
	private Logger logger = LoggerFactory.getLogger(HttpClientWrapper.class);
	private CookieStore cookieStore = new BasicCookieStore();
	private CloseableHttpClient httpClient = HttpClients.custom().setDefaultCookieStore(cookieStore).build();

	public String getCookie(String name) {
		List<Cookie> cookies = cookieStore.getCookies();
		for (Cookie cookie : cookies) {
			if (cookie.getName().equalsIgnoreCase(name)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public CloseableHttpClient getHttpClient() {
		return httpClient;
	}

	public HttpEntity doGet(String url, List<BasicNameValuePair> params, boolean redirect,
			Map<String, String> headerMap) {
		HttpGet httpGet = new HttpGet();
		try {
			if (params != null) {
				String paramStr = EntityUtils.toString(new UrlEncodedFormEntity(params, Consts.UTF_8));
				httpGet = new HttpGet(url + "?" + paramStr);
			} else {
				httpGet = new HttpGet(url);
			}
			if (!redirect) {
				httpGet.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build()); // 禁止重定向
			}
			httpGet.setHeader("User-Agent", Config.USER_AGENT);
			if (headerMap != null) {
				Set<Entry<String, String>> entries = headerMap.entrySet();
				for (Entry<String, String> entry : entries) {
					httpGet.setHeader(entry.getKey(), entry.getValue());
				}
			}
			return httpClient.execute(httpGet).getEntity();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	public HttpEntity doPost(String url, String paramsStr) {
		HttpPost httpPost = new HttpPost();
		try {
			StringEntity params = new StringEntity(paramsStr, Consts.UTF_8);
			httpPost = new HttpPost(url);
			httpPost.setEntity(params);
			httpPost.setHeader("Content-type", "application/json; charset=utf-8");
			httpPost.setHeader("User-Agent", Config.USER_AGENT);
			return httpClient.execute(httpPost).getEntity();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	public HttpEntity doPostFile(String url, HttpEntity reqEntity) {
		HttpPost httpPost = new HttpPost(url);
		try {
			httpPost.setHeader("User-Agent", Config.USER_AGENT);
			httpPost.setEntity(reqEntity);
			return httpClient.execute(httpPost).getEntity();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}
}