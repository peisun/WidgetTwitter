package jp.peisun.shuzobotwidget;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigData {
	public boolean wifionly;
	public long accessUpdateTime;
	public long widgetUpdateTime;
	public String screenName;
	public String accessToken;
	public String accessTokenSecret;

	
	// 設定ファイルについて
	public final static String PF_WIFIONLY = "wifionly";
	public final static String PF_SCREEN_NAME = "screenname";
	public final static String PF_ACCESS_UPDATE = "access";
	public final static String PF_WIDGET_UPDATE = "widget";
	public final static String PF_ACCESSTOKEN = "accesstoken";
	public final static String PF_ACCESSTOKENSECRET = "accesstokensecret";
	public final static String PF_ORDER = "order";
	public static interface Order {
		int ORDER_WIFI = 1;
		int ORDER_ACCESS_UPDATE = 2;
		int ORDER_WIDGET_UPDATE = 3;
		int ORDER_TOKEN = 4;
	};
	
	private final static String CONFIG_FILE = "config";
	
	
	private static Context mContext;
//	public ConfigData(Context context){
//		mContext = context;
//		wifionly = Boolean.parseBoolean(context.getString(R.string.defaultValuewifionly));
//		accessUpdateTime = Long.parseLong(context.getString(R.string.defaultValueAccessUpdate));
//		widgetUpdateTime = Long.parseLong(context.getString(R.string.defaultValueWidgetUpdate));
//		screenName = "";
//		accessToken = "";
//		accessTokenSecret = "";
//	}
	public void getSharedPreferences(Context context){
		mContext = context;
//		SharedPreferences mSharedPreferencs = PreferenceManager.getDefaultSharedPreferences(mContext);
		SharedPreferences mSharedPreferencs = mContext.getSharedPreferences(CONFIG_FILE,Context.MODE_PRIVATE);
		boolean booleanValue = Boolean.parseBoolean(mContext.getString(R.string.defaultValuewifionly));
		wifionly = mSharedPreferencs.getBoolean(PF_WIFIONLY,booleanValue);
		String value = mContext.getString(R.string.defaultValueAccessUpdate);
		accessUpdateTime = mSharedPreferencs.getLong(PF_ACCESS_UPDATE, Long.parseLong(value));
		value = mContext.getString(R.string.defaultValueWidgetUpdate);
		widgetUpdateTime = mSharedPreferencs.getLong(PF_ACCESS_UPDATE, Long.parseLong(value));
		screenName = mSharedPreferencs.getString(PF_SCREEN_NAME, "");
		accessToken = mSharedPreferencs.getString(PF_ACCESSTOKEN, "");
		accessTokenSecret = mSharedPreferencs.getString(PF_ACCESSTOKENSECRET, "");

	}
	public boolean isAccessToken(){
		if(accessToken.equals("") == true || accessTokenSecret.equals("") == true){
			return false;
		}
		else {
			return true;
		}
	}
	public String getAccessToken(){
		return accessToken;
	}
	public String getAccessTokenSecret(){
		return accessTokenSecret;
	}
	public void setAccessToken(String token,String secret){
		accessToken = token;
		accessTokenSecret = secret;
	}
	public void CommitConfig(){
//		SharedPreferences mSharedPreferencs = PreferenceManager.getDefaultSharedPreferences(mContext);
		SharedPreferences mSharedPreferencs = mContext.getSharedPreferences(CONFIG_FILE,Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = mSharedPreferencs.edit();
		editor.putString(PF_ACCESSTOKEN, accessToken);
		editor.putString(PF_ACCESSTOKENSECRET, accessTokenSecret);
		editor.putString(PF_SCREEN_NAME, screenName);
		editor.putBoolean(PF_WIFIONLY, wifionly);
		editor.putLong(PF_ACCESS_UPDATE, accessUpdateTime);
		editor.putLong(PF_WIDGET_UPDATE, widgetUpdateTime);
		editor.commit();
	}
}
