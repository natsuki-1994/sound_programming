package com.example_test.strerch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    AudioRecord audioRec = null;
    AudioTrack audioTrack;
    boolean bIsRecording = false;

    /**
     * onCreate で初期化するときに値を代入する
     */
    int bufInSizeByteMin;
    int bufInSizeByte;
    int bufInSizeShort;

    /**
     * SAMPLING_RATE ... サンプリング周波数
     * INDENT        ... 一つ前の工程で未処理のフレーム数
     * TEMPLATE_SIZE ... 相関係数を計算する際に用いるフレーム数
     * P_MIN         ... 音の予想周期の最小値
     * P_MAX         ... 音の予想周期の最大値
     */
    int SAMPLING_RATE = 44100;
    int indent = 0;
    int TEMPLATE_SIZE = 441;
    int P_MIN = 220;
    int P_MAX = 882;

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
        /** API のバージョンをチェック, API version < 23 なら何もしない */
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

                    /**
                     * bufIn と bufOut との時間軸が等しい必要があるみたい？
                     * 具体的には 1s 分のデータを加工して 2s になる場合でも、AudioTrack からの出力は 1s ごとなのでこのままでは リングバッファでうまく処理できない
                     * よってbufIn と bufOut との間に FIFO を挟んで、その差を吸収する。
                     * bufIn       ... 読み取り用バッファ (mono)
                     * bufOut      ... 書き込み用バッファ (stereo・bufIn の 2倍)
                     * bufOutFifo  ... 書き込み待ち用FIFO
                     * bufTemp     ... 前回読み取った中で書き込み待ち用バッファに書き込んでいないものと今回読み取ったもの
                     * bufTempTemp
                     */
                    short bufIn[] = new short[bufInSizeShort];
                    short bufOut[] = new short[bufInSizeShort * 2];
                    LinkedList<Short> bufOutFifo = new LinkedList<>();
//                    short bufTemp[] = new short[bufInSizeShort + P_MAX * 2];
                    short bufTemp[] = new short[bufInSizeShort];
