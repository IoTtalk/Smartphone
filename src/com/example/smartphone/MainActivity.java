package com.example.smartphone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.WindowManager;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

public class MainActivity extends TabActivity {
	final int NOTIFICATION_ID = 1;
	static final String version = "20151127";
	
	static MainActivity self;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        logging("=============================================");
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
	        profile.put("d_name", "Android"+ EasyConnect.get_mac_addr());
	        profile.put("dm_name", C.dm_name);
	        JSONArray feature_list = new JSONArray();
	        for (String f: C.df_list) {
	        	feature_list.put(f);
	        }
	        profile.put("df_list", feature_list);
	        profile.put("u_name", C.u_name);
	        profile.put("monitor", EasyConnect.get_mac_addr());
	        EasyConnect.attach(EasyConnect.get_d_id(EasyConnect.get_mac_addr()), profile);
		} catch (JSONException e) {
			e.printStackTrace();
		}
    }
    
    public void end () {
		EasyConnect.detach();
        finish();
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        int MENU_ITEM_ID_API_VERSION = 0;
        int MENU_ITEM_ID_DA_VERSION = 1;
        menu.add(0, MENU_ITEM_ID_API_VERSION, 0, "API Version: "+ EasyConnect.version);
        menu.add(0, MENU_ITEM_ID_DA_VERSION, 0, "DA Version: "+ MainActivity.version);
        return super.onPrepareOptionsMenu(menu);
    }
    
    static public void logging (String message) {
        Log.i(C.log_tag, "[MainActivity] " + message);
    }
}
