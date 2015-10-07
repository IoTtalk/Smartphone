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
    
    private final IBinder mBinder = new MyBinder();
    public class MyBinder extends Binder {
        GSensorService getService() {
            return GSensorService.this;
        }
    }
    
    public GSensorService () {
        running = true;
        working = false;
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
        	working = true;
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
            EasyConnect.push_data("G-sensor", data);
            logging(String.format("push_data(%.10f, %.10f, %.10f)", data_x, data_y, data_z));
        } catch (JSONException e) {
            e.printStackTrace();
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