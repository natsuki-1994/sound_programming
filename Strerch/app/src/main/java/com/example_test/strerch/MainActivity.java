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
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.FileNotFoundException;
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

    /**
     * Audio 関連の変数
     */
    AudioRecord audioRec = null;
    AudioTrack audioTrack;
    boolean bIsRecording = false;
    MediaPlayer mediaPlayer;
    int bufInSizeByteMin;
    int bufInSizeByte;
    int bufInSizeShort;
    int SAMPLING_RATE = 44100;
    int playState = 0;  /** stop : 0 , play : 1, slow: 2 */
    int fftSize = 32768;

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

                    //Prepare values of the window func (# of value = bufInSizeShort)
                    double valWindowFunc[] = new double[2 * bufInSizeShort];
                    for (int i = 0; i < 2 * bufInSizeShort; i++) {
                        valWindowFunc[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (2 * bufInSizeShort)); //Hamming Window
                    }

                    while (bIsRecording) {
                        /**
                         * 音声データの読み込み
                         */
                        audioRec.read(bufIn, 0, bufInSizeShort);

                        if (playState == 1) {  /** state : play */
//                            /**
//                             * stereo に変更する
//                             */
//                            for (int i = 0; i < bufInSizeShort; i++) {
//                                bufOutFifo.offer(bufIn[i]);
//                                bufOutFifo.offer(bufIn[i]);
//                            }

                            DoubleFFT_1D fft = new DoubleFFT_1D(fftSize * 2);
                            double fftData[] = new double[fftSize * 2];
                            double ifftData[] = new double[fftSize * 2];

                            /**
                             * FFT サイズ分だけ取り出し、データ型を short から double に変換し、-1 ～ +1 に正規化
                             */
                            for (int j = 0; j < fftSize; j++) {
                                fftData[2 * j] = (bufIn[j] * 1.0) / Short.MAX_VALUE;
                                fftData[2 * j + 1] = (bufIn[j] * 1.0) / Short.MAX_VALUE;
                            }

                            /**
                             * FFT 実行
                             * 変換後のデータは [振幅成分] [位相成分] [振幅成分] [位相成分] [振幅成分] [位相成分] ... の 繰り返しとなる
                             */
                            fft.realForward(fftData);

                            for (int j = 0; j < fftSize * 2; j++) {
                               ifftData[j] = fftData[j];
                            }
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
                            /**
                             * IFFT 実行
                             */
                            fft.realInverse(ifftData, true);

                            /**
                             * stereo に変更して bufOutFifo にプッシュ
                             */
                            for (int j = 0; j < fftSize * 2; j++) {
                                bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
                                bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
                            }
                        } else if (playState == 2) {  /** state : slow */
                            /**
                             * 1/2 倍速 にする
                             */

                            //step 1: index 0, 2, 4... (2 * bufInSizeShort - 2) にbufInの数値をセット
                            for (int i = 0; i < bufInSizeShort; i++) {
                                bufInStretched[2 * i] = bufIn[i];
                            }

                            //step 2: index 1, 3, 5... (2 * bufInSizeShort - 3) に両隣の値の平均値をセット
                            for (int i = 0; i < bufInSizeShort - 1; i++) {
                                bufInStretched[2 * i + 1] = (short) ((bufInStretched[2 * i] + bufInStretched[2 * i + 2]) / 2);
                            }

                            //step 3: index (2 * bufInSizeShort - 1) (配列の最後) には 右隣がないので、 左隣の値をセット
                            bufInStretched[bufInSizeShort - 1] = bufInStretched[bufInSizeShort - 2];

//                            /**
//                             * 窓関数を適用
//                             */
//                            for (int i = 0; i < 2 * bufInSizeShort; i++) {
//                                bufInStretched[i] = (short)(bufInStretched[i] * valWindowFunc[i]);
//                            }

                            /**
                             * fft 処理
                             *   fft      ... FFT インスタンスの生成
                             *   fftData  ... FFT をかけるデータ（double 型）
                             *   ifftData ... IFFT をかけるデータ（double 型）
                             */
                            DoubleFFT_1D fft = new DoubleFFT_1D(fftSize);  /** fftSize = 4096 / bufInStretched = 4096 * 2 */
                            double fftData[] = new double[fftSize];
                            double ifftData[] = new double[fftSize];

                            for (int i = 0; i < bufInStretched.length; i += fftSize) {
                                /**
                                 * FFT サイズ分だけ取り出し、データ型を short から double に変換し、-1 ～ +1 に正規化
                                 */
                                for (int j = 0; j < fftSize; j++) {
                                    fftData[j] = (bufInStretched[j] * 1.0) / Short.MAX_VALUE;
                                }

                                /**
                                 * FFT 実行
                                 * <前に使ったAPIでは>変換後のデータは [振幅成分] [位相成分] [振幅成分] [位相成分] [振幅成分] [位相成分] ... の 繰り返しとなる
                                 */
                                fft.realForward(fftData);

                                /**
                                 * 周波数シフトを行う
                                 */

                                /*
                                 Nが偶数なら
                                  fftData[n]: { Re[0], Re[n/2], Re[1], Im[1], Re[2], Im[2], ..., Re[n/2-1], Im[n/2-1] }
                                  ドキュメント参照 http://wendykierp.github.io/JTransforms/apidocs/
                                 */
//                                 for (int j = 0; j < ifftData.length; j += 4) {
//                                     ifftData[j]     = fftData[j / 2];
//                                     ifftData[j + 1] = fftData[j / 2 + 1];
//                                     ifftData[j + 2] = 0;
//                                     ifftData[j + 3] = 0;
//                                 }

                                //shiftなし(テスト用)
                                for (int j = 0 ; j < fftSize; j++) {
                                    ifftData[j] = fftData[j];
                                }

                                /**
                                 * IFFT 実行
                                 */
                                fft.realInverse(ifftData, true);

                                /**
                                 * stereo に変更して bufOutFifo にプッシュ
                                 */
                                for (int j = 0; j < fftSize; j++) {
                                    bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
                                    bufOutFifo.offer((short) (ifftData[j] * Short.MAX_VALUE));
                                }
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
        bufInSizeByte = fftSize * 2;
        bufInSizeShort = fftSize;

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
    }

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
    public Track getFocusedTrack() { return  focusedTrack; }

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

            changeAlbumArt();

            Toast.makeText(MainActivity.this, "LongClick: " + item.album, Toast.LENGTH_LONG).show();
            mediaPlayer = MediaPlayer.create(MainActivity.this, item.uri);
            mediaPlayer.start();
            return true;
        }
    };

    public void changeAlbumArt() {
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
    }

    /**
     * ToggleSwitch をクリックしたときの動作
     */
    public SwitchCompat.OnCheckedChangeListener toggleOutsideClickListener = new SwitchCompat.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {  /** isChecked で outside ON */
            Toast.makeText(MainActivity.this, "outside", Toast.LENGTH_SHORT).show();
            if (isChecked) {
                playState = 1;  /** state : play */
                audioTrack.play();
                recordingAndPlay();
            } else {
                playState = 0;  /** state : stop */
                bIsRecording = false;
                audioTrack.stop();
            }
        }
    };

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
