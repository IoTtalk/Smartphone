package com.example.smartphone;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DAN {
    
    // ************************************************ //
    // * Constants or Semi-constants (Seldom changed) * //
    // ************************************************ //
    static public final String version = "20160510";
    static public final String CONTROL_CHANNEL = "Control_channel";
    static private String log_tag = "DAN";
    static private final String dan_log_tag = "DAN";
    static private final String DEFAULT_EC_HOST = "140.113.215.10";
    static private final int EC_BROADCAST_PORT = 17000;
    static private final Long HEART_BEAT_DEAD_MILLISECOND = 3000l;
    static private long request_interval = 150;
    static private boolean initialized;
    

    // ****************** //
    // * Public classes * //
    // ****************** //
    static public enum Event {
        FOUND_NEW_EC,
        REGISTER_FAILED,
        REGISTER_SUCCEED,
        PUSH_FAILED,
        PUSH_SUCCEED,
        PULL_FAILED,
        DEREGISTER_FAILED,
        DEREGISTER_SUCCEED,
    };
    
    static public class ODFObject {
        // data part
        public String timestamp;
        public JSONArray data;
        
        // event part
        public Event event;
        public String message;

        public ODFObject (Event event, String message) {
            this.timestamp = null;
            this.data = null;
            this.event = event;
            this.message = message;
        }

        public ODFObject (String timestamp, JSONArray data) {
            this.timestamp = timestamp;
            this.data = data;
            this.event = null;
            this.message = null;
        }
    }

    static public interface Subscriber {
        void odf_handler(String feature, ODFObject odf_object);
    }
    
    static abstract public class Reducer {
        abstract public JSONArray reduce (JSONArray a, JSONArray b, int b_index, int last_index);
        
        static public final Reducer LAST = new Reducer () {
            @Override
            public JSONArray reduce(JSONArray a, JSONArray b, int b_index, int last_index) {
                return b;
            }
        };
    }
    

    // ******************* //
    // * Private classes * //
    // ******************* //
    
    /*
     * SearchECThread searches EC in the same LAN by receiving UDP broadcast packets
     * 
     * SearchECThread is a Thread singleton class
     * SearchECThread.instance() returns the singleton instance
     *      The instance is created AND RUN after first call
     * 
     * SearchECThread.kill() stops the thread and cleans the singleton instance
     */
    static private class SearchECThread extends Thread {
        static private SearchECThread self = null;
        static private final Semaphore instance_lock = new Semaphore(1);
        
        private DatagramSocket socket;
        
        private SearchECThread () {}
        
        static public SearchECThread instance () {
            try {
                instance_lock.acquire();
                if (self == null) {
                    logging("SearchECThread.instance(): create instance");
                    self = new SearchECThread();
                }
                instance_lock.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return self;
        }

        public void kill () {
            logging("SearchECThread.kill()");
            try {
                instance_lock.acquire();
                if (self == null) {
                    logging("SearchECThread.kill(): not running, skip");
                    return;
                }
                self.socket.close();
                try {
                    self.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                self = null;
                instance_lock.release();
                logging("SearchECThread.kill(): singleton cleaned");
            } catch (InterruptedException e1) {
                logging("SearchECThread.kill(): InterruptedException");
            }
        }

        public void run () {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress("0.0.0.0", DAN.EC_BROADCAST_PORT));
                byte[] lmessage = new byte[20];
                DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);
                while (true) {
                    socket.receive(packet);
                    String input_data = new String( lmessage, 0, packet.getLength() );
                    if (input_data.equals("easyconnect")) {
                        // It's easyconnect packet
                        InetAddress ec_raw_addr = packet.getAddress();
                        String ec_endpoint = "http://"+ ec_raw_addr.getHostAddress() +":9999";
                        synchronized (detected_ec_heartbeat) {
                            if (!detected_ec_heartbeat.containsKey(ec_endpoint)) {
                                logging("FOUND_NEW_EC: %s", ec_endpoint);
                                broadcast_event(Event.FOUND_NEW_EC, ec_endpoint);
                            }
                            detected_ec_heartbeat.put(ec_endpoint, System.currentTimeMillis());
                        }
                    }
                }
            } catch (IOException e) {
                logging("SearchECThread: IOException");
            }
        }
    }
    
    /*
     * SessionThread handles the session status between DAN and EasyConnect.
     *      ``session_status`` records the status,
     *          and this value SHOULD NOT be true after disconnect() and before connect().
     * 
     * SessionThread is a Thread singleton class.
     * SessionThread.instance() returns the singleton instance.
     *      The instance is created AND RUN after first call
     * 
     * SessionThread.connect(String) registers to the given EasyConnect host
     *      This method retries 3 times, between each retry it sleeps 2000 milliseconds
     *      This method is non-blocking, because it notifies subscribers in ``event_subscribers``.
     * 
     * SessionThread.disconnect() deregisters from previous connected EasyConnect host
     *      This method retries 3 times, between each retry it sleeps 2000 milliseconds
     *      This method is blocking, because it doesn't generate events. Re-register also needs it to be blocking.
     * 
     * SessionThread.kill() stops the thread and cleans the singleton instance
     */
    static private class SessionThread extends Thread {
        static private final int RETRY_COUNT = 3;
        static private final int RETRY_INTERVAL = 2000;
        static private final Semaphore instance_lock = new Semaphore(1);
        static private SessionThread self;
        private boolean session_status;
        
        static private class SessionCommand {
            private enum Action {
                REGISTER, DEREGISTER
            }
            
            public Action action;
            public String ec_endpoint;
            public SessionCommand (Action op_code, String ec_endpoint) {
                this.action = op_code;
                this.ec_endpoint = ec_endpoint;
            }
        }

        private final LinkedBlockingQueue<SessionCommand> command_channel =
                new LinkedBlockingQueue<SessionCommand>();
        private final LinkedBlockingQueue<Integer> response_channel =
                new LinkedBlockingQueue<Integer>();
        
        private SessionThread () {}
        
        static public SessionThread instance () {
            try {
                instance_lock.acquire();
                if (self == null) {
                    logging("SessionThread.instance(): create instance");
                    self = new SessionThread();
                }
                instance_lock.release();
            } catch (InterruptedException e) {
                logging("SessionThread.instance(): InterruptedException");
            }
            return self;
        }
        
        public void register (String ec_endpoint) {
            logging("SessionThread.connect(%s)", ec_endpoint);
            SessionCommand sc = new SessionCommand(SessionCommand.Action.REGISTER, ec_endpoint);
            try {
                command_channel.add(sc);
            } catch (IllegalStateException e) {
                logging("SessionThread.connect(): IllegalStateException, command channel is full");
            }
        }
        
        public void deregister () {
            logging("SessionThread.disconnect()");
            SessionCommand sc = new SessionCommand(SessionCommand.Action.DEREGISTER, "");
            try {
                command_channel.add(sc);
                response_channel.take();
            } catch (IllegalStateException e) {
                logging("SessionThread.connect(): IllegalStateException, command channel is full");
            } catch (InterruptedException e) {
                logging("SessionThread.disconnect(): InterruptedException, response_channel is interrupted");
            }
        }
        
        @Override
        public void run () {
            try {
                while (true) {
                    SessionCommand sc = command_channel.take();
                    switch (sc.action) {
                    case REGISTER:
                        logging("SessionThread.run(): REGISTER: %s", sc.ec_endpoint);
                        if (session_status && !CSMapi.ENDPOINT.equals(sc.ec_endpoint)) {
                            logging("SessionThread.run(): REGISTER: Already registered to another EC");
                        } else {
                            CSMapi.ENDPOINT = sc.ec_endpoint;
                            for (int i = 0; i < RETRY_COUNT; i++) {
                                try {
                                    session_status = CSMapi.register(d_id, profile);
                                    logging("SessionThread.run(): REGISTER: %s: %b", CSMapi.ENDPOINT, session_status);
                                    if (session_status) {
                                        break;
                                    }
                                } catch (CSMapi.CSMError e) {
                                    logging("SessionThread.run(): REGISTER: CSMError");
                                }
                                logging("SessionThread.run(): REGISTER: Wait %d milliseconds before retry", RETRY_INTERVAL);
                                Thread.sleep(RETRY_INTERVAL);
                            }
                            
                            if (session_status) {
                                broadcast_event(Event.REGISTER_SUCCEED, CSMapi.ENDPOINT);
                            } else {
                                logging("SessionThread.run(): REGISTER: Give up");
                                broadcast_event(Event.REGISTER_FAILED, CSMapi.ENDPOINT);
                            }
                        }
                        break;
                        
                    case DEREGISTER:
                        logging("SessionThread.run(): DEREGISTER: %s", CSMapi.ENDPOINT);
                        if (!session_status) {
                            logging("SessionThread.run(): DEREGISTER: Not registered to any EC, abort");
                        } else {
                            boolean deregister_success = false;
                            for (int i = 0; i < RETRY_COUNT; i++) {
                                try {
                                    deregister_success = CSMapi.deregister(d_id);
                                    logging("SessionThread.run(): DEREGISTER: %s: %b", CSMapi.ENDPOINT, deregister_success);
                                    if (deregister_success) {
                                        break;
                                    }
                                } catch (CSMapi.CSMError e) {
                                    logging("SessionThread.run(): DEREGISTER: CSMError");
                                }
                                logging("SessionThread.run(): DEREGISTER: Wait %d milliseconds before retry", RETRY_INTERVAL);
                                Thread.sleep(RETRY_INTERVAL);
                            }

                            if (deregister_success) {
                                broadcast_event(Event.DEREGISTER_SUCCEED, CSMapi.ENDPOINT);
                            } else {
                                logging("SessionThread.run(): DEREGISTER: Give up");
                                broadcast_event(Event.DEREGISTER_FAILED, CSMapi.ENDPOINT);
                            }
                            // No matter what result is,
                            //  set session_status to false because I've already retry <RETRY_COUNT> times
                            session_status = false;
                        }
                        response_channel.add(0);
                        break;
                        
                    default:
                        break;
                    }
                }
            } catch (InterruptedException e) {
                logging("SessionThread.run(): InterruptedException");
            }
        }
        
        public void kill () {
            logging("SessionThread.kill()");
            try {
                instance_lock.acquire();
                if (self == null) {
                    logging("SessionThread.kill(): not running, skip");
                    return;
                }
                
                if (status()) {
                    deregister();
                }
                
                self.interrupt();
                try {
                    self.join();
                } catch (InterruptedException e) {
                    logging("SessionThread.kill(): InterruptedException");
                }
                self = null;
                logging("SessionThread.kill(): singleton cleaned");
                instance_lock.release();
            } catch (InterruptedException e1) {
                logging("SessionThread.kill(): InterruptedException");
            }
        }
        
        static public boolean status () {
            if (self != null) {
                return self.session_status;
            }
            return false;
        }
    }

    static private class UpStreamThread extends Thread {
        String feature;
        final LinkedBlockingQueue<JSONArray> queue = new LinkedBlockingQueue<JSONArray>();
        Reducer reducer;

        public UpStreamThread (String feature) {
            this.feature = feature;
            this.reducer = Reducer.LAST;
        }

        public void enqueue (JSONArray data, Reducer reducer) {
            enqueue(data);
            this.reducer = reducer;
        }

        public void enqueue (JSONArray data) {
            try {
                queue.put(data);
            } catch (InterruptedException e) {
                logging("UpStreamThread(%s).enqueue(): InterruptedException", feature);
            }
        }

        public void kill () {
            this.interrupt();
        }

        public void run () {
            logging("UpStreamThread(%s) starts", feature);
            try {
                while (!isInterrupted()) {
                    Thread.sleep(request_interval);

                    JSONArray data = queue.take();
                    int count = queue.size();
                    for (int i = 1; i <= count; i++) {
                        JSONArray tmp = queue.take();
                        data = reducer.reduce(data, tmp, i, count);
                    }
                    
                    if (SessionThread.status()) {
                        logging("UpStreamThread(%s).run(): push %s", feature, data.toString());
                        try {
                            CSMapi.push(d_id, feature, data);
                            broadcast_event(Event.PUSH_SUCCEED, feature);
                        } catch (CSMapi.CSMError e) {
                            logging("UpStreamThread(%s).run(): CSMError", feature);
                            broadcast_event(Event.PUSH_FAILED, feature);
                        }
                    } else {
                        logging("UpStreamThread(%s).run(): skip. (ec_status == false)", feature);
                    }
                }
            } catch (InterruptedException e) {
                logging("UpStreamThread(%s).run(): InterruptedException", feature);
            }
            logging("UpStreamThread(%s) ends", feature);
        }
    }

    static private class DownStreamThread extends Thread {
        String feature;
        Subscriber subscriber;
        String timestamp;

        public DownStreamThread (String feature, Subscriber callback) {
            this.feature = feature;
            this.subscriber = callback;
            this.timestamp = "";
        }
        
        public boolean has_subscriber (Subscriber subscriber) {
            return this.subscriber.equals(subscriber);
        }

        private void deliver_data (JSONArray dataset) throws JSONException {
            logging("DownStreamThread(%s).deliver_data(): %s", feature, dataset.toString());
            if (dataset.length() == 0) {
                logging("DownStreamThread(%s).deliver_data(): No any data", feature);
                return;
            }
            
            String new_timestamp = dataset.getJSONArray(0).getString(0);
            JSONArray new_data = dataset.getJSONArray(0).getJSONArray(1);
            if (new_timestamp.equals(timestamp)) {
                logging("DownStreamThread(%s).deliver_data(): No new data", feature);
                return;
            }
            
            timestamp = new_timestamp;
            subscriber.odf_handler(feature, new ODFObject(new_timestamp, new_data));
        }

        public void kill () {
            this.interrupt();
        }

        public void run () {
            logging("DownStreamThread(%s) starts", feature);
            try {
                while (!isInterrupted()) {
                    try{
                        Thread.sleep(request_interval);
                        if (SessionThread.status()) {
                            logging("DownStreamThread(%s).run(): pull", feature);
                            deliver_data(CSMapi.pull(d_id, feature));
                        } else {
                            logging("DownStreamThread(%s).run(): skip. (ec_status == false)", feature);
                        }
                    } catch (JSONException e) {
                        logging("DownStreamThread(%s).run(): JSONException", feature);
                    } catch (CSMapi.CSMError e) {
                        logging("DownStreamThread(%s).run(): CSMError", feature);
                        broadcast_event(Event.PULL_FAILED, feature);
                    }
                }
            } catch (InterruptedException e) {
                logging("DownStreamThread(%s).run(): InterruptedException", feature);
            }
            logging("DownStreamThread(%s) ends", feature);
        }
    }
    
    
    // ********************** //
    // * Private Containers * //
    // ********************** //
    static private final Set<Subscriber> event_subscribers = Collections.synchronizedSet(new HashSet<Subscriber>());
    static private String d_id;
    static private JSONObject profile;
    static private final ConcurrentHashMap<String, UpStreamThread> upstream_thread_pool = new ConcurrentHashMap<String, UpStreamThread>();
    static private final ConcurrentHashMap<String, DownStreamThread> downstream_thread_pool = new ConcurrentHashMap<String, DownStreamThread>();
    // LinkedHashMap is ordered-map
    static private final Map<String, Long> detected_ec_heartbeat = Collections.synchronizedMap(new LinkedHashMap<String, Long>());
    

    // ************** //
    // * Public API * //
    // ************** //
    
    static public void set_log_tag (String log_tag) {
        DAN.log_tag = log_tag;
        CSMapi.set_log_tag(log_tag);
    }
    
    static public void init (Subscriber init_subscriber) {
        logging("init()");
        if (initialized) {
            logging("init(): Already initialized");
            return;
        }
        SearchECThread.instance().start();
        SessionThread.instance().start();
        
        CSMapi.ENDPOINT = DEFAULT_EC_HOST;
        set_request_interval(150);

        synchronized (event_subscribers) {
            event_subscribers.clear();
            event_subscribers.add(init_subscriber);
        }
        upstream_thread_pool.clear();
        downstream_thread_pool.clear();
        synchronized (detected_ec_heartbeat) {
            detected_ec_heartbeat.clear();
        }
        initialized = true;
    }

    static public String get_clean_mac_addr (String mac_addr) {
        return mac_addr.replace(":", "");
    }

    static public String get_d_id (String mac_addr) {
        return get_clean_mac_addr(mac_addr);
    }

    static public String get_d_name () {
        try {
            if (profile == null) {
                return "Error";
            }
            return profile.getString("d_name");
        } catch (JSONException e) {
            logging("get_d_name(): JSONException");
        }
        return "Error";
    }

    static public void register (String d_id, JSONObject profile) {
        register(CSMapi.ENDPOINT, d_id, profile);
    }
    
    static public void register (String ec_endpoint, String d_id, JSONObject profile) {
        DAN.d_id = d_id;
        DAN.profile = profile;
        if (!DAN.profile.has("is_sim")) {
            try {
                DAN.profile.put("is_sim", false);
            } catch (JSONException e) {
                logging("register(): JSONException");
            }
        }

        SessionThread.instance().register(ec_endpoint);
    }
    
    static public void reregister (String ec_endpoint) {
        logging("reregister(%s)", ec_endpoint);
        SessionThread.instance().deregister();
        SessionThread.instance().register(ec_endpoint);
    }

    static public void push (String feature, double[] data) {
        push(feature, data, Reducer.LAST);
    }

    static public void push (String feature, double[] data, Reducer reducer) {
        JSONArray tmp = new JSONArray();
        for (int i = 0; i < data.length; i++) {
            try {
                tmp.put(data[i]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        push(feature, tmp, reducer);
    }

    static public void push (String feature, float[] data) {
        push(feature, data, Reducer.LAST);
    }

    static public void push (String feature, float[] data, Reducer reducer) {
        JSONArray tmp = new JSONArray();
        for (int i = 0; i < data.length; i++) {
            try {
                tmp.put(data[i]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        push(feature, tmp, reducer);
    }

    static public void push (String feature, int[] data) {
        push(feature, data, Reducer.LAST);
    }
    
    static public void push (String feature, int[] data, Reducer reducer) {
        JSONArray tmp = new JSONArray();
        for (int i = 0; i < data.length; i++) {
            tmp.put(data[i]);
        }
        push(feature, tmp, reducer);
    }
    
    static public void push (String feature, JSONArray data) {
        push(feature, data, Reducer.LAST);
    }
    
    static public void push (String feature, JSONArray data, Reducer reducer) {
        if (!device_feature_exists(feature)) {
            logging("push(%s): feature not exists", feature);
            return;
        }
        if (!upstream_thread_pool.containsKey(feature)) {
            UpStreamThread ust = new UpStreamThread(feature);
            upstream_thread_pool.put(feature, ust);
            ust.start();
        }
        UpStreamThread ust = upstream_thread_pool.get(feature);
        ust.enqueue(data, reducer);
    }

    static public void subscribe (String feature, Subscriber subscriber) {
        if (feature.equals(CONTROL_CHANNEL)) {
            synchronized (event_subscribers) {
                event_subscribers.add(subscriber);
            }
        } else {
            if (!device_feature_exists(feature)) {
                logging("subscribe(%s): feature not exists", feature);
                return;
            }
            if (!downstream_thread_pool.containsKey(feature)) {
                DownStreamThread dst = new DownStreamThread(feature, subscriber);
                downstream_thread_pool.put(feature, dst);
                dst.start();
            }
        }
    }

    static public void unsubscribe (String feature) {
        if (feature.equals(CONTROL_CHANNEL)) {
            synchronized (event_subscribers) {
                event_subscribers.clear();
            }
        } else {
            DownStreamThread down_stream_thread = downstream_thread_pool.get(feature);
            if (down_stream_thread == null) {
                return;
            }
            down_stream_thread.kill();
            try {
                down_stream_thread.join();
            } catch (InterruptedException e) {
                logging("unsubscribe(): DownStreamThread: InterruptedException");
            }
            downstream_thread_pool.remove(feature);
        }
    }

    static public void unsubscribe (Subscriber subscriber) {
        synchronized (event_subscribers) {
            event_subscribers.remove(subscriber);
        }

        for (Map.Entry<String, DownStreamThread> p: downstream_thread_pool.entrySet()) {
            String feature = p.getKey();
            DownStreamThread down_stream_thread = p.getValue();
            if (down_stream_thread.has_subscriber(subscriber)) {
                down_stream_thread.kill();
                try {
                    down_stream_thread.join();
                } catch (InterruptedException e) {
                    logging("unsubscribe(): DownStreamThread: InterruptedException");
                }
                downstream_thread_pool.remove(feature);
                break;
            }
        }
    }

    static public void deregister () {
        SessionThread.instance().deregister();
    }
    
    static public void shutdown () {
        logging("shutdown()");
        if (!initialized) {
            logging("shutdown(): Already shutdown");
            return;
        }
        
        for (Map.Entry<String, UpStreamThread> p: upstream_thread_pool.entrySet()) {
            UpStreamThread t = p.getValue();
            t.kill();
            try {
                t.join();
            } catch (InterruptedException e) {
                logging("shutdown(): UpStreamThread: InterruptedException");
            }
        }
        upstream_thread_pool.clear();

        for (Map.Entry<String, DownStreamThread> p: downstream_thread_pool.entrySet()) {
            DownStreamThread t = p.getValue();
            t.kill();
            try {
                t.join();
            } catch (InterruptedException e) {
                logging("shutdown(): DownStreamThread: InterruptedException");
            }
        }
        downstream_thread_pool.clear();
        
        SearchECThread.instance().kill();
        SessionThread.instance().kill();
        initialized = false;
    }
    
    static public String[] available_ec () {
        ArrayList<String> t = new ArrayList<String>();
        t.add(DEFAULT_EC_HOST);
        synchronized (detected_ec_heartbeat) {
            for (Map.Entry<String, Long> p: detected_ec_heartbeat.entrySet()) {
                if (System.currentTimeMillis() - p.getValue() < HEART_BEAT_DEAD_MILLISECOND) {
                    t.add(p.getKey());
                } else {
                    detected_ec_heartbeat.remove(p);
                }
            }
        }
        return t.toArray(new String[]{});
    }
    
    static public String ec_endpoint () {
        return CSMapi.ENDPOINT;
    }
    
    static public boolean session_status () {
        return SessionThread.status();
    }

    static public long get_request_interval () {
        return DAN.request_interval;
    }

    static public void set_request_interval (long request_interval) {
        if (request_interval > 0) {
            logging("set_request_interval(%d)", request_interval);
            DAN.request_interval = request_interval;
        }
    }


    // ***************************** //
    // * Internal Helper Functions * //
    // ***************************** //
    static private boolean device_feature_exists (String feature) {
        JSONArray df_list = null;
        try {
            df_list = profile.getJSONArray("df_list");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < df_list.length(); i++) {
            try {
                if (df_list.getString(i).equals(feature)) {
                    return true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
    
    static public void logging (String format, Object... args) {
        logging(String.format(format, args));
    }

    static private void logging (String message) {
        System.out.printf("[%s][%s] %s%n", log_tag, dan_log_tag, message);
    }

    static private void broadcast_event (Event event, String message) {
        logging("broadcast_control_message()");
        synchronized (event_subscribers) {
            for (Subscriber handler: event_subscribers) {
                handler.odf_handler(CONTROL_CHANNEL, new ODFObject(event, message));
            }
        }
    }
}

