package jp.peisun.shuzobotwidget;


import java.io.File;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

public class ShuzobotAppWidgetProvider extends AppWidgetProvider {
	private final static String TAG = "ShuzobotAppWidgetProvider";
	public final static String INTENT_STATUS = "jp.peisun.shuzobotwidget.status";
	public final static String TWEET_STATUS = "status";
	private String mTweet = null;
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		// TODO 自動生成されたメソッド・スタブ
		final int N = appWidgetIds.length;
        for (int i=0; i<N; i++) {
        	int appWidgetId = appWidgetIds[i];
        	Intent intent = new Intent(TwitterAccessService.INTENT_STOP);
        	intent.putExtra(TwitterAccessService.INTENT_STOP, appWidgetId);
        	context.startService(intent);
        }
		super.onDeleted(context, appWidgetIds);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO 自動生成されたメソッド・スタブ

		super.onReceive(context, intent);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		// TODO 自動生成されたメソッド・スタブ
		final int N = appWidgetIds.length;
        for (int i=0; i<N; i++) {
        	RemoteViews views = new RemoteViews(context.getPackageName(),
        			R.layout.layoutwidget);
        	if(mTweet != null){
        		views.setTextViewText(R.id.textView1, mTweet);
        	}
        	
//        	views.setImageViewResource(R.id.imageView1, R.drawable.fukidasi001);
//        	ComponentName cn = new ComponentName(context, ShuzobotAppWidgetProvider.class);
        	int appWidgetId = appWidgetIds[i];
        	
        	Intent intent = new Intent(TwitterAccessService.INTENT_START);
        	intent.putExtra(TwitterAccessService.WIDGET_ID, appWidgetId);
        	context.startService(intent);
        	
        	appWidgetManager.updateAppWidget(appWidgetId, views);
        	Log.d(TAG, "updateAppWidget appWidgetId=" + appWidgetId );
		
        }

		
		
	}

	@Override
	public void onDisabled(Context context) {
		// TODO 自動生成されたメソッド・スタブ
		super.onDisabled(context);
	}

	@Override
	public void onEnabled(Context context) {
		// TODO 自動生成されたメソッド・スタブ
		
		
		super.onEnabled(context);
	}
	public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, String text) {
        Log.d(TAG, "updateAppWidget appWidgetId=" + appWidgetId + " text=" + text);
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.layoutwidget);
		remoteViews.setTextViewText(R.id.textView1, text);
		//remoteViews.setImageViewResource(R.id.imageView1,R.drawable.icon01);
		//remoteViews.setImageViewResource(R.id.imageView2, R.drawable.fukidasi001);
		// AppWidgetの画面更新
		ComponentName thisWidget = new ComponentName(context, ShuzobotAppWidgetProvider.class);
		AppWidgetManager manager = AppWidgetManager.getInstance(context);
		manager.updateAppWidget(thisWidget, remoteViews);
    }
}
