package com.example.smartphone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.smartphone.DAN.Tag;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

public class FeatureActivity extends Activity {
	static public Handler ec_status_handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_features);

        final ToggleButton btn_gsensor = (ToggleButton) findViewById(R.id.btn_accelerometer);
        btn_gsensor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    logging("Request GSensorService start");
                    Intent intent = new Intent (FeatureActivity.this, AccelerometerService.class);
                    getApplicationContext().startService(intent);
                    
                } else {
                    logging("Request GSensorService stop");
                    getApplicationContext().stopService(new Intent(FeatureActivity.this, AccelerometerService.class));
                    
                }
            }
        });
        
        if ( !AccelerometerService.is_running() ) {
        	btn_gsensor.setChecked(false);
            
        } else {
        	btn_gsensor.setChecked(true);
            
        }
        
        final ToggleButton btn_mic = (ToggleButton) findViewById(R.id.btn_mic);
        btn_mic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    logging("Request MicService start");
                    Intent intent = new Intent (FeatureActivity.this, MicService.class);
                    getApplicationContext().startService(intent);
                    
                } else {
                    logging("Request MicService stop");
                    getApplicationContext().stopService(new Intent(FeatureActivity.this, MicService.class));
                    
                }
            }
        });
        
        if ( !MicService.is_running() ) {
        	btn_mic.setChecked(false);
            
        } else {
        	btn_mic.setChecked(true);
            
        }
        
        final ToggleButton btn_speaker = (ToggleButton) findViewById(R.id.btn_speaker);
        btn_speaker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    logging("Request SpeakerService start");
                    Intent intent = new Intent (FeatureActivity.this, SpeakerService.class);
                    getApplicationContext().startService(intent);
                    
                } else {
                    logging("Request SpeakerService stop");
                    getApplicationContext().stopService(new Intent(FeatureActivity.this, SpeakerService.class));
                    
                }
            }
        });
        
        if ( !SpeakerService.is_running() ) {
        	btn_speaker.setChecked(false);
            
        } else {
        	btn_speaker.setChecked(true);
            
        }
        
        Button btn_detach = (Button)findViewById(R.id.btn_deregister);
        btn_detach.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                logging("Request GSensorService stop");
                getApplicationContext().stopService(new Intent(FeatureActivity.this, AccelerometerService.class));
                logging("Request MicService stop");
                getApplicationContext().stopService(new Intent(FeatureActivity.this, MicService.class));
                logging("Request SpeakerService stop");
                getApplicationContext().stopService(new Intent(FeatureActivity.this, SpeakerService.class));

        		DAN.deregister();
                finish();
            }
        });
        
        // start EasyConnect Service
        DAN.init(this, C.dm_name);
        
        ec_status_handler = new Handler () {
    	    public void handleMessage (Message msg) {
    	        switch ((DAN.Tag)msg.getData().get("tag")) {
    	        case REGISTER_TRYING:
    	        	show_ec_status((DAN.Tag)msg.getData().get("tag"), msg.getData().getString("message"));
    	        	break;
    	        	
    	        case REGISTER_FAILED:
    	        	show_ec_status((DAN.Tag)msg.getData().get("tag"), msg.getData().getString("message"));
    	        	break;
    	        	
    	        case REGISTER_SUCCESSED:
    	        	show_ec_status((DAN.Tag)msg.getData().get("tag"), msg.getData().getString("message"));
    	        	String d_name = DAN.get_d_name();
    	        	logging("Get d_name:"+ d_name);
    				TextView tv_d_name = (TextView)findViewById(R.id.tv_d_name);
    				tv_d_name.setText(d_name);
    				break;
    	        }
    	    }
    	};
    	DAN.subscribe_message(ec_status_handler);
        
        JSONObject profile = new JSONObject();
        try {
	        profile.put("d_name", "Android"+ DAN.get_mac_addr());
	        profile.put("dm_name", C.dm_name);
	        JSONArray feature_list = new JSONArray();
	        for (String f: C.df_list) {
	        	feature_list.put(f);
	        }
	        profile.put("df_list", feature_list);
	        profile.put("u_name", C.u_name);
	        profile.put("monitor", DAN.get_mac_addr());
	        DAN.register(DAN.get_d_id(DAN.get_mac_addr()), profile);
		} catch (JSONException e) {
			e.printStackTrace();
		}
    	
    	String d_name = DAN.get_d_name();
    	logging("Get d_name:"+ d_name);
		TextView tv_d_name = (TextView)findViewById(R.id.tv_d_name);
		tv_d_name.setText(d_name);

    }
    
    public void show_ec_status (DAN.Tag t, String host) {
		((TextView)findViewById(R.id.tv_ec_host_address)).setText(host);
		TextView tv_ec_host_status = (TextView)findViewById(R.id.tv_ec_host_status);
		switch (t) {
		case REGISTER_TRYING:
			tv_ec_host_status.setText("...");
			tv_ec_host_status.setTextColor(Color.rgb(128, 0, 0));
			break;
		
		case REGISTER_FAILED:
			tv_ec_host_status.setText("!");
			tv_ec_host_status.setTextColor(Color.rgb(128, 0, 0));
			break;
			
		case REGISTER_SUCCESSED:
			tv_ec_host_status.setText("~");
			tv_ec_host_status.setTextColor(Color.rgb(0, 128, 0));
			break;
			
		}

    }
    
    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        // to prevent "screen rotate caused onCreate being called again"
    }
    
    @Override
    public void onPause () {
    	super.onPause();
    	if (isFinishing()) {
        	DAN.deregister(ec_status_handler);
    	}
    }
    
    public void logging (String message) {
        Log.i(C.log_tag, "[FeatureActivity] " + message);
    }
	
}