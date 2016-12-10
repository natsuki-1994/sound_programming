package com.example_test.strerch;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import static java.lang.String.format;

class ListTrackAdapter extends ArrayAdapter<Track> {

    private LayoutInflater mInflater;

    /**
     * RootMenu.java TrackSectionFragment 内にて
     * menu_tracks の R.id.list (= trackList) に SetAdapter する
     * param : MainActivity
     * param : tracks
     */
    ListTrackAdapter(Context context, List<Track> item) {
        super(context, 0, item);
        mInflater =  (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        /**
         * item は param : List<Track> item に代入された tracks を取り出したもの
         */
        Track item = getItem(position);
        ViewHolder holder;

        /**
         * item_track を捕まえて膨らませる (inflate させる)
         *   item_track を捕まえて convertView に代入
         *   convertView に holder を setTag
         *   holder に setText
         */
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_track, null);
            holder = new ViewHolder();
            holder.trackTextView = (TextView) convertView.findViewById(R.id.title);
            holder.artistTextView = (TextView) convertView.findViewById(R.id.artist);
            holder.durationTextView = (TextView) convertView.findViewById(R.id.duration);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        assert item != null;
        long dm = item.duration / 60000;
        long ds = (item.duration - (dm * 60000)) / 1000;
        holder.artistTextView.setText(item.artist);
        holder.trackTextView.setText(item.title);
        holder.durationTextView.setText(format("%d:%02d", dm, ds));

        return convertView;
    }

    /**
     * item_track に実際に表示させたい項目を設定
     */
    private static class ViewHolder{
        TextView trackTextView;
        TextView artistTextView;
        TextView durationTextView;
    }
}
