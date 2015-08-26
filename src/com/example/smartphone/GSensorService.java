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
        notify_message("constructor");
    }
    
    static boolean is_running () {
        return running;
    }
    
    @Override
    public void onCreate () {
        running = true;
        working = false;
        timestamp = 0;
        notify_message("onCreate");
        
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notify_message("onStartCommand");
        if ( !working ) {

            notify_message("getting phone sensor service");
            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(SENSOR_TYPE);
            if ( mSensor != null ) {
                mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
                notify_message("g-sensor available");
                
            } else {
                notify_message("g-sensor not available");
                return Service.START_NOT_STICKY;
                
            }

            new DataThread().start();
            
        } else {
            notify_message("already initialized");
            
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
        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.
        //final float dT = (event.timestamp - timestamp) * NS2S;
        // Axis of the rotation sample, not normalized yet.
        float axisX = event.values[0];
        float axisY = event.values[1];
        float axisZ = event.values[2];

        // Calculate the angular speed of the sample
        //float omegaMagnitude = (float)Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);
        
        long now = System.currentTimeMillis();
        if (now - timestamp > 150) {
            push_data(axisX, axisY, axisZ);
            
            timestamp = now;
            
        }
        
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
                        notify_message("push_data(" + x + "," + y + "," + z + ")");
                        
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
    
    boolean logging = true;

    private void notify_message (String message) {
        
        if ( !logging ) return;
        
        Log.i(C.log_tag, "[GSensorService] " + message);
    }
    
}