package com.example.smartphone;

import org.json.JSONArray;
import org.json.JSONException;

import DAN.DAN;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class AccelerometerService extends Service implements SensorEventListener {
    static final int SENSOR_TYPE = Sensor.TYPE_ACCELEROMETER;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    long timestamp;
    
    static private boolean running = false;
    static private boolean working = false;
    
    private final IBinder mBinder = new MyBinder();
    public class MyBinder extends Binder {
        AccelerometerService getService() {
            return AccelerometerService.this;
        }
    }
    
    public AccelerometerService () {
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
                logging("Accelerometer available");
            } else {
                logging("Accelerometer not available");
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

        float[] data = new float[3];
        data[0] = event.values[0];
        data[1] = event.values[1];
        data[2] = event.values[2];
        DAN.push("Acceleration", data);
        logging(String.format("push(%.10f, %.10f, %.10f)", data[0], data[1], data[2]));
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
        Log.i(Constants.log_tag, "[AccelerometerService] " + message);
    }
}