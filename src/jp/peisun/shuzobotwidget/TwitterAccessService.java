package jp.peisun.shuzobotwidget;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.IDs;
import twitter4j.Friendship;

public class TwitterAccessService extends Service {
	private final static String TAG = "TwitterAccessService";
	public final static String INTENT_OAUTH = "jp.peisun.shuzobotwidget.oauth";
	//	public final static String INTENT_WAIT = "jp.peisun.shuzobotwidget.wait";
	//	public final static String INTENT_READ_TWITTER = "jp.peisun.shuzobotwidget.readtwitter";
	public final static String INTENT_READ_SHUZO = "jp.peisun.shuzobotwidget.readshuzo";
	public final static String INTENT_IS_SHUZO = "jp.peisun.shuzobotwidget.is_shuzo";
	public final static String INTENT_FOLLOW_SHUZO = "jp.peisun.shuzobotwidget.follow_shuzo";
	public final static String INTENT_STOP = "jp.peisun.shuzobotwidget.stop";



	public final static String SHUZO = "shuzo_matsuoka";
	public final static String OAUTH = "oauth";

	// AccessToken のファイル名
	public final static String ACCESS_TOKEN = "AccessToken";
	// Twitter関連
	private AccessToken mAccessToken = null;
	public static Twitter mTwitter = null;
	public static RequestToken mRequestToken = null;
	private long mRequestWaitTime = 10*60*1000; // 10分
	private final static int PAGING_SIZE = 20;
	private final static long mUpdateTime = 60*1000; // 1分
	private long lestId = 0;

	// 修造bot
	private String mShuzoBot = "shuzo_matsuoka";
	private long mShuzoBotId;
	private User mUser = null;
	private ResponseList<Status> mResponselist;
	private int mListNum = 0;

	// ハンドラ
	private final int MSG_GETTIMELINE = 1;
	private WaitHandler mHandler = new WaitHandler();

	// コンフィグレーション
	private boolean mWifiOnly = false;	// true:Wifi Only false:other
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void onDestroy() {
		// TODO 自動生成されたメソッド・スタブ
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO 自動生成されたメソッド・スタブ
		if(mTwitter == null){
			String[] line = readAssetFile(this,"key.txt");
			
			try {
				String filepath = this.getFilesDir().getAbsolutePath() + "/" +  TwitterAccessService.ACCESS_TOKEN;
				File file = new File(filepath);
				if(file.exists() == true){
					try {
						mAccessToken = readAccessToken();
					}
					catch(Exception e){
						e.printStackTrace();
						updateStatusText(getString(R.string.fileerror_message));
						stopSelf();
					}
					if(mAccessToken != null){
						setOauth(mAccessToken,line);
					}
					else {
						updateStatusText(getString(R.string.fileerror_message));
						stopSelf();
					}
				}
				else {
					updateStatusText(getString(R.string.signin_message));
					stopSelf();
				}
			} catch (Exception e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
				stopSelf();
			}
		}
		String action = intent.getAction();
		if(action.equals(INTENT_READ_SHUZO)){
			if(chkWifiStatus() ==true && mWifiOnly == true){
				actionGetTimelineUser();
			}
			else if(mWifiOnly == false){
				actionGetTimelineUser();
			}
		}
		else if(action.equals(INTENT_STOP)){
			if(mTwitter != null){
				mTwitter.shutdown();
				mTwitter = null;
			}
			stopSelf();
		}

		return START_STICKY;
		//return super.onStartCommand(intent, flags, startId);
	}
	private void actionGetTimelineUser(){
		mResponselist = getTimelineUser(mShuzoBot);
		if(mResponselist != null && mResponselist.size() != 0){
			if(mListNum < mResponselist.size()){
				String status_text = mResponselist.get(mListNum).getText();
				updateStatusText(splitStatusText(status_text));
				mListNum++;
				mHandler.waitGetTimeline();
			}
		}
		else {
			updateStatusText(getString(R.string.errorGetTimeline));
		}
		waitRequest(mRequestWaitTime);
	}
	public static String[] readAssetFile(Context context,String filename){
		String[] split = null;
		AssetManager as = context.getResources().getAssets();   

		InputStream is = null;  
		BufferedReader br = null;  

		StringBuilder sb = new StringBuilder();   
		try{  
			try {  
				is = as.open("key.txt");  
				br = new BufferedReader(new InputStreamReader(is));   

				String str;     
				if((str = br.readLine()) != null){     
					split = str.split(",");    
				}      
			} finally {  
				if (br != null) br.close();  
			} 
		} catch (IOException e) {  
			Toast.makeText(context, "読み込み失敗", Toast.LENGTH_SHORT).show();  
		}
		Log.d(TAG,"key = " + split[0] + " secret = " + split[1]);
		return split;

	}
	private void setOauth(AccessToken accessToken,String[] consumer){

		mTwitter = new TwitterFactory().getInstance();
		mTwitter.setOAuthConsumer(consumer[0], consumer[1]);
		mTwitter.setOAuthAccessToken(accessToken);

	}


