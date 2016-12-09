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

    ListArtistAdapter(Context context, List<Artist> item) {
        super(context, 0, item);
        mInflater =  (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent){
        Artist item = getItem(position);
        ViewHolder holder;

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

    private static class ViewHolder{
        TextView  artistTextView;
        TextView  albumsTextView;
        TextView  tracksTextView;
    }
}
