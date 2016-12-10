package com.example_test.strerch;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

class Artist {

    /**
     * artistKey は今のところ使用せず
     */
    public long id;
    public String artist;
    // private String artistKey;
    int albums;
    public int tracks;

    /**
     * getItems の cursor で使用したい column 名を FILLED_PROJECTION に格納しておく
     */
    private static final String[] FILLED_PROJECTION = {
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.ARTIST_KEY,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
    };

    /**
     * Artist クラスのコンストラクタ
     */
    private Artist(Cursor cursor){
        id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Artists._ID));
        artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST));
        // artistKey = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST_KEY));
        albums = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS));
        tracks = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_TRACKS));
    }

    /**
     * RootMenu.java の ArtistSectionFragment で呼び出す
     */
    static List<Artist> getItems(Context activity) {
        List<Artist> artists = new ArrayList<>();

        ContentResolver resolver = activity.getContentResolver();
        Cursor cursor = resolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                Artist.FILLED_PROJECTION,
                null,
                null,
                "ARTIST  ASC"
        );

        /**
         * Artist クラスのコンストラクタ (param : cursor)を artists に追加
         * cursor を最後まで while 文で回して artists に追加
         */
        assert cursor != null;
        while (cursor.moveToNext()){
            artists.add(new Artist(cursor));
        }

        cursor.close();
        return artists;
    }
}
