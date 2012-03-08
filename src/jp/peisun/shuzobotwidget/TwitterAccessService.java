package jp.peisun.shuzobotwidget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

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
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterAccessService extends Service {
	private final static String TAG = "TwitterAccessService";
	//使ってない

	public final static String INTENT_IS_SHUZO = "jp.peisun.shuzobotwidget.is_shuzo";
	public final static String INTENT_FOLLOW_SHUZO = "jp.peisun.shuzobotwidget.follow_shuzo";
	//	public final static String INTENT_WAIT = "jp.peisun.shuzobotwidget.wait";
	//	public final static String INTENT_READ_TWITTER = "jp.peisun.shuzobotwidget.readtwitter";
	public final static String INTENT_OAUTH = "jp.peisun.shuzobotwidget.oauth";
	public final static String INTENT_READ_SHUZO     = "jp.peisun.shuzobotwidget.readshuzo";
	public final static String INTEINT_UPDATE_CONFIG = "jp.peisun.shuzobotwidget.updateconfig";
	public final static String INTENT_STOP           = "jp.peisun.shuzobotwidget.stop";
	public final static String INTENT_START          = "jp.peisun.shuzobotwidget.start";
	public final static String INTENT_WIDGET_UPDATE  = "jp.peisun.shuzobotwidget.widgetupdate";
	public final static String INTENT_WIFI_CHANGED   = "jp.peisun.shuzobotwidget.wifichanged";
	public final static String INTENT_SCREEN_CHANGED = "jp.peisun.shuzobotwidget.screenchanged";

	public final static String CONFIG_ORDER = "order";


	public final static String SHUZO = "shuzo_matsuoka";
	public final static String OAUTH = "oauth";
	public final static String SCREEN = "screen";
	public final static String WIDGET_ID ="WidgetID";
	public final static String WIDGET_TYPE ="wiget_type";
	public final static int WIDGET_TYPE_1 = 1;
	public final static int WIDGET_TYPE_2 = 2;


	public static volatile ConfigData mConfig = null;

	// ペンディンングインテント
	private final Intent mReadShuzoIntent = new Intent(INTENT_READ_SHUZO);


	private BroadcastReceiver mScreenOffReceiver = null;
	private BroadcastReceiver mScreenOnReceiver = null;

	// ウィジットについて
	class Widget{
		public int Id;
		public int Type;
		public Widget(int id,int type){
			Id = id;
			Type = type;
		}
	}
	class WidgetArray {

		private ArrayList<Widget> mArray = new ArrayList<Widget>();
		private HashMap<Integer,Integer> mWidgetMap = new HashMap<Integer,Integer>();

		public WidgetArray(){

		}
		public void put(Widget widget){
			Integer type = mWidgetMap.get(new Integer(widget.Id));
			if(type == null){
				mWidgetMap.put(new Integer(widget.Id), new Integer(widget.Type));
				mArray.add(widget);
				Log.d(TAG, "mArray.add "+ widget.Id +" "+widget.Type);
				Log.d(TAG,"mArray.size " + mArray.size());
			}
			
		}
		public Widget getRandom(){
			Random r = new Random();
			Widget widget = null;
			int size = mArray.size();
			if(size == 0){
				widget = null;
			}
			else if(size == 1){
				widget = mArray.get(0);
			}
			else {
				int index = Math.abs(r.nextInt()) % size;

				Log.d(TAG,"widgetArray index "+index+ " size of "+size);
				widget = mArray.get(index);

			}
			return widget;
		}
		public boolean isEmpty(){
			if(mArray.size() == 0){
				return true;
			}
			else {
				return false;
			}

		}
		public void remove(int widget){
			int size = mArray.size();
			for(int n=0; n < size; n++){
				Widget w = mArray.get(n);
				if(w.Id == widget){
					mArray.remove(n);
					mWidgetMap.remove(new Integer(widget));
					break;
				}
			}
		}
		public int size(){
			return mArray.size();
		}
	}
	private WidgetArray mWidgetArray = null;
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
	private ResponseList<Status> mResponselist;
	private int mListNum = 0;
	private TimelineTask mTimelineTask = null;

	// ハンドラ
	private final int MSG_WIDGET_UPDATE = 1;
	private final int MSG_WAIT_GETTIMELINE = 2;
	private final int MSG_SCREEN_OFF = 3;
	private final long WIFI_ENABLED_WAIT = 2000;
	private WaitHandler mHandler = new WaitHandler();



	// デバッグ用
	private String moji140 = "あ";

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
		// ConfigDataの読み込み
		if(mConfig == null){
			mConfig = new ConfigData();
			mConfig.getSharedPreferences(getApplicationContext());
		}
		// WidgetIdの保存先
		if(mWidgetArray == null){
			mWidgetArray = new WidgetArray();
		}

		String action;
		if (intent != null) {
			action = intent.getAction();

		} else {
			action = "";
		}
		// インテントの処理
		if(action.equals(INTENT_START)){
			int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			int widgetType = intent.getIntExtra(WIDGET_TYPE, 0);
			if(mWidgetArray == null){
				mWidgetArray = new WidgetArray();	
			}
			if(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID){
				Log.d(TAG,INTENT_START+" widgetId "+widgetId+ "widgetType "+ widgetType);
				mWidgetArray.put(new Widget(widgetId,widgetType));
			}

			if(mConfig.isAccessToken() == true ){
				if(mTwitter == null){
					mTwitter = getTwitterInstance();
				}
				if(mTwitter == null){
					updateStatusText(getString(R.string.twitter_null_message));
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
				Log.d(TAG,INTENT_START+ " mConfig null");
				return START_NOT_STICKY;
			}
		}
		else if(action.equals(INTENT_OAUTH)){
			String token = intent.getStringExtra(ConfigData.PF_ACCESSTOKEN);
			String secret = intent.getStringExtra(ConfigData.PF_ACCESSTOKENSECRET);
			mConfig.setAccessToken(token, secret);
			mConfig.CommitConfig();
			if(mWidgetArray.isEmpty() == false){
				getShuzoBot();
			}

		}
		else if(action.equals(INTENT_STOP)){
			Log.d(TAG,INTENT_STOP);
			int widgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			if(mWidgetArray.size() == 1){
				mWidgetArray.remove(widgetId);
				if(mTwitter != null){
					mTwitter.shutdown();
					mTwitter = null;
				}
				if(mHandler != null){
					mHandler.delete();
				}
				getApplicationContext().unregisterReceiver(mScreenOffReceiver);
				getApplicationContext().unregisterReceiver(mScreenOnReceiver);
				stopSelf();
				return START_NOT_STICKY;
			}
			else {
				mWidgetArray.remove(widgetId);
			}


		}
		else if(action.equals(INTEINT_UPDATE_CONFIG)){
			int order = getIntentExtra(intent);
			orderAction(order);

		}
		else if(action.equals(INTENT_READ_SHUZO)){
			if(mConfig.isAccessToken() == false){
				Intent i = new Intent(getApplicationContext(),ShuzoConfigActivity.class);
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(i);
			}
			else {
				if(mTwitter == null){
					mTwitter = getTwitterInstance();
				}
				int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
				int widgetType = intent.getIntExtra(WIDGET_TYPE, 0);
				Log.d(TAG,INTENT_READ_SHUZO + " widgetId "+ widgetId + "widgetType "+widgetType);
				if(widgetType != AppWidgetManager.INVALID_APPWIDGET_ID){
					Widget widget = new Widget(widgetId,widgetType);
					mWidgetArray.put(widget);
				}
				getShuzoBot();
			}
		}

		else if(action.equals(INTENT_WIDGET_UPDATE)){
			int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			int widgetType = intent.getIntExtra(WIDGET_TYPE, 0);
			Log.d(TAG,INTENT_READ_SHUZO + " widgetId "+ widgetId + "widgetType "+widgetType);
			if(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID){
				Widget widget = new Widget(widgetId,widgetType);
				mWidgetArray.put(widget);
			}
			if(mResponselist != null){
				actionWidgetUpdate();
			}
			else {
				getShuzoBot();
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
			Log.d(TAG,"ORDER_WIDGET_UPDATE " + mConfig.widgetUpdateTime);
			mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
			break;
		default:
			break;
		}
	}
	private void actionWidgetUpdate(){
		Log.d(TAG,"actionWidgetUpdate");
		// オブジェクトがnullの時はsynchronizedがエラーを吐くから
		if(mResponselist != null){
			synchronized(mResponselist){
				if(mListNum < mResponselist.size()){
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
			}
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

	private synchronized void actionGetTimelineUser(){
		if(mTimelineTask != null){
			if(mTimelineTask.getStatus() != AsyncTask.Status.FINISHED){
				return;
			}
			else {
				mTimelineTask = null;
			}
		}
		//showToast(getString(R.string.gettimeline_message));
		//updateStatusText(getString(R.string.gettimeline_message));
		//updateStatusText(moji140);
		mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
		mTimelineTask = new TimelineTask();
		mTimelineTask.setUser(mShuzoBot);
		mTimelineTask.execute(mTwitter);
	}
	private void cancelGetTimelineUser(){
		if(mTimelineTask != null){
			mTimelineTask.cancel(true);
		}
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

		Log.d(TAG,"Request wait set "+ time);
		if(time < 0){
			cancelGetTimelineUser();
		}
		else {
			long current = SystemClock.elapsedRealtime();
			current += time;

			final PendingIntent mReadShuzoSender = PendingIntent.getService(this,0, mReadShuzoIntent, 0);
			AlarmManager mAmWaitRequest =(AlarmManager)getSystemService(ALARM_SERVICE);
			mAmWaitRequest.cancel(mReadShuzoSender);
			mAmWaitRequest.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,current,mReadShuzoSender);
		}
	}
	private void cancelShuzoSender(){


		final PendingIntent mReadShuzoSender = PendingIntent.getService(this,0, mReadShuzoIntent, 0);
		AlarmManager mAmWaitRequest =(AlarmManager)getSystemService(ALARM_SERVICE);
		mAmWaitRequest.cancel(mReadShuzoSender);
	}
	private void showToast(String message){
		// 第3引数は、表示期間（LENGTH_SHORT、または、LENGTH_LONG）
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}


	private String splitStatusText(String status_text){
		int i;
		Log.d(TAG,"text: "+status_text);
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
		int index;
		if(mWidgetArray.isEmpty() == true){
			return ; // ウィジットが表示されていない
		}
		Widget appWidget = mWidgetArray.getRandom();

		Log.d(TAG,String.format("appWidgetId = %d Type = %d", appWidget.Id,appWidget.Type));

		// AppWidgetの画面更新
		RemoteViews remoteViews;
		if(appWidget.Type == WIDGET_TYPE_1){
			remoteViews = new RemoteViews(getPackageName(), R.layout.layoutwidget);
			remoteViews.setTextViewText(R.id.textView1, text);
		}
		else {
			remoteViews = new RemoteViews(getPackageName(), R.layout.layoutwidget144);
			remoteViews.setTextViewText(R.id.textView144, text);
		}

		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		manager.updateAppWidget(appWidget.Id, remoteViews);

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

				actionWidgetUpdate();

				mHandler.waitWidgetUpdate(mConfig.widgetUpdateTime);
				break;
			case MSG_WAIT_GETTIMELINE:
				Log.d(TAG,"MSG_WAIT_GETTIMELINE");
				if(mWidgetArray.isEmpty()==false){
					actionGetTimelineUser();
				}
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
		public void delete(){
			this.removeMessages(MSG_WAIT_GETTIMELINE);
			this.removeMessages(MSG_WIDGET_UPDATE); 
			this.removeMessages(MSG_WIDGET_UPDATE);
		}
	}
	@Override
	public void onCreate() {



		// Intent.ACTION_SCrEEN_ON/OFFはManifestに書いておいてもダメらしい
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		mScreenOnReceiver = new BroadcastReceiver (){
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG,intent.getAction());

			}
		};

		getApplicationContext().registerReceiver(mScreenOnReceiver,filter); 

		filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		mScreenOffReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG,intent.getAction());
				mHandler.scrrenOff();
			}
		};
		this.getApplicationContext().registerReceiver(mScreenOffReceiver ,filter);

		//		
		//		String[] a = {"あ","い","う","え","お"};
		//		int i=0;
		//		int j=0;
		//		for(i=0,j=0;i<140-2;i++){
		//			Log.d(TAG,"i="+i+" j="+j);
		//			moji140 += a[j];
		//			if(i != 0 && i%10 == 0){
		//				j++;
		//			}
		//			if(j >= 5){
		//				j=0;
		//			}
		//		}
		//		moji140+="は";
		super.onCreate();
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

				if(this.isCancelled() != true){
					list = mTaskTwitter.getUserTimeline(user,paging);
				}
				if(this.isCancelled() == true){
					list = null;
				}

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
				if(mResponselist == null){
					// 最初は同期を取らない
					mResponselist = result;
				}
				else {
					// 2回目以降は同期を取る
					// オブジェクトがnullだとエラーを吐くから
					synchronized(mResponselist){
						mResponselist = result;
					}
				}
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
			showToast(getString(R.string.gettimeline_message));
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			// TODO 自動生成されたメソッド・スタブ
			super.onProgressUpdate(values);
		}

	}

}
