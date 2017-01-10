package com.example_test.strerch;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import static com.example_test.strerch.R.id;
import static com.example_test.strerch.R.layout;

public class MainActivity extends FragmentActivity {

    /**
     * Fragment のタイプ
     */
    enum FrgmType {fRoot, fAlbum, fArtist}

    /**
     * Focus されている Album, Artist, Track
     */
    private static Album focusedAlbum;
    private static Artist focusedArtist;
    public Track focusedTrack;
    private Fragment focusedFragment;

    /**
     * Audio 関連の変数
     */
    AudioRecord audioRec = null;
    AudioTrack audioTrack;
    boolean bIsRecording = false;
    int bufInSizeByteMin;
    int bufInSizeByte;
    int bufInSizeShort;
    int SAMPLING_RATE = 44100;
    int playState = 0;  /** stop : 0 , play : 1, slow: 2 */
    int fftSize = 4096;
    int TEMPLATE_SIZE = 441;
    int P_MIN = 220;
    int P_MAX = 882;

    /**
     * MediaPlayer 関連の変数
     */
    MediaPlayer mPlayer;
    int mTotalTime;

    /**
     * API のバージョンをチェック, API version < 23 なら何もしない
     */
    private void requestPermission() {
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

    /**
     * 音楽再生 / 外部音再生
     */
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
                     * bufIn          ... 読み取り用バッファ (mono)
                     * bufInStretched ... 2倍に伸ばした音声（周波数は 1/2 となる）
                     * bufOut         ... 書き込み用バッファ (stereo なので bufIn の 2倍)
                     * bufOutFifo     ... 書き込み待ち用FIFO
                     */
                    short bufIn[] = new short[bufInSizeShort];
                    short bufInStretched[] = new short[bufInSizeShort * 2];
                    short bufOut[] = new short[bufInSizeShort * 2];
                    LinkedList<Short> bufOutFifo = new LinkedList<>();

                    while (bIsRecording) {
                        /**
                         * 音声データの読み込み
                         */
                        audioRec.read(bufIn, 0, bufInSizeShort);

                        if (playState == 1) {  /** state : play */

                            int offset0 = 0;
                            int offset1 = 0;
                            int pFound;

                            short al[] = new short[TEMPLATE_SIZE];
                            short bl[] = new short[TEMPLATE_SIZE];
//                            short cl[] = new short[(int) (bufIn.length * 1.5)];
                            short cl[] = new short[bufIn.length * 2];
                            double temp;
                            double r;

                            while ((offset0 + P_MAX * 2) < bufInSizeShort) {
                                Log.v("AudioTrack", String.valueOf(offset1));
                                Log.v("bufOut.length", String.valueOf(bufOut.length));
                                for (int i = 0; i < TEMPLATE_SIZE; i++) {
                                    al[i] = bufIn[offset0 + i];
                                }
                                double rMax = 0.0;
                                pFound = P_MIN;

                                for (int tau = P_MIN; tau < P_MAX; tau++) {
                                    r = 0;
                                    for (int j = 0; j < TEMPLATE_SIZE; j++) {
                                        bl[j] = bufIn[offset0 + tau + j];
                                    }
                                    for (int j = 0; j < TEMPLATE_SIZE; j++) {
                                        temp = al[j] * bl[j];
                                        r += temp;
                                    }
                                    if (r > rMax) {
                                        rMax = r;
                                        pFound = tau;
                                    }
                                }

                                for (int i = 0; i < pFound; i++) {
                                    cl[offset1 + 2 * i] = bufIn[offset0 + i];
                                    cl[offset1 + 2 * i + i] = bufIn[offset0 + i];
                                }
//                                for (int i = 0; i < pFound; i++) {
//                                    cl[offset1 + pFound + i] = (short) (bufIn[offset0 + i] * (i / pFound));
//                                    cl[offset1 + pFound + i] = (short) (bufIn[offset0 + pFound + i] * (1 - (i / pFound)));
//                                }
//                                for (int i = 0; i < pFound; i++) {
//                                    cl[offset1 + 2 * pFound + i] = bufIn[offset0 + pFound + i];
//                                }
                                offset0 = offset0 + pFound;
                                offset1 = offset1 + 2 * pFound;
                            }

                            for (int i = 0; i < offset1; i++) {
                                bufOutFifo.offer(cl[i]);
                                bufOutFifo.offer(cl[i]);
                            }
                            Log.v("AudioTrack", String.valueOf(offset1));
                            Log.v("bufOut.length", String.valueOf(bufOut.length));

//                            /**
//                             * stereo に変更する
//                             */
//                            for (int i = 0; i < bufInSizeShort; i++) {
//                                bufOutFifo.offer(bufIn[i]);
//                                bufOutFifo.offer(bufIn[i]);
//                            }

//                            DoubleFFT_1D fft = new DoubleFFT_1D(fftSize);
//                            double fftData[] = new double[fftSize];
//                            double ifftData[] = new double[fftSize];
//
//                            /**
//                             * FFT サイズ分だけ取り出し、データ型を short から double に変換し、-1 ～ +1 に正規化
//                             */
//                            for (int j = 0; j < fftSize; j++) {
//                                fftData[j] = (bufIn[j] * 1.0) / Short.MAX_VALUE;
//                            }
//
//                            /**
//                             * FFT 実行
//                             * 変換後のデータは [振幅成分] [位相成分] [振幅成分] [位相成分] [振幅成分] [位相成分] ... の 繰り返しとなる
//                             */
//                            fft.realForward(fftData);
//
////                            for (int j = 0; j < fftSize; j++) {
////                               ifftData[j] = fftData[j];
////                            }
//
//                            /**
//                             * 周波数シフトを行う
//                             */
//                            for (int j = 0; j < fftSize / 2; j += 2) {
//                                ifftData[2 * j] = fftData[j];
//                                ifftData[2 * j + 1] = fftData[j + 1];
//                                if (j > 0) {
//                                    ifftData[2 * j + 2] = (fftData[j] + fftData[j - 2]) / 2;
//                                } else {
//                                    ifftData[2 * j + 2] = fftData[j];
//                                }
//                                ifftData[2 * j + 3] = 0;
//                            }
//
//                            /**
//                             * IFFT 実行
//                             */
//                            fft.realInverse(ifftData, true);
//
//                            /**
//                             * stereo に変更して bufOutFifo にプッシュ
//                             */
//                            for (int j = 0; j < fftSize; j++) {
//                                bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
//                                bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
//                            }
                        } else if (playState == 2) {  /** state : slow */
//                            /**
//                             * 1/2 倍速 にする
//                             */
//                            for (int i = 0; i < bufInSizeShort; i++) {
//                                bufInStretched[2 * i] = bufIn[i];
//                                bufInStretched[2 * i + 1] = bufIn[i];
//                            }
//
//                            /**
//                             * fft 処理
//                             *   fft      ... FFT インスタンスの生成
//                             *   fftData  ... FFT をかけるデータ（double 型）
//                             *   ifftData ... IFFT をかけるデータ（double 型）
//                             */
//                            DoubleFFT_1D fft = new DoubleFFT_1D(fftSize);  /** fftSize = 4096 / bufInStretched = 4096 * 2 */
//                            double fftData[] = new double[fftSize];
//                            double ifftData[] = new double[fftSize];
//
//                            for (int i = 0; i < bufInStretched.length; i += fftSize) {
//                                /**
//                                 * FFT サイズ分だけ取り出し、データ型を short から double に変換し、-1 ～ +1 に正規化
//                                 */
//                                for (int j = 0; j < fftSize; j++) {
//                                    fftData[j] = (bufInStretched[j] * 1.0) / Short.MAX_VALUE;
//                                }
//
//                                /**
//                                 * FFT 実行
//                                 * 変換後のデータは [振幅成分] [位相成分] [振幅成分] [位相成分] [振幅成分] [位相成分] ... の 繰り返しとなる
//                                 */
//                                fft.realForward(fftData);
//
//                                /**
//                                 * 周波数シフトを行う
//                                 */
//                                 for (int j = 0; j < fftSize / 2; j += 2) {
//                                     ifftData[2 * j] = fftData[j];
//                                     ifftData[2 * j + 1] = fftData[j + 1];
//                                     ifftData[2 * j + 2] = fftData[j] / 10;
//                                     ifftData[2 * j + 3] = 0;
//                                 }
//
//                                /**
//                                 * IFFT 実行
//                                 */
//                                fft.realInverse(ifftData, true);
//
//                                /**
//                                 * stereo に変更して bufOutFifo にプッシュ
//                                 */
//                                for (int j = 0; j < fftSize; j++) {
//                                    bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
//                                    bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
//                                }
//                            }
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

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);  /** layout を設定 */
        requestPermission();  /** API23 以上で Permission 取得を Activity で行う */

//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        /**
         * Fragment の初期化
         * onCreate では activity_main.xml の R.id.root には RootMenu Fragment を配置
         */
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(id.root, new RootMenu(), "Root");  /** Fragment をここで設定 */
        ft.commit();  /** Fragment をコミット */

        /**
         * bufInSizeByteMin ... 最低必要となるバッファ数, 端末ごとに異なる (今回は使用していない)
         * bufInSizeByte    ... モノラルで Byte 型
         * bufInSizeShort   ... モノラルで Short 型
         */
        bufInSizeByteMin = AudioRecord.getMinBufferSize(
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
//        bufInSizeByte = fftSize * 2;
//        bufInSizeShort = fftSize;
        bufInSizeByte = 40000 * 2;
        bufInSizeShort = 40000;

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

        /**
         * MediaPlayer の初期化
         */
        mPlayer = MediaPlayer.create(this, R.raw.pcm);
    }

    public void changeSeekbar() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mPlayer != null) {
                        int currentPosition = mPlayer.getCurrentPosition();
                        Message msg = new Message();
                        msg.what = currentPosition;
                        threadHandler.sendMessage(msg);
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Handler threadHandler = new Handler() {
        public void handleMessage(Message msg) {
            final SeekBar mSeekBarPosition = (SeekBar) findViewById(R.id.seekBar);
            mSeekBarPosition.setProgress(msg.what);
            mSeekBarPosition.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    if (b) {
                        mPlayer.seekTo(i);
                        mSeekBarPosition.setProgress(i);
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * param に指定した Fragment を activity_main.xml の R.id.root にセットするメソッド
     */
    public void setNewFragment(FrgmType CallFragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment focusedFragmentAlbum = new AlbumMenu();
        Fragment focusedFragmentArtist = new ArtistMenu();

        switch (CallFragment) {
//            case fRoot : ft.replace(id.root, new RootMenu(), "Root"); break;
//            case fAlbum : ft.replace(id.root, new AlbumMenu(), "album"); break;
//            case fArtist : ft.replace(id.root, new ArtistMenu(), "artist"); break;
            case fRoot : ft.replace(id.root, new RootMenu(), "Root"); break;
            case fAlbum : ft.replace(id.root, new AlbumMenu(), "album"); break;
            case fArtist : ft.replace(id.root, new ArtistMenu(), "artist"); break;
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
    public void focusTrack(Track item) { if (item != null) focusedTrack = item; }
//    public Track getFocusedTrack() { return  focusedTrack; }

    /**
     * アルバム一覧をクリックしたときの動作
     * クリックした AdapterView の親要素が ListView
     * focusAlbum を実行し、AlbumMenu Fragment に変更
     * focusedAlbum は RootMenu 内で使用
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
     * focusedArtist は RootMenu 内で使用
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

    /**
     * トラック一覧をクリックしたときの動作確認のための LongClick 動作
     */
    public  AdapterView.OnItemLongClickListener TrackLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            ListView lv = (ListView) parent;
            Track item = (Track) lv.getItemAtPosition(position);
            focusTrack(item);

            changeInformation();
            try {
                changeTrack();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Toast.makeText(MainActivity.this, "LongClick: " + item.album, Toast.LENGTH_LONG).show();

            return true;
        }
    };
    public void changeInformation() {
        Bitmap album_art_ = null;
        long albumId = focusedTrack.albumId;
        Uri albumArtUri = Uri.parse(
                "content://media/external/audio/albumart");
        Uri albumUri = ContentUris.withAppendedId(albumArtUri, albumId);
        ContentResolver cr = getContentResolver();
        try {
            InputStream is = cr.openInputStream(albumUri);
            album_art_ = BitmapFactory.decodeStream(is);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        ImageView album_art_root = (ImageView) findViewById(R.id.imageViewHome);
        album_art_root.setImageBitmap(album_art_);
        TextView track_root = (TextView) findViewById(R.id.textView_track);
        track_root.setText(focusedTrack.title);
        TextView artist_root = (TextView) findViewById(R.id.textView_artist);
        artist_root.setText(focusedTrack.artist);
    }
    public void changeTrack() throws IOException {
        Button buttonPlayPause =  (Button) findViewById(R.id.c_btn);
        SeekBar mSeekBarPosition = (SeekBar) findViewById(R.id.seekBar);

        if (mPlayer.isPlaying()) {
            mPlayer.stop();
            mPlayer.prepare();
        }
        mPlayer = MediaPlayer.create(this, focusedTrack.uri);
        mPlayer.start();
        mTotalTime = mPlayer.getDuration();
        mSeekBarPosition.setMax(mTotalTime);
        buttonPlayPause.setText(R.string.icon_pause);
    }

    /**
     * ToggleSwitch をクリックしたときの動作
     */
    public SwitchCompat.OnCheckedChangeListener toggleOutsideClickListener = new SwitchCompat.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {  /** isChecked で outside ON */
            Toast.makeText(MainActivity.this, "outside", Toast.LENGTH_SHORT).show();
            if (isChecked) {
                playState = 2;  /** state : play */
                audioTrack.play();
                recordingAndPlay();
            } else {
                playState = 0;  /** state : stop */
                bIsRecording = false;
                audioTrack.stop();
            }
        }
    };

    /**
     * PLAY / PAUSE ボタン押したとき
     */
    public Button.OnClickListener buttonPlayPauseClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            changeInformationPlayPause();
        }
    };
    public void changeInformationPlayPause() {
        Button buttonPlayPause =  (Button) findViewById(R.id.c_btn);
        SeekBar mSeekBarPosition = (SeekBar) findViewById(R.id.seekBar);

        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            buttonPlayPause.setText(R.string.icon_play);
        } else {
            mPlayer.start();
            mTotalTime = mPlayer.getDuration();
            mSeekBarPosition.setMax(mTotalTime);
//            mSeekBarPosition.setProgress(mTotalTime / 2);
            buttonPlayPause.setText(R.string.icon_pause);
        }
    }

//    /**
//     * RootMenu のボタンをクリックしたときの動作
//     */
//    public Button.OnClickListener outsideClickListener = new Button.OnClickListener() {
//        @Override
//        public void onClick(View view) {
//            Toast.makeText(MainActivity.this, "outside", Toast.LENGTH_SHORT).show();
//            if (playState == 0) {  /** state : stop */
//                playState = 1;  /** state : play */
//                audioTrack.play();
//                recordingAndPlay();
//            } else if (playState == 2) {  /** state : slow */
//                bIsRecording = false;
//                audioTrack.stop();
//                playState = 1;  /** state : play */
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                audioTrack.play();
//                recordingAndPlay();
//            }  /** state : play ... 何もしない */
//        }
//    };
//    public Button.OnClickListener slowClickListener = new Button.OnClickListener() {
//        @Override
//        public void onClick(View view) {
//            Toast.makeText(MainActivity.this, "slow", Toast.LENGTH_SHORT).show();
//            if (playState == 0) {  /** state : stop */
//                playState = 2;  /** state : play */
//                audioTrack.play();
//                recordingAndPlay();
//            } else if (playState == 1) {  /** state : play */
//                bIsRecording = false;
//                audioTrack.stop();
//                playState = 2;  /** state : slow */
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                audioTrack.play();
//                recordingAndPlay();
//            } /** state : slow ... 何もしない */
//        }
//    };
}
