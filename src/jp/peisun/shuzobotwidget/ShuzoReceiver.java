package jp.peisun.shuzobotwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;

public class ShuzoReceiver extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO 自動生成されたメソッド・スタブ
		String action = intent.getAction();
		if(action.equals(Intent.ACTION_BOOT_COMPLETED)){
			
		}
		else if(action.equals(Intent.ACTION_USER_PRESENT)){
			Log.d("Receiver",Intent.ACTION_USER_PRESENT);
			Intent i = new Intent(TwitterAccessService.INTENT_WIDGET_UPDATE);
			context.startService(i);
		}
		else if(action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
			Log.d("Receiver",WifiManager.WIFI_STATE_CHANGED_ACTION);
			
			int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
			int previous = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE,WifiManager.WIFI_STATE_UNKNOWN);
			Intent i = new Intent(TwitterAccessService.INTENT_WIFI_CHANGED);
			// 必要なのは、EXTRA_WIFI_STATEだけ
			i.putExtra(WifiManager.EXTRA_WIFI_STATE, state);
			context.startService(i);
		}
	}

}
