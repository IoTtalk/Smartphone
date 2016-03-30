package com.example.smartphone;

import java.util.ArrayList;

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

public class SelectECActivity extends Activity {
	private DAN.Subscriber ec_status_handler;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ec_list);

        DAN.init(C.log_tag);
        
        // lv_available_ec_endpoints is used to display available EC ENDPOINTS
        final ListView lv_available_ec_endpoints = (ListView)findViewById(R.id.lv_available_ec_endpoints);
        final ArrayList<String> ec_endpoint_list = new ArrayList<String>();
        for (String i: DAN.available_ec()) {
        	ec_endpoint_list.add(i);
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
    	    this, android.R.layout.simple_list_item_1, android.R.id.text1, ec_endpoint_list);
        lv_available_ec_endpoints.setAdapter(adapter);
        lv_available_ec_endpoints.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                    View view, int position, long id) {
                Intent intent = new Intent(SelectECActivity.this, MainActivity.class);
                intent.putExtra("EC_ENDPOINT", ec_endpoint_list.get(position));
                startActivity(intent);
            }
        });
        
        ec_status_handler = new DAN.Subscriber () {
    	    public void odf_handler (final DAN.ODFObject odf_object) {
    	    	if (odf_object.event_tag == DAN.EventTag.FOUND_NEW_EC) {
					logging("FOUND_NEW_EC: "+ odf_object.message);
					ec_endpoint_list.add(odf_object.message);
					
	    	    	runOnUiThread(new Thread () {
	    	    		@Override
	    	    		public void run () {
	    	    			adapter.notifyDataSetChanged();
	    	    		}
	    	    	});
    	        }
    	    }
    	};
    	DAN.subscribe("Control_channel", ec_status_handler);
        
    }
    
    @Override
    public void onPause () {
    	super.onPause();
    	if (isFinishing()) {
    		DAN.unsubcribe(ec_status_handler);
    		if (!DAN.session_status()) {
    			DAN.shutdown();
    		}
    	}
    }
    
    static public void logging (String message) {
        Log.i(C.log_tag, "[SelectECActivity] " + message);
    }

}
