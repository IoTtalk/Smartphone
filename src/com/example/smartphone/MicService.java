package com.example.smartphone;

import org.json.JSONArray;
import org.json.JSONObject;

import DAN.DAN;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

public class MicService extends Service {
    long timestamp;
    
    static private boolean running = false;
    static private boolean working = false;
    HandlerThread handler_thread;
    Handler data_handler;
    
    private final IBinder mBinder = new MyBinder();
    public class MyBinder extends Binder {
        MicService getService() {
            return MicService.this;
        }
    }
    
    public MicService () {
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
        if ( !working ) {
            logging("MicRecordThread start");
            new MicRecordThread().start();
        } else {
            logging("already initialized");
        }
        return Service.START_NOT_STICKY;
    }

    class MicRecordThread extends Thread {
	    private AudioRecord ar;
	    private int bs = 100;
	    private static final int SAMPLE_RATE_IN_HZ = 8000;//採集頻率範圍0~8000
	    private int number = 1;
	    private int tal = 1;
	    private long currenttime;  //開始獲取聲音資料當前時間
	    private long endtime;       //獲取聲音資料結束的當前時間
	    private long time = 1;
	
	    //到達該值之後 觸發事件
	    private static final int BLOW_ACTIVI=3000;
	
	//    public MicRecordThread(Handler myHandler) {
	    public MicRecordThread() {
	        super();
	        bs = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
	                AudioFormat.CHANNEL_CONFIGURATION_MONO,
	                AudioFormat.ENCODING_PCM_16BIT);
	        ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_IN_HZ,
	                AudioFormat.CHANNEL_CONFIGURATION_MONO,
	                AudioFormat.ENCODING_PCM_16BIT, bs);
	//        handler = myHandler;
	    }
	
	    @Override
	    public void run() {
	        try {
	        	if (working) { return; }
	            working = true;
	            ar.startRecording();
	            //Parameter.isblow = true;
	            // 用於讀取的 buffer
	            byte[] buffer = new byte[bs];
	            //while (Parameter.isblow) {
	            while (working) {
	                number++;
	                sleep(250);
	                currenttime = System.currentTimeMillis();
	                int r = ar.read(buffer, 0, bs);
	                // got raw data
	                JSONObject data = new JSONObject();
	                JSONArray ary = new JSONArray();
	                for (int i = 0; i < buffer.length; i++) {
	                	ary.put(buffer[i]);
	                }
	                data.put("data", ary);
	                DAN.push("Raw-mic", data);
	                
	                int v = 0;
	                for (int i = 0; i < buffer.length; i++) {
	                    v += (buffer[i] * buffer[i]);
	                }
	                int value = Integer.valueOf(v / (int) r);
	                tal = tal + value;
	                endtime = System.currentTimeMillis();
	                time = time + (endtime - currenttime);
	                // 平方和除以資料總長度，得到音量大小。可以獲取白色雜訊值，然後對實際採樣進行標準化
	                // 如果想利用這個數值進行操作，建議用 sendMessage 將其拋出，在 Handler 裡進行處理。
	
	                //if (time >= 500 && number > 5)
	                //{
	
	                int total = tal / number;
	                //轉換為分貝數
	                double dB = 10 * Math.log10( v / (double)r );
	                
	                //if (total > BLOW_ACTIVI)
	                if ( dB >= 0 && dB <= 250 ) {
	                	DAN.push("Microphone", dB * 10);
	                	logging("push_data(\"Microphone\", ["+ (dB * 10) +"])");
	                    number = 1;
	                    tal = 1;
	                    time = 1;
	                }
	                //}
	
	            }
	            ar.stop();
	            ar.release();
	            bs=100;
	
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	}

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }
    
    @Override
    public void onDestroy () {
        running = false;
        working = false;
        
    }

    private void logging (String message) {
        
        Log.i(C.log_tag, "[MicService] " + message);
    }
    
}