package com.example.smartphone;

public class Waveshape {

    public static double[] sin(double sample[], int numSamples, int sampleRate, double freqOfTone)
    {//建立正弦函數離散化的數據
        for (int i = 0; i < numSamples; ++i)
        {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }
        return sample;
    }

}
