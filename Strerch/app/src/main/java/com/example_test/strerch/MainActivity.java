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
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends FragmentActivity implements SensorEventListener {

    enum FrgmType {fRoot, fAlbum, fArtist}  /** Fragment のタイプ */
    private FrgmType fTop;  /** 現在表示している Fragment を格納 */

    private static Album focusedAlbum;
    private static Artist focusedArtist;

    AudioRecord audioRec = null;
    AudioTrack audioTrack;
    boolean bIsRecording = false;

    int bufInSizeByteMin;
    int bufInSizeByte;
    int bufInSizeShort;

    int SAMPLING_RATE = 44100;

    int playState = 0;  /** stop : 0 , play : 1, slow : 2 */

    private void requestPermission(){
        /** API のバージョンをチェック, API version < 23 なら何もしない */
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
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

//                        if (bIsRecording == false) {
//
//                        }
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
        setContentView(R.layout.activity_main);  /** layout を設定 */
        requestPermission();  /** API23 以上で Permission 取得を Activity で行う */

        /**
         * Fragment の初期化
         * onCreate では activity_main.xml の R.id.root には RootMenu Fragment を配置
         */
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.root, new RootMenu(), "Root");  /** Fragment をここで設定 */
        ft.commit();  /** Fragment をコミット */

        /**
         * Sensor の初期化
         */
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

//        findViewById(R.id.button_start).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (playState == 0) {  /** state : stop */
//                    playState = 1;  /** state -> normal  */
//                    audioTrack.play();
//                    recordingAndPlay();
//                } else if (playState == 2) {  /** state : slow */
//                    bIsRecording = false;
//                    audioTrack.stop();
//                    playState = 1;  /** state -> normal  */
//                    audioTrack.play();
//                    recordingAndPlay();
//                }  /** state : start ... 何もしない */
//            }
//        });
//
//        findViewById(R.id.button_stop).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (playState == 1 | playState == 2) {  /** state : normal or state : slow */
//                    audioTrack.stop();
//                    bIsRecording = false;
//                    playState = 0;  /** state -> stop  */
//                }  /** state : stop ... 何もしない */
//            }
//        });
//
//        findViewById(R.id.button_slow).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (playState == 0) {  /** state : stop */
//                    playState = 2;  /** state -> slow  */
//                    audioTrack.play();
//                    recordingAndPlay();
//                } else if (playState == 1) {  /** state : normal */
//                    bIsRecording = false;
//                    audioTrack.stop();
//                    playState = 2;  /** state -> slow  */
//                    audioTrack.play();
//                    recordingAndPlay();
//                }  /** state : slow ... 何もしない */
//            }
//        });
//        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                audioTrack.play();
//                recordingAndPlay();
//            }
//        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * param に指定した Fragment を activity_main.xml の R.id.root にセットするメソッド
     */
    public void setNewFragment(FrgmType CallFragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        fTop = CallFragment;
        switch (CallFragment) {
            case fRoot : ft.replace(R.id.root, new RootMenu(), "Root"); break;
            case fAlbum : ft.replace(R.id.root, new AlbumMenu(), "album"); break;
            case fArtist : ft.replace(R.id.root, new AlbumMenu(), "artist"); break;
        }
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        ft.addToBackStack(null);
        ft.commit();
    }

    /**
     * focusAlbum ...
     *   AlbumClickListener で呼ばれる
     *   Album 一覧メニューでクリックされた item を focusedAlbum に格納
     * getFocusedAlbum ...
     *   AlbumMenu.java で呼ばれる
     *   focusedAlbumを返すメソッド
     */
    public void	focusAlbum(Album item) { if (item != null) focusedAlbum = item; }
    public Album getFocusedAlbum() { return focusedAlbum; }
    public void focusArtist(Artist item) { if (item != null) focusedArtist = item; }
    public Artist getFocusedArtist() { return focusedArtist; }

    /**
     * アルバム一覧をクリックしたときの動作
     * クリックした AdapterView の親要素が ListView
     * focusAlbum を実行し、AlbumMenu Fragment に変更
     * focusedAlbum は AlbumMenu 内で使用
     */
    public AdapterView.OnItemClickListener AlbumClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ListView lv = (ListView) parent;
            focusAlbum((Album) lv.getItemAtPosition(position));
            setNewFragment(FrgmType.fAlbum);
        }
    };

    /**
     * アルバム一覧をクリックしたときの動作確認のための LongClick 動作
     */
    public  AdapterView.OnItemLongClickListener AlbumLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            ListView lv = (ListView) parent;
            Album item = (Album) lv.getItemAtPosition(position);
            Toast.makeText(MainActivity.this, "LongClick: " + item.album, Toast.LENGTH_LONG).show();
            return true;
        }
    };

    /**
     * アーティスト一覧をクリックしたときの動作
     * クリックした AdapterView の親要素が ListView
     * focusArtist を実行し、ArtistMenu Fragment に変更
     * focusedArtist は ArtistMenu 内で使用
     */
    public AdapterView.OnItemClickListener ArtistClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ListView lv = (ListView) parent;
            focusArtist((Artist) lv.getItemAtPosition(position));
            setNewFragment(FrgmType.fArtist);
        }
    };

    /**
     * アーティスト一覧をクリックしたときの動作確認のための LongClick 動作
     */
    public  AdapterView.OnItemLongClickListener ArtistLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            ListView lv = (ListView) parent;
            Artist item = (Artist) lv.getItemAtPosition(position);
            Toast.makeText(MainActivity.this, "LongClick: " + item.artist, Toast.LENGTH_LONG).show();
            return true;
        }
    };
}
