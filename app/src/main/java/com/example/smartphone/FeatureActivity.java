package com.example.smartphone;

import org.json.JSONException;
import org.json.JSONObject;

import DAN.DAN;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

public class FeatureActivity extends Activity {
	static final String local_tag = FeatureActivity.class.getSimpleName();
	
    final String TITLE_FEATURES = "Features";
    final String TITLE_DISPLAY = "Display";
    final String CURRENT_TAB = "CURRENT_TAB";
	
    final FragmentManager fragment_manager = getFragmentManager();
    SwitchFeatureFragment switch_feature_fragment;
    ChartFragment chart_fragment;
	final EventSubscriber event_subscriber = new EventSubscriber();
	final ODFSubscriber display_subscriber = new ODFSubscriber();
	
	public enum TAB {
		FEATURES, DISPLAY,
	}
	TAB current_tab;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_feature);
        
    	if (!DAN.session_status()) {
    		Intent intent = new Intent(FeatureActivity.this, SelectECActivity.class);
            startActivity(intent);
            finish();
    	}
        
        final ActionBar actionbar = getActionBar();
        actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        switch_feature_fragment = (SwitchFeatureFragment) fragment_manager.findFragmentById(R.id.frag_features);
        chart_fragment = (ChartFragment) fragment_manager.findFragmentById(R.id.frag_display_feature_list);
        
        ActionBar.TabListener tablistener = new ActionBar.TabListener () {
    		@Override
    		public void onTabSelected(Tab tab, FragmentTransaction ft) {
    			switch ((String) tab.getText()) {
    			case TITLE_FEATURES:
    				ft.show(switch_feature_fragment);
    				DAN.unsubscribe("Display");
    				current_tab = TAB.FEATURES;
    		        actionbar.setHomeButtonEnabled(false);
    				actionbar.setDisplayHomeAsUpEnabled(false);
    				break;
    			case TITLE_DISPLAY:
    				ft.show(chart_fragment);
    				DAN.subscribe("Display", display_subscriber);
    				current_tab = TAB.DISPLAY;
    				boolean show_home_as_up = (chart_fragment.current_page() == ChartFragment.PAGE.DATA);
    		        actionbar.setHomeButtonEnabled(show_home_as_up);
					actionbar.setDisplayHomeAsUpEnabled(show_home_as_up);
    				break;
    			}
    		}

    		@Override
    		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    			switch ((String) tab.getText()) {
    			case TITLE_FEATURES:
    				ft.hide(switch_feature_fragment);
    				break;
    			case TITLE_DISPLAY:
    				ft.hide(chart_fragment);
    				break;
    			}
    		}

    		@Override
    		public void onTabReselected(Tab tab, FragmentTransaction ft) {
    		}
    	};

    	actionbar.addTab(actionbar.newTab().setText(TITLE_FEATURES).setTabListener(tablistener));
    	actionbar.addTab(actionbar.newTab().setText(TITLE_DISPLAY).setTabListener(tablistener));
        
    	DAN.subscribe("Control_channel", event_subscriber);

    	switch_feature_fragment.show_d_name_on_ui(DAN.get_d_name());
		switch_feature_fragment.show_ec_status_on_ui(DAN.ec_endpoint(), DAN.session_status());
    }
	
	@Override
	public void onPause () {
    	super.onPause();
		if (current_tab == TAB.DISPLAY) {
			DAN.unsubscribe("Display");
		}
	}
	
	@Override
	public void onResume () {
		super.onResume();
		if (current_tab == TAB.DISPLAY) {
			DAN.subscribe("Display", display_subscriber);
		}
	}
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, Constants.MENU_ITEM_ID_DAN_VERSION, 0, "DAN Version: "+ DAN.version);
        menu.add(0, Constants.MENU_ITEM_ID_DAI_VERSION, 0, "DAI Version: "+ Constants.version);
        menu.add(0, Constants.MENU_ITEM_REQUEST_INTERVAL, 0, "Request Interval: "+ DAN.get_request_interval() +" ms");
        menu.add(0, Constants.MENU_ITEM_REREGISTER, 0, "Reregsiter to another EC");
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
        case Constants.MENU_ITEM_REQUEST_INTERVAL:
        	show_input_dialog("Change Request Interval", "Input a integer as request interval (unit: ms)", ""+ DAN.get_request_interval());
            break;
        case Constants.MENU_ITEM_REREGISTER:
        	show_selection_dialog("Reregister to EC", DAN.available_ec());
        	break;
        case android.R.id.home:
        	onBackPressed();
        	break;
        }
        return super.onOptionsItemSelected(item);
    }
	
	@Override
	public void onBackPressed() {
		if (current_tab == TAB.FEATURES) {
			finish();
		} else if (chart_fragment.current_page() == ChartFragment.PAGE.LIST) {
			finish();
		} else {
			chart_fragment.show_page(ChartFragment.PAGE.LIST);
		}
	}
    
	public void shutdown() {
		DAN.deregister();
		DAN.shutdown();
		Utils.remove_all_notification(FeatureActivity.this);
        finish();
	}
	
	class EventSubscriber extends DAN.Subscriber {
	    public void odf_handler (final String feature, final DAN.ODFObject odf_object) {
			if (!feature.equals(DAN.CONTROL_CHANNEL)) {
                Utils.logging(local_tag, "EventSubscriber should only receive {} events", DAN.CONTROL_CHANNEL);
                return;
            }
	    	runOnUiThread(new Thread () {
	    		@Override
	    		public void run () {
    	    		switch (odf_object.event_tag) {
        	        case REGISTER_FAILED:
        	        	switch_feature_fragment.show_ec_status_on_ui(odf_object.message, false);
        	        	Utils.show_ec_status_on_notification(FeatureActivity.this, odf_object.message, false);
        	        	break;
        	        	
        	        case REGISTER_SUCCEED:
        	        	switch_feature_fragment.show_ec_status_on_ui(odf_object.message, true);
        	        	Utils.show_ec_status_on_notification(FeatureActivity.this, odf_object.message, true);
        	        	String d_name = DAN.get_d_name();
        	        	Utils.logging(local_tag, "Get d_name:"+ d_name);
        				TextView tv_d_name = (TextView)findViewById(R.id.tv_item_d_name);
        				tv_d_name.setText(d_name);
        				break;
        	        }
	    		}
	    	});
	    }
	};
	
	class ODFSubscriber extends DAN.Subscriber {
	    public void odf_handler (final String feature, final DAN.ODFObject odf_object) {
            if (feature.equals(DAN.CONTROL_CHANNEL)) {
                Utils.logging(local_tag, "ODFSubscriber should not receive {} events", DAN.CONTROL_CHANNEL);
                return;
            }
	    	// send feature data to display_device_list_fragment
			runOnUiThread(new Thread () {
				@Override
				public void run () {
					try {
						JSONObject newest_data = odf_object.data.getJSONObject(0);
						chart_fragment.set_newest_metadata(newest_data);
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			});
	    }
	};

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
                	Utils.logging(local_tag, "Input is not a integer");
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
                Utils.logging(local_tag, "Selected EC: "+ which_ec);
                DAN.reregister(which_ec);
            }
        });
        dialog.create().show();
    }
}
