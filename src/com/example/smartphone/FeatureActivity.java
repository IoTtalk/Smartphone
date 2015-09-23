package com.example.smartphone;

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
        Log.e(C.log_tag, "create session filter");

        final ToggleButton btn_gsensor = (ToggleButton) findViewById(R.id.btn_gsensor);
        btn_gsensor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    logging("Request GSensorService start");
                    Intent intent = new Intent (FeatureActivity.this, GSensorService.class);
                    getApplicationContext().startService(intent);
                    
                } else {
                    logging("Request GSensorService stop");
                    getApplicationContext().stopService(new Intent(FeatureActivity.this, GSensorService.class));
                    
                }
            }
        });
        
        if ( !GSensorService.is_running() ) {
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
        
        Button btn_detach = (Button)findViewById(R.id.btn_detach);
        btn_detach.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                logging("Request GSensorService stop");
                getApplicationContext().stopService(new Intent(FeatureActivity.this, GSensorService.class));
                logging("Request MicService stop");
                getApplicationContext().stopService(new Intent(FeatureActivity.this, MicService.class));
                logging("Request SpeakerService stop");
                getApplicationContext().stopService(new Intent(FeatureActivity.this, SpeakerService.class));
                
                MainActivity.self.end();
            }
        });
        
        ec_status_handler = new Handler () {
    	    public void handleMessage (Message msg) {
    	        int tag = msg.getData().getInt("tag");
    	        if (tag == EasyConnect.ATTACH_SUCCESS) {
    	        	String host = msg.getData().getString("message");
    				((TextView)findViewById(R.id.tv_ec_host_address)).setText(host);
    				TextView tv_ec_host_status = (TextView)findViewById(R.id.tv_ec_host_status);
    				tv_ec_host_status.setText("~");
					tv_ec_host_status.setTextColor(Color.rgb(0, 128, 0));
    				
    	        } else if (tag == EasyConnect.D_NAME_GENEREATED) {
    	        	String d_name = msg.getData().getString("message");
    	        	logging("Get d_name:"+ d_name);
    				TextView tv_d_name = (TextView)findViewById(R.id.tv_d_name);
    				tv_d_name.setText(d_name);
    				
    	        }
    	    }
    	};
    	EasyConnect.register(ec_status_handler);

    }
    
    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        // to prevent "screen rotate caused onCreate being called again"
    }
    
    @Override
    public void onDestroy () {
        super.onDestroy();
    	EasyConnect.deregister(ec_status_handler);
        
    }
    
    public void logging (String message) {
        Log.i(C.log_tag, "[FeatureActivity] " + message);
    }
	
}