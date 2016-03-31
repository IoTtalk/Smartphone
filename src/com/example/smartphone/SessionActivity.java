package com.example.smartphone;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

public class SessionActivity extends Activity {
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ec_list);
    }
}
