package jp.peisun.shuzobotwidget;


import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;


public class ShuzoConfigActivity extends PreferenceActivity {
	private boolean mWifiOnly = false;
	private Preference.OnPreferenceChangeListener  onPreferenceChangeListener_wifi =
		new OnPreferenceChangeListener(){
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			
			boolean check = ((Boolean)newValue).booleanValue();
			
			if (check != mWifiOnly ) {
				mWifiOnly = check;
			}
	        // 変更を適用するために true を返す  
	        return true;
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO 自動生成されたメソッド・スタブ
		super.onCreate(savedInstanceState);
	}

}
