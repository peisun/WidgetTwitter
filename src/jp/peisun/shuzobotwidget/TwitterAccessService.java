package jp.peisun.shuzobotwidget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
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
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.IDs;

public class TwitterAccessService extends Service {
	private final static String TAG = "TwitterAccessService";
	//使ってない
	
	public final static String INTENT_IS_SHUZO = "jp.peisun.shuzobotwidget.is_shuzo";
	public final static String INTENT_FOLLOW_SHUZO = "jp.peisun.shuzobotwidget.follow_shuzo";
	//	public final static String INTENT_WAIT = "jp.peisun.shuzobotwidget.wait";
	//	public final static String INTENT_READ_TWITTER = "jp.peisun.shuzobotwidget.readtwitter";
	public final static String INTENT_OAUTH = "jp.peisun.shuzobotwidget.oauth";
	public final static String INTENT_READ_SHUZO     = "jp.peisun.shuzobotwidget.readshuzo";
	public final static String INTENT_CLICK_GET      = "jp.peisun.shuzobotwidget.clickget";
	public final static String INTEINT_UPDATE_CONFIG = "jp.peisun.shuzobotwidget.updateconfig";
	public final static String INTENT_STOP           = "jp.peisun.shuzobotwidget.stop";
	public final static String INTENT_START          = "jp.peisun.shuzobotwidget.start";
	public final static String INTENT_CLICK          = "jp.peisun.shuzobotwidget.click";
	public final static String INTENT_WIDGET_UPDATE  = "jp.peisun.shuzobotwidget.widgetupdate";
	public final static String INTENT_WIFI_CHANGED   = "jp.peisun.shuzobotwidget.wifichanged";
	public final static String INTENT_SCREEN_CHANGED = "jp.peisun.shuzobotwidget.screenchanged";

	public final static String CONFIG_ORDER = "order";


	public final static String SHUZO = "shuzo_matsuoka";
	public final static String OAUTH = "oauth";
	public final static String SCREEN = "screen";
	public final static String WIDGET_ID ="WidgetID";

	public static ConfigData mConfig = null;

	private int mWidgetId;

	// ネットワークについて
	private final static int DISCONNECT = -1; // >=0はConnectivityManager.TYPE_xxxにあるから

	// AccessToken のファイル名
	public final static String ACCESS_TOKEN = "AccessToken";
	// Twitter関連
	public static Twitter mTwitter = null;
	public static RequestToken mRequestToken = null;
	private final static int PAGING_SIZE = 20;

	private int retry = 0;
	private final static int RETRY_MAX = 3;

	// 修造bot
	private String mShuzoBot = "shuzo_matsuoka";
	private long mShuzoBotId;
	private User mUser = null;
	private ResponseList<Status> mResponselist;
	private int mListNum = 0;
	private TimelineTask mTimelineTask = null;

	// ハンドラ
	private final int MSG_WIDGET_UPDATE = 1;
	private final int MSG_WAIT_GETTIMELINE = 2;
	private final int MSG_SCREEN_OFF = 3;
	private final long WIFI_ENABLED_WAIT = 2000;
	private WaitHandler mHandler = new WaitHandler();


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

