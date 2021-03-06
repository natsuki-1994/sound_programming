package com.example_test.strerch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
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

import java.io.IOException;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {

    AudioRecord audioRec = null;
    AudioTrack audioTrack;
    boolean bIsRecording = false;
    /**
     * 音楽再生用
     */
    AudioTrack audioTrackMusic;
    byte[] mByteArray = null;

    int bufInSizeByteMin;
    int bufInSizeByte;
    int bufInSizeShort;

    int SAMPLING_RATE = 44100;

    int playState = 0;  /** stop : 0 , play : 1, slow : 2 */

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

                    while (bIsRecording) {
                        /**
                         * 音声データの読み込み
                         */
                        audioRec.read(bufIn, 0, bufInSizeShort);

                        if (playState == 1) {  /** state : normal */
                            /**
                             * stereo に変更する
                             */
                            for (int i = 0; i < bufInSizeShort; i++) {
                                bufOutFifo.offer(bufIn[i]);
                                bufOutFifo.offer(bufIn[i]);
                            }
                        } else if (playState == 2) {  /** state : slow */
                            /**
                             * stereo に変更かつ 1/2 倍速 にする
                             */
                            for (int i = 0; i < bufInSizeShort; i++) {
                                bufOutFifo.offer(bufIn[i]);
                                bufOutFifo.offer(bufIn[i]);
                                bufOutFifo.offer(bufIn[i]);
                                bufOutFifo.offer(bufIn[i]);
                            }
                        }

                        if (!bIsRecording) {
                            break;
                        }
                        /**
                         * bufOutFifo から bufOut.length 分だけ audioTrack のリングバッファに入力
                         */
                        for (int j = 0; j < bufOut.length; j++) {
                            bufOut[j] = bufOutFifo.poll();
                        }
                        audioTrack.write(bufOut, 0, bufOut.length);

                    }
                    Log.v("AudioRecord", "stopRecording");
                    audioRec.stop();
                    bufOutFifo.clear();
                }
            }).start();
        }
    }

    private void musicPlay() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                /** AudioTrack を再生状態に */
                Log.v("audio", "start");
                audioTrackMusic.play();

                Log.v("audio", String.valueOf(mByteArray.length));
                /** 音声データを書き込む */
                audioTrackMusic.write(mByteArray, 46, mByteArray.length - 46);
                Log.v("audio", "write");
                /** AudioTrack を停止 */
                audioTrackMusic.stop();

                /** AudioTrack をフラッシュ */
                audioTrackMusic.flush();
            }
        }).start();
    }

