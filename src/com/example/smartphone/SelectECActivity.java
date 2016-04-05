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
	
	// Check if user exit without selecting any EC
	// In which case we should call DAN.shutdown()
	private boolean direct_exit;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        logging("================== SelectECActivity start ==================");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ec_list);

        DAN.init(Constants.log_tag);
        direct_exit = true;
        if (DAN.session_status()) {
        	logging("Already registered, jump to SessionActivity");
            Intent intent = new Intent(SelectECActivity.this, SessionActivity.class);
            startActivity(intent);
            direct_exit = false;
            finish();
        }
        
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
                Intent intent = new Intent(SelectECActivity.this, SessionActivity.class);
                intent.putExtra("EC_ENDPOINT", ec_endpoint_list.get(position));
                startActivity(intent);
                direct_exit = false;
                finish();
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
    		if (direct_exit) {
    			DAN.shutdown();
    		}
    	}
    }
    
    static public void logging (String message) {
        Log.i(Constants.log_tag, "[SelectECActivity] " + message);
    }

}
