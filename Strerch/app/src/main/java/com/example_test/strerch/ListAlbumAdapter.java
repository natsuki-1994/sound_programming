package com.example_test.strerch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

class ListAlbumAdapter extends ArrayAdapter<Album> {

    private LayoutInflater mInflater;
    private static Context Mcontext;

    ListAlbumAdapter(Context context, List<Album> item) {
        super(context, 0, item);
        mInflater =  (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Mcontext = context;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Album item = getItem(position);
        ViewHolder holder;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_album, null);
            holder = new ViewHolder();
            holder.albumTextView = (TextView)convertView.findViewById(R.id.title);
            holder.artistTextView = (TextView)convertView.findViewById(R.id.artist);
            holder.tracksTextView = (TextView)convertView.findViewById(R.id.tracks);
            holder.artworkImageView = (ImageView)convertView.findViewById(R.id.albumart);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        assert item != null;
        holder.albumTextView.setText(item.album);
        holder.artistTextView.setText(item.artist);
        holder.tracksTextView.setText(String.valueOf(item.tracks) + "tracks");

        String path = item.albumArt;
        holder.artworkImageView.setImageResource(R.mipmap.ic_launcher);
        if (path == null) {
            path = String.valueOf(R.mipmap.ic_launcher);
            Bitmap bitmap = ImageCache.getImage(path);
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeResource(Mcontext.getResources(), R.mipmap.ic_launcher);
                ImageCache.setImage(path, bitmap);
            }
        }
        holder.artworkImageView.setTag(path);
        ImageGetTask task = new ImageGetTask(holder.artworkImageView);
        task.execute(path);

        return convertView;
    }

    private static class ViewHolder{
        TextView  albumTextView;
        TextView  artistTextView;
        TextView  tracksTextView;
        ImageView artworkImageView;
    }

}
