package jp.peisun.shuzobotwidget;



import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RemoteViews;


public class ShuzoConfigActivity extends PreferenceActivity {
	private String TAG = "ShuzoConfigActivity";
	private static ConfigData mConfig = null;
//	public static Twitter mTwitter = null;
	public static RequestToken mRequestToken = null;
	public final static String CALLBACK_URL = "myapp://mainactivity";
	
	
	private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	
	private Preference.OnPreferenceChangeListener  onPreferenceChangeListener_checkwifionly =
		new OnPreferenceChangeListener(){
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			CheckBoxPreference cbp = (CheckBoxPreference)preference;
			boolean check = ((Boolean)newValue).booleanValue();
			
			if (check != mConfig.wifionly ) {
				mConfig.wifionly = check;
			}
			if(mConfig.wifionly == true){
				cbp.setSummaryOn(R.string.summary_wifionly_on);
	        }
	        else {
	        	cbp.setSummaryOff(R.string.summary_wifionly_off);
	        }
			mConfig.CommitConfig();
			sendIntentConfig(ConfigData.Order.ORDER_WIFI);
	        // 変更を適用するために true を返す  
	        return true;
		}
	};
	private Preference.OnPreferenceChangeListener  onPreferenceChangeListener_accessUpdate =
		new OnPreferenceChangeListener(){
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			ListPreference listpref =(ListPreference)preference;
			mConfig.accessUpdateTime = Long.parseLong(newValue.toString());
			String summary = SummaryfindById(R.array.entries_access_time,
	        		R.array.entryvalue_access_update,mConfig.accessUpdateTime);
			if(mConfig.accessUpdateTime < 0){
				listpref.setSummary(getString(R.string.summary_update_type_user));
			}
			else {
				listpref.setSummary(String.format("%s %s",summary, getString(R.string.summary_update_type_auto)));
			}
	        mConfig.CommitConfig();
	        
	        sendIntentConfig(ConfigData.Order.ORDER_ACCESS_UPDATE);
	        // 変更を適用するために true を返す  
	        return true;
		}
	};
	private Preference.OnPreferenceChangeListener  onPreferenceChangeListener_widgetUpdate =
		new OnPreferenceChangeListener(){
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			ListPreference listpref =(ListPreference)preference;
			mConfig.widgetUpdateTime = Long.parseLong(newValue.toString());
	        String summary = SummaryfindById(R.array.entries_widget_update,
	        		R.array.entryvalue_widget_update,mConfig.widgetUpdateTime);
	        listpref.setSummary(String.format("%s%s",summary, getString(R.string.summary_widget_update))); 
	        mConfig.CommitConfig();
	        
	        sendIntentConfig(ConfigData.Order.ORDER_WIDGET_UPDATE);

	        // 変更を適用するために true を返す  
	        return true;
		}
	};
	private Preference.OnPreferenceClickListener  onPreferenceClickListener_oauth =
		new OnPreferenceClickListener(){
		@Override
		public boolean onPreferenceClick(Preference preference) {
			mRequestToken = doOauth(null);

	        // 変更を適用するために true を返す  
	        return false;
		}
	};
	private Preference.OnPreferenceClickListener  onPreferenceClickListener_about =
		new OnPreferenceClickListener(){
		@Override
		public boolean onPreferenceClick(Preference preference) {
			// TODO 自動生成されたメソッド・スタブ
			Intent i = new Intent(getApplicationContext(),ShuzobotwidgetActivity.class);
			startActivity(i);
			return true;
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO 自動生成されたメソッド・スタブ
		super.onCreate(savedInstanceState);
		
		this.addPreferencesFromResource(R.xml.peference_main);
	
		// 
		if(mConfig == null){
			mConfig = new ConfigData();
			mConfig.getSharedPreferences(getApplicationContext());
		}
		// サインイン
        CharSequence cs = getText(R.string.pfkey_signin);  
        Preference pref = (Preference)findPreference(cs);   
        // リスナーを設定する  
        pref.setOnPreferenceClickListener(onPreferenceClickListener_oauth);
        // 
        if(mConfig.isAccessToken()==false){
        	pref.setTitle(R.string.menu_oauth);
        }
        else {
        	pref.setTitle(mConfig.screenName);
        	pref.setSummary(getString(R.string.summary_oauth_signin));
        }
        
        // WiFi Only  
        cs = getText(R.string.pfkey_wifiony);  
        CheckBoxPreference cbp = (CheckBoxPreference)findPreference(cs);   
        // リスナーを設定する  
        cbp.setOnPreferenceChangeListener(onPreferenceChangeListener_checkwifionly);
        cbp.setChecked(mConfig.wifionly);
        if(mConfig.wifionly){
        	cbp.setSummaryOff(R.string.summary_wifionly_on);
        }
        else {
        	cbp.setSummaryOff(R.string.summary_wifionly_off);
        }
        
        // 更新時間
        cs = getText(R.string.pfkey_access);  
        pref = (Preference)findPreference(cs);   
        // リスナーを設定する  
        pref.setOnPreferenceChangeListener(onPreferenceChangeListener_accessUpdate);
        String summary = SummaryfindById(R.array.entries_access_time,
        		R.array.entryvalue_access_update,mConfig.accessUpdateTime);
        pref.setDefaultValue((Object)new Long(mConfig.accessUpdateTime).toString());
		if(mConfig.accessUpdateTime < 0){
			pref.setSummary(getString(R.string.summary_update_type_user));
		}
		else {
			pref.setSummary(String.format("%s%s",summary,getString(R.string.summary_update_type_auto)));
			
		}
		Log.d(TAG,"access update "+ mConfig.accessUpdateTime + " summary " + summary);
        
        // 表示間隔
        cs = getText(R.string.pfkey_widget);  
        pref = (Preference)findPreference(cs);   
        summary = SummaryfindById(R.array.entries_widget_update,
        		R.array.entryvalue_widget_update,mConfig.widgetUpdateTime);
        if(summary == null){
        	summary = getString(R.string.defaulEntreisWidgetUpdate);
        	mConfig.widgetUpdateTime = Long.parseLong(getString(R.string.defaultValueWidgetUpdate));
        }
        pref.setSummary(String.format("%s%s",summary, getString(R.string.summary_widget_update)));
        pref.setDefaultValue((Object)String.format("%d", mConfig.widgetUpdateTime));
        // リスナーを最後に設定して反映される  
        pref.setOnPreferenceChangeListener(onPreferenceChangeListener_widgetUpdate);
        Log.d(TAG,"widget update "+ mConfig.widgetUpdateTime + " summary " + summary);
        
        // about
        cs = getText(R.string.pfkey_about);  
        pref = (Preference)findPreference(cs);   
        // リスナーを設定する  
        pref.setOnPreferenceClickListener(onPreferenceClickListener_about);
        

	}
	@Override
	protected void onNewIntent(Intent intent) {
		// TODO 自動生成されたメソッド・スタブ
		super.onNewIntent(intent);
		
		String action;
		if(intent == null){
			action = "";
		}
		else {
			action = intent.getAction();
		}
		if(action.equals("android.intent.action.VIEW")){
			Uri uri = intent.getData();
			if(uri != null && uri.toString().startsWith(CALLBACK_URL)){
				AccessToken accessToken = getTwitterAccessToken(uri,mRequestToken);
				
				// アクセストークンが取れたら、スクリーンネームを取っておく
				mConfig.screenName = accessToken.getScreenName();
				
				// トークンをConfigDataに入れる
				mConfig.setAccessToken(accessToken.getToken(),accessToken.getTokenSecret());
				
				// インテントを投げる
				sendIntentOauth();
				
				CharSequence cs = getText(R.string.pfkey_signin);  
		        Preference pref = (Preference)findPreference(cs);
		        pref.setTitle(mConfig.screenName);
		        pref.setSummary(getString(R.string.summary_oauth_signin));
		        
				Log.d(TAG,"screenName "+ mConfig.screenName);
				
//				Intent i = new Intent(TwitterAccessService.INTENT_READ_SHUZO);
//				startService(i);
				
			}
			
			
		}
		
	}
	private RequestToken doOauth(AccessToken accessToken){
		String authUrl = null;
		RequestToken requestToken = null;
		String[] consumer = TwitterAccessService.readAssetFile(this, "key.txt");
		TwitterAccessService.mTwitter = new TwitterFactory().getInstance();
		TwitterAccessService.mTwitter.setOAuthConsumer(consumer[0], consumer[1]);

		if(accessToken == null){
			//AccessToken accessToken = null;
			try {
				requestToken = TwitterAccessService.mTwitter.getOAuthRequestToken(CALLBACK_URL);
				authUrl = requestToken.getAuthorizationURL();
			}
			catch(Exception e){
				e.printStackTrace();
				return null;
			}
			Log.d(TAG,authUrl);
			Uri uri = Uri.parse(authUrl);  
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);  
			startActivity(intent);
		}
		else {
			
        }
		return requestToken;
    }
	private AccessToken getTwitterAccessToken(Uri uri,RequestToken requestToken){
		AccessToken accessToken = null;
		String verifier = uri.getQueryParameter("oauth_verifier"); 
		if(verifier == null || verifier.length() == 0){
			Log.d(TAG,"あれ？");
			return null;
		}
		Log.d(TAG,"oauth_verifier : " + verifier);
		try {
			accessToken = TwitterAccessService.mTwitter.getOAuthAccessToken(requestToken, verifier);
		}
		catch (TwitterException te) {
	        if(401 == te.getStatusCode()){
	        	Log.d(TAG,"Unable to get the access token.");
	          }else{
	            te.printStackTrace();
	          }
 			Log.d(TAG,"認証失敗");
 			return null;
		}  
		
		Log.d(TAG,"認証成功");
		return accessToken;
	}
	private String SummaryfindById(int id,int valueid,long value){
		String[] entries = getResources().getStringArray(id);
		String[] entryValue = getResources().getStringArray(valueid);
	    
	    final String valueString = String.format("%d",value);
	    int i;
	    for (i = 0; i < entryValue.length; i++) {
	    	if(valueString.equals(entryValue[i])){
	    		break;
	    	}
	    }
	    if(i >= entryValue.length){
	    	return null;
	    }
	    return (String)entries[i];
	}
	private void sendIntentOauth(){
		Intent i = new Intent(TwitterAccessService.INTENT_OAUTH);
		i.putExtra(ConfigData.PF_ACCESSTOKEN, mConfig.getAccessToken());
		i.putExtra(ConfigData.PF_ACCESSTOKENSECRET, mConfig.getAccessTokenSecret());
		mConfig.CommitConfig();
		startService(i);
	}
	private void sendIntentConfig(int order){
		Intent i = new Intent(TwitterAccessService.INTEINT_UPDATE_CONFIG);
		i.putExtra(ConfigData.PF_ORDER, order);
		i.putExtra(ConfigData.PF_ACCESSTOKEN, mConfig.getAccessToken());
		i.putExtra(ConfigData.PF_ACCESSTOKENSECRET, mConfig.getAccessTokenSecret());
		i.putExtra(ConfigData.PF_SCREEN_NAME, mConfig.screenName);
		i.putExtra(ConfigData.PF_WIFIONLY, mConfig.wifionly);
		i.putExtra(ConfigData.PF_WIDGET_UPDATE, mConfig.widgetUpdateTime);
		i.putExtra(ConfigData.PF_ACCESS_UPDATE, mConfig.accessUpdateTime);
		mConfig.CommitConfig();
		startService(i);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO 自動生成されたメソッド・スタブ
		if(keyCode == KeyEvent.KEYCODE_BACK) {
//			widgetSetResult(RESULT_OK);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	
}
