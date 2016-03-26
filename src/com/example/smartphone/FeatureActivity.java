package com.example.smartphone;

import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

public class FeatureActivity extends Activity {
	static private DAN.Subscriber ec_status_handler;
    static private final int NOTIFICATION_ID = 1;

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
        		DAN.shutdown();
        		remove_all_notification();
                finish();
            }
        });
        
        // initialize DAN
        DAN.init(C.dm_name);
        
        ec_status_handler = new DAN.Subscriber () {
    	    public void odf_handler (DAN.ODFObject odf_object) {
    	        switch (odf_object.event_tag) {
    	        case REGISTER_FAILED:
    	        	show_ec_status_on_ui(odf_object.message, odf_object.event_tag);
    	        	show_ec_status_on_notification(odf_object.message, false);
    	        	break;
    	        	
    	        case REGISTER_SUCCEED:
    	        	show_ec_status_on_ui(odf_object.message, odf_object.event_tag);
    	        	show_ec_status_on_notification(odf_object.message, true);
    	        	String d_name = DAN.get_d_name();
    	        	logging("Get d_name:"+ d_name);
    				TextView tv_d_name = (TextView)findViewById(R.id.tv_d_name);
    				tv_d_name.setText(d_name);
    				break;
    	        }
    	    }
    	};
    	DAN.subscribe("Control_channel", ec_status_handler);
        
        JSONObject profile = new JSONObject();
        try {
	        profile.put("d_name", "Android"+ DAN.get_clean_mac_addr(get_mac_addr()));
	        profile.put("dm_name", C.dm_name);
	        JSONArray feature_list = new JSONArray();
	        for (String f: C.df_list) {
	        	feature_list.put(f);
	        }
	        profile.put("df_list", feature_list);
	        profile.put("u_name", C.u_name);
	        profile.put("monitor", DAN.get_clean_mac_addr(get_mac_addr()));
	        DAN.register(DAN.get_d_id(get_mac_addr()), profile);
		} catch (JSONException e) {
			e.printStackTrace();
		}
    	
    	String d_name = DAN.get_d_name();
    	logging("Get d_name: "+ d_name);
		TextView tv_d_name = (TextView)findViewById(R.id.tv_d_name);
		tv_d_name.setText(d_name);

    }
    
    public void show_ec_status_on_ui (String host, DAN.EventTag ec_status) {
		((TextView)findViewById(R.id.tv_ec_host_address)).setText(host);
		TextView tv_ec_host_status = (TextView)findViewById(R.id.tv_ec_host_status);
		String status_text = "";
		int status_color = Color.rgb(0, 0, 0);
		switch (ec_status) {
		case REGISTER_FAILED:
			status_text = "!";
			status_color = Color.rgb(128, 0, 0);
			break;
			
		case REGISTER_SUCCEED:
			status_text = "~";
			status_color = Color.rgb(0, 128, 0);
			break;
		}
		tv_ec_host_status.setText(status_text);
		tv_ec_host_status.setTextColor(status_color);

    }
    
    private void show_ec_status_on_notification (String host, boolean status) {
        String text = status ? host : "Connecting";
        NotificationManager notification_manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notification_builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(C.dm_name)
                        .setContentText(text);

        PendingIntent pending_intent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        notification_builder.setContentIntent(pending_intent);
        notification_manager.notify(NOTIFICATION_ID, notification_builder.build());
    }
    
    private void remove_all_notification () {
    	NotificationManager notification_manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    	notification_manager.cancelAll();
    }

    public String get_mac_addr () {
        logging("get_mac_addr()");

        // Generate error mac address
        final Random rn = new Random();
        String ret = "E2202";
        for (int i = 0; i < 7; i++) {
        	ret += "0123456789ABCDEF".charAt(rn.nextInt(16));
        }

        WifiManager wifiMan = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        if (wifiMan == null) {
            logging("Cannot get WiFiManager system service");
            return ret;
        }

        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        if (wifiInf == null) {
            logging("Cannot get connection info");
            return ret;
        }

        return wifiInf.getMacAddress();
    }
    
    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        // to prevent "screen rotate caused onCreate being called again"
    }
    
    @Override
    public void onPause () {
    	super.onPause();
    	if (isFinishing()) {
    		DAN.unsubcribe(ec_status_handler);
    	}
    }
    
    public void logging (String message) {
        Log.i(C.log_tag, "[FeatureActivity] " + message);
    }
	
}