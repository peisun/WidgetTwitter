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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
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
	public final static String INTENT_WIDGET_UPDATE ="jp.peisun.shuzobotwidget.widgetupdate";
	public final static String INTENT_WIFI_CHANGED = "jp.peisun.shuzobotwidget.wifichanged";
	public final static String INTENT_SCREEN_CHANGED = "jp.peisun.shuzobotwidget.screenchanged";



	public final static String SHUZO = "shuzo_matsuoka";
	public final static String OAUTH = "oauth";
	public final static String SCREEN = "screen";
	
	
	
	// ネットワークについて
	private final static int DISCONNECT = -1; // >=0はConnectivityManager.TYPE_xxxにあるから

	// AccessToken のファイル名
	public final static String ACCESS_TOKEN = "AccessToken";
	// Twitter関連
	private AccessToken mAccessToken = null;
	public static Twitter mTwitter = null;
	public static RequestToken mRequestToken = null;
	private long mRequestWaitTime = 10*60*1000; // 10分
	private final static int PAGING_SIZE = 20;
	private final static long mUpdateTime = 15*1000; // 1分
	private long lestId = 0;
	private int retry = 0;
	private final static int RETRY_MAX = 3;

	// 修造bot
	private String mShuzoBot = "shuzo_matsuoka";
	private long mShuzoBotId;
	private User mUser = null;
	private ResponseList<Status> mResponselist;
	private int mListNum = 0;

	// ハンドラ
	private final int MSG_WIDGET_UPDATE = 1;
	private final int MSG_WAIT_GETTIMELINE = 2;
	private final int MSG_SCREEN_OFF = 3;
	private final long WIFI_ENABLED_WAIT = 2000;
	private WaitHandler mHandler = new WaitHandler();

	// コンフィグレーション
	private boolean mWifiOnly = true;	// true:Wifi Only false:other
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
		
		// Twitterのインスタンスがなかったときの処理
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
		// インテントの処理
		if(intent != null){
		String action = intent.getAction();
		if(action.equals(INTENT_READ_SHUZO)){
			int connectType = ConectivityStatus();
			if(connectType == ConnectivityManager.TYPE_WIFI){
				// WIFIに接続されていたら、無条件にTimelineを取得
				actionGetTimelineUser();
			}
			else if(connectType != ConnectivityManager.TYPE_WIFI && mWifiOnly == false){
				// WIFI接続じゃなくてもよいなら、Timelineを取得
				actionGetTimelineUser();
			}
			else {
				// たぶんnetworkに接続されていない
			}
		}
		// Wifiの状態が変化したとき
		else if(action.equals(INTENT_WIFI_CHANGED)){
			int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
			// WIFI接続されたら、Timelineを取得
			if(state == WifiManager.WIFI_STATE_ENABLED){
				Log.d(TAG,"WifiManager.WIFI_STATE_CHANGED_ACTION: WIFI_STATE_ENABLED");
				// どうも、WIFI接続されても、ちょっと待たないといけないっぽい
				mHandler.waitGetTimeline();

				
			}
			else if(mWifiOnly == true && state != WifiManager.WIFI_STATE_ENABLED){
				Log.d(TAG,"WifiManager.WIFI_STATE_CHANGED_ACTION: OTHER");
				
				// WIFI接続時のみの取得で、WiFi接続が切られたら定期取得を止める
				cancelGetTimelineUser();
			}
			else {
				Log.d(TAG,"WifiManager.WIFI_STATE_CHANGED_ACTION: no connect");
			}
			
		}
		else if(action.equals(INTENT_SCREEN_CHANGED)){
			boolean screen = intent.getBooleanExtra(SCREEN, false);
			Log.d(TAG,"INTENT_SCREEN_CHANGED "+screen);
			if(screen == false){
				mHandler.scrrenOff();
			}
		}
		else if(action.equals(INTENT_WIDGET_UPDATE)){
			if(mResponselist != null){
				actionWidgetUpdate();
			}
			else {
				Intent i = new Intent(INTENT_READ_SHUZO);
				this.startService(i);
			}
		}
		else if(action.equals(INTENT_STOP)){
			if(mTwitter != null){
				mTwitter.shutdown();
				mTwitter = null;
			}
			stopSelf();
		}
		}

		return START_STICKY;
		//return super.onStartCommand(intent, flags, startId);
	}
	private void actionWidgetUpdate(){
		Log.d(TAG,"actionWidgetUpdate");
		if(mResponselist != null && mListNum < mResponselist.size()){
			String status_text = mResponselist.get(mListNum).getText();
			updateStatusText(splitStatusText(status_text));
			mListNum++;
			if(mListNum > mResponselist.size()){
				mListNum = 0;
			}	
			mHandler.waitWidgetUpdate(mUpdateTime);
		}
		else {
			updateStatusText(splitStatusText(this.getString(R.string.init_message)));
		}
	}
	
	private void actionGetTimelineUser(){
		ResponseList<Status> list = null;
		list = getTimelineUser(mShuzoBot);
		if(list != null && list.size() != 0){
			// Timelineが取得できたら、新しいものと入れ替え
			mResponselist = list;
			actionWidgetUpdate();
			waitRequest(mRequestWaitTime);
		}
		// Timelineが取得できなくても、すでに取得できているものがあれば
		// それを表示
		else if(list == null && mResponselist != null){
			actionWidgetUpdate();
			waitRequest(mRequestWaitTime);
		}
		// リトライをかける
		else if(retry < RETRY_MAX){
			retry++;
			updateStatusText(getString(R.string.errorGetTimeline));
			mHandler.waitGetTimeline();
		}
		// まったくもって取得できなかったら、エラーらしきものを表示
		else {
			updateStatusText(getString(R.string.errorGetTimeline));
		}
		
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
		Log.d(TAG,"TwitterFactory.getInstanc");

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
	private void cancelGetTimelineUser(){
		Intent intent = new Intent(INTENT_READ_SHUZO);
		PendingIntent readTwitterSender = PendingIntent.getService(this,0, intent, 0);
		AlarmManager mAmWaitRequest =(AlarmManager)getSystemService(ALARM_SERVICE);
		mAmWaitRequest.cancel(readTwitterSender);
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
		if(list != null){
			retry = 0;
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
	private int ConectivityStatus(){
		int connectType;
		ConnectivityManager connectivityManager = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netinfo = connectivityManager.getActiveNetworkInfo();
		if(netinfo != null){
			connectType = netinfo.getType();
			Log.d(TAG,"Connecting " + netinfo.getTypeName());
		}
		else {
			connectType = DISCONNECT;
		}
		return connectType;
	}
	private boolean pmisScreen(){
		// PowerManagerを取得する
		boolean screen;
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		screen = pm.isScreenOn();
		// スクリーンが暗いままならlockを解除する
		if(screen==false){
			Log.d(TAG,"ScreenOff");
		}
		else {
			Log.d(TAG,"ScreenOn");
		}
		return screen;
	}
	class WaitHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
			case MSG_WIDGET_UPDATE:
//				if(pmisScreen() == true && mResponselist != null && mListNum < mResponselist.size()){
				if(mResponselist != null && mListNum < mResponselist.size()){
					actionWidgetUpdate();
				}
				mHandler.waitWidgetUpdate(mUpdateTime);
				break;
			case MSG_WAIT_GETTIMELINE:
				Log.d(TAG,"MSG_WAIT_GETTIMELINE");
				actionGetTimelineUser();
			case MSG_SCREEN_OFF:
				break;
			default:
				break;
			}
		}
		public void scrrenOff(){
			this.removeMessages(MSG_WIDGET_UPDATE); 
			this.removeMessages(MSG_SCREEN_OFF);
			sendMessage(obtainMessage(MSG_SCREEN_OFF));
		}
		public void waitWidgetUpdate(long time){
			this.removeMessages(MSG_WIDGET_UPDATE);  
			sendMessageDelayed(obtainMessage(MSG_WIDGET_UPDATE), time);
		}
		public void waitGetTimeline(){
			Log.d(TAG,"Handler:waitGetTimeline");
			this.removeMessages(MSG_WAIT_GETTIMELINE);  
			sendMessageDelayed(obtainMessage(MSG_WAIT_GETTIMELINE), WIFI_ENABLED_WAIT);
		}
	}
	@Override
	public void onCreate() {
		// TODO 自動生成されたメソッド・スタブ
		// Intent.ACTION_SCrEEN_ON/OFFはManifestに書いておいてもダメらしい
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
	    getApplicationContext().registerReceiver(new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
	          Log.d(TAG,intent.getAction());
	          
	        }
	    }, filter);
	    
	    filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
	    this.getApplicationContext().registerReceiver(new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
	        	Log.d(TAG,intent.getAction());
	        	mHandler.scrrenOff();
	        }
	    }, filter);
		super.onCreate();
	}
	
	
}
