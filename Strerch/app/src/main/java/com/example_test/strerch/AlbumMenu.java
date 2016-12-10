package com.example_test.strerch;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class AlbumMenu extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        /**
         * part_album を捕まえて膨らませる (inflate する)
         * 上部には単純にコンテンツを set するだけ
         * 下部のリストは setAdapter を使用する必要がある
         */
        View partView = inflater.inflate(R.layout.part_album, container, false);

        MainActivity activity = (MainActivity) getActivity();
        Album album_item = activity.getFocusedAlbum();  /** MainActivity から focus されているアルバムを引っ張ってくる */

        TextView album_title =  (TextView) partView.findViewById(R.id.title);
        TextView album_artist = (TextView) partView.findViewById(R.id.artist);
        TextView album_tracks = (TextView) partView.findViewById(R.id.tracks);
        ImageView album_art =  (ImageView) partView.findViewById(R.id.albumart);

        album_title.setText(album_item.album);
        album_artist.setText(album_item.artist);
        album_tracks.setText(String.valueOf(album_item.tracks) + "tracks");

        String path = album_item.albumArt;
        album_art.setImageResource(R.mipmap.ic_launcher);
        if (path != null) {
            album_art.setTag(path);
            ImageGetTask task = new ImageGetTask(album_art);
            task.execute(path);
        }

        partView.findViewById(R.id.album_info).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {} });
        partView.findViewById(R.id.tracktitle).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {} });

        /**
         * 以下が list に setAdapter している部分
         * album_item は focus されているアルバム
         */
        List<Track> tracks  = Track.getItemsByAlbum(getActivity(), album_item.albumId);
        ListView trackList = (ListView) partView.findViewById(R.id.list);
        ListTrackAdapter adapter = new ListTrackAdapter(activity, tracks);
        trackList.setAdapter(adapter);

        return partView;
    }

}
