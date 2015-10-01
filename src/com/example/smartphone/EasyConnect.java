package com.example.smartphone;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;

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
	static private Context creater = null;
	static private String log_tag = "EasyConnect";
	static private String mac_addr_cache = null;
	
	static HashSet<Handler> subscribers = null;
	static public enum Tag {
		D_NAME_GENEREATED,
		ATTACH_SUCCESS,
		DETACH_SUCCESS,
	};
	
	static private final int NOTIFICATION_ID = 1;
    static public  int      EC_PORT           = 9999;
    static         String   EC_HOST           = "openmtc.darkgerm.com:"+ EC_PORT;
    static         String   DEFAULT_EC_HOST   = "openmtc.darkgerm.com:"+ EC_PORT;
    static public  int      EC_BROADCAST_PORT = 17000;
    static private JSONObject profile;
    static private boolean ec_status = false;
    
    static HashMap<String, UpStreamThread> upstream_thread_pool;
    
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	self = this;
    	upstream_thread_pool = new HashMap<String, UpStreamThread>();
    	show_ec_status_on_notification(ec_status);
    	DetectLocalECThread.work();
    	return START_STICKY;
    }
    
    // *********** //
    // * Threads * //
    // *********** //
    
    static private class DetectLocalECThread extends Thread {
    	static DetectLocalECThread self = null;
    	private DetectLocalECThread () {}
    	
    	static DatagramSocket socket;
    	static boolean working_permission;
    	static String verifying_ec_host = null;
    	static int receive_count = 0;
    	
    	static public void work () {
			logging("DetectLocalECThread.work()");
    		if (self != null) {
    			logging("already working");
    			return;
    		}
    		working_permission = true;
    		self = new DetectLocalECThread();
    		self.start();
    	}
    	
    	static public void stop_working () {
			logging("DetectLocalECThread.stop_working()");
			if (self == null) {
    			logging("Already stopped");
				return;
			}
    		working_permission = false;
    		socket.close();
    		self = null;
    	}
    	
    	private void process_easyconnect_packet (String _) {
    		logging("Get EasyConnect UDP Packet from "+ _);
        	String new_ec_host = _ +":"+ EasyConnect.EC_PORT;
    		
    		if (verifying_ec_host == null) {
    			verifying_ec_host = new_ec_host;
    		}
    		
    		if (EC_HOST.equals(DEFAULT_EC_HOST)) {
    			// we are now on default EC, and we should use local EC first
    			receive_count = 10;
    		} else if (verifying_ec_host.equals(new_ec_host)) {
    			// contiguously receiving EC packet
        		receive_count += 1;
    		} else {
    			receive_count = 0;
    		}
    		
    		// prevent overflow
    		if (receive_count >= 10) {
    			receive_count = 10;
    		}
    		
        	if (receive_count >= 5 && !EC_HOST.equals(verifying_ec_host)) {
        		boolean reattach_successed = EasyConnect.reattach_to(new_ec_host);
            	show_ec_status_on_notification(reattach_successed);
        	}
    	}
    	
    	public void run () {
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
                    	process_easyconnect_packet(ec_addr);
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
				DetectLocalECThread.stop_working();
				logging("Detection Thread stops");
			}
    	}
    }
    
    static private class RegisterThread extends Thread {
    	static RegisterThread self;
    	private RegisterThread () {}
    	
    	static boolean working_permission;
    	
    	static public void work () {
    		logging("RegisterThread.work()");
    		if (ec_status) {
    			logging("already registered");
    	    	show_ec_status_on_notification(ec_status);
    			return;
    		}
    		
    		if (self != null) {
    			logging("already working");
    	    	show_ec_status_on_notification(ec_status);
    			return;
    		}
    		working_permission = true;
    		self = new RegisterThread();
    		self.start();
    	}
    	
    	static public void stop_working () {
			logging("RegisterThread.stop_working()");
    		working_permission = false;
    		self = null;
    	}
    	
    	@Override
        public void run () {
    		logging("RegisterThread starts");
    		boolean attach_success = false;
    		
        	try {
				notify_all_subscribers(Tag.D_NAME_GENEREATED, profile.getString("d_name"));
			} catch (JSONException e1) {
				e1.printStackTrace();
			}
    		
            while ( working_permission && !attach_success ) {
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
            		show_ec_status_on_notification(true);
    			}
    			
            }
            logging("RegisterThread stops");
            RegisterThread.stop_working();
    	}
    }
    
    static private class DetachThread extends Thread {
    	static DetachThread self;
    	private DetachThread () {}
    	
    	static public void work() {
    		if (!ec_status) {
    			logging("Already detached");
    	    	show_ec_status_on_notification(ec_status);
    			return;
    		}
    		
    		if (self != null) {
    			logging("already working");
    	    	show_ec_status_on_notification(ec_status);
    			return;
    		}
    		self = new DetachThread();
    		self.start();
    	}
    	
    	@Override
        public void run () {
    		DetectLocalECThread.stop_working();
            RegisterThread.stop_working();
            EasyConnect.self.getApplicationContext().stopService(new Intent(EasyConnect.self, EasyConnect.class));
            self = null;
            ec_status = false;
            boolean detach_result = detach_api();
    		
            notify_all_subscribers(Tag.DETACH_SUCCESS, EC_HOST);
            NotificationManager notification_manager = (NotificationManager) get_reliable_context().getSystemService(Context.NOTIFICATION_SERVICE);
            notification_manager.cancelAll();
        	
    		logging("Detached from EasyConnect, result: "+ detach_result);
            
            // reset
        	EC_PORT = 9999;
        	EC_HOST = "openmtc.darkgerm.com:"+ EC_PORT;
    	}
    }
    
    static private class UpStreamThread extends Thread {
    	// UpStreamThread cannot be singleton,
    	// or it may block other threads
    	boolean working_permission;
    	String feature;
    	LinkedBlockingQueue<JSONArray> queue;
    	int dimension;
    	boolean queueable;
    	long timestamp;
    	
    	public UpStreamThread (String feature) {
    		this.feature = feature;
    		this.queue = new LinkedBlockingQueue<JSONArray>();
    		this.dimension = 0;
    		this.queueable = false;
    		this.timestamp = 0;
    	}
    	
    	public void stop_working () {
			working_permission = false;
			this.interrupt();
    	}
    	
    	public void enqueue (JSONArray data) {
    		if (dimension == 0) {
    			try {
    				// check if the data is queueable
    				// which means every dimension is in "double" type
					dimension = data.length();
					queueable = true;
					for (int i = 0; i < dimension; i++) {
						if (!(data.get(i) instanceof Double)) {
							queueable = false;
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
    		}
    		
    		if (queueable) {
	    		try {
					queue.put(data);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
    		} else {
    			try {
        			queue.clear();
					queue.put(data);
				} catch (InterruptedException e) {
					e.printStackTrace();
				};
    		}
    	}
    	
    	public void run () {
    		logging("UpStreamThread("+ feature +") starts");
    		working_permission = true;
    		while (working_permission) {
    			try {
        			long now = System.currentTimeMillis();
        			if (now - timestamp < 150) {
        				Thread.sleep(now - timestamp);
        			}
    				timestamp = now;
    				double[] buffer = new double[dimension];
    				if (!queueable) {
    					JSONArray tmp = queue.take();
    					if (!working_permission) {
    			    		logging("UpStreamThread("+ feature +") interrupted");
    						return;
    					}
    					for (int i = 0; i < tmp.length(); i++) {
    						buffer[i] = tmp.getDouble(i);
    					}
    				} else {
	    				int buffer_count = 0;
	    				do {
	    					JSONArray tmp = queue.take();
	    					if (!working_permission) {
	    			    		logging("UpStreamThread("+ feature +") interrupted");
	    						return;
	    					}
	    					for (int i = 0; i < dimension; i++) {
	    						buffer[i] += tmp.getDouble(i);
	    					}
	    					buffer_count += 1;
	    				} while (!queue.isEmpty());
	    				
	    				if (buffer_count == 0) {
	    					continue;
	    				}
	    				
	    				for (int i = 0; i < dimension; i++) {
	    					buffer[i] /= buffer_count;
	    				}
    				}
    				
    		        String url;
    				url = "http://"+ EC_HOST +"/push/"+ profile.getString("d_id") +"/"+ feature +"?data="+ Arrays.toString(buffer).replace(" ", "");
    				logging("UpStreamThread("+ feature +") push data: "+ Arrays.toString(buffer));
    		        HttpRequest.get(url);
    			} catch (JSONException e) {
    				e.printStackTrace();
    			} catch (InterruptedException e) {
					e.printStackTrace();
				}
    		}
    		logging("UpStreamThread("+ feature +") stops");
    	}
    }
    
    // *************************** //
    // * Internal Used Functions * //
    // *************************** //
    
    static private void show_ec_status_on_notification (boolean new_ec_status) {
    	ec_status = new_ec_status;
    	if (ec_status) {
        	notify_all_subscribers(Tag.ATTACH_SUCCESS, EC_HOST);
    	}
    	show_ec_status_on_notification();
    }
    
    static private void show_ec_status_on_notification () {
    	Context ctx = get_reliable_context();
    	if (ctx == null) {
    		return;
    	}
    	String text = ec_status ? EasyConnect.EC_HOST : "Connecting";
        NotificationManager notification_manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notification_builder =
    		new NotificationCompat.Builder(ctx)
	    	.setSmallIcon(R.drawable.ic_launcher)
	    	.setContentTitle(C.dm_name)
	    	.setContentText(text)
	    	.setOngoing(true);
        
        PendingIntent pending_intent = PendingIntent.getActivity(
        	ctx,
    		0,
    		new Intent(ctx, MainActivity.class),
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
    
    static private void notify_all_subscribers (Tag tag, String message) {
    	if (subscribers == null) {
    		logging("Broadcast: No subscribers");
    		return;
    	}
    	for (Handler handler: subscribers) {
    		send_message_to(handler, tag, message);
    	}
    }
    
    static private void send_message_to (Handler handler, Tag tag, String message) {
        Message msgObj = handler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putSerializable("tag", tag);
        bundle.putString("message", message);
        msgObj.setData(bundle);
        handler.sendMessage(msgObj);
    }
    
    static private Context get_reliable_context () {
    	if (self == null) {
    		logging("EasyConnect Service is null, use creater instead");
    		return creater;
    	}
    	return self;
    }
    
    // ************** //
    // * Public API * //
    // ************** //
    
    static public void start (Context ctx, String tag) {
    	creater = ctx;
    	log_tag = tag;
    	// start this service
        Intent intent = new Intent (ctx, EasyConnect.class);
        ctx.getApplicationContext().startService(intent);
    }
    
    static public String get_mac_addr () {
    	if (mac_addr_cache != null) {
    		logging("We have mac address cache: "+ mac_addr_cache);
    		return mac_addr_cache;
    	}
    	
    	String error_mac_addr = "E2202E2202";
    	Context ctx = get_reliable_context();
    	
    	if (ctx == null) {
    		return error_mac_addr;
    	}
    	
		WifiManager wifiMan = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
		if (wifiMan == null) {
    		logging("Cannot get WiFiManager system service");
    		return error_mac_addr;
		}
		
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        if (wifiInf == null) {
        	logging("Cannot get connection info");
    		return error_mac_addr;
        }
        
        mac_addr_cache = wifiInf.getMacAddress().replace(":", ""); 
        return mac_addr_cache;
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
    	RegisterThread.work();
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
    	if (!upstream_thread_pool.containsKey(feature)) {
    		UpStreamThread ust = new UpStreamThread(feature);
    		upstream_thread_pool.put(feature, ust);
    		ust.start();
    	}
		return false;
    }

    static public void push_data2 (String feature, JSONArray data) {
    	if (!upstream_thread_pool.containsKey(feature)) {
    		UpStreamThread ust = new UpStreamThread(feature);
    		upstream_thread_pool.put(feature, ust);
    		ust.start();
    	}
    	UpStreamThread ust = upstream_thread_pool.get(feature);
    	ust.enqueue(data);
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

    static public void detach () {
		for (String feature: upstream_thread_pool.keySet()) {
			upstream_thread_pool.get(feature).stop_working();
		}
    	DetachThread.work();
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
