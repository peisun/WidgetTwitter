package jp.peisun.shuzobotwidget;



import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class ShuzobotAppWidgetProvider23 extends AppWidgetProvider {
	private final static String TAG = "ShuzobotAppWidgetProvider";
	
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
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		// TODO 自動生成されたメソッド・スタブ
		final int N = appWidgetIds.length;
        for (int i=0; i<N; i++) {
        	RemoteViews views = new RemoteViews(context.getPackageName(),
        			R.layout.layoutwidget144);
        	if(mTweet != null){
        		views.setTextViewText(R.id.textView144, mTweet);
        	}
        	
//        	views.setImageViewResource(R.id.imageView1, R.drawable.fukidasi001);
//        	ComponentName cn = new ComponentName(context, ShuzobotAppWidgetProvider.class);
        	int appWidgetId = appWidgetIds[i];
        	Intent mWidgetupdateIntent = new Intent(context,TwitterAccessService.class);
        	mWidgetupdateIntent.setAction(TwitterAccessService.INTENT_WIDGET_UPDATE);
        	mWidgetupdateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        	mWidgetupdateIntent.putExtra(TwitterAccessService.WIDGET_TYPE, TwitterAccessService.WIDGET_TYPE_2);
        	
        	Intent mReadShuzoIntent = new Intent(context,TwitterAccessService.class);
        	mReadShuzoIntent.setAction(TwitterAccessService.INTENT_READ_SHUZO);
        	mReadShuzoIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        	mReadShuzoIntent.putExtra(TwitterAccessService.WIDGET_TYPE, TwitterAccessService.WIDGET_TYPE_2);
        	
        	
    		PendingIntent WidgetUpdatePendigIntent = PendingIntent.getService(context, appWidgetId, mWidgetupdateIntent, 0);
    		PendingIntent ReadShuzoPendingIntent= PendingIntent.getService(context,appWidgetId, mReadShuzoIntent, 0);;

    		views.setOnClickPendingIntent(R.id.imageView144_icon, ReadShuzoPendingIntent);
			views.setOnClickPendingIntent(R.id.relativeLayout144, WidgetUpdatePendigIntent);
        	

        	appWidgetManager.updateAppWidget(appWidgetId, views);
        	
        	Intent intent = new Intent(TwitterAccessService.INTENT_START);
        	intent.putExtra(TwitterAccessService.WIDGET_ID, appWidgetId);
        	intent.putExtra(TwitterAccessService.WIDGET_TYPE, TwitterAccessService.WIDGET_TYPE_2);
        	context.startService(intent);
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
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.layoutwidget144);
		remoteViews.setTextViewText(R.id.textView144, text);
		//remoteViews.setImageViewResource(R.id.imageView1,R.drawable.icon01);
		//remoteViews.setImageViewResource(R.id.imageView2, R.drawable.fukidasi001);
		// AppWidgetの画面更新
		ComponentName thisWidget = new ComponentName(context, ShuzobotAppWidgetProvider23.class);
		AppWidgetManager manager = AppWidgetManager.getInstance(context);
		manager.updateAppWidget(thisWidget, remoteViews);
    }
}
