package jp.peisun.shuzobotwidget;


import java.io.File;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;


public class ShuzoConfigActivity extends PreferenceActivity {
	private String TAG = "ShuzoConfigActivity";
	private ConfigData mConfig = new ConfigData(getBaseContext());
	public static Twitter mTwitter = null;
	public static RequestToken mRequestToken = null;
	public final static String CALLBACK_URL = "myapp://mainactivity";
	
	private Preference.OnPreferenceChangeListener  onPreferenceChangeListener_checkwifionly =
		new OnPreferenceChangeListener(){
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			
			boolean check = ((Boolean)newValue).booleanValue();
			
			if (check != mConfig.wifionly ) {
				mConfig.wifionly = check;
			}
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
	        listpref.setSummary(summary); 
	        
	        sendIntentConfig();
	        // 変更を適用するために true を返す  
	        return true;
		}
	};
	private Preference.OnPreferenceChangeListener  onPreferenceChangeListener_widgetUpdate =
		new OnPreferenceChangeListener(){
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			ListPreference listpref =(ListPreference)preference;
			mConfig.accessUpdateTime = Long.parseLong(newValue.toString());
	        String summary = SummaryfindById(R.array.entries_widget_update,
	        		R.array.entryvalue_widget_update,mConfig.widgetUpdateTime);
	        listpref.setSummary(summary); 
	        
	        sendIntentConfig();

	        // 変更を適用するために true を返す  
	        return true;
		}
	};
	private Preference.OnPreferenceChangeListener  onPreferenceChangeListener_oauth =
		new OnPreferenceChangeListener(){
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			mRequestToken = doOauth(null);
	        
	        sendIntentConfig();

	        // 変更を適用するために true を返す  
	        return true;
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO 自動生成されたメソッド・スタブ
		super.onCreate(savedInstanceState);
	}
	@Override
	protected void onNewIntent(Intent intent) {
		// TODO 自動生成されたメソッド・スタブ
		super.onNewIntent(intent);
		String action = intent.getAction();
		if(action.equals(TwitterAccessService.INTENT_IS_SHUZO)){
			boolean is = intent.getBooleanExtra(TwitterAccessService.SHUZO, false);
			if(is == false){
				showShuzoConsentFollowDialog();
			}
		}
		else if(action.equals("android.intent.action.VIEW")){
			Uri uri = intent.getData();
			if(uri != null && uri.toString().startsWith(CALLBACK_URL)){
				AccessToken accessToken = getTwitterAccessToken(uri,mRequestToken);
				
				// アクセストークンが取れたら、スクリーンネームを取っておく
				mConfig.screenName = mTwitter.getScreenName();
				Log.d(TAG,"screenName "+ mConfig.screenName);
				
				// ActivityでのTwitterは終わり
				mTwitter.shutdown();
				// ここでAccessTokenを保存しておけば次回から認証不要となる
				try{
					writeAccessToken(accessToken);
				}
				catch (Exception e){
					Log.d(TAG,"accessTokenファイルセーブ失敗");
				}
				
				Intent i = new Intent(TwitterAccessService.INTENT_READ_SHUZO);
				startService(i);
			}
			mTwitter.shutdown();
			mTwitter = null;
			
		}
		
	}
	private RequestToken doOauth(AccessToken accessToken){
		String authUrl = null;
		RequestToken requestToken = null;
		String[] consumer = TwitterAccessService.readAssetFile(this, "key.txt");
		mTwitter = new TwitterFactory().getInstance();
		mTwitter.setOAuthConsumer(consumer[0], consumer[1]);

		if(accessToken == null){
			//AccessToken accessToken = null;
			try {
				requestToken = mTwitter.getOAuthRequestToken(CALLBACK_URL);
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
			accessToken = mTwitter.getOAuthAccessToken(requestToken, verifier);
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
	    return (String)entries[i];
	}
	private void writeAccessToken (AccessToken accessToken)throws Exception {
    	if(accessToken == null ){
    		return ;
    	}
    	// RequestTokenを保存  
     	OutputStream out = openFileOutput(TwitterAccessService.ACCESS_TOKEN, MODE_PRIVATE);

    	ObjectOutputStream oos = new ObjectOutputStream(out);  
    	oos.writeObject(accessToken); 
    	oos.close();
    	out.close();
    }
	@Override
	protected void onResume() {
		// TODO 自動生成されたメソッド・スタブ
		String filepath = this.getFilesDir().getAbsolutePath() + "/" +  TwitterAccessService.ACCESS_TOKEN;
		File file = new File(filepath);
		if(file.exists() == true){
			mBtnSignIn.setEnabled(false);
			Intent intent = new Intent(TwitterAccessService.INTENT_READ_SHUZO);
			startService(intent);
		}
		mConfig.CommitConfig(getBaseContext());
		super.onResume();
	}
	private boolean readPreference(){
		
	}
}
