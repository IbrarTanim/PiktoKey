package com.limbika.piktoplus.key;

import java.io.File;
import java.io.InputStream;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.util.Log;

import com.limbika.shared.ByteUtils;
import com.limbika.shared.Streamer;
import com.limbika.shared.Util;

public class Connector {
	
	private static final String TAG 			= "Connector";
	private static final String METHOD_APK 		= "http://apiktoplus.limbika.com/apk/";
	private static final String METHOD_TOKEN 	= "http://apiktoplus.limbika.com/token/";
	
	private static Connector	sInstance = null;
	private DefaultHttpClient 	mHttpClient;

	private Connector() {
		mHttpClient = new DefaultHttpClient();
	}

	public static Connector get() {
		if ( sInstance == null ) {
			sInstance = new Connector();
		}
		return sInstance;
	}
	
	public int download(String token, String key, String id, File file) {
		try {
			String md5 = Util.md5(id);
			String chunk = chunk(token, key, md5.substring(0, 16));
			HttpGet get = new HttpGet();
			get.setURI(new URI(METHOD_APK + token + File.separator + chunk));
			
			HttpResponse response = mHttpClient.execute(get);
			HttpEntity entity = response.getEntity();
			
			if ( response.getStatusLine().getStatusCode() != 200 ) {
				Log.e(TAG, "Server response " + response.getStatusLine().getStatusCode());
				return -4;
			}
			
			InputStream is = entity.getContent();
			Util.writeFile(is, file.getAbsolutePath());
			entity.consumeContent();
		} 
		catch (Exception e) {
			Log.e(TAG, "Server error!", e);
			return -4;
		}
		return 0;
	}
	
	public String gettoken(String id) {
		HttpGet get = new HttpGet();
		try {
			get.setURI(new URI(METHOD_TOKEN + id));
			HttpResponse response = mHttpClient.execute(get);
			HttpEntity entity = response.getEntity();
			
			InputStream is = entity.getContent();
			String out = Streamer.toString(is);
			entity.consumeContent();
			return out;
		} 
		catch (Exception e) {
			Log.e(TAG, "No token!", e);
		}
		return null;
	}
	
	private static String chunk(String a, String b, String c) {
		byte[] ba = ByteUtils.hexStringToByteArray(a);
		byte[] bb = ByteUtils.hexStringToByteArray(b);
		byte[] bc = ByteUtils.hexStringToByteArray(c);
		
		byte[] out = ByteUtils.xor ( ByteUtils.xor (ba, bb), bc);
		
		return ByteUtils.bytesArrayToHexString(out);
	}

}
