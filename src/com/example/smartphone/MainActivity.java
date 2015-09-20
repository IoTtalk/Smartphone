package com.example.smartphone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

public class MainActivity extends TabActivity {
	final int NOTIFICATION_ID = 1;
	
	static MainActivity self;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	self = this;
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        TabHost tabHost = (TabHost)findViewById(android.R.id.tabhost);
        TabSpec tabspec;
        
        tabspec = tabHost.newTabSpec("tab-features");
        tabspec.setIndicator("Features");
        tabspec.setContent(new Intent(this, FeatureActivity.class));
        tabHost.addTab(tabspec);
        
        tabspec = tabHost.newTabSpec("tab-monitor");
        tabspec.setIndicator("Monitor");
        tabspec.setContent(new Intent(this, MonitorDeviceListActivity.class));
        tabHost.addTab(tabspec);
        
        // start EasyConnect Service
        EasyConnect.start(this, C.dm_name);
        
        JSONObject profile = new JSONObject();
        try {
			profile.put("d_id", EasyConnect.get_d_id());
	        profile.put("d_name", "Android"+ EasyConnect.get_mac_addr());
	        profile.put("dm_name", C.dm_name);
	        JSONArray feature_list = new JSONArray();
	        for (String f: C.df_list) {
	        	feature_list.put(f);
	        }
	        profile.put("features", feature_list);
	        profile.put("u_name", C.u_name);
	        profile.put("monitor", EasyConnect.get_mac_addr());
	        EasyConnect.attach(profile);
		} catch (JSONException e) {
			e.printStackTrace();
		}
    }
    
    public void end () {
        new DetachThread().start();
        NotificationManager notification_manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notification_manager.cancelAll();
        finish();
    }
    
    @Override
    public void onDestroy () {
        super.onDestroy();
		MonitorDataThread.work_permission = false;
    }
    
    static private class DetachThread extends Thread {
    	public void run () {
    		EasyConnect.detach();
	        EasyConnect.reset_ec_host();
            logging("Detached from EasyConnect");
    	}
    }
    
    static public void logging (String message) {
        Log.i(C.log_tag, "[MainActivity] " + message);
    }
}