//    /**
//     * スマホを初めて連続して3回以上降ったときに recordingAndPlay メソッド実行
//     */
//    private static final int FORCE_THRESHOLD = 550;
//    private static final int TIME_THRESHOLD = 100;
//    private static final int SHAKE_TIMEOUT = 500;
//    private static final int SHAKE_DURATION = 100;
//    private static final int SHAKE_COUNT = 3;
//
//    private SensorManager mSensorManager;
//    public boolean mRegisteredSensor;
//    private float mLastX = -1.0f, mLastY = -1.0f, mLastZ = -1.0f;
//    private long mLastTime;
//    private int mShakeCount = 0;
//    private long mLastShake;
//    private long mLastForce;
//    private int recordingAndPlayFlag = 0;
//
//    @Override
//    public void onSensorChanged(SensorEvent sensorEvent) {
//        /** SensorManager.SENSOR_ACCELEROMETER = 加速度センサーでなければreturn */
//        if (sensorEvent.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
//
//        /** 現在時刻を取得 */
//        long now = System.currentTimeMillis();
//
//        /** 最後に動かしてから500ms経過、連続していないのでカウントを0に戻す */
//        if ((now - mLastForce) > SHAKE_TIMEOUT) {
//            mShakeCount = 0;
//        }
//
//        /** 最後に動かしてから100ms経過していたら以下の処理 */
//        if ((now - mLastTime) > TIME_THRESHOLD) {
//            long diff = now - mLastTime;
//            float speed = Math.abs(sensorEvent.values[0] + sensorEvent.values[1] + sensorEvent.values[2] - mLastX - mLastY - mLastZ) / diff * 10000;
//
//            /**
//             * 350より大きい速度で、振られたのが3回目（以上）でかつ、最後にシェイクを検知してから
//             * 100ms以上経過していたら、今の時間を残してシェイク回数を0に戻し、recordingAndPlay メソッドを呼び出す。
//             */
//            if (speed > FORCE_THRESHOLD) {
//                if ((++mShakeCount >= SHAKE_COUNT) && now - mLastShake > SHAKE_DURATION && (recordingAndPlayFlag == 0)) {
//                    mLastShake = now;
//                    mShakeCount = 0;
//                    recordingAndPlayFlag = 1;
//                    audioTrack.play();
//                    recordingAndPlay();
//                    return;
//                }
//                mLastForce = now;
//            }
//            mLastTime = now;
//            mLastX = sensorEvent.values[0];
//            mLastY = sensorEvent.values[1];
//            mLastZ = sensorEvent.values[2];
//        }
//    }
//
//    @Override
//    public void onAccuracyChanged(Sensor sensor, int i) {
//        /** 今回は特に設定しない */
//    }

    @Override
    protected void onResume() {
        super.onResume();
//        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
//        if (sensors.size() > 0) {
//            Sensor sensor = sensors.get(0);
//            mRegisteredSensor = mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
//            mRegisteredSensor = true;
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        if (mSensorManager != null) {
//            mSensorManager.unregisterListener(this);
//            mSensorManager = null;
//        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRecordAudioPermission();  /** 録音などの Permission 取得を Activity で行う必要がある (最新 API で変更があったらしい) */
//        mRegisteredSensor = false;
//        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);  /** SensorManager の初期化 */

        /**
         * bufInSizeByteMin ... 最低必要となるバッファ数, 端末ごとに異なる (今回は使用していない)
         * bufInSizeByte    ... モノラルで Byte 型
         * bufInSizeShort   ... モノラルで Short 型
         */
        bufInSizeByteMin = AudioRecord.getMinBufferSize(
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        bufInSizeByte = 5000;
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
        audioTrackMusic = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLING_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufInSizeByte * 2,
                AudioTrack.MODE_STREAM
        );

        AssetManager assetManager = getAssets();
        AssetFileDescriptor assetFileDescriptor = null;
        try {
            assetFileDescriptor = assetManager.openFd("PCM-D50_441kHz16bit.wav");
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert assetFileDescriptor != null;
        mByteArray = new byte[(int)assetFileDescriptor.getLength()];
        Log.v("Audio", String.valueOf(mByteArray.length));

        findViewById(R.id.button_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playState == 0) {  /** state : stop */
                    playState = 1;  /** state -> normal  */
                    audioTrack.play();
                    recordingAndPlay();
                    musicPlay();
                } else if (playState == 2) {  /** state : slow */
                    bIsRecording = false;
                    audioTrack.stop();
                    playState = 1;  /** state -> normal  */
                    audioTrack.play();
                    recordingAndPlay();
                }  /** state : start ... 何もしない */
            }
        });

        findViewById(R.id.button_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playState == 1 | playState == 2) {  /** state : normal or state : slow */
                    audioTrack.stop();
                    bIsRecording = false;
                    playState = 0;  /** state -> stop  */
                }  /** state : stop ... 何もしない */
            }
        });

        findViewById(R.id.button_slow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playState == 0) {  /** state : stop */
                    playState = 2;  /** state -> slow  */
                    audioTrack.play();
                    recordingAndPlay();
                } else if (playState == 1) {  /** state : normal */
                    bIsRecording = false;
                    audioTrack.stop();
                    playState = 2;  /** state -> slow  */
                    audioTrack.play();
                    recordingAndPlay();
                }  /** state : slow ... 何もしない */
            }
        });
//        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                audioTrack.play();
//                recordingAndPlay();
//            }
//        });
    }
}
