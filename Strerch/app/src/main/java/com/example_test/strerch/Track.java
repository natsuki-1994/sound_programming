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

    public long id;
    public long albumId;
    public long artistId;
    public String path;
    public String title;
    public String album;
    public String artist;
    public Uri uri;
    public long duration;
    public int trackNo;

    private static final String[] COLUMNS = {
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

    private Track(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
        path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
        title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
        album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
        artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
        albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
        artistId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID));
        duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
        trackNo = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK));
        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    static List<Track> getItems(Context activity) {
        List<Track> tracks = new ArrayList<>();
        ContentResolver resolver = activity.getContentResolver();
        Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Track.COLUMNS,
                null,
                null,
                null
        );
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

    static List<Track> getItemsByAlbum(Context activity, long albumID) {
        List<Track> tracks = new ArrayList<>();
        ContentResolver resolver = activity.getContentResolver();
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
