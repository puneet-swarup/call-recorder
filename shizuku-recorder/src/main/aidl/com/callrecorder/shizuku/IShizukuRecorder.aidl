package com.callrecorder.shizuku;

import android.os.ParcelFileDescriptor;

interface IShizukuRecorder {

    void destroy() = 16777114;

    void start(in ParcelFileDescriptor sink, int source, int sampleRate) = 1;

    void stop() = 2;

    int peakAmplitude() = 3;

    String lastSource() = 4;
}
