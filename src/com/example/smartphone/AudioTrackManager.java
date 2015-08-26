package com.example.smartphone;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class AudioTrackManager {
	//聲音維持的秒數
	private int duration = 1;
	//每秒採樣數據(撥放數度)
    private int sampleRate = 8000;
    //所以，數學上總共需要duration * sampleRate這麼多點的採樣數據(離散化後的正弦函數值)
    private int numSamples = duration * sampleRate;
    //儲存正弦函數值的陣列
    private double sample[] = new double[numSamples];
    //想要產生聲音的頻率(單位：HZ)
    private double freqOfTone = 400;
    //使用AudioFormat.ENCODING_PCM_16BIT，所以要乘2(* numSamples)
    //儲存真正撥放的PCM數據
    private byte generatedSnd[] = new byte[2 * numSamples];
    //創建AudioTrack撥放PCM數據
    AudioTrack audioTrack;
    public boolean isPlaySound = true;
    public AudioTrackManager()
    {
    	
	}
    void setTone(double freqOfTone)
    {
    	this.freqOfTone = freqOfTone;
    }
    //建立單一音階PCM數據
	void genTone(){
        //建立正弦函數離散化的數據
		sample = Waveshape.sin(sample, numSamples, sampleRate, freqOfTone);
        // 轉換成 16 bit pcm 聲音數據陣列
        // 由於正弦函數是歸一化函數，震幅太小，需要放大
        int idx = 0;
        for (final double dVal : sample) {
            //放大震幅
            final short val = (short) ((dVal * 32767));
            //在 16 bit wav PCM的數據裡面, 第一個byte是低位byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            //先位移再去儲存高位元的數據
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }
    
    //正式撥放
    void playSound(){
    	//AudioTrack.MODE_STREAM可以等待數據寫入，不過一定要等到播完才會停止
    	//AudioTrack.MODE_STATIC一定要先有數據才可以play
    	pause();
    	if(audioTrack==null)
    	{
	        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
	                sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
	                AudioFormat.ENCODING_PCM_16BIT, numSamples,
	                AudioTrack.MODE_STATIC);
    	}
        //audioTrack.write(generatedSnd, 0, generatedSnd.length);
       
    	audioTrack.write(generatedSnd, 0, generatedSnd.length);
    	//無限輪迴撥放，一定要generatedSnd.length/4，-1表無限次的撥放。
    	audioTrack.setLoopPoints(0, generatedSnd.length/4, -1);
    	audioTrack.play();
    	/*
        new Thread(new Runnable() 
        {
            
            public void run() 
            {
                // TODO Auto-generated method stub
                while(isPlaySound)
                {

                }
            }
        }).start();
        */
    }
	/**
	 * 停止撥放
	 */
	public void stop()
	{
		if(audioTrack!=null)
		{
			audioTrack.pause();
			audioTrack.release();
			audioTrack=null;
		}
	}
	public void pause(){
		if(audioTrack!=null)
		{
			audioTrack.pause();
		}
	}

}
