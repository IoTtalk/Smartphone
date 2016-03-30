package com.example.smartphone;

import DAN.DAN;
import android.app.AlertDialog;
import android.app.TabActivity;
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
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

public class MainActivity extends TabActivity {
	final int NOTIFICATION_ID = 1;

    final int MENU_ITEM_ID_API_VERSION = 0;
    final int MENU_ITEM_ID_DA_VERSION = 1;
    final int MENU_ITEM_REQUEST_INTERVAL = 2;
    final int MENU_ITEM_REREGISTER = 3;
    
	static final String version = "20160327";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        logging("=============================================");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        TabHost tabHost = (TabHost)findViewById(android.R.id.tabhost);
        TabSpec tabspec;
        
        tabspec = tabHost.newTabSpec("tab-features");
        tabspec.setIndicator("Features");
        tabspec.setContent(new Intent(this, FeatureActivity.class));
        tabHost.addTab(tabspec);
        
        tabspec = tabHost.newTabSpec("tab-monitor");
        tabspec.setIndicator("Monitor");
        tabspec.setContent(new Intent(this, MonitorDeviceListActivity.class));
        tabHost.addTab(tabspec);
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, MENU_ITEM_ID_API_VERSION, 0, "API Version: "+ DAN.version);
        menu.add(0, MENU_ITEM_ID_DA_VERSION, 0, "DA Version: "+ MainActivity.version);
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
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
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
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        dialog.setTitle(title);
        final ArrayAdapter<String> array_adapter = new ArrayAdapter<String>(
                MainActivity.this,
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
        Log.i(C.log_tag, "[MainActivity] " + message);
    }
}