//                    short bufTempTemp[] = new short[P_MAX * 2];

                    while (bIsRecording) {
                        /**
                         * 音声データの読み込み
                         * bufTemp の indent フレーム以降に bufIn をコピー
                         * indent フレームまでは前回のバッファの未処理フレームで埋まっている
                         * これ以降 bufTemp で埋まっているフレームは (indent + bufInSizeShort) フレームまで
                         */
                        audioRec.read(bufIn, 0, bufInSizeShort);
//                        System.arraycopy(bufIn, 0, bufTemp, indent, bufInSizeShort);
                        System.arraycopy(bufIn, 0, bufTemp, 0, bufInSizeShort);

//                        // FFTインスタンス生成
//                        DoubleFFT_1D fft = new DoubleFFT_1D(size);
//
//                        // fftData ifftData
//                        double fftData[] = new double[size];
//                        double fftDataAmp[] = new double[size / 2];
//                        double fftDataPhase[] = new double[size / 2];
//                        double ifftData[] = new double[size];
//                        for (int i = 0; i < size; i++) {
//                            // データ型を変換し -1 - +1 に正規化
//                            fftData[i] = (bufIn[i] * 1.0) / Short.MAX_VALUE;
//                        }
//
//                        // FFT実行
//                        fft.realForward(fftData);
//                        // noise canceling
//                        for (int i = 0; i < size; i += 2) {
//                            // 振幅成分と位相成分
//                            fftDataAmp[i / 2] = Math.sqrt(Math.pow(fftData[i], 2) + Math.pow(fftData[i + 1], 2));
//                            fftDataPhase[i / 2] = Math.atan2(fftData[i + 1], fftData[i]);
//                            // 振幅成分から閾値一律マイナス
//                            fftDataAmp[i / 2] -= 1.0;
//                            if (fftDataAmp[i / 2] < 0) fftDataAmp[i / 2] = 0;
//
//                            ifftData[i] = fftDataAmp[i / 2] * Math.cos(fftDataPhase[i / 2]);
//                            ifftData[i + 1] = fftDataAmp[i / 2] * Math.sin(fftDataPhase[i / 2]);
//                        }
//                        System.arraycopy(fftData, 0, ifftData, 0, size);
//                        // IFFT実行
//                        fft.realInverse(ifftData, true);

                        /**
                         * 音声の伸長スタート
                         */

                        /**
                         * offset0 ... 伸長する前のデータで処理済のフレーム数
                         * offset1 ... 伸長した後のデータのフレーム数
                         * pFound  ... 検出された音の周期
                         */
                        int offset0 = 0;
                        int offset1 = 0;
                        int pFound;  /** 検出した周波数 */

                        /**
                         * al   ... 相関係数を計算するための配列
                         * bl   ... 相関係数を計算するための配列 (al とは tau ずらしている)
                         * cl   ... 2/3 倍速に直したデータを格納する配列
                         * temp ... 足し合わせて相関係数を求める際に用いる一時変数
                         * r    ... 足しあわされた相関係数
                         */
                        short al[] = new short[TEMPLATE_SIZE];
                        short bl[] = new short[TEMPLATE_SIZE];
                        short cl[] = new short[(int) (bufTemp.length * 1.5)];
                        double temp;
                        double r;

//                        while ((offset0 + P_MAX * 2) < (indent + bufInSizeShort)) {
//                            for (int i = 0; i < TEMPLATE_SIZE; i++) {
//                                al[i] = bufTemp[offset0 + i];
//                            }
//                            double rMax = 0.0;  /** 相関係数の Max 値 */
//                            pFound = P_MIN;  /** pFound の初期値は P_MIN */
//
//                            /**
//                             * P_MIN から P_MAX まで tau ずらしながら相関係数を計算
//                             */
//                            for (int tau = P_MIN; tau < P_MAX; tau++) {  //
//                                r = 0;
//                                for (int i = 0; i < TEMPLATE_SIZE; i++) {
//                                    bl[i] = bufTemp[offset0 + tau + i];
//                                }
//                                for (int i = 0; i < TEMPLATE_SIZE; i++) {
//                                    temp = al[i] * bl[i];
//                                    r += temp;
//                                }
//                                if (r > rMax) {
//                                    rMax = r;  /** 自己相関係数のピーク値 */
//                                    pFound = tau;  /** 音データの基本周期 */
//                                }
//                            }
//
//                            /**
//                             * bufTemp の2周期 (offset0 から pFound 2つ分) を cl に3周期分 copy (offset1 から pFound 3つ分)
//                             */
//                            for (int i = 0; i < pFound; i++) {
//                                cl[offset1 + i] = bufTemp[offset0 + i];
//                            }
//                            for (int i = 0; i < pFound; i++) {
//                                cl[offset1 + pFound + i] = (short) (bufTemp[offset0 + i] * (i / pFound));
//                                cl[offset1 + pFound + i] += (short) (bufTemp[offset0 + pFound + i] * (1 - (i / pFound)));
//                            }
//                            for (int i = 0; i < pFound; i++) {
//                                cl[offset1 + 2 * pFound + i] = bufTemp[offset0 + pFound + i];
//                            }
//                            offset0 = offset0 + 2 * pFound;
//                            offset1 = offset1 + 3 * pFound;
//                            Log.v("AudioRecord", "offset0 " + offset0);
//                            Log.v("AudioRecord", "offset1 " + offset1);
//                        }

                        /**
                         * cl で音データで埋まっているのは offset1 フレームまで
                         * offset1 フレームの cl の音データを stereo に拡張して bufOutFifo にコピー
                         */
                        for (int i = 0; i < offset1; i++) {
                            bufOutFifo.offer(cl[i]);
                            bufOutFifo.offer(cl[i]);
                        }

                        /**
                         * offset0 フレームまでしか bufTemp を処理していない
                         * 残りフレーム ((indent + bufInSizeShort) - offset0) フレームを bufTemp の先頭にコピー
                         * indent の値を ((indent + bufInSizeShort) - offset0) に更新
                         * clで音データで埋まっているのはoffset1 番目まで
                         */
//                        for (int i = 0; i < ((indent + bufInSizeShort) - offset0); i++) {
//                            bufTempTemp[i] = bufTemp[offset0 + i];
//                        }
//                        for (int i = 0; i < ((indent + bufInSizeShort) - offset0); i++) {
//                            bufTemp[i] = bufTempTemp[i];
//                        }
//                        indent = (indent + bufInSizeShort) - offset0;


//
//                        for (int i = 0; i < offset1; i++) {
//                            // データ型を変換
//                            bufOut[2 * i] = (short) (cl[i] * Short.MAX_VALUE);
//                            bufOut[2 * i + 1] = (short) (cl[i] * Short.MAX_VALUE);
//                        }
//
//                        /**
//                         * stereo に変更かつ 1/2 倍速 にする
//                         * bufTemp のうち bufOutFifo に含めるのは先頭 bufInSizeShort 分
//                         * 残りの INDENT フレーム分 (bufTemp の bufInSizeShort ～ bufInSizeShort + INDENT) は次回の処理に回すため、bufTemp の先頭にコピーする
//                         */
//                        for (int i = 0; i < bufInSizeShort; i++) {
//                            bufOutFifo.offer(bufTemp[i]);
//                            bufOutFifo.offer(bufTemp[i]);
//                            bufOutFifo.offer(bufTemp[i]);
//                            bufOutFifo.offer(bufTemp[i]);
//                        }
//                        System.arraycopy(bufTemp, bufInSizeShort, bufTempTemp, 0, INDENT);
//                        System.arraycopy(bufTempTemp, 0, bufTemp, 0, INDENT);
                        /**
                         * bufOutFifo から bufOut.length 分だけ audioTrack のリングバッファに入力
                         */
                        for (int j = 0; j < bufOut.length; j++) {
                            bufOut[j] = bufOutFifo.poll(); ///////////////////////////////////////////////////////////////////////////////////////pollしないで引っ張ってくる
                            // わざと処理時間等しくする❓　audiorecordもたぶんリングバッファなので処理時間等しくしないといけないのか...? 周波数シフトを用いるか、マルチスレッドで処理部分を別スレッドにする
                        }
                        audioTrack.write(bufOut, 0, bufOut.length);
                        Log.v("AudioRecord", "bufOutFifoPush " + offset1 * 2);
                        Log.v("AudioRecord", "bufOutFifo.length " + bufOutFifo.size());
                        Log.v("AudioRecord", "bufOut.length " + bufOut.length);
                        Log.v("AudioRecord", "bufTemp.length " + bufTemp.length);
                        Log.v("AudioRecord", "cl.length " + cl.length);
//                        Log.v("AudioRecord", "indent " + audioTrack.getPlaybackHeadPosition());

                    }
                    Log.v("AudioRecord", "stopRecording");
                    audioRec.stop();
                }
            }).start();
        }
    }

    /**
     * スマホを初めて連続して3回以上降ったときに recordingAndPlay メソッド実行
     */
    private static final int FORCE_THRESHOLD = 550;
    private static final int TIME_THRESHOLD = 100;
    private static final int SHAKE_TIMEOUT = 500;
    private static final int SHAKE_DURATION = 100;
    private static final int SHAKE_COUNT = 3;

    private SensorManager mSensorManager;
    public boolean mRegisteredSensor;
    private float mLastX = -1.0f, mLastY = -1.0f, mLastZ = -1.0f;
    private long mLastTime;
    private int mShakeCount = 0;
    private long mLastShake;
    private long mLastForce;
    private int recordingAndPlayFlag = 0;

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        /** SensorManager.SENSOR_ACCELEROMETER = 加速度センサーでなければreturn */
        if (sensorEvent.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        /** 現在時刻を取得 */
        long now = System.currentTimeMillis();

        /** 最後に動かしてから500ms経過、連続していないのでカウントを0に戻す */
        if ((now - mLastForce) > SHAKE_TIMEOUT) {
            mShakeCount = 0;
        }

        /** 最後に動かしてから100ms経過していたら以下の処理 */
        if ((now - mLastTime) > TIME_THRESHOLD) {
            long diff = now - mLastTime;
            float speed = Math.abs(sensorEvent.values[0] + sensorEvent.values[1] + sensorEvent.values[2] - mLastX - mLastY - mLastZ) / diff * 10000;

            /**
             * 350より大きい速度で、振られたのが3回目（以上）でかつ、最後にシェイクを検知してから
             * 100ms以上経過していたら、今の時間を残してシェイク回数を0に戻し、recordingAndPlay メソッドを呼び出す。
             */
            if (speed > FORCE_THRESHOLD) {
                if ((++mShakeCount >= SHAKE_COUNT) && now - mLastShake > SHAKE_DURATION && (recordingAndPlayFlag == 0)) {
                    mLastShake = now;
                    mShakeCount = 0;
                    recordingAndPlayFlag = 1;
                    audioTrack.play();
                    recordingAndPlay();
                    return;
                }
                mLastForce = now;
            }
            mLastTime = now;
            mLastX = sensorEvent.values[0];
            mLastY = sensorEvent.values[1];
            mLastZ = sensorEvent.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        /** 今回は特に設定しない */
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        if (sensors.size() > 0) {
            Sensor sensor = sensors.get(0);
            mRegisteredSensor = mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            mRegisteredSensor = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
            mSensorManager = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRecordAudioPermission();  /** 録音などの Permission 取得を Activity で行う必要がある (最新 API で変更があったらしい) */
        mRegisteredSensor = false;
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);  /** SensorManager の初期化 */

        /**
         * bufInSizeByteMin ... 最低必要となるバッファ数, 端末ごとに異なる (今回は使用していない)
         * bufInSizeByte    ... モノラルで Byte 型
         * bufInSizeShort   ... モノラルで Short 型
         */
        bufInSizeByteMin = AudioRecord.getMinBufferSize(
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        bufInSizeByte = 5000 * 2;
        bufInSizeShort = bufInSizeByte / 2;

        /**
         * AudioRecord の初期化
         *  マイクからの標準入力
         *  サンプリング周波数 ... 44100
         *  チャンネル数       ... 1
         *  量子化ビット数     ... 16
         *  バッファ Byte 数   ... bufInSizeByte
         */
        audioRec = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufInSizeByte);
        /**
         * AudioTrack の初期化
         *  電話の耳からの出力
         *  サンプリング周波数 ... 44100
         *  チャンネル数       ... 2 (モノラルだとエラーになる)
         *  量子化ビット数     ... 16
         *  バッファ Byte 数   ... bufInSizeByte * 2 (チャンネル数が 2 になったため)
         *  再生モード         ... ストリーミング (リングバッファ使用)
         */
        audioTrack = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufInSizeByte * 2,
                AudioTrack.MODE_STREAM);

//        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                audioTrack.play();
//                recordingAndPlay();
//            }
//        });
    }
}
