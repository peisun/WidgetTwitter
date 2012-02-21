package jp.peisun.shuzobotwidget;


import java.io.File;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class ShuzobotAppWidgetProvider extends AppWidgetProvider {
	public final static String INTENT_STATUS = "jp.peisun.shuzobotwidget.status";
	public final static String TWEET_STATUS = "status";
	private String mTweet = null;
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		// TODO 自動生成されたメソッド・スタブ
		Intent i = new Intent(TwitterAccessService.INTENT_STOP);
		context.startService(i);
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
		
		// TODO 自動生成されたメソッド・スタブ
		
		RemoteViews views = new RemoteViews(context.getPackageName(),
				R.layout.layoutwidget);
		if(mTweet != null){
			views.setTextViewText(R.id.textView1, mTweet);
		}
		//views.setImageViewResource(R.id.imageView1, R.drawable.wedget_icon01);
		ComponentName cn = new ComponentName(context, ShuzobotAppWidgetProvider.class);
		appWidgetManager.updateAppWidget(cn, views);

		//super.onUpdate(context, appWidgetManager, appWidgetIds);
		
	}

	@Override
	public void onDisabled(Context context) {
		// TODO 自動生成されたメソッド・スタブ
		super.onDisabled(context);
	}

	@Override
	public void onEnabled(Context context) {
		// TODO 自動生成されたメソッド・スタブ
		String filepath = context.getFilesDir().getAbsolutePath() + "/" +  TwitterAccessService.ACCESS_TOKEN;
		File file = new File(filepath);
		if(file.exists() == true){
			mTweet = context.getString(R.string.init_message);
			Intent i = new Intent(TwitterAccessService.INTENT_READ_SHUZO);
			context.startService(i);
		}
		else {
			mTweet = context.getString(R.string.signin_message);
		}
		
		super.onEnabled(context);
	}

}
