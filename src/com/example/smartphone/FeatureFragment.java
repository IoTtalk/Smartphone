package com.example.smartphone;

import DAN.DAN;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

public class FeatureFragment extends Fragment {
	View root_view;
	DeregisterCallback deregister_callback;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        root_view = inflater.inflate(R.layout.frag_feature_switch, container, false);
        
        final ToggleButton btn_asensor = (ToggleButton) root_view.findViewById(R.id.btn_accelerometer);
    	btn_asensor.setChecked(AccelerometerService.is_running());
        btn_asensor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    logging("Request AccelerometerService to start");
                    getActivity().getApplicationContext().startService(new Intent(getActivity(), AccelerometerService.class));
                } else {
                    logging("Request AccelerometerService to stop");
                    getActivity().getApplicationContext().stopService(new Intent(getActivity(), AccelerometerService.class));
                }
            }
        });
        
        final ToggleButton btn_mic = (ToggleButton) root_view.findViewById(R.id.btn_mic);
        btn_mic.setChecked(MicService.is_running());
        btn_mic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    logging("Request MicService to start");
                    getActivity().getApplicationContext().startService(new Intent(getActivity(), MicService.class));
                    
                } else {
                    logging("Request MicService to stop");
                    getActivity().getApplicationContext().stopService(new Intent(getActivity(), MicService.class));
                    
                }
            }
        });
        
        final ToggleButton btn_speaker = (ToggleButton) root_view.findViewById(R.id.btn_speaker);
        btn_speaker.setChecked(SpeakerService.is_running());
        btn_speaker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    logging("Request SpeakerService to start");
                    getActivity().getApplicationContext().startService(new Intent(getActivity(), SpeakerService.class));
                    
                } else {
                    logging("Request SpeakerService to stop");
                    getActivity().getApplicationContext().stopService(new Intent(getActivity(), SpeakerService.class));
                    
                }
            }
        });
        
        final Button btn_deregister = (Button) root_view.findViewById(R.id.btn_deregister);
        btn_deregister.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                logging("Request AccelerometerService to stop");
                getActivity().getApplicationContext().stopService(new Intent(getActivity(), AccelerometerService.class));
                logging("Request MicService to stop");
                getActivity().getApplicationContext().stopService(new Intent(getActivity(), MicService.class));
                logging("Request SpeakerService to stop");
                getActivity().getApplicationContext().stopService(new Intent(getActivity(), SpeakerService.class));
                if (deregister_callback != null) {
                	deregister_callback.trigger();
                }
            }
        });
        
        show_wifi_ssid_on_ui();
        
        return root_view;
    }
    
    public interface DeregisterCallback {
    	public void trigger ();
    }
	
	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
        	deregister_callback = (DeregisterCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DeregisterCallback");
        }
    }
	
    public void show_ec_status_on_ui (String host, boolean ec_status) {
		((TextView) root_view.findViewById(R.id.tv_ec_host_address)).setText(host);
		TextView tv_ec_host_status = (TextView) root_view.findViewById(R.id.tv_ec_host_status);
		String status_text = "";
		int status_color = Color.rgb(0, 0, 0);
		if (ec_status) {
			status_text = "~";
			status_color = Color.rgb(0, 128, 0);
		} else {
			status_text = "!";
			status_color = Color.rgb(128, 0, 0);
		}
		tv_ec_host_status.setText(status_text);
		tv_ec_host_status.setTextColor(status_color);
    }
    
    public void show_d_name_on_ui (String d_name) {
		((TextView)root_view.findViewById(R.id.tv_d_name)).setText(DAN.get_d_name());
    }
    
    public void show_wifi_ssid_on_ui () {
        final WifiManager wifiManager = (WifiManager) getActivity().getSystemService("wifi");
        final WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    	((TextView) root_view.findViewById(R.id.tv_wifi_ssid)).setText(wifiInfo.getSSID());
    }
    
    static public void logging (String message) {
        Log.i(Constants.log_tag, "[FeatureFragment] " + message);
    }
}