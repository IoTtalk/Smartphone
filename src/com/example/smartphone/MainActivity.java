package com.example.smartphone;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

public class MainActivity extends TabActivity {
	final int NOTIFICATION_ID = 1;
	
	static boolean register_retry_permission = true;
	static boolean detect_local_ec_permission = true;
	static DetectLocalECThread detact_local_ec_thread = null;
	static MainActivity self;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	self = this;
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
        
        register_retry_permission = true;
        detect_local_ec_permission = true;
        
        // show ec status on monitor page
        show_ec_status(false);

        // detect local EC
        if (detact_local_ec_thread == null) {
	        detact_local_ec_thread = new DetectLocalECThread();
	        detact_local_ec_thread.start();
        }
        new RegisterThread().start();
        
        EasyConnect.start(this);
        
    }
    
    public void end () {
		detact_local_ec_thread.stop_working();
		detact_local_ec_thread = null;
        detect_local_ec_permission = false;
        new DetachThread().start();
        NotificationManager notification_manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notification_manager.cancelAll();
        finish();
    }
    
    @Override
    public void onDestroy () {
        super.onDestroy();
		MonitorDataThread.work_permission = false;
		register_retry_permission = false;
    }
    
    private class DetectLocalECThread extends Thread {
    	DatagramSocket socket;
    	public void stop_working () {
    		socket.close();
    	}
    	
    	public void run () {
			logging("Detection Thread starts");
    		try {
    			String current_ec_host = EasyConnect.EC_HOST;
				socket = new DatagramSocket(null);
				socket.setReuseAddress(true);
				socket.bind(new InetSocketAddress("0.0.0.0", EasyConnect.EC_BROADCAST_PORT));
				byte[] lmessage = new byte[20];
				DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);
				while (detect_local_ec_permission) {
					logging("wait for UDP packet");
                    socket.receive(packet);
                    String input_data = new String( lmessage, 0, packet.getLength() );
                    if (input_data.equals("easyconnect")) {
                    	InetAddress ec_raw_addr = packet.getAddress();
                    	String ec_addr = ec_raw_addr.getHostAddress();
                    	logging("Get easyconnect UDP Packet from "+ ec_addr);
                    	String new_ec_host = ec_addr +":"+ EasyConnect.EC_PORT;
                    	if (!current_ec_host.equals(new_ec_host)) {
	                    	logging("Reattach to "+ new_ec_host);
	                    	EasyConnect.reattach_to(new_ec_host);
	                    	current_ec_host = new_ec_host;
	                    	show_ec_status(true);
                    	}
                    }
                }
				socket.close();
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				logging("Detection Thread stops");
			}
    	}
    }
    
    static private class DetachThread extends Thread {
    	public void run () {
    		EasyConnect.detach();
	        EasyConnect.reset_ec_host();
            logging("Detached from EasyConnect");
    	}
    }
    
    private class RegisterThread extends Thread {
    	@Override
        public void run () {
    		logging("RegisterThread starts");
            
            // show smartphone name on features page
    		String smartphone_d_name = "Android"+ get_wifi_mac_addr().replace(":", "");
            send_message_to_features_page("D_NAME", smartphone_d_name);
    		
    		boolean attach_success = false;
    		
            while ( register_retry_permission && !attach_success ) {
	    		//attach_success = DeFeMa.attach(
            	attach_success = EasyConnect.attach(
	        		get_wifi_mac_addr(),
					C.dm_name,
					C.df_list,
					smartphone_d_name,
					C.u_name,
					get_wifi_mac_addr()
				);

    			if ( !attach_success ) {
		    		logging("Attach failed, wait for 2000ms and try again");
    				try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
		    		
    			} else {
		    		logging("Attach Successed");
            		new MonitorDataThread().start();
                	show_ec_status(true);
    			}
    			
            }
            logging("RegisterThread stops");
    	}
    }
    
    private String get_wifi_mac_addr () {
//        WifiManager wifiMan = (WifiManager) this.getSystemService(
//            Context.WIFI_SERVICE);
//        WifiInfo wifiInf = wifiMan.getConnectionInfo();
//        return wifiInf.getMacAddress();
    	return EasyConnect.get_mac_addr();
    }
    
    public void send_message_to_features_page (String tag, String data) {
    	FeatureActivity activity = (FeatureActivity)getLocalActivityManager().getActivity("tab-features");
    	if (activity != null) {
	    	Handler handler = activity.ec_status_handler;
	        Message msgObj = handler.obtainMessage();
	        Bundle b = new Bundle();
	        b.putString("tag", tag);
	        b.putString("data", data);
	        msgObj.setData(b);
	        handler.sendMessage(msgObj);
    	}
    }
    
    public void show_ec_status_on_features_page (boolean status) {
        send_message_to_features_page("EC_STATUS", status ? "~" : "!");
    }
    
    static public void logging (String message) {
        Log.i(C.log_tag, "[MainActivity] " + message);
    }
    
    public void show_ec_status (boolean status) {
    	show_ec_status_on_features_page(status);
    	String text = status ? EasyConnect.EC_HOST : "connecting";
        NotificationManager notification_manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notification_builder =
    		new NotificationCompat.Builder(this)
	    	.setSmallIcon(R.drawable.ic_launcher)
	    	.setContentTitle(C.dm_name)
	    	.setContentText(text)
	    	.setOngoing(true);
        
        PendingIntent pending_intent = PendingIntent.getActivity(
    		this,
    		0,
    		new Intent(this, MainActivity.class),
    	    PendingIntent.FLAG_UPDATE_CURRENT
		);
        
        notification_builder.setContentIntent(pending_intent);
        notification_manager.notify(NOTIFICATION_ID, notification_builder.build());
    }
    
}
