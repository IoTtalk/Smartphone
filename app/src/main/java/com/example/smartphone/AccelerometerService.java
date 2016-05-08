package com.example.smartphone;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONException;

import DAN.DAN;

public class AccelerometerService extends Service implements SensorEventListener {
	static final String local_tag = AccelerometerService.class.getSimpleName();
	
    static final int SENSOR_TYPE = Sensor.TYPE_ACCELEROMETER;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    long timestamp;
    
    static private boolean running = false;
    static private boolean working = false;

    static private final DAN.Reducer reducer = new DAN.Reducer() {
        @Override
        public JSONArray reduce(JSONArray a, JSONArray b, int b_index, int last_index) {
            JSONArray ret = new JSONArray();
            try {
                if (b_index < last_index) {
                    for (int i = 0; i < a.length(); i++) {
                        ret.put(a.getDouble(i) + b.getDouble(i));
                    }
                } else {
                    for (int i = 0; i < a.length(); i++) {
                        ret.put((a.getDouble(i) + b.getDouble(i)) / ((double)last_index));
                    }
                }
                return ret;
            } catch (JSONException e) {
                Utils.logging(local_tag, "Reducer: JSONException");
            }
            return null;
        }
    };
    
    private final IBinder mBinder = new MyBinder();
    public class MyBinder extends Binder {
        AccelerometerService getService() {
            return AccelerometerService.this;
        }
    }
    
    public AccelerometerService () {
        running = true;
        working = false;
        Utils.logging(local_tag, "constructor");
    }
    
    static boolean is_running () {
        return running;
    }
    
    @Override
    public void onCreate () {
        running = true;
        working = false;
        timestamp = 0;
        Utils.logging(local_tag, "onCreate");
        
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.logging(local_tag, "onStartCommand");
        if ( !working ) {
            Utils.logging(local_tag, "getting phone sensor service");
            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(SENSOR_TYPE);
            if ( mSensor != null ) {
                mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
                Utils.logging(local_tag, "Accelerometer available");
            } else {
                Utils.logging(local_tag, "Accelerometer not available");
                return Service.START_NOT_STICKY;
            }
        	working = true;
        } else {
            Utils.logging(local_tag, "already initialized");
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

        float[] data = new float[3];
        data[0] = event.values[0];
        data[1] = event.values[1];
        data[2] = event.values[2];
        DAN.push("Acceleration", data, reducer);
        Utils.logging(local_tag, "push(%3.10f, %3.10f, %3.10f)", data[0], data[1], data[2]);
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
}