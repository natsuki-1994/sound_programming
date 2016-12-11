package com.example_test.strerch;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

class Track {

    /**
     * albumId, artistId, path, album, uri, trackNo は今のところ使用せず
     */
    public long id;
    // private long albumId;
    // private long artistId;
    // private String path;
    public String title;
    // private String album;
    public String artist;
    Uri uri;
    long duration;
    // private int trackNo;

    /**
     * getItems の cursor で使用したい column 名を COLUMNS に格納しておく
     */
    static final String[] COLUMNS = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
    };

    /**
     *  Track クラスのコンストラクタ
     */
    Track(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
        // path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
        title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
        // album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
        artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
        // albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
        // artistId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID));
        duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
        // trackNo = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK));
        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    /**
     * RootMenu.java の TrackSectionFragment で呼び出す
     */
    static List<Track> getItems(Context activity) {
        List<Track> tracks = new ArrayList<>();
        ContentResolver resolver = activity.getContentResolver();

        /**
         * Track クラスのコンストラクタの param : cursor を設定
         */
        Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Track.COLUMNS,
                null,
                null,
                null
        );

        /**
         * Track クラスのコンストラクタ (param : cursor)を tracks に追加
         * cursor を最後まで while 文で回して tracks に追加
         * 短すぎる音源 (duration < 3000) はパス
         */
        assert cursor != null;
        while (cursor.moveToNext()) {
            if (cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)) < 3000) {
                continue;
            }
            tracks.add(new Track(cursor));
        }
        cursor.close();
        return tracks;
    }

    /**
     * Album 一覧をクリックしたときに呼び出される AlbumMenu.java Fragment にて呼び出す
     * albumID は MainActivity で保管している FocusedAlbum
     */
    static List<Track> getItemsByAlbum(Context activity, long albumID) {
        List<Track> tracks = new ArrayList<>();
        ContentResolver resolver = activity.getContentResolver();

        /**
         * 全 Track から albumID が一致する Track のみを抽出して tracks に追加
         */
        String[] SELECTION_ARG = {""};
        SELECTION_ARG[0] = String.valueOf(albumID);
        Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Track.COLUMNS,
                MediaStore.Audio.Media.ALBUM_ID + "= ?",
                SELECTION_ARG,
                null
        );
        assert cursor != null;
        while (cursor.moveToNext()) {
            if (cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)) < 3000) { continue; }
            tracks.add(new Track(cursor));
        }
        cursor.close();
        return tracks;
    }
}
