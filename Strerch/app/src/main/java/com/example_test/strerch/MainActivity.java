package com.example_test.strerch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import org.jtransforms.fft.DoubleFFT_1D;

public class MainActivity extends AppCompatActivity {

    AudioRecord audioRec = null;
    boolean bIsRecording = false;
    int bufSize;
    int samplingRate = 44100;
    AudioTrack audioTrack;

//    int fftSize = 4096;
//    int fftOverlap = 2;
//    int fftShift = fftSize / fftOverlap;
//    double threshold = 0.5;
//    double hanningWindow[] = new double[fftSize];

//    void createHunningWindow() {
//        for (int a = 0; a < fftSize; a++) {
//            hanningWindow[a] = 0.5 - 0.5 * Math.cos(2 * Math.PI * (a / fftSize));
//        }
//    }

    private void requestRecordAudioPermission(){
        // TODO: check API version, do not do this if API version < 23!
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private void recordingAndPlay() {
        if (bIsRecording) {
            bIsRecording = false;
        } else {
            Log.v("AudioRecord", "startRecording");
            audioRec.startRecording();
            bIsRecording = true;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    // 読み込み用バッファ・書き込み用バッファ
                    short bufIn[] = new short[bufSize / 2];  // mono かつ short型
                    short bufOut[] = new short[bufSize];  // stereo かつ short型

                    while (bIsRecording) {
                        // 音声データの読み込み
                        int size = audioRec.read(bufIn, 0, bufIn.length);
                        Log.v("AudioRecord", "fft_size" + size);

                        // FFTインスタンス生成
                        DoubleFFT_1D fft = new DoubleFFT_1D(size);

                        // fftData ifftData
                        double fftData[] = new double[size];
                        double fftDataAmp[] = new double[size / 2];
                        double fftDataPhase[] = new double[size / 2];
                        double ifftData[] = new double[size];
                        for (int i = 0; i < size; i++) {
                            // データ型を変換し -1 - +1 に正規化
                            fftData[i] = (bufIn[i] * 1.0) / Short.MAX_VALUE;
                        }

                        // FFT実行
                        fft.realForward(fftData);
                        // noise canceling
                        for (int i = 0; i < size; i += 2) {
                            // 振幅成分と位相成分
                            fftDataAmp[i / 2] = Math.sqrt(Math.pow(fftData[i], 2) + Math.pow(fftData[i + 1], 2));
                            fftDataPhase[i / 2] = Math.atan2(fftData[i + 1], fftData[i]);
                            // 振幅成分から閾値一律マイナス
                            fftDataAmp[i / 2] -= 0.5;
                            if (fftDataAmp[i / 2] < 0) fftDataAmp[i / 2] = 0;

                            ifftData[i] = fftDataAmp[i / 2] * Math.cos(fftDataPhase[i / 2]);
                            ifftData[i + 1] = fftDataAmp[i / 2] * Math.sin(fftDataPhase[i / 2]);
                        }
                        // IFFT実行
                        fft.realInverse(ifftData, true);

                        // data stretch
                        int offset0 = 0;
                        int offset1 = 0;
                        int templateSize = 441;  // samplingRate * 0.01
                        int p_min = 220;  // samplingRate * 0.05
                        int p_max = 882;  // samplingRate * 0.02
                        int p;
                        double al[] = new double[templateSize];
                        double bl[] = new double[templateSize];
                        double cl[] = new double[(int)(size * 1.5)];
                        double temp;
                        double r = 0;

                        while (offset0 + p_max * 2 < size) {
                            for (int i = 0; i < templateSize; i++) {
                                al[i] = ifftData[offset0 + i];
                            }
                            double rMax = 0.0;
                            p = p_min;
                            for (int tau = p_min; tau < p_max; tau++) {
                                r = 0;
                                for (int i = 0; i < templateSize; i++) {
                                    bl[i] = ifftData[offset0 + tau + i];
                                }
                                for (int i = 0; i < templateSize; i++) {
                                    temp = al[i] * bl[i];
                                    r += temp;
                                }
                                if (r > rMax) {
                                    rMax = r;  // 自己相関関数のピーク値
                                    p = tau;  // 音データの基本周期
                                }
                            }
                            for (int i = 0; i < p; i++) {
                                cl[offset1 + i] = ifftData[offset0 + i];
                            }
                            for (int i = 0; i < p; i++) {
                                cl[offset1 + p + i] = ifftData[offset0 + i] * (i / p);
                                cl[offset1 + p + i] += ifftData[offset0 + p + i] * (1 - (i / p));
                            }
                            for (int i = 0; i < p; i++) {
                                cl[offset1 + 2 * p + i] = ifftData[offset0 + p + i];
                            }
                            offset0 += p;
                            offset1 += p;

                        }

                        for (int i = 0; i < size; i++) {
                            // データ型を変換
                            bufOut[2 * i] = (short) (ifftData[i] * Short.MAX_VALUE);
                            bufOut[2 * i + 1] = (short) (ifftData[i] * Short.MAX_VALUE);
                        }

                        audioTrack.write(bufOut, 0, bufOut.length);
                        Log.v("AudioRecord", "read" + bufIn.length + "bytes");
                    }
                    Log.v("AudioRecord", "stopRecording");
                    audioRec.stop();
                }
            }).start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRecordAudioPermission();

        bufSize = AudioRecord.getMinBufferSize(
                samplingRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        bufSize = 4096 * 2;
        audioRec = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                samplingRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize);
        audioTrack = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                samplingRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 2,
                AudioTrack.MODE_STREAM);
        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                audioTrack.play();
                recordingAndPlay();
            }
        });
    }
}