		String action;
		if (intent != null) {
			action = intent.getAction();

		} else {
			action = "";
		}
		// インテントの処理
		if(action.equals(INTENT_START)){
			mWidgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			setClickPendingIntent(mWidgetId);
			
			if(mConfig.isAccessToken() == true && mTwitter == null){
				mTwitter = getTwitterInstance();
				if(mTwitter == null){
					updateStatusText(getString(R.string.signin_message));
					stopSelf();
					Log.d(TAG,INTENT_START+ "mTwitter null");
					return START_NOT_STICKY;
				}
				else {
					Log.d(TAG,INTENT_START+" getShuzoBot");
					getShuzoBot();
				}
			}
			else {
				updateStatusText(getString(R.string.signin_message));
				stopSelf();
				Log.d(TAG,INTENT_START+ "mConfig null");
				return START_NOT_STICKY;
			}
		}
		else if(action.equals(INTENT_OAUTH)){
			String token = intent.getStringExtra(ConfigData.PF_ACCESSTOKEN);
			String secret = intent.getStringExtra(ConfigData.PF_ACCESSTOKENSECRET);
			mConfig.setAccessToken(token, secret);
			mConfig.CommitConfig();
			getShuzoBot();
		}
		else if(action.equals(INTENT_CLICK)){
			Intent i = new Intent(this,ShuzoConfigActivity.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}
		else if(action.equals(INTENT_STOP)){
			if(mTwitter != null){
				mTwitter.shutdown();
				mTwitter = null;
			}
			stopSelf();
			return START_NOT_STICKY;
		}
		else if(action.equals(INTEINT_UPDATE_CONFIG)){
			if(mConfig == null){
				mConfig = new ConfigData();
				mConfig.getSharedPreferences(getApplicationContext());
			}
			int order = getIntentExtra(intent);
			orderAction(order);

		}
		else if(mConfig != null && mConfig.isAccessToken() == true){
			if(mTwitter == null){
				mTwitter = getTwitterInstance();
			}
			if(action.equals(INTENT_READ_SHUZO)){
				getShuzoBot();
			}
			else if(action.equals(INTENT_CLICK_GET)){
				
				getShuzoBot();
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
				else if(mConfig.wifionly == true && state != WifiManager.WIFI_STATE_ENABLED){
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
		}
		else {
			Log.d(TAG,INTENT_START+ " not signin ?");
			return START_NOT_STICKY;
		}



		return START_STICKY;
		//return super.onStartCommand(intent, flags, startId);
	}
	private int getIntentExtra(Intent intent){

		mConfig.wifionly = intent.getBooleanExtra(ConfigData.PF_WIFIONLY, mConfig.wifionly);
		mConfig.accessUpdateTime = intent.getLongExtra(ConfigData.PF_ACCESS_UPDATE, mConfig.accessUpdateTime);
		mConfig.widgetUpdateTime = intent.getLongExtra(ConfigData.PF_WIDGET_UPDATE, mConfig.widgetUpdateTime);
		mConfig.screenName = intent.getStringExtra(ConfigData.PF_SCREEN_NAME);
		mConfig.CommitConfig();
		int order = intent.getIntExtra(CONFIG_ORDER, -1);
		return order;
	}
	private void orderAction(int order){
		switch(order){
		case ConfigData.Order.ORDER_WIFI:
			getShuzoBot();
			break;
		case ConfigData.Order.ORDER_ACCESS_UPDATE:
			if(mConfig.accessUpdateTime > 0){
				waitRequest(mConfig.accessUpdateTime);
			}
			else {
				// <=0 は「更新ボタン」
				cancelGetTimelineUser();
			}
			break;
		case ConfigData.Order.ORDER_WIDGET_UPDATE:
			mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
			break;
		default:
			break;
		}
	}
	private void actionWidgetUpdate(){
		Log.d(TAG,"actionWidgetUpdate");
		if(mResponselist != null && mListNum < mResponselist.size()){
			String status_text = mResponselist.get(mListNum).getText();
			updateStatusText(splitStatusText(status_text));
			mListNum++;
			if(mListNum >= mResponselist.size()){
				mListNum = 0;
			}	
			
		}
		else {
			updateStatusText(splitStatusText(this.getString(R.string.init_message)));
		}
		mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
	}
	private void getShuzoBot(){
		int connectType = ConectivityStatus();
		if(connectType == ConnectivityManager.TYPE_WIFI){
			// WIFIに接続されていたら、無条件にTimelineを取得
			actionGetTimelineUser();
		}
		else if(connectType != ConnectivityManager.TYPE_WIFI && mConfig.wifionly == false){
			// WIFI接続じゃなくてもよいなら、Timelineを取得
			actionGetTimelineUser();
		}
		else {
			// たぶんnetworkに接続されていない
			updateStatusText(getString(R.string.errorGetTimeline));
			mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
		}
	}
	private void actionGetTimelineUser(){
		if(mTimelineTask != null){
			if(mTimelineTask.getStatus() != AsyncTask.Status.FINISHED){
				return;
			}
			else {
				mTimelineTask = null;
			}
		}
		updateStatusText(getString(R.string.gettimeline_message));
		mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
		mTimelineTask = new TimelineTask();
		mTimelineTask.setUser(mShuzoBot);
		mTimelineTask.execute(mTwitter);
	}
	public static String[] readAssetFile(Context context,String filename){
		String[] split = null;
		AssetManager as = context.getResources().getAssets();   

		InputStream is = null;  
		BufferedReader br = null;  

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
			Toast.makeText(context, "悪いけど、あなたはこのWidgetを使えません。", Toast.LENGTH_SHORT).show();  
		}
		//		Log.d(TAG,"key = " + split[0] + " secret = " + split[1]);
		return split;

	}
	private Twitter getTwitterInstance(){
		Twitter tw = null;
		if(mConfig != null){
			String[] consumer = readAssetFile(this,"key.txt");

			String token = mConfig.getAccessToken();
			String secret = mConfig.getAccessTokenSecret();
			if(consumer != null 
					&& token.equals("") == false 
					&& secret.equals("")== false){
				ConfigurationBuilder cb = new ConfigurationBuilder();
				cb.setOAuthAccessToken(mConfig.getAccessToken());
				cb.setOAuthAccessTokenSecret(mConfig.getAccessTokenSecret());
				cb.setOAuthConsumerKey(consumer[0]);
				cb.setOAuthConsumerSecret(consumer[1]);

				TwitterFactory tf = new TwitterFactory(cb.build());
				tw = tf.getInstance();
				Log.d(TAG,"TwitterFactory.getInstanc");
			}
			else {
				Log.d(TAG, "AccessToken is null");
			}
		}

		return tw;
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
				mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
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
		// ConfigDataの読み込み
		mConfig = new ConfigData();
		mConfig.getSharedPreferences(getApplicationContext());
		
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
	private void setClickPendingIntent(int widgetId){
		if(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID){
			Log.d(TAG,"setClickPendingIntent wWidgetId " +widgetId);
			RemoteViews views = new RemoteViews(getPackageName(),R.layout.layoutwidget);
			Intent configIntent = new Intent(INTENT_CLICK);
			Intent updateIntent = new Intent(INTENT_CLICK_GET);
			PendingIntent pendingConfigIntent = PendingIntent.getService(this, 0, configIntent, 0);
			PendingIntent pendingUpdateIntent = PendingIntent.getService(this, 0, updateIntent, 0);
			views.setOnClickPendingIntent(R.id.imageButton1, pendingConfigIntent);
			views.setOnClickPendingIntent(R.id.relativeLayout1, pendingUpdateIntent);
			AppWidgetManager manager = AppWidgetManager.getInstance(this);

			ComponentName widget = new ComponentName(this,ShuzobotAppWidgetProvider.class);
			//			manager.setAppWidgetId(widgetId);
			manager.updateAppWidget(widget, views);
			//		views.setOnClickPendingIntent(R.id.textView1, pendingIntent);
			//		views.setOnClickPendingIntent(R.id.relativeLayout1, pendingIntent);
		}
	}
	public class TimelineTask extends AsyncTask<Twitter,Void,ResponseList<twitter4j.Status>>{
		private Twitter mTaskTwitter= null;
		private String user = null;;
		public void setUser(String name){
			user = name;
		}
		private ResponseList<twitter4j.Status> getTimeline(String user){
			ResponseList<twitter4j.Status> list = null;
			try {
				Paging paging = new Paging(PAGING_SIZE);
				Log.d(TAG,"getUserTimline...");

				list = mTaskTwitter.getUserTimeline(user,paging);

				Log.d(TAG,"getUserTimeline " + user + ":"+list.size());

			}
			catch(TwitterException te){
				te.printStackTrace();
			}
			return list;
		}
		@Override
		protected ResponseList<twitter4j.Status> doInBackground(Twitter... arg0) {
			// TODO 自動生成されたメソッド・スタブ
			mTaskTwitter = arg0[0];
			if(user != null && mTaskTwitter != null){
				return getTimeline(mShuzoBot);
			}
			return null;
		}
		
		@Override
		protected void onCancelled() {
			// TODO 自動生成されたメソッド・スタブ
			super.onCancelled();
		}

		@Override
		protected void onPostExecute(ResponseList<twitter4j.Status> result) {
			// TODO 自動生成されたメソッド・スタブ
			super.onPostExecute(result);
			if(result != null && result.size() != 0){
				// Timelineが取得できたら、新しいものと入れ替え
				mResponselist = result;
				retry = 0;
				actionWidgetUpdate();
				waitRequest(mConfig.accessUpdateTime);
			}
			// Timelineが取得できなくても、すでに取得できているものがあれば
			// それを表示
			else if(result == null && mResponselist != null){
				actionWidgetUpdate();
				waitRequest(mConfig.accessUpdateTime);
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
				retry = 0;
			}
			
		}

		@Override
		protected void onPreExecute() {
			// TODO 自動生成されたメソッド・スタブ
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			// TODO 自動生成されたメソッド・スタブ
			super.onProgressUpdate(values);
		}

	}

}
