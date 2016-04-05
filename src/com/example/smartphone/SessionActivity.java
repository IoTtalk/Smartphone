package com.example.smartphone;

import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import DAN.DAN;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

public class SessionActivity extends Activity implements FeatureFragment.DeregisterCallback {
	final String version = "20160405";

    final int NOTIFICATION_ID = 1;
    
    final int MENU_ITEM_ID_DAN_VERSION = 0;
    final int MENU_ITEM_ID_DAI_VERSION = 1;
    final int MENU_ITEM_REQUEST_INTERVAL = 2;
    final int MENU_ITEM_REREGISTER = 3;
	
    final String TITLE_FEATURES = "Features";
    final String TITLE_DISPLAY = "Display";
	
    final FragmentManager fragment_manager = getFragmentManager();
    FeatureFragment feature_fragment;
    DisplayFragment display_fragment;
	final ECStatusHandler ec_status_handler = new ECStatusHandler();
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_session);
        
        final ActionBar actionbar = getActionBar();
        actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
//        actionbar.setDisplayHomeAsUpEnabled(true);

        feature_fragment = (FeatureFragment) fragment_manager.findFragmentById(R.id.frag_features);
        display_fragment = (DisplayFragment) fragment_manager.findFragmentById(R.id.frag_display);
        
        ActionBar.TabListener tablistener = new ActionBar.TabListener () {
    		@Override
    		public void onTabSelected(Tab tab, FragmentTransaction ft) {
    			switch ((String) tab.getText()) {
    			case TITLE_FEATURES:
    				ft.show(feature_fragment);
    				break;
    			case TITLE_DISPLAY:
    				ft.show(display_fragment);
    				break;
    			}
    		}

    		@Override
    		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    			switch ((String) tab.getText()) {
    			case TITLE_FEATURES:
    				ft.hide(feature_fragment);
    				break;
    			case TITLE_DISPLAY:
    				ft.hide(display_fragment);
    				break;
    			}
    		}

    		@Override
    		public void onTabReselected(Tab tab, FragmentTransaction ft) {
    		}
    	};

    	actionbar.addTab(actionbar.newTab().setText(TITLE_FEATURES).setTabListener(tablistener));
    	actionbar.addTab(actionbar.newTab().setText(TITLE_DISPLAY).setTabListener(tablistener));
    	
    	fragment_manager.beginTransaction().show(feature_fragment).hide(display_fragment).commit();

        // initialize DAN
        DAN.init(Constants.log_tag);
        
    	DAN.subscribe("Control_channel", ec_status_handler);
        
    	if (!DAN.session_status()) {
	        final String EC_ENDPOINT = getIntent().getStringExtra("EC_ENDPOINT");
	        JSONObject profile = new JSONObject();
	        try {
		        profile.put("d_name", "Android"+ DAN.get_clean_mac_addr(get_mac_addr()));
		        profile.put("dm_name", Constants.dm_name);
		        JSONArray feature_list = new JSONArray();
		        for (String f: Constants.df_list) {
		        	feature_list.put(f);
		        }
		        profile.put("df_list", feature_list);
		        profile.put("u_name", Constants.u_name);
		        profile.put("monitor", DAN.get_clean_mac_addr(get_mac_addr()));
	        	DAN.register(EC_ENDPOINT, DAN.get_d_id(get_mac_addr()), profile);
			} catch (JSONException e) {
				e.printStackTrace();
			}
    	} else {
    		feature_fragment.show_ec_status_on_ui(DAN.ec_endpoint(), DAN.session_status());
    	}
    	
    	feature_fragment.show_d_name_on_ui(DAN.get_d_name());
    }
    
	@Override
	public void trigger() {
		DAN.deregister();
		DAN.shutdown();
		remove_all_notification();
        finish();
	}
	
	class ECStatusHandler extends DAN.Subscriber {
	    public void odf_handler (final DAN.ODFObject odf_object) {
	    	runOnUiThread(new Thread () {
	    		@Override
	    		public void run () {
    	    		switch (odf_object.event_tag) {
        	        case REGISTER_FAILED:
        	        	feature_fragment.show_ec_status_on_ui(odf_object.message, false);
        	        	show_ec_status_on_notification(odf_object.message, false);
        	        	break;
        	        	
        	        case REGISTER_SUCCEED:
        	        	feature_fragment.show_ec_status_on_ui(odf_object.message, true);
        	        	show_ec_status_on_notification(odf_object.message, true);
        	        	String d_name = DAN.get_d_name();
        	        	logging("Get d_name:"+ d_name);
        				TextView tv_d_name = (TextView)findViewById(R.id.tv_d_name);
        				tv_d_name.setText(d_name);
        				break;
        	        }
	    		}
	    	});
	    }
	};
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, MENU_ITEM_ID_DAN_VERSION, 0, "DAN Version: "+ DAN.version);
        menu.add(0, MENU_ITEM_ID_DAI_VERSION, 0, "DAI Version: "+ version);
        menu.add(0, MENU_ITEM_REQUEST_INTERVAL, 0, "Request Interval: "+ DAN.get_request_interval() +" ms");
        menu.add(0, MENU_ITEM_REREGISTER, 0, "Reregsiter to another EC");
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
        case MENU_ITEM_REQUEST_INTERVAL:
        	show_input_dialog("Change Request Interval", "Input a integer as request interval (unit: ms)", ""+ DAN.get_request_interval());
            break;
        case MENU_ITEM_REREGISTER:
        	show_selection_dialog("Reregister to EC", DAN.available_ec());
        	break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void show_input_dialog (String title, String message, String hint) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(title);
        dialog.setMessage(message);

        final EditText input = new EditText(this);
        input.setHint(hint);
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        dialog.setView(input);
        
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener () {
            public void onClick (DialogInterface dialog, int id) {
                String value = input.getText().toString();
                try {
                	DAN.set_request_interval(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                	logging("Input is not a integer");
                }
            }
        });

        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener () {
            public void onClick (DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });

        dialog.create().show();
    }

    private void show_selection_dialog (String title, String[] available_ec) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(title);
        final ArrayAdapter<String> array_adapter = new ArrayAdapter<String>(
        		this,
                android.R.layout.select_dialog_item);
        array_adapter.addAll(available_ec);
        
        dialog.setAdapter(array_adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String which_ec = array_adapter.getItem(which);
                logging("selected: "+ which_ec);
                DAN.reregister(which_ec);
            }
        });
        dialog.create().show();
    }
    
    private void show_ec_status_on_notification (String host, boolean status) {
        String text = status ? host : "Connecting";
        NotificationManager notification_manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder notification_builder =
                new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(Constants.dm_name)
                        .setContentText(text);

        PendingIntent pending_intent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, SessionActivity.class),
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
    
    static public void logging (String message) {
        Log.i(Constants.log_tag, "[SessionActivity] " + message);
    }

}
