package com.example.smartphone;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import DAN.DAN;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class SelectECActivity extends Activity {
	final ArrayList<String> ec_endpoint_list = new ArrayList<String>();
    ArrayAdapter<String> adapter;
	final DAN.Subscriber event_subscriber = new EventSubscriber();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        logging("================== SelectECActivity start ==================");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ec_list);

        DAN.init(Constants.log_tag);
        if (DAN.session_status()) {
        	logging("Already registered, jump to SessionActivity");
            Intent intent = new Intent(SelectECActivity.this, FeatureActivity.class);
            startActivity(intent);
            finish();
        }
        
        adapter = new ArrayAdapter<String>(
        		this, android.R.layout.simple_list_item_1, android.R.id.text1, ec_endpoint_list);
        for (String i: DAN.available_ec()) {
        	ec_endpoint_list.add(i);
        }
		adapter.notifyDataSetChanged();

        // show available EC ENDPOINTS
        final ListView lv_available_ec_endpoints = (ListView)findViewById(R.id.lv_available_ec_endpoints);
        lv_available_ec_endpoints.setAdapter(adapter);
        lv_available_ec_endpoints.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                    View view, int position, long id) {
            	String clean_mac_addr = DAN.get_clean_mac_addr(Utils.get_mac_addr(SelectECActivity.this));
            	String EC_ENDPOINT = ec_endpoint_list.get(position);
            	JSONObject profile = new JSONObject();
    	        try {
    		        profile.put("d_name", "Android"+ clean_mac_addr);
    		        profile.put("dm_name", Constants.dm_name);
    		        JSONArray feature_list = new JSONArray();
    		        for (String f: Constants.df_list) {
    		        	feature_list.put(f);
    		        }
    		        profile.put("df_list", feature_list);
    		        profile.put("u_name", Constants.u_name);
    		        profile.put("monitor", clean_mac_addr);
    	        	DAN.register(EC_ENDPOINT, clean_mac_addr, profile);
    			} catch (JSONException e) {
    				e.printStackTrace();
    			}
    	        Utils.show_ec_status_on_notification(SelectECActivity.this, EC_ENDPOINT, false);
            }
        });
        
    	DAN.subscribe("Control_channel", event_subscriber);
    }
    
    class EventSubscriber extends DAN.Subscriber {
	    public void odf_handler (final DAN.ODFObject odf_object) {
	    	switch (odf_object.event_tag) {
			case FOUND_NEW_EC:
				logging("FOUND_NEW_EC: "+ odf_object.message);
				ec_endpoint_list.add(odf_object.message);
    	    	runOnUiThread(new Thread () {
    	    		@Override
    	    		public void run () {
    	    			adapter.notifyDataSetChanged();
    	    		}
    	    	});
				break;
			case REGISTER_FAILED:
				runOnUiThread(new Thread () {
    	    		@Override
    	    		public void run () {
    					Toast.makeText(getApplicationContext(), "Register failed.", Toast.LENGTH_LONG).show();
    	    		}
    	    	});
    	        Utils.remove_all_notification(SelectECActivity.this);
				break;
			case REGISTER_SUCCEED:
    	        Utils.show_ec_status_on_notification(SelectECActivity.this, DAN.ec_endpoint(), true);
	            Intent intent = new Intent(SelectECActivity.this, FeatureActivity.class);
	            startActivity(intent);
	            finish();
				break;
			default:
				break;
	    	}
	    }
	}
    
    @Override
    public void onPause () {
    	super.onPause();
    	if (isFinishing()) {
    		DAN.unsubcribe(event_subscriber);
    		if (!DAN.session_status()) {
    	        Utils.remove_all_notification(SelectECActivity.this);
    			DAN.shutdown();
    		}
    	}
    }
    
    static public void logging (String message) {
        Log.i(Constants.log_tag, "[SelectECActivity] " + message);
    }

}
