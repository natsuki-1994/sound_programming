package com.example_test.strerch;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

class Album {

    /**
     * albumKey は今のところ使用せず
     */
    public long id;
    String album;
    String albumArt;
    long albumId;
    // private String albumKey;
    public String artist;
    public int tracks;

    /**
     * getItems の cursor で使用したい column 名を FILLED_PROJECTION に格納しておく
     */
    private static final String[] FILLED_PROJECTION = {
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ALBUM_ART,
            MediaStore.Audio.Albums.ALBUM_KEY,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
    };

    /**
     * Album クラスのコンストラクタ
     */
    private Album(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Albums._ID));
        album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM));
        albumArt = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
        albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
        // albumKey = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_KEY));
        artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST));
        tracks = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Albums.NUMBER_OF_SONGS));
    }

    /**
     * RootMenu.java の AlbumSectionFragment で呼び出す
     */
    static List<Album> getItems(Context activity) {
        List<Album> albums = new ArrayList<>();
        ContentResolver resolver = activity.getContentResolver();

        /**
         * Album クラスのコンストラクタの param : cursor を設定
         */
        Cursor cursor = resolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                Album.FILLED_PROJECTION,
                null,
                null,
                "ALBUM  ASC"
        );

        /**
         * Album クラスのコンストラクタ (param : cursor)を albums に追加
         * cursor を最後まで while 文で回して albums に追加
         */
        assert cursor != null;
        while (cursor.moveToNext()) {
            albums.add(new Album(cursor));
        }
        cursor.close();
        return albums;
    }
}
