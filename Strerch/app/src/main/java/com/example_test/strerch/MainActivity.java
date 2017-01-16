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

import org.jtransforms.fft.DoubleFFT_1D;

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
     * Focus されている Album, Artist, Track, Fragment
     */
    private static Album focusedAlbum = null;
    private static Artist focusedArtist;
    public Track focusedTrack = null;
    public int focusedFragment = 1;  /** 1 : fRoot, 2 : fAlbum, 3 : fArtist */

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
    int fftSize;
    int sizeOfResampling;
    int nbSamplesFadeIO;

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

//                    //Prepare values of the window func (# of value = bufInSizeShort)
//                    double valWindowFunc[] = new double[2 * bufInSizeShort];
//                    for (int i = 0; i < 2 * bufInSizeShort; i++) {
//                        valWindowFunc[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (2 * bufInSizeShort)); //Hamming Window
//                    }

                    while (bIsRecording) {
                        /**
                         * 音声データの読み込み
                         */
                        audioRec.read(bufIn, 0, bufInSizeShort);

                        if (playState == 1) {  /** state : play */
                            for (int j = 0; j < bufIn.length; j++) {
                                bufOutFifo.offer(bufIn[j]);
                                bufOutFifo.offer(bufIn[j]);
                            }
                        } else if (playState == 2) {  /** state : slow */
                            LinkedList<Short> resampleFifo = new LinkedList<>();

                            for (int j = 0; j < bufIn.length; j++) {
                                resampleFifo.offer(bufIn[j]);
                            }

                            short resampleChunk[] = new short[sizeOfResampling];
                            short prevChunkFadeMargin[] = new short[nbSamplesFadeIO];
                            for (int j = 0; j < prevChunkFadeMargin.length; j++) {
                                prevChunkFadeMargin[j] = 0;
                            }

                            int phaseResampling = 0;
                            while (resampleFifo.size() >= sizeOfResampling) {
                                if (phaseResampling == 0 || phaseResampling == 1) {
                                    for (int j = 0; j < resampleChunk.length/*=sizeOfResampling*/; j++) {
                                        resampleChunk[j] = resampleFifo.poll();
                                    }
                                }

                                for (int j = 0; j < nbSamplesFadeIO; j++) {
                                    double r = (double)j / (double)nbSamplesFadeIO;
                                    resampleChunk[j] = (short) Math.min(Short.MAX_VALUE, (short)((1.0 - r) * prevChunkFadeMargin[j] + r * resampleChunk[j]));
                                }

                                //音量が一定値以下なら切る(これは聴いた感じイマイチだった…)
//                                double sqsumAvg = 0.0;
//                                for (int j = 0; j < sizeOfResampling; j++) {
//                                    sqsumAvg += (double)resampleChunk[j] * (double)resampleChunk[j] / Short.MAX_VALUE;
//                                }
//                                if (Math.sqrt(sqsumAvg) <= 64) {
//                                    for (int k = 0; k < sizeOfResampling; k++) {
//                                        resampleChunk[k] = 0;
//                                    }
//                                    for (int k = 0; k < nbSamplesFadeIO; k++) {
//                                        prevChunkFadeMargin[k] = 0;
//                                    }
//                                }

                                for (int j = 0; j < prevChunkFadeMargin.length; j++) {
                                    prevChunkFadeMargin[j] = resampleFifo.get(j);
                                }

                                for (int j = 0; j < resampleChunk.length; j++) {
                                    bufOutFifo.offer(resampleChunk[j]);
                                    bufOutFifo.offer(resampleChunk[j]);
                                }
                                phaseResampling = (phaseResampling + 1) % 3;
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
//                            bufOut[j] = 1;
                        }

                        double fftBuf[] = new double[bufOut.length];

                        //normalize
                        for (int j = 0; j < bufOut.length; j++) {
                            fftBuf[j] = (double)bufOut[j] / (double)Short.MAX_VALUE;
                        }

                        //low-pass filter
                        DoubleFFT_1D fft = new DoubleFFT_1D(fftBuf.length);

                        fft.realForward(fftBuf);
                        for (int j = 0; j < fftBuf.length; j++) {
                            double pos = (double) j / (double)fftBuf.length;
                            double low_limit = 0.0015;
                            double decay_start = 0.18, decay_end = 0.21;
                            if (pos <= low_limit) {
                                fftBuf[j] = 0;
                            } else if (decay_start <= pos && pos <= decay_end) {
                                fftBuf[j] *= (decay_end - pos) / (decay_end - decay_start);
                            } else if (decay_end <= pos) {
                                fftBuf[j] = 0;
                            }
                        }
                        fft.realInverse(fftBuf, true);

                        for (int j = 0; j < fftBuf.length; j++) {
                            bufOut[j] = (short)(fftBuf[j] * Short.MAX_VALUE);
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
        bufInSizeByte = 65536;
        bufInSizeShort = bufInSizeByte / 2;

        sizeOfResampling = 400;
        nbSamplesFadeIO = 350;

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

    /**
     * SeekBar を動かすメソッド
     */
    public void changeSeekBar() {
        Log.v("SeekBarThread", "start");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.v("SeekBarThread", "running");
                try {
                    /**
                     * FocusedFragment != 1 に変わると、RootMenu が破棄されて Null となってしまう
                     * ので、その瞬間スレッドを停止して破棄する
                     */
                    while (mPlayer != null & focusedFragment == 1) {
                        int currentPosition = mPlayer.getCurrentPosition();
                        Message msg = new Message();
                        msg.what = currentPosition;
                        threadHandler.sendMessage(msg);
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.v("SeekBarThread", "stop");
            }
        }).start();
    }

    /**
     * SeekBar スレッドと UI との Handler
     */
    private Handler threadHandler = new Handler() {
        public void handleMessage(Message msg) {
            final SeekBar mSeekBarPosition = (SeekBar) findViewById(R.id.seekBar);
            mSeekBarPosition.setProgress(msg.what);
            mSeekBarPosition.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        mPlayer.seekTo(progress);
                        mSeekBarPosition.setProgress(progress);
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
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

        switch (CallFragment) {
            case fRoot :
                focusedFragment = 1;
                ft.replace(id.root, new RootMenu(), "Root");
                break;
            case fAlbum :
                focusedFragment = 2;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ft.replace(id.root, new AlbumMenu(), "album");
                break;
            case fArtist :
                focusedFragment = 3;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ft.replace(id.root, new ArtistMenu(), "artist");
                break;
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
    public  AdapterView.OnItemClickListener TrackLongClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ListView lv = (ListView) parent;
            Track item = (Track) lv.getItemAtPosition(position);
            focusTrack(item);

            /**
             * ホーム画面に表示されるアルバム画像などの情報を変更
             * FocusedFragment != 1 だと Null となってしまう
             */
            if (focusedFragment == 1) {
                changeInformation();
            }
            try {
                /**
                 * 曲の変更
                 */
                changeTrack();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    public void changeInformation() {
        if (focusedTrack != null) {
            Bitmap album_art_ = null;
            long albumId = focusedTrack.albumId;
            Uri albumArtUri = Uri.parse("content://media/external/audio/albumart");
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

            SeekBar mSeekBarPosition = (SeekBar) findViewById(R.id.seekBar);
            mSeekBarPosition.setMax(mTotalTime);

            if (mPlayer.isPlaying()) {
                Button buttonPlayPause =  (Button) findViewById(R.id.c_btn);
                buttonPlayPause.setText(R.string.icon_pause);
            }
        }
    }
    public void changeTrack() throws IOException {
        if (mPlayer.isPlaying()) {
            mPlayer.stop();
            mPlayer.prepare();
        }
        mPlayer = MediaPlayer.create(this, focusedTrack.uri);
        mPlayer.start();
        mTotalTime = mPlayer.getDuration();
        if (focusedFragment == 1) {
            Button buttonPlayPause =  (Button) findViewById(R.id.c_btn);
            SeekBar mSeekBarPosition = (SeekBar) findViewById(R.id.seekBar);
            mSeekBarPosition.setMax(mTotalTime);
            buttonPlayPause.setText(R.string.icon_pause);
        }
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
     * PLAY / PAUSE ボタン押したときの動作
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
            buttonPlayPause.setText(R.string.icon_pause);
        }
    }
}
