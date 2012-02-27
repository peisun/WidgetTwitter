package jp.peisun.shuzobotwidget;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;



import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

public class ShuzobotwidgetActivity extends Activity implements OnClickListener {
	private final static String TAG = "ShuzobotwidgetActivity";
    /** Called when the activity is first created. */
	WebView mWebView = null;
	private Button mBtnSignIn;
	private Button mBtnConfig;
	
	public static Twitter mTwitter = null;
	public static RequestToken mRequestToken = null;
	public final static String CALLBACK_URL = "myapp://mainactivity";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        
        mBtnConfig = (Button)findViewById(R.id.button_config);
        mBtnConfig.setOnClickListener(this);
        mWebView = (WebView)findViewById(R.id.webView);
        mWebView.loadUrl("file:///android_asset/index.html");
        
        
    }
    @Override
	public boolean onKeyDown(int keycode, KeyEvent event) {
		if(keycode == KeyEvent.KEYCODE_BACK) {
			if( mWebView.canGoBack()) {
				mWebView.goBack();
			} else {
				this.finish();				
			}
		}
		return super.onKeyDown(keycode, event);
	}
	@Override
	public void onClick(View v) {
		// TODO 自動生成されたメソッド・スタブ
		if(v == (View)mBtnSignIn){
			mRequestToken = doOauth(null);
		}
		else if(v == (View)mBtnConfig){
			
		}
		
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
	 
	private void showShuzoConsentFollowDialog(){
		AlertDialog.Builder dlg = new AlertDialog.Builder(this);

		dlg.setMessage(getString(R.string.consentfollow_message));
		dlg.setPositiveButton("OK", new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				// TODO 自動生成されたメソッド・スタブ
				Intent intent = new Intent(TwitterAccessService.INTENT_FOLLOW_SHUZO);
				startService(intent);
				arg0.dismiss();
			}

		});
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
		super.onResume();
	}	
}