	private AccessToken readAccessToken() throws Exception {
		AccessToken accessToken = null;  
		InputStream in = openFileInput("AccessToken");  
		ObjectInputStream ois = new ObjectInputStream(in);  
		accessToken = (AccessToken)ois.readObject();
		ois.close();
		in.close();
		return accessToken;
	}
	private void waitRequest(long time){

		Log.d(TAG,"Request wait set");
		long current = SystemClock.elapsedRealtime();
		current += time;
		Intent intent = new Intent(INTENT_READ_SHUZO);
		PendingIntent readTwitterSender = PendingIntent.getService(this,0, intent, 0);
		AlarmManager mAmWaitRequest =(AlarmManager)getSystemService(ALARM_SERVICE);
		mAmWaitRequest.cancel(readTwitterSender);
		mAmWaitRequest.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,current,readTwitterSender);

	}
	private void showToast(String message){
		// 第3引数は、表示期間（LENGTH_SHORT、または、LENGTH_LONG）
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	private boolean isFollowShuzo(){
		IDs followListId = null;
		boolean is = false;
		ResponseList<User> followUser;
		String name;
		try {
			followListId = mTwitter.getFriendsIDs(-1);

			long[] ids = followListId.getIDs();

			followUser = mTwitter.lookupUsers(ids);
			for(int i=0;i<followUser.size();i++){
				name = followUser.get(i).getScreenName();
				Log.d(TAG,"name " + name + " id "+followUser.get(i).getId());
				if(SHUZO.equals(name)){
					mShuzoBotId = followUser.get(i).getId();
					mShuzoBot = name;
					is = true;
					break;
				}
			}
		}
		catch (Exception e){

		}
		return is;
	}
	private boolean consetFriendship(String name){
		boolean success = false;
		try {
			mUser = mTwitter.createFriendship(name);
			String msg = String.format("%s%s", mUser.getName(),getString(R.string.createdFriendship_toast));
			showToast(msg);
			success = true;
		}
		catch (TwitterException te){
			String msg = String.format("%s%n%s%s%n%s",
					getString(R.string.sorry),
					mUser.getName(),
					getString(R.string.nocreatedFriendship_toast),
					getString(R.string.pleaseanother_createFrinedship));
			showToast(msg);
			success = false;
			te.getStatusCode();
		}
		return success;
	}
	private ResponseList<Status> getTimelineUser(String name){
		ResponseList<Status> list = null;
		try {
			Paging paging = new Paging(PAGING_SIZE);
			Log.d(TAG,"getUserTimline...");

			list = mTwitter.getUserTimeline(name,paging);

			Log.d(TAG,"getUserTimeline " + name + ":"+list.size());
		}
		catch(TwitterException te){
			te.printStackTrace();
		}
		return list;
	}

	private String splitStatusText(String status_text){
		int i;
		final CharSequence atMark = (CharSequence)"@";
		String[] split = status_text.split("[ \t]");
		if(split[0].contains(atMark)){
			i = 2;
		}
		else {
			i = 0;
		}

		String t = split[i];
		i++;
		for(;i < split.length ;i++){
			t += split[i];
		}
		//Log.d(TAG,status_text);
		Log.d(TAG,"split= "+t);
		return t;

	}
	private void updateStatusText(String text){


		RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.layoutwidget);
		remoteViews.setTextViewText(R.id.textView1, text);
		// AppWidgetの画面更新
		ComponentName thisWidget = new ComponentName(this, ShuzobotAppWidgetProvider.class);
		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		manager.updateAppWidget(thisWidget, remoteViews);

	}
	private boolean chkWifiStatus(){
		WifiManager wifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
		int state = wifiManager.getWifiState();
		if(state == WifiManager.WIFI_STATE_ENABLING){
			Log.d(TAG,"WifiManager: WIFI_STATE_ENABLING");
			return true;
		}
		else {
			Log.d(TAG,"WifiManager: OTHER");
			return false;
		}
	}
	class WaitHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
			case MSG_GETTIMELINE:
				if(mListNum < mResponselist.size()){
					String status_text = mResponselist.get(mListNum).getText();
					updateStatusText(splitStatusText(status_text));
					mListNum++;
					if(mListNum > mResponselist.size()){
						mListNum = 0;
					}
					mHandler.waitGetTimeline();
				}
				break;
			default:
				break;
			}
		}
		public void waitGetTimeline(){
			this.removeMessages(MSG_GETTIMELINE);  
			sendMessageDelayed(obtainMessage(MSG_GETTIMELINE), mUpdateTime);
		}
	}
}