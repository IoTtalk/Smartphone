package com.example.smartphone;

import DAN.DAN;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;

public class SessionActivity extends Activity {
	final String version = "20160331";
	
    final int MENU_ITEM_ID_DAN_VERSION = 0;
    final int MENU_ITEM_ID_DAI_VERSION = 1;
    final int MENU_ITEM_REQUEST_INTERVAL = 2;
    final int MENU_ITEM_REREGISTER = 3;
	
	final String TITLE_FEATURES = "Features";
	final String TITLE_DISPLAY = "Display";
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_session);
        
        final ActionBar actionbar = getActionBar();
        actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        ActionBar.TabListener tablistener = new ActionBar.TabListener () {
    		@Override
    		public void onTabSelected(Tab tab, FragmentTransaction ft) {
    	        FragmentManager fragment_manager = getFragmentManager();
    			switch ((String) tab.getText()) {
    			case TITLE_FEATURES:
    				ft.show(fragment_manager.findFragmentById(R.id.frag_features));
    				break;
    			case TITLE_DISPLAY:
    				ft.show(fragment_manager.findFragmentById(R.id.frag_display));
    				break;
    			}
    		}

    		@Override
    		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    	        FragmentManager fragment_manager = getFragmentManager();
    			switch ((String) tab.getText()) {
    			case TITLE_FEATURES:
    				ft.hide(fragment_manager.findFragmentById(R.id.frag_features));
    				break;
    			case TITLE_DISPLAY:
    				ft.hide(fragment_manager.findFragmentById(R.id.frag_display));
    				break;
    			}
    		}

    		@Override
    		public void onTabReselected(Tab tab, FragmentTransaction ft) {
    		}
    	};

    	actionbar.addTab(actionbar.newTab().setText(TITLE_FEATURES).setTabListener(tablistener));
    	actionbar.addTab(actionbar.newTab().setText(TITLE_DISPLAY).setTabListener(tablistener));
    }
    
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
    
    static public void logging (String message) {
        Log.i(C.log_tag, "[SessionActivity] " + message);
    }
}
