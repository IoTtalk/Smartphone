package com.example.smartphone;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

public class SessionActivity extends Activity {
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
    
    static public void logging (String message) {
        Log.i(C.log_tag, "[SessionActivity] " + message);
    }
}
