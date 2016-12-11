package com.example_test.strerch;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArtistMenu extends Fragment{

    private static Artist artist_item;
    private View partView;

    private final static String default_msg = "全てのトラック";  /** Spinner のデフォルトメッセージ */
    private static ListTrackAdapter track_adapter;  /** 下部のリストにトラックを表示するための adapter */
    private ImageView album_art;
    private static HashMap<String,String> album_hash = new HashMap<>();  /** アルバムを保存するハッシュ */

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        /**
         * part_album を捕まえて膨らませる (inflate する)
         * 上部には単純にコンテンツを set するだけ
         * 下部のリストは setAdapter を使用する必要がある
         */
        partView =inflater.inflate(R.layout.part_artist, container, false);

        MainActivity activity = (MainActivity) getActivity();
        artist_item   = activity.getFocusedArtist();

        TextView artist_title = (TextView) partView.findViewById(R.id.title);
        TextView artist_albums = (TextView) partView.findViewById(R.id.albums);
        TextView artist_tracks = (TextView) partView.findViewById(R.id.tracks);
        artist_title.setText(artist_item.artist);
        artist_albums.setText(String.valueOf(artist_item.albums)+"Albums");
        artist_tracks.setText(String.valueOf(artist_item.tracks)+"Tracks");

        /**
         * 以下が Spinner に関連する処理
         */
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.spinner);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        adapter.add(default_msg);

        List<Track> tracks = new ArrayList<>();
        tracks.clear();

        String[] SELECTION_ARG = {""};
        SELECTION_ARG[0] = artist_item.artist;

        /**
         * 今選択しているアーティスト (SELECTION_ARG = artist_item.artist) で絞り込む
         */
        ContentResolver resolver = activity.getContentResolver();
        Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Track.COLUMNS,
                MediaStore.Audio.Media.ARTIST + "= ?",
                SELECTION_ARG,
                "TRACK  ASC"
        );
        album_hash.clear();
        assert cursor != null;
        while (cursor.moveToNext()) {
            if (cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)) < 3000) { continue; }
            tracks.add(new Track(cursor));
            /**
             * アルバムを album_hash に追加
             * ハッシュマップを使うことでかぶりなくアルバム名のリストを作成
             */
            album_hash.put(
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                    String.valueOf(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)))
            );
        }

        /**
         * default_msg 以外にアルバム一覧を adapter に追加
         */
        Set<Map.Entry<String, String>> s = album_hash.entrySet();
        for (Map.Entry<String, String> value : s) {
            adapter.add(objectStrip(value.toString()));
        }

        /**
         * Spinner に 上記で設定した adapter をセット
         */
        Spinner spinner = (Spinner) partView.findViewById(R.id.album_spinner);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            /**
             * Spinner の各アルバム名をクリックしたときの動作
             * 各アルバムのトラック一覧にリストを変更する
             */
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Spinner spinner = (Spinner) parent;
                String item = (String) spinner.getSelectedItem();

                /**
                 * default_msg をクリックしたら param : null で setList 呼ぶ
                 * それ以外なら param : item で setList 呼んてリストを更新
                 */
                if (item.equals(default_msg)) setList(null);
                else setList(item);

                /**
                 * アルバムタイトルからコンテントプロバイダ経由でアルバムアートを取得する
                 */
//                String path = ImageGetTask.searchArtPath(getActivity(), item);
                album_art = (ImageView) partView.findViewById(R.id.albumart);
                album_art.setImageResource(R.mipmap.ic_launcher);
//                if (path != null) {
//                    album_art.setTag(path);
//                    ImageGetTask task = new ImageGetTask(album_art);
//                    task.execute(path);
//                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        ListView trackList = (ListView) partView.findViewById(R.id.list);
        track_adapter = new ListTrackAdapter(activity, tracks);
        trackList.setAdapter(track_adapter);
//        /**
//         * trackList がクリックされたら曲の再生
//         */
//        trackList.setOnItemClickListener(activity.TrackClickListener);
        trackList.setOnItemLongClickListener(activity.TrackLongClickListener);

        return partView;
    }

    private String objectStrip(String base){
        if (base == null)
            return null;
        int point = base.lastIndexOf("=");
        if (point != -1) {
            return base.substring(0, point);
        }
        return base;
    }

    private void setList(String item) {
        /**
         * 少し冗長な可能性もあるが Spinner のアイテムクリックしたら毎回新たに tracks を list に set
         */
        MainActivity activity = (MainActivity) getActivity();
        ContentResolver resolver = activity.getContentResolver();
        track_adapter.clear();
        List<Track> tracks = new ArrayList<>();
        String[] SELECTION_ARG = {""};

        if (item == null) {
            SELECTION_ARG[0] = artist_item.artist;
            Cursor cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    Track.COLUMNS,
                    MediaStore.Audio.Media.ARTIST + "= ?",
                    SELECTION_ARG,
                    "TRACK ASC"
            );
            assert cursor != null;
            while (cursor.moveToNext()) {
                if (cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)) < 3000) { continue; }
                tracks.add(new Track(cursor));
            }
        } else {
            /**
             * 選択されたアルバムで絞り込む
             */
            SELECTION_ARG[0] = album_hash.get(item);
            Cursor cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    Track.COLUMNS,
                    MediaStore.Audio.Media.ALBUM_ID + "= ?",
                    SELECTION_ARG,
                    null
            );
            assert cursor != null;
            while (cursor.moveToNext()) {
                if(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)) < 3000) { continue; }
                tracks.add(new Track(cursor));
            }
        }

        track_adapter.addAll(tracks);
        track_adapter.notifyDataSetChanged();
    }
}
