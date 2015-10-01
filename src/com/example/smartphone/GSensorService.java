package com.example.smartphone;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class GSensorService extends Service implements SensorEventListener {
    static final int SENSOR_TYPE = Sensor.TYPE_ACCELEROMETER;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    long timestamp;
    
    static private boolean running = false;
    static private boolean working = false;
    HandlerThread handler_thread;
    Handler data_handler;
    
    // accumulated value
    float[] history_x;
    float[] history_y;
    float[] history_z;
    int history_count;
    
    private final IBinder mBinder = new MyBinder();
    public class MyBinder extends Binder {
        GSensorService getService() {
            return GSensorService.this;
        }
    }
    
    public GSensorService () {
        running = true;
        working = false;
        data_handler = null;
        handler_thread = null;
        logging("constructor");
    }
    
    static boolean is_running () {
        return running;
    }
    
    @Override
    public void onCreate () {
        running = true;
        working = false;
        timestamp = 0;
        logging("onCreate");
        
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logging("onStartCommand");
        if ( !working ) {

            logging("getting phone sensor service");
            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(SENSOR_TYPE);
            if ( mSensor != null ) {
                mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
                logging("g-sensor available");
                
            } else {
                logging("g-sensor not available");
                return Service.START_NOT_STICKY;
                
            }

            new DataThread().start();
            
        } else {
            logging("already initialized");
            
        }
        return Service.START_NOT_STICKY;
    }
    

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != SENSOR_TYPE) {
            return;
        }
        if ( !working ) {
            return;
        }

        float data_x = event.values[0];
        float data_y = event.values[1];
        float data_z = event.values[2];

        JSONArray data = new JSONArray();
        try {
            data.put(data_x);
            data.put(data_y);
            data.put(data_z);
            EasyConnect.push_data2("G-sensor", data);
            logging(String.format("push_data(%.10f, %.10f, %.10f)", data_x, data_y, data_z));
            
        } catch (JSONException e) {
            e.printStackTrace();
        }

//        int r = history_count % history_x.length;
//        history_x[r] = data_x;
//        history_y[r] = data_y;
//        history_z[r] = data_z;
//        
//        history_count += 1;
//        long now = System.currentTimeMillis();
//        if (now - timestamp > 150) {
//        	logging(history_count +"");
//        	float acc_x = 0;
//        	float acc_y = 0;
//        	float acc_z = 0;
//        	for (int i = 0; i < history_count && i < history_x.length; i++) {
//        		acc_x += history_x[i];
//        		acc_y += history_y[i];
//        		acc_z += history_z[i];
//        	}
//        	acc_x = acc_x / history_count;
//        	acc_y = acc_y / history_count;
//        	acc_z = acc_z / history_count;
//            push_data(acc_x, acc_y, acc_z);
//        	history_count = 0;
//            timestamp = now;
//        }
    }
    
    private void push_data (float x, float y, float z) {
        if (data_handler == null) return;
        Message msgObj = data_handler.obtainMessage();
        Bundle b = new Bundle();
        b.putFloat("x", x);
        b.putFloat("y", y);
        b.putFloat("z", z);
        msgObj.setData(b);
        data_handler.sendMessage(msgObj);
        
    }
    
    class DataThread extends Thread {
        @Override
        public void run () {
        	history_x = new float[100];
        	history_y = new float[100];
        	history_z = new float[100];
        	history_count = 0;
        	
        	working = true;
            
            handler_thread = new HandlerThread("MyHandlerThread");
            handler_thread.start();
            
            Looper looper = handler_thread.getLooper();
            
            data_handler = new Handler(looper, new Callback () {
                @Override
                public boolean handleMessage(Message msg) {
                    float x = msg.getData().getFloat("x");
                    float y = msg.getData().getFloat("y");
                    float z = msg.getData().getFloat("z");

                    JSONArray data = new JSONArray();
                    try {
                        data.put(x);
                        data.put(y);
                        data.put(z);
                        EasyConnect.push_data("G-sensor", data);
                        logging("push_data(" + x + "," + y + "," + z + ")");
                        
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    
                    return true;
                }
            });
            
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }
    
    @Override
    public void onDestroy () {
        running = false;
        working = false;
        mSensorManager.unregisterListener(this, mSensor);
        
    }

    private void logging (String message) {
        
        Log.i(C.log_tag, "[GSensorService] " + message);
    }
    
}