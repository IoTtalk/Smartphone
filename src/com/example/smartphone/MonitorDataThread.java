package com.example.smartphone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.smartphone.HttpRequest.HttpResponse;

import android.os.Handler;
import android.util.Log;

class MonitorDataThread extends Thread {
	
	/*
    {   <d_name>: {
            <feature> : <data>,
            <feature> : <data>,
            ...
        },
		...
    }
    */
	
	static public JSONObject newest_raw_data;
	static public Handler device_list_handler = null;
	static boolean work_permission = true;
	
	static public void set_device_list_handler (Handler h) {
		device_list_handler = h;
	}
	
	static public void remove_device_list_handler () {
		device_list_handler = null;
	}
	
	public void run () {
		work_permission = true;
		logging("MonitorDataThread starts");
		
		newest_raw_data = new JSONObject();
		
		while (work_permission) {
			try {
				Object tmp = EasyConnect.pull_data("Display").getJSONArray("data").get(0);
				if ( !tmp.equals(null) ) {
					if (tmp instanceof JSONArray) {
						// many wrappings
						newest_raw_data = ((JSONArray)tmp).getJSONObject(0);
					} else {
						newest_raw_data = (JSONObject)tmp;
					}
					
					if (device_list_handler != null) {
					    device_list_handler.sendEmptyMessage(0);
					}
					
					logging(newest_raw_data.toString());
					
				} else {
					logging("Got null value");
				}
				
			} catch (JSONException e) {
				e.printStackTrace();
				newest_raw_data = new JSONObject();
			} catch (ClassCastException e) {
				logging("ClassCastException");
				e.printStackTrace();
			}

			try {
				Thread.sleep(300);
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		
		logging("MonitorDataThread stops");
		
	}
    
    public void logging (String message) {
        Log.i(C.log_tag, "[MonitorData] " + message);
    }
	
}