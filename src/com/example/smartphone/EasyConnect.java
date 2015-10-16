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
import android.os.Parcel;
import android.os.Parcelable;
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
    static HashMap<String, DownStreamThread> downstream_thread_pool;
    
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	self = this;
    	upstream_thread_pool = new HashMap<String, UpStreamThread>();
    	downstream_thread_pool = new HashMap<String, DownStreamThread>();
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
			if (self == null) {
    			logging("Already stopped");
				return;
			}
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
    	
    	static public void work () {
    		
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
            
            ec_status = false;
            boolean detach_result = detach_api();
    		
            notify_all_subscribers(Tag.DETACH_SUCCESS, EC_HOST);
            NotificationManager notification_manager = (NotificationManager) get_reliable_context().getSystemService(Context.NOTIFICATION_SERVICE);
            notification_manager.cancelAll();
        	
    		logging("Detached from EasyConnect, result: "+ detach_result);
            
            // reset
        	EC_PORT = 9999;
        	EC_HOST = "openmtc.darkgerm.com:"+ EC_PORT;
            EasyConnect.self.getApplicationContext().stopService(new Intent(EasyConnect.self, EasyConnect.class));
            self = null;
    	}
    }
    
    static private class UpStreamThread extends Thread {
    	// UpStreamThread cannot be singleton,
    	// or it may block other threads
    	boolean working_permission;
    	String feature;
    	LinkedBlockingQueue<EasyConnectDataObject> queue;
    	long timestamp;
    	
    	public UpStreamThread (String feature) {
    		this.feature = feature;
    		this.queue = new LinkedBlockingQueue<EasyConnectDataObject>();
    		this.timestamp = 0;
    	}
    	
    	public void stop_working () {
			working_permission = false;
			this.interrupt();
    	}
    	
    	public void enqueue (EasyConnectDataObject data) {
    		try {
				queue.put(data);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	
    	public void run () {
    		logging("UpStreamThread("+ feature +") starts");
    		working_permission = true;
    		while (working_permission) {
    			try {
        			long now = System.currentTimeMillis();
        			if (now - timestamp < 150) {
        				Thread.sleep(150 - (now - timestamp));
        			}
    				timestamp = System.currentTimeMillis();
    				
					EasyConnectDataObject acc = queue.take();
    				int buffer_count = 1;
    				while (!queue.isEmpty()) {
    					EasyConnectDataObject tmp = queue.take();
    					if (!working_permission) {
        		    		logging("UpStreamThread("+ feature +") droped");
    						return;
    					}
    					acc.accumulate(tmp);
    					buffer_count += 1;
    				}
    				acc.average(buffer_count);
    				
    		        String url;
    				url = "http://"+ EC_HOST +"/push/"+ profile.getString("d_id") +"/"+ feature +"?data="+ acc.toString();
    				logging("UpStreamThread("+ feature +") push data: "+ acc.toString());
    		        HttpRequest.get(url);
    			} catch (JSONException e) {
    				e.printStackTrace();
    			} catch (InterruptedException e) {
		    		logging("UpStreamThread("+ feature +") interrupted");
					e.printStackTrace();
				}
    		}
    		logging("UpStreamThread("+ feature +") stops");
    	}
    }
    
    static private class DownStreamThread extends Thread {
    	// DownStreamThread cannot be singleton,
    	// or it may block other threads
    	boolean working_permission;
    	String feature;
    	String url;
    	Handler subscriber;
    	long timestamp;
    	String data_timestamp;
    	int interval;
    	
    	public DownStreamThread (String feature, Handler callback) {
    		this(feature, callback, 150);
    	}
    	
    	public DownStreamThread (String feature, Handler callback, int interval) {
    		this.feature = feature;
    		try {
				this.url = "http://"+ EC_HOST +"/pull/"+ profile.getString("d_id") +"/"+ feature;
			} catch (JSONException e) {
				e.printStackTrace();
			}
    		this.subscriber = callback;
    		this.timestamp = 0;
    		this.interval = interval;
    	}
    	
    	public void stop_working () {
			working_permission = false;
			this.interrupt();
    	}
    	
    	public void run () {
    		logging("DownStreamThread("+ feature +") starts");
    		working_permission = true;
    		data_timestamp = "";
    		while (working_permission) {
    			try {
        			long now = System.currentTimeMillis();
        			if (now - timestamp < interval) {
        				Thread.sleep(interval - (now - timestamp));
        			}
    				timestamp = System.currentTimeMillis();
			        HttpRequest.HttpResponse a = HttpRequest.get(url);
			        if (a.status_code == 200) {
	                	deliver_data(new JSONObject(a.body));
			        }
	            } catch (JSONException e) {
		    		logging("DownStreamThread("+ feature +") JSONException");
	                e.printStackTrace();
    			} catch (InterruptedException e) {
		    		logging("DownStreamThread("+ feature +") interrupted");
					e.printStackTrace();
				}
    		}
    		logging("DownStreamThread("+ feature +") stops");
    	}
    	
    	private void deliver_data (JSONObject data) throws JSONException {
    		if (data.getString("timestamp").equals(data_timestamp)) {
    			return;
    		}
    		if (data.getJSONArray("data").length() == 0) {
    			// server responded, but the container is empty
    			return;
    		}
    		if (data.getJSONArray("data").get(0).equals(null)) {
    			return;
    		}
    		data_timestamp = data.getString("timestamp");
    		// We got new data
            Message msgObj = subscriber.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putParcelable("dataset", new DataSet(data));
            msgObj.setData(bundle);
            subscriber.sendMessage(msgObj);
    	}
    }
    
    static public class DataSet implements Parcelable {
    	public String timestamp;
    	private JSONArray dataset;
    	private JSONArray dataset_timestamp;
    	
    	public DataSet (Parcel in) {
    		this.timestamp = in.readString();
			this.dataset = null;
			this.dataset_timestamp = null;
			
			try {
				this.dataset = new JSONArray(in.readString());
				this.dataset_timestamp = new JSONArray(in.readString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
    	}
    	
    	public DataSet (JSONObject in) {
			this.timestamp = "";
			this.dataset = null;
			this.dataset_timestamp = null;
			
    		try {
				this.timestamp = in.getString("timestamp");
				this.dataset = in.getJSONArray("data");
				this.dataset_timestamp = in.getJSONArray("timestamp_full");
			} catch (JSONException e) {
				e.printStackTrace();
			}
    	}
    	
    	public Data newest () {
    		Data ret = new Data();
    		try {
				ret.timestamp = this.dataset_timestamp.getString(0);
	    		ret.data = this.dataset.get(0);
			} catch (JSONException e) {
				e.printStackTrace();
			}
    		return ret;
    	}
    	
    	@Override
    	public String toString () {
    		JSONObject ret = new JSONObject();
    		try {
				ret.put("timestamp", this.timestamp);
	    		ret.put("data", this.dataset);
	    		ret.put("timestamp_full", this.dataset_timestamp);
	    		return ret.toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
    		return "";
    	}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(this.timestamp);
			dest.writeString(this.dataset.toString());
			dest.writeString(this.dataset_timestamp.toString());
		}
		
		public static final Parcelable.Creator<DataSet> CREATOR
		        = new Parcelable.Creator<DataSet>() {
		    public DataSet createFromParcel(Parcel in) {
		        return new DataSet(in);
		    }
		
		    public DataSet[] newArray(int size) {
		        return new DataSet[size];
		    }
		};
    }
    
    static public class Data {
    	public String timestamp;
    	public Object data;
    	
    	public Data () {
    		this.timestamp = "";
    		this.data = null;
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
    
    static private class EasyConnectDataObject {
    	private Object value;
    	
    	static private boolean is_double_array (Object obj) {
    		if (obj instanceof JSONArray) {
				try {
	    			JSONArray tmp = (JSONArray)obj;
	    			for (int i = 0; i < tmp.length(); i++) {
						if (!(tmp.get(i) instanceof Double)) {
				    		return false;
						}
	    			}
	    			// YA, it's double array
	    			return true;
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
    		if (obj instanceof double[]) {
    			return true;
    		} else if (obj instanceof float[]) {
    			return true;
    		}
    		return false;
    	}
    	
    	static private double[] to_double_array (Object obj) throws JSONException {
    		if (obj instanceof JSONArray) {
    			JSONArray tmp = (JSONArray)obj;
    			int length = tmp.length();
    			double[] ret = new double[length];
    			for (int i = 0; i < length; i++) {
					ret[i] = tmp.getDouble(i);
    			}
    			return ret;
    		} else if (obj instanceof float[]) {
    			float[] tmp = (float[])obj;
    			int length = tmp.length;
    			double[] ret = new double[length];
    			for (int i = 0; i < length; i++) {
					ret[i] = (double)tmp[i];
    			}
    			return ret;
    		}
    		return null;
    	}
    	
    	public EasyConnectDataObject (Object obj) {
    		if (is_double_array(obj)) {
    			try {
					value = to_double_array(obj);
				} catch (JSONException e) {
		    		value = obj;
					e.printStackTrace();
				}
    		} else {
    			value = obj;
    		}
    	}
    	
    	@Override
    	public String toString () {
    		if (value instanceof Integer || value instanceof Float || value instanceof Double) {
    			return "["+ value +"]";
    		} else if (value instanceof int[]) {
    			return Arrays.toString((int[])value).replace(" ", "");
    		} else if (value instanceof float[]) {
    			return Arrays.toString((float[])value).replace(" ", "");
    		} else if (value instanceof double[]) {
    			return Arrays.toString((double[])value).replace(" ", "");
    		} else if (value instanceof byte[]) {
    			return Arrays.toString((byte[])value).replace(" ", "");
    		} else if (value instanceof String) {
    			return "\""+ (String)value +"\"";
    		} else if (value instanceof JSONArray) {
    			return ((JSONArray)value).toString().replace(" ", "");
    		} else if (value instanceof JSONObject) {
    			return ((JSONObject)value).toString().replace(" ", "");
    		}
    		return "";
    	}
    	
    	public void accumulate (EasyConnectDataObject obj) {
    		if (!value.getClass().equals(obj.value.getClass())) {
    			return;
    		}
    		
    		if (value instanceof Integer) {
    			value = (int)value + (int)obj.value;
    		} else if (value instanceof Float) {
    			value = (float)value + (float)obj.value;
    		} else if (value instanceof Double) {
    			value = (double)value + (double)obj.value;
    		} else if (value instanceof int[]) {
    			for (int i = 0; i < ((int[])value).length; i++) {
    				((int[])value)[i] += ((int[])obj.value)[i];
    			}
    		} else if (value instanceof float[]) {
    			for (int i = 0; i < ((float[])value).length; i++) {
    				((float[])value)[i] += ((float[])obj.value)[i];
    			}
    		} else if (value instanceof double[]) {
    			for (int i = 0; i < ((double[])value).length; i++) {
    				((double[])value)[i] += ((double[])obj.value)[i];
    			}
    		}
    	}
    	
    	public void average (int count) {
    		if (value instanceof Integer) {
    			value = (int)((int)value / count);
    		} else if (value instanceof Float) {
    			value = (float)value / count;
    		} else if (value instanceof Double) {
    			value = (double)value / count;
    		} else if (value instanceof int[]) {
    			for (int i = 0; i < ((int[])value).length; i++) {
    				((int[])value)[i] = (int)(((int[])value)[i] / count);
    			}
    		} else if (value instanceof float[]) {
    			for (int i = 0; i < ((float[])value).length; i++) {
    				((float[])value)[i] = ((float[])value)[i] / count;
    			}
    		} else if (value instanceof double[]) {
    			for (int i = 0; i < ((double[])value).length; i++) {
    				((double[])value)[i] = ((double[])value)[i] / count;
    			}
    		} else if (value instanceof JSONArray) {
    			return; // good JSONArray is translated into double[]
    		} else if (value instanceof byte[]) {
    			return;	// don't accumulate byte[]
    		} else if (value instanceof String) {
    			return;	// don't know now to accumulate String
    		} else if (value instanceof JSONObject) {
    			return;	// don't know now to accumulate JSONObject
    		}
    	}
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
    
    static public String get_d_name () {
    	try {
    		if (profile == null) {
    			return "Error";
    		}
			return profile.getString("d_name");
		} catch (JSONException e) {
			e.printStackTrace();
		}
    	return "Error";
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

    static public void push_data (String feature, Object data) {
    	EasyConnectDataObject ary = new EasyConnectDataObject(data);
        push_data(feature, ary);
    }
    
    static private void push_data (String feature, EasyConnectDataObject data) {
    	if (!upstream_thread_pool.containsKey(feature)) {
    		UpStreamThread ust = new UpStreamThread(feature);
    		upstream_thread_pool.put(feature, ust);
    		ust.start();
    	}
    	UpStreamThread ust = upstream_thread_pool.get(feature);
    	ust.enqueue(data);
    }
    
    static public void subscribe (String feature, Handler callback) {
    	subscribe(feature, callback, 150);
    }
    
    static public void subscribe (String feature, Handler callback, int interval) {
    	if (!downstream_thread_pool.containsKey(feature)) {
    		DownStreamThread dst = new DownStreamThread(feature, callback, interval);
    		downstream_thread_pool.put(feature, dst);
    		dst.start();
    	}
    }
    
    static public void unsubscribe (String feature) {
		DownStreamThread dst = downstream_thread_pool.get(feature);
		if (dst != null) {
			dst.stop_working();
			downstream_thread_pool.remove(feature);
		}
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
		for (String feature: downstream_thread_pool.keySet()) {
			downstream_thread_pool.get(feature).stop_working();
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
		} catch (NullPointerException e) {
			e.printStackTrace();
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
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return false;
    }
}
