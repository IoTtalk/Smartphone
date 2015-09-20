package com.example.smartphone;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class EasyConnect extends Service {
	static private EasyConnect self = null;
	static private DetectLocalECThread detect_local_ec_thread = null;
	static private RegisterThread register_thread = null;
	static private String log_tag = "EasyConnect";
	
	static HashSet<Handler> subscribers = null;
	static public final int ATTACH_SUCCESS = 0;
	
	static private final int NOTIFICATION_ID = 1;
    static public  int      EC_PORT           = 9999;
    static         String   EC_HOST           = "openmtc.darkgerm.com:"+ EC_PORT;
    static public  int      EC_BROADCAST_PORT = 17000;
    static private JSONObject profile;
    static private boolean ec_connected = false;
    
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	self = this;
    	show_ec_status_on_notification(false);
    	detect_local_ec_thread = new DetectLocalECThread();
    	detect_local_ec_thread.start();
    	return START_STICKY;
    }
    
    // *********** //
    // * Threads * //
    // *********** //
    
    static private class DetectLocalECThread extends Thread {
    	DatagramSocket socket;
    	boolean working_permission;
    	public void stop_working () {
			logging("Request Detection Thread stop_working");
    		working_permission = false;
    		socket.close();
    	}
    	
    	public void run () {
    		working_permission = true;
			logging("Detection Thread starts");
    		try {
				socket = new DatagramSocket(null);
				socket.setReuseAddress(true);
				socket.bind(new InetSocketAddress("0.0.0.0", EasyConnect.EC_BROADCAST_PORT));
				byte[] lmessage = new byte[20];
				DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);
				while (working_permission) {
					logging("Wait for UDP packet");
                    socket.receive(packet);
                    String input_data = new String( lmessage, 0, packet.getLength() );
                    if (input_data.equals("easyconnect")) {
                    	// It's easyconnect packet
                    	InetAddress ec_raw_addr = packet.getAddress();
                    	String ec_addr = ec_raw_addr.getHostAddress();
                    	logging("Get easyconnect UDP Packet from "+ ec_addr);
                    	String new_ec_host = ec_addr +":"+ EasyConnect.EC_PORT;
                    	if (!EC_HOST.equals(new_ec_host)) {
                    		boolean reattach_successed = EasyConnect.reattach_to(new_ec_host);
	                    	show_ec_status_on_notification(reattach_successed);
	                    	notify_all_subscribers(ATTACH_SUCCESS, new_ec_host);
                    	}
                    }
                }
				socket.close();
			} catch (SocketException e) {
				logging("SocketException");
				e.printStackTrace();
			} catch (IOException e) {
				logging("IOException");
				e.printStackTrace();
			} finally {
				logging("Detection Thread stops");
			}
    	}
    }
    
    static private class RegisterThread extends Thread {
    	boolean working_permission;
    	public void stop_working () {
			logging("Request Register Thread stop_working");
    		working_permission = false;
    	}
    	
    	@Override
        public void run () {
    		working_permission = true;
    		logging("RegisterThread starts");
    		boolean attach_success = false;
    		
            while ( working_permission && !attach_success ) {
	    		//attach_success = DeFeMa.attach(
            	attach_success = EasyConnect.attach_api(profile);

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
            		show_ec_status_on_notification();
    			}
    			
            }
            logging("RegisterThread stops");
            register_thread = null;
    	}
    }
    
    // *************************** //
    // * Internal Used Functions * //
    // *************************** //
    
    static private void show_ec_status_on_notification (boolean new_status) {
    	ec_connected = new_status;
    	show_ec_status_on_notification();
    }
    static private void show_ec_status_on_notification () {
    	String text = ec_connected ? EasyConnect.EC_HOST : "connecting";
        NotificationManager notification_manager = (NotificationManager) self.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notification_builder =
    		new NotificationCompat.Builder(self)
	    	.setSmallIcon(R.drawable.ic_launcher)
	    	.setContentTitle(C.dm_name)
	    	.setContentText(text)
	    	.setOngoing(true);
        
        PendingIntent pending_intent = PendingIntent.getActivity(
    		self,
    		0,
    		new Intent(self, MainActivity.class),
    	    PendingIntent.FLAG_UPDATE_CURRENT
		);
        
        notification_builder.setContentIntent(pending_intent);
        notification_manager.notify(NOTIFICATION_ID, notification_builder.build());
    }
    
    static private boolean reattach_to (String new_host) {
    	detach_api();
    	EC_HOST = new_host;
    	logging("Reattach to "+ new_host);
    	return attach_api(profile);
    }

    static private void logging (String message) {
        Log.i(log_tag, "[EasyConnect] " + message);
    }
    
    static private void notify_all_subscribers (int tag, String message) {
    	if (subscribers == null) {
    		return;
    	}
    	for (Handler handler: subscribers) {
    		send_message_to(handler, tag, message);
    	}
    }
    
    static private void send_message_to (Handler handler, int tag, String message) {
        Message msgObj = handler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt("tag", tag);
        b.putString("message", message);
        msgObj.setData(b);
        handler.sendMessage(msgObj);
    }
    
    // ************** //
    // * Public API * //
    // ************** //
    
    static public void start (Context ctx, String tag) {
    	log_tag = tag;
    	// start this service
        Intent intent = new Intent (ctx, EasyConnect.class);
        ctx.getApplicationContext().startService(intent);
    }
    
    static public String get_mac_addr () {
    	String error_mac_addr = "E2202E2202";
    	if (self == null) {
    		return error_mac_addr;
    	}
    	
		WifiManager wifiMan = (WifiManager) self.getSystemService(Context.WIFI_SERVICE);
		if (wifiMan == null) {
    		return error_mac_addr;
		}
		
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        if (wifiInf == null) {
    		return error_mac_addr;
        }
        
        return wifiInf.getMacAddress().replace(":", "");
    }
    
    static public String get_d_id () {
        return "defema"+ get_mac_addr();
    }
    
    static public void register (Handler handler) {
    	if (subscribers == null) {
    		subscribers = new HashSet<Handler>();
    	}
    	subscribers.add(handler);
    }
    
    static public void deregister (Handler handler) {
    	subscribers.remove(handler);
    }
    
    static public void attach (JSONObject profile) {
    	EasyConnect.profile = profile;
    	if (register_thread != null) {
    		return;
    	}
    	register_thread = new RegisterThread();
    	register_thread.start();
    }

    static public boolean push_data (String feature, int data) {
        return _push_data(feature, "["+ data +"]");
    }
    static public boolean push_data (String feature, int[] data) {
        return _push_data(feature, Arrays.toString(data).replace(" ", ""));
    }
    static public boolean push_data (String feature, float data) {
        return _push_data(feature, "["+ data +"]");
    }
    static public boolean push_data (String feature, float[] data) {
        return _push_data(feature, Arrays.toString(data).replace(" ", ""));
    }
    static public boolean push_data (String feature, double data) {
        return _push_data(feature, "["+ data +"]");
    }
    static public boolean push_data (String feature, double[] data) {
        return _push_data(feature, Arrays.toString(data).replace(" ", ""));
    }
    static public boolean push_data (String feature, byte[] data) {
        return _push_data(feature, Arrays.toString(data).replace(" ", ""));
    }
    static public boolean push_data (String feature, JSONArray data) {
        return _push_data(feature, data.toString().replace(" ", ""));
    }
    static public boolean push_data (String feature, String data) {
    	return _push_data(feature, "[\""+ data +"\"]");
    }

    static private boolean _push_data (String feature, String data) {
        String url;
		try {
			url = "http://"+ EC_HOST +"/push/"+ profile.getString("d_id") +"/"+ feature +"?data="+ data;
	        return HttpRequest.get(url).status_code == 200;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return false;
    }

    static public JSONObject pull_data (String feature) {
        String url;
		try {
			url = "http://"+ EC_HOST +"/pull/"+ profile.getString("d_id") +"/"+ feature;
	        HttpRequest.HttpResponse a = HttpRequest.get(url);

	        if (a.status_code != 200) {
	            try {
	                JSONObject ret = new JSONObject();
	                ret.put("timestamp", "none");
	                ret.put("data", new JSONArray());
	                return ret;
	                
	            } catch (JSONException e) {
	                e.printStackTrace();
	            }
	            
	        }

	        try {
	            return new JSONObject(a.body);
	        } catch (JSONException e) {
	            return new JSONObject();
	        }
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
        return new JSONObject();
    }

    static public boolean detach () {
    	detect_local_ec_thread.stop_working();
    	detect_local_ec_thread = null;
        self.getApplicationContext().stopService(new Intent(self, EasyConnect.class));
        self = null;
        return detach_api();
    }
    
    static public void reset_ec_host () {
    	EC_PORT = 9999;
    	EC_HOST = "openmtc.darkgerm.com:"+ EC_PORT;
    	EC_BROADCAST_PORT = 17000;
    }
    
    // ********************* //
    // * Internal HTTP API * //
    // ********************* //

    static private boolean attach_api (JSONObject profile) {
        String url;
		try {
			url = "http://"+ EC_HOST +"/create/"+ profile.getString("d_id") +"?profile="+ profile.toString().replace(" ", "");
	        return HttpRequest.get(url).status_code == 200;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return false;
    }

    static private boolean detach_api () {
        String url;
		try {
			url = "http://"+ EC_HOST +"/delete/"+ profile.getString("d_id");
	        return HttpRequest.get(url).status_code == 200;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return false;
    }
}
