package com.example_test.strerch;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

class ListArtistAdapter extends ArrayAdapter<Artist> {

    private LayoutInflater mInflater;

    /**
     * RootMenu.java ArtistSectionFragment 内にて
     * menu_tracks の R.id.list (= trackList) に SetAdapter する
     * param : MainActivity
     * param : artists
     */
    ListArtistAdapter(Context context, List<Artist> item) {
        super(context, 0, item);
        mInflater =  (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        /**
         * item は param : List<Artist> item に代入された artists を取り出したもの
         */
        Artist item = getItem(position);
        ViewHolder holder;

        /**
         * item_artist layout を捕まえて膨らませる (inflate させる)
         *   item_artist を捕まえて convertView に代入
         *   convertView に holder を setTag
         *   holder に setText
         */
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_artist, null);
            holder = new ViewHolder();
            holder.artistTextView = (TextView) convertView.findViewById(R.id.artist_list);
            holder.albumsTextView = (TextView) convertView.findViewById(R.id.info1);
            holder.tracksTextView = (TextView) convertView.findViewById(R.id.info2);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        assert item != null;
        holder.artistTextView.setText(item.artist);
        holder.albumsTextView.setText(String.format("%d Albums", item.albums));
        holder.tracksTextView.setText(String.format("%d tracks", item.tracks));

        return convertView;
    }

    /**
     * item_artist に実際に表示させたい項目を設定
     */
    private static class ViewHolder{
        TextView artistTextView;
        TextView albumsTextView;
        TextView tracksTextView;
    }
}